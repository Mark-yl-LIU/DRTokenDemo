package net.corda.example.flows

import net.corda.core.contracts.Amount
import net.corda.core.node.services.Vault.StateStatus
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import java.util.concurrent.Future
import kotlin.test.assertEquals
import java.math.BigDecimal

// Test Flows
import net.corda.example.states.ShareState

class StockIssueFlowTests {
    private var network: MockNetwork? = null
    private var Nodea: StartedMockNode? = null
    private var Nodeb: StartedMockNode? = null

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("net.corda.example.contracts"),
            TestCordapp.findCordapp("net.corda.example.flows"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")
        ), networkParameters = testNetworkParameters(minimumPlatformVersion = 4)))
        Nodea = network!!.createPartyNode(null)
        Nodeb = network!!.createPartyNode(null)
        network!!.runNetwork()
    }


    @After
    fun tearDown() {
        network!!.stopNodes()
    }

    @Test
    fun StockStateCreation() {

        val createAndIssueFlow = CreateAndIssueStock(
            "CNE000001R84",
            Amount.parseCurrency("67.56 CNY"),
            4000,
            Nodeb!!.info.legalIdentities[0])
        val future: Future<String> = Nodeb!!.startFlow(createAndIssueFlow)
        network!!.runNetwork()
        val resultString = future.get()
        println(resultString)
        val subString = resultString.indexOf("Token ID: ");
        val SharefungibleTokenId = resultString.substring(subString + 10, resultString.indexOf(". Enjoy!"))
        println("-" + SharefungibleTokenId + "-")
        val inputCriteria: QueryCriteria = LinearStateQueryCriteria().withUuid(listOf(UUID.fromString(SharefungibleTokenId))).withStatus(StateStatus.UNCONSUMED)
        val storedFungibleTokenb = Nodeb!!.services.vaultService.queryBy(ShareState::class.java, inputCriteria).states
        val (sharestateinfo) = storedFungibleTokenb[0].state
        println("-$sharestateinfo.linearId-")
        assertEquals(sharestateinfo.linearId.toString(), SharefungibleTokenId)
    }
}

