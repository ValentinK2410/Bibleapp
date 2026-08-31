package com.example.bible.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TravelExplore
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepSeekAskScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.deepSeekAsk.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        onDispose { viewModel.clearDeepSeekAsk() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_ask_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
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
                return@Column
            }
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
                    onClick = { viewModel.setDeepSeekAskStyle(DeepSeekAskStyle.QUICK) },
                    enabled = !state.loading,
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    label = { Text(stringResource(R.string.ai_ask_mode_quick)) },
                )
                FilterChip(
                    selected = state.style == DeepSeekAskStyle.DEEP,
                    onClick = { viewModel.setDeepSeekAskStyle(DeepSeekAskStyle.DEEP) },
                    enabled = !state.loading,
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    label = { Text(stringResource(R.string.ai_ask_mode_deep)) },
                )
                FilterChip(
                    selected = state.webSearch,
                    onClick = { viewModel.setDeepSeekAskWebSearch(!state.webSearch) },
                    enabled = !state.loading,
                    leadingIcon = {
                        Icon(
                            Icons.Filled.TravelExplore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    label = { Text(stringResource(R.string.ai_ask_mode_web)) },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.ai_ask_field)) },
                    minLines = 2,
                    enabled = !state.loading,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val q = draft.trim()
                        if (q.isNotEmpty()) {
                            viewModel.askDeepSeekQuestion(q)
                            draft = ""
                        }
                    },
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
}
