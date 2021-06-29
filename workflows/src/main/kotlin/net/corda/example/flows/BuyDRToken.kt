package net.corda.example.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
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

//        val getshareresult = subFlow(GetSharePrice(Ord_Share_Symbol))
        println("Get Share Price")
        val ShareoracleName = Orcal_Market.name
        val Shareoracle = serviceHub.networkMapCache.getNodeByLegalName(ShareoracleName)?.legalIdentities?.first()
            ?: throw IllegalArgumentException("Requested oracle $ShareoracleName not found on network.")
        val sharepriceRequestedFromOracle = subFlow(QuerySharePrice(Shareoracle, Ord_Share_Symbol))
        println(sharepriceRequestedFromOracle)

        progressTracker.currentStep = QUERYING_SIGNED_FX_ORACLE
        println("Get FX Rate")
        val getFXresult = subFlow(GetFXPairRate("GBP","CNY"))

        val FXoracleName = Orcal_FX.name
        val FXoracle = serviceHub.networkMapCache.getNodeByLegalName(FXoracleName)?.legalIdentities?.first()
            ?: throw IllegalArgumentException("Requested oracle $FXoracleName not found on network.")
        //Normally fetch currency from Local Broker / DR broker to get currecny pair
        //Here just simplify the case.
        val fxRateRequestedFromOracle = subFlow(QueryFxRate(FXoracle, listOf("GBP", "CNY")))


        // Calculate the Ord Share Value
        val ordQuantity = DR_quantity * Ord_Share_Rate
        println(ordQuantity)
        val divfx = 1/ fxRateRequestedFromOracle
        println(divfx)

        val FXcalQ= BigDecimal(sharepriceRequestedFromOracle.quantity) * BigDecimal(Ord_Share_Rate) / BigDecimal(fxRateRequestedFromOracle)
        val FXcalround = FXcalQ.setScale(0,	BigDecimal.ROUND_FLOOR).toLong()
        val TokenPrice = Amount(FXcalround , Currency.getInstance("GBP"))
        println(TokenPrice)
        println("Create DR Token")
        val dRTokenCreated = subFlow(CreateAndIssueDRToken(Local_Bank,DepositBank,fxRateRequestedFromOracle.toBigDecimal(),sharepriceRequestedFromOracle,TokenPrice,Ord_Share_Rate,Ord_Share_Symbol,DR_quantity))
        println(dRTokenCreated)

        println("Get DR Token ID")
        val subStr = dRTokenCreated.indexOf("UUID: ");
        val drFungiableTokenID = dRTokenCreated.substring(subStr + 6, dRTokenCreated.indexOf(" from"))

        /* Build the transaction builder */
        progressTracker.currentStep = BUILDING_THE_TX

        /* Create a move token proposal for the DR token using the helper function provided by Token SDK. This would create the movement proposal and would
         * be committed in the ledgers of parties once the transaction in finalized.
        **/
        //Handling Move Ord Share Quantity

        val stateAndRef = serviceHub.vaultService.queryBy<ShareState>()
            .states.filter { it.state.data.symbol.equals(Ord_Share_Symbol) }[0]

        val evolvableTokenType = stateAndRef.state.data

        val tokenPointer: TokenPointer<ShareState> = evolvableTokenType.toPointer(evolvableTokenType.javaClass)

        val amount:Amount<TokenType> = Amount(ordQuantity,tokenPointer)

        progressTracker.currentStep = WE_SIGN
        /* Call finality flow to notarise the transaction */
//        val stx = subFlow(FinalityFlow(ftx, listOf(localBankSession)))

        val stx = subFlow(MoveFungibleTokens(amount,DepositBank))

        println("Moved $ordQuantity $Ord_Share_Symbol token(s) to ${DepositBank.name.organisation}"+"\ntxId: ${stx.id}")
        /* Distribution list is a list of identities that should receive updates. For this mechanism to behave correctly we call the UpdateDistributionListFlow flow */

        progressTracker.currentStep = FINALISING

        subFlow(UpdateDistributionListFlow(stx))

        return ("\nThe DRToken UUID: "+  drFungiableTokenID  +" is Raised " + DR_Broker.name.organisation + "\nTransaction ID: "
                + stx.id)
    }
}


@InitiatedBy(BuyDRToken::class)
class BuyDRTokenResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        return subFlow(MoveFungibleTokensHandler(counterpartySession))
    }
}
