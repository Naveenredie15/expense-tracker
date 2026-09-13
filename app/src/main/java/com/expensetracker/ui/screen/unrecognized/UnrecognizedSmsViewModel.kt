package com.expensetracker.ui.screen.unrecognized

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.dao.AccountDao
import com.expensetracker.data.dao.IgnoredSmsPatternDao
import com.expensetracker.data.dao.SmsTemplateDao
import com.expensetracker.data.entity.Account
import com.expensetracker.data.entity.IgnoredSmsPattern
import com.expensetracker.data.entity.SmsTemplate
import com.expensetracker.data.entity.TransactionType
import com.expensetracker.repository.TransactionRepository
import com.expensetracker.sms.SmsReader
import com.expensetracker.sms.TemplateRegexBuilder
import com.expensetracker.sms.UnrecognizedSms
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One representative unrecognized SMS + how many similar ones share its pattern. */
data class UnrecognizedGroup(
    val signature: String,
    val representative: UnrecognizedSms,
    val count: Int
)

data class UnrecognizedUiState(
    val groups: List<UnrecognizedGroup> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class UnrecognizedSmsViewModel @Inject constructor(
    private val templateDao: SmsTemplateDao,
    accountDao: AccountDao,
    private val transactionRepository: TransactionRepository,
    private val ignoredPatternDao: IgnoredSmsPatternDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val smsReader = SmsReader(context)

    private val _uiState = MutableStateFlow(UnrecognizedUiState())
    val uiState: StateFlow<UnrecognizedUiState> = _uiState.asStateFlow()

    val accounts: StateFlow<List<Account>> =
        accountDao.getAllAccounts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { scan() }

    fun consumeMessage() { _uiState.value = _uiState.value.copy(message = null) }

    /** Collapse SMS that differ only by numbers/dates into one representative pattern. */
    private fun signatureOf(body: String): String =
        body.lowercase()
            .replace(Regex("""\d+"""), "#")
            .replace(Regex("""\s+"""), " ")
            .trim()

    fun scan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val ignored = try { ignoredPatternDao.getSignatures().toSet() } catch (_: Exception) { emptySet() }
            val raw = try { smsReader.readUnrecognizedBankSms() } catch (_: Exception) { emptyList() }
            val groups = raw
                .groupBy { signatureOf(it.body) }
                .filterKeys { it !in ignored }          // hide patterns the user marked as promo/ignore
                .map { (sig, list) ->
                    UnrecognizedGroup(sig, list.first(), list.size) // list is newest-first
                }
                .sortedByDescending { it.count }
            _uiState.value = _uiState.value.copy(groups = groups, isLoading = false)
        }
    }

    /** Mark a pattern as promotional / non-transaction so it never appears again. */
    fun ignorePattern(group: UnrecognizedGroup) {
        viewModelScope.launch {
            ignoredPatternDao.insert(
                IgnoredSmsPattern(signature = group.signature, sample = group.representative.body)
            )
            _uiState.value = _uiState.value.copy(message = "Marked as promotion · won't show again")
            scan()
        }
    }

    /**
     * Turn a tagged unrecognized SMS into a new per-account format, then re-sync so
     * this (and every similar) SMS is imported.
     */
    fun createFormat(
        sample: String,
        spans: List<TemplateRegexBuilder.Span>,
        account: Account,
        type: TransactionType
    ) {
        viewModelScope.launch {
            try {
                val regex = TemplateRegexBuilder.build(sample, spans)
                templateDao.insertTemplate(
                    SmsTemplate(
                        accountId = account.id,
                        label = "${account.label} · ${if (type == TransactionType.DEBIT) "Debit" else "Credit"}",
                        sampleMessage = sample,
                        regex = regex,
                        type = type
                    )
                )
                // Pull in everything the new format now recognizes (insert dedups).
                val transactions = smsReader.readBankSms(limitDays = 90)
                transactionRepository.insertTransactions(transactions)
                _uiState.value = _uiState.value.copy(message = "Format saved · transactions imported")
                scan() // refresh: the just-tagged pattern should disappear
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Couldn't save format: ${e.message}")
            }
        }
    }
}
