package net.corda.example.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
//import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap


// Simple flows that requests the FX rate from the specified oracle.
@InitiatingFlow
class QueryFxRate(val fxrateOracle: Party, val curpair: List<String>) : FlowLogic<Double>() {
    @Suspendable override fun call() = initiateFlow(fxrateOracle).sendAndReceive<Double>(curpair).unwrap { it }
}

//
//@CordaSerializable
//data class QueryFXRequest(val curPair: List<String>)
