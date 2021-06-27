package net.corda.example.contracts


import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.example.states.ShareState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test



class ShareContractTest {
    private val ledgerServices = MockServices()
    val Operator = TestIdentity(CordaX500Name(organisation = "Alice", locality = "TestLand", country = "US"))

    @Test
    fun `multiple Output Tests`() {
        val tokenPass = ShareState("TT", Amount.parseCurrency("50 CNY"), UniqueIdentifier(), 2,listOf(Operator.party))
        val tokenFail = ShareState("", Amount.parseCurrency("50 USD"),UniqueIdentifier(), 2,listOf(Operator.party))
        ledgerServices.ledger {
            transaction {
                output(ShareContract.Share_CONTRACT_ID, tokenFail)
                command(Operator.publicKey, com.r3.corda.lib.tokens.contracts.commands.Create())
                this.fails()
            }
        }
        ledgerServices.ledger {
            transaction {
                output(ShareContract.Share_CONTRACT_ID, tokenPass)
                command(Operator.publicKey, com.r3.corda.lib.tokens.contracts.commands.Create())
                this.verifies()
            }
        }
    }    

}