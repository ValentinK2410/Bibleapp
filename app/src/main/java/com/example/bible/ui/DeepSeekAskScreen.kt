package com.example.bible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.AiChatSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepSeekAskScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.deepSeekAsk.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<AiChatSummary?>(null) }
    val dateFormat = remember {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    }
    LaunchedEffect(Unit) {
        viewModel.openDeepSeekAsk()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.leaveDeepSeekAsk() }
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
                        IconButton(
                            onClick = {
                                draft = ""
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
                    onQuick = { viewModel.setDeepSeekAskStyle(DeepSeekAskStyle.QUICK) },
                    onDeep = { viewModel.setDeepSeekAskStyle(DeepSeekAskStyle.DEEP) },
                    onToggleWeb = { viewModel.setDeepSeekAskWebSearch(!state.webSearch) },
                    onSend = {
                        val q = draft.trim()
                        if (q.isNotEmpty()) {
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
    onQuick: () -> Unit,
    onDeep: () -> Unit,
    onToggleWeb: () -> Unit,
    onSend: () -> Unit,
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
                Text(
                    if (isUser) {
                        stringResource(R.string.ai_ask_you, msg.content)
                    } else {
                        msg.content
                    },
                    style = if (isUser) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
            state.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error)
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
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.ai_ask_field)) },
                minLines = 2,
                enabled = !state.loading,
            )
            Spacer(Modifier.width(8.dp))
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
