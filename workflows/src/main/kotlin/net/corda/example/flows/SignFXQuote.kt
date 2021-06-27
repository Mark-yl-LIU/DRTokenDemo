package net.corda.example.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.unwrap

@InitiatingFlow
class SignFXQuote(private val FXoracle: Party, private val ftx: FilteredTransaction) : FlowLogic<TransactionSignature>() {

    @Suspendable
    override fun call(): TransactionSignature {
        val session = initiateFlow(FXoracle)
        return session.sendAndReceive<TransactionSignature>(ftx).unwrap { it }
    }
}