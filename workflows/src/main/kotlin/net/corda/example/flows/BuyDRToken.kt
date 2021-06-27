package net.corda.example.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
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
import net.corda.core.utilities.unwrap



//For DR Token Issue

//For Share Info

//For FX Info

// *********
// * Flows *
// *********

@InitiatingFlow
@StartableByRPC
class BuyDRToken(
    val DR_Broker: Party,
    val Local_Bank: Party,
    val DepositBank: Party,
    val Orcal_FX: Party,
    val Orcal_Market: Party,
    val Ord_Share_Symbol: String,
    val Ord_Share_Rate: Long,
    val DR_quantity: Long
    ) : FlowLogic<String>()  {


    companion object {
        object SET_UP : ProgressTracker.Step("Initialising flows.")
        object QUERYING_SIGNED_SHARE_ORACLE : ProgressTracker.Step("Querying oracle for the Share Price.")
        object QUERYING_SIGNED_FX_ORACLE : ProgressTracker.Step("Querying oracle for the FX Price.")
        object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
        object WE_SIGN : ProgressTracker.Step("signing transaction.")
        object ORACLES_SIGNS : ProgressTracker.Step("Requesting oracle signature.")
        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }
    }


    override val progressTracker = ProgressTracker(
        SET_UP,
        QUERYING_SIGNED_SHARE_ORACLE,
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

        progressTracker.currentStep = QUERYING_SIGNED_SHARE_ORACLE

        val getshareresult = subFlow(GetSharePrice(Ord_Share_Symbol))

        val ShareoracleName = Orcal_Market.name
        val Shareoracle = serviceHub.networkMapCache.getNodeByLegalName(ShareoracleName)?.legalIdentities?.first()
            ?: throw IllegalArgumentException("Requested oracle $ShareoracleName not found on network.")
        val sharepriceRequestedFromOracle = subFlow(QuerySharePrice(Shareoracle, Ord_Share_Symbol))

        progressTracker.currentStep = QUERYING_SIGNED_FX_ORACLE

        val getFXresult = subFlow(GetFXPairRate("GBP","CNY"))

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
        val investorSession = initiateFlow(Local_Bank)

        /* Build the transaction builder */
        progressTracker.currentStep = BUILDING_THE_TX

        val txBuilder = TransactionBuilder(notary)

        // Calculate the Ord Share Value
        val ordQuantity = DR_quantity * Ord_Share_Rate
        val divfx = 1/ fxRateRequestedFromOracle

        var TokenPrice = Amount.parseCurrency("1 GBP")
        if (fxRateRequestedFromOracle >1) {
            TokenPrice = sharepriceRequestedFromOracle.times(Ord_Share_Rate).times(fxRateRequestedFromOracle.toLong())
        }
        else {
            TokenPrice = sharepriceRequestedFromOracle.times(Ord_Share_Rate).splitEvenly(divfx.toInt())[0]
        }

        val dRTokenCreated = subFlow(CreateAndIssueDRToken(Local_Bank,DepositBank,fxRateRequestedFromOracle.toBigDecimal(),sharepriceRequestedFromOracle,TokenPrice,Ord_Share_Rate,Ord_Share_Symbol,DR_quantity))

        investorSession.send(ordQuantity)

        val subString = dRTokenCreated.indexOf("UUID: ");
        val DRfungibleTokenId = dRTokenCreated.substring(subString + 6, dRTokenCreated.indexOf(" from"))

        val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken>(investorSession))

        val moneyReceived: List<FungibleToken> = investorSession.receive<List<FungibleToken>>().unwrap { it -> it}

        /* Create a fiat currency proposal for the DR token using the helper function provided by Token SDK. */
        addMoveTokens(txBuilder, inputs, moneyReceived)

        progressTracker.currentStep = WE_SIGN
        /* Sign the transaction with your private */
        val initialSignedTrnx = serviceHub.signInitialTransaction(txBuilder)

        /* Call the CollectSignaturesFlow to recieve signature of the buyer */
        val ftx= subFlow(CollectSignaturesFlow(initialSignedTrnx, listOf(investorSession)))

        /* Call finality flow to notarise the transaction */
        val stx = subFlow(FinalityFlow(ftx, listOf(investorSession)))

        /* Distribution list is a list of identities that should receive updates. For this mechanism to behave correctly we call the UpdateDistributionListFlow flow */
        subFlow(UpdateDistributionListFlow(stx))

        return ("\nThe DRToken UUID: "+  DRfungibleTokenId  +" is sold to " + DR_Broker.name.organisation + "\nTransaction ID: "
                + stx.id)
    }
}


@InitiatedBy(BuyDRToken::class)
class BuyDRTokenResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
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
