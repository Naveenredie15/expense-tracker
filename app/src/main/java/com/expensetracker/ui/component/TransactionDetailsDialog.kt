package com.expensetracker.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.expensetracker.data.entity.Account
import com.expensetracker.data.entity.AccountType
import com.expensetracker.data.entity.Category
import com.expensetracker.data.entity.Transaction
import com.expensetracker.data.entity.TransactionType
import com.expensetracker.planner.BillingPlanner
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailsDialog(
    transaction: Transaction,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onCategoryUpdate: (String?) -> Unit,
    onDelete: () -> Unit = {},
    /** The account this transaction belongs to, used to show/edit its billing cycle. */
    account: Account? = null,
    onBillingCycleShiftChange: (Int) -> Unit = {},
    /** Split this transaction into parts (amount, payee). */
    onSplit: (List<Pair<Double, String?>>) -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showSplitDialog by remember { mutableStateOf(false) }
    var currentTransaction by remember(transaction) { mutableStateOf(transaction) }

    // Update current transaction when category changes
    LaunchedEffect(transaction) {
        currentTransaction = transaction
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaction Details",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Amount with colored background
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentTransaction.type == TransactionType.CREDIT) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${if (currentTransaction.type == TransactionType.CREDIT) "+" else "-"}₹${NumberFormat.getNumberInstance(Locale("en", "IN")).format(currentTransaction.amount)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (currentTransaction.type == TransactionType.CREDIT)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = currentTransaction.type.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (currentTransaction.type == TransactionType.CREDIT) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else 
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Transaction details in scrollable column
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Payee
                    currentTransaction.payee?.let { payee ->
                        DetailRow(label = "Payee", value = payee)
                    }
                    
                    // Category with edit button
                    EditableDetailRow(
                        label = "Category",
                        value = currentTransaction.category ?: "Uncategorized",
                        onEditClick = { showCategoryDialog = true }
                    )
                    
                    // Date and Time
                    val localDateTime = currentTransaction.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
                    DetailRow(
                        label = "Date", 
                        value = "${localDateTime.dayOfMonth}/${localDateTime.monthNumber}/${localDateTime.year}"
                    )
                    DetailRow(
                        label = "Time",
                        value = String.format("%02d:%02d", localDateTime.hour, localDateTime.minute)
                    )

                    // Billing cycle (card debits only) — with a control to move the txn
                    // to an adjacent cycle when the bank posted it to the "wrong" one.
                    if (account?.type == AccountType.CARD &&
                        account.statementDay != null && account.dueDay != null &&
                        currentTransaction.type == TransactionType.DEBIT
                    ) {
                        BillingCycleSection(
                            transaction = currentTransaction,
                            statementDay = account.statementDay,
                            dueDay = account.dueDay,
                            onShiftChange = { newShift ->
                                currentTransaction = currentTransaction.copy(billingCycleShift = newShift)
                                onBillingCycleShiftChange(newShift)
                            }
                        )
                    }

                    // SMS Sender
                    DetailRow(label = "Bank/Sender", value = currentTransaction.sender)
                    
                    // Full SMS Message
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Original SMS Message",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = currentTransaction.message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showCategoryDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Category")
                    }
                    
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showSplitDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Split Transaction")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Transaction")
                }
            }
        }
    }

    if (showSplitDialog) {
        SplitTransactionDialog(
            total = currentTransaction.amount,
            defaultPayee = currentTransaction.payee,
            onDismiss = { showSplitDialog = false },
            onConfirm = { parts ->
                showSplitDialog = false
                onSplit(parts)
                onDismiss()
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this transaction?") },
            text = { Text("This removes it from your records. If the same SMS is re-synced it may reappear.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
    
    // Category Selection Dialog
    if (showCategoryDialog) {
        CategorySelectionDialog(
            categories = categories,
            currentCategory = currentTransaction.category,
            onCategorySelected = { newCategory ->
                currentTransaction = currentTransaction.copy(category = newCategory)
                onCategoryUpdate(newCategory)
                showCategoryDialog = false
            },
            onDismiss = { showCategoryDialog = false }
        )
    }
}

private val cycleDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM ''yy", Locale.ENGLISH)

@Composable
private fun BillingCycleSection(
    transaction: Transaction,
    statementDay: Int,
    dueDay: Int,
    onShiftChange: (Int) -> Unit
) {
    val txnDate = Instant.ofEpochSecond(transaction.timestamp.epochSeconds)
        .atZone(ZoneId.systemDefault()).toLocalDate()
    val shift = transaction.billingCycleShift
    val cycle = BillingPlanner.cycleFor(txnDate, statementDay, dueDay, shift)

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Billing cycle",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onShiftChange(shift - 1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous cycle")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${cycle.start.format(cycleDateFmt)} – ${cycle.end.format(cycleDateFmt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Due ${cycle.dueDate.format(cycleDateFmt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onShiftChange(shift + 1) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next cycle")
                }
            }
            if (shift != 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Moved ${if (shift > 0) "+$shift" else "$shift"} cycle" +
                            (if (kotlin.math.abs(shift) > 1) "s" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { onShiftChange(0) }) { Text("Reset") }
                }
            }
        }
    }
}

private class SplitPart(amount: String, payee: String) {
    var amount by mutableStateOf(amount)
    var payee by mutableStateOf(payee)
}

@Composable
private fun SplitTransactionDialog(
    total: Double,
    defaultPayee: String?,
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<Double, String?>>) -> Unit
) {
    val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
    val parts = remember {
        mutableStateListOf(
            SplitPart("", defaultPayee ?: ""),
            SplitPart("", defaultPayee ?: "")
        )
    }
    val amounts = parts.map { it.amount.toDoubleOrNull() }
    val sum = amounts.filterNotNull().sum()
    val allValid = amounts.all { it != null && it > 0 }
    val remaining = total - sum
    val valid = allValid && kotlin.math.abs(remaining) < 0.5 && parts.size >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split ₹${fmt.format(total)}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Split this transaction into parts (e.g. the recurring amount + the extra you'll get refunded). Parts must add up to the total.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                parts.forEachIndexed { i, part ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = part.amount,
                            onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) part.amount = it },
                            label = { Text("Amount") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = part.payee,
                            onValueChange = { part.payee = it },
                            label = { Text("Label / payee") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                        if (parts.size > 2) {
                            IconButton(onClick = { parts.removeAt(i) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove part")
                            }
                        }
                    }
                }
                TextButton(onClick = { parts.add(SplitPart("", defaultPayee ?: "")) }) {
                    Text("+ Add part")
                }
                Text(
                    if (kotlin.math.abs(remaining) < 0.5) "Adds up ✓"
                    else "Remaining: ₹${fmt.format(remaining)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (kotlin.math.abs(remaining) < 0.5) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(parts.map { (it.amount.toDoubleOrNull() ?: 0.0) to it.payee.trim().ifBlank { null } })
                }
            ) { Text("Split") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(2f)
        )
    }
}

@Composable
private fun EditableDetailRow(
    label: String,
    value: String,
    onEditClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        
        Row(
            modifier = Modifier.weight(2f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit $label",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
} 