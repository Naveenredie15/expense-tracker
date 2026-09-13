package com.expensetracker.data.dao

import androidx.room.*
import com.expensetracker.data.entity.SmsTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsTemplateDao {

    @Query("SELECT * FROM sms_templates ORDER BY createdAt DESC")
    fun getAllTemplates(): Flow<List<SmsTemplate>>

    @Query("SELECT * FROM sms_templates WHERE enabled = 1")
    suspend fun getEnabledTemplates(): List<SmsTemplate>

    @Query("SELECT * FROM sms_templates WHERE accountId = :accountId")
    suspend fun getTemplatesForAccount(accountId: String): List<SmsTemplate>

    @Query("DELETE FROM sms_templates WHERE accountId = :accountId")
    suspend fun deleteTemplatesForAccount(accountId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: SmsTemplate)

    @Update
    suspend fun updateTemplate(template: SmsTemplate)

    @Delete
    suspend fun deleteTemplate(template: SmsTemplate)
}
