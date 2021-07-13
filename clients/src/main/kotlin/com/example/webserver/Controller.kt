package com.example.webserver

import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.identity.CordaX500Name
import net.corda.finance.workflows.getCashBalance
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.*
import java.util.*

//Corda pack
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.utilities.getOrThrow
import net.corda.example.flows.*
import net.corda.example.states.DRTokenState
import net.corda.example.states.FXState
import net.corda.example.states.ShareState
import net.corda.finance.contracts.asset.Cash
import java.math.BigDecimal


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
    fun getAssetList() : APIResponse<List<StateAndRef<ContractState>>> {
        return APIResponse.success(proxy.vaultQuery(FungibleToken::class.java).states)
    }

    @GetMapping(value = [ "asset/list/sharestate" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getShareList() : APIResponse<List<StateAndRef<ShareState>>> {
        return APIResponse.success(proxy.vaultQuery(ShareState::class.java).states)
    }

    @GetMapping(value = [ "asset/list/drtoken" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getDRList() : APIResponse<List<StateAndRef<DRTokenState>>> {
        return APIResponse.success(proxy.vaultQuery(DRTokenState::class.java).states)
    }



    @GetMapping(value = [ "peers" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = proxy.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
            .map { it.legalIdentities.first().name }
            //filter out myself, notary and eventual network map started by driver
            )
    }


    @GetMapping(value = ["cashall"], produces = [ APPLICATION_JSON_VALUE ])
    fun getFiantCurrencies() : APIResponse<List<StateAndRef<ContractState>>>{
        return APIResponse.success(proxy.vaultQuery(FungibleToken::class.java).states)
    }


    @PostMapping(value = ["issueCash"])
    fun issueCash(@RequestBody issueCashForm: Forms.IssueCashForm): APIResponse<String> {
        return try {
            Oracle_FXProxy.startFlowDynamic(
                    FiatCurrencyIssueFlow::class.java,
                    issueCashForm.currency,
                    issueCashForm.amount,
                    proxy.partiesFromName(issueCashForm.party!!, false).iterator().next()
            ).returnValue.get()
            APIResponse.success("Cash issued. Amount: ${issueCashForm.amount}")
        } catch (e: Exception) {
            handleError(e)
        }
    }


    @PostMapping(value = ["issueShare"])
    fun issueCash(@RequestBody issueShareForm: Forms.ShareForm): APIResponse<String> {
        val shareprice = Amount.parseCurrency(issueShareForm.amount.toString().plus(" ").plus(issueShareForm.currency))
        val currentNode = proxy.partiesFromName("Oracle_Stock", false).iterator().next()
        return try {
            Orcale_StockProxy.startFlowDynamic(
                CreateAndIssueStock::class.java,
                issueShareForm.symbo_ISIN,
                shareprice,
                issueShareForm.quantity,
                currentNode
            ).returnValue.get()
            APIResponse.success("Share  issued. Amount: ${issueShareForm.amount}")
        } catch (e: Exception) {
            handleError(e)
        }
    }


    @PostMapping(value = ["buyDR"])
    fun buyDR(@RequestBody drTokenForm: Forms.DRTokenForm): APIResponse<String> {
        // Get the total Price Value
        var ansString: String

        val totalshare = drTokenForm.ordShareRate * drTokenForm.quantity

        return try {

            // Get FX Rate
            val fxpairratetx = Oracle_FXProxy.startFlowDynamic(
                GetFXPairRate::class.java,
                "GBP",
                "CNY"
            ).returnValue.get()
            val fxpairrate = fxpairratetx.tx.outputsOfType<FXState>().single().fxrate

            // Get Share Price

            val shareprice= Amount.parseCurrency("64.73 CNY")

            // Calculate the total Pricing
            // Total CNY
            val totalamount = shareprice.quantity * totalshare
            val amountIncny = Amount(totalamount.toLong(),Currency.getInstance("CNY"))

            // Total GBP
            val priceingbp = totalamount / fxpairrate
            val amountInPrice = Amount(priceingbp.toLong(),Currency.getInstance("GBP"))


            // 1. Send the Amount from Investor into DR Broker
            val step1resu = InvestorProxy.startFlowDynamic(
                MoveCashFlow::class.java,
                amountInPrice,
                proxy.partiesFromName(drTokenForm.drbroker!!, false).iterator().next()
            ).returnValue.get()
            println(step1resu)

            //2. Get exchange between DR Broker and FX Market

            val step2resu = DR_BrokerProxy.startFlowDynamic(
                FXexchange::class.java,
                proxy.partiesFromName("Oracle_FX", false).iterator().next(),
                proxy.partiesFromName(drTokenForm.drbroker!!, false).iterator().next(),
                proxy.partiesFromName("Oracle_FX", false).iterator().next(),
                amountInPrice,
                "CNY"
            ).returnValue.get()
            println(step2resu)

            //3. Send Instruction to Local Broker with Money

            val step3resu = DR_BrokerProxy.startFlowDynamic(
                MoveCashFlow::class.java,
                amountIncny,
                proxy.partiesFromName("Broker2", false).iterator().next()
            ).returnValue.get()
            println(step3resu)

            //4. Buy Share into Local Market by Local Broker

//            val step4resu = Orcale_StockProxy.startFlowDynamic(
//                BuyShare::class.java,
//                proxy.partiesFromName("Broker2", false).iterator().next(),
//                proxy.partiesFromName("Oracle_Stock", false).iterator().next(),
//                drTokenForm.ordShareSymbol!!,
//                totalshare
//            ).returnValue.get()
            val step4resu1= Local_BrokerProxy.startFlowDynamic(
                MoveCashFlow::class.java,
                amountIncny,
                proxy.partiesFromName("Oracle_Stock", false).iterator().next()
            ).returnValue.get()
            val step4resu2= Orcale_StockProxy.startFlowDynamic(
                MoveShareFlow::class.java,
                drTokenForm.ordShareSymbol,
                proxy.partiesFromName("Broker2", false).iterator().next(),
                totalshare
            ).returnValue.get()
            println(step4resu2)

            //5. Move Share to Custodian
            val step5resu = Local_BrokerProxy.startFlowDynamic(
                MoveShareFlow::class.java,
                drTokenForm.ordShareSymbol,
                proxy.partiesFromName("Custody", false).iterator().next(),
                totalshare
            ).returnValue.get()
            println(step5resu)

            //6. Raise the DR Token
//            val step6resu = Depository_BankProxy.startFlowDynamic(
//                BuyDRToken::class.java,
//                proxy.partiesFromName(drTokenForm.drbroker.toString(), false).iterator().next(),
//                proxy.partiesFromName("Broker2", false).iterator().next(),
//                proxy.partiesFromName("Depositary", false).iterator().next(),
//                proxy.partiesFromName("Oracle_FX", false).iterator().next(),
//                proxy.partiesFromName("Oracle_Stock", false).iterator().next(),
//                drTokenForm.ordShareSymbol,
//                drTokenForm.ordShareRate,
//                drTokenForm.quantity
//            ).returnValue.get()
//            println(step6resu)

            //7. Move the DR Token to investor
            val step7resu = Depository_BankProxy.startFlowDynamic(
                MoveDRFlow::class.java,
                drTokenForm.ordShareSymbol,
                proxy.partiesFromName("Investor", false).iterator().next(),
                drTokenForm.quantity
            ).returnValue.get()
            println(step7resu)

            val sharepricestr = Orcale_StockProxy.startFlowDynamic(
                GetSharePrice::class.java,
                drTokenForm.ordShareSymbol
            ).returnValue.get()
            val resustr1 = sharepricestr.indexOf("updated as ")

            APIResponse.success("DR Token Issued and sent in Investor")
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
        return APIResponse.success("Party Switched")
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
