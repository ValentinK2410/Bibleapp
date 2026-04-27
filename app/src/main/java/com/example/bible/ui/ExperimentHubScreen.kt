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
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Videocam
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
fun ExperimentHubScreen(
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenCameraControl: () -> Unit,
    onOpenCamera4: () -> Unit,
    onOpenCamera5MediaPipe: () -> Unit,
    onOpenCallsSms: () -> Unit,
    onOpenSensorLab: () -> Unit,
    onOpenSoundLab: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.experiment_title)) },
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
                text = stringResource(R.string.experiment_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            ExperimentHubSectionButton(
                icon = { Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(28.dp)) },
                title = stringResource(R.string.experiment_hub_section_camera),
                description = stringResource(R.string.experiment_hub_section_camera_desc),
                onClick = onOpenCamera,
            )
            ExperimentHubSectionButton(
                icon = { Icon(Icons.Filled.Gesture, contentDescription = null, modifier = Modifier.size(28.dp)) },
                title = stringResource(R.string.experiment_hub_section_camera_control),
                description = stringResource(R.string.experiment_hub_section_camera_control_desc),
                onClick = onOpenCameraControl,
            )
            ExperimentHubSectionButton(
                icon = { Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(28.dp)) },
                title = stringResource(R.string.experiment_hub_section_camera4),
                description = stringResource(R.string.experiment_hub_section_camera4_desc),
                onClick = onOpenCamera4,
            )
            ExperimentHubSectionButton(
                icon = { Icon(Icons.Filled.Face, contentDescription = null, modifier = Modifier.size(28.dp)) },
                title = stringResource(R.string.experiment_hub_section_camera5_mediapipe),
                description = stringResource(R.string.experiment_hub_section_camera5_mediapipe_desc),
                onClick = onOpenCamera5MediaPipe,
            )
            ExperimentHubSectionButton(
                icon = { Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(28.dp)) },
                title = stringResource(R.string.experiment_hub_section_calls),
                description = stringResource(R.string.experiment_hub_section_calls_desc),
                onClick = onOpenCallsSms,
            )
            ExperimentHubSectionButton(
                icon = { Icon(Icons.Filled.Sensors, contentDescription = null, modifier = Modifier.size(28.dp)) },
                title = stringResource(R.string.experiment_hub_section_sensor_lab),
                description = stringResource(R.string.experiment_hub_section_sensor_lab_desc),
                onClick = onOpenSensorLab,
            )
            ExperimentHubSectionButton(
                icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(28.dp)) },
                title = stringResource(R.string.experiment_hub_section_sound_lab),
                description = stringResource(R.string.experiment_hub_section_sound_lab_desc),
                onClick = onOpenSoundLab,
            )
        }
    }
}

@Composable
private fun ExperimentHubSectionButton(
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
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
