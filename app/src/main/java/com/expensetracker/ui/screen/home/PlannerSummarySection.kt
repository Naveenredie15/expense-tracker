package com.expensetracker.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("d MMM")

private fun money(v: Double?): String =
    if (v == null) "—" else "₹" + String.format("%,.0f", v)

/**
 * Small swipeable row of recurring bills not yet paid this cycle. Sits above the
 * Planner, below the Current Balance card. Hidden when everything's paid.
 */
@Composable
fun UnpaidRecurringSection(
    currentBalance: Double? = null,
    viewModel: PlannerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val unpaid = state.unpaidRecurring
    if (unpaid.isEmpty()) return

    // Running balance after paying each bill in turn (can go negative). Precomputed so
    // it doesn't depend on LazyRow composition order.
    val afters: List<Double?> = remember(unpaid, currentBalance) {
        var r = currentBalance
        unpaid.map { r = r?.minus(it.amount); r }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Not paid this month",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(unpaid.size) { i ->
                val bill = unpaid[i]
                val after = afters.getOrNull(i)
                ElevatedCard(
                    modifier = Modifier.widthIn(min = 140.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            bill.name,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            money(bill.amount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            bill.dayOfMonth?.let { "Due day $it" } ?: "Due this month",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                        )
                        if (after != null) {
                            // Balance left after paying this (smaller); red when negative.
                            Text(
                                "Left: ${money(after)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (after < 0) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlannerSummarySection(viewModel: PlannerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    if (!state.hasData) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Planner", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        state.cards.forEach { card ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(card.label, fontWeight = FontWeight.Bold)
                    StatLine(
                        "Due ${card.nextDueDate.format(dateFmt)} (in ${card.daysToDue}d)",
                        money(card.cycleSpend),
                        emphasize = true
                    )
                    if (card.creditLimit != null) {
                        StatLine("Credit left", money(card.available), emphasize = true)
                        val used = if (card.creditLimit > 0)
                            (card.outstanding / card.creditLimit).coerceIn(0.0, 1.0) else 0.0
                        LinearProgressIndicator(
                            progress = used.toFloat(),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                        StatLine(
                            "Limit ${money(card.creditLimit)}",
                            "used ${money(card.outstanding)}"
                        )
                    }
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Cash flow", fontWeight = FontWeight.Bold)
                StatLine("Bank balance", money(state.bankBalance))
                state.salaryDate?.let {
                    StatLine("Next salary ${it.format(dateFmt)}", "in ${state.daysToSalary}d")
                }
                if (state.monthlySalary != null) {
                    StatLine("Salary", money(state.monthlySalary))
                    StatLine("− Card spend", money(state.cardSpend))
                    if (state.recurringTotal > 0) {
                        StatLine("− Regular bills", money(state.recurringTotal))
                    }
                    StatLine("Next month left", money(state.nextMonthAfterCards), emphasize = true)
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
