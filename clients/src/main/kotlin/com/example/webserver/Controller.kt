package com.example.webserver

import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import net.corda.example.flows.*
import net.corda.example.states.*

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType

//Corda Main
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import net.corda.finance.workflows.getCashBalance
import net.corda.finance.contracts.asset.Cash

val SERVICE_NAMES = listOf("Notary", "Network Map Service")

/**
 * Define your API endpoints here.
 */
@RestController
@RequestMapping("/api/example/") // The paths for HTTP requests are relative to this base path.
class Controller(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val myLegalName = rpc.proxy.nodeInfo().legalIdentities.first().name
    private val proxy = rpc.proxy

    @GetMapping(value = [ "me" ], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun whoami() = mapOf("me" to myLegalName)

    @GetMapping(value = [ "peers" ], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = proxy.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
            .map { it.legalIdentities.first().name }
            //filter out myself, notary and eventual network map started by driver
            .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    @GetMapping(value = [ "money" ], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getAccount() : ResponseEntity<List<StateAndRef<net.corda.finance.contracts.asset.Cash.State>>> {
        return ResponseEntity.ok(proxy.vaultQueryBy<Cash.State>().states)
    }

    @GetMapping(value = [ "holding" ], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getHolding(): ResponseEntity<List<StateAndRef<EvolvableTokenType>>>  {
        val myholding = proxy.vaultQueryBy<EvolvableTokenType>().states
        return ResponseEntity.ok(myholding)
    }




    @GetMapping(value = ["/templateendpoint"], produces = ["text/plain"])
    private fun templateendpoint(): String {
        return "Define an endpoint here."
    }
}