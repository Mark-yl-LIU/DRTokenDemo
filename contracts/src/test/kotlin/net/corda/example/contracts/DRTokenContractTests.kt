package net.corda.example.contracts

import net.corda.core.identity.CordaX500Name
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.example.states.DRTokenState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import java.math.BigDecimal
import java.util.*

class DRTokenContractTests {
    private val ledgerServices = MockServices()
    val Operator1 = TestIdentity(CordaX500Name(organisation = "Broker1", locality = "TestLand", country = "GB"))
    val Operator2 = TestIdentity(CordaX500Name(organisation = "Broker2", locality = "TestLand", country = "GB"))
    val Operator3 = TestIdentity(CordaX500Name(organisation = "Depositary", locality = "TestLand", country = "GB"))
    val Operator4 = TestIdentity(CordaX500Name(organisation = "Custody", locality = "TestLand", country = "GB"))

    //sample Tests
    @Test
    fun dummytest() {
        val tokenPass = DRTokenState(
            UniqueIdentifier(),
            Arrays.asList(Operator4.party),
            Operator1.party,
            Operator2.party,
            BigDecimal(0.5),
            Amount.parseCurrency("50 USD"),
            Amount.parseCurrency("40 USD"),
            10,
            "HSBC"
        )

        val tokenFail = DRTokenState(
            UniqueIdentifier(),
            Arrays.asList(Operator4.party),
            Operator1.party,
            Operator2.party,
            BigDecimal(0.5),
            Amount.parseCurrency("0 USD"),
            Amount.parseCurrency("0 USD"),
            10,
            "HSBC"
        )

        ledgerServices.ledger {
            // Should fail Ord_Share price is equal to 0
            transaction {
                //failing transaction
                output(DRTokenContract.CONTRACT_ID, tokenFail)
                command(Operator4.publicKey, com.r3.corda.lib.tokens.contracts.commands.Create())
                this.fails()
            }
        }
        //pass

        ledgerServices.ledger {
            transaction {
                //passing transaction
                output(DRTokenContract.CONTRACT_ID, tokenPass)
                command(Operator4.publicKey, com.r3.corda.lib.tokens.contracts.commands.Create())
                verifies()
            }
        }
    }
}