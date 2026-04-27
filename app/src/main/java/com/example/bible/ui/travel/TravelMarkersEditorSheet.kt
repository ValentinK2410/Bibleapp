package com.example.bible.ui.travel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.travel.TravelMapIncident
import com.example.bible.data.travel.TravelUserSoundStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val PICK_MARKER_DEFAULT = "__def_marker__"
private const val PICK_POLYGON_GLOBAL = "__def_poly__"

@Composable
fun TravelMarkersEditorSheet(
    vm: TravelViewModel,
    mapIncidents: List<TravelMapIncident>,
    markerProximityEnabled: Boolean,
    markerDefaultSoundUri: String?,
    polygonEntrySoundUri: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingPickTarget by remember { mutableStateOf<String?>(null) }
    var recordTarget by remember { mutableStateOf<String?>(null) }

    val pickAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        val target = pendingPickTarget
        pendingPickTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                TravelUserSoundStorage.copyUriToFilesDir(context, uri)
            } ?: return@launch
            val stored = TravelUserSoundStorage.toFileUriString(path)
            when (target) {
                PICK_MARKER_DEFAULT -> vm.setMarkerDefaultSoundUri(stored)
                PICK_POLYGON_GLOBAL -> vm.setPolygonEntrySoundUri(stored)
                else -> {
                    val inc = mapIncidents.find { it.id == target } ?: return@launch
                    vm.replaceMapIncident(inc.copy(soundUri = stored))
                }
            }
        }
    }

    fun applyStoredSound(stored: String) {
        val target = recordTarget ?: return
        recordTarget = null
        when (target) {
            PICK_MARKER_DEFAULT -> vm.setMarkerDefaultSoundUri(stored)
            PICK_POLYGON_GLOBAL -> vm.setPolygonEntrySoundUri(stored)
            else -> {
                val inc = mapIncidents.find { it.id == target } ?: return
                vm.replaceMapIncident(inc.copy(soundUri = stored))
            }
        }
    }

    if (recordTarget != null) {
        TravelRecordSoundDialog(
            onDismiss = { recordTarget = null },
            onSoundSaved = { stored -> applyStoredSound(stored) },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.travel_markers_editor_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.travel_marker_proximity_switch),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = markerProximityEnabled,
                    onCheckedChange = { vm.setMarkerProximityEnabled(it) },
                )
            }
            Text(
                stringResource(R.string.travel_marker_proximity_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.travel_polygon_global_sound),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.travel_polygon_global_sound_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        TextButton(onClick = {
                            pendingPickTarget = PICK_POLYGON_GLOBAL
                            pickAudio.launch("audio/*")
                        }) {
                            Text(stringResource(R.string.travel_marker_sound_pick))
                        }
                        TextButton(onClick = { recordTarget = PICK_POLYGON_GLOBAL }) {
                            Text(stringResource(R.string.travel_marker_sound_record))
                        }
                        TextButton(
                            onClick = { vm.setPolygonEntrySoundUri(null) },
                            enabled = !polygonEntrySoundUri.isNullOrBlank(),
                        ) {
                            Text(stringResource(R.string.travel_marker_sound_clear))
                        }
                    }
                    polygonEntrySoundUri?.let { u ->
                        Text(
                            stringResource(R.string.travel_media_picked, u.take(40)),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.travel_marker_default_sound),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.travel_marker_default_sound_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        TextButton(onClick = {
                            pendingPickTarget = PICK_MARKER_DEFAULT
                            pickAudio.launch("audio/*")
                        }) {
                            Text(stringResource(R.string.travel_marker_sound_pick))
                        }
                        TextButton(onClick = { recordTarget = PICK_MARKER_DEFAULT }) {
                            Text(stringResource(R.string.travel_marker_sound_record))
                        }
                        TextButton(
                            onClick = { vm.setMarkerDefaultSoundUri(null) },
                            enabled = !markerDefaultSoundUri.isNullOrBlank(),
                        ) {
                            Text(stringResource(R.string.travel_marker_sound_clear))
                        }
                    }
                    markerDefaultSoundUri?.let { u ->
                        Text(
                            stringResource(R.string.travel_media_picked, u.take(40)),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        item {
            HorizontalDivider()
            Text(
                stringResource(R.string.travel_markers_list_edit_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (mapIncidents.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.travel_markers_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(mapIncidents, key = { it.id }) { inc ->
                IncidentEditorCard(
                    incident = inc,
                    onPickSound = {
                        pendingPickTarget = inc.id
                        pickAudio.launch("audio/*")
                    },
                    onRecordSound = { recordTarget = inc.id },
                    onClearSound = {
                        vm.replaceMapIncident(inc.copy(soundUri = null))
                    },
                    onSaveNote = { note ->
                        vm.replaceMapIncident(inc.copy(note = note.trim()))
                    },
                )
            }
        }
        item {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.travel_markers_editor_done))
            }
        }
    }
}

@Composable
private fun IncidentEditorCard(
    incident: TravelMapIncident,
    onPickSound: () -> Unit,
    onRecordSound: () -> Unit,
    onClearSound: () -> Unit,
    onSaveNote: (String) -> Unit,
) {
    var noteDraft by remember(incident.id) { mutableStateOf(incident.note) }
    LaunchedEffect(incident.id, incident.note) {
        noteDraft = incident.note
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                String.format(
                    Locale.getDefault(),
                    "%.5f, %.5f",
                    incident.latitude,
                    incident.longitude,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = noteDraft,
                onValueChange = { noteDraft = it },
                label = { Text(stringResource(R.string.travel_incident_note_label)) },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { onSaveNote(noteDraft) }) {
                Text(stringResource(R.string.travel_marker_save_note))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPickSound) {
                    Text(stringResource(R.string.travel_marker_sound_pick))
                }
                TextButton(onClick = onRecordSound) {
                    Text(stringResource(R.string.travel_marker_sound_record))
                }
                TextButton(
                    onClick = onClearSound,
                    enabled = !incident.soundUri.isNullOrBlank(),
                ) {
                    Text(stringResource(R.string.travel_marker_sound_clear))
                }
            }
            incident.soundUri?.let { u ->
                Text(
                    stringResource(R.string.travel_media_picked, u.take(40)),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
