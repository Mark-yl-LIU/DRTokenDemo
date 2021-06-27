package net.corda.example.services.oracle


import net.corda.core.contracts.Command
import net.corda.core.crypto.TransactionSignature
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
//import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction
import net.corda.example.contracts.FxContract
import java.util.*


@CordaService
// For Oracle FX Rate Case
class FxRateOracle(val services: ServiceHub) : SingletonSerializeAsToken() {


    //PoC Only Support GBP and CNY
    //Product, should use Currency Type and Currency Pair class
    private val ccyList = listOf("GBP","CNY")

    //Default FX Rate for GBP and CNY
    val fxpair = mapOf("GBP:CNY" to 10.0,"CNY:GBP" to 0.1)

    private val myKey = services.myInfo.legalIdentities.first().owningKey

    // Returns the Currency Pair Exchange Rate from File / Not found.
    // Change to get the FX rate from Txt Files
    fun query(curPair: List<String>): Double {
        val curA = curPair[0]
        val curB = curPair[1]
        val ans = fxpair.getOrDefault(curA.plus(":").plus(curB),0.0)
        if (ans ==0.0)
        {
            throw IllegalArgumentException ("FXOracle in PoC just support GBP and CNY.")
        }
        else
        {
            return ans as Double
        }
    }


    // Signs over a transaction if the specified Nth prime for a particular N is correct.
    // This function takes a filtered transaction which is a partial Merkle tree. Any parts of the transaction which
    // the oracle doesn't need to see in order to verify the correctness of the nth prime have been removed. In this
    // case, all but the [PrimeContract.Create] commands have been removed. If the Nth prime is correct then the oracle
    // signs over the Merkle root (the hash) of the transaction.
    fun sign(ftx: FilteredTransaction): TransactionSignature {
        // Check the partial Merkle tree is valid.
        ftx.verify()

        /** Returns true if the component is an Create command that:
         *  - States the correct prime
         *  - Has the oracle listed as a signer
         */
        fun isCommandWithCorrectPrimeAndIAmSigner(elem: Any) = when {
            elem is Command<*> && elem.value is FxContract.Create -> {
                val cmdData = elem.value as FxContract.Create
                myKey in elem.signers && query(listOf(cmdData.CurA,cmdData.CurB)) == cmdData.FX_rate
            }
            else -> false
        }

        // Is it a Merkle tree we are willing to sign over?
        val isValidMerkleTree = ftx.checkWithFun(::isCommandWithCorrectPrimeAndIAmSigner)

        /**
         * Function that checks if all of the commands that should be signed by the input public key are visible.
         * This functionality is required from Oracles to check that all of the commands they should sign are visible.
         */
        ftx.checkCommandVisibility(services.myInfo.legalIdentities.first().owningKey);

        if (isValidMerkleTree) {
            return services.createSignature(ftx, myKey)
        } else {
            throw IllegalArgumentException("Oracle signature requested over invalid transaction.")
        }
    }




}