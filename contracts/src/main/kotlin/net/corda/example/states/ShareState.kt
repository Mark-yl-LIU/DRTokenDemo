package net.corda.example.states

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.StatePersistable
import net.corda.example.contracts.ShareContract
import java.util.*


@BelongsToContract(ShareContract::class)
data class ShareState(val symbol: String,
                      val price: Amount<Currency>,
                      override val linearId: UniqueIdentifier,
                      override val fractionDigits: Int = 0,
                      override val maintainers: List<Party>) : EvolvableTokenType(), StatePersistable
{
    override fun toString() = "The Share<$symbol> price is $price."
}
