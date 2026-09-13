package com.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "payees")
data class Payee(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val linkedCategoryId: String? = null, // Auto-suggest category for this payee
    val transactionCount: Int = 0, // Track frequency for better suggestions
    val lastUsed: Long = System.currentTimeMillis()
) 