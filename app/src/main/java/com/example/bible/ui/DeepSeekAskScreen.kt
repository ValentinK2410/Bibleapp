package com.example.bible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.AiChatSummary
import com.example.bible.data.AiChatVoiceText
import com.example.bible.data.TranslationId
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepSeekAskScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMicroblogPost: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val state by viewModel.deepSeekAsk.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var speakAnswers by rememberSaveable { mutableStateOf(false) }
    var replyWasLoading by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AiChatSummary?>(null) }
    var shareMenu by remember { mutableStateOf(false) }
    val dateFormat = remember {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    }
    val hasConversation = state.messages.any { it.role == "user" || it.role == "assistant" }
    val tts = rememberStudyTextToSpeech(TranslationId.SYNODAL)
    var lastQuestion by remember { mutableStateOf("") }
    val speech = rememberAiSpeechToText(
        onPartial = { draft = it },
        onFinal = { text ->
            draft = text
            when {
                text.isBlank() -> Unit
                state.loading ->
                    Toast.makeText(context, R.string.ai_ask_wait_reply, Toast.LENGTH_SHORT).show()
                else -> {
                    tts.stop()
                    lastQuestion = text
                    viewModel.askDeepSeekQuestion(text)
                    draft = ""
                }
            }
        },
    )
    // Ответ не пришёл (обрыв связи) — возвращаем вопрос в поле, чтобы его не пришлось диктовать заново.
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
    LaunchedEffect(Unit) {
        viewModel.openDeepSeekAsk()
    }
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            speech.stop()
            viewModel.leaveDeepSeekAsk()
        }
    }
    val goBack = {
        if (state.pane == DeepSeekAskPane.CHAT) {
            viewModel.showDeepSeekAskList()
        } else {
            onBack()
        }
    }
    BackHandler(onBack = { goBack() })
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.pane == DeepSeekAskPane.CHAT) {
                            state.chatTitle.ifBlank { stringResource(R.string.ai_ask_title) }
                        } else {
                            stringResource(R.string.ai_ask_chats_title)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (state.pane == DeepSeekAskPane.CHAT) {
                        if (hasConversation) {
                            Box {
                                IconButton(onClick = { shareMenu = true }) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.ai_ask_copy_chat),
                                    )
                                }
                                DropdownMenu(
                                    expanded = shareMenu,
                                    onDismissRequest = { shareMenu = false },
                                ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ai_ask_copy_chat)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                    },
                                    onClick = {
                                        shareMenu = false
                                        val text = viewModel.deepSeekAskPlainText()
                                        if (text.isBlank()) {
                                            Toast.makeText(
                                                context,
                                                R.string.ai_ask_publish_empty,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            copyAskChat(context, text)
                                            Toast.makeText(
                                                context,
                                                R.string.ai_ask_copied,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ai_ask_publish_microblog)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Forum, contentDescription = null)
                                    },
                                    onClick = {
                                        shareMenu = false
                                        viewModel.publishDeepSeekAskToMicroblog { result ->
                                            result.fold(
                                                onSuccess = { id ->
                                                    Toast.makeText(
                                                        context,
                                                        R.string.ai_ask_published_microblog,
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                    onOpenMicroblogPost(id)
                                                },
                                                onFailure = { e ->
                                                    Toast.makeText(
                                                        context,
                                                        e.message ?: context.getString(R.string.ai_ask_publish_empty),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                            }
                        }
                        IconButton(
                            onClick = {
                                draft = ""
                                tts.stop()
                                speech.stop()
                                viewModel.startNewDeepSeekAsk()
                            },
                            enabled = !state.loading,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.ai_ask_new_chat_cd),
                            )
                        }
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
                Text(stringResource(R.string.deepseek_needs_key))
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.deepseek_open_settings))
                }
                Spacer(Modifier.height(16.dp))
            }
            if (state.pane == DeepSeekAskPane.LIST) {
                DeepSeekAskChatList(
                    chats = state.chats,
                    dateFormat = dateFormat,
                    onNewChat = {
                        draft = ""
                        viewModel.startNewDeepSeekAsk()
                    },
                    onOpenChat = { viewModel.openDeepSeekAskChat(it) },
                    onDeleteChat = { deleteTarget = it },
                )
            } else {
                DeepSeekAskConversation(
                    state = state,
                    draft = draft,
                    onDraftChange = { draft = it },
                    listening = speech.listening,
                    speakAnswers = speakAnswers,
                    onToggleSpeakAnswers = {
                        speakAnswers = !speakAnswers
                        if (!speakAnswers) tts.stop()
                    },
                    onSpeakMessage = { text ->
                        tts.speak(AiChatVoiceText.forSpeech(text))
                    },
                    onMicClick = {
                        tts.stop()
                        if (speech.listening) speech.stop() else speech.start()
                    },
                    onQuick = { viewModel.setDeepSeekAskStyle(DeepSeekAskStyle.QUICK) },
                    onDeep = { viewModel.setDeepSeekAskStyle(DeepSeekAskStyle.DEEP) },
                    onToggleWeb = { viewModel.setDeepSeekAskWebSearch(!state.webSearch) },
                    onSend = {
                        val q = draft.trim()
                        if (q.isNotEmpty()) {
                            speech.stop()
                            tts.stop()
                            lastQuestion = q
                            viewModel.askDeepSeekQuestion(q)
                            draft = ""
                        }
                    },
                    onRetry = {
                        val q = lastQuestion.trim().ifBlank { draft.trim() }
                        if (q.isNotEmpty()) {
                            tts.stop()
                            speech.stop()
                            lastQuestion = q
                            viewModel.askDeepSeekQuestion(q)
                            draft = ""
                        }
                    },
                )
            }
        }
    }
    deleteTarget?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.ai_ask_delete_chat)) },
            text = { Text(stringResource(R.string.ai_ask_delete_chat_body, chat.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDeepSeekAskChat(chat.id)
                        deleteTarget = null
                    },
                ) {
                    Text(
                        stringResource(R.string.ai_ask_delete_chat_cd),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun DeepSeekAskChatList(
    chats: List<AiChatSummary>,
    dateFormat: SimpleDateFormat,
    onNewChat: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onDeleteChat: (AiChatSummary) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Button(onClick = onNewChat, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ai_ask_new_chat))
        }
        Spacer(Modifier.height(12.dp))
        if (chats.isEmpty()) {
            Text(
                stringResource(R.string.ai_ask_chats_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.ai_ask_chats_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(chats, key = { it.id }) { chat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(chat.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                chat.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                dateFormat.format(Date(chat.updatedAtMs)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDeleteChat(chat) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.ai_ask_delete_chat_cd),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DeepSeekAskConversation(
    state: DeepSeekAskUiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    listening: Boolean,
    speakAnswers: Boolean,
    onToggleSpeakAnswers: () -> Unit,
    onSpeakMessage: (String) -> Unit,
    onMicClick: () -> Unit,
    onQuick: () -> Unit,
    onDeep: () -> Unit,
    onToggleWeb: () -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.messages.isEmpty() && !state.loading) {
                Text(
                    stringResource(R.string.ai_ask_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.messages.forEach { msg ->
                val isUser = msg.role == "user"
                if (isUser) {
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
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onSpeakMessage(msg.content) },
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
                        onClick = onRetry,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.style == DeepSeekAskStyle.QUICK,
                onClick = onQuick,
                enabled = !state.loading,
                leadingIcon = {
                    Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                label = { Text(stringResource(R.string.ai_ask_mode_quick)) },
            )
            FilterChip(
                selected = state.style == DeepSeekAskStyle.DEEP,
                onClick = onDeep,
                enabled = !state.loading,
                leadingIcon = {
                    Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                label = { Text(stringResource(R.string.ai_ask_mode_deep)) },
            )
            FilterChip(
                selected = state.webSearch,
                onClick = onToggleWeb,
                enabled = !state.loading,
                leadingIcon = {
                    Icon(Icons.Filled.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                label = { Text(stringResource(R.string.ai_ask_mode_web)) },
            )
            FilterChip(
                selected = speakAnswers,
                onClick = onToggleSpeakAnswers,
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
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                label = {
                    Text(
                        stringResource(
                            if (listening) R.string.ai_ask_listening else R.string.ai_ask_field,
                        ),
                    )
                },
                minLines = 2,
                enabled = !state.loading,
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onMicClick,
                enabled = !state.loading,
            ) {
                Icon(
                    if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = stringResource(
                        if (listening) R.string.ai_ask_voice_stop_cd else R.string.ai_ask_voice_cd,
                    ),
                    tint = if (listening) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            IconButton(
                onClick = onSend,
                enabled = !state.loading && draft.isNotBlank(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.deepseek_send),
                )
            }
        }
    }
}

private fun copyAskChat(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("ai_chat", text))
}
