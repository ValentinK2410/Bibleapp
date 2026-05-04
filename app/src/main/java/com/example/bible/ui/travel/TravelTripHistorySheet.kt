package com.example.bible.ui.travel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.travel.TravelGeoPoint
import com.example.bible.data.travel.TravelTripTrackPoint
import com.example.bible.data.travel.tripTrackPathLengthMeters
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun filterTripTrackByLocalWindow(
    points: List<TravelTripTrackPoint>,
    zone: ZoneId,
    date: LocalDate,
    start: LocalTime,
    end: LocalTime,
): List<TravelTripTrackPoint> {
    val z1 = ZonedDateTime.of(date, start, zone)
    val z2 = ZonedDateTime.of(date, end, zone)
    val from = minOf(z1, z2).toInstant().toEpochMilli()
    val to = maxOf(z1, z2).toInstant().toEpochMilli()
    return points.asSequence()
        .filter { it.timestampMs in from..to }
        .sortedBy { it.timestampMs }
        .toList()
}

private fun centerOfTripPoints(pts: List<TravelTripTrackPoint>): TravelGeoPoint? {
    if (pts.isEmpty()) return null
    val lat = pts.sumOf { it.latitude } / pts.size
    val lon = pts.sumOf { it.longitude } / pts.size
    return TravelGeoPoint(lat, lon)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelTripHistorySheet(
    vm: TravelViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val dateFmt = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault())
    }
    val timeFmt = remember {
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    }

    var rawPoints by remember { mutableStateOf<List<TravelTripTrackPoint>>(emptyList()) }
    LaunchedEffect(Unit) {
        rawPoints = vm.tripTrackSnapshot()
    }

    var pickedDate by remember { mutableStateOf(LocalDate.now()) }
    var startTime by remember { mutableStateOf(LocalTime.MIDNIGHT) }
    var endTime by remember { mutableStateOf(LocalTime.of(23, 59)) }

    val filtered = remember(rawPoints, pickedDate, startTime, endTime, zone) {
        filterTripTrackByLocalWindow(rawPoints, zone, pickedDate, startTime, endTime)
    }
    val distanceKm = remember(filtered) { tripTrackPathLengthMeters(filtered) / 1000.0 }
    val maxKmh = remember(filtered) {
        filtered.maxOfOrNull { it.speedMps * 3.6f } ?: 0f
    }

    val tripRecording by vm.tripHistoryEnabled.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = pickedDate.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { ms ->
                            pickedDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.travel_trip_history_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.travel_trip_history_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showStartTimePicker) {
        val st = rememberTimePickerState(
            initialHour = startTime.hour,
            initialMinute = startTime.minute,
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startTime = LocalTime.of(st.hour, st.minute)
                        showStartTimePicker = false
                    },
                ) {
                    Text(stringResource(R.string.travel_trip_history_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) {
                    Text(stringResource(R.string.travel_trip_history_cancel))
                }
            },
            title = { Text(stringResource(R.string.travel_trip_history_interval_start)) },
            text = { TimePicker(state = st) },
        )
    }

    if (showEndTimePicker) {
        val et = rememberTimePickerState(
            initialHour = endTime.hour,
            initialMinute = endTime.minute,
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endTime = LocalTime.of(et.hour, et.minute)
                        showEndTimePicker = false
                    },
                ) {
                    Text(stringResource(R.string.travel_trip_history_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) {
                    Text(stringResource(R.string.travel_trip_history_cancel))
                }
            },
            title = { Text(stringResource(R.string.travel_trip_history_interval_end)) },
            text = { TimePicker(state = et) },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearTripTrackHistory()
                        rawPoints = emptyList()
                        vm.setTripHistoryOverlay(null)
                        showClearConfirm = false
                    },
                ) {
                    Text(stringResource(R.string.travel_trip_history_clear_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.travel_trip_history_cancel))
                }
            },
            title = { Text(stringResource(R.string.travel_trip_history_clear_title)) },
            text = { Text(stringResource(R.string.travel_trip_history_clear_body)) },
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.travel_trip_history_title),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(
                onClick = {
                    scope.launch {
                        rawPoints = vm.tripTrackSnapshot()
                    }
                },
            ) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.travel_trip_history_reload_cd))
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.travel_trip_history_recording_switch),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = tripRecording,
                onCheckedChange = { vm.setTripHistoryRecordingEnabled(it) },
            )
        }
        Text(
            stringResource(R.string.travel_trip_history_recording_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        Text(stringResource(R.string.travel_trip_history_pick_day_time), style = MaterialTheme.typography.titleSmall)

        OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.travel_trip_history_day_fmt, pickedDate.format(dateFmt)))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { showStartTimePicker = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.travel_trip_history_from_fmt, startTime.format(timeFmt)))
            }
            OutlinedButton(
                onClick = { showEndTimePicker = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.travel_trip_history_until_fmt, endTime.format(timeFmt)))
            }
        }

        Text(
            stringResource(
                R.string.travel_trip_history_stats_fmt,
                filtered.size,
                distanceKm,
                maxKmh,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(
            onClick = {
                if (filtered.size < 2) return@Button
                val poly = filtered.map { TravelGeoPoint(it.latitude, it.longitude) }
                vm.setTripHistoryOverlay(poly)
                centerOfTripPoints(filtered)?.let { vm.setCameraJump(it) }
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = filtered.size >= 2,
        ) {
            Text(stringResource(R.string.travel_trip_history_show_track))
        }
        OutlinedButton(
            onClick = {
                vm.setTripHistoryOverlay(null)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.travel_trip_history_hide_track))
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = { showClearConfirm = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.travel_trip_history_clear_all))
        }

        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.travel_trip_history_close))
        }

        Spacer(Modifier.height(16.dp))
    }
}
