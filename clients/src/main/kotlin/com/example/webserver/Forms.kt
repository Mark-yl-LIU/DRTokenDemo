package com.example.webserver

import java.math.BigDecimal

class Forms {

    class BidForm {
        var auctionId: String? = null
        var amount: Int = 0
    }

    class SettlementForm {
        var auctionId: String? = null
        var amount: String? = null
    }

    class CreateAuctionForm {
        var basePrice: Int = 0
        var assetId: String? = null
        var deadline: String? = null
    }

    class IssueCashForm {
        var currency: String = "GBP"
        var amount: Long = 0
        var party: String? = null
    }

    class AssetForm {
        var imageUrl: String? = null
        var title: String? = null
        var description: String? = null
    }

    class DRTokenForm {
        var drbroker: String? = null
        var localBroker: String? = null
        var drbank: String? = null
        var localBank: String? = null
        var ordShareSymbol: String? = null
        var ordShareRate: Long = 0
        var quantity : Long = 0
    }

    class ShareForm {
        var symbo_ISIN: String? = null
        var amount: BigDecimal = BigDecimal(0.0)
        var currency: String? = null
        var quantity: Long = 0
    }


}
