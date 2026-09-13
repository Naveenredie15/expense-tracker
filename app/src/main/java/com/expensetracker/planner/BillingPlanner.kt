package com.expensetracker.planner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Pure-Kotlin credit-card billing-cycle + salary math. No Android deps.
 *
 * Billing cycle: a statement is generated on [statementDay] each month, covering
 * spends since the previous statement (day after last statement -> this statement).
 * Payment is due on [dueDay] of the following month.
 */
object BillingPlanner {

    data class CardStatus(
        val cycleStart: LocalDate,
        val cycleEnd: LocalDate,          // == next statement date
        val nextStatementDate: LocalDate,
        val nextDueDate: LocalDate,
        val cycleSpend: Double,           // net spends in the current open cycle (debits - refunds)
        val cycleRefunds: Double,         // refunds/reversals in the current open cycle
        val outstanding: Double,          // owed for the current cycle (== cycleSpend; prior bills assumed paid)
        val creditLimit: Double?,
        val available: Double?,           // creditLimit - outstanding
        val daysToStatement: Long,
        val daysToDue: Long
    )

    private fun clampDay(year: Int, month: Int, day: Int): LocalDate {
        val len = YearMonth.of(year, month).lengthOfMonth()
        return LocalDate.of(year, month, minOf(day, len))
    }

    /** The billing cycle a transaction belongs to. [end] is the statement date. */
    data class Cycle(
        val start: LocalDate,
        val end: LocalDate,        // statement date (last day of the cycle)
        val dueDate: LocalDate
    )

    /**
     * The billing cycle that [date] falls into, optionally [shift]ed by N whole
     * cycles (-1 = previous statement, +1 = next). Shared by the planner and the
     * transaction dialog so both agree on which cycle a spend counts toward.
     */
    fun cycleFor(date: LocalDate, statementDay: Int, dueDay: Int, shift: Int = 0): Cycle {
        // First statement on/after this date's position in its month.
        val baseStatement =
            if (date.dayOfMonth <= statementDay) clampDay(date.year, date.monthValue, statementDay)
            else clampDay(date.plusMonths(1).year, date.plusMonths(1).monthValue, statementDay)

        val stmtMonth = baseStatement.plusMonths(shift.toLong())
        val statement = clampDay(stmtMonth.year, stmtMonth.monthValue, statementDay)

        val prevMonth = statement.minusMonths(1)
        val start = clampDay(prevMonth.year, prevMonth.monthValue, statementDay).plusDays(1)

        val dueMonth = statement.plusMonths(1)
        val due = clampDay(dueMonth.year, dueMonth.monthValue, dueDay)

        return Cycle(start = start, end = statement, dueDate = due)
    }

    /**
     * @param cardDebits    list of (date, amount) for card spends
     * @param cardPayments  list of (date, amount) for card bill payments / refunds
     */
    fun cardStatus(
        today: LocalDate,
        statementDay: Int,
        dueDay: Int,
        creditLimit: Double?,
        cardDebits: List<Pair<LocalDate, Double>>,
        cardPayments: List<Pair<LocalDate, Double>>,
        cardRefunds: List<Pair<LocalDate, Double>> = emptyList()
    ): CardStatus {
        val nextStatement =
            if (today.dayOfMonth <= statementDay) clampDay(today.year, today.monthValue, statementDay)
            else clampDay(today.plusMonths(1).year, today.plusMonths(1).monthValue, statementDay)

        val prevStatementMonth = nextStatement.minusMonths(1)
        val prevStatement = clampDay(prevStatementMonth.year, prevStatementMonth.monthValue, statementDay)
        val cycleStart = prevStatement.plusDays(1)
        val cycleEnd = nextStatement

        val dueMonth = nextStatement.plusMonths(1)
        val nextDue = clampDay(dueMonth.year, dueMonth.monthValue, dueDay)

        fun inCycle(p: Pair<LocalDate, Double>) =
            !p.first.isBefore(cycleStart) && !p.first.isAfter(cycleEnd)

        val cycleDebits = cardDebits.filter(::inCycle).sumOf { it.second }
        val cycleRefunds = cardRefunds.filter(::inCycle).sumOf { it.second }
        // Net: a spend refunded in the same cycle cancels out.
        val cycleSpend = (cycleDebits - cycleRefunds).coerceAtLeast(0.0)

        // Prior statements are assumed paid, so "owed" = only the current cycle's net spend.
        val outstanding = cycleSpend
        val available = creditLimit?.let { it - outstanding }

        return CardStatus(
            cycleStart = cycleStart,
            cycleEnd = cycleEnd,
            nextStatementDate = nextStatement,
            nextDueDate = nextDue,
            cycleSpend = cycleSpend,
            cycleRefunds = cycleRefunds,
            outstanding = outstanding,
            creditLimit = creditLimit,
            available = available,
            daysToStatement = ChronoUnit.DAYS.between(today, nextStatement),
            daysToDue = ChronoUnit.DAYS.between(today, nextDue)
        )
    }

    /** Last Monday–Friday of the given month (skips Sat/Sun; ignores holidays). */
    fun lastWorkingDay(ym: YearMonth): LocalDate {
        var d = ym.atEndOfMonth()
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) {
            d = d.minusDays(1)
        }
        return d
    }

    /** Next salary date: this month's last working day if not yet passed, else next month's. */
    fun nextSalaryDate(today: LocalDate): LocalDate {
        val thisMonth = lastWorkingDay(YearMonth.from(today))
        return if (!today.isAfter(thisMonth)) thisMonth
        else lastWorkingDay(YearMonth.from(today).plusMonths(1))
    }

    /** Next salary date from an explicit salary [day] of month (clamped to short months). */
    fun nextSalaryDate(today: LocalDate, day: Int): LocalDate {
        val thisMonth = clampDay(today.year, today.monthValue, day)
        return if (!today.isAfter(thisMonth)) thisMonth
        else {
            val next = today.plusMonths(1)
            clampDay(next.year, next.monthValue, day)
        }
    }
}
