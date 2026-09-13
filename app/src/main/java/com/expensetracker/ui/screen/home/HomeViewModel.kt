package com.expensetracker.ui.screen.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.dao.AccountDao
import com.expensetracker.data.entity.Account
import com.expensetracker.data.entity.AccountType
import com.expensetracker.data.entity.Transaction
import com.expensetracker.data.entity.TransactionType
import com.expensetracker.data.entity.Category
import com.expensetracker.repository.CategoryRepository
import com.expensetracker.repository.TransactionRepository
import com.expensetracker.sms.SmsReader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import javax.inject.Inject

data class HomeUiState(
    val monthlyIncome: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val totalTransactions: Int = 0,
    val averageExpense: Double = 0.0,
    val recentTransactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Subtitle for the income/expense cards, e.g. "Since 25 Aug" or "This Month". */
    val periodLabel: String = "This Month",
    /** Expenses split by source (net of refunds). */
    val upiExpenses: Double = 0.0,
    val cardExpenses: Double = 0.0
)

/** Live bank balance for the Home "Current Balance" card. */
data class BankBalanceInfo(val label: String, val balance: Double?, val daysToSalary: Long? = null)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountDao: AccountDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val accounts: StateFlow<List<Account>> = accountDao.getAllAccounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Live bank (UPI) balance: the balance the user entered / last seen in SMS, plus the
     * net of every transaction on that account AFTER the balance's timestamp
     * (credits add, debits subtract). This carries over previous months' money, unlike
     * income − expenses. `balance` is null if the bank account has no balance set yet.
     */
    val bankBalance: StateFlow<BankBalanceInfo?> =
        combine(accounts, transactionRepository.getAllTransactions()) { accts, txns ->
            val bank = accts.firstOrNull { it.type == AccountType.BANK && it.isSalaryAccount }
                ?: accts.firstOrNull { it.type == AccountType.BANK }
            // Days until the next salary — how long this balance must last.
            val salaryDay = accts.firstOrNull { it.isSalaryAccount }?.salaryDay
            val daysToSalary = salaryDay?.let {
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                today.daysUntil(nextSalaryDate(today, it)).toLong()
            }
            when {
                bank == null -> null
                bank.latestBalance == null -> BankBalanceInfo(bank.label, null, daysToSalary)
                else -> {
                    val since = bank.latestBalanceAt ?: 0L
                    val net = txns
                        .filter { it.accountId == bank.id && it.timestamp.toEpochMilliseconds() > since }
                        .sumOf { if (it.type == TransactionType.CREDIT) it.amount else -it.amount }
                    BankBalanceInfo(bank.label, bank.latestBalance!! + net, daysToSalary)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun nextSalaryDate(today: LocalDate, salaryDay: Int): LocalDate {
        fun clamp(y: Int, m: Int, d: Int): LocalDate {
            val last = LocalDate(y, m, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
            return LocalDate(y, m, minOf(d, last))
        }
        val thisMonth = clamp(today.year, today.monthNumber, salaryDay)
        return if (today <= thisMonth) thisMonth
        else { val n = LocalDate(today.year, today.monthNumber, 1).plus(1, DateTimeUnit.MONTH); clamp(n.year, n.monthNumber, salaryDay) }
    }
    
    private val smsReader = SmsReader(context)
    
    init {
        loadData()
        initializeDefaultCategories()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            val tz = TimeZone.currentSystemDefault()
            val now = Clock.System.now()

            // Income/expenses are computed over the SALARY CYCLE (salary date → next
            // salary date). If no salary day is set, fall back to the calendar month.
            val allAccounts = accountDao.getAllAccountsOnce()
            val salaryDay = allAccounts.firstOrNull { it.isSalaryAccount }?.salaryDay
            val cardIds = allAccounts.filter { it.type == AccountType.CARD }.map { it.id }.toSet()
            val (periodStart, periodStartDate) = periodStartFor(salaryDay, tz, now)
            val periodLabel = if (periodStartDate != null)
                "Since ${periodStartDate.dayOfMonth} ${monthShort(periodStartDate.monthNumber)}"
            else "This Month"

            transactionRepository.getAllTransactions().collect { transactions ->
                val recentTransactions = transactions.take(10)

                // Average expense over ALL real debits (not period-scoped, excludes card payments).
                val allDebits = transactions.filter { it.type == TransactionType.DEBIT && !it.excludeFromTotals }
                val averageExpense = if (allDebits.isNotEmpty())
                    allDebits.sumOf { it.amount } / allDebits.size else 0.0

                // Period-scoped totals (salary cycle).
                val inPeriod = transactions.filter { it.timestamp >= periodStart }
                val income = inPeriod
                    .filter { it.type == TransactionType.CREDIT && !it.excludeFromTotals }
                    .sumOf { it.amount }
                val grossExpenses = inPeriod
                    .filter { it.type == TransactionType.DEBIT && !it.excludeFromTotals }
                    .sumOf { it.amount }
                val refunds = inPeriod
                    .filter { it.category == "Refund" }
                    .sumOf { it.amount }
                val expenses = (grossExpenses - refunds).coerceAtLeast(0.0)

                // Split expenses by source: card accounts vs everything else (UPI/bank).
                fun net(onCard: Boolean): Double {
                    val debits = inPeriod.filter {
                        it.type == TransactionType.DEBIT && !it.excludeFromTotals &&
                            (it.accountId in cardIds) == onCard
                    }.sumOf { it.amount }
                    val ref = inPeriod.filter {
                        it.category == "Refund" && (it.accountId in cardIds) == onCard
                    }.sumOf { it.amount }
                    return (debits - ref).coerceAtLeast(0.0)
                }
                val cardExpenses = net(onCard = true)
                val upiExpenses = net(onCard = false)

                _uiState.value = _uiState.value.copy(
                    recentTransactions = recentTransactions,
                    totalTransactions = transactions.size,
                    averageExpense = averageExpense,
                    monthlyIncome = income,
                    monthlyExpenses = expenses,
                    periodLabel = periodLabel,
                    upiExpenses = upiExpenses,
                    cardExpenses = cardExpenses
                )
            }
        }
    }

    /** Start of the current income period: the most recent salary date on/before today. */
    private fun periodStartFor(salaryDay: Int?, tz: TimeZone, now: Instant): Pair<Instant, LocalDate?> {
        val today = now.toLocalDateTime(tz).date
        if (salaryDay == null) {
            val monthStart = LocalDate(today.year, today.monthNumber, 1)
            return monthStart.atStartOfDayIn(tz) to null
        }
        fun salaryDateIn(year: Int, month: Int): LocalDate {
            val first = LocalDate(year, month, 1)
            val lastDay = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
            return LocalDate(year, month, minOf(salaryDay, lastDay))
        }
        val thisMonthSalary = salaryDateIn(today.year, today.monthNumber)
        val startDate = if (today >= thisMonthSalary) {
            thisMonthSalary
        } else {
            val prev = LocalDate(today.year, today.monthNumber, 1).minus(1, DateTimeUnit.MONTH)
            salaryDateIn(prev.year, prev.monthNumber)
        }
        return startDate.atStartOfDayIn(tz) to startDate
    }

    private fun monthShort(month: Int): String {
        val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        return names.getOrElse(month - 1) { "" }
    }
    
    private fun initializeDefaultCategories() {
        viewModelScope.launch {
            try {
                categoryRepository.insertDefaultCategories()
                // Standardize: merge/rename old categories on existing installs.
                categoryRepository.standardizeCategories()
                transactionRepository.reassignCategory("Fuel", "Transport")
                transactionRepository.reassignCategory("Transportation", "Transport")
                transactionRepository.reassignCategory("People", "Transfer")
                transactionRepository.clearCategory("Other") // merged into Uncategorized
            } catch (e: Exception) {
                // ignore
            }
        }
    }
    
    fun syncSmsTransactions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                val transactions = smsReader.readBankSms(limitDays = 30)
                transactionRepository.insertTransactions(transactions)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = if (transactions.isEmpty()) "No bank transactions found in SMS" else null
                )
                
                // Reload data after sync
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error syncing SMS: ${e.message}"
                )
            }
        }
    }
    
    fun splitTransaction(transaction: Transaction, parts: List<Pair<Double, String?>>) {
        viewModelScope.launch {
            try {
                transactionRepository.splitTransaction(transaction, parts)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to split: ${e.message}")
            }
        }
    }

    /** Move a card transaction to an adjacent billing cycle (0 = auto, ±N = shift). */
    fun setBillingCycleShift(transaction: Transaction, shift: Int) {
        viewModelScope.launch {
            try {
                transactionRepository.updateTransaction(transaction.copy(billingCycleShift = shift))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to update billing cycle: ${e.message}")
            }
        }
    }

    fun updateTransactionCategory(transaction: Transaction, categoryName: String?) {
        viewModelScope.launch {
            try {
                val updatedTransaction = transaction.copy(category = categoryName)
                transactionRepository.updateTransaction(updatedTransaction)
                // Remember it so future syncs/re-syncs keep this payee's category.
                if (categoryName != null && !transaction.payee.isNullOrBlank()) {
                    transactionRepository.rememberPayeeCategory(transaction.payee, categoryName)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to update category: ${e.message}")
            }
        }
    }
    
    private suspend fun <T> Flow<T>.asFlow(): Flow<T> = this
} 