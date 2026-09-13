package com.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A user split of one parsed transaction into parts (e.g. a ₹10k payment → ₹5k + ₹5k).
 * Keyed by the parent transaction's [signature] so the split is re-applied on every
 * sync: when the original SMS is parsed again it's expanded into these parts instead.
 */
@Entity(tableName = "transaction_splits")
data class TransactionSplit(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val signature: String,   // parent transaction's dedup signature
    val amount: Double,
    val payee: String? = null
)
