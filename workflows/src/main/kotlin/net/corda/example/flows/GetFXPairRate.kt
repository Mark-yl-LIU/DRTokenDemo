package net.corda.example.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.example.contracts.FxContract
import net.corda.example.contracts.FxContract.Companion.FX_CONTRACT_ID
import net.corda.example.states.FXState
import java.util.function.Predicate
import net.corda.example.flows.QueryFxRate
import net.corda.example.flows.SignFXQuote

// The client-side flows that:
// - Uses 'QueryFxRate' to request the CurA:CurB FX Rate
// - Adds it to a transaction and signs it
// - Uses 'SignFXQuote' to gather the oracle's signature attesting that this really is the FX Rate
// - Finalises the transaction
// - Get the FXState Consensus from Orcale_FX
@InitiatingFlow
@StartableByRPC
class GetFXPairRate(val CurA: String, val CurB: String) : FlowLogic<SignedTransaction>() {

    companion object {
        object SET_UP : ProgressTracker.Step("Initialising flows.")
        object QUERYING_THE_ORACLE : ProgressTracker.Step("Querying oracle for the Currency Pair.")
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

    @Suspendable
    override fun call(): SignedTransaction {
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
        val oracleName = CordaX500Name("Oracle_FX", "London", "GB")
        val oracle = serviceHub.networkMapCache.getNodeByLegalName(oracleName)?.legalIdentities?.first()
                ?: throw IllegalArgumentException("Requested oracle $oracleName not found on network.")

        progressTracker.currentStep = QUERYING_THE_ORACLE
        val fxRateRequestedFromOracle = subFlow(QueryFxRate(oracle, listOf(CurA, CurB)))

        progressTracker.currentStep = BUILDING_THE_TX
        //Build the transaction with Oracle Key
        val fxState = FXState(CurA,CurB, fxRateRequestedFromOracle, ourIdentity)


        val fxCmdData = FxContract.Create(CurA,CurB, fxRateRequestedFromOracle)
        // By listing the oracle here, we make the oracle a required signer.
        val fxrateCmdRequiredSigners = listOf(oracle.owningKey, ourIdentity.owningKey)
        val builder = TransactionBuilder(notary)
                .addOutputState(fxState, FX_CONTRACT_ID)
                .addCommand(fxCmdData, fxrateCmdRequiredSigners)

        progressTracker.currentStep = VERIFYING_THE_TX
        builder.verify(serviceHub)

        progressTracker.currentStep = WE_SIGN
        val ptx = serviceHub.signInitialTransaction(builder)

        progressTracker.currentStep = ORACLE_SIGNS
        // For privacy reasons, we only want to expose to the oracle any commands of type `Prime.Create`
        // that require its signature.
        val ftx = ptx.buildFilteredTransaction(Predicate {
            when (it) {
                is Command<*> -> oracle.owningKey in it.signers && it.value is FxContract.Create
                else -> false
            }
        })

        val oracleSignature = subFlow(SignFXQuote(oracle, ftx))
        val stx = ptx.withAdditionalSignature(oracleSignature)

        progressTracker.currentStep = FINALISING
        return subFlow(FinalityFlow(stx, listOf()))
    }
}
