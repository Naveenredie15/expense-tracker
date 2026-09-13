package com.expensetracker.data.dao

import androidx.room.*
import com.expensetracker.data.entity.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY label ASC")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts")
    suspend fun getAllAccountsOnce(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: String): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account)

    @Update
    suspend fun updateAccount(account: Account)

    @Delete
    suspend fun deleteAccount(account: Account)
}
