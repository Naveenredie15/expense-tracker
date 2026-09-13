package com.expensetracker.di

import android.content.Context
import androidx.room.Room
import com.expensetracker.data.dao.AccountDao
import com.expensetracker.data.dao.CategoryDao
import com.expensetracker.data.dao.IgnoredTransactionDao
import com.expensetracker.data.dao.PayeeDao
import com.expensetracker.data.dao.SmsTemplateDao
import com.expensetracker.data.dao.TransactionDao
import com.expensetracker.data.database.ExpenseTrackerDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideExpenseTrackerDatabase(
        @ApplicationContext context: Context
    ): ExpenseTrackerDatabase {
        // Delegate to the single builder that registers real migrations, so the
        // Hilt-injected instance and the SMS-path instance are the SAME database
        // (one file, one connection pool) and both preserve data across version bumps.
        return ExpenseTrackerDatabase.getDatabase(context)
    }
    
    @Provides
    fun provideTransactionDao(database: ExpenseTrackerDatabase): TransactionDao {
        return database.transactionDao()
    }
    
    @Provides
    fun provideCategoryDao(database: ExpenseTrackerDatabase): CategoryDao {
        return database.categoryDao()
    }
    
    @Provides
    fun providePayeeDao(database: ExpenseTrackerDatabase): PayeeDao {
        return database.payeeDao()
    }

    @Provides
    fun provideAccountDao(database: ExpenseTrackerDatabase): AccountDao {
        return database.accountDao()
    }

    @Provides
    fun provideSmsTemplateDao(database: ExpenseTrackerDatabase): SmsTemplateDao {
        return database.smsTemplateDao()
    }

    @Provides
    fun provideIgnoredTransactionDao(database: ExpenseTrackerDatabase): IgnoredTransactionDao {
        return database.ignoredTransactionDao()
    }

    @Provides
    fun provideRecurringExpenseDao(database: ExpenseTrackerDatabase): com.expensetracker.data.dao.RecurringExpenseDao {
        return database.recurringExpenseDao()
    }

    @Provides
    fun provideIgnoredSmsPatternDao(database: ExpenseTrackerDatabase): com.expensetracker.data.dao.IgnoredSmsPatternDao {
        return database.ignoredSmsPatternDao()
    }

    @Provides
    fun provideTransactionSplitDao(database: ExpenseTrackerDatabase): com.expensetracker.data.dao.TransactionSplitDao {
        return database.transactionSplitDao()
    }
} 