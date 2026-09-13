package com.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A user-taught SMS format. Built by the "tag-the-fields" flow: the user pastes
 * one sample SMS, taps the amount and merchant words, and picks debit/credit.
 * [TemplateRegexBuilder] turns that into [regex] with named groups
 * (?<amount>...) and (?<merchant>...). The parser tries these before falling
 * back to the built-in patterns.
 */
@Entity(tableName = "sms_templates")
data class SmsTemplate(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    /** The account this format belongs to. */
    val accountId: String,
    /** User-friendly label, e.g. "ICICI UPI Debit". */
    val label: String,
    /** The original sample SMS the template was generated from. */
    val sampleMessage: String,
    /** Generated regex with named groups amount/merchant. */
    val regex: String,
    val type: TransactionType,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
