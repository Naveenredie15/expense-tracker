package com.expensetracker.ui.screen.unrecognized

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.data.entity.Account
import com.expensetracker.data.entity.AccountType
import com.expensetracker.data.entity.TransactionType
import com.expensetracker.sms.TemplateRegexBuilder
import com.expensetracker.ui.screen.profile.TransactionTagger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnrecognizedSmsScreen(
    onBack: () -> Unit,
    viewModel: UnrecognizedSmsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    var selected by remember { mutableStateOf<UnrecognizedGroup?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected == null) "Unrecognized SMS" else "Tag format") },
                navigationIcon = {
                    IconButton(onClick = { if (selected == null) onBack() else selected = null }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val group = selected
            when {
                group != null -> TagPanel(
                    group = group,
                    accounts = accounts,
                    onSave = { sample, spans, account, type ->
                        viewModel.createFormat(sample, spans, account, type)
                        selected = null
                    },
                    onIgnore = {
                        viewModel.ignorePattern(group)
                        selected = null
                    }
                )
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.groups.isEmpty() -> EmptyState(accounts.isEmpty())
                else -> GroupList(state.groups) { selected = it }
            }
        }
    }
}

@Composable
private fun GroupList(groups: List<UnrecognizedGroup>, onPick: (UnrecognizedGroup) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Bank SMS we couldn't read yet. Tap one to teach the app its format — every similar message (this month and future) will then be imported.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        items(groups.size) { i ->
            val g = groups[i]
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().clickable { onPick(g) }
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(g.representative.sender, fontWeight = FontWeight.SemiBold)
                        if (g.count > 1) {
                            Text(
                                "${g.count} similar",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        g.representative.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EmptyState(noAccounts: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            if (noAccounts) "Add an account first" else "Nothing to tag 🎉",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            if (noAccounts)
                "Once you add a bank/card account, unread transaction SMS will show up here to tag."
            else
                "Every transaction SMS in the last 90 days matches a saved format.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private enum class TagMode { DEBIT, CREDIT, PROMO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagPanel(
    group: UnrecognizedGroup,
    accounts: List<Account>,
    onSave: (sample: String, spans: List<TemplateRegexBuilder.Span>, account: Account, type: TransactionType) -> Unit,
    onIgnore: () -> Unit
) {
    val body = group.representative.body
    val guessedCredit = Regex("credited|received|deposit", RegexOption.IGNORE_CASE).containsMatchIn(body)
    var mode by remember { mutableStateOf(if (guessedCredit) TagMode.CREDIT else TagMode.DEBIT) }
    var account by remember {
        mutableStateOf(
            accounts.firstOrNull { it.last4 == group.representative.detectedLast4 } ?: accounts.firstOrNull()
        )
    }
    var sample by remember { mutableStateOf(body) }
    var spans by remember { mutableStateOf<List<TemplateRegexBuilder.Span>>(emptyList()) }
    var valid by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Show the SMS being tagged.
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text("What is this SMS?", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(mode == TagMode.DEBIT, { mode = TagMode.DEBIT }, label = { Text("Debit (out)") })
            FilterChip(mode == TagMode.CREDIT, { mode = TagMode.CREDIT }, label = { Text("Credit (in)") })
            FilterChip(mode == TagMode.PROMO, { mode = TagMode.PROMO }, label = { Text("Promotion") })
        }

        if (mode == TagMode.PROMO) {
            Text(
                "Marks this and every similar message as non-transaction — it won't be imported or shown here again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onIgnore, modifier = Modifier.fillMaxWidth()) {
                Text("Mark as promotion & hide")
            }
        } else {
            // Account picker
            ExposedDropdownMenuBox(expanded = accountMenu, onExpandedChange = { accountMenu = it }) {
                OutlinedTextField(
                    value = account?.let { "${it.label} ••${it.last4}" } ?: "Select account",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenu) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                    accounts.forEach { a ->
                        DropdownMenuItem(
                            text = { Text("${a.label} ••${a.last4}") },
                            onClick = { account = a; accountMenu = false }
                        )
                    }
                }
            }

            Text(
                "Tag the amount and the merchant/person in the SMS below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TransactionTagger(initialSample = body) { s, sp, v ->
                sample = s; spans = sp; valid = v
            }

            Button(
                onClick = {
                    account?.let {
                        onSave(sample, spans, it, if (mode == TagMode.CREDIT) TransactionType.CREDIT else TransactionType.DEBIT)
                    }
                },
                enabled = valid && account != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save format & import") }
        }
    }
}
