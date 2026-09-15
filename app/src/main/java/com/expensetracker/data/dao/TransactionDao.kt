package com.expensetracker.data.dao

import androidx.room.*
import com.expensetracker.data.entity.Transaction
import com.expensetracker.data.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface TransactionDao {
    
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY timestamp DESC")
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE category = :category ORDER BY timestamp DESC")
    fun getTransactionsByCategory(category: String): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    fun getTransactionsByDateRange(startDate: Instant, endDate: Instant): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE sender = :sender ORDER BY timestamp DESC")
    fun getTransactionsBySender(sender: String): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE payee LIKE '%' || :payee || '%' ORDER BY timestamp DESC")
    fun getTransactionsByPayee(payee: String): Flow<List<Transaction>>
    
    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type AND excludeFromTotals = 0 AND timestamp BETWEEN :startDate AND :endDate")
    suspend fun getTotalAmountByTypeAndDateRange(
        type: TransactionType,
        startDate: Instant,
        endDate: Instant
    ): Double?

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE type = 'DEBIT' AND excludeFromTotals = 0 AND category IS NOT NULL AND timestamp BETWEEN :startDate AND :endDate GROUP BY category ORDER BY total DESC")
    suspend fun getCategoryWiseExpenses(startDate: Instant, endDate: Instant): List<CategoryAmount>

    @Query("SELECT SUM(amount) FROM transactions WHERE category = :category AND timestamp BETWEEN :startDate AND :endDate")
    suspend fun getTotalAmountByCategoryAndDateRange(
        category: String,
        startDate: Instant,
        endDate: Instant
    ): Double?
    
    @Query("SELECT * FROM transactions WHERE message = :message AND sender = :sender AND ABS(timestamp - :timestamp) < 60000000000") // 1 minute tolerance
    suspend fun findDuplicateTransaction(message: String, sender: String, timestamp: Instant): Transaction?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<Transaction>)
    
    @Update
    suspend fun updateTransaction(transaction: Transaction)
    
    @Delete
    suspend fun deleteTransaction(transaction: Transaction)
    
    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: String)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): Transaction?

    @Query("SELECT * FROM transactions WHERE referenceId = :referenceId LIMIT 1")
    suspend fun findByReferenceId(referenceId: String): Transaction?

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    /** Delete only SMS-parsed rows; keep manually-entered ones (used on re-sync). */
    @Query("DELETE FROM transactions WHERE isManual = 0")
    suspend fun clearParsedTransactions()

    /** Bulk re-categorize (used when categories are merged/renamed). */
    @Query("UPDATE transactions SET category = :newCategory WHERE category = :oldCategory")
    suspend fun reassignCategory(oldCategory: String, newCategory: String)

    /** Clear a category (used when a category is removed → back to uncategorized). */
    @Query("UPDATE transactions SET category = NULL WHERE category = :oldCategory")
    suspend fun clearCategory(oldCategory: String)
    
    // Smart categorization: Get the most recent category used for a payee
    @Query("SELECT category FROM transactions WHERE payee = :payee AND category IS NOT NULL ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastCategoryForPayee(payee: String): String?
    
    // Payee-wise spending analytics
    @Query("SELECT payee, SUM(amount) as total FROM transactions WHERE payee IS NOT NULL AND type = 'DEBIT' GROUP BY payee ORDER BY total DESC")
    suspend fun getPayeeWiseSpending(): List<PayeeAmount>
    
    @Query("SELECT payee, SUM(amount) as total FROM transactions WHERE payee IS NOT NULL AND type = 'DEBIT' AND timestamp BETWEEN :startDate AND :endDate GROUP BY payee ORDER BY total DESC")
    suspend fun getPayeeWiseSpendingByDateRange(startDate: Instant, endDate: Instant): List<PayeeAmount>
}

data class CategoryAmount(
    val category: String,
    val total: Double
)

data class PayeeAmount(
    val payee: String,
    val total: Double
) 