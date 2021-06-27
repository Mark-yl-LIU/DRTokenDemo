package net.corda.example.contracts

import net.corda.example.states.DRTokenState
import net.corda.testing.node.MockServices
import org.junit.Test


class DRTokenStateTests {
    private val ledgerServices = MockServices()

    //State Test
    @Test
    @Throws(NoSuchFieldException::class)
    fun hasFX_RateFieldOfCorrectType() {
        // Does the field exist?
        DRTokenState::class.java.getDeclaredField("fx_rate")
        // Is the field of the correct type?
        assert(DRTokenState::class.java.getDeclaredField("fx_rate").type == Double::class.java)
    }
}