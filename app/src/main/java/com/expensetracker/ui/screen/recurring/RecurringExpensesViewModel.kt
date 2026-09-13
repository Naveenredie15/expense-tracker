package com.expensetracker.ui.screen.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.dao.AccountDao
import com.expensetracker.data.dao.RecurringExpenseDao
import com.expensetracker.data.entity.RecurringExpense
import com.expensetracker.data.entity.Transaction
import com.expensetracker.data.entity.TransactionType
import com.expensetracker.repository.TransactionRepository
import com.expensetracker.sms.TemplateRegexBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import javax.inject.Inject
import kotlin.math.abs

/** Whether a recurring bill has a matching transaction in the current cycle. */
data class RecurringStatus(
    val paid: Boolean,
    val paidOn: LocalDate? = null
)

@HiltViewModel
class RecurringExpensesViewModel @Inject constructor(
    private val dao: RecurringExpenseDao,
    private val transactionRepository: TransactionRepository,
    private val accountDao: AccountDao
) : ViewModel() {

    val expenses: StateFlow<List<RecurringExpense>> =
        dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Paid/Due status per recurring item for the current salary cycle. */
    val statuses: StateFlow<Map<String, RecurringStatus>> =
        combine(
            dao.getAll(),
            transactionRepository.getAllTransactions(),
            accountDao.getAllAccounts()
        ) { items, txns, accounts ->
            val salaryDay = accounts.firstOrNull { it.isSalaryAccount }?.salaryDay
            val (start, end) = cycleBounds(salaryDay)
            val cycleDebits = txns.filter {
                it.type == TransactionType.DEBIT && it.timestamp >= start && it.timestamp < end
            }
            items.associate { item ->
                val match = cycleDebits.firstOrNull { matches(item, it) }
                item.id to RecurringStatus(
                    paid = match != null,
                    paidOn = match?.timestamp?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** A transaction pays a recurring item if the amount matches AND (payee or pattern matches). */
    private fun matches(item: RecurringExpense, txn: Transaction): Boolean {
        if (abs(txn.amount - item.amount) >= 0.5) return false
        val rxMatch = item.matchRegex?.let {
            runCatching { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(txn.message) }.getOrDefault(false)
        } == true
        val pyMatch = item.matchPayee?.let { p ->
            val tp = txn.payee?.uppercase()
            val pp = p.uppercase()
            !tp.isNullOrBlank() && pp.isNotBlank() && (tp.contains(pp) || pp.contains(tp))
        } == true
        return rxMatch || pyMatch
    }

    /** Recent debits (last 40 days) whose amount matches [amount] — candidates to link. */
    suspend fun candidateTransactions(amount: Double): List<Transaction> {
        if (amount <= 0) return emptyList()
        val cutoff = Clock.System.now().minus(40, DateTimeUnit.DAY, TimeZone.currentSystemDefault())
        return transactionRepository.getAllTransactions().first()
            .filter {
                it.type == TransactionType.DEBIT && it.timestamp >= cutoff && abs(it.amount - amount) < 0.5
            }
            .sortedByDescending { it.timestamp }
    }

    fun addOrUpdate(
        existing: RecurringExpense?,
        name: String,
        amount: Double,
        dayOfMonth: Int?,
        category: String?,
        linked: Transaction?
    ) {
        viewModelScope.launch {
            // Build a generalized matcher regex from the linked transaction (amounts/dates
            // become wildcards, so next months' SMS still match). Keep the old matcher on
            // edit if the user didn't pick a new transaction.
            val matchRegex = when {
                linked != null -> runCatching {
                    TemplateRegexBuilder.build(linked.message, emptyList())
                }.getOrNull()
                else -> existing?.matchRegex
            }
            val matchPayee = when {
                linked != null -> linked.payee
                else -> existing?.matchPayee
            }
            if (existing == null) {
                dao.insert(
                    RecurringExpense(
                        name = name.trim(), amount = amount, dayOfMonth = dayOfMonth,
                        category = category, matchRegex = matchRegex, matchPayee = matchPayee
                    )
                )
            } else {
                dao.update(
                    existing.copy(
                        name = name.trim(), amount = amount, dayOfMonth = dayOfMonth,
                        category = category, matchRegex = matchRegex, matchPayee = matchPayee
                    )
                )
            }
            // Retroactively tag existing matching transactions as "Recurring" so past
            // occurrences (and the linked one) show under the Recurring category too.
            tagExistingAsRecurring(amount, matchRegex, matchPayee)
        }
    }

    private suspend fun tagExistingAsRecurring(amount: Double, matchRegex: String?, matchPayee: String?) {
        val rx = matchRegex?.let { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }
        if (rx == null && matchPayee == null) return
        val pp = matchPayee?.uppercase()
        transactionRepository.getAllTransactions().first()
            .filter {
                if (it.type != TransactionType.DEBIT || it.category == "Recurring") return@filter false
                if (abs(it.amount - amount) >= 0.5) return@filter false
                val tp = it.payee?.uppercase()
                (rx?.containsMatchIn(it.message) == true) ||
                    (pp != null && !tp.isNullOrBlank() && (tp.contains(pp) || pp.contains(tp)))
            }
            .forEach { transactionRepository.updateTransaction(it.copy(category = "Recurring")) }
    }

    fun setEnabled(expense: RecurringExpense, enabled: Boolean) {
        viewModelScope.launch { dao.update(expense.copy(enabled = enabled)) }
    }

    fun delete(expense: RecurringExpense) {
        viewModelScope.launch { dao.delete(expense) }
    }

    // Current salary cycle (salary date → next), or calendar month if no salary day.
    private fun cycleBounds(salaryDay: Int?): Pair<Instant, Instant> {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        fun clamp(y: Int, m: Int, d: Int): LocalDate {
            val last = LocalDate(y, m, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
            return LocalDate(y, m, minOf(d, last))
        }
        val start = if (salaryDay != null) {
            val thisMonth = clamp(today.year, today.monthNumber, salaryDay)
            if (today >= thisMonth) thisMonth
            else { val p = LocalDate(today.year, today.monthNumber, 1).minus(1, DateTimeUnit.MONTH); clamp(p.year, p.monthNumber, salaryDay) }
        } else LocalDate(today.year, today.monthNumber, 1)
        val endM = LocalDate(start.year, start.monthNumber, 1).plus(1, DateTimeUnit.MONTH)
        val end = if (salaryDay != null) clamp(endM.year, endM.monthNumber, salaryDay) else endM
        return start.atStartOfDayIn(tz) to end.atStartOfDayIn(tz)
    }
}
