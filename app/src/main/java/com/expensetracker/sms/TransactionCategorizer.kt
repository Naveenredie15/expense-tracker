package com.expensetracker.sms

import com.expensetracker.data.entity.TransactionType

/**
 * Rule-based, on-device categorizer (no AI model). English only.
 *
 * To tune categories, just edit [categoryKeywords]: each category maps to a list
 * of keywords. Matching is case-insensitive and checks both the merchant/payee
 * and the full SMS text. The FIRST category with a matching keyword wins, so more
 * specific categories are listed earlier.
 */
class TransactionCategorizer {

    companion object {
        // category -> keywords. Order matters: first match wins.
        private val categoryKeywords: Map<String, List<String>> = linkedMapOf(
            "Food & Dining" to listOf(
                "ZOMATO", "SWIGGY", "DOMINOS", "PIZZA", "MCDONALD", "KFC",
                "BURGER KING", "STARBUCKS", "CAFE", "COFFEE DAY", "CCD",
                "RESTAURANT", "BIRYANI", "BAKERY", "FOOD", "EAT", "DHABA",
                "CHICKEN", "MEAT", "DINE"
            ),
            "Groceries" to listOf(
                "BIG BASKET", "BIGBASKET", "GROFERS", "BLINKIT", "ZEPTO", "DUNZO",
                "DMART", "D MART", "RELIANCE FRESH", "RELIANCE SMART", "SPENCER",
                "SUPERMARKET", "GROCERY", "KIRANA", "STORES",
                "PROVISION", "VEGETABLE", "MART"
            ),
            "Transport" to listOf(
                // Ride/travel
                "UBER", "OLA", "RAPIDO", "AUTO", "TAXI", "CAB", "METRO", "BUS",
                "RAILWAY", "IRCTC", "TRAIN", "FLIGHT", "AIRLINE", "INDIGO",
                "SPICEJET", "AIR INDIA", "TOLL", "FASTAG", "PARKING",
                // Fuel (merged in)
                "PETROL", "DIESEL", "FUEL", "PUMP", "BHARAT PETROLEUM",
                "BPCL", "INDIAN OIL", "IOCL", "HP PETROL", "HPCL", "SHELL",
                "FILLING STATION", "GAS STATION"
            ),
            "Shopping" to listOf(
                "AMAZON", "FLIPKART", "MYNTRA", "AJIO", "NYKAA", "MEESHO",
                "SNAPDEAL", "TATA CLIQ", "RELIANCE RE", "RELIANCE TRENDS",
                "SHOPPING", "MALL", "STORE", "LIFESTYLE", "MAX", "DECATHLON",
                "PURCHASE", "RETAIL"
            ),
            "Entertainment" to listOf(
                "NETFLIX", "PRIME VIDEO", "AMAZON PRIME", "HOTSTAR", "JIOCINEMA",
                "SPOTIFY", "GAANA", "WYNK", "YOUTUBE", "CINEMA", "PVR", "INOX",
                "MOVIE", "THEATER", "BOOKMYSHOW", "GAMING", "GAMES"
            ),
            "Bills & Utilities" to listOf(
                "ELECTRICITY", "POWER", "WATER", "INTERNET", "BROADBAND",
                "AIRTEL", "JIO", "VI ", "VODAFONE", "BSNL", "RECHARGE", "DTH",
                "TATA SKY", "CABLE", "GAS BILL", "LPG", "BILL", "EMI", "LOAN",
                "RENT", "MAINTENANCE"
            ),
            "Healthcare" to listOf(
                "HOSPITAL", "CLINIC", "DOCTOR", "PHARMACY", "MEDICAL", "MEDICINE",
                "APOLLO", "FORTIS", "MEDPLUS", "PHARMEASY", "1MG", "NETMEDS",
                "DIAGNOSTIC", "LAB", "HEALTHCARE"
            ),
            "Education" to listOf(
                "SCHOOL", "COLLEGE", "UNIVERSITY", "COURSE", "TUITION",
                "EDUCATION", "EXAM", "COACHING", "UDEMY", "COURSERA", "BYJU",
                "UNACADEMY", "FEES"
            ),
            "Investment" to listOf(
                "MUTUAL FUND", "SIP", "STOCK", "TRADING", "ZERODHA", "GROWW",
                "UPSTOX", "INVESTMENT", "INSURANCE", "LIC", "PPF", "FD ",
                "DIVIDEND", "INTEREST"
            )
        )

        // Keywords that indicate transfers between people/accounts.
        private val transferKeywords = listOf(
            "transfer", "transferred", "fund transfer", "account transfer",
            "self", "own account", "wallet", "load", "top up", "imps", "neft", "rtgs"
        )
    }

    /**
     * @param hasUpiReference true when the SMS carried a UPI RRN. A UPI debit to a named
     *        payee that matches no merchant keyword is money moved to another person/account,
     *        so it's bucketed under "Transfer".
     */
    fun categorizeTransaction(
        payee: String?,
        message: String,
        transactionType: TransactionType,
        hasUpiReference: Boolean = false
    ): String? {
        val messageUpper = message.uppercase()
        val payeeUpper = payee?.uppercase() ?: ""
        fun matches(keyword: String) =
            messageUpper.contains(keyword) || payeeUpper.contains(keyword)

        // Income-specific handling (CREDIT only). Salary/plain income is left
        // uncategorized (no "Salary" category); refunds keep the internal "Refund" tag
        // (used to net against spend, not shown as a pickable category).
        if (transactionType == TransactionType.CREDIT) {
            when {
                matches("DIVIDEND") || matches("INTEREST") -> return "Investment"
                matches("REFUND") || matches("REVERSAL") -> return "Refund"
            }
        }

        // Merchant/keyword categories (first match wins).
        for ((category, keywords) in categoryKeywords) {
            if (keywords.any { matches(it) }) return category
        }

        // Self/bank transfers (self, wallet load, IMPS/NEFT/RTGS) — checked after
        // merchants so a known merchant wins.
        if (transferKeywords.any { matches(it.uppercase()) }) return "Transfer"

        // Person-to-person UPI transfer: a UPI debit to a named payee with no merchant
        // match → money moved to another person/account → "Transfer".
        if (transactionType == TransactionType.DEBIT && hasUpiReference && !payee.isNullOrBlank()) {
            return "Transfer"
        }

        // Unknown / ATM / cash -> leave uncategorized (user can categorize manually).
        return null
    }
}
