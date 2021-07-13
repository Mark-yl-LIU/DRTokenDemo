package net.corda.example.flows


import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.contracts.commands.EvolvableTokenTypeCommand
import com.r3.corda.lib.tokens.workflows.flows.evolvable.UpdateEvolvableTokenFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.example.contracts.ShareContract
import net.corda.example.contracts.ShareContract.Companion.Share_CONTRACT_ID
import net.corda.core.transactions.LedgerTransaction
import net.corda.example.states.ShareState
import java.util.function.Predicate
import net.corda.example.flows.QuerySharePrice
import net.corda.example.flows.SignSharePrice
import java.math.BigDecimal
import java.util.ArrayList


// The client-side flows that:
// - Uses 'QuerySharePrice' to request the Symbol Share Price
// - Adds it to a transaction and signs it
// - Uses 'SignSharePrice' to gather the oracle's signature attesting that this really is the Symbol Share Price
// - Finalises the transaction
// - Get the Shareprice Consensus from Orcale_Share

@InitiatingFlow
@StartableByRPC

class GetSharePrice(val Symbol: String) : FlowLogic<String>() {

    companion object {
        object SET_UP : ProgressTracker.Step("Initialising flows.")
        object QUERYING_THE_ORACLE : ProgressTracker.Step("Querying oracle for the Share Price.")
        object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
        object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
        object WE_SIGN : ProgressTracker.Step("signing transaction.")
        object ORACLE_SIGNS : ProgressTracker.Step("Requesting oracle signature.")
        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(SET_UP, QUERYING_THE_ORACLE, BUILDING_THE_TX,
            VERIFYING_THE_TX, WE_SIGN, ORACLE_SIGNS, FINALISING)
    }

    override val progressTracker = tracker()
//    val identityService = serviceHub.identityService



    @Suspendable
    override fun call(): String {
        progressTracker.currentStep = SET_UP

        // Obtain a reference from a notary we wish to use.
        /**
         *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
         *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flows or parsed from config (Preferred)
         *
         *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
         */
        val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
        // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

        // In Corda v1.0, we identify oracles we want to use by name.
        val oracleName = CordaX500Name("Oracle_Stock", "ShenZhen", "CN")
        val oracle = serviceHub.networkMapCache.getNodeByLegalName(oracleName)?.legalIdentities?.first()
            ?: throw IllegalArgumentException("Requested oracle $oracleName not found on network.")

        progressTracker.currentStep = QUERYING_THE_ORACLE
        val sharepriceRequestedFromOracle = subFlow(QuerySharePrice(oracle, Symbol))

        progressTracker.currentStep = BUILDING_THE_TX
        val shareState = ShareState(Symbol,sharepriceRequestedFromOracle,UniqueIdentifier(),0,listOf(oracle))
        val shareStateRef: StateAndRef<ShareState> = serviceHub.vaultService.queryBy<ShareState>().states[0]

        val sstx = subFlow(UpdateEvolvableTokenFlow(shareStateRef, shareState, listOf(), listOf()))

        progressTracker.currentStep = VERIFYING_THE_TX

        progressTracker.currentStep = WE_SIGN

        progressTracker.currentStep = ORACLE_SIGNS

        progressTracker.currentStep = FINALISING
        subFlow(UpdateDistributionListFlow(sstx))

        val pricestr = sharepriceRequestedFromOracle.toString()

        return "Share ${Symbol} Price updated as ${pricestr} . Tx is ${sstx.id}"
    }
}
