package net.corda.example.states


import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.example.contracts.DRTokenContract
import java.math.BigDecimal
import java.util.*

@BelongsToContract(DRTokenContract::class)
data class DRTokenState(override val linearId: UniqueIdentifier,
                        override val maintainers: List<Party>,
//                        val local_broker: Party,
//                        val dr_broker: Party,
                        val local_custody: Party,
                        val deposit: Party,
                        val fx_rate: BigDecimal,
                        val ord_share_price: Amount<Currency>,
                        val tokenvalue: Amount<Currency>,
                        val toekn_ordshare_rate: Long,
                        val ord_share_isin: String,
                        val issuer: Party = maintainers.single(),
                        override val fractionDigits: Int = 2
):EvolvableTokenType()

