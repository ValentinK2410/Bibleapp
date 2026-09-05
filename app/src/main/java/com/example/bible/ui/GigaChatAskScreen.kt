package com.example.bible.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.bible.R
import com.example.bible.data.AiChatSummary
import com.example.bible.data.AiChatVoiceText
import com.example.bible.data.GigaChatContentPart
import com.example.bible.data.GigaChatImages
import com.example.bible.data.TranslationId
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GigaChatAskScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMicroblogPost: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val state by viewModel.gigaChatAsk.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var speakAnswers by rememberSaveable { mutableStateOf(true) }
    var replyWasLoading by remember { mutableStateOf(false) }
    var lastQuestion by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<AiChatSummary?>(null) }
    var shareMenu by remember { mutableStateOf(false) }
    val dateFormat = remember {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    }
    val hasConversation = state.messages.any { it.role == "user" || it.role == "assistant" }
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
    val imagesDir = remember { GigaChatImages.dir(context) }
    LaunchedEffect(state.messages) {
        val lastUser = state.messages.lastOrNull { it.role == "user" }?.content?.trim().orEmpty()
        if (lastUser.isNotBlank() && lastUser != "Голосовой вопрос") {
            lastQuestion = lastUser
        }
    }
    LaunchedEffect(state.error) {
        if (state.error != null && draft.isBlank() && lastQuestion.isNotBlank() && lastQuestion != "Голосовой вопрос") {
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
        viewModel.openGigaChatAsk()
    }
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            speech.stop()
            viewModel.leaveGigaChatAsk()
        }
    }
    val goBack = {
        if (state.pane == DeepSeekAskPane.CHAT) {
            viewModel.showGigaChatAskList()
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
                            state.chatTitle.ifBlank { stringResource(R.string.gigachat_ask_title) }
                        } else {
                            stringResource(R.string.gigachat_ask_chats_title)
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
                                            val text = viewModel.gigaChatAskPlainText()
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
                                            viewModel.publishGigaChatAskToMicroblog { result ->
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
                                viewModel.startNewGigaChatAsk()
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
                Text(stringResource(R.string.gigachat_needs_key))
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.gigachat_open_settings))
                }
                Spacer(Modifier.height(16.dp))
            }
            if (state.pane == DeepSeekAskPane.LIST) {
                DeepSeekAskChatList(
                    chats = state.chats,
                    dateFormat = dateFormat,
                    onNewChat = {
                        draft = ""
                        viewModel.startNewGigaChatAsk()
                    },
                    onOpenChat = { viewModel.openGigaChatAskChat(it) },
                    onDeleteChat = { deleteTarget = it },
                )
            } else {
                GigaChatAskConversation(
                    state = state,
                    draft = draft,
                    onDraftChange = { draft = it },
                    listening = speech.listening,
                    speakAnswers = speakAnswers,
                    imagesDir = imagesDir,
                    onToggleSpeakAnswers = {
                        speakAnswers = !speakAnswers
                        if (!speakAnswers) tts.stop()
                    },
                    onSpeakMessage = { text ->
                        tts.speak(AiChatVoiceText.forSpeech(text))
                    },
                    onSaveImage = { file ->
                        viewModel.saveGigaChatImageToGallery(file) { result ->
                            Toast.makeText(
                                context,
                                if (result.isSuccess) {
                                    R.string.gigachat_image_saved
                                } else {
                                    R.string.gigachat_image_save_failed
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onMicClick = {
                        tts.stop()
                        if (speech.listening) speech.stop() else speech.start()
                    },
                    onSend = {
                        val q = draft.trim()
                        if (q.isNotEmpty()) {
                            speech.stop()
                            tts.stop()
                            lastQuestion = q
                            viewModel.askGigaChatQuestion(q)
                            draft = ""
                        }
                    },
                    onRetry = {
                        val q = lastQuestion.trim().ifBlank { draft.trim() }
                        if (q.isNotEmpty() && q != "Голосовой вопрос") {
                            tts.stop()
                            speech.stop()
                            lastQuestion = q
                            viewModel.askGigaChatQuestion(q)
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
                        viewModel.deleteGigaChatAskChat(chat.id)
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
private fun GigaChatAskConversation(
    state: GigaChatAskUiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    listening: Boolean,
    speakAnswers: Boolean,
    imagesDir: File,
    onToggleSpeakAnswers: () -> Unit,
    onSpeakMessage: (String) -> Unit,
    onSaveImage: (File) -> Unit,
    onMicClick: () -> Unit,
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
                    stringResource(R.string.gigachat_ask_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.messages.forEach { msg ->
                if (msg.role == "user") {
                    AiChatUserRichMessage(
                        content = msg.content,
                        imagesDir = imagesDir,
                        onSaveImage = onSaveImage,
                    )
                } else {
                    GigaChatAssistantMessage(
                        content = msg.content,
                        imagesDir = imagesDir,
                        enabled = !state.loading,
                        onSpeakMessage = onSpeakMessage,
                        onSaveImage = onSaveImage,
                    )
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
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                label = {
                    Text(
                        stringResource(
                            if (listening) R.string.gigachat_ask_listening else R.string.ai_ask_field,
                        ),
                    )
                },
                minLines = 2,
                enabled = !state.loading && !state.needsKey,
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onMicClick,
                enabled = !state.loading && !state.needsKey,
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

@Composable
internal fun AiChatUserRichMessage(
    content: String,
    imagesDir: File,
    onSaveImage: ((File) -> Unit)? = null,
) {
    val parts = remember(content, imagesDir) { GigaChatImages.parts(content, imagesDir) }
    val text = parts.filterIsInstance<GigaChatContentPart.Text>().joinToString(" ") { it.value }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (text.isNotBlank()) {
            Text(
                stringResource(R.string.ai_ask_you, text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.ai_ask_you, stringResource(R.string.ai_identify_title)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        parts.forEach { part ->
            if (part is GigaChatContentPart.Image) {
                Box(Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = part.file,
                        contentDescription = stringResource(R.string.gigachat_image_cd),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        contentScale = ContentScale.Fit,
                    )
                    if (onSaveImage != null) {
                        IconButton(
                            onClick = { onSaveImage(part.file) },
                            modifier = Modifier.align(Alignment.BottomEnd),
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = stringResource(R.string.gigachat_image_save_cd),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GigaChatAssistantMessage(
    content: String,
    imagesDir: File,
    enabled: Boolean,
    onSpeakMessage: (String) -> Unit,
    onSaveImage: (File) -> Unit,
) {
    val parts = remember(content, imagesDir) { GigaChatImages.parts(content, imagesDir) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            parts.forEach { part ->
                when (part) {
                    is GigaChatContentPart.Text -> Text(
                        part.value,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    is GigaChatContentPart.Image -> Box(Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = part.file,
                            contentDescription = stringResource(R.string.gigachat_image_cd),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp),
                            contentScale = ContentScale.Fit,
                        )
                        IconButton(
                            onClick = { onSaveImage(part.file) },
                            enabled = enabled,
                            modifier = Modifier.align(Alignment.BottomEnd),
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = stringResource(R.string.gigachat_image_save_cd),
                            )
                        }
                    }
                    GigaChatContentPart.MissingImage -> Text(
                        stringResource(R.string.gigachat_image_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        IconButton(
            onClick = { onSpeakMessage(content) },
            enabled = enabled,
        ) {
            Icon(
                Icons.Filled.VolumeUp,
                contentDescription = stringResource(R.string.ai_ask_speak_cd),
            )
        }
    }
}
