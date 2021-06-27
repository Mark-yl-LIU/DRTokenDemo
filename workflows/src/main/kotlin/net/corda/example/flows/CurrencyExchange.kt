package net.corda.example.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.FiatCurrency.Companion.getInstance
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap



//For DR Token Issue
import java.util.*

//For Share Info
import net.corda.example.states.ShareState
import net.corda.example.contracts.ShareContract

//For FX Info
import net.corda.example.states.FXState
import net.corda.example.contracts.FxContract
import net.corda.finance.contracts.asset.CASH

// *********
// * Flows *
// *********

@InitiatingFlow
@StartableByRPC
class CurrencyExchange(
    val fromParty: Party,
    val toParty: Party,
    val Orcal_FX: Party,
    val fromAmount: Amount<Currency>,
    val tocurrency: String
    ) : FlowLogic<String>()  {


    companion object {
        object SET_UP : ProgressTracker.Step("Initialising flows.")
        object QUERYING_SIGNED_FX_ORACLE : ProgressTracker.Step("Querying oracle for the FX Price.")
        object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
        object WE_SIGN : ProgressTracker.Step("signing transaction.")
        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }
    }


    override val progressTracker = ProgressTracker(
        SET_UP,
        QUERYING_SIGNED_FX_ORACLE,
        BUILDING_THE_TX,
        WE_SIGN,
        FINALISING
    )

    @Suspendable
    override fun call(): String {

        //1. Setup Notary
        progressTracker.currentStep = SET_UP

        val notary = serviceHub.networkMapCache.notaryIdentities.single() // PoC Usage

        //1. Get Latest exchange rate from Oracle
        progressTracker.currentStep = QUERYING_SIGNED_FX_ORACLE

        subFlow(GetFXPairRate(fromAmount.token.toString(),tocurrency))

        val FXoracleName = Orcal_FX.name
        val FXoracle = serviceHub.networkMapCache.getNodeByLegalName(FXoracleName)?.legalIdentities?.first()
            ?: throw IllegalArgumentException("Requested oracle $FXoracleName not found on network.")
        //Normally fetch currency from Local Broker / DR broker to get currecny pair
        //Here just simplify the case.
        val fxRateRequestedFromOracle = subFlow(QueryFxRate(FXoracle, listOf("CNY", "GBP")))


        /* Create a move token proposal for the DR token using the helper function provided by Token SDK. This would create the movement proposal and would
         * be committed in the ledgers of parties once the transaction in finalized.
        **/


        /* Initiate a flow session with the Investor to send the DR valuation and transfer of the fiat currency to DR Broker */
        val FXSession = initiateFlow(fromParty)

        /* Build the transaction builder */
        progressTracker.currentStep = BUILDING_THE_TX

        val txBuilder = TransactionBuilder(notary)

        // Calculate the Ord Share Value
        val remoteQuantity = fromAmount.quantity * fxRateRequestedFromOracle

        val remoteAmount = Amount.parseCurrency(remoteQuantity.toString().plus(" ").plus(tocurrency))

        FXSession.send(fromAmount.quantity)

        val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken>(FXSession))

        val moneyReceived: List<FungibleToken> = FXSession.receive<List<FungibleToken>>().unwrap { it -> it}

        /* Create a fiat currency proposal for the DR token using the helper function provided by Token SDK. */
        addMoveTokens(txBuilder, inputs, moneyReceived)

        progressTracker.currentStep = WE_SIGN
        /* Sign the transaction with your private */
        val initialSignedTrnx = serviceHub.signInitialTransaction(txBuilder)

        /* Call the CollectSignaturesFlow to recieve signature of the buyer */
        val ftx= subFlow(CollectSignaturesFlow(initialSignedTrnx, listOf(FXSession)))

        /* Call finality flow to notarise the transaction */
        val stx = subFlow(FinalityFlow(ftx, listOf(FXSession)))

        /* Distribution list is a list of identities that should receive updates. For this mechanism to behave correctly we call the UpdateDistributionListFlow flow */
        subFlow(UpdateDistributionListFlow(stx))

        return ("\nThe FX exchange is done between" + fromParty.name.organisation + " and " + toParty.name.organisation+ "\nTransaction ID: "
                + stx.id)
    }
}


@InitiatedBy(CurrencyExchange::class)
class CurrencyExchangeResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call():SignedTransaction {
        /* Recieve the valuation of the DR */
        val price = counterpartySession.receive<List<FungibleToken>>().unwrap { it }

//        /* Create instance of the fiat currecy token amount */
        val priceToken = Amount(price.size.toLong(), price[0].tokenType)
        /*
        *  Generate the move proposal, it returns the input-output pair for the fiat currency transfer, which we need to send to the Initiator.
        * */
        val partyAndAmount = PartyAndAmount(counterpartySession.counterparty,priceToken)
        val inputsAndOutputs : Pair<List<StateAndRef<FungibleToken>>, List<FungibleToken>> =
            DatabaseTokenSelection(serviceHub).generateMove(listOf(Pair(counterpartySession.counterparty,priceToken)),ourIdentity)
        //.generateMove(runId.uuid, listOf(partyAndAmount),ourIdentity,null)

        /* Call SendStateAndRefFlow to send the inputs to the Initiator*/
        subFlow(SendStateAndRefFlow(counterpartySession, inputsAndOutputs.first))
        /* Send the output generated from the fiat currency move proposal to the initiator */
        counterpartySession.send(inputsAndOutputs.second)

        //signing
        subFlow(object : SignTransactionFlow(counterpartySession) {
            @Throws(FlowException::class)
            override fun checkTransaction(stx: SignedTransaction) { // Custom Logic to validate transaction.
            }
        })
        return subFlow(ReceiveFinalityFlow(counterpartySession))
    }
}
