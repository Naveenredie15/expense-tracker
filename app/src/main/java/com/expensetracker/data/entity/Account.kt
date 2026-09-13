package com.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A bank account or credit card the user owns, identified by the last 3-4 digits
 * that appear in their SMS (e.g. "XX000" -> "000"). Used to attribute a parsed
 * transaction to the right account for per-account dashboards.
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    /** Last 3-4 digits as shown in SMS, e.g. "465" or "1008". */
    val last4: String,
    /** User-friendly label, e.g. "ICICI Salary" or "ICICI Amazon Card". */
    val label: String,
    val type: AccountType,
    // --- Credit-card billing config (CARD only) ---
    val creditLimit: Double? = null,      // total credit limit, user-entered
    val statementDay: Int? = null,        // day of month the bill is generated, e.g. 22
    val dueDay: Int? = null,              // day of month payment is due, e.g. 8
    // --- Bank config (BANK only) ---
    val isSalaryAccount: Boolean = false, // salary credited to this account
    val monthlySalary: Double? = null,    // user-entered monthly salary (salary accounts)
    val salaryDay: Int? = null,           // day of month salary is credited (1..31)
    val latestBalance: Double? = null,    // last "Available Balance" seen in SMS
    val latestBalanceAt: Long? = null,    // when that balance was seen (epoch millis)
    val createdAt: Long = System.currentTimeMillis()
)

enum class AccountType {
    BANK,
    CARD
}
