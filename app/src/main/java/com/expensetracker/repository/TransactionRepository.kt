package com.expensetracker.repository

import com.expensetracker.data.dao.CategoryAmount
import com.expensetracker.data.dao.IgnoredTransactionDao
import com.expensetracker.data.dao.PayeeAmount
import com.expensetracker.data.dao.PayeeDao
import com.expensetracker.data.dao.TransactionDao
import com.expensetracker.data.dao.TransactionSplitDao
import com.expensetracker.data.entity.IgnoredTransaction
import com.expensetracker.data.entity.Payee
import com.expensetracker.data.entity.Transaction
import com.expensetracker.data.entity.TransactionSplit
import com.expensetracker.data.entity.TransactionType
import com.expensetracker.sms.TransactionCategorizer
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val ignoredTransactionDao: IgnoredTransactionDao,
    private val payeeDao: PayeeDao,
    private val splitDao: TransactionSplitDao
) {

    /** Expand a parsed transaction into its user-defined split parts, if any. */
    private fun expandToParts(parent: Transaction, splits: List<TransactionSplit>): List<Transaction> =
        splits.mapIndexed { i, s ->
            parent.copy(
                id = UUID.randomUUID().toString(),
                amount = s.amount,
                payee = s.payee ?: parent.payee,
                referenceId = null,                       // distinct signature per part
                message = parent.message + " [split ${i + 1}]"
            )
        }

    /** Replace [transactions] that the user has split with their parts (durable via splitDao). */
    private suspend fun applySplits(transactions: List<Transaction>): List<Transaction> {
        val bySig = splitDao.getAll().groupBy { it.signature }
        if (bySig.isEmpty()) return transactions
        return transactions.flatMap { t ->
            val parts = bySig[signatureOf(t)]
            if (parts.isNullOrEmpty()) listOf(t) else expandToParts(t, parts)
        }
    }

    /**
     * Stable identity for a transaction. Prefers the bank's UPI RRN (unique per
     * transaction) so re-syncs and duplicate/differently-worded SMS collapse to one.
     * Credit-card SMS carry no RRN, so those fall back to sender+message+timestamp.
     */
    private fun signatureOf(t: Transaction): String =
        t.referenceId?.let { "ref:$it" } ?: "${t.sender}|${t.message}|${t.timestamp.epochSeconds}"
    
    fun getAllTransactions(): Flow<List<Transaction>> = transactionDao.getAllTransactions()
    
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> = 
        transactionDao.getTransactionsByType(type)
    
    fun getTransactionsByCategory(category: String): Flow<List<Transaction>> = 
        transactionDao.getTransactionsByCategory(category)
    
    fun getTransactionsByDateRange(startDate: Instant, endDate: Instant): Flow<List<Transaction>> = 
        transactionDao.getTransactionsByDateRange(startDate, endDate)
    
    fun getTransactionsBySender(sender: String): Flow<List<Transaction>> = 
        transactionDao.getTransactionsBySender(sender)
    
    fun getTransactionsByPayee(payee: String): Flow<List<Transaction>> = 
        transactionDao.getTransactionsByPayee(payee)
    
    suspend fun getTotalAmountByTypeAndDateRange(
        type: TransactionType,
        startDate: Instant,
        endDate: Instant
    ): Double = transactionDao.getTotalAmountByTypeAndDateRange(type, startDate, endDate) ?: 0.0
    
    suspend fun getCategoryWiseExpenses(startDate: Instant, endDate: Instant): List<CategoryAmount> =
        transactionDao.getCategoryWiseExpenses(startDate, endDate)

    /** Total refunds in a range; netted against expenses so a refunded spend nets to zero. */
    suspend fun getRefundTotalByDateRange(startDate: Instant, endDate: Instant): Double =
        transactionDao.getTotalAmountByCategoryAndDateRange("Refund", startDate, endDate) ?: 0.0
    
    /** True if a transaction with the same identity already exists in the DB. */
    private suspend fun isDuplicate(t: Transaction): Boolean {
        // Prefer the RRN: catches re-syncs AND differently-worded SMS for one payment.
        t.referenceId?.let { return transactionDao.findByReferenceId(it) != null }
        // No RRN (e.g. card spend): fall back to exact sender+message+timestamp.
        return transactionDao.findDuplicateTransaction(t.message, t.sender, t.timestamp) != null
    }

    suspend fun insertTransaction(transaction: Transaction) {
        // A live SMS for an already-split transaction expands into its parts.
        val splits = splitDao.getBySignature(signatureOf(transaction))
        if (splits.isNotEmpty()) {
            insertTransactions(listOf(transaction))
            return
        }
        val ignored = ignoredTransactionDao.getAllSignatures().toSet()
        if (signatureOf(transaction) in ignored) return // user deleted this before
        if (!isDuplicate(transaction)) {
            transactionDao.insertTransaction(transaction)
        }
    }

    suspend fun insertTransactions(transactionsIn: List<Transaction>) {
        val transactions = applySplits(transactionsIn)
        val ignored = ignoredTransactionDao.getAllSignatures().toSet()
        val seenRefs = mutableSetOf<String>()
        val uniqueTransactions = mutableListOf<Transaction>()

        for (transaction in transactions) {
            // Skip transactions the user has previously deleted
            if (signatureOf(transaction) in ignored) continue
            // Skip duplicate RRNs within this same sync batch
            val ref = transaction.referenceId
            if (ref != null && !seenRefs.add(ref)) continue
            if (!isDuplicate(transaction)) {
                uniqueTransactions.add(transaction)
            }
        }

        if (uniqueTransactions.isNotEmpty()) {
            transactionDao.insertTransactions(uniqueTransactions)
        }
    }

    suspend fun updateTransaction(transaction: Transaction) =
        transactionDao.updateTransaction(transaction)

    /**
     * Split one transaction into [parts] (amount + optional payee). Persisted so the split
     * survives re-syncs (the original SMS is re-expanded into these parts). Applied now:
     * the original row is removed and the parts inserted.
     */
    suspend fun splitTransaction(original: Transaction, parts: List<Pair<Double, String?>>) {
        val sig = signatureOf(original)
        splitDao.deleteBySignature(sig)
        val splits = parts.map { TransactionSplit(signature = sig, amount = it.first, payee = it.second) }
        splitDao.insertAll(splits)
        transactionDao.deleteTransaction(original)
        transactionDao.insertTransactions(expandToParts(original, splits))
    }

    /** Bulk re-categorize existing transactions (category merge/rename). */
    suspend fun reassignCategory(oldCategory: String, newCategory: String) =
        transactionDao.reassignCategory(oldCategory, newCategory)

    /** Clear a removed category from existing transactions (→ uncategorized). */
    suspend fun clearCategory(oldCategory: String) =
        transactionDao.clearCategory(oldCategory)

    /** User-initiated delete: remove the row AND remember it so re-sync won't re-add it. */
    suspend fun deleteTransaction(transaction: Transaction) {
        ignoredTransactionDao.ignore(IgnoredTransaction(signatureOf(transaction)))
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun deleteTransactionById(transactionId: String) {
        val txn = transactionDao.getTransactionById(transactionId)
        if (txn != null) {
            deleteTransaction(txn)
        } else {
            transactionDao.deleteTransactionById(transactionId)
        }
    }

    /** Wipe transactions and forget the ignore list, so a fresh Sync re-pulls everything. */
    suspend fun clearAllTransactions() {
        transactionDao.clearAllTransactions()
        ignoredTransactionDao.clearAll()
    }

    /**
     * Wipe transactions but KEEP the ignore list. Used when an account/template edit
     * requires re-parsing every SMS: transactions are rebuilt from scratch (so they
     * pick up the new account attribution / classification) while user-deleted ones
     * stay deleted.
     */
    suspend fun clearTransactionsKeepIgnored() {
        // Keep manually-entered rows: they have no SMS to rebuild from.
        transactionDao.clearParsedTransactions()
    }

    /**
     * Add a hand-entered transaction (no SMS). Category is auto-derived from the
     * description via the same keyword engine; the row is flagged [Transaction.isManual]
     * so re-syncs preserve it. Inserted directly (bypasses SMS dedup/ignore).
     */
    suspend fun addManualTransaction(
        amount: Double,
        type: TransactionType,
        description: String,
        accountId: String?,
        timestamp: Instant
    ) {
        val payee = description.trim().ifBlank { null }
        val message = "Manual entry" + (payee?.let { ": $it" } ?: "") + " • Rs $amount"
        val category = TransactionCategorizer().categorizeTransaction(payee, message, type)
        transactionDao.insertTransaction(
            Transaction(
                sender = "MANUAL",
                message = message,
                amount = amount,
                type = type,
                payee = payee,
                category = category,
                timestamp = timestamp,
                referenceId = null,
                accountId = accountId,
                isVerified = true,
                isManual = true
            )
        )
    }
    
    // Smart categorization
    suspend fun getLastCategoryForPayee(payee: String): String? =
        transactionDao.getLastCategoryForPayee(payee)

    /**
     * Remember that [payee] should be categorized as [categoryName] (stored in the
     * payees table so it survives transaction wipes / re-syncs). The parser re-applies
     * these on every sync, so a person the user tagged "Family" stays "Family".
     * NOTE: [Payee.linkedCategoryId] holds the category *name* (transactions store
     * categories by name), not a Category row id.
     */
    suspend fun rememberPayeeCategory(payee: String, categoryName: String) {
        val name = payee.trim()
        if (name.isEmpty()) return
        val existing = payeeDao.getPayeeByName(name)
        if (existing != null) {
            payeeDao.updatePayee(
                existing.copy(linkedCategoryId = categoryName, lastUsed = System.currentTimeMillis())
            )
        } else {
            payeeDao.insertPayee(Payee(name = name, linkedCategoryId = categoryName))
        }
    }

    /** payee name (UPPERCASE) -> learned category name, for the parser to apply. */
    suspend fun getLearnedPayeeCategories(): Map<String, String> =
        payeeDao.getLearnedPayees()
            .mapNotNull { p -> p.linkedCategoryId?.let { p.name.uppercase() to it } }
            .toMap()
    
    // Payee analytics
    suspend fun getPayeeWiseSpending(): List<PayeeAmount> = 
        transactionDao.getPayeeWiseSpending()
    
    suspend fun getPayeeWiseSpendingByDateRange(startDate: Instant, endDate: Instant): List<PayeeAmount> = 
        transactionDao.getPayeeWiseSpendingByDateRange(startDate, endDate)
} 