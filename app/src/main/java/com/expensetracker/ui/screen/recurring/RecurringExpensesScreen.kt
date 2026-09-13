package com.expensetracker.ui.screen.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.data.entity.RecurringExpense
import com.expensetracker.data.entity.Transaction
import java.text.NumberFormat
import java.util.Locale

private fun money(v: Double): String =
    "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringExpensesScreen(
    onBack: () -> Unit,
    viewModel: RecurringExpensesViewModel = hiltViewModel()
) {
    val expenses by viewModel.expenses.collectAsState()
    val statuses by viewModel.statuses.collectAsState()
    var editing by remember { mutableStateOf<RecurringExpense?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val monthlyTotal = expenses.filter { it.enabled }.sumOf { it.amount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring expenses") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { editing = null; showDialog = true }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Subscriptions, EMIs, rent and other fixed monthly costs. Link each to its transaction so the app can tell if it's been paid this cycle.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            if (expenses.isNotEmpty()) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total per month", fontWeight = FontWeight.SemiBold)
                            Text(money(monthlyTotal), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            items(expenses, key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    status = statuses[expense.id],
                    onClick = { editing = expense; showDialog = true },
                    onToggle = { viewModel.setEnabled(expense, it) },
                    onDelete = { viewModel.delete(expense) }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showDialog) {
        RecurringExpenseDialog(
            existing = editing,
            viewModel = viewModel,
            onDismiss = { showDialog = false },
            onSave = { name, amount, day, category, linked ->
                viewModel.addOrUpdate(editing, name, amount, day, category, linked)
                showDialog = false
            }
        )
    }
}

@Composable
private fun ExpenseRow(
    expense: RecurringExpense,
    status: RecurringStatus?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(expense.name, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Paid / Due badge for the current cycle.
                    val paid = status?.paid == true
                    Text(
                        if (paid) "Paid ✓" else "Due",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (paid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                    val extra = buildString {
                        if (expense.matchRegex == null && expense.matchPayee == null) append(" · not linked")
                        expense.dayOfMonth?.let { append(" · day $it") }
                    }
                    if (extra.isNotEmpty()) {
                        Text(
                            extra,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(money(expense.amount), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Switch(checked = expense.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringExpenseDialog(
    existing: RecurringExpense?,
    viewModel: RecurringExpensesViewModel,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, dayOfMonth: Int?, category: String?, linked: Transaction?) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember {
        mutableStateOf(existing?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }
    var day by remember { mutableStateOf(existing?.dayOfMonth?.toString() ?: "") }
    var linked by remember { mutableStateOf<Transaction?>(null) }
    var candidates by remember { mutableStateOf<List<Transaction>>(emptyList()) }

    val amountValue = amount.toDoubleOrNull()
    val valid = name.isNotBlank() && amountValue != null && amountValue > 0

    // Load candidate transactions of the entered amount (only while none is linked).
    LaunchedEffect(amountValue, linked) {
        candidates = if (amountValue != null && amountValue > 0 && linked == null)
            viewModel.candidateTransactions(amountValue) else emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add recurring expense" else "Edit recurring expense") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name (e.g. Netflix, Home Loan)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) amount = it },
                    label = { Text("Monthly amount (₹)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) day = it },
                    label = { Text("Charged on day (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                Divider()
                Text("Link the matching transaction", fontWeight = FontWeight.SemiBold)
                Text(
                    "So the app knows when it's paid, and can match it next month.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (linked != null) {
                    val t = linked!!
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${t.payee ?: "Transaction"} · ${money(t.amount)}", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.message, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { linked = null }) { Text("Change") }
                        }
                    }
                } else {
                    if (existing?.matchPayee != null) {
                        Text("Currently linked: ${existing.matchPayee}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (amountValue == null) {
                        Text(
                            "Enter the amount above to see matching transactions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (candidates.isEmpty()) {
                        Text(
                            "No debit of ${money(amountValue)} in the last 40 days. Tip: if you paid a different amount, split that transaction into parts first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            candidates.forEach { t ->
                                ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { linked = t }) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text("${t.payee ?: "Transaction"} · ${money(t.amount)}", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            t.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(name, amountValue ?: 0.0, day.toIntOrNull()?.coerceIn(1, 31), null, linked) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
