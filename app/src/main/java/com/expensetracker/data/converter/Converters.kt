package com.expensetracker.data.converter

import androidx.room.TypeConverter
import com.expensetracker.data.entity.AccountType
import com.expensetracker.data.entity.TransactionType
import kotlinx.datetime.Instant

class Converters {
    
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? {
        return instant?.epochSeconds
    }
    
    @TypeConverter
    fun toInstant(epochSeconds: Long?): Instant? {
        return epochSeconds?.let { Instant.fromEpochSeconds(it) }
    }
    
    @TypeConverter
    fun fromTransactionType(type: TransactionType): String {
        return type.name
    }
    
    @TypeConverter
    fun toTransactionType(type: String): TransactionType {
        return TransactionType.valueOf(type)
    }

    @TypeConverter
    fun fromAccountType(type: AccountType): String {
        return type.name
    }

    @TypeConverter
    fun toAccountType(type: String): AccountType {
        return AccountType.valueOf(type)
    }
} 