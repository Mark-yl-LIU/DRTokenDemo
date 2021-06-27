package net.corda.example.contracts

import net.corda.testing.node.MockServices
import org.junit.Test

import net.corda.example.states.ShareState

class ShareStateTests {
    private val ledgerServices = MockServices()

    //sample State tests
    @Test
    @Throws(NoSuchFieldException::class)
    fun hasConstructionAreaFieldOfCorrectType() {
        // Does the message field exist?
        ShareState::class.java.getDeclaredField("symbol")
        assert(ShareState::class.java.getDeclaredField("symbol").type == String::class.java)
    }

}