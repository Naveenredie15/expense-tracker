package com.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import java.util.UUID

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val message: String,
    val amount: Double,
    val type: TransactionType,
    val payee: String?,
    val category: String?,
    val timestamp: Instant,
    val referenceId: String? = null, // Bank's unique ref (UPI RRN), when present
    val accountId: String? = null, // Linked registered account (matched by last-4)
    val excludeFromTotals: Boolean = false, // e.g. credit-card bill payments: not real income/expense
    val isVerified: Boolean = false, // User can verify/confirm parsed data
    val isDuplicate: Boolean = false, // For duplicate detection
    /**
     * Manual billing-cycle correction for card transactions: shifts which cycle this
     * txn counts toward by N cycles (0 = the cycle its date falls in, -1 = previous,
     * +1 = next). Used when a bank posts a spend into an adjacent statement.
     */
    val billingCycleShift: Int = 0
)

enum class TransactionType {
    DEBIT,
    CREDIT
} 