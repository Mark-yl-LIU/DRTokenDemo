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
import net.corda.example.states.DRTokenState

class DRTokenIssueFlowTests {
    private var network: MockNetwork? = null
    private var Nodea: StartedMockNode? = null
    private var Nodeb: StartedMockNode? = null
    private var Nodec: StartedMockNode? = null
    private var Noded: StartedMockNode? = null

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
        Nodec = network!!.createPartyNode(null)
        Noded = network!!.createPartyNode(null)
        network!!.runNetwork()
    }


    @After
    fun tearDown() {
        network!!.stopNodes()
    }

    @Test
    fun DRTokenStateCreation() {

        val createAndIssueFlow = CreateAndIssueDRToken(
            Nodea!!.info.legalIdentities[0],Nodeb!!.info.legalIdentities[0],
                    BigDecimal(0.5),
            Amount.parseCurrency("50 USD"),
            Amount.parseCurrency("30 USD"),
            5,
            "HSBC", 20)
        val future: Future<String> = Nodeb!!.startFlow(createAndIssueFlow)
        network!!.runNetwork()
        val resultString = future.get()
        println(resultString)
        val subString = resultString.indexOf("UUID: ");
        val DRfungibleTokenId = resultString.substring(subString + 6, resultString.indexOf(" from"))
        println("-" + DRfungibleTokenId + "-")
        val inputCriteria: QueryCriteria = LinearStateQueryCriteria().withUuid(Arrays.asList(UUID.fromString(DRfungibleTokenId))).withStatus(StateStatus.UNCONSUMED)
        val storedFungibleTokenb = Nodeb!!.services.vaultService.queryBy(DRTokenState::class.java, inputCriteria).states
        val (linearId) = storedFungibleTokenb[0].state.data
        println("-$linearId-")
        assertEquals(linearId.toString(), DRfungibleTokenId)
    }
}

