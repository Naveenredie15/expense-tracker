package com.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A fixed expense the user pays every month — subscriptions (Netflix), loan/EMI,
 * rent, insurance, etc. Not parsed from SMS; entered by the user. Used by the
 * planner to project next month's disposable cash (salary − card spend − these).
 */
@Entity(tableName = "recurring_expenses")
data class RecurringExpense(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,                 // e.g. "Netflix", "Home Loan EMI"
    val amount: Double,               // monthly amount
    val dayOfMonth: Int? = null,      // optional day it's charged (1..31)
    val category: String? = null,     // optional category, e.g. "Bills & Utilities"
    val enabled: Boolean = true,      // counted in projections only when enabled
    // "Teach by example": derived from a transaction the user linked when creating this.
    // matchRegex generalizes the linked SMS (amounts/dates) so future months auto-match;
    // matchPayee is a simpler fallback + display label.
    val matchRegex: String? = null,
    val matchPayee: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
