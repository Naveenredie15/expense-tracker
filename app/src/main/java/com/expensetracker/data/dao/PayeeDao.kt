package com.expensetracker.data.dao

import androidx.room.*
import com.expensetracker.data.entity.Payee
import kotlinx.coroutines.flow.Flow

@Dao
interface PayeeDao {
    
    @Query("SELECT * FROM payees ORDER BY transactionCount DESC, lastUsed DESC")
    fun getAllPayees(): Flow<List<Payee>>
    
    @Query("SELECT * FROM payees WHERE linkedCategoryId = :categoryId ORDER BY transactionCount DESC")
    fun getPayeesByCategory(categoryId: String): Flow<List<Payee>>
    
    @Query("SELECT * FROM payees WHERE name LIKE '%' || :searchQuery || '%' ORDER BY transactionCount DESC LIMIT 10")
    suspend fun searchPayees(searchQuery: String): List<Payee>
    
    @Query("SELECT * FROM payees WHERE id = :payeeId")
    suspend fun getPayeeById(payeeId: String): Payee?
    
    @Query("SELECT * FROM payees WHERE name = :name LIMIT 1")
    suspend fun getPayeeByName(name: String): Payee?

    /** Payees the user has assigned a category to; used to re-apply on every sync. */
    @Query("SELECT * FROM payees WHERE linkedCategoryId IS NOT NULL")
    suspend fun getLearnedPayees(): List<Payee>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayee(payee: Payee)
    
    @Update
    suspend fun updatePayee(payee: Payee)
    
    @Query("UPDATE payees SET transactionCount = transactionCount + 1, lastUsed = :timestamp WHERE id = :payeeId")
    suspend fun incrementPayeeUsage(payeeId: String, timestamp: Long = System.currentTimeMillis())
    
    @Delete
    suspend fun deletePayee(payee: Payee)
} 