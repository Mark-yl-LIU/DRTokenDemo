package net.corda.example.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.money.FiatCurrency.Companion.getInstance
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokensHandler
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.example.states.DRTokenState
import net.corda.example.states.ShareState
import java.math.BigDecimal
import java.util.*


//For DR Token Issue

//For Share Info

//For FX Info

// *********
// * Flows *
// *********

@InitiatingFlow
@StartableByRPC
class FXexchange(
    val DR_Broker: Party,
    val Local_Broker: Party,
    val Orcal_FX: Party,
    val fromAmount: Amount<Currency>,
    val tocurrency: String
    ) : FlowLogic<String>()  {

// Get the FX from Orcale and do the exchange between from_DR Broker to Local Broker

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

        progressTracker.currentStep = SET_UP

        val notary = serviceHub.networkMapCache.notaryIdentities.single() // PoC Usage

        // 1. Get the Share State from the Vault using Vault Query

        progressTracker.currentStep = QUERYING_SIGNED_FX_ORACLE
        println("Get FX Rate")
        val fromCurrency = fromAmount.token.currencyCode
        println("Get FX Pair Rate")
        val getFXresult = subFlow(GetFXPairRate(fromCurrency,tocurrency))
        println(fromCurrency)
        println(tocurrency)

        val FXoracleName = Orcal_FX.name
        val FXoracle = serviceHub.networkMapCache.getNodeByLegalName(FXoracleName)?.legalIdentities?.first()
            ?: throw IllegalArgumentException("Requested oracle $FXoracleName not found on network.")

        //Here just simplify the case.
        println("Query Rate")
        val fxRateRequestedFromOracle = subFlow(QueryFxRate(FXoracle, listOf("GBP", "CNY")))

        val FXcalQ= BigDecimal(fromAmount.quantity) * BigDecimal(fxRateRequestedFromOracle)
        val FXcalround = FXcalQ.setScale(0,	BigDecimal.ROUND_FLOOR).toLong()
        val toAmount = Amount(FXcalround, Currency.getInstance(tocurrency))
        println(toAmount)


        println("Get DR Token ID")
        /* Build the transaction builder */
        progressTracker.currentStep = BUILDING_THE_TX

        /* Create a move token proposal for the DR token using the helper function provided by Token SDK. This would create the movement proposal and would
         * be committed in the ledgers of parties once the transaction in finalized.
        **/
        //Handling Move Ord Share Quantity

        val txBuilder = TransactionBuilder(notary)
        val ccytoken =getInstance(fromAmount.token.currencyCode)

        val sendfrmAmt : Amount<TokenType> = Amount(fromAmount.quantity,ccytoken)
        val partyAndAmount = listOf(PartyAndAmount(DR_Broker,sendfrmAmt ))
        addMoveFungibleTokens(txBuilder,serviceHub, partyAndAmount,Local_Broker )
        // Sent the From Amount to Target Party

        val toCurrencySession = initiateFlow(DR_Broker)
        toCurrencySession.send(toAmount)

        // Recieve inputStatesAndRef for the fiat currency exchange from the buyer, these would be inputs to the fiat currency exchange transaction.
        val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken>(toCurrencySession))

        // Recieve output for the fiat currency from the buyer, this would contain the transfered amount from buyer to yourself
        val moneyReceived: List<FungibleToken> = toCurrencySession.receive<List<FungibleToken>>().unwrap { it -> it}

        /* Create a fiat currency proposal for the house token using the helper function provided by Token SDK. */
        addMoveTokens(txBuilder, inputs, moneyReceived)

        /* Sign the transaction with your private */
        val initialSignedTrnx = serviceHub.signInitialTransaction(txBuilder)

        /* Call the CollectSignaturesFlow to recieve signature of the buyer */
        val ftx= subFlow(CollectSignaturesFlow(initialSignedTrnx, listOf(toCurrencySession)))

        progressTracker.currentStep = WE_SIGN
        /* Call finality flow to notarise the transaction */
        val stx = subFlow(FinalityFlow(ftx, listOf(toCurrencySession)))

        progressTracker.currentStep = FINALISING


        return ("\nExchange between " + Local_Broker.name.organisation + " And " + DR_Broker.name.organisation + " on Amount" + fromAmount.toString() +"\nTransaction ID: "
                + stx.id)
    }
}


@InitiatedBy(FXexchange::class)
class FXexchangeResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call():SignedTransaction {
        /* Recieve the valuation of the house */
        val toccyAmount = counterpartySession.receive<Amount<Currency>>().unwrap { it }

        /* Create instance of the fiat currecy token amount */
        val priceToken = Amount(toccyAmount.quantity, getInstance(toccyAmount.token.currencyCode))

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
