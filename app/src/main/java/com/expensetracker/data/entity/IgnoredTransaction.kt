package com.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A tombstone for a transaction the user deleted. When the SMS inbox is re-synced,
 * any parsed transaction whose [signature] is present here is skipped, so deleted
 * transactions never reappear.
 *
 * The signature mirrors the dedup key (sender + message + timestamp) so it matches
 * exactly what a re-parse of the same SMS would produce.
 */
@Entity(tableName = "ignored_transactions")
data class IgnoredTransaction(
    @PrimaryKey
    val signature: String,
    val createdAt: Long = System.currentTimeMillis()
)
