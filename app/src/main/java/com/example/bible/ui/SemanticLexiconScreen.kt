package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.LexiconMediaRefs
import com.example.bible.data.LexiconTone
import com.example.bible.data.SemanticDisplayStyle
import com.example.bible.data.SemanticLexiconRule
import com.example.bible.data.copyWithNewId
import java.util.UUID

private val LexiconPalette = listOf(
    0xFF0D47A1L,
    0xFF1565C0L,
    0xFF00838FL,
    0xFF2E7D32L,
    0xFF43A047L,
    0xFF6D4C41L,
    0xFFB71C1CL,
    0xFFC62828L,
    0xFFE53935L,
    0xFF5D4037L,
    0xFFAD1457L,
    0xFFEF6C00L,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SemanticLexiconScreen(
    userRules: List<SemanticLexiconRule>,
    presetRules: List<SemanticLexiconRule>,
    lexiconPresetEnabled: Boolean,
    lexiconPresetToneIds: Set<String>,
    lexiconUserEnabled: Boolean,
    lexiconUserToneIds: Set<String>,
    onSetLexiconUserEnabled: (Boolean) -> Unit,
    onSetLexiconUserToneIds: (Set<String>) -> Unit,
    onSaveUserRule: (SemanticLexiconRule) -> Unit,
    onDeleteUserRule: (String) -> Unit,
    onSetPresetEnabled: (Boolean) -> Unit,
    onSetPresetToneIds: (Set<String>) -> Unit,
    onBack: () -> Unit,
) {
    var showEditor by remember { mutableStateOf<SemanticLexiconRule?>(null) }
    var showPresetPicker by remember { mutableStateOf(false) }

    val allToneIds = remember { LexiconTone.entries.map { it.id }.toSet() }
    val effectivePresetTones = if (lexiconPresetToneIds.isEmpty()) allToneIds else lexiconPresetToneIds
    val effectiveUserTones = if (lexiconUserToneIds.isEmpty()) allToneIds else lexiconUserToneIds

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.semantic_lexicon_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    showEditor = SemanticLexiconRule(
                        id = "user_" + UUID.randomUUID().toString().replace("-", ""),
                        wordsRu = emptySet(),
                        wordsEn = emptySet(),
                        senseLabel = "",
                        colorArgb = LexiconPalette.first(),
                        displayStyle = SemanticDisplayStyle.BACKGROUND,
                        tone = LexiconTone.GOOD,
                        enabled = true,
                        isPreset = false,
                    )
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.semantic_lexicon_add))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.semantic_lexicon_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.semantic_lexicon_preset_switch), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = lexiconPresetEnabled, onCheckedChange = onSetPresetEnabled)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.semantic_lexicon_preset_tones), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    LexiconTone.entries.forEach { tone ->
                        val selected = tone.id in effectivePresetTones
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val newSet = if (lexiconPresetToneIds.isEmpty()) {
                                    allToneIds - tone.id
                                } else {
                                    if (selected) lexiconPresetToneIds - tone.id else lexiconPresetToneIds + tone.id
                                }
                                when {
                                    newSet.isEmpty() -> onSetPresetEnabled(false)
                                    newSet == allToneIds -> onSetPresetToneIds(emptySet())
                                    else -> onSetPresetToneIds(newSet)
                                }
                            },
                            label = { Text(tone.labelRu, style = MaterialTheme.typography.labelSmall) },
                            enabled = lexiconPresetEnabled,
                        )
                    }
                }
                TextButton(onClick = { showPresetPicker = true }) {
                    Text(stringResource(R.string.semantic_lexicon_import_preset))
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.semantic_lexicon_user_switch), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = lexiconUserEnabled, onCheckedChange = onSetLexiconUserEnabled)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.semantic_lexicon_user_tones), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    LexiconTone.entries.forEach { tone ->
                        val selected = tone.id in effectiveUserTones
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val newSet = if (lexiconUserToneIds.isEmpty()) {
                                    allToneIds - tone.id
                                } else {
                                    if (selected) lexiconUserToneIds - tone.id else lexiconUserToneIds + tone.id
                                }
                                when {
                                    newSet.isEmpty() -> onSetLexiconUserEnabled(false)
                                    newSet == allToneIds -> onSetLexiconUserToneIds(emptySet())
                                    else -> onSetLexiconUserToneIds(newSet)
                                }
                            },
                            label = { Text(tone.labelRu, style = MaterialTheme.typography.labelSmall) },
                            enabled = lexiconUserEnabled,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.semantic_lexicon_my_rules), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }
            items(userRules, key = { it.id }) { rule ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { onSaveUserRule(rule.copy(enabled = it)) },
                            enabled = lexiconUserEnabled,
                        )
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(rule.colorArgb)),
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(rule.senseLabel, style = MaterialTheme.typography.titleSmall)
                            Text(
                                (rule.wordsRu + rule.wordsEn).take(12).joinToString(", ") +
                                    if (rule.wordsRu.size + rule.wordsEn.size > 12) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${rule.tone.labelRu} · ${rule.displayStyle.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { showEditor = rule }) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                        IconButton(onClick = { onDeleteUserRule(rule.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showEditor != null) {
        LexiconRuleEditorDialog(
            initial = showEditor!!,
            onDismiss = { showEditor = null },
            onSave = { r ->
                onSaveUserRule(r)
                showEditor = null
            },
        )
    }

    if (showPresetPicker) {
        AlertDialog(
            onDismissRequest = { showPresetPicker = false },
            title = { Text(stringResource(R.string.semantic_lexicon_pick_preset)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(presetRules, key = { it.id }) { pr ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onSaveUserRule(pr.copyWithNewId())
                                    showPresetPicker = false
                                },
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(pr.senseLabel, style = MaterialTheme.typography.bodyMedium)
                                Text(pr.tone.labelRu, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPresetPicker = false }) {
                    Text(stringResource(R.string.back))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LexiconRuleEditorDialog(
    initial: SemanticLexiconRule,
    onDismiss: () -> Unit,
    onSave: (SemanticLexiconRule) -> Unit,
) {
    val scroll = rememberScrollState()
    var wordsRuText by remember(initial.id) {
        mutableStateOf(initial.wordsRu.joinToString(", "))
    }
    var wordsEnText by remember(initial.id) {
        mutableStateOf(initial.wordsEn.joinToString(", "))
    }
    var sense by remember(initial.id) { mutableStateOf(initial.senseLabel) }
    var colorArgb by remember(initial.id) { mutableStateOf(initial.colorArgb) }
    var style by remember(initial.id) { mutableStateOf(initial.displayStyle) }
    var tone by remember(initial.id) { mutableStateOf(initial.tone) }
    var ruleEnabled by remember(initial.id) { mutableStateOf(initial.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.semantic_lexicon_edit_rule)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = wordsRuText,
                    onValueChange = { wordsRuText = it },
                    label = { Text(stringResource(R.string.semantic_lexicon_words_ru)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = wordsEnText,
                    onValueChange = { wordsEnText = it },
                    label = { Text(stringResource(R.string.semantic_lexicon_words_en)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = sense,
                    onValueChange = { sense = it },
                    label = { Text(stringResource(R.string.semantic_lexicon_sense_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.semantic_lexicon_rule_enabled), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = ruleEnabled, onCheckedChange = { ruleEnabled = it })
                }
                Text(stringResource(R.string.semantic_lexicon_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LexiconPalette.forEach { c ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable { colorArgb = c }
                                .then(
                                    if (colorArgb == c) Modifier else Modifier,
                                ),
                        )
                    }
                }
                Text(stringResource(R.string.semantic_display_style), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SemanticDisplayStyle.entries.forEach { s ->
                        FilterChip(
                            selected = style == s,
                            onClick = { style = s },
                            label = { Text(s.name) },
                        )
                    }
                }
                Text(stringResource(R.string.semantic_lexicon_tone_axis), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LexiconTone.entries.forEach { t ->
                        FilterChip(
                            selected = tone == t,
                            onClick = { tone = t },
                            label = { Text(t.labelRu, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ru = wordsRuText.split(',').map { it.trim().lowercase() }.filter { it.length >= 2 }.toSet()
                    val en = wordsEnText.split(',').map { it.trim().lowercase() }.filter { it.length >= 2 }.toSet()
                    if (ru.isEmpty() && en.isEmpty()) return@TextButton
                    onSave(
                        initial.copy(
                            wordsRu = ru,
                            wordsEn = en,
                            senseLabel = sense.ifBlank { "Без подписи" },
                            colorArgb = colorArgb,
                            displayStyle = style,
                            tone = tone,
                            enabled = ruleEnabled,
                            isPreset = false,
                            media = LexiconMediaRefs(),
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.semantic_lexicon_save_rule))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.back))
            }
        },
    )
}
