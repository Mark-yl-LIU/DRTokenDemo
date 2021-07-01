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
import com.r3.corda.lib.tokens.contracts.states.FungibleToken


// Test Flows
import net.corda.example.states.DRTokenState
import net.corda.example.states.ShareState


class MoveShareTokenTestMoveShareTokenTest {
    private var network: MockNetwork? = null
    private var NodeDRBroker: StartedMockNode? = null
    private var NodeLcoalBank: StartedMockNode? = null
    private var NodeDRBank: StartedMockNode? = null
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
        val drBrokerName = CordaX500Name("DR Broker", "London", "GB")
        val lcBankName = CordaX500Name("Local Bank", "ShenZhen", "CN")
        val dpBankName = CordaX500Name("DR Bank", "London", "GB")
        val FXoracleName = CordaX500Name("Oracle_FX", "London", "GB")
        val StockOracleName = CordaX500Name("Oracle_Stock", "ShenZhen", "CN")

        NodeDRBroker = network!!.createPartyNode(drBrokerName)
        NodeLcoalBank = network!!.createPartyNode(lcBankName)
        NodeDRBank = network!!.createPartyNode(dpBankName)
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

        //Raise Money to Local Broker
        val fiatCurrencyIssueFlow2 = FiatCurrencyIssueFlow("CNY",400000,NodeLcoalBank!!.info.legalIdentities[0])
        val result2:Future<String> = Noded!!.startFlow(fiatCurrencyIssueFlow2)
        network!!.runNetwork()
        val resultString2 = result2.get()
        println(resultString2)

        //Move Share
        val moveShareFlow = MoveShareFlow(
            "CNE000001R84",NodeLcoalBank!!.info.legalIdentities[0],10)
        val future: Future<String> = Nodee!!.startFlow(moveShareFlow)
        network!!.runNetwork()
        val resultString = future.get()
        println(resultString)

    }
}
