package com.expensetracker.data.dao

import androidx.room.*
import com.expensetracker.data.entity.RecurringExpense
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringExpenseDao {

    @Query("SELECT * FROM recurring_expenses ORDER BY name ASC")
    fun getAll(): Flow<List<RecurringExpense>>

    @Query("SELECT * FROM recurring_expenses")
    suspend fun getAllOnce(): List<RecurringExpense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: RecurringExpense)

    @Update
    suspend fun update(expense: RecurringExpense)

    @Delete
    suspend fun delete(expense: RecurringExpense)
}
