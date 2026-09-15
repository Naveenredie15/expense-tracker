package com.expensetracker.ui.screen.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.data.entity.Transaction
import com.expensetracker.data.entity.TransactionType
import com.expensetracker.data.entity.Category
import com.expensetracker.ui.component.TransactionDetailsDialog
import com.expensetracker.ui.component.CategorySelectionDialog
import com.expensetracker.ui.component.AddTransactionDialog
import com.expensetracker.ui.theme.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    var selectedTransactionForDetails by remember { mutableStateOf(null as Transaction?) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
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
            if (uiState.isSelectionMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.selectedTransactionIds.size} selected",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.toggleSelectAll() }
                            ) {
                                Text(if (uiState.isAllSelected) "Deselect All" else "Select All")
                            }
                            
                            IconButton(
                                onClick = { showDeleteConfirmation = true },
                                enabled = uiState.selectedTransactionIds.isNotEmpty()
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                            }
                            
                            IconButton(
                                onClick = { viewModel.exitSelectionMode() }
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Exit selection")
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Transactions",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        
                        if (uiState.transactions.isNotEmpty()) {
                            Text(
                                text = if (uiState.selectedAccountId == null)
                                    "${uiState.transactions.size} total transactions"
                                else
                                    "${uiState.transactions.size} transactions in this account",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
        
        // Filters — three swipeable lines (type · account · category), hidden in multi-select.
        if (!uiState.isSelectionMode) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Type
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(uiState.selectedType == null, { viewModel.setTypeFilter(null) }, label = { Text("All") })
                        }
                        item {
                            FilterChip(uiState.selectedType == TransactionType.CREDIT, { viewModel.setTypeFilter(TransactionType.CREDIT) }, label = { Text("Income") })
                        }
                        item {
                            FilterChip(uiState.selectedType == TransactionType.DEBIT, { viewModel.setTypeFilter(TransactionType.DEBIT) }, label = { Text("Expense") })
                        }
                    }
                    // Account
                    if (accounts.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(uiState.selectedAccountId == null, { viewModel.setAccountFilter(null) }, label = { Text("All accounts") })
                            }
                            items(accounts) { account ->
                                FilterChip(
                                    selected = uiState.selectedAccountId == account.id,
                                    onClick = { viewModel.setAccountFilter(account.id) },
                                    label = { Text("${account.label} ••${account.last4}") }
                                )
                            }
                        }
                    }
                    // Category
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(uiState.selectedCategory == null, { viewModel.setCategoryFilter(null) }, label = { Text("All categories") })
                        }
                        item {
                            FilterChip(
                                selected = uiState.selectedCategory == TransactionsViewModel.UNCATEGORIZED,
                                onClick = { viewModel.setCategoryFilter(TransactionsViewModel.UNCATEGORIZED) },
                                label = { Text("Uncategorized") }
                            )
                        }
                        items(categories) { category ->
                            FilterChip(
                                selected = uiState.selectedCategory == category.name,
                                onClick = { viewModel.setCategoryFilter(category.name) },
                                label = { Text(category.name) }
                            )
                        }
                    }
                }
            }
        }

        // Content
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
                                    text = "Loading transactions...",
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
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Error: ${uiState.errorMessage}",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            
            uiState.transactions.isEmpty() -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
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
                                text = if (uiState.selectedAccountId != null) "No transactions for this account"
                                       else "No transactions found",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (uiState.selectedAccountId != null)
                                    "Try selecting \"All\", or sync SMS to pull this account's transactions."
                                else
                                    "Your transactions will appear here once you sync SMS or add them manually",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            
            else -> {
                items(uiState.transactions) { transaction ->
                    CleanTransactionItem(
                        transaction = transaction,
                        isSelected = uiState.selectedTransactionIds.contains(transaction.id),
                        isSelectionMode = uiState.isSelectionMode,
                        categories = categories,
                        onClick = { 
                            if (uiState.isSelectionMode) {
                                viewModel.toggleTransactionSelection(transaction.id)
                            } else {
                                selectedTransactionForDetails = transaction
                            }
                        },
                        onLongClick = {
                            if (!uiState.isSelectionMode) {
                                viewModel.enterSelectionMode()
                                viewModel.toggleTransactionSelection(transaction.id)
                            }
                        },
                        onCategoryUpdate = { categoryName ->
                            viewModel.updateTransactionCategory(transaction, categoryName)
                        }
                    )
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(100.dp)) }
    }

        // Add-transaction FAB (hidden during multi-select)
        if (!uiState.isSelectionMode) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        }
    }

    if (showAddDialog) {
        AddTransactionDialog(
            accounts = accounts,
            onDismiss = { showAddDialog = false },
            onAdd = { amount, type, description, accountId, timestamp ->
                viewModel.addManualTransaction(amount, type, description, accountId, timestamp)
                showAddDialog = false
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Transactions") },
            text = { 
                Text("Are you sure you want to delete ${uiState.selectedTransactionIds.size} selected transaction(s)? This action cannot be undone.") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedTransactions()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Transaction Details Dialog
    selectedTransactionForDetails?.let { transaction ->
        TransactionDetailsDialog(
            transaction = transaction,
            categories = categories,
            onDismiss = { selectedTransactionForDetails = null },
            onCategoryUpdate = { newCategory ->
                viewModel.updateTransactionCategory(transaction, newCategory)
            },
            onDelete = {
                viewModel.deleteTransaction(transaction)
                selectedTransactionForDetails = null
            },
            account = accounts.firstOrNull { it.id == transaction.accountId },
            onBillingCycleShiftChange = { shift ->
                viewModel.setBillingCycleShift(transaction, shift)
            },
            onSplit = { parts ->
                viewModel.splitTransaction(transaction, parts)
                selectedTransactionForDetails = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CleanTransactionItem(
    transaction: Transaction,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    categories: List<Category>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCategoryUpdate: (String?) -> Unit
) {
    val isCredit = transaction.type == TransactionType.CREDIT
    val amountColor = if (isCredit) SuccessGreen else ErrorRed
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Selection checkbox (only show in selection mode)
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            }
            
            // Transaction type icon
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = amountColor.copy(alpha = 0.1f)
            ) {
                Icon(
                    imageVector = if (isCredit) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                    contentDescription = null,
                    tint = amountColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }
            
            // Transaction details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = transaction.payee ?: "Transaction",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transaction.category ?: "Uncategorized",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    val localDateTime = transaction.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
                    Text(
                        text = "${localDateTime.dayOfMonth}/${localDateTime.monthNumber}/${localDateTime.year}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                if (transaction.sender.isNotBlank()) {
                    Text(
                        text = "From: ${transaction.sender}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Amount
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${if (isCredit) "+" else "-"}₹${String.format("%,.0f", transaction.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = amountColor
                )
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = amountColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isCredit) "Income" else "Expense",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = amountColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
} 