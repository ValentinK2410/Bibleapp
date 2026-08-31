package com.example.bible.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_hub_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            AiHubSectionButton(
                icon = { Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(28.dp)) },
                title = stringResource(R.string.ai_hub_ask),
                description = stringResource(R.string.ai_hub_ask_desc),
                onClick = onOpenAsk,
            )
            AiHubSectionButton(
                icon = { Icon(Icons.Filled.ImageSearch, contentDescription = null, modifier = Modifier.size(28.dp)) },
                title = stringResource(R.string.ai_hub_identify),
                description = stringResource(R.string.ai_hub_identify_desc),
                onClick = onOpenIdentifyPhoto,
            )
            AiHubSectionButton(
                icon = { Icon(Icons.Filled.TextSnippet, contentDescription = null, modifier = Modifier.size(28.dp)) },
                title = stringResource(R.string.ai_hub_transcribe),
                description = stringResource(R.string.ai_hub_transcribe_desc),
                onClick = onOpenTranscribePhoto,
            )
            AiHubSectionButton(
                icon = { Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(28.dp)) },
                title = stringResource(R.string.ai_hub_settings),
                description = stringResource(R.string.ai_hub_settings_desc),
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun AiHubSectionButton(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            icon()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
