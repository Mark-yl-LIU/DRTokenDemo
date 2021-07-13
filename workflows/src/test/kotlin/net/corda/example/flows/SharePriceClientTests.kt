package net.corda.example.flows

import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.example.flows.GetFXPairRate
import net.corda.example.flows.FXQueryHandler
import net.corda.example.flows.FXSignHandler
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.example.states.FXState
import net.corda.example.states.ShareState
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Future
import kotlin.test.assertEquals

class SharePriceClientTests {
    private var network: MockNetwork? = null
    private var NodeDRBroker: StartedMockNode? = null
    private var NodeLcoalBank: StartedMockNode? = null
    private var Nodee: StartedMockNode? = null

    @Before
    fun setUp() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("net.corda.example.contracts"),
            TestCordapp.findCordapp("net.corda.example.flows"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")
        ), networkParameters = testNetworkParameters(minimumPlatformVersion = 4)))
        val drBrokerName = CordaX500Name("DR Broker", "London", "GB")
        val lcBankName = CordaX500Name("Local Bank", "ShenZhen", "CN")
        val StockOracleName = CordaX500Name("Oracle_Stock", "ShenZhen", "CN")

        NodeDRBroker = network!!.createPartyNode(drBrokerName)
        NodeLcoalBank = network!!.createPartyNode(lcBankName)
        Nodee = network!!.createPartyNode(StockOracleName)

        network!!.runNetwork()
    }

    @After
    fun tearDown() {
        network!!.stopNodes()
    }

    @Test
    fun `oracle returns correct share price`() {

        //put share into Local Custody
        val createAndIssueFlow = CreateAndIssueStock(
            "CNE000001R84",
            Amount.parseCurrency("67.56 CNY"),
            4000,
            Nodee!!.info.legalIdentities[0])
        val future1: Future<String> = Nodee!!.startFlow(createAndIssueFlow)
        network!!.runNetwork()
        val resultString1 = future1.get()
        println(resultString1)


        val flow = Nodee!!.startFlow(GetSharePrice("CNE000001R84"))
        network!!.runNetwork()
        val result = flow.get()
        println(result)
    }

}
