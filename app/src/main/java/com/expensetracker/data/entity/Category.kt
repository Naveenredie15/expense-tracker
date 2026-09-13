package com.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: String = "#2196F3", // Default blue color
    val icon: String? = null, // Material icon name
    val isDefault: Boolean = false // For system-provided categories
) 