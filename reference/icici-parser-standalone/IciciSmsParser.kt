package com.expensetracker.parser

/**
 * Pure-Kotlin ICICI SMS parser. No Android dependencies, so it runs on the JVM
 * and is trivially unit-testable. Wrap it in a BroadcastReceiver later.
 *
 * Scope (for now):
 *   - ICICI credit card spends
 *   - ICICI bank account UPI debits (money out)
 *   - ICICI bank account UPI credits (money in)
 *
 * Everything else returns null (ignored). Add patterns as you find new formats.
 */

enum class TxnType { DEBIT, CREDIT }

enum class TxnSource { CREDIT_CARD, UPI, ACCOUNT }

data class ParsedTxn(
    val type: TxnType,
    val source: TxnSource,
    /** Amount in rupees. 1234.00 -> 1234.00 */
    val amount: Double,
    /** Merchant / counterparty as printed, best-effort. May be null. */
    val merchant: String?,
    /** Last 3-4 digits of card or account, e.g. "7003" / "898". */
    val instrument: String?,
    /** Raw date token as printed, e.g. "05-Sep-25". Parse to LocalDate on Android. */
    val dateToken: String?,
    /** UPI reference / RRN if present. */
    val refId: String?,
    val rawBody: String,
)

object IciciSmsParser {

    /** Accept only messages from ICICI senders. On Android, pass the SMS sender/header here. */
    fun isIciciSender(sender: String): Boolean {
        val s = sender.uppercase()
        // DLT headers look like "VM-ICICIB", "AD-ICICIT", "JD-ICICIBI", etc.
        return s.contains("ICICIB") || s.contains("ICICIT") || s.contains("ICICI")
    }

    // ---- Reusable fragments -------------------------------------------------

    // Amount like "1,234.00" or "1,50,000.00" or "100" -> captured without commas later.
    private const val AMT = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    // Date like "05-Sep-25" / "05-SEP-2025" / "05-09-25"
    private const val DATE = """(\d{1,2}[-/][A-Za-z0-9]{2,3}[-/]\d{2,4})"""

    // ---- Patterns -----------------------------------------------------------

    // Credit card spend:
    // "INR 1,234.00 spent using ICICI Bank Card XX7003 on 05-Sep-25 on Amazon."
    // also: "... spent on ICICI Bank Card XX7003 ..." and "Spends of INR ..."
    private val CC_SPEND = Regex(
        """INR\s+$AMT\s+spent\s+(?:using|on)\s+ICICI\s+Bank\s+Card\s+(?:XX|xx)?(\d{3,4})\s+on\s+$DATE\s+(?:at|on)\s+([^.]+?)\.""",
        RegexOption.IGNORE_CASE,
    )

    // UPI / account debit:
    // "ICICI Bank Acct XX898 debited for Rs 100.00 on 05-Sep-25; Swiggy credited. UPI:512345678901."
    private val ACCT_DEBIT = Regex(
        """ICICI\s+Bank\s+Acct?\s+(?:XX|xx)?(\d{3,4})\s+debited\s+for\s+Rs\.?\s*$AMT\s+on\s+$DATE(?:;\s*([^.;]+?)\s+credited)?""",
        RegexOption.IGNORE_CASE,
    )

    // UPI / account credit:
    // "ICICI Bank Account XX898 credited:Rs 500.00 on 05-Sep-25. Info:UPI/512345678901/Payment."
    private val ACCT_CREDIT = Regex(
        """ICICI\s+Bank\s+Acc(?:t|ount)?\s+(?:XX|xx)?(\d{3,4})\s+credited[:\s]*Rs\.?\s*$AMT\s+on\s+$DATE""",
        RegexOption.IGNORE_CASE,
    )

    // UPI reference number, extracted separately so an optional trailing group
    // doesn't get swallowed by a preceding .*? (it always prefers matching nothing).
    private val UPI_REF = Regex("""UPI[/:\s]*(\d{9,})""", RegexOption.IGNORE_CASE)

    fun parse(body: String): ParsedTxn? {
        val text = body.trim().replace(Regex("""\s+"""), " ")

        CC_SPEND.find(text)?.let { m ->
            return ParsedTxn(
                type = TxnType.DEBIT,
                source = TxnSource.CREDIT_CARD,
                amount = toAmount(m.groupValues[1]),
                merchant = m.groupValues.getOrNull(4)?.trim()?.ifBlank { null },
                instrument = m.groupValues[2],
                dateToken = m.groupValues[3],
                refId = null,
                rawBody = body,
            )
        }

        ACCT_DEBIT.find(text)?.let { m ->
            return ParsedTxn(
                type = TxnType.DEBIT,
                source = TxnSource.UPI,
                amount = toAmount(m.groupValues[2]),
                merchant = m.groupValues.getOrNull(4)?.trim()?.ifBlank { null },
                instrument = m.groupValues[1],
                dateToken = m.groupValues[3],
                refId = UPI_REF.find(text)?.groupValues?.get(1),
                rawBody = body,
            )
        }

        ACCT_CREDIT.find(text)?.let { m ->
            return ParsedTxn(
                type = TxnType.CREDIT,
                source = TxnSource.UPI,
                amount = toAmount(m.groupValues[2]),
                merchant = null,
                instrument = m.groupValues[1],
                dateToken = m.groupValues[3],
                refId = UPI_REF.find(text)?.groupValues?.get(1),
                rawBody = body,
            )
        }

        return null
    }

    private fun toAmount(raw: String): Double =
        raw.replace(",", "").toDouble()
}
