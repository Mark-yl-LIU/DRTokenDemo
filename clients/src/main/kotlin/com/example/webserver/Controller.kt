package com.example.webserver

import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.workflows.getCashBalance
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

//Corda pack
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.example.flows.*
import net.corda.example.states.*


@RestController
@RequestMapping("/api/example") // The paths for HTTP requests are relative to this base path.
class Controller() {

    @Autowired lateinit var InvestorProxy: CordaRPCOps

    @Autowired lateinit var DR_BrokerProxy: CordaRPCOps

    @Autowired lateinit var Local_BrokerProxy: CordaRPCOps

    @Autowired lateinit var Custody_BankProxy: CordaRPCOps

    @Autowired lateinit var Depository_BankProxy: CordaRPCOps

    @Autowired lateinit var Oracle_FXProxy: CordaRPCOps

    @Autowired lateinit var Orcale_StockProxy: CordaRPCOps

    @Autowired
    @Qualifier("InvestorProxy")
    lateinit var proxy: CordaRPCOps

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    @GetMapping(value = [ "asset/list" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getAssetList() : APIResponse<List<StateAndRef<EvolvableTokenType>>> {
        return APIResponse.success(proxy.vaultQuery(EvolvableTokenType::class.java).states)
    }

    @PostMapping(value = ["asset/create"])
    fun createAsset(@RequestBody assetForm: Forms.AssetForm): APIResponse<String> {
//        return try {
//            proxy.startFlowDynamic(
//                    CreateAssetFlow::class.java,
//                    assetForm.title,
//                    assetForm.description,
//                    assetForm.imageUrl
//            ).returnValue.get()
//
            return  APIResponse.success("Account ${assetForm.title} Created")
//        } catch (e: Exception) {
//            handleError(e)
//        }
    }

    @PostMapping(value = ["create"])
    fun createAuction(@RequestBody auctionForm: Forms.CreateAuctionForm): APIResponse<String> {
        return try {
//            proxy.startFlowDynamic(
//                    CreateAuctionFlow::class.java,
//                    Amount.parseCurrency("${auctionForm.basePrice} USD"),
//                    UUID.fromString(auctionForm.assetId),
//                    LocalDateTime.parse(auctionForm.deadline, DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm:ss a"))
//            ).returnValue.get()
            APIResponse.success("Auction ${auctionForm.assetId} Created")
        } catch (e: Exception) {
            handleError(e)
        }
    }

    @PostMapping(value = ["issueCash"])
    fun issueCash(@RequestBody issueCashForm: Forms.IssueCashForm): APIResponse<String> {
        return try {
            proxy.startFlowDynamic(
                    CashIssueAndPaymentFlow::class.java,
                    Amount.parseCurrency("${issueCashForm.amount} USD"),
                    OpaqueBytes("PartyA".toByteArray()),
                    proxy.partiesFromName(issueCashForm.party!!, false).iterator().next(),
                    false,
                    proxy.notaryIdentities().firstOrNull()
            ).returnValue.get()
            APIResponse.success("Cash issued. Amount: ${issueCashForm.amount}")
        } catch (e: Exception) {
            handleError(e)
        }
    }

    @GetMapping(value = [ "getCashBalance" ])
    fun getCashBalance(): APIResponse<String> {
        return try {
            var amount = proxy.getCashBalance(Currency.getInstance("")).quantity

            if(amount >= 100L)
                amount /= 100L

            APIResponse.success("Balance: $amount")
        } catch (e: Exception) {
            handleError(e)
        }
    }

    @PostMapping(value = ["switch-party/{party}"])
    fun switchParty(@PathVariable party:String): APIResponse<String> {
        when (party) {
            "Investor"-> proxy = InvestorProxy
            "DR_Broker"-> proxy = DR_BrokerProxy
            "Local_Broker"-> proxy = Local_BrokerProxy
            "Custody_Bank"-> proxy = Custody_BankProxy
            "Depository_Bank"-> proxy = Depository_BankProxy
            "Oracle_FX"-> proxy = Oracle_FXProxy
            "Orcale_Stock"-> proxy = Orcale_StockProxy
            else -> return APIResponse.error("Unrecognised Party")
        }
        return getCashBalance()
    }

    private fun handleError(e: Exception): APIResponse<String> {
        logger.error("RequestError", e)
        return when (e) {
            is TransactionVerificationException.ContractRejection ->
                APIResponse.error(e.cause?.message ?: e.message!!)
            else ->
                APIResponse.error(e.message!!)
        }
    }
}
