package com.expensetracker.ui.screen.formats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.dao.AccountDao
import com.expensetracker.data.dao.SmsTemplateDao
import com.expensetracker.data.entity.SmsTemplate
import com.expensetracker.data.entity.TransactionType
import com.expensetracker.repository.TransactionRepository
import com.expensetracker.sms.SmsReader
import com.expensetracker.sms.TemplateRegexBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A saved format plus the label of the account it belongs to. */
data class FormatRow(
    val template: SmsTemplate,
    val accountLabel: String,
    val accountLast4: String
)

@HiltViewModel
class SmsFormatsViewModel @Inject constructor(
    private val templateDao: SmsTemplateDao,
    accountDao: AccountDao,
    private val transactionRepository: TransactionRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val smsReader = SmsReader(context)

    val formats: StateFlow<List<FormatRow>> =
        combine(templateDao.getAllTemplates(), accountDao.getAllAccounts()) { templates, accounts ->
            val byId = accounts.associateBy { it.id }
            templates
                .map {
                    FormatRow(
                        template = it,
                        accountLabel = byId[it.accountId]?.label ?: "(deleted account)",
                        accountLast4 = byId[it.accountId]?.last4 ?: "----"
                    )
                }
                .sortedWith(compareBy({ it.accountLabel }, { it.template.type }))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Re-derive the tagged spans from a saved format so the editor can pre-highlight them. */
    fun spansFor(template: SmsTemplate): List<TemplateRegexBuilder.Span> {
        val match = runCatching {
            Regex(template.regex, RegexOption.IGNORE_CASE).find(template.sampleMessage)
        }.getOrNull() ?: return emptyList()
        return buildList {
            match.groups["amount"]?.range?.let {
                add(TemplateRegexBuilder.Span(it.first, it.last + 1, TemplateRegexBuilder.Field.AMOUNT))
            }
            match.groups["merchant"]?.range?.let {
                add(TemplateRegexBuilder.Span(it.first, it.last + 1, TemplateRegexBuilder.Field.MERCHANT))
            }
        }
    }

    fun updateFormat(
        template: SmsTemplate,
        sample: String,
        spans: List<TemplateRegexBuilder.Span>,
        type: TransactionType
    ) {
        viewModelScope.launch {
            templateDao.updateTemplate(
                template.copy(
                    sampleMessage = sample,
                    regex = TemplateRegexBuilder.build(sample, spans),
                    type = type
                )
            )
            resync()
        }
    }

    fun deleteFormat(template: SmsTemplate) {
        viewModelScope.launch {
            templateDao.deleteTemplate(template)
            resync()
        }
    }

    /** Rebuild transactions so a changed/removed format takes effect (keeps tombstones). */
    private suspend fun resync() {
        val transactions = try { smsReader.readBankSms(limitDays = 90) } catch (_: Exception) { return }
        transactionRepository.clearTransactionsKeepIgnored()
        transactionRepository.insertTransactions(transactions)
    }
}
