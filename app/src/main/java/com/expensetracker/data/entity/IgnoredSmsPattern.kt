package com.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-marked "this pattern is not a transaction" rule (e.g. a promotional SMS).
 * [signature] is the normalized message shape (digits collapsed) so every similar
 * SMS stays out of the unrecognized-SMS inbox forever.
 */
@Entity(tableName = "ignored_sms_patterns")
data class IgnoredSmsPattern(
    @PrimaryKey
    val signature: String,
    val sample: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
