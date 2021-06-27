package net.corda.example.states


import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.example.contracts.FxContract

@BelongsToContract(FxContract::class)
data class FXState(val curA: String ,
                   val curB: String ,
                   val fxrate: Double,
                   val requester: AbstractParty
                   ): ContractState {
    override val participants: List<AbstractParty> get() = listOf(requester)
    override fun toString() = "The <$curA : $curB> exchange rate is $fxrate."

                   }
