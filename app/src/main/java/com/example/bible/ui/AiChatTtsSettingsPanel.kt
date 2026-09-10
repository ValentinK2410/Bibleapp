package com.example.bible.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.AiChatTtsEngine
import com.example.bible.data.AiChatTtsIntonation
import com.example.bible.data.AiChatTtsSettings
import com.example.bible.data.AiChatTtsVoiceOption

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiChatTtsSettingsPanel(
    settings: AiChatTtsSettings,
    neuralVoices: List<AiChatTtsVoiceOption>,
    systemVoices: List<AiChatTtsVoiceOption>,
    enabled: Boolean,
    onEngineChange: (AiChatTtsEngine) -> Unit,
    onIntonationChange: (AiChatTtsIntonation) -> Unit,
    onVoiceChange: (String) -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voiceOptions = if (settings.engine == AiChatTtsEngine.NEURAL) {
        neuralVoices
    } else {
        systemVoices
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.ai_chat_tts_engine_title),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = settings.engine == AiChatTtsEngine.NEURAL,
                onClick = { onEngineChange(AiChatTtsEngine.NEURAL) },
                enabled = enabled,
                label = { Text(stringResource(R.string.ai_chat_tts_engine_neural)) },
            )
            FilterChip(
                selected = settings.engine == AiChatTtsEngine.SYSTEM,
                onClick = { onEngineChange(AiChatTtsEngine.SYSTEM) },
                enabled = enabled,
                label = { Text(stringResource(R.string.ai_chat_tts_engine_system)) },
            )
        }
        if (settings.engine == AiChatTtsEngine.NEURAL) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.ai_chat_tts_neural_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.ai_chat_tts_intonation_title),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            intonationChip(AiChatTtsIntonation.CALM, settings, enabled, onIntonationChange)
            intonationChip(AiChatTtsIntonation.NORMAL, settings, enabled, onIntonationChange)
            intonationChip(AiChatTtsIntonation.JOYFUL, settings, enabled, onIntonationChange)
            intonationChip(AiChatTtsIntonation.BOLD, settings, enabled, onIntonationChange)
            intonationChip(AiChatTtsIntonation.WHISPER, settings, enabled, onIntonationChange)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.ai_chat_tts_voice_title),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(6.dp))
        AiChatVoiceDropdown(
            engine = settings.engine,
            selectedVoice = settings.voiceName,
            voices = voiceOptions,
            enabled = enabled,
            onVoiceChange = onVoiceChange,
        )
        TextButton(onClick = onPreview, enabled = enabled) {
            Text(stringResource(R.string.ai_chat_tts_preview))
        }
    }
}

@Composable
private fun intonationChip(
    mode: AiChatTtsIntonation,
    settings: AiChatTtsSettings,
    enabled: Boolean,
    onChange: (AiChatTtsIntonation) -> Unit,
) {
    val label = when (mode) {
        AiChatTtsIntonation.CALM -> stringResource(R.string.ai_chat_tts_intonation_calm)
        AiChatTtsIntonation.NORMAL -> stringResource(R.string.ai_chat_tts_intonation_normal)
        AiChatTtsIntonation.JOYFUL -> stringResource(R.string.ai_chat_tts_intonation_joyful)
        AiChatTtsIntonation.BOLD -> stringResource(R.string.ai_chat_tts_intonation_bold)
        AiChatTtsIntonation.WHISPER -> stringResource(R.string.ai_chat_tts_intonation_whisper)
    }
    FilterChip(
        selected = settings.intonation == mode,
        onClick = { onChange(mode) },
        enabled = enabled,
        label = { Text(label) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiChatVoiceDropdown(
    engine: AiChatTtsEngine,
    selectedVoice: String,
    voices: List<AiChatTtsVoiceOption>,
    enabled: Boolean,
    onVoiceChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val autoLabel = stringResource(R.string.ai_chat_tts_voice_auto)
    val selectedLabel = remember(selectedVoice, voices, autoLabel, engine) {
        when {
            engine == AiChatTtsEngine.SYSTEM && selectedVoice.isBlank() -> autoLabel
            else -> voices.firstOrNull { it.name == selectedVoice }?.label ?: selectedVoice
        }
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (engine == AiChatTtsEngine.SYSTEM) {
                DropdownMenuItem(
                    text = { Text(autoLabel) },
                    onClick = {
                        onVoiceChange("")
                        expanded = false
                    },
                )
            }
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            buildString {
                                append(voice.label)
                                if (voice.network && engine == AiChatTtsEngine.SYSTEM) {
                                    append(" · online")
                                }
                            },
                        )
                    },
                    onClick = {
                        onVoiceChange(voice.name)
                        expanded = false
                    },
                )
            }
        }
    }
    if (engine == AiChatTtsEngine.SYSTEM && voices.isEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.ai_chat_tts_voice_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
