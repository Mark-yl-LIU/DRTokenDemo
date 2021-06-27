package net.corda.example.flows


import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
//import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap
import java.util.*

@InitiatingFlow
class QuerySharePrice(val SharePriceOracle: Party, val Symbol: String) : FlowLogic<Amount<Currency>>() {
    @Suspendable override fun call() = initiateFlow(SharePriceOracle).sendAndReceive<Amount<Currency>>(Symbol).unwrap { it }
}

//
//@CordaSerializable
//data class QueryShareRequest(val Symbol: String)
