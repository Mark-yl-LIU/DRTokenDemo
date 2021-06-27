package net.corda.example.flows

import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.example.flows.GetSharePrice
import net.corda.example.flows.ShareQueryHandler
import net.corda.example.flows.ShareSignHandler
import net.corda.example.states.ShareState
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class SharePriceClientTests {
    private val mockNet = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("net.corda.example.flows"),
            TestCordapp.findCordapp("net.corda.example.contracts"))))
    private lateinit var a: StartedMockNode

    @Before
    fun setUp() {
        a = mockNet.createNode()

        val oracleName = CordaX500Name("Oracle_Stock", "ShenZhen", "CN")
        val oracle = mockNet.createNode(MockNodeParameters(legalName = oracleName))
        listOf(ShareQueryHandler::class.java, ShareSignHandler::class.java).forEach { oracle.registerInitiatedFlow(it) }

        mockNet.runNetwork()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test
    fun `oracle returns correct Share Price`() {
        val flow = a.startFlow(GetSharePrice("CNE000001R84"))
        mockNet.runNetwork()
        val result = flow.getOrThrow().tx.outputsOfType<ShareState>().single()
        assertEquals("CNE000001R84", result.symbol)
        val SharePrice = Amount.parseCurrency("64.73 CNY")
        assertEquals(SharePrice, result.price)
    }

}
