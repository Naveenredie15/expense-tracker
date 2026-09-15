package com.expensetracker.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.expensetracker.data.entity.Account
import com.expensetracker.data.entity.TransactionType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Minimal manual add-transaction form. Required: amount + type (defaults to Expense).
 * Optional: description (also drives auto-category), account, and date (defaults to now).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onAdd: (amount: Double, type: TransactionType, description: String, accountId: String?, timestamp: Instant) -> Unit
) {
    val tz = TimeZone.currentSystemDefault()
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.DEBIT) }
    var description by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf<String?>(null) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var pickedInstant by remember { mutableStateOf(Clock.System.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val amount = amountText.toDoubleOrNull()
    val canSave = amount != null && amount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { new -> amountText = new.filter { it.isDigit() || it == '.' } },
                    label = { Text("Amount *") },
                    leadingIcon = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == TransactionType.DEBIT,
                        onClick = { type = TransactionType.DEBIT },
                        label = { Text("Expense") }
                    )
                    FilterChip(
                        selected = type == TransactionType.CREDIT,
                        onClick = { type = TransactionType.CREDIT },
                        label = { Text("Income") }
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("e.g. Zepto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (accounts.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = accountMenuExpanded,
                        onExpandedChange = { accountMenuExpanded = it }
                    ) {
                        val selectedLabel = accounts.firstOrNull { it.id == accountId }
                            ?.let { "${it.label} ••${it.last4}" } ?: "None"
                        OutlinedTextField(
                            value = selectedLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Account (optional)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = { accountId = null; accountMenuExpanded = false }
                            )
                            accounts.forEach { acct ->
                                DropdownMenuItem(
                                    text = { Text("${acct.label} ••${acct.last4}") },
                                    onClick = { accountId = acct.id; accountMenuExpanded = false }
                                )
                            }
                        }
                    }
                }

                val dateLabel = pickedInstant.toLocalDateTime(tz).date.toString()
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Date: $dateLabel")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onAdd(amount!!, type, description.trim(), accountId, pickedInstant) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = pickedInstant.toEpochMilliseconds())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { pickedInstant = Instant.fromEpochMilliseconds(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }
}
