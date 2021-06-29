package net.corda.example.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokensHandler
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import jdk.nashorn.internal.parser.Token
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
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
class BuyShare(
    val LocalBroker: Party,
    val Orcal_Market: Party,
    val Ord_Share_Symbol: String,
    val Ord_Share_quantity: Long
    ) : FlowLogic<String>()  {


    companion object {
        object SET_UP : ProgressTracker.Step("Initialising flows.")
        object QUERYING_SIGNED_SHARE_ORACLE : ProgressTracker.Step("Querying oracle for the Share Price.")
        object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
        object WE_SIGN : ProgressTracker.Step("signing transaction.")
        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }
    }


    override val progressTracker = ProgressTracker(
        SET_UP,
        QUERYING_SIGNED_SHARE_ORACLE,
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

        println("Get Share Price")
        val ShareoracleName = Orcal_Market.name
        val Shareoracle = serviceHub.networkMapCache.getNodeByLegalName(ShareoracleName)?.legalIdentities?.first()
            ?: throw IllegalArgumentException("Requested oracle $ShareoracleName not found on network.")
        val sharepriceRequestedFromOracle = subFlow(QuerySharePrice(Shareoracle, Ord_Share_Symbol))
        println(sharepriceRequestedFromOracle)

        //Normally fetch currency from Local Broker / DR broker to get currecny pair
        //Here just simplify the case.


        // Calculate the Ord Share Value

        val shareValue= BigDecimal(sharepriceRequestedFromOracle.quantity) * BigDecimal(Ord_Share_quantity)
        val shareValueround = shareValue.setScale(0,	BigDecimal.ROUND_FLOOR).toLong()
        val TokenPrice = Amount(shareValueround , sharepriceRequestedFromOracle.token)
        println(TokenPrice)
        //println("Create share Token")

        val shareStateAndRef = serviceHub.vaultService.queryBy<ShareState>().states.single()
        val shareState = shareStateAndRef.state.data
        val moveresult = subFlow(MoveShareFlow(Ord_Share_Symbol,LocalBroker,Ord_Share_quantity))
        /* Build the transaction builder */
        progressTracker.currentStep = BUILDING_THE_TX

        /* Create a move token proposal for the DR token using the helper function provided by Token SDK. This would create the movement proposal and would
         * be committed in the ledgers of parties once the transaction in finalized.
        **/
        //Handling Move Ord Share Quantity
        val txBuilder = TransactionBuilder(notary)

        val shareAmount : Amount<TokenType> = Amount(Ord_Share_quantity,shareState.toPointer(shareState.javaClass))
        val shareAmountParty = PartyAndAmount(ourIdentity,shareAmount)

        val (linearId) = shareState

        val buyerSession = initiateFlow(LocalBroker)

        buyerSession.send(TokenPrice)



        val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken>(buyerSession))

        val moneyReceived: List<FungibleToken> = buyerSession.receive<List<FungibleToken>>().unwrap { it -> it}

        addMoveTokens(txBuilder, inputs, moneyReceived)


        progressTracker.currentStep = WE_SIGN
        /* Call finality flow to notarise the transaction */
//        val stx = subFlow(FinalityFlow(ftx, listOf(localBankSession)))
        val initialSignedTrnx = serviceHub.signInitialTransaction(txBuilder)

        val ftx= subFlow(CollectSignaturesFlow(initialSignedTrnx, listOf(buyerSession)))

        val stx = subFlow(FinalityFlow(ftx, listOf(buyerSession)))


        println("Moved $Ord_Share_quantity $Ord_Share_Symbol token(s) to ${LocalBroker.name.organisation}"+"\ntxId: ${stx.id}")
        /* Distribution list is a list of identities that should receive updates. For this mechanism to behave correctly we call the UpdateDistributionListFlow flow */

        progressTracker.currentStep = FINALISING

        subFlow(UpdateDistributionListFlow(stx))

        return ("\nThe " +Ord_Share_quantity + "  UUID: <" + linearId +"> "+ Ord_Share_Symbol + " Sold to " + LocalBroker.name.organisation + "\nTransaction ID: "
                + stx.id )
    }
}


@InitiatedBy(BuyShare::class)
class BuyShareResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call():SignedTransaction {
        val price = counterpartySession.receive<Amount<Currency>>().unwrap { it }

        /* Create instance of the fiat currecy token amount */
        val priceToken = Amount(price.quantity, FiatCurrency.getInstance(price.token.currencyCode))

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
