package com.expensetracker.parser

/**
 * Quick JVM test harness. Run with:
 *   kotlinc IciciSmsParser.kt TestParser.kt -include-runtime -d parser.jar && java -jar parser.jar
 * or just:
 *   kotlin IciciSmsParser.kt TestParser.kt
 *
 * Paste your OWN real ICICI SMS bodies into `samples` to confirm they parse.
 */
fun main() {
    val samples = listOf(
        "INR 1,234.00 spent using ICICI Bank Card XX7003 on 05-Sep-25 on Amazon. Avl Limit: INR 1,50,000.00. If not you, call 1800...",
        "INR 499.00 spent using ICICI Bank Card XX7003 on 05-Sep-25 on SWIGGY. Avl Limit: INR 1,49,501.00.",
        "ICICI Bank Acct XX898 debited for Rs 100.00 on 05-Sep-25; Swiggy credited. UPI:512345678901. Call 1800... for dispute.",
        "ICICI Bank Acct XX898 debited for Rs 2,500.00 on 06-Sep-25; John Doe credited. UPI:998877665544.",
        "ICICI Bank Account XX898 credited:Rs 500.00 on 05-Sep-25. Info:UPI/512345678901/Payment. Available Balance is Rs 10,000.00.",
        // Should be ignored (OTP / promo):
        "123456 is your ICICI Bank OTP. Do not share with anyone.",
    )

    var ok = 0
    for (s in samples) {
        val p = IciciSmsParser.parse(s)
        if (p == null) {
            println("IGNORED: ${s.take(50)}...")
        } else {
            ok++
            println("PARSED : ${p.type}/${p.source} amt=${p.amount} merchant=${p.merchant} instr=${p.instrument} date=${p.dateToken} ref=${p.refId}")
        }
    }
    println("\n$ok/${samples.size} parsed (last one is expected to be ignored).")
}
