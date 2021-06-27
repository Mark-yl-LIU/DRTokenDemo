package net.corda.example.contracts

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import com.r3.corda.lib.tokens.contracts.commands.Create
import com.r3.corda.lib.tokens.contracts.commands.EvolvableTokenTypeCommand
import com.r3.corda.lib.tokens.contracts.commands.Update
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.example.states.ShareState
import java.math.BigDecimal
import java.util.*


// ************
// * Contract *
// ************
class ShareContract : EvolvableTokenContract(),Contract {

    companion object {
        const val Share_CONTRACT_ID = "net.corda.example.contracts.ShareContract"
    }

    interface Commands :CommandData {
        class Create(val Symbol: String, val Price: Amount<Currency>) : Commands
        class Update : Commands
    }

//    class View(val Symbol: String, val Price: Amount<Currency>) : CommandData

    @Throws(IllegalArgumentException::class)
    override fun verify(tx: LedgerTransaction)  {
        val  outputState: ShareState = tx.getOutput(0) as ShareState
        val command = tx.commands.requireSingleCommand<EvolvableTokenTypeCommand>()
        when (command.value) {
            is Create -> additionalCreateChecks(tx)
            is Update -> additionalUpdateChecks(tx)
        }

    }
    override fun additionalCreateChecks(tx: LedgerTransaction) { // Number of outputs is guaranteed as 1
        val createdStockState: ShareState = tx.outputsOfType(ShareState::class.java)[0]
        requireThat{
            //Validations when creating a new stock
            "Stock symbol must not be empty".using(!createdStockState.symbol.isEmpty())
        }
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) { // Number of inputs and outputs are guaranteed as 1
        val input: ShareState = tx.inputsOfType(ShareState::class.java)[0]
        val output: ShareState = tx.outputsOfType(ShareState::class.java)[0]
        requireThat{
            //Validations when a stock is updated, ie. AnnounceDividend (UpdateEvolvableToken)
            "Stock Symbol must not be changed.".using(input.symbol == output.symbol)
            "Stock symbol must not be empty".using(input.symbol.isEmpty())
        }
    }


}