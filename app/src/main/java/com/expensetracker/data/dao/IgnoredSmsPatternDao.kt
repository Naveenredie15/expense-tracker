package com.expensetracker.data.dao

import androidx.room.*
import com.expensetracker.data.entity.IgnoredSmsPattern
import kotlinx.coroutines.flow.Flow

@Dao
interface IgnoredSmsPatternDao {

    @Query("SELECT * FROM ignored_sms_patterns ORDER BY createdAt DESC")
    fun getAll(): Flow<List<IgnoredSmsPattern>>

    @Query("SELECT signature FROM ignored_sms_patterns")
    suspend fun getSignatures(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pattern: IgnoredSmsPattern)

    @Delete
    suspend fun delete(pattern: IgnoredSmsPattern)
}
