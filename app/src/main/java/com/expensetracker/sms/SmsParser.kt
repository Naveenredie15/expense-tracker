package com.expensetracker.sms

import com.expensetracker.data.entity.Account
import com.expensetracker.data.entity.AccountType
import com.expensetracker.data.entity.SmsTemplate
import com.expensetracker.data.entity.Transaction
import com.expensetracker.data.entity.TransactionType
import kotlinx.datetime.Instant

/**
 * On-device ICICI SMS parser (no AI). Three layers, in order:
 *   1. Noise filter — drop OTP / promo / statement / reminder / failed SMS.
 *   2. Built-in ICICI extractors — recognize the bank's known formats automatically
 *      (card spend, UPI debit, account credit, ATM, card-bill payment), plus a
 *      generic fallback for any amount+verb SMS on a REGISTERED account.
 *   3. User templates — optional per-account overrides for unusual wordings.
 *
 * Only genuine transaction SMS the engine can't read reach the "unrecognized" inbox.
 *
 * @param userTemplates optional user-tagged formats (tried before the generic fallback).
 * @param accounts registered accounts; a txn is attributed by the last-4 in the SMS.
 * @param payeeCategories learned payee→category overrides re-applied on every sync.
 */
class SmsParser(
    private val userTemplates: List<SmsTemplate> = emptyList(),
    private val accounts: List<Account> = emptyList(),
    private val payeeCategories: Map<String, String> = emptyMap(),
    /** Matchers for recurring expenses (linked by the user); a matched debit → "Recurring". */
    private val recurringMatchers: List<RecurringMatcher> = emptyList()
) {

    /** Matches a recurring expense: same amount AND (payee contains OR regex matches). */
    data class RecurringMatcher(val amount: Double, val payeeUpper: String?, val regex: Regex?)

    private val categorizer = TransactionCategorizer()

    private val last4Regex = Regex("""[Xx*]{2,}(\d{3,4})""")
    private val upiRefRegex = Regex("""UPI[:/\s]*(\d{9,})""", RegexOption.IGNORE_CASE)
    private val refundRegex = Regex("""refund|reversal|reversed|refunded""", RegexOption.IGNORE_CASE)
    // A credit-card bill payment (money paid TO a card) — never income, even if the
    // card isn't a registered account. e.g. "Payment of Rs X received on … Credit Card …".
    private val cardBillPaymentRegex = Regex(
        """(?:payment\s+of|received).*credit\s*card|credit\s*card.*(?:payment\s+received|received)""",
        RegexOption.IGNORE_CASE
    )
    private val amountRegex = Regex("""(?:Rs|INR|₹)\.?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    private val debitVerbs = Regex("""debited|spent|withdrawn|purchase|deducted|paid""", RegexOption.IGNORE_CASE)
    private val creditVerbs = Regex("""credited|received|deposited|refunded|reversed|reversal""", RegexOption.IGNORE_CASE)

    // A transaction that did NOT happen — never record or surface these. Includes
    // negations ("not debited/credited") and bank rejections (e.g. failed autopay).
    private val failedRegex = Regex(
        """failed|declined|unsuccessful|reject(?:ed|ion)|not\s+(?:been\s+)?(?:debited|credited|successful)|could\s+not\s+be\s+(?:completed|processed)|has\s+been\s+cancelled""",
        RegexOption.IGNORE_CASE
    )

    // Non-transaction SMS (info/marketing/reminders). Used to keep junk out of the
    // "unrecognized" inbox. Refund/reversal is deliberately NOT here — it's a real event.
    private val noiseRegexes = listOf(
        failedRegex,
        Regex("""\botp\b|one[\s-]?time\s?password|do not share|verification code|\bcvv\b""", RegexOption.IGNORE_CASE),
        Regex("""statement\s+(?:is\s+)?(?:generated|ready|will\s+be|has\s+been|sent)|e-?statement|mini\s+statement""", RegexOption.IGNORE_CASE),
        Regex("""(?:total|minimum)\s+amount\s+due|payment\s+.*\bdue\b|due\s+date|overdue|payment\s+reminder|kindly\s+pay|please\s+pay""", RegexOption.IGNORE_CASE),
        Regex("""will\s+be\s+(?:debited|deducted)|is\s+due\s+for|upcoming|scheduled\s+for""", RegexOption.IGNORE_CASE),
        Regex("""requested\s+money|collect\s+request|payment\s+request|has\s+requested""", RegexOption.IGNORE_CASE),
        Regex("""https?://|www\.|bit\.ly|\boffer\b|apply\s+now|\bavail\b|pre-?approved|instant\s+loan|reward\s+points|congratulations""", RegexOption.IGNORE_CASE),
        // Limit-increase / spend-management marketing.
        Regex("""increas(?:e|ing)\s+(?:the\s+)?limit|raise\s+(?:the\s+)?limit|enhanc\w*\s+(?:the\s+)?limit|\bCRLIM\b|manage\s+spends""", RegexOption.IGNORE_CASE)
    )

    /** A built-in recognizer for one ICICI message shape. */
    private data class Extractor(
        val regex: Regex,
        val type: TransactionType,
        val amountGroup: Int,
        val merchantGroup: Int? = null
    )

    private val extractors = listOf(
        // Card spend: "INR 500.00 spent using ICICI Bank Card XX111 on 01-Jan-25 on SAMPLE MERCHANT."
        Extractor(
            Regex("""(?:INR|Rs)\.?\s*([\d,]+\.?\d*)\s+spent\s+using\s+ICICI\s+Bank\s+Card\s+[Xx*]+\d{3,4}\s+on\s+[\dA-Za-z-]+\s+(?:at|on)\s+([^.]+?)(?:\.|$)""", RegexOption.IGNORE_CASE),
            TransactionType.DEBIT, amountGroup = 1, merchantGroup = 2
        ),
        // UPI / account debit: "ICICI Bank Acct XX000 debited for Rs 100.00 on ...; NAME credited. UPI:..."
        Extractor(
            Regex("""ICICI\s+Bank\s+Acc(?:t|ount)?\s+[Xx*]+\d{3,4}\s+debited\s+for\s+Rs\.?\s*([\d,]+\.?\d*).*?;\s*([^.;]+?)\s+credited""", RegexOption.IGNORE_CASE),
            TransactionType.DEBIT, amountGroup = 1, merchantGroup = 2
        ),
        // Card-bill payment received: "Payment of INR 10,000 received on ICICI Bank Credit Card XX111"
        Extractor(
            Regex("""Payment\s+of\s+(?:INR|Rs)\.?\s*([\d,]+\.?\d*)\s+(?:has\s+been\s+)?received.*?Card\s+[Xx*]+\d{3,4}""", RegexOption.IGNORE_CASE),
            TransactionType.CREDIT, amountGroup = 1
        ),
        // Account credit: "ICICI Bank Account XX000 credited:Rs. 50,000.00 on ..."
        Extractor(
            Regex("""ICICI\s+Bank\s+Acc(?:t|ount)?\s+[Xx*]+\d{3,4}\s+credited[:\s]*(?:with\s*)?Rs\.?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
            TransactionType.CREDIT, amountGroup = 1
        ),
        // Generic ICICI credit: "Rs 50,000 credited to your Account XX000 ..."
        Extractor(
            Regex("""(?:INR|Rs)\.?\s*([\d,]+\.?\d*)\s+credited(?:\s+to)?.*?[Xx*]+\d{3,4}""", RegexOption.IGNORE_CASE),
            TransactionType.CREDIT, amountGroup = 1
        ),
        // ATM / generic withdrawal: "Rs 2,000 withdrawn ..."
        Extractor(
            Regex("""(?:Rs|INR)\.?\s*([\d,]+\.?\d*)\s+(?:has\s+been\s+)?withdrawn""", RegexOption.IGNORE_CASE),
            TransactionType.DEBIT, amountGroup = 1
        )
    )

    /** True if this SMS is not a real completed transaction (OTP/promo/statement/failed…). */
    fun isNoise(message: String): Boolean = noiseRegexes.any { it.containsMatchIn(message) }

    fun parseTransaction(sender: String, message: String, timestamp: Long = System.currentTimeMillis()): Transaction? {
        // Layer 1: never record something that didn't happen — UNLESS it's a refund
        // (e.g. "transaction cancelled and Rs X refunded"), which IS a real money-back event.
        if (failedRegex.containsMatchIn(message) && !refundRegex.containsMatchIn(message)) return null

        val ts = Instant.fromEpochMilliseconds(timestamp)
        val referenceId = upiRefRegex.find(message)?.groupValues?.get(1)
        val last4 = last4Regex.find(message)?.groupValues?.get(1)
        val accountId = last4?.let { d -> accounts.firstOrNull { it.last4 == d }?.id }

        // Layer 2a: built-in ICICI extractors (best merchant + subtype).
        for (ex in extractors) {
            val m = ex.regex.find(message) ?: continue
            val amount = parseAmount(m.groupValues.getOrNull(ex.amountGroup) ?: continue)
            val merchant = ex.merchantGroup?.let { m.groupValues.getOrNull(it) }
            return build(sender, message, ts, amount, ex.type, merchant, accountId, referenceId)
        }

        // Layer 3: user templates (per-account overrides for unusual wordings).
        for (template in userTemplates) {
            if (!template.enabled) continue
            tryTemplate(template, sender, message, ts, referenceId)?.let { return it }
        }

        // Layer 2b: generic fallback — any amount+verb SMS on a REGISTERED account, OR a
        // refund/reversal even without a registered account (so it still nets expenses).
        val amount = amountRegex.find(message)?.groupValues?.get(1)?.let { parseAmount(it) }
        val type = detectType(message)
        if (amount != null && type != null) {
            val isRefundMsg = type == TransactionType.CREDIT && refundRegex.containsMatchIn(message)
            if (accountId != null || (isRefundMsg && !isNoise(message))) {
                return build(sender, message, ts, amount, type, null, accountId, referenceId)
            }
        }

        return null
    }

    /** Debit wins when both verbs appear (e.g. "…debited…; NAME credited"). */
    private fun detectType(message: String): TransactionType? = when {
        debitVerbs.containsMatchIn(message) -> TransactionType.DEBIT
        creditVerbs.containsMatchIn(message) -> TransactionType.CREDIT
        else -> null
    }

    private fun tryTemplate(
        template: SmsTemplate,
        sender: String,
        message: String,
        timestamp: Instant,
        referenceId: String?
    ): Transaction? {
        val match = try {
            Regex(template.regex, RegexOption.IGNORE_CASE).find(message)
        } catch (_: Exception) {
            null
        } ?: return null
        val amountStr = match.groups["amount"]?.value ?: return null
        val merchant = match.groups["merchant"]?.value
        return build(sender, message, timestamp, parseAmount(amountStr), template.type, merchant, template.accountId, referenceId)
    }

    /** Single place that classifies category / card-payment / refund and builds the row. */
    private fun build(
        sender: String,
        message: String,
        timestamp: Instant,
        amount: Double,
        type: TransactionType,
        merchant: String?,
        accountId: String?,
        referenceId: String?
    ): Transaction {
        val ownerIsCard = accounts.firstOrNull { it.id == accountId }?.type == AccountType.CARD
        // Refund/reversal takes precedence: a credit that says "refund/reversed" is money
        // BACK for a purchase → "Refund" (nets spend), NOT a bill payment. Only a non-refund
        // card credit is a bill payment (owner is a CARD, or the message says so). Both are
        // kept out of income.
        val isRefund = type == TransactionType.CREDIT && refundRegex.containsMatchIn(message)
        val isCardCredit = type == TransactionType.CREDIT && !isRefund &&
            (ownerIsCard || cardBillPaymentRegex.containsMatchIn(message))
        val payee = cleanPayeeName(merchant)
        val learned = payee?.let { payeeCategories[it.uppercase()] }
        // A debit that matches a user-linked recurring expense → "Recurring".
        val payeeUpper = payee?.uppercase()
        val isRecurring = type == TransactionType.DEBIT && recurringMatchers.any { m ->
            kotlin.math.abs(amount - m.amount) < 0.5 && (
                (m.payeeUpper != null && payeeUpper != null &&
                    (payeeUpper.contains(m.payeeUpper) || m.payeeUpper.contains(payeeUpper))) ||
                    (m.regex != null && m.regex.containsMatchIn(message))
                )
        }

        return Transaction(
            sender = sender,
            message = message,
            amount = amount,
            type = type,
            payee = payee,
            category = when {
                isRefund -> "Refund"
                isCardCredit -> "Card Payment"
                learned != null -> learned
                isRecurring -> "Recurring"
                else -> categorizer.categorizeTransaction(payee, message, type, hasUpiReference = referenceId != null)
            },
            timestamp = timestamp,
            referenceId = referenceId,
            accountId = accountId,
            excludeFromTotals = isCardCredit || isRefund
        )
    }

    private fun parseAmount(amountStr: String): Double =
        amountStr.replace(",", "").replace(" ", "").toDoubleOrNull() ?: 0.0

    private fun cleanPayeeName(payee: String?): String? {
        if (payee == null) return null
        val cleaned = payee
            .replace(Regex("""\b(UPI|NEFT|RTGS|IMPS|\d+)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[^a-zA-Z\s]"""), "")
            .trim()
            .replace(Regex("""\s+"""), " ")
        return if (cleaned.length >= 3) cleaned else null
    }
}
