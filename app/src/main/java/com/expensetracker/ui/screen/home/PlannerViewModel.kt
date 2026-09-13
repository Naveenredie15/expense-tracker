package com.expensetracker.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.dao.AccountDao
import com.expensetracker.data.dao.RecurringExpenseDao
import com.expensetracker.data.entity.Account
import com.expensetracker.data.entity.AccountType
import com.expensetracker.data.entity.TransactionType
import com.expensetracker.planner.BillingPlanner
import com.expensetracker.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class CardSummary(
    val label: String,
    val cycleSpend: Double,
    val cycleRefunds: Double,
    val outstanding: Double,
    val creditLimit: Double?,
    val available: Double?,
    val nextDueDate: LocalDate,
    val daysToDue: Long,
    val nextStatementDate: LocalDate,
    val daysToStatement: Long
)

data class UnpaidBill(
    val name: String,
    val amount: Double,
    val dayOfMonth: Int?
)

data class PlannerUiState(
    val cards: List<CardSummary> = emptyList(),
    val unpaidRecurring: List<UnpaidBill> = emptyList(),
    val bankBalance: Double? = null,
    val totalOutstanding: Double = 0.0,
    val disposableAfterBills: Double? = null,
    val monthlySalary: Double? = null,
    val cardSpend: Double = 0.0,             // total current-cycle spend across cards
    val recurringTotal: Double = 0.0,        // enabled recurring monthly expenses
    val nextMonthAfterCards: Double? = null, // salary − card spend − recurring
    val salaryDate: LocalDate? = null,
    val daysToSalary: Long? = null,
    val hasData: Boolean = false
)

@HiltViewModel
class PlannerViewModel @Inject constructor(
    accountDao: AccountDao,
    transactionRepository: TransactionRepository,
    recurringExpenseDao: RecurringExpenseDao
) : ViewModel() {

    val uiState: StateFlow<PlannerUiState> =
        combine(
            accountDao.getAllAccounts(),
            transactionRepository.getAllTransactions(),
            recurringExpenseDao.getAll()
        ) { accounts, transactions, recurring ->
            val today = LocalDate.now()

            fun dateOf(epochSeconds: Long): LocalDate =
                Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate()

            val cards = accounts
                .filter { it.type == AccountType.CARD && it.statementDay != null && it.dueDay != null }
                .map { card ->
                    val mine = transactions.filter { it.accountId == card.id }
                    val debits = mine.filter { it.type == TransactionType.DEBIT }
                        .map {
                            // Bucket by the (possibly user-shifted) cycle's statement date,
                            // so a manually-moved spend counts toward the chosen cycle.
                            val cycle = BillingPlanner.cycleFor(
                                dateOf(it.timestamp.epochSeconds),
                                card.statementDay!!, card.dueDay!!, it.billingCycleShift
                            )
                            cycle.end to it.amount
                        }
                    // All card credits reduce outstanding (refunds + bill payments).
                    val payments = mine.filter { it.type == TransactionType.CREDIT }
                        .map { dateOf(it.timestamp.epochSeconds) to it.amount }
                    // Refunds also offset that cycle's spend; bucket them by cycle like debits.
                    val refunds = mine
                        .filter { it.type == TransactionType.CREDIT && it.category == "Refund" }
                        .map {
                            val cycle = BillingPlanner.cycleFor(
                                dateOf(it.timestamp.epochSeconds),
                                card.statementDay!!, card.dueDay!!, it.billingCycleShift
                            )
                            cycle.end to it.amount
                        }
                    val s = BillingPlanner.cardStatus(
                        today = today,
                        statementDay = card.statementDay!!,
                        dueDay = card.dueDay!!,
                        creditLimit = card.creditLimit,
                        cardDebits = debits,
                        cardPayments = payments,
                        cardRefunds = refunds
                    )
                    CardSummary(
                        label = card.label,
                        cycleSpend = s.cycleSpend,
                        cycleRefunds = s.cycleRefunds,
                        outstanding = s.outstanding,
                        creditLimit = s.creditLimit,
                        available = s.available,
                        nextDueDate = s.nextDueDate,
                        daysToDue = s.daysToDue,
                        nextStatementDate = s.nextStatementDate,
                        daysToStatement = s.daysToStatement
                    )
                }

            val salaryAccount = accounts.firstOrNull { it.type == AccountType.BANK && it.isSalaryAccount }
                ?: accounts.firstOrNull { it.type == AccountType.BANK }
            val bankBalance = salaryAccount?.latestBalance
            val salaryAcct = accounts.firstOrNull { it.isSalaryAccount }
            val monthlySalary = salaryAcct?.monthlySalary
            val totalOutstanding = cards.sumOf { it.outstanding }
            val recurringTotal = recurring.filter { it.enabled }.sumOf { it.amount }
            val salaryDate = salaryAcct?.salaryDay?.let { BillingPlanner.nextSalaryDate(today, it) }
                ?: BillingPlanner.nextSalaryDate(today)

            // Unpaid recurring bills this cycle: enabled items with no matching debit
            // (amount + payee/regex) since the current salary-cycle start.
            val cycleStart = salaryCycleStart(today, salaryAcct?.salaryDay)
            val cycleDebits = transactions.filter {
                it.type == TransactionType.DEBIT && !dateOf(it.timestamp.epochSeconds).isBefore(cycleStart)
            }
            val unpaidRecurring = recurring.filter { it.enabled }
                .filter { item -> cycleDebits.none { recurringMatches(item, it) } }
                .map { UnpaidBill(it.name, it.amount, it.dayOfMonth) }

            PlannerUiState(
                cards = cards,
                unpaidRecurring = unpaidRecurring,
                bankBalance = bankBalance,
                totalOutstanding = totalOutstanding,
                disposableAfterBills = bankBalance?.let { it - totalOutstanding },
                monthlySalary = monthlySalary,
                cardSpend = totalOutstanding,
                recurringTotal = recurringTotal,
                // What's left of next salary once this cycle's card spend and the
                // fixed monthly bills are paid.
                nextMonthAfterCards = monthlySalary?.let { it - totalOutstanding - recurringTotal },
                salaryDate = salaryDate,
                daysToSalary = java.time.temporal.ChronoUnit.DAYS.between(today, salaryDate),
                hasData = cards.isNotEmpty() || bankBalance != null || monthlySalary != null
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlannerUiState())

    private fun clampDay(year: Int, month: Int, day: Int): LocalDate {
        val len = java.time.YearMonth.of(year, month).lengthOfMonth()
        return LocalDate.of(year, month, minOf(day, len))
    }

    /** Start of the current salary cycle (or calendar month if no salary day). */
    private fun salaryCycleStart(today: LocalDate, salaryDay: Int?): LocalDate {
        if (salaryDay == null) return today.withDayOfMonth(1)
        val thisMonth = clampDay(today.year, today.monthValue, salaryDay)
        return if (!today.isBefore(thisMonth)) thisMonth
        else today.minusMonths(1).let { clampDay(it.year, it.monthValue, salaryDay) }
    }

    /** A debit matches a recurring item: same amount AND (payee contains OR regex). */
    private fun recurringMatches(item: com.expensetracker.data.entity.RecurringExpense, txn: com.expensetracker.data.entity.Transaction): Boolean {
        if (kotlin.math.abs(txn.amount - item.amount) >= 0.5) return false
        val rx = item.matchRegex?.let { runCatching { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(txn.message) }.getOrDefault(false) } == true
        val py = item.matchPayee?.let { p ->
            val tp = txn.payee?.uppercase(); val pp = p.uppercase()
            !tp.isNullOrBlank() && pp.isNotBlank() && (tp.contains(pp) || pp.contains(tp))
        } == true
        return rx || py
    }
}
