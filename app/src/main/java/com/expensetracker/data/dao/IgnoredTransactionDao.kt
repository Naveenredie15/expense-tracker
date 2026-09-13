package com.expensetracker.data.dao

import androidx.room.*
import com.expensetracker.data.entity.IgnoredTransaction

@Dao
interface IgnoredTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun ignore(entry: IgnoredTransaction)

    @Query("SELECT signature FROM ignored_transactions")
    suspend fun getAllSignatures(): List<String>

    @Query("DELETE FROM ignored_transactions")
    suspend fun clearAll()
}
