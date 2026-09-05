package com.example.bible.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.AiChatVoiceText
import com.example.bible.data.TranslationId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GigaChatAskScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.gigaChatAsk.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var speakAnswers by rememberSaveable { mutableStateOf(true) }
    var replyWasLoading by remember { mutableStateOf(false) }
    var lastQuestion by remember { mutableStateOf("") }
    val tts = rememberStudyTextToSpeech(TranslationId.SYNODAL)
    val speech = rememberGigaChatVoiceRecorder(
        onRecorded = { file ->
            if (state.loading) {
                file.delete()
                Toast.makeText(context, R.string.ai_ask_wait_reply, Toast.LENGTH_SHORT).show()
            } else {
                tts.stop()
                lastQuestion = "Голосовой вопрос"
                viewModel.askGigaChatVoice(file.absolutePath)
            }
        },
    )
    LaunchedEffect(state.error) {
        if (state.error != null && draft.isBlank() && lastQuestion.isNotBlank()) {
            draft = lastQuestion
        }
    }
    LaunchedEffect(state.loading, state.messages, speakAnswers) {
        val finished = replyWasLoading && !state.loading
        replyWasLoading = state.loading
        if (finished && speakAnswers && state.error == null) {
            val last = state.messages.lastOrNull { it.role == "assistant" }
            if (last != null) {
                tts.speak(AiChatVoiceText.forSpeech(last.content))
            }
        }
        if (!speakAnswers) tts.stop()
    }
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            speech.stop()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gigachat_ask_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            draft = ""
                            tts.stop()
                            speech.stop()
                            viewModel.clearGigaChatAsk()
                        },
                        enabled = !state.loading,
                    ) {
                        Text(stringResource(R.string.gigachat_ask_clear))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .padding(16.dp),
        ) {
            if (state.needsKey) {
                Text(stringResource(R.string.gigachat_needs_key))
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.gigachat_open_settings))
                }
                Spacer(Modifier.height(16.dp))
            }
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.messages.isEmpty() && !state.loading && !state.needsKey) {
                        Text(
                            stringResource(R.string.gigachat_ask_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.messages.forEach { msg ->
                        if (msg.role == "user") {
                            Text(
                                stringResource(R.string.ai_ask_you, msg.content),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    msg.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { tts.speak(AiChatVoiceText.forSpeech(msg.content)) },
                                    enabled = !state.loading,
                                ) {
                                    Icon(
                                        Icons.Filled.VolumeUp,
                                        contentDescription = stringResource(R.string.ai_ask_speak_cd),
                                    )
                                }
                            }
                        }
                    }
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                    state.error?.let { err ->
                        Column {
                            Text(err, color = MaterialTheme.colorScheme.error)
                            TextButton(
                                onClick = {
                                    val q = lastQuestion.trim().ifBlank { draft.trim() }
                                    if (q.isNotEmpty()) {
                                        tts.stop()
                                        speech.stop()
                                        lastQuestion = q
                                        viewModel.askGigaChatQuestion(q)
                                        draft = ""
                                    }
                                },
                                enabled = !state.loading,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.ai_ask_retry))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                FilterChip(
                    selected = speakAnswers,
                    onClick = {
                        speakAnswers = !speakAnswers
                        if (!speakAnswers) tts.stop()
                    },
                    enabled = !state.loading,
                    leadingIcon = {
                        Icon(
                            if (speakAnswers) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    label = { Text(stringResource(R.string.ai_ask_speak_answers)) },
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                stringResource(
                                    if (speech.listening) R.string.gigachat_ask_listening else R.string.ai_ask_field,
                                ),
                            )
                        },
                        minLines = 2,
                        enabled = !state.loading && !state.needsKey,
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            tts.stop()
                            if (speech.listening) speech.stop() else speech.start()
                        },
                        enabled = !state.loading && !state.needsKey,
                    ) {
                        Icon(
                            if (speech.listening) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = stringResource(
                                if (speech.listening) R.string.ai_ask_voice_stop_cd else R.string.ai_ask_voice_cd,
                            ),
                            tint = if (speech.listening) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    IconButton(
                        onClick = {
                            val q = draft.trim()
                            if (q.isNotEmpty()) {
                                speech.stop()
                                tts.stop()
                                lastQuestion = q
                                viewModel.askGigaChatQuestion(q)
                                draft = ""
                            }
                        },
                        enabled = !state.loading && !state.needsKey && draft.isNotBlank(),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.deepseek_send),
                        )
                    }
                }
            }
        }
    }
}
