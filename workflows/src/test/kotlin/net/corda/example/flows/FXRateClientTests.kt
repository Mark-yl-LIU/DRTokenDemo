package net.corda.example.flows

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.example.flows.GetFXPairRate
import net.corda.example.flows.FXQueryHandler
import net.corda.example.flows.FXSignHandler
import net.corda.example.states.FXState
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class FXRateClientTests {
    private val mockNet = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("net.corda.example.flows"),
            TestCordapp.findCordapp("net.corda.example.contracts"))))
    private lateinit var a: StartedMockNode

    @Before
    fun setUp() {
        a = mockNet.createNode()

        val oracleName = CordaX500Name("Oracle_FX", "London", "GB")
        val oracle = mockNet.createNode(MockNodeParameters(legalName = oracleName))
        listOf(FXQueryHandler::class.java, FXSignHandler::class.java).forEach { oracle.registerInitiatedFlow(it) }

        mockNet.runNetwork()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test
    fun `oracle returns correct FX pair`() {
        val flow = a.startFlow(GetFXPairRate("GBP","CNY"))
        mockNet.runNetwork()
        val result = flow.getOrThrow().tx.outputsOfType<FXState>().single()
        assertEquals("GBP", result.curA)
        assertEquals("CNY", result.curB)
        val GBPCNYRate = 10.0
        assertEquals(GBPCNYRate, result.fxrate)
    }

}
