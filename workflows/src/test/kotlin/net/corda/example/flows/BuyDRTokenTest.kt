package net.corda.example.flows

import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
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


class BuyDRTokenTest {
    private var network: MockNetwork? = null
    private var Nodea: StartedMockNode? = null
    private var Nodeb: StartedMockNode? = null
    private var Nodec: StartedMockNode? = null
    private var Noded: StartedMockNode? = null
    private var Nodee: StartedMockNode? = null

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("net.corda.example.contracts"),
            TestCordapp.findCordapp("net.corda.example.flows"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
            TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")
        ), networkParameters = testNetworkParameters(minimumPlatformVersion = 4)))
        val FXoracleName = CordaX500Name("Oracle_FX", "London", "GB")
        val StockOracleName = CordaX500Name("Oracle_Stock", "ShenZhen", "CN")
        Nodea = network!!.createPartyNode(null)
        Nodeb = network!!.createPartyNode(null)
        Nodec = network!!.createPartyNode(null)
        Noded = network!!.createPartyNode(FXoracleName)
        Nodee = network!!.createPartyNode(StockOracleName)
        network!!.runNetwork()
    }


    @After
    fun tearDown() {
        network!!.stopNodes()
    }

    @Test
    fun DRTokenStateCreation() {

        val buyDRTokenFlow = BuyDRToken(
            Nodea!!.info.legalIdentities[0],Nodeb!!.info.legalIdentities[0],Nodec!!.info.legalIdentities[0],Noded!!.info.legalIdentities[0],Nodee!!.info.legalIdentities[0],
            "CNE000001R84",10,40)
        val future: Future<String> = Nodeb!!.startFlow(buyDRTokenFlow)
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