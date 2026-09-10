package com.example.bible.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.GigaChatClient

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GigaChatSettingsScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
) {
    val savedKey by viewModel.gigaChatAuthKey.collectAsStateWithLifecycle()
    val savedScope by viewModel.gigaChatScope.collectAsStateWithLifecycle()
    val keyTest by viewModel.gigaChatKeyTest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var draft by remember { mutableStateOf(savedKey) }
    LaunchedEffect(savedKey) { draft = savedKey }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gigachat_settings_title)) },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.gigachat_settings_key_title)) },
                supportingContent = {
                    Text(
                        if (savedKey.isNotBlank()) {
                            stringResource(R.string.gigachat_settings_saved)
                        } else {
                            stringResource(R.string.gigachat_settings_empty)
                        },
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Filled.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
            Text(
                stringResource(R.string.gigachat_settings_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringResource(R.string.gigachat_settings_key_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Text(
                stringResource(R.string.gigachat_settings_scope_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = savedScope == GigaChatClient.SCOPE_PERS,
                    onClick = { viewModel.setGigaChatScope(GigaChatClient.SCOPE_PERS) },
                    label = { Text(stringResource(R.string.gigachat_scope_pers)) },
                )
                FilterChip(
                    selected = savedScope == GigaChatClient.SCOPE_B2B,
                    onClick = { viewModel.setGigaChatScope(GigaChatClient.SCOPE_B2B) },
                    label = { Text(stringResource(R.string.gigachat_scope_b2b)) },
                )
                FilterChip(
                    selected = savedScope == GigaChatClient.SCOPE_CORP,
                    onClick = { viewModel.setGigaChatScope(GigaChatClient.SCOPE_CORP) },
                    label = { Text(stringResource(R.string.gigachat_scope_corp)) },
                )
            }
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.sber.ru/studio/")),
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.gigachat_settings_get_key))
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = { viewModel.setGigaChatAuthKey(draft) }) {
                    Text(stringResource(R.string.gigachat_settings_save))
                }
                TextButton(
                    onClick = { viewModel.testGigaChatKey(draft) },
                    enabled = !keyTest.loading,
                ) {
                    Text(stringResource(R.string.gigachat_settings_test))
                }
                TextButton(onClick = {
                    draft = ""
                    viewModel.setGigaChatAuthKey("")
                }) {
                    Text(stringResource(R.string.gigachat_settings_clear))
                }
                if (keyTest.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            keyTest.message?.let { msg ->
                Text(
                    msg,
                    color = if (keyTest.ok) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.salute_speech_settings_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                stringResource(R.string.salute_speech_settings_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val savedSaluteKey by viewModel.saluteSpeechAuthKey.collectAsStateWithLifecycle()
            val saluteKeyTest by viewModel.saluteSpeechKeyTest.collectAsStateWithLifecycle()
            var saluteDraft by remember { mutableStateOf(savedSaluteKey) }
            LaunchedEffect(savedSaluteKey) { saluteDraft = savedSaluteKey }
            OutlinedTextField(
                value = saluteDraft,
                onValueChange = { saluteDraft = it },
                label = { Text(stringResource(R.string.salute_speech_settings_key_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { viewModel.setSaluteSpeechAuthKey(saluteDraft) }) {
                    Text(stringResource(R.string.gigachat_settings_save))
                }
                TextButton(
                    onClick = { viewModel.testSaluteSpeechKey(saluteDraft) },
                    enabled = !saluteKeyTest.loading,
                ) {
                    Text(stringResource(R.string.salute_speech_settings_test))
                }
                TextButton(onClick = {
                    saluteDraft = ""
                    viewModel.setSaluteSpeechAuthKey("")
                }) {
                    Text(stringResource(R.string.gigachat_settings_clear))
                }
            }
            saluteKeyTest.message?.let { msg ->
                Text(
                    msg,
                    color = if (saluteKeyTest.ok) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://developers.sber.ru/docs/ru/salutespeech/quick-start/integration-individuals"),
                        ),
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.salute_speech_settings_get_key))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.gigachat_settings_voice_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            val aiTtsSettings by viewModel.aiChatTtsSettings.collectAsStateWithLifecycle()
            val gigaKey by viewModel.gigaChatAuthKey.collectAsStateWithLifecycle()
            val saluteScope by viewModel.saluteSpeechScope.collectAsStateWithLifecycle()
            val speechHandle = rememberAiChatTextToSpeech(
                saluteAuthKey = savedSaluteKey,
                gigaChatAuthKey = gigaKey,
                saluteScope = saluteScope,
            )
            AiChatTtsSettingsPanel(
                settings = aiTtsSettings,
                neuralVoices = speechHandle.neuralVoices,
                systemVoices = speechHandle.systemVoices,
                enabled = true,
                onEngineChange = viewModel::setAiChatTtsEngine,
                onIntonationChange = viewModel::setAiChatTtsIntonation,
                onVoiceChange = viewModel::setAiChatTtsVoice,
                onPreview = {
                    speechHandle.preview(context.getString(R.string.ai_chat_tts_preview_phrase))
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
