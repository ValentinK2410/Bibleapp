package com.example.bible.ui.travel

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.bible.R
import com.example.bible.data.travel.TravelMapIncident
import com.example.bible.service.TravelMediaService
import java.text.DateFormat
import java.util.Locale

/**
 * Нижний лист при тапе по пользовательской метке на карте: просмотр, правка текста, звук, переход в полный редактор, удаление.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentMarkerTapSheet(
    incident: TravelMapIncident?,
    mapIncidents: List<TravelMapIncident>,
    markerDefaultSoundUri: String?,
    onDismiss: () -> Unit,
    onSaveNote: (TravelMapIncident, String) -> Unit,
    onDelete: (TravelMapIncident) -> Unit,
    onOpenFullEditor: () -> Unit,
) {
    if (incident == null) return
    val live = mapIncidents.find { it.id == incident.id }
    LaunchedEffect(incident.id, mapIncidents) {
        if (mapIncidents.none { it.id == incident.id }) {
            onDismiss()
        }
    }
    if (live == null) return

    ModalBottomSheet(onDismissRequest = onDismiss) {
        IncidentMarkerTapSheetBody(
            incident = live,
            markerDefaultSoundUri = markerDefaultSoundUri,
            onSaveNote = onSaveNote,
            onDelete = onDelete,
            onOpenFullEditor = onOpenFullEditor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun IncidentMarkerTapSheetBody(
    incident: TravelMapIncident,
    markerDefaultSoundUri: String?,
    onSaveNote: (TravelMapIncident, String) -> Unit,
    onDelete: (TravelMapIncident) -> Unit,
    onOpenFullEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var noteDraft by remember(incident.id) { mutableStateOf(incident.note) }
    LaunchedEffect(incident.id, incident.note) {
        noteDraft = incident.note
    }

    val effectiveSoundUri = incident.soundUri ?: markerDefaultSoundUri
    val titleForSound = incident.note.trim().ifBlank {
        context.getString(R.string.travel_incidents_header)
    }
    val dateStr = remember(incident.createdAt) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
            .format(incident.createdAt)
    }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            incident.note.trim().ifBlank { stringResource(R.string.travel_incident_sheet_untitled) },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(
                R.string.travel_incident_sheet_coords,
                incident.latitude,
                incident.longitude,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.travel_incident_sheet_created_fmt, dateStr),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        Text(
            stringResource(R.string.travel_incident_sheet_note_section),
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(
            value = noteDraft,
            onValueChange = { noteDraft = it },
            label = { Text(stringResource(R.string.travel_incident_note_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 5,
        )
        TextButton(
            onClick = { onSaveNote(incident, noteDraft.trim()) },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.travel_incident_sheet_save_note))
        }

        if (incident.photoUris.isNotEmpty()) {
            Text(
                stringResource(R.string.travel_marker_photos_label),
                style = MaterialTheme.typography.titleSmall,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                itemsIndexed(
                    incident.photoUris,
                    key = { ix, u -> "${incident.id}_$ix$u" },
                ) { _, uriStr ->
                    AsyncImage(
                        model = Uri.parse(uriStr),
                        contentDescription = null,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        Text(
            stringResource(R.string.travel_incident_sheet_sound_section),
            style = MaterialTheme.typography.titleSmall,
        )
        if (incident.soundUri.isNullOrBlank()) {
            Text(
                stringResource(R.string.travel_incident_sheet_sound_own_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.travel_incident_sheet_sound_own_set),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!markerDefaultSoundUri.isNullOrBlank() && incident.soundUri.isNullOrBlank()) {
            Text(
                stringResource(R.string.travel_incident_sheet_sound_fallback_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(
            onClick = {
                val uri = effectiveSoundUri ?: return@FilledTonalButton
                TravelMediaService.startSound(
                    context.applicationContext,
                    Uri.parse(uri),
                    titleForSound,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !effectiveSoundUri.isNullOrBlank(),
        ) {
            Text(stringResource(R.string.travel_incident_sheet_play_sound))
        }

        TextButton(
            onClick = onOpenFullEditor,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.travel_incident_sheet_open_editor))
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        TextButton(
            onClick = { onDelete(incident) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.travel_incident_sheet_delete_marker),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
