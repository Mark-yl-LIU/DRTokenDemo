package net.corda.example.contracts


import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import net.corda.example.states.FXState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

// ************
// * Contract *
// ************


class FxContract: EvolvableTokenContract(),Contract{
    companion object {
        const val FX_CONTRACT_ID = "net.corda.example.contracts.FxContract"
    }
    override fun additionalCreateChecks(tx: LedgerTransaction) {
        TODO("Not yet implemented")
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) {
        TODO("Not yet implemented")
    }

    class Create(val CurA: String, val CurB: String , val FX_rate: Double) : CommandData

    override fun verify(tx: LedgerTransaction) = requireThat {
        "There are no inputs" using (tx.inputs.isEmpty())
        val output = tx.outputsOfType<FXState>().single()
        val command = tx.commands.requireSingleCommand<Create>().value
        "The FXQuote in the output does not match the FX in the command." using
                (command.CurA == output.curA && command.CurB == output.curB && command.FX_rate == output.fxrate)

    }
}
