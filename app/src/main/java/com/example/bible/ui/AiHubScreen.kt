package com.example.bible.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bible.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHubScreen(
    onBack: () -> Unit,
    onOpenAsk: () -> Unit,
    onOpenIdentifyPhoto: () -> Unit,
    onOpenTranscribePhoto: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.ai_hub_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_hub_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            AiHubActionCard(
                icon = Icons.Filled.Chat,
                iconBackground = scheme.primaryContainer,
                iconTint = scheme.onPrimaryContainer,
                title = stringResource(R.string.ai_hub_ask),
                description = stringResource(R.string.ai_hub_ask_desc),
                onClick = onOpenAsk,
            )
            AiHubActionCard(
                icon = Icons.Filled.ImageSearch,
                iconBackground = scheme.tertiaryContainer,
                iconTint = scheme.onTertiaryContainer,
                title = stringResource(R.string.ai_hub_identify),
                description = stringResource(R.string.ai_hub_identify_desc),
                onClick = onOpenIdentifyPhoto,
            )
            AiHubActionCard(
                icon = Icons.Filled.TextSnippet,
                iconBackground = scheme.secondaryContainer,
                iconTint = scheme.onSecondaryContainer,
                title = stringResource(R.string.ai_hub_transcribe),
                description = stringResource(R.string.ai_hub_transcribe_desc),
                onClick = onOpenTranscribePhoto,
            )
            AiHubActionCard(
                icon = Icons.Filled.Key,
                iconBackground = scheme.surfaceVariant,
                iconTint = scheme.onSurfaceVariant,
                title = stringResource(R.string.ai_hub_settings),
                description = stringResource(R.string.ai_hub_settings_desc),
                onClick = onOpenSettings,
            )
        }
    }
}
