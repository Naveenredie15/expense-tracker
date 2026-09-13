package com.expensetracker.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.expensetracker.data.entity.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A bank-looking SMS that matched no template yet (candidate for a new format). */
data class UnrecognizedSms(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val detectedLast4: String?
)

class SmsReader(private val context: Context) {

    /** Builds a parser loaded with the user's saved templates + accounts. */
    private suspend fun buildParser(): SmsParser {
        val db = com.expensetracker.data.database.ExpenseTrackerDatabase.getDatabase(context)
        val templates = try { db.smsTemplateDao().getEnabledTemplates() } catch (_: Exception) { emptyList() }
        val accounts = try { db.accountDao().getAllAccountsOnce() } catch (_: Exception) { emptyList() }
        val payeeCategories = try {
            db.payeeDao().getLearnedPayees()
                .mapNotNull { p -> p.linkedCategoryId?.let { p.name.uppercase() to it } }
                .toMap()
        } catch (_: Exception) { emptyMap() }
        val recurring = try { db.recurringExpenseDao().getAllOnce().filter { it.enabled } } catch (_: Exception) { emptyList() }
        val recurringMatchers = recurring.map {
            SmsParser.RecurringMatcher(
                amount = it.amount,
                payeeUpper = it.matchPayee?.uppercase(),
                regex = it.matchRegex?.let { r -> runCatching { Regex(r, RegexOption.IGNORE_CASE) }.getOrNull() }
            )
        }
        return SmsParser(templates, accounts, payeeCategories, recurringMatchers)
    }
    
    // "Available Balance is Rs. 71,249.99" -> 71249.99
    private val availableBalanceRegex =
        Regex("""Available\s+Balance\s+is\s+Rs\.?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val last4Regex = Regex("""[Xx*]{2,}(\d{3,4})""")

    suspend fun readBankSms(limitDays: Int = 30): List<Transaction> = withContext(Dispatchers.IO) {
        val transactions = mutableListOf<Transaction>()
        val smsParser = buildParser()

        if (!hasReadSmsPermission()) {
            return@withContext transactions
        }

        val db = com.expensetracker.data.database.ExpenseTrackerDatabase.getDatabase(context)
        val accounts = try { db.accountDao().getAllAccountsOnce() } catch (_: Exception) { emptyList() }
        // Latest bank balance seen per account (SMS come newest-first, so first wins).
        val latestBalance = mutableMapOf<String, Pair<Double, Long>>()

        val uri = Uri.parse("content://sms/inbox")
        
        // Calculate timestamp for limiting days
        val limitTimestamp = System.currentTimeMillis() - (limitDays * 24 * 60 * 60 * 1000L)
        
        val projection = arrayOf(
            "_id",
            "address", // sender
            "body",    // message content
            "date"     // timestamp
        )
        
        val selection = "date >= ?"
        val selectionArgs = arrayOf(limitTimestamp.toString())
        val sortOrder = "date DESC"
        
        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                
                val addressColumn = cursor.getColumnIndexOrThrow("address")
                val bodyColumn = cursor.getColumnIndexOrThrow("body")
                val dateColumn = cursor.getColumnIndexOrThrow("date")
                
                while (cursor.moveToNext()) {
                    val sender = cursor.getString(addressColumn) ?: continue
                    val message = cursor.getString(bodyColumn) ?: continue
                    val timestamp = cursor.getLong(dateColumn)
                    
                    // Debug logging
                    println("SMS Reader - Found SMS from: '$sender', Message: '${message.take(50)}...'")
                    
                    // Parse the SMS message
                    val transaction = smsParser.parseTransaction(sender, message, timestamp)
                    if (transaction != null) {
                        println("SMS Reader - Successfully parsed transaction: ${transaction.amount} ${transaction.type}")
                        transactions.add(transaction)
                    } else {
                        println("SMS Reader - Failed to parse as transaction")
                    }

                    // Capture the latest bank balance for a matched account.
                    val bal = availableBalanceRegex.find(message)?.groupValues?.get(1)
                    val digits = last4Regex.find(message)?.groupValues?.get(1)
                    if (bal != null && digits != null) {
                        val acct = accounts.firstOrNull { it.last4 == digits }
                        if (acct != null && !latestBalance.containsKey(acct.id)) {
                            latestBalance[acct.id] = bal.replace(",", "").toDouble() to timestamp
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Persist the newest balance we saw for each account, but only if it's newer
        // than what's already stored (so a manually-entered balance isn't clobbered by
        // an older SMS reading).
        for (acct in accounts) {
            latestBalance[acct.id]?.let { (bal, at) ->
                if (at > (acct.latestBalanceAt ?: 0L)) {
                    try {
                        db.accountDao().updateAccount(acct.copy(latestBalance = bal, latestBalanceAt = at))
                    } catch (_: Exception) {
                    }
                }
            }
        }

        return@withContext transactions
    }
    
    // Looks like a money transaction: has an amount and a debit/credit keyword.
    private val amountRegex = Regex("""(?:Rs|INR|₹)\.?\s*[\d,]+(?:\.\d{1,2})?""", RegexOption.IGNORE_CASE)
    private val txnKeywordRegex = Regex(
        """debited|credited|spent|withdrawn|deposited|received|debit|credit""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Bank-looking SMS from the last [limitDays] that matched NO template — candidates
     * for the user to tag as a new format. Filters to messages that have an amount AND
     * a transaction keyword (drops OTP/promo). The caller groups these by pattern.
     */
    suspend fun readUnrecognizedBankSms(limitDays: Int = 90): List<UnrecognizedSms> = withContext(Dispatchers.IO) {
        val result = mutableListOf<UnrecognizedSms>()
        if (!hasReadSmsPermission()) return@withContext result

        val parser = buildParser()
        val db = com.expensetracker.data.database.ExpenseTrackerDatabase.getDatabase(context)
        val accounts = try { db.accountDao().getAllAccountsOnce() } catch (_: Exception) { emptyList() }

        val uri = Uri.parse("content://sms/inbox")
        val limitTimestamp = System.currentTimeMillis() - (limitDays * 24 * 60 * 60 * 1000L)
        val projection = arrayOf("_id", "address", "body", "date")
        try {
            context.contentResolver.query(
                uri, projection, "date >= ?", arrayOf(limitTimestamp.toString()), "date DESC"
            )?.use { cursor ->
                val addressColumn = cursor.getColumnIndexOrThrow("address")
                val bodyColumn = cursor.getColumnIndexOrThrow("body")
                val dateColumn = cursor.getColumnIndexOrThrow("date")
                while (cursor.moveToNext()) {
                    val sender = cursor.getString(addressColumn) ?: continue
                    val message = cursor.getString(bodyColumn) ?: continue
                    val timestamp = cursor.getLong(dateColumn)

                    // Skip anything the engine already parses.
                    if (parser.parseTransaction(sender, message, timestamp) != null) continue
                    // Skip non-transaction noise (OTP / promo / statement / reminder / failed).
                    if (parser.isNoise(message)) continue
                    // Keep only money-looking SMS.
                    if (!amountRegex.containsMatchIn(message)) continue
                    if (!txnKeywordRegex.containsMatchIn(message)) continue

                    val digits = last4Regex.find(message)?.groupValues?.get(1)
                    result.add(UnrecognizedSms(sender, message, timestamp, digits))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    suspend fun readSmsFromSender(sender: String, limitDays: Int = 30): List<Transaction> = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission()) {
            return@withContext emptyList()
        }
        
        val transactions = mutableListOf<Transaction>()
        val smsParser = buildParser()
        val uri = Uri.parse("content://sms/inbox")

        val limitTimestamp = System.currentTimeMillis() - (limitDays * 24 * 60 * 60 * 1000L)

        val projection = arrayOf(
            "_id",
            "address",
            "body",
            "date"
        )

        val selection = "address LIKE ? AND date >= ?"
        val selectionArgs = arrayOf("%$sender%", limitTimestamp.toString())
        val sortOrder = "date DESC"
        
        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                
                val addressColumn = cursor.getColumnIndexOrThrow("address")
                val bodyColumn = cursor.getColumnIndexOrThrow("body")
                val dateColumn = cursor.getColumnIndexOrThrow("date")
                
                while (cursor.moveToNext()) {
                    val senderAddress = cursor.getString(addressColumn) ?: continue
                    val message = cursor.getString(bodyColumn) ?: continue
                    val timestamp = cursor.getLong(dateColumn)
                    
                    val transaction = smsParser.parseTransaction(senderAddress, message, timestamp)
                    if (transaction != null) {
                        transactions.add(transaction)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return@withContext transactions
    }
    
    suspend fun getAllBankSenders(): List<String> = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission()) {
            return@withContext emptyList()
        }
        
        val senders = mutableSetOf<String>()
        val uri = Uri.parse("content://sms/inbox")
        
        val projection = arrayOf("address")
        val limitTimestamp = System.currentTimeMillis() - (90 * 24 * 60 * 60 * 1000L) // 90 days
        val selection = "date >= ?"
        val selectionArgs = arrayOf(limitTimestamp.toString())
        
        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                
                val addressColumn = cursor.getColumnIndexOrThrow("address")
                
                while (cursor.moveToNext()) {
                    val sender = cursor.getString(addressColumn) ?: continue
                    
                    // Check if this sender sends bank-like messages
                    if (isPotentialBankSender(sender)) {
                        senders.add(sender)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return@withContext senders.toList().sorted()
    }
    
    private fun hasReadSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun isPotentialBankSender(sender: String): Boolean {
        // Be very liberal - check any SMS that might contain transaction info
        // The parser will do the real filtering based on message content
        return true // Process all SMS messages and let the parser decide
    }
}