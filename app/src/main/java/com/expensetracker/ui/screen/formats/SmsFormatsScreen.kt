package com.expensetracker.ui.screen.formats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.expensetracker.data.entity.SmsTemplate
import com.expensetracker.data.entity.TransactionType
import com.expensetracker.sms.TemplateRegexBuilder
import com.expensetracker.ui.screen.profile.TransactionTagger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsFormatsScreen(
    onBack: () -> Unit,
    viewModel: SmsFormatsViewModel = hiltViewModel()
) {
    val formats by viewModel.formats.collectAsState()
    var editing by remember { mutableStateOf<FormatRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing == null) "SMS formats" else "Edit format") },
                navigationIcon = {
                    IconButton(onClick = { if (editing == null) onBack() else editing = null }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val row = editing
            if (row != null) {
                EditPanel(
                    row = row,
                    initialSpans = viewModel.spansFor(row.template),
                    onDelete = { viewModel.deleteFormat(row.template); editing = null },
                    onSave = { sample, spans, type ->
                        viewModel.updateFormat(row.template, sample, spans, type)
                        editing = null
                    }
                )
            } else if (formats.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(48.dp))
                    Text("No formats yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Add an account, then tag its transaction SMS in \"Unrecognized SMS\". Saved formats show up here to edit or delete.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(formats.size) { i ->
                        val f = formats[i]
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth().clickable { editing = f }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${f.accountLabel} ••${f.accountLast4}",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        val isDebit = f.template.type == TransactionType.DEBIT
                                        AssistChip(
                                            onClick = { editing = f },
                                            label = { Text(if (isDebit) "Debit" else "Credit") }
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        f.template.sampleMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteFormat(f.template) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPanel(
    row: FormatRow,
    initialSpans: List<TemplateRegexBuilder.Span>,
    onDelete: () -> Unit,
    onSave: (sample: String, spans: List<TemplateRegexBuilder.Span>, type: TransactionType) -> Unit
) {
    var type by remember { mutableStateOf(row.template.type) }
    var sample by remember { mutableStateOf(row.template.sampleMessage) }
    var spans by remember { mutableStateOf(initialSpans) }
    var valid by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "${row.accountLabel} ••${row.accountLast4}",
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = type == TransactionType.DEBIT,
                onClick = { type = TransactionType.DEBIT },
                label = { Text("Debit (out)") }
            )
            FilterChip(
                selected = type == TransactionType.CREDIT,
                onClick = { type = TransactionType.CREDIT },
                label = { Text("Credit (in)") }
            )
        }
        TransactionTagger(
            initialSample = row.template.sampleMessage,
            initialSpans = initialSpans
        ) { s, sp, v -> sample = s; spans = sp; valid = v }

        Button(
            onClick = { onSave(sample, spans, type) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save changes") }

        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Delete this format")
        }
    }
}
