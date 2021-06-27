package net.corda.samples.services

import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.TransactionBuilder
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import org.junit.Rule
import org.junit.Test
import java.util.function.Predicate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

import net.corda.example.contracts.ShareContract
import net.corda.example.contracts.ShareContract.Companion.Share_CONTRACT_ID
import net.corda.example.services.oracle.SharePriceOracle
import net.corda.example.states.ShareState


class ShareOracleServiceTests {
    private val oracleIdentity = TestIdentity(CordaX500Name("Oracle", "New York", "US"))
    private val dummyServices = MockServices(listOf("net.corda.example.contracts"), oracleIdentity)
    private val oracle = SharePriceOracle(dummyServices)
    private val aliceIdentity = TestIdentity(CordaX500Name("Alice", "", "GB"))
    private val notaryIdentity = TestIdentity(CordaX500Name("Notary", "", "GB"))

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Test
    fun `oracle returns correct Share Price`() {
        assertEquals(Amount.parseCurrency("64.73 CNY"), oracle.query("CNE000001R84"))

    }

    @Test
    fun `oracle rejects invalid values `() {
        assertFailsWith<IllegalArgumentException> { oracle.query("ASD") }
    }

    @Test
    fun `oracle signs transactions including a valid Share price`() {
        val command = Command(ShareContract.Commands.Create("CNE000001R84",Amount.parseCurrency("64.73 CNY")), listOf(oracleIdentity.publicKey))
        val state = ShareState("CNE000001R84",Amount.parseCurrency("64.73 CNY"), UniqueIdentifier(),2,listOf(aliceIdentity.party))
        val stateAndContract = StateAndContract(state, Share_CONTRACT_ID)
        val ftx = TransactionBuilder(notaryIdentity.party)
                .withItems(stateAndContract, command)
                .toWireTransaction(dummyServices)
                .buildFilteredTransaction(Predicate {
                    when (it) {
                        is Command<*> -> oracle.services.myInfo.legalIdentities.first().owningKey in it.signers && it.value is ShareContract.Commands.Create
                        else -> false
                    }
                })

        val signature = oracle.sign(ftx)
        assert(signature.verify(ftx.id))
    }

    @Test
    fun `oracle does not sign transactions including an invalid Share Price`() {
        val command = Command(ShareContract.Commands.Create("CNE000001R84",Amount.parseCurrency("64.53 CNY")), listOf(oracleIdentity.publicKey))
        val state = ShareState("CNE000001R84",Amount.parseCurrency("64.73 CNY"),UniqueIdentifier(),2,listOf(aliceIdentity.party))
        val stateAndContract = StateAndContract(state, Share_CONTRACT_ID)
        val ftx = TransactionBuilder(notaryIdentity.party)
                .withItems(stateAndContract, command)
                .toWireTransaction(oracle.services)
                .buildFilteredTransaction(Predicate {
                    when (it) {
                        is Command<*> -> oracle.services.myInfo.legalIdentities.first().owningKey in it.signers && it.value is ShareContract.Commands.Create
                        else -> false
                    }
                })
        assertFailsWith<IllegalArgumentException> { oracle.sign(ftx) }
    }
}
