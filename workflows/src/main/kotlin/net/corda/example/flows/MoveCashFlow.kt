package net.corda.example.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokensHandler
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.ProgressTracker
import net.corda.example.states.DRTokenState
import java.util.*


// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC

class MoveCashFlow (
    val moneyamount: Amount<Currency>,
    val receiver: Party
    ) : FlowLogic<String>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():String {
        val priceToken = Amount(moneyamount.quantity, FiatCurrency.getInstance(moneyamount.token.currencyCode))
        val partyAndAmount = PartyAndAmount(ourIdentity,priceToken)

        val stx = subFlow(MoveFungibleTokens(priceToken,receiver))

        return "Moved $moneyamount to ${receiver.name.organisation}."+
                "\ntxId: ${stx.id}"
    }

}

@InitiatedBy(MoveCashFlow::class)
class MoveCashFlowResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Simply use the MoveFungibleTokensHandler as the responding flow
        return subFlow(MoveFungibleTokensHandler(counterpartySession))
    }


}
