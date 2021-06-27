package net.corda.example.flows

import co.paralleluniverse.fibers.Suspendable
//import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier.Companion.fromString
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.example.states.DRTokenState
import java.math.BigDecimal
import java.util.*

// *********
// * Flows *
// *********
@StartableByRPC
class CreateAndIssueDRToken(   val Local_Custody: Party,
                               val Deposit: Party,
                               val FX_Rate: BigDecimal,
                               val Ord_Price: Amount<Currency>,
                               val Toekn_Price:Amount<Currency>,
                                val Ord_Rate: Long,
                                val Ord_Isin: String,
                                val Quantity: Long
                               ) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():String {
        /* Choose the notary for the transaction */
        // Obtain a reference from a notary we wish to use.
        val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
        /* Get a reference of own identity */
        val issuer = ourIdentity
        /* Construct the output state */
        val uuid = fromString(UUID.randomUUID().toString())
        val DRState = DRTokenState(
            uuid,
            Arrays.asList(Deposit),
            Local_Custody,
            Deposit,
            FX_Rate,
            Ord_Price,
            Toekn_Price,
            Ord_Rate,
            Ord_Isin)

        /* Create an instance of TransactionState using the DRState token and the notary */
        val transactionState = DRState withNotary notary

        /* Create the DR token. TokenSDK provides the CreateEvolvableTokens flow which could be called to create an evolvable token in the ledger.*/
        subFlow(CreateEvolvableTokens(transactionState))

        // A Evolvable Fungible Token Created , DR Token Created

        /*
        * Create an instance of IssuedTokenType, it is used by our Non-Fungible token which would be issued to the owner. Note that the IssuedTokenType takes
        * a TokenPointer as an input, since EvolvableTokenType is not TokenType, but is a LinearState. This is done to separate the state info from the token
        * so that the state can evolve independently.
        * IssuedTokenType is a wrapper around the TokenType and the issuer.
        * */

        val issuedDRToken = DRState.toPointer(DRState.javaClass) issuedBy  issuer

        /* Create an instance of the fungible DR token with the owner as the token holder. The last paramter is a hash of the jar containing the TokenType, use the helper function to fetch it. */
        val drtoken = FungibleToken(Amount(Quantity,issuedDRToken),Deposit)

        /* Issue the DR token by calling the IssueTokens flow provided with the TokenSDK */
        val stx = subFlow(IssueTokens(listOf(drtoken)))

        return ("\nThe fungible DR token is created with UUID: " + DRState.linearId + " from Deposit: "+ Deposit.name.toString() + ". And"
                + "\nTransaction ID: " + stx.id)
    }
}
