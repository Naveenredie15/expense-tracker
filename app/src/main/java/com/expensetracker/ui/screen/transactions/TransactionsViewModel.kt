package com.expensetracker.ui.screen.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.dao.AccountDao
import com.expensetracker.data.entity.Account
import com.expensetracker.data.entity.Transaction
import com.expensetracker.data.entity.TransactionType
import com.expensetracker.data.entity.Category
import com.expensetracker.repository.TransactionRepository
import com.expensetracker.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedTransactionIds: Set<String> = emptySet(),
    val isAllSelected: Boolean = false,
    /** Account filter; null = show all accounts. */
    val selectedAccountId: String? = null,
    /** Type filter; null = all, else DEBIT/CREDIT. */
    val selectedType: TransactionType? = null,
    /** Salary-cycle month filter; null = all time, 0 = this month, 1 = last month, … */
    val selectedMonthOffset: Int? = null,
    /** Category filter; null = all, "Uncategorized" = no category, else category name. */
    val selectedCategory: String? = null
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    accountDao: AccountDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    /** All transactions unfiltered; [uiState].transactions is the filtered view. */
    private var allTransactions: List<Transaction> = emptyList()
    /** Salary day of the salary account, if set — makes "month" a salary cycle. */
    private var salaryDay: Int? = null

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

    init {
        observeTransactions()
        viewModelScope.launch {
            accounts.collect { accts ->
                salaryDay = accts.firstOrNull { it.isSalaryAccount }?.salaryDay
                // Re-apply in case a month filter is active and the salary day loaded late.
                _uiState.value = _uiState.value.copy(transactions = applyFilters())
            }
        }
    }

    private fun observeTransactions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                transactionRepository.getAllTransactions().collect { transactions ->
                    allTransactions = transactions
                    _uiState.value = _uiState.value.copy(
                        transactions = applyFilters(),
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    /** Apply account + type + month + category filters to the full list. */
    private fun applyFilters(): List<Transaction> {
        val s = _uiState.value
        var list = allTransactions
        s.selectedAccountId?.let { id -> list = list.filter { it.accountId == id } }
        s.selectedType?.let { t -> list = list.filter { it.type == t } }
        s.selectedMonthOffset?.let { off ->
            val (start, end) = monthBounds(off)
            list = list.filter { it.timestamp >= start && it.timestamp < end }
        }
        s.selectedCategory?.let { cat ->
            list = if (cat == UNCATEGORIZED) list.filter { it.category.isNullOrBlank() }
                   else list.filter { it.category == cat }
        }
        return list
    }

    private fun refilter() {
        _uiState.value = _uiState.value.copy(
            transactions = applyFilters(),
            selectedTransactionIds = emptySet(),
            isAllSelected = false
        )
    }

    fun setAccountFilter(accountId: String?) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
        refilter()
    }

    fun setTypeFilter(type: TransactionType?) {
        _uiState.value = _uiState.value.copy(selectedType = type)
        refilter()
    }

    fun setMonthFilter(offset: Int?) {
        _uiState.value = _uiState.value.copy(selectedMonthOffset = offset)
        refilter()
    }

    fun setCategoryFilter(category: String?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        refilter()
    }

    companion object { const val UNCATEGORIZED = "Uncategorized" }

    // --- Salary-cycle month bounds ("month" = salary date → next salary date) ---

    private fun clampDay(year: Int, month: Int, day: Int): LocalDate {
        val first = LocalDate(year, month, 1)
        val last = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
        return LocalDate(year, month, minOf(day, last))
    }

    private fun salaryCycleStart(today: LocalDate, sd: Int): LocalDate {
        val thisMonth = clampDay(today.year, today.monthNumber, sd)
        return if (today >= thisMonth) thisMonth
        else {
            val prev = LocalDate(today.year, today.monthNumber, 1).minus(1, DateTimeUnit.MONTH)
            clampDay(prev.year, prev.monthNumber, sd)
        }
    }

    /** [offset] cycles back from the current one (0 = current). */
    private fun monthBounds(offset: Int): Pair<Instant, Instant> {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val sd = salaryDay
        val curStart = if (sd != null) salaryCycleStart(today, sd) else LocalDate(today.year, today.monthNumber, 1)
        val startMonth = LocalDate(curStart.year, curStart.monthNumber, 1).minus(offset.toLong(), DateTimeUnit.MONTH)
        val start = if (sd != null) clampDay(startMonth.year, startMonth.monthNumber, sd)
                    else LocalDate(startMonth.year, startMonth.monthNumber, 1)
        val endMonth = LocalDate(start.year, start.monthNumber, 1).plus(1, DateTimeUnit.MONTH)
        val end = if (sd != null) clampDay(endMonth.year, endMonth.monthNumber, sd)
                  else LocalDate(endMonth.year, endMonth.monthNumber, 1)
        return start.atStartOfDayIn(tz) to end.atStartOfDayIn(tz)
    }
    
    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                transactionRepository.deleteTransaction(transaction)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun splitTransaction(transaction: Transaction, parts: List<Pair<Double, String?>>) {
        viewModelScope.launch {
            try {
                transactionRepository.splitTransaction(transaction, parts)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }
    
    fun enterSelectionMode() {
        _uiState.value = _uiState.value.copy(isSelectionMode = true)
    }
    
    fun exitSelectionMode() {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = false,
            selectedTransactionIds = emptySet(),
            isAllSelected = false
        )
    }
    
    fun toggleTransactionSelection(transactionId: String) {
        val currentSelected = _uiState.value.selectedTransactionIds
        val newSelected = if (currentSelected.contains(transactionId)) {
            currentSelected - transactionId
        } else {
            currentSelected + transactionId
        }
        
        val isAllSelected = newSelected.size == _uiState.value.transactions.size
        
        _uiState.value = _uiState.value.copy(
            selectedTransactionIds = newSelected,
            isAllSelected = isAllSelected
        )
    }
    
    fun toggleSelectAll() {
        val allTransactionIds = _uiState.value.transactions.map { it.id }.toSet()
        val newSelected = if (_uiState.value.isAllSelected) {
            emptySet()
        } else {
            allTransactionIds
        }
        
        _uiState.value = _uiState.value.copy(
            selectedTransactionIds = newSelected,
            isAllSelected = !_uiState.value.isAllSelected
        )
    }
    
    fun deleteSelectedTransactions() {
        viewModelScope.launch {
            try {
                val selectedIds = _uiState.value.selectedTransactionIds.toList()
                selectedIds.forEach { id ->
                    transactionRepository.deleteTransactionById(id)
                }
                // Exit selection mode after deletion
                exitSelectionMode()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }
    
    /** Move a card transaction to an adjacent billing cycle (0 = auto, ±N = shift). */
    fun setBillingCycleShift(transaction: Transaction, shift: Int) {
        viewModelScope.launch {
            try {
                transactionRepository.updateTransaction(transaction.copy(billingCycleShift = shift))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun updateTransactionCategory(transaction: Transaction, categoryName: String?) {
        viewModelScope.launch {
            try {
                val updatedTransaction = transaction.copy(category = categoryName)
                transactionRepository.updateTransaction(updatedTransaction)

                // Apply smart categorization to other transactions with same payee
                if (categoryName != null && !transaction.payee.isNullOrBlank()) {
                    applyCategoryToSimilarPayees(transaction.payee, categoryName)
                    // Remember it so future syncs/re-syncs keep this payee's category.
                    transactionRepository.rememberPayeeCategory(transaction.payee, categoryName)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }
    
    private suspend fun applyCategoryToSimilarPayees(payee: String, categoryName: String) {
        try {
            // Get all uncategorized transactions with the same payee
            val transactions = _uiState.value.transactions.filter { 
                it.payee == payee && it.category.isNullOrBlank() 
            }
            
            // Update them with the new category
            transactions.forEach { transaction ->
                val updatedTransaction = transaction.copy(category = categoryName)
                transactionRepository.updateTransaction(updatedTransaction)
            }
        } catch (e: Exception) {
            // Silent fail for smart categorization
        }
    }
    
    suspend fun getSuggestedCategory(payee: String?): String? {
        return if (!payee.isNullOrBlank()) {
            transactionRepository.getLastCategoryForPayee(payee)
        } else {
            null
        }
    }
} 