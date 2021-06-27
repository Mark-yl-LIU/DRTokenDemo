package net.corda.example.contracts

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction
import net.corda.example.states.DRTokenState
import java.math.BigDecimal

class DRTokenContract:EvolvableTokenContract(),Contract {
    companion object {
        const val CONTRACT_ID = "net.corda.example.contracts.DRTokenContract"
    }
    override fun additionalCreateChecks(tx: LedgerTransaction) {
        // Write contract validation logic to be performed while creation of token
        val outputState = tx.getOutput(0) as DRTokenState
        outputState.apply {
            require(outputState.ord_share_price.quantity > 0) {"Ord_Price cannot be zero"}
            require(outputState.fx_rate > BigDecimal.ZERO) {"FX cannot be zero"}
            require(outputState.ord_share_isin != "") {"'Ord_share_isin' is mandatory"}
        }
    }
    override fun additionalUpdateChecks(tx: LedgerTransaction) {
        // Write contract validation logic to be performed while updation of token
    }



}