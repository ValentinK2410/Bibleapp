package com.example.bible.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R

@Composable
fun DeepSeekVerseDialog(
    viewModel: BibleViewModel,
    target: VerseActionTarget,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.deepSeekChat.collectAsStateWithLifecycle()
    var followUp by remember { mutableStateOf("") }

    LaunchedEffect(target.ref.toKey(), target.verseText) {
        viewModel.askDeepSeekAboutVerse(
            bookName = target.bookName,
            chapter = target.ref.chapter,
            verse = target.ref.verse,
            verseText = target.verseText,
        )
    }

    AlertDialog(
        onDismissRequest = {
            viewModel.clearDeepSeekChat()
            onDismiss()
        },
        title = {
            Text(
                stringResource(
                    R.string.deepseek_verse_title,
                    target.bookName,
                    target.ref.chapter,
                    target.ref.verse,
                ),
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    state.needsKey -> Text(stringResource(R.string.deepseek_needs_key))
                    state.loading && state.answer.isBlank() -> CircularProgressIndicator()
                    state.error != null && state.answer.isBlank() ->
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    else -> {
                        if (state.answer.isNotBlank()) {
                            Text(state.answer, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (state.loading) {
                            Spacer(Modifier.height(12.dp))
                            CircularProgressIndicator()
                        }
                        state.error?.takeIf { state.answer.isNotBlank() }?.let { err ->
                            Spacer(Modifier.height(8.dp))
                            Text(err, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = followUp,
                            onValueChange = { followUp = it },
                            label = { Text(stringResource(R.string.deepseek_follow_up)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            enabled = !state.loading && !state.needsKey,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                state.needsKey -> TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.deepseek_open_settings))
                }
                else -> TextButton(
                    onClick = {
                        val q = followUp.trim()
                        if (q.isNotEmpty()) {
                            viewModel.askDeepSeekFollowUp(q)
                            followUp = ""
                        }
                    },
                    enabled = !state.loading && followUp.isNotBlank(),
                ) {
                    Text(stringResource(R.string.deepseek_send))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.clearDeepSeekChat()
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.timemark_close))
            }
        },
    )
}
