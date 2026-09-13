package com.expensetracker.ui.screen.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expensetracker.sms.TemplateRegexBuilder
import com.expensetracker.sms.TemplateRegexBuilder.Field

data class Token(val text: String, val start: Int, val end: Int)

fun tokenize(text: String): List<Token> =
    Regex("""\S+""").findAll(text).map { Token(it.value, it.range.first, it.range.last + 1) }.toList()

/**
 * Reusable "tag the fields" widget. The user pastes one sample SMS, taps the
 * amount and merchant word(s), and this reports (sample, spans, isValid) upward.
 * Used once for the debit sample and once for the credit sample.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionTagger(
    initialSample: String = "",
    initialSpans: List<TemplateRegexBuilder.Span> = emptyList(),
    onChange: (sample: String, spans: List<TemplateRegexBuilder.Span>, valid: Boolean) -> Unit
) {
    var sample by remember { mutableStateOf(initialSample) }
    var activeField by remember { mutableStateOf(Field.AMOUNT) }
    var amountIdx by remember { mutableStateOf<Int?>(null) }
    val merchantIdxs = remember { mutableStateListOf<Int>() }
    val tokens = remember(sample) { tokenize(sample) }

    // Pre-fill the tags from a saved template (edit mode) exactly once. Maps each
    // saved span's char range back onto the whitespace tokens the tagger works in.
    LaunchedEffect(Unit) {
        if (initialSample.isNotEmpty() && initialSpans.isNotEmpty()) {
            val toks = tokenize(initialSample)
            initialSpans.firstOrNull { it.field == Field.AMOUNT }?.let { span ->
                toks.indexOfFirst { span.start >= it.start && span.start < it.end }
                    .takeIf { it >= 0 }
                    ?.let { amountIdx = it }
            }
            initialSpans.firstOrNull { it.field == Field.MERCHANT }?.let { span ->
                toks.forEachIndexed { i, t ->
                    if (t.start < span.end && t.end > span.start) merchantIdxs.add(i)
                }
            }
        }
    }

    // Clear tags only when the user actually changes the sample text (not on the
    // initial value, so pre-filled edit tags survive first composition).
    var prevSample by remember { mutableStateOf(initialSample) }
    LaunchedEffect(sample) {
        if (sample != prevSample) {
            amountIdx = null
            merchantIdxs.clear()
            prevSample = sample
        }
    }

    val spans = buildList {
        amountIdx?.let { add(TemplateRegexBuilder.Span(tokens[it].start, tokens[it].end, Field.AMOUNT)) }
        if (merchantIdxs.isNotEmpty()) {
            val s = merchantIdxs.minOf { tokens[it].start }
            val e = merchantIdxs.maxOf { tokens[it].end }
            add(TemplateRegexBuilder.Span(s, e, Field.MERCHANT))
        }
    }

    val regex = remember(spans, sample) {
        if (amountIdx != null && merchantIdxs.isNotEmpty())
            runCatching { TemplateRegexBuilder.build(sample, spans) }.getOrNull()
        else null
    }
    val testMatch = remember(regex) {
        regex?.let { runCatching { Regex(it, RegexOption.IGNORE_CASE).find(sample) }.getOrNull() }
    }
    val extractedAmount = testMatch?.groups?.get("amount")?.value
    val extractedMerchant = testMatch?.groups?.get("merchant")?.value
    val valid = extractedAmount != null && extractedMerchant != null

    // Report changes upward
    LaunchedEffect(sample, amountIdx, merchantIdxs.toList()) {
        onChange(sample, spans, valid)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = sample,
            onValueChange = { sample = it },
            label = { Text("Paste one sample SMS") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        if (tokens.isNotEmpty()) {
            Text("Tap the word(s) to tag", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeField == Field.AMOUNT,
                    onClick = { activeField = Field.AMOUNT },
                    label = { Text(if (amountIdx != null) "Amount ✓" else "Amount") }
                )
                FilterChip(
                    selected = activeField == Field.MERCHANT,
                    onClick = { activeField = Field.MERCHANT },
                    label = { Text(if (merchantIdxs.isNotEmpty()) "Merchant ✓" else "Merchant") }
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tokens.forEachIndexed { i, token ->
                    val isAmount = amountIdx == i
                    val isMerchant = merchantIdxs.contains(i)
                    val container = when {
                        isAmount -> MaterialTheme.colorScheme.primary
                        isMerchant -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val content = when {
                        isAmount -> MaterialTheme.colorScheme.onPrimary
                        isMerchant -> MaterialTheme.colorScheme.onTertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    AssistChip(
                        onClick = {
                            if (activeField == Field.AMOUNT) {
                                amountIdx = if (isAmount) null else i
                                merchantIdxs.remove(i)
                            } else {
                                if (isMerchant) merchantIdxs.remove(i) else merchantIdxs.add(i)
                                if (amountIdx == i) amountIdx = null
                            }
                        },
                        label = { Text(token.text) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = container,
                            labelColor = content
                        )
                    )
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Preview", fontWeight = FontWeight.Bold)
                    if (valid) {
                        Text("Amount: $extractedAmount")
                        Text("Merchant: $extractedMerchant")
                    } else {
                        Text(
                            "Tag both an amount and a merchant.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
