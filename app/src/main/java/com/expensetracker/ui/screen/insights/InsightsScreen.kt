package com.expensetracker.ui.screen.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.data.dao.CategoryAmount
import com.expensetracker.data.dao.PayeeAmount
import com.expensetracker.data.entity.Account
import com.expensetracker.ui.theme.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.text.NumberFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        
        // Clean Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Insights",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                
                FilledTonalButton(
                    onClick = { viewModel.refreshInsights() }
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh")
                }
            }
        }
        
        // Simple Time Period Selector
        item {
            CleanTimePeriodSelector(
                selectedPeriod = uiState.selectedPeriod,
                onPeriodSelected = viewModel::selectTimePeriod
            )
        }

        // Exact date range for the selected period (e.g. salary cycle for Monthly)
        if (uiState.periodRangeLabel.isNotBlank()) {
            item {
                Text(
                    text = uiState.periodRangeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Account filter (All + each account/card)
        if (uiState.accounts.isNotEmpty()) {
            item {
                AccountFilterChips(
                    accounts = uiState.accounts,
                    selectedAccountId = uiState.selectedAccountId,
                    onSelect = viewModel::setAccountFilter
                )
            }
        }

        when {
            uiState.isLoading -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Analyzing your finances...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
            
            uiState.errorMessage != null -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Error: ${uiState.errorMessage}",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            else -> {
                // Simple Chart
                if (uiState.expenseChartData.isNotEmpty()) {
                    item {
                        CleanExpenseChart(
                            expenseData = uiState.expenseChartData,
                            incomeData = uiState.incomeChartData
                        )
                    }
                }
                
                // Spending by category (donut + legend, incl. Uncategorized)
                if (uiState.categoryBreakdown.isNotEmpty()) {
                    item {
                        Text(
                            text = "Spending by category",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    item {
                        CategoryDonutCard(slices = uiState.categoryBreakdown)
                    }
                }
                
            }
        }
        
        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountFilterChips(
    accounts: List<Account>,
    selectedAccountId: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selectedAccountId == null,
                onClick = { onSelect(null) },
                label = { Text("All") }
            )
        }
        items(accounts) { a ->
            FilterChip(
                selected = selectedAccountId == a.id,
                onClick = { onSelect(a.id) },
                label = { Text("${a.label} ••${a.last4}") }
            )
        }
    }
}

private fun rupees(v: Double): String =
    "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(v.toLong())

@Composable
private fun CategoryDonutCard(slices: List<CategorySlice>) {
    val total = slices.sumOf { it.amount }.coerceAtLeast(0.0001)
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(180.dp)) {
                    val strokeW = 38.dp.toPx()
                    val diameter = size.minDimension - strokeW
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val arcSize = Size(diameter, diameter)
                    var startAngle = -90f
                    slices.forEach { s ->
                        val sweep = (s.amount / total * 360.0).toFloat()
                        drawArc(
                            color = s.color,
                            startAngle = startAngle,
                            sweepAngle = sweep - 1.5f, // tiny gap between slices
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW)
                        )
                        startAngle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Spent",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        rupees(slices.sumOf { it.amount }),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                slices.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(12.dp).clip(CircleShape).background(s.color)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            s.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (s.label == "Uncategorized") FontWeight.Normal else FontWeight.Medium,
                            color = if (s.label == "Uncategorized")
                                MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            rupees(s.amount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${s.percent.toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(min = 34.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanTimePeriodSelector(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit
) {
    val periods = listOf(
        TimePeriod.MONTHLY to "This Month",
        TimePeriod.QUARTERLY to "3 Months", 
        TimePeriod.YEARLY to "This Year"
    )
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Time Period",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(periods) { (period, label) ->
                    FilterChip(
                        onClick = { onPeriodSelected(period) },
                        label = { Text(label) },
                        selected = selectedPeriod == period,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanFinancialSummary(
    income: Double,
    expenses: Double,
    period: TimePeriod
) {
    val balance = income - expenses
    val isPositive = balance >= 0
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Financial Summary",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FinancialMetricCard(
                    title = "Income",
                    amount = income,
                    icon = Icons.Filled.TrendingUp,
                    color = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
                
                FinancialMetricCard(
                    title = "Expenses",
                    amount = expenses,
                    icon = Icons.Filled.TrendingDown,
                    color = ErrorRed,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Net Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Net Balance",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "${if (isPositive) "+" else "-"}₹${String.format("%,.0f", kotlin.math.abs(balance))}",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isPositive) SuccessGreen else ErrorRed
                        )
                    )
                }
                
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = (if (isPositive) SuccessGreen else ErrorRed).copy(alpha = 0.15f)
                ) {
                    Icon(
                        if (isPositive) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        tint = if (isPositive) SuccessGreen else ErrorRed
                    )
                }
            }
        }
    }
}

@Composable
private fun FinancialMetricCard(
    title: String,
    amount: Double,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Text(
                text = "₹${String.format("%,.0f", amount)}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
        }
    }
}

@Composable
private fun CleanExpenseChart(
    expenseData: List<ChartData>,
    incomeData: List<ChartData>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Expense Trend",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
            
            // Simple bar chart representation
            if (expenseData.isNotEmpty()) {
                val maxAmount = (expenseData + incomeData).maxOfOrNull { it.value.toDouble() } ?: 1.0
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    expenseData.take(7).forEach { data ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = data.label,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(60.dp)
                            )
                            
                            LinearProgressIndicator(
                                progress = (data.value.toDouble() / maxAmount).toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                color = ErrorRed,
                                trackColor = ErrorRed.copy(alpha = 0.2f)
                            )
                            
                            Text(
                                text = "₹${String.format("%.0f", data.value)}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier.width(80.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanCategoryBreakdown(
    categories: List<CategoryAmount>,
    totalExpenses: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            categories.forEach { category ->
                val percentage = if (totalExpenses > 0) (category.total / totalExpenses * 100) else 0.0
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = category.category,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        LinearProgressIndicator(
                            progress = (percentage / 100).toFloat(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "₹${String.format("%,.0f", category.total)}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "${String.format("%.1f", percentage)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanPayeeBreakdown(
    payees: List<PayeeAmount>,
    totalExpenses: Double
) {
    var selectedPayee by remember { mutableStateOf(null as String?) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            payees.forEach { payee ->
                val percentage = if (totalExpenses > 0) (payee.total / totalExpenses * 100) else 0.0
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPayee = payee.payee },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = payee.payee,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = (percentage / 100).toFloat(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "₹${String.format("%,.0f", payee.total)}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "${String.format("%.1f", percentage)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = "View transactions",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            
            if (payees.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 Tip: Tap on a payee to see all transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    
    // Show detailed payee dialog
    selectedPayee?.let { payeeName ->
        PayeeDetailsDialog(
            payeeName = payeeName,
            onDismiss = { selectedPayee = null }
        )
    }
}

@Composable
private fun CleanQuickStats(
    totalTransactions: Int,
    avgTransactionAmount: Double,
    largestExpense: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Quick Stats",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Transactions",
                    value = totalTransactions.toString(),
                    icon = Icons.Filled.Receipt
                )
                
                Divider(
                    modifier = Modifier
                        .height(40.dp)
                        .width(1.dp)
                )
                
                StatItem(
                    label = "Avg Amount",
                    value = "₹${String.format("%.0f", avgTransactionAmount)}",
                    icon = Icons.Filled.TrendingFlat
                )
                
                Divider(
                    modifier = Modifier
                        .height(40.dp)
                        .width(1.dp)
                )
                
                StatItem(
                    label = "Largest",
                    value = "₹${String.format("%.0f", largestExpense)}",
                    icon = Icons.Filled.KeyboardArrowUp
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

// Helper functions
private fun calculateFinancialHealth(income: Double, expenses: Double): Float {
    if (income <= 0) return 0f
    val savingsRate = ((income - expenses) / income) * 100
    return when {
        savingsRate >= 20 -> 90f
        savingsRate >= 10 -> 75f
        savingsRate >= 5 -> 60f
        savingsRate >= 0 -> 40f
        else -> 20f
    }
}

private fun getHealthDescription(score: Float): String {
    return when {
        score >= 80 -> "Excellent financial health!"
        score >= 60 -> "Good financial management"
        score >= 40 -> "Room for improvement"
        else -> "Needs attention"
    }
}

private fun getRandomCategoryColor(category: String): Color {
    val colors = listOf(
        Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800),
        Color(0xFF9C27B0), Color(0xFFE91E63), Color(0xFF00BCD4),
        Color(0xFF795548), Color(0xFF607D8B), Color(0xFFFF5722)
    )
    return colors[category.hashCode().mod(colors.size)]
}

private fun formatAmount(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))
    formatter.minimumFractionDigits = 0
    formatter.maximumFractionDigits = 0
    return formatter.format(amount)
}

// Data classes for enhanced insights
data class PreviousPeriodComparison(
    val incomeChange: Double,
    val expenseChange: Double,
    val balanceChange: Double
)

data class FinancialInsight(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

data class BudgetStatus(
    val category: String,
    val budget: Double,
    val spent: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayeeDetailsDialog(
    payeeName: String,
    onDismiss: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel()
) {
    var transactions by remember { mutableStateOf(emptyList<com.expensetracker.data.entity.Transaction>()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(payeeName) {
        try {
            // Get transactions for this payee
            viewModel.getTransactionsForPayee(payeeName).collect { transactionList ->
                transactions = transactionList
                isLoading = false
            }
        } catch (e: Exception) {
            isLoading = false
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = payeeName,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Transaction History",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                
                Divider()
                
                // Content
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (transactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Filled.Receipt,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "No transactions found",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                } else {
                    // Transaction list
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Summary
                        item {
                            val totalAmount = transactions.sumOf { it.amount }
                            val transactionCount = transactions.size
                            
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Total Spent",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "₹${String.format("%,.0f", totalAmount)}",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                    
                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = "Transactions",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = transactionCount.toString(),
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Transaction items
                        items(transactions) { transaction ->
                            PayeeTransactionItem(transaction = transaction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PayeeTransactionItem(
    transaction: com.expensetracker.data.entity.Transaction
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = transaction.category ?: "Uncategorized",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                
                val localDateTime = transaction.timestamp.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                Text(
                    text = "${localDateTime.dayOfMonth}/${localDateTime.monthNumber}/${localDateTime.year}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            Text(
                text = "₹${String.format("%,.0f", transaction.amount)}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = ErrorRed
            )
        }
    }
} 