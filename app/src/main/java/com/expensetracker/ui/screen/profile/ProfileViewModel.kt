package com.expensetracker.ui.screen.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.dao.AccountDao
import com.expensetracker.data.dao.SmsTemplateDao
import com.expensetracker.data.entity.Account
import com.expensetracker.data.entity.AccountType
import com.expensetracker.repository.TransactionRepository
import com.expensetracker.sms.SmsReader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val templateDao: SmsTemplateDao,
    private val transactionRepository: TransactionRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val smsReader = SmsReader(context)

    val accounts: StateFlow<List<Account>> =
        accountDao.getAllAccounts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** One-shot user-facing message; shown as a snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    suspend fun getAccount(accountId: String): Account? = accountDao.getAccountById(accountId)

    /**
     * Create an account from its details only. SMS formats are NOT tagged here — after
     * adding, the user is taken to the "Unrecognized SMS" flow to tag whatever actually
     * arrives for this account.
     */
    fun addAccount(
        last4: String,
        label: String,
        type: AccountType,
        creditLimit: Double? = null,
        statementDay: Int? = null,
        dueDay: Int? = null,
        isSalaryAccount: Boolean = false,
        monthlySalary: Double? = null,
        salaryDay: Int? = null,
        latestBalance: Double? = null
    ) {
        viewModelScope.launch {
            accountDao.insertAccount(
                Account(
                    last4 = last4.trim(),
                    label = label.trim(),
                    type = type,
                    creditLimit = creditLimit,
                    statementDay = statementDay,
                    dueDay = dueDay,
                    isSalaryAccount = isSalaryAccount,
                    monthlySalary = monthlySalary,
                    salaryDay = salaryDay,
                    latestBalance = latestBalance,
                    latestBalanceAt = latestBalance?.let { System.currentTimeMillis() }
                )
            )
        }
    }

    /**
     * Update an existing account's details (formats are managed separately). If the
     * account type or last-4 changed (both affect how transactions are classified /
     * balances captured), re-sync so the dashboard stays consistent.
     */
    fun updateAccount(
        accountId: String,
        last4: String,
        label: String,
        type: AccountType,
        creditLimit: Double? = null,
        statementDay: Int? = null,
        dueDay: Int? = null,
        isSalaryAccount: Boolean = false,
        monthlySalary: Double? = null,
        salaryDay: Int? = null,
        latestBalance: Double? = null
    ) {
        viewModelScope.launch {
            val existing = accountDao.getAccountById(accountId) ?: return@launch
            val balanceChanged = latestBalance != existing.latestBalance
            val updated = existing.copy(
                last4 = last4.trim(),
                label = label.trim(),
                type = type,
                creditLimit = creditLimit,
                statementDay = statementDay,
                dueDay = dueDay,
                isSalaryAccount = isSalaryAccount,
                monthlySalary = monthlySalary,
                salaryDay = salaryDay,
                latestBalance = latestBalance,
                latestBalanceAt = if (balanceChanged && latestBalance != null) System.currentTimeMillis()
                                  else existing.latestBalanceAt
            )
            accountDao.updateAccount(updated)

            if (existing.type != type || existing.last4.trim() != last4.trim()) {
                resyncAllTransactions()
            } else {
                _message.value = "Account updated"
            }
        }
    }

    /** Rebuild all transactions from SMS using current accounts + templates (keeps tombstones). */
    private suspend fun resyncAllTransactions() {
        _message.value = "Reassigning transactions…"
        val transactions = try {
            smsReader.readBankSms(limitDays = 90)
        } catch (e: Exception) {
            _message.value = "Couldn't reassign transactions: ${e.message}"
            return
        }
        transactionRepository.clearTransactionsKeepIgnored()
        transactionRepository.insertTransactions(transactions)
        _message.value = "Transactions reassigned"
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            templateDao.deleteTemplatesForAccount(account.id)
            accountDao.deleteAccount(account)
            // Its transactions are now orphaned/misattributed; rebuild from SMS.
            resyncAllTransactions()
        }
    }

    fun clearAllTransactions() {
        viewModelScope.launch { transactionRepository.clearAllTransactions() }
    }
}
