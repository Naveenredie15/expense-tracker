package com.expensetracker.data.dao

import androidx.room.*
import com.expensetracker.data.entity.TransactionSplit

@Dao
interface TransactionSplitDao {

    @Query("SELECT * FROM transaction_splits")
    suspend fun getAll(): List<TransactionSplit>

    @Query("SELECT * FROM transaction_splits WHERE signature = :signature")
    suspend fun getBySignature(signature: String): List<TransactionSplit>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(splits: List<TransactionSplit>)

    @Query("DELETE FROM transaction_splits WHERE signature = :signature")
    suspend fun deleteBySignature(signature: String)
}
