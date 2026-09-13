package com.expensetracker.ui.screen.insights

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.dao.AccountDao
import com.expensetracker.data.dao.CategoryAmount
import com.expensetracker.data.dao.PayeeAmount
import com.expensetracker.data.entity.Account
import com.expensetracker.data.entity.Transaction
import com.expensetracker.data.entity.TransactionType
import com.expensetracker.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import javax.inject.Inject

data class ChartData(val label: String, val value: Float, val date: LocalDate)
data class PeriodData(val income: Double, val expenses: Double, val period: String)

/** One slice of the category spend donut. */
data class CategorySlice(
    val label: String,
    val amount: Double,
    val percent: Double,
    val color: Color
)

enum class TimePeriod { DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY }

data class InsightsUiState(
    val selectedPeriod: TimePeriod = TimePeriod.MONTHLY,
    val selectedAccountId: String? = null,     // null = all accounts
    val accounts: List<Account> = emptyList(),
    val periodRangeLabel: String = "",         // e.g. "25 Aug – 24 Sep"
    val monthlyIncome: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val savingsRate: Double = 0.0,
    val categoryExpenses: List<CategoryAmount> = emptyList(),
    val categoryBreakdown: List<CategorySlice> = emptyList(),
    val payeeSpending: List<PayeeAmount> = emptyList(),
    val totalTransactions: Int = 0,
    val avgTransactionAmount: Double = 0.0,
    val largestExpense: Double = 0.0,
    val expenseChartData: List<ChartData> = emptyList(),
    val incomeChartData: List<ChartData> = emptyList(),
    val periodData: List<PeriodData> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountDao: AccountDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    // A stable, colour-blind-friendly palette; index 0.. by descending amount.
    private val palette = listOf(
        Color(0xFF4C72B0), Color(0xFFDD8452), Color(0xFF55A868), Color(0xFFC44E52),
        Color(0xFF8172B3), Color(0xFF937860), Color(0xFFDA8BC3), Color(0xFF8C8C8C),
        Color(0xFFCCB974), Color(0xFF64B5CD)
    )
    private val uncategorizedColor = Color(0xFFB0BEC5)
    private val recurringColor = Color(0xFF7E57C2)

    init {
        viewModelScope.launch {
            accountDao.getAllAccounts().collect { accts ->
                _uiState.value = _uiState.value.copy(accounts = accts)
            }
        }
        loadInsights()
    }

    fun selectTimePeriod(period: TimePeriod) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period)
        loadInsights()
    }

    fun setAccountFilter(accountId: String?) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
        loadInsights()
    }

    fun refreshInsights() = loadInsights()

    fun getTransactionsForPayee(payeeName: String) =
        transactionRepository.getTransactionsByPayee(payeeName)

    private fun loadInsights() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val period = _uiState.value.selectedPeriod
                val accountId = _uiState.value.selectedAccountId
                val tz = TimeZone.currentSystemDefault()
                val now = Clock.System.now().toLocalDateTime(tz)
                // Monthly period follows the SALARY CYCLE (salary date → next salary date),
                // matching Home; other periods stay calendar-based.
                val salaryDay = accountDao.getAllAccountsOnce().firstOrNull { it.isSalaryAccount }?.salaryDay
                val (start, end) = getPeriodBounds(now, period, salaryDay)
                val rangeLabel = run {
                    val s = start.toLocalDateTime(tz).date
                    val e = end.toLocalDateTime(tz).date.minus(1, DateTimeUnit.DAY)
                    "${s.dayOfMonth} ${monthShort(s.monthNumber)} – ${e.dayOfMonth} ${monthShort(e.monthNumber)}"
                }

                val all = transactionRepository.getAllTransactions().first()
                val scoped = if (accountId == null) all else all.filter { it.accountId == accountId }
                val inPeriod = scoped.filter { it.timestamp >= start && it.timestamp < end }

                val income = inPeriod.filter { it.type == TransactionType.CREDIT && !it.excludeFromTotals }
                    .sumOf { it.amount }
                val debits = inPeriod.filter { it.type == TransactionType.DEBIT && !it.excludeFromTotals }
                val grossExpenses = debits.sumOf { it.amount }
                val refunds = inPeriod.filter { it.category == "Refund" }.sumOf { it.amount }
                val expenses = (grossExpenses - refunds).coerceAtLeast(0.0)
                val savingsRate = if (income > 0) ((income - expenses) / income) * 100 else 0.0

                // Category breakdown (includes "Uncategorized"); spending only.
                val byCategory = debits
                    .groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: "Uncategorized" }
                    .mapValues { (_, list) -> list.sumOf { it.amount } }
                    .entries.sortedByDescending { it.value }
                val catTotal = byCategory.sumOf { it.value }
                val slices = byCategory.mapIndexed { i, e ->
                    CategorySlice(
                        label = e.key,
                        amount = e.value,
                        percent = if (catTotal > 0) e.value / catTotal * 100 else 0.0,
                        color = when (e.key) {
                            "Uncategorized" -> uncategorizedColor
                            "Recurring" -> recurringColor
                            else -> palette[i % palette.size]
                        }
                    )
                }
                val categoryExpenses = byCategory.map { CategoryAmount(it.key, it.value) }

                val payeeSpending = debits.filter { !it.payee.isNullOrBlank() }
                    .groupBy { it.payee!! }
                    .mapValues { (_, list) -> list.sumOf { it.amount } }
                    .entries.sortedByDescending { it.value }.take(10)
                    .map { PayeeAmount(it.key, it.value) }

                val avg = if (inPeriod.isNotEmpty()) inPeriod.map { it.amount }.average() else 0.0
                val largest = debits.maxOfOrNull { it.amount } ?: 0.0

                val (expenseChart, incomeChart, periodData) = generateChartData(period, now, scoped, tz, salaryDay)

                _uiState.value = _uiState.value.copy(
                    monthlyIncome = income,
                    monthlyExpenses = expenses,
                    savingsRate = savingsRate,
                    periodRangeLabel = rangeLabel,
                    categoryExpenses = categoryExpenses,
                    categoryBreakdown = slices,
                    payeeSpending = payeeSpending,
                    totalTransactions = inPeriod.size,
                    avgTransactionAmount = avg,
                    largestExpense = largest,
                    expenseChartData = expenseChart,
                    incomeChartData = incomeChart,
                    periodData = periodData,
                    isLoading = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    /** Sum expenses/income for a scoped list within [start, end). */
    private fun sums(list: List<Transaction>, start: Instant, end: Instant): Pair<Double, Double> {
        val inRange = list.filter { it.timestamp >= start && it.timestamp < end }
        val income = inRange.filter { it.type == TransactionType.CREDIT && !it.excludeFromTotals }.sumOf { it.amount }
        val gross = inRange.filter { it.type == TransactionType.DEBIT && !it.excludeFromTotals }.sumOf { it.amount }
        val refunds = inRange.filter { it.category == "Refund" }.sumOf { it.amount }
        return income to (gross - refunds).coerceAtLeast(0.0)
    }

    private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private fun monthShort(month: Int): String = monthNames.getOrElse(month - 1) { "" }

    private fun clampDay(year: Int, month: Int, day: Int): LocalDate {
        val first = LocalDate(year, month, 1)
        val last = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
        return LocalDate(year, month, minOf(day, last))
    }

    /** Most recent salary date on/before [today]. */
    private fun salaryCycleStart(today: LocalDate, salaryDay: Int): LocalDate {
        val thisMonth = clampDay(today.year, today.monthNumber, salaryDay)
        return if (today >= thisMonth) thisMonth
        else {
            val prev = LocalDate(today.year, today.monthNumber, 1).minus(1, DateTimeUnit.MONTH)
            clampDay(prev.year, prev.monthNumber, salaryDay)
        }
    }

    private fun generateChartData(
        period: TimePeriod,
        current: LocalDateTime,
        scoped: List<Transaction>,
        tz: TimeZone,
        salaryDay: Int?
    ): Triple<List<ChartData>, List<ChartData>, List<PeriodData>> {
        val expense = mutableListOf<ChartData>()
        val income = mutableListOf<ChartData>()
        val periodData = mutableListOf<PeriodData>()

        fun bucket(label: String, date: LocalDate, start: Instant, end: Instant) {
            val (inc, exp) = sums(scoped, start, end)
            expense.add(ChartData(label, exp.toFloat(), date))
            income.add(ChartData(label, inc.toFloat(), date))
            periodData.add(PeriodData(inc, exp, label))
        }

        when (period) {
            TimePeriod.DAILY -> repeat(7) { i ->
                val d = current.date.minus(6 - i, DateTimeUnit.DAY)
                bucket("${d.dayOfMonth}/${d.monthNumber}", d,
                    d.atTime(0, 0).toInstant(tz), d.plus(1, DateTimeUnit.DAY).atTime(0, 0).toInstant(tz))
            }
            TimePeriod.WEEKLY -> repeat(8) { i ->
                val ws = current.date.minus((7 - i) * 7, DateTimeUnit.DAY)
                bucket("W${i + 1}", ws,
                    ws.atTime(0, 0).toInstant(tz), ws.plus(7, DateTimeUnit.DAY).atTime(0, 0).toInstant(tz))
            }
            TimePeriod.MONTHLY -> {
                if (salaryDay != null) {
                    // Salary-cycle buckets: each "month" runs salary date → next salary date.
                    val curStart = salaryCycleStart(current.date, salaryDay)
                    repeat(6) { i ->
                        val sm = LocalDate(curStart.year, curStart.monthNumber, 1).minus((5 - i).toLong(), DateTimeUnit.MONTH)
                        val s = clampDay(sm.year, sm.monthNumber, salaryDay)
                        val em = LocalDate(s.year, s.monthNumber, 1).plus(1, DateTimeUnit.MONTH)
                        val e = clampDay(em.year, em.monthNumber, salaryDay)
                        bucket(monthShort(s.monthNumber), s, s.atTime(0, 0).toInstant(tz), e.atTime(0, 0).toInstant(tz))
                    }
                } else {
                    repeat(6) { i ->
                        val m = current.date.minus(5 - i, DateTimeUnit.MONTH)
                        val s = LocalDate(m.year, m.month, 1)
                        bucket(monthShort(s.monthNumber), s,
                            s.atTime(0, 0).toInstant(tz), s.plus(1, DateTimeUnit.MONTH).atTime(0, 0).toInstant(tz))
                    }
                }
            }
            TimePeriod.QUARTERLY -> repeat(4) { i ->
                val q = current.date.minus((3 - i) * 3, DateTimeUnit.MONTH)
                val s = LocalDate(q.year, q.month, 1)
                bucket("Q${i + 1}", s,
                    s.atTime(0, 0).toInstant(tz), s.plus(3, DateTimeUnit.MONTH).atTime(0, 0).toInstant(tz))
            }
            TimePeriod.YEARLY -> repeat(3) { i ->
                val y = current.year - (2 - i)
                bucket(y.toString(), LocalDate(y, 1, 1),
                    LocalDate(y, 1, 1).atTime(0, 0).toInstant(tz), LocalDate(y + 1, 1, 1).atTime(0, 0).toInstant(tz))
            }
        }
        return Triple(expense, income, periodData)
    }

    private fun getPeriodBounds(current: LocalDateTime, period: TimePeriod, salaryDay: Int? = null): Pair<Instant, Instant> {
        val tz = TimeZone.currentSystemDefault()
        return when (period) {
            TimePeriod.DAILY -> {
                val s = LocalDate(current.year, current.month, current.dayOfMonth)
                s.atTime(0, 0).toInstant(tz) to s.plus(1, DateTimeUnit.DAY).atTime(0, 0).toInstant(tz)
            }
            TimePeriod.WEEKLY -> {
                val s = current.date.minus(current.dayOfWeek.ordinal, DateTimeUnit.DAY)
                s.atTime(0, 0).toInstant(tz) to s.plus(7, DateTimeUnit.DAY).atTime(0, 0).toInstant(tz)
            }
            TimePeriod.MONTHLY -> {
                if (salaryDay != null) {
                    val s = salaryCycleStart(current.date, salaryDay)
                    val em = LocalDate(s.year, s.monthNumber, 1).plus(1, DateTimeUnit.MONTH)
                    val e = clampDay(em.year, em.monthNumber, salaryDay)
                    s.atTime(0, 0).toInstant(tz) to e.atTime(0, 0).toInstant(tz)
                } else {
                    val s = LocalDate(current.year, current.month, 1)
                    s.atTime(0, 0).toInstant(tz) to s.plus(1, DateTimeUnit.MONTH).atTime(0, 0).toInstant(tz)
                }
            }
            TimePeriod.QUARTERLY -> {
                val qm = when (current.month.number) { in 1..3 -> Month.JANUARY; in 4..6 -> Month.APRIL; in 7..9 -> Month.JULY; else -> Month.OCTOBER }
                val s = LocalDate(current.year, qm, 1)
                s.atTime(0, 0).toInstant(tz) to s.plus(3, DateTimeUnit.MONTH).atTime(0, 0).toInstant(tz)
            }
            TimePeriod.YEARLY -> {
                val s = LocalDate(current.year, Month.JANUARY, 1)
                s.atTime(0, 0).toInstant(tz) to LocalDate(current.year + 1, Month.JANUARY, 1).atTime(0, 0).toInstant(tz)
            }
        }
    }
}
