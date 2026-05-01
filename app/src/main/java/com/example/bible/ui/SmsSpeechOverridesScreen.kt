package com.example.bible.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.SmsSpeechOverrideEntry
import com.example.bible.data.SmsSpeechOverrideRepository
import com.example.bible.data.normalizeSmsDigits
import java.util.UUID

private data class SpeechOverrideEditorState(
    val id: String,
    val title: String,
    val senderFieldText: String,
    val utteranceFieldText: String,
) {
    constructor(e: SmsSpeechOverrideEntry) : this(
        id = e.id,
        title = e.title,
        senderFieldText = e.senderDigitPatterns.joinToString("\n"),
        utteranceFieldText = e.utterance,
    )

    fun toEntry(): SmsSpeechOverrideEntry {
        val patterns = senderFieldText
            .split('\n', ',', ';')
            .map { it.normalizeSmsDigits() }
            .filter { it.isNotEmpty() }
            .distinct()
        return SmsSpeechOverrideEntry(id, title.trim(), patterns, utteranceFieldText.trim())
    }
}

private fun speechCardSummary(ctx: Context, e: SmsSpeechOverrideEntry): String =
    ctx.getString(
        R.string.experiment_sms_speech_override_card_summary_fmt,
        e.senderDigitPatterns.size,
        e.utterance.trim().take(48),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsSpeechOverridesScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { SmsSpeechOverrideRepository(context) }
    var entries by remember { mutableStateOf(repo.load()) }
    fun persist(next: List<SmsSpeechOverrideEntry>) {
        entries = next
        repo.save(next)
    }

    var editorState by remember { mutableStateOf<SpeechOverrideEditorState?>(null) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.experiment_sms_speech_overrides_title)) },
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
                    editorState = SpeechOverrideEditorState(
                        SmsSpeechOverrideEntry(
                            id = UUID.randomUUID().toString(),
                            title = context.getString(R.string.experiment_sms_speech_override_new_title),
                            senderDigitPatterns = emptyList(),
                            utterance = "",
                        ),
                    )
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.experiment_sms_speech_override_add_cd))
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.experiment_sms_speech_overrides_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(entries, key = { it.id }) { e ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        speechCardSummary(context, e),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                IconButton(onClick = { editorState = SpeechOverrideEditorState(e) }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.experiment_sms_speech_override_edit_cd))
                                }
                                IconButton(onClick = { deleteConfirmId = e.id }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.travel_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteConfirmId?.let { did ->
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text(stringResource(R.string.experiment_sms_speech_override_delete_title)) },
            text = { Text(stringResource(R.string.experiment_sms_speech_override_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        persist(entries.filter { it.id != did })
                        deleteConfirmId = null
                    },
                ) {
                    Text(stringResource(R.string.travel_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) {
                    Text(stringResource(R.string.travel_cancel))
                }
            },
        )
    }

    editorState?.let {
        AlertDialog(
            onDismissRequest = { editorState = null },
            title = { Text(stringResource(R.string.experiment_sms_speech_override_editor_title)) },
            dismissButton = {
                TextButton(onClick = { editorState = null }) {
                    Text(stringResource(R.string.travel_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleaned = editorState?.toEntry() ?: return@TextButton
                        if (cleaned.title.isBlank() || cleaned.senderDigitPatterns.isEmpty() || cleaned.utterance.isBlank()) {
                            return@TextButton
                        }
                        val idx = entries.indexOfFirst { it.id == cleaned.id }
                        val next =
                            if (idx >= 0) {
                                entries.toMutableList().also { it[idx] = cleaned }
                            } else {
                                entries + cleaned
                            }
                        persist(next)
                        editorState = null
                    },
                ) {
                    Text(stringResource(R.string.travel_save))
                }
            },
            text = {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = editorState!!.title,
                        onValueChange = { nv -> editorState = editorState!!.copy(title = nv) },
                        label = { Text(stringResource(R.string.experiment_sms_speech_override_field_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editorState!!.senderFieldText,
                        onValueChange = { nv -> editorState = editorState!!.copy(senderFieldText = nv) },
                        label = { Text(stringResource(R.string.experiment_sms_speech_override_field_numbers)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 6,
                        supportingText = { Text(stringResource(R.string.experiment_sms_speech_override_field_numbers_hint)) },
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editorState!!.utteranceFieldText,
                        onValueChange = { nv -> editorState = editorState!!.copy(utteranceFieldText = nv) },
                        label = { Text(stringResource(R.string.experiment_sms_speech_override_field_utterance)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 6,
                        supportingText = { Text(stringResource(R.string.experiment_sms_speech_override_field_utterance_hint)) },
                    )
                }
            },
        )
    }
}
