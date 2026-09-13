package com.expensetracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.expensetracker.data.database.ExpenseTrackerDatabase
import com.expensetracker.repository.TransactionRepository
import com.expensetracker.sms.SmsParser
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    /** Hilt accessor so a plain BroadcastReceiver can reach the app's repository. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmsReceiverEntryPoint {
        fun transactionRepository(): TransactionRepository
    }

    // "Available Balance is Rs. 71,249.99" -> 71249.99  (same as SmsReader)
    private val availableBalanceRegex =
        Regex("""Available\s+Balance\s+is\s+Rs\.?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val last4Regex = Regex("""[Xx*]{2,}(\d{3,4})""")

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // A long SMS arrives as several parts; concatenate them so the full message
        // (e.g. an ICICI card spend > 160 chars) is parsed as one, not per-fragment.
        val sender = messages.first().originatingAddress ?: return
        val fullMessage = messages.joinToString("") { it.messageBody ?: "" }
        val timestamp = messages.first().timestampMillis

        // Keep the process alive while we hit the DB off the main thread.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processIncomingSms(context, sender, fullMessage, timestamp)
            } catch (e: Exception) {
                android.util.Log.e("SmsReceiver", "Error processing SMS", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun processIncomingSms(
        context: Context,
        sender: String,
        message: String,
        timestamp: Long
    ) {
        val db = ExpenseTrackerDatabase.getDatabase(context)
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

        val smsParser = SmsParser(templates, accounts, payeeCategories, recurringMatchers)
        val transaction = smsParser.parseTransaction(sender, message, timestamp)

        if (transaction != null) {
            val repository = EntryPointAccessors.fromApplication(
                context.applicationContext,
                SmsReceiverEntryPoint::class.java
            ).transactionRepository()
            // insertTransaction skips duplicates (by RRN) and user-deleted (ignored) ones.
            repository.insertTransaction(transaction)
            android.util.Log.d(
                "SmsReceiver",
                "Saved transaction: ${transaction.type} of ₹${transaction.amount} from ${transaction.payee}"
            )
        }

        // Keep the account's latest balance fresh from a live SMS, if it carries one
        // and it's newer than what's stored.
        val bal = availableBalanceRegex.find(message)?.groupValues?.get(1)
        val digits = last4Regex.find(message)?.groupValues?.get(1)
        if (bal != null && digits != null) {
            val acct = accounts.firstOrNull { it.last4 == digits }
            if (acct != null && timestamp > (acct.latestBalanceAt ?: 0L)) {
                try {
                    db.accountDao().updateAccount(
                        acct.copy(
                            latestBalance = bal.replace(",", "").toDouble(),
                            latestBalanceAt = timestamp
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }
    }
}
