package com.expensetracker.ui.screen.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.data.entity.Account
import com.expensetracker.data.entity.AccountType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onAddAccount: () -> Unit,
    onEditAccount: (String) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenUnrecognized: () -> Unit,
    onOpenFormats: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val message by viewModel.message.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Profile") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Accounts first — the primary thing on this screen.
            item {
                SectionHeader(
                    title = "Accounts",
                    actionLabel = "Add",
                    onAction = onAddAccount
                )
            }
            if (accounts.isEmpty()) {
                item { EmptyHint("Add your bank accounts & cards. After adding, tag their transaction SMS so they parse and attribute correctly.") }
            }
            items(accounts, key = { it.id }) { account ->
                AccountRow(
                    account,
                    onEdit = { onEditAccount(account.id) },
                    onDelete = { viewModel.deleteAccount(account) }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }

            // Manage — secondary tools.
            item {
                Text(
                    "Manage",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                NavRow(
                    icon = Icons.Filled.Category,
                    title = "Categories",
                    subtitle = "Organize how transactions are grouped",
                    onClick = onOpenCategories
                )
            }
            item {
                NavRow(
                    icon = Icons.Filled.Autorenew,
                    title = "Recurring expenses",
                    subtitle = "Subscriptions, EMIs & rent for next-month planning",
                    onClick = onOpenRecurring
                )
            }
            item {
                NavRow(
                    icon = Icons.Filled.Inbox,
                    title = "Unrecognized SMS",
                    subtitle = "Teach the app new formats (salary, UPI received, etc.)",
                    onClick = onOpenUnrecognized
                )
            }
            item {
                NavRow(
                    icon = Icons.Filled.Rule,
                    title = "SMS formats",
                    subtitle = "View, edit or delete saved patterns",
                    onClick = onOpenFormats
                )
            }
            item {
                NavRow(
                    icon = Icons.Filled.DeleteSweep,
                    title = "Clear All Transactions",
                    subtitle = "Delete every stored transaction",
                    onClick = { showClearConfirm = true },
                    tint = MaterialTheme.colorScheme.error
                )
            }

            // Breathing room so the last card clears the bottom nav bar.
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all transactions?") },
            text = { Text("This permanently deletes every stored transaction. Your accounts and SMS formats are kept. You can re-sync from SMS afterwards.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllTransactions()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear All") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TextButton(onClick = onAction) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(actionLabel)
        }
    }
}

@Composable
private fun NavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color? = null
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint ?: LocalContentColor.current)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = tint ?: Color.Unspecified)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun AccountRow(account: Account, onEdit: () -> Unit, onDelete: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (account.type == AccountType.CARD) Icons.Filled.CreditCard else Icons.Filled.AccountBalance,
                contentDescription = null
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(account.label, fontWeight = FontWeight.SemiBold)
                Text(
                    "••${account.last4}  ·  ${account.type.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}
