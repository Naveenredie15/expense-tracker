package com.expensetracker.sms

/**
 * Turns a tagged sample SMS into a reusable regex.
 *
 * The user pastes one sample and marks the amount span and merchant span. This
 * builder reproduces the message as a regex where:
 *   - the tagged spans become named capture groups (?<amount>) / (?<merchant>)
 *   - dates and stray numbers (ref ids, balances) are generalized so they don't
 *     pin the template to one specific message
 *   - runs of whitespace become \s+ so spacing variations still match
 *
 * Validated to match all real ICICI debit/credit formats from a single sample.
 */
object TemplateRegexBuilder {

    /** A tagged region of the sample. [end] is exclusive. */
    data class Span(val start: Int, val end: Int, val field: Field)

    enum class Field { AMOUNT, MERCHANT }

    // Date like "10-Sep-26" / "10-Sep-2026"
    private const val DATE = """\d{1,2}-[A-Za-z]{3}-\d{2,4}"""
    // A date OR a number run (with optional , and .)
    private val TOKEN = Regex("""($DATE|\d[\d.,]*)""")
    private val META = Regex("""([.\^$*+?()\[\]{}|\\])""")

    /**
     * Build a regex string from [sample] and the [spans] the user tagged.
     * Spans must not overlap.
     */
    fun build(sample: String, spans: List<Span>): String {
        val sorted = spans.sortedBy { it.start }
        val sb = StringBuilder()
        var cursor = 0
        for (span in sorted) {
            sb.append(escapeLiteral(sample.substring(cursor, span.start)))
            sb.append(
                when (span.field) {
                    Field.AMOUNT -> """(?<amount>[\d,]+\.?\d*)"""
                    Field.MERCHANT -> """(?<merchant>.+?)"""
                }
            )
            cursor = span.end
        }
        sb.append(escapeLiteral(sample.substring(cursor)))
        return sb.toString()
    }

    /**
     * Escape a literal chunk: keep structural words literal, but generalize dates
     * and numbers, and collapse whitespace. Tokens are handled in a single pass so
     * generalized regex is never re-escaped.
     */
    private fun escapeLiteral(text: String): String {
        val sb = StringBuilder()
        var last = 0
        for (m in TOKEN.findall(text)) {
            // literal text before this token
            sb.append(escapePlain(text.substring(last, m.range.first)))
            val tok = m.value
            sb.append(if (Regex(DATE).matches(tok)) DATE else """[\d.,]+""")
            last = m.range.last + 1
        }
        sb.append(escapePlain(text.substring(last)))
        return sb.toString()
    }

    private fun escapePlain(text: String): String {
        val escaped = META.replace(text) { "\\" + it.groupValues[1] }
        // collapse any run of whitespace to \s+ (lambda replacement = literal, no $/\ interpretation)
        return Regex("""\s+""").replace(escaped) { """\s+""" }
    }

    // small helper to mirror Python's finditer usage
    private fun Regex.findall(input: CharSequence) = findAll(input).toList()
}
