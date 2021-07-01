package com.example.webserver

import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.client.jackson.JacksonSupport
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
open class AppConfig : WebMvcConfigurer{

    @Value("\${Investor.host}")
    lateinit var InvestorHostAndPort: String

    @Value("\${DR_Broker.host}")
    lateinit var DR_BrokerHostAndPort: String

    @Value("\${Local_Broker.host}")
    lateinit var Local_BrokerHostAndPort: String

    @Value("\${Custody_Bank.host}")
    lateinit var Custody_BankHostAndPort: String

    @Value("\${Depository_Bank.host}")
    lateinit var Depository_BankHostAndPort: String

    @Value("\${Oracle_FX.host}")
    lateinit var Oracle_FXHostAndPort: String

    @Value("\${Orcale_Stock.host}")
    lateinit var Orcale_StockHostAndPort: String

    @Bean(destroyMethod = "")
    open fun InvestorProxy(): CordaRPCOps {
        val InvestorClient = CordaRPCClient(NetworkHostAndPort.parse(InvestorHostAndPort))
        return InvestorClient.start("user1", "test").proxy
    }

    @Bean(destroyMethod = "")
    open fun DR_BrokerProxy(): CordaRPCOps {
        val DR_BrokerClient = CordaRPCClient(NetworkHostAndPort.parse(DR_BrokerHostAndPort))
        return DR_BrokerClient.start("user1", "test").proxy
    }

    @Bean(destroyMethod = "")
    open fun Local_BrokerProxy(): CordaRPCOps {
        val Local_BrokerClient = CordaRPCClient(NetworkHostAndPort.parse(Local_BrokerHostAndPort))
        return Local_BrokerClient.start("user1", "test").proxy
    }

    @Bean(destroyMethod = "")
    open fun Custody_BankProxy(): CordaRPCOps {
        val Custody_BankClient = CordaRPCClient(NetworkHostAndPort.parse(Custody_BankHostAndPort))
        return Custody_BankClient.start("user1", "test").proxy
    }

    @Bean(destroyMethod = "")
    open fun Depository_BankProxy(): CordaRPCOps {
        val Depository_BankClient = CordaRPCClient(NetworkHostAndPort.parse(Depository_BankHostAndPort))
        return Depository_BankClient.start("user1", "test").proxy
    }

    @Bean(destroyMethod = "")
    open fun Oracle_FXProxy(): CordaRPCOps {
        val Oracle_FXClient = CordaRPCClient(NetworkHostAndPort.parse(Oracle_FXHostAndPort))
        return Oracle_FXClient.start("user1", "test").proxy
    }

    @Bean(destroyMethod = "")
    open fun Orcale_StockProxy(): CordaRPCOps {
        val Orcale_StockClient = CordaRPCClient(NetworkHostAndPort.parse(Orcale_StockHostAndPort))
        return Orcale_StockClient.start("user1", "test").proxy
    }

    /**
     * Corda Jackson Support, to convert corda objects to json
     */
    @Bean
    open fun mappingJackson2HttpMessageConverter(): MappingJackson2HttpMessageConverter {
        val mapper = JacksonSupport.createDefaultMapper(InvestorProxy())
        mapper.registerModule(KotlinModule())
        val converter = MappingJackson2HttpMessageConverter()
        converter.objectMapper = mapper
        return converter
    }
}
