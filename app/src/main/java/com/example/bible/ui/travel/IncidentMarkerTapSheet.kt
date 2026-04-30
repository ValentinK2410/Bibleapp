package com.example.bible.ui.travel

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.bible.R
import com.example.bible.data.TranslationId
import com.example.bible.data.travel.TravelMapIncident
import com.example.bible.service.TravelMediaService
import com.example.bible.ui.rememberStudyTextToSpeech
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Locale

/**
 * Нижний лист при тапе по метке: превью фото, автоматически звук или озвучка текста (TTS),
 * сбоку кнопки удалить и редактировать.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentMarkerTapSheet(
    incident: TravelMapIncident?,
    mapIncidents: List<TravelMapIncident>,
    onDismiss: () -> Unit,
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
    onDelete: (TravelMapIncident) -> Unit,
    onOpenFullEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val voice = rememberStudyTextToSpeech(TranslationId.SYNODAL)

    DisposableEffect(Unit) {
        onDispose {
            voice.stop()
        }
    }

    LaunchedEffect(incident.id, incident.soundUri, incident.note) {
        voice.stop()
        delay(340)
        val ownSound = incident.soundUri?.trim()?.takeIf { it.isNotEmpty() }
        val text = incident.note.trim()
        when {
            ownSound != null -> {
                TravelMediaService.startSound(
                    context.applicationContext,
                    Uri.parse(ownSound),
                    text.ifBlank { context.getString(R.string.travel_incidents_header) },
                )
            }
            text.isNotEmpty() -> voice.speak(text)
            else -> { /* только фото или пустая метка — без озвучки */ }
        }
    }

    val dateStr = remember(incident.createdAt) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
            .format(incident.createdAt)
    }

    val photos = incident.photoUris
    var previewIndex by remember(incident.id) { mutableIntStateOf(0) }

    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.travel_incident_sheet_auto_listen_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (photos.isNotEmpty()) {
                val idx = previewIndex.coerceIn(0, photos.lastIndex)
                AsyncImage(
                    model = Uri.parse(photos[idx]),
                    contentDescription = stringResource(R.string.travel_incident_sheet_preview_cd),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                )
                if (photos.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        itemsIndexed(
                            photos,
                            key = { ix, u -> "${incident.id}_thumb_$ix$u" },
                        ) { ix, uriStr ->
                            val selected = ix == idx
                            AsyncImage(
                                model = Uri.parse(uriStr),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .clickable { previewIndex = ix },
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }

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

            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            if (!incident.soundUri.isNullOrBlank()) {
                Text(
                    stringResource(R.string.travel_incident_sheet_sound_own_set),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (incident.note.trim().isNotEmpty()) {
                Text(
                    stringResource(R.string.travel_incident_sheet_tts_used_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
        }

        Column(
            Modifier
                .padding(top = 24.dp)
                .width(56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val deleteCd = stringResource(R.string.travel_incident_sheet_cd_delete)
            FilledTonalIconButton(
                onClick = { onDelete(incident) },
                modifier = Modifier
                    .size(52.dp)
                    .semantics { contentDescription = deleteCd },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
            Text(
                stringResource(R.string.travel_incident_sheet_side_delete),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )

            val editCd = stringResource(R.string.travel_incident_sheet_cd_edit)
            FilledTonalIconButton(
                onClick = onOpenFullEditor,
                modifier = Modifier
                    .size(52.dp)
                    .semantics { contentDescription = editCd },
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
            }
            Text(
                stringResource(R.string.travel_incident_sheet_side_edit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
