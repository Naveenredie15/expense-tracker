package com.expensetracker.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.expensetracker.data.converter.Converters
import com.expensetracker.data.dao.AccountDao
import com.expensetracker.data.dao.CategoryDao
import com.expensetracker.data.dao.IgnoredTransactionDao
import com.expensetracker.data.dao.IgnoredSmsPatternDao
import com.expensetracker.data.dao.PayeeDao
import com.expensetracker.data.dao.RecurringExpenseDao
import com.expensetracker.data.dao.SmsTemplateDao
import com.expensetracker.data.dao.TransactionDao
import com.expensetracker.data.dao.TransactionSplitDao
import com.expensetracker.data.entity.Account
import com.expensetracker.data.entity.Category
import com.expensetracker.data.entity.IgnoredSmsPattern
import com.expensetracker.data.entity.IgnoredTransaction
import com.expensetracker.data.entity.Payee
import com.expensetracker.data.entity.RecurringExpense
import com.expensetracker.data.entity.SmsTemplate
import com.expensetracker.data.entity.Transaction
import com.expensetracker.data.entity.TransactionSplit

@Database(
    entities = [Transaction::class, Category::class, Payee::class, Account::class, SmsTemplate::class, IgnoredTransaction::class, RecurringExpense::class, IgnoredSmsPattern::class, TransactionSplit::class],
    version = 15,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ExpenseTrackerDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun payeeDao(): PayeeDao
    abstract fun accountDao(): AccountDao
    abstract fun smsTemplateDao(): SmsTemplateDao
    abstract fun ignoredTransactionDao(): IgnoredTransactionDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
    abstract fun ignoredSmsPatternDao(): IgnoredSmsPatternDao
    abstract fun transactionSplitDao(): TransactionSplitDao
    
    companion object {
        @Volatile
        private var INSTANCE: ExpenseTrackerDatabase? = null

        // --- Schema migrations (preserve user data across version bumps) ---
        // Each schema change MUST add a Migration here AND bump `version` above, or
        // fallbackToDestructiveMigration() will wipe the DB for that step.
        // All changes so far are additive, so these are simple ALTER/CREATE steps.

        // v9: Transaction.billingCycleShift (manual card billing-cycle override)
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN billingCycleShift INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v10: Account.monthlySalary (salary amount for planner projection)
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN monthlySalary REAL")
            }
        }

        // v11: recurring_expenses table (subscriptions/EMIs/rent)
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring_expenses` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `amount` REAL NOT NULL, " +
                        "`dayOfMonth` INTEGER, `category` TEXT, `enabled` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        // v12: Account.salaryDay (day of month salary is credited)
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN salaryDay INTEGER")
            }
        }

        // v13: ignored_sms_patterns table (user-marked promotional / non-transaction SMS)
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ignored_sms_patterns` (" +
                        "`signature` TEXT NOT NULL, `sample` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`signature`))"
                )
            }
        }

        // v14: recurring_expenses.matchRegex / matchPayee (link a subscription to its txn)
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN matchRegex TEXT")
                db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN matchPayee TEXT")
            }
        }

        // v15: transaction_splits table (split one txn into parts)
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transaction_splits` (" +
                        "`id` TEXT NOT NULL, `signature` TEXT NOT NULL, `amount` REAL NOT NULL, " +
                        "`payee` TEXT, PRIMARY KEY(`id`))"
                )
            }
        }

        private val ALL_MIGRATIONS =
            arrayOf(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)

        fun getDatabase(context: Context): ExpenseTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseTrackerDatabase::class.java,
                    "expense_tracker_database"
                )
                .addMigrations(*ALL_MIGRATIONS)
                // Backstop only for version gaps with no migration (shouldn't happen if
                // every bump adds one above). Keeps the app from crashing.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 