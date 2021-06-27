package net.corda.example.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.example.states.ShareState
import java.util.*

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class CreateAndIssueStock(val ISIN: String,
                          val price: Amount<Currency>,
                          val issueVol: Long,
                          val Oracle_Market: Party) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():String {

        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // Sample specific - retrieving the hard-coded observers
        val identityService = serviceHub.identityService
//        val observers: List<Party> = getObserverLegalIdenties(identityService)!!

        // Construct the output StockState
        val shareState = ShareState( ISIN,price,UniqueIdentifier(),2,listOf(Oracle_Market))

        // The notary provided here will be used in all future actions of this token
        val transactionState = shareState withNotary notary

        // Using the build-in flow to create an evolvable token type -- Stock
        subFlow(CreateEvolvableTokens(transactionState))
        // For Stock Token also can create by add Observer which make the Token info auto update when data change
        // Todo: Add Observer logic and nodes
        // Similar in IssueMoney flow, class of IssuedTokenType represents the stock is issued by the company party
        val issuedStock = shareState.toPointer(shareState.javaClass) issuedBy ourIdentity

        // Create an specified amount of stock with a pointer that refers to the StockState
        val issueAmount = Amount(issueVol.toLong(), issuedStock)

        // Indicate the recipient which is the issuing party itself here
        val shareToken = FungibleToken(issueAmount, ourIdentity)

        // Finally, use the build-in flow to issue the stock tokens. Observer parties provided here will record a copy of the transactions
        val stx = subFlow(IssueTokens(listOf(shareToken)))

        return ("\nGenerated " + issueVol + " " + ISIN + " stocks with price: "
                + price + "\n as Token with Token ID: " + shareState.linearId + ". Enjoy!")
    }
//
//    fun getObserverLegalIdenties(identityService: IdentityService): List<Party>? {
//        var observers: MutableList<Party> = ArrayList()
//        for (observerName in listOf("Broker")) {
//            val observerSet = identityService.partiesFromName(observerName!!, false)
//            if (observerSet.size != 1) {
//                val errMsg = String.format("Found %d identities for the observer.", observerSet.size)
//                throw IllegalStateException(errMsg)
//            }
//            observers.add(observerSet.iterator().next())
//        }
//        return observers
//    }
}
