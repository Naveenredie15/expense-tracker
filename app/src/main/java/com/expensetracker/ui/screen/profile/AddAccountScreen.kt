package com.expensetracker.ui.screen.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.data.entity.Account
import com.expensetracker.data.entity.AccountType

/**
 * Add- or edit-account form. Only account DETAILS are captured here — SMS formats are
 * NOT tagged during creation. After adding, the caller sends the user to the
 * "Unrecognized SMS" flow to tag whatever transactions actually arrive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onDone: () -> Unit,
    onAccountAdded: () -> Unit = onDone,
    accountId: String? = null,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    var existing by remember { mutableStateOf<Account?>(null) }
    var loaded by remember { mutableStateOf(accountId == null) }
    LaunchedEffect(accountId) {
        if (accountId != null) {
            existing = viewModel.getAccount(accountId)
            loaded = true
        }
    }

    if (!loaded) {
        Scaffold(topBar = { TopAppBar(title = { Text("Edit Account") }) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    AccountForm(existing, onDone, onAccountAdded, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountForm(
    account: Account?,
    onDone: () -> Unit,
    onAccountAdded: () -> Unit,
    viewModel: ProfileViewModel
) {
    val isEdit = account != null

    var last4 by remember { mutableStateOf(account?.last4 ?: "") }
    var label by remember { mutableStateOf(account?.label ?: "") }
    var type by remember { mutableStateOf(account?.type ?: AccountType.BANK) }

    var creditLimit by remember { mutableStateOf(account?.creditLimit?.let(::formatAmount) ?: "") }
    var statementDay by remember { mutableStateOf(account?.statementDay?.toString() ?: "") }
    var dueDay by remember { mutableStateOf(account?.dueDay?.toString() ?: "") }

    var isSalaryAccount by remember { mutableStateOf(account?.isSalaryAccount ?: false) }
    var monthlySalary by remember { mutableStateOf(account?.monthlySalary?.let(::formatAmount) ?: "") }
    var salaryDay by remember { mutableStateOf(account?.salaryDay?.toString() ?: "") }
    var balance by remember { mutableStateOf(account?.latestBalance?.let(::formatAmount) ?: "") }

    val detailsValid = last4.length >= 3 && label.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit Account" else "Add Account") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = last4,
                onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) last4 = it },
                label = { Text("Last 4 digits (as shown in SMS)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label (e.g. ICICI Salary)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = type == AccountType.BANK,
                    onClick = { type = AccountType.BANK },
                    label = { Text("Bank") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = type == AccountType.CARD,
                    onClick = { type = AccountType.CARD },
                    label = { Text("Card") }
                )
            }

            if (type == AccountType.CARD) {
                OutlinedTextField(
                    value = creditLimit,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) creditLimit = it },
                    label = { Text("Total credit limit (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = statementDay,
                        onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) statementDay = it },
                        label = { Text("Bill day") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dueDay,
                        onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) dueDay = it },
                        label = { Text("Due day") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "e.g. bill day 22, due day 8. Available-to-spend = limit − outstanding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSalaryAccount, onCheckedChange = { isSalaryAccount = it })
                    Text("Salary is credited to this account")
                }
                if (isSalaryAccount) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = monthlySalary,
                            onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) monthlySalary = it },
                            label = { Text("Monthly salary (₹)") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = salaryDay,
                            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) salaryDay = it },
                            label = { Text("Day") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "Salary day starts your income cycle (salary date → next salary date) and drives next-month planning.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = balance,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) balance = it },
                    label = { Text("Current balance (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Drives \"how much you can spend after card bills\". Auto-updates from SMS whenever a newer balance is seen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isEdit) {
                Text(
                    "After saving, we'll scan your recent SMS and show any we couldn't read so you can tag their format.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    val cl = if (type == AccountType.CARD) creditLimit.toDoubleOrNull() else null
                    val sd = if (type == AccountType.CARD) statementDay.toIntOrNull() else null
                    val dd = if (type == AccountType.CARD) dueDay.toIntOrNull() else null
                    val bankSalary = type == AccountType.BANK && isSalaryAccount
                    if (isEdit && account != null) {
                        viewModel.updateAccount(
                            accountId = account.id,
                            last4 = last4, label = label, type = type,
                            creditLimit = cl, statementDay = sd, dueDay = dd,
                            isSalaryAccount = bankSalary,
                            monthlySalary = if (bankSalary) monthlySalary.toDoubleOrNull() else null,
                            salaryDay = if (bankSalary) salaryDay.toIntOrNull()?.coerceIn(1, 31) else null,
                            latestBalance = if (type == AccountType.BANK) balance.toDoubleOrNull() else null
                        )
                        onDone()
                    } else {
                        viewModel.addAccount(
                            last4 = last4, label = label, type = type,
                            creditLimit = cl, statementDay = sd, dueDay = dd,
                            isSalaryAccount = bankSalary,
                            monthlySalary = if (bankSalary) monthlySalary.toDoubleOrNull() else null,
                            salaryDay = if (bankSalary) salaryDay.toIntOrNull()?.coerceIn(1, 31) else null,
                            latestBalance = if (type == AccountType.BANK) balance.toDoubleOrNull() else null
                        )
                        onAccountAdded()
                    }
                },
                enabled = detailsValid,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isEdit) "Save Changes" else "Save & tag SMS") }
        }
    }
}

/** Show a stored amount without a trailing ".0" for whole rupees. */
private fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
