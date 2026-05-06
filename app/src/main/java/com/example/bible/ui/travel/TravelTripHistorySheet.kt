package com.example.bible.ui.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.travel.TRIP_TRACK_COLOR_SCALE_MAX_KMH
import com.example.bible.data.travel.TRIP_TRACK_COLOR_SCALE_MIN_KMH
import com.example.bible.data.travel.TravelGeoPoint
import com.example.bible.data.travel.TravelTripTrackPoint
import com.example.bible.data.travel.tripTrackPathLengthMeters
import com.example.bible.data.travel.tripTrackSpeedKmhToArgb
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

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

fun tripTrackLocalDatesWithData(points: List<TravelTripTrackPoint>, zone: ZoneId): Set<LocalDate> =
    points.mapTo(HashSet(points.size.coerceAtLeast(16))) {
        Instant.ofEpochMilli(it.timestampMs).atZone(zone).toLocalDate()
    }

fun filterTripTrackForLocalDate(
    points: List<TravelTripTrackPoint>,
    zone: ZoneId,
    date: LocalDate,
): List<TravelTripTrackPoint> =
    points.asSequence()
        .filter { Instant.ofEpochMilli(it.timestampMs).atZone(zone).toLocalDate() == date }
        .sortedBy { it.timestampMs }
        .toList()

private fun carrySelectionToMonth(selected: LocalDate, ym: YearMonth): LocalDate {
    val last = ym.lengthOfMonth()
    val day = selected.dayOfMonth.coerceAtMost(last)
    return ym.atDay(day)
}

private fun mondayFirstOffset(dayOfWeek: DayOfWeek): Int =
    when (dayOfWeek) {
        DayOfWeek.MONDAY -> 0
        DayOfWeek.TUESDAY -> 1
        DayOfWeek.WEDNESDAY -> 2
        DayOfWeek.THURSDAY -> 3
        DayOfWeek.FRIDAY -> 4
        DayOfWeek.SATURDAY -> 5
        DayOfWeek.SUNDAY -> 6
    }

private fun centerOfTripPoints(pts: List<TravelTripTrackPoint>): TravelGeoPoint? {
    if (pts.isEmpty()) return null
    val lat = pts.sumOf { it.latitude } / pts.size
    val lon = pts.sumOf { it.longitude } / pts.size
    return TravelGeoPoint(lat, lon)
}

@Composable
private fun TripHistorySpeedScaleLegend(modifier: Modifier = Modifier) {
    val gradient = remember {
        val steps = 48
        val colors = List(steps + 1) { i ->
            val t = i / steps.toFloat()
            val kmh = TRIP_TRACK_COLOR_SCALE_MIN_KMH +
                (TRIP_TRACK_COLOR_SCALE_MAX_KMH - TRIP_TRACK_COLOR_SCALE_MIN_KMH) * t
            val argb = tripTrackSpeedKmhToArgb(kmh)
            Color(
                red = ((argb shr 16) and 0xFF) / 255f,
                green = ((argb shr 8) and 0xFF) / 255f,
                blue = (argb and 0xFF) / 255f,
                alpha = ((argb ushr 24) and 0xFF) / 255f,
            )
        }
        Brush.horizontalGradient(colors)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(gradient),
    )
}

@Composable
private fun TripHistoryCalendarGrid(
    yearMonth: YearMonth,
    datesWithData: Set<LocalDate>,
    selectedDate: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
) {
    val loc = Locale.getDefault()
    val weekendColor = MaterialTheme.colorScheme.primary
    val normalColor = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (d in 1..7) {
            val dow = DayOfWeek.of(d)
            val label = dow.getDisplayName(TextStyle.SHORT, loc).take(2)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(loc) else it.toString() }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                    weekendColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
        }
    }
    Spacer(Modifier.height(6.dp))

    val firstOfMonth = yearMonth.atDay(1)
    val pad = mondayFirstOffset(firstOfMonth.dayOfWeek)
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = ((pad + daysInMonth + 6) / 7) * 7

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until totalCells / 7) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (col in 0..6) {
                    val idx = row * 7 + col
                    val dayNum = idx - pad + 1
                    val isSelected =
                        dayNum in 1..daysInMonth &&
                            LocalDate.of(yearMonth.year, yearMonth.month, dayNum) == selectedDate
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                } else {
                                    Color.Transparent
                                },
                                shape = CircleShape,
                            )
                            .clip(CircleShape)
                            .clickable(enabled = dayNum in 1..daysInMonth) {
                                onSelectDay(yearMonth.atDay(dayNum))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayNum in 1..daysInMonth) {
                            val cellDate = LocalDate.of(yearMonth.year, yearMonth.month, dayNum)
                            val isWeekend =
                                cellDate.dayOfWeek == DayOfWeek.SATURDAY ||
                                    cellDate.dayOfWeek == DayOfWeek.SUNDAY
                            val hasTripHistory = cellDate in datesWithData
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$dayNum",
                                    color = if (isWeekend) weekendColor else normalColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                )
                                // Полоска под числом: день с записанным перемещением (история трека).
                                if (hasTripHistory) {
                                    val stripeColor =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                        }
                                    Box(
                                        Modifier
                                            .padding(top = 4.dp)
                                            .width(20.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(stripeColor),
                                    )
                                } else {
                                    // Резерв высоты, чтобы ячейки с данными и без не «прыгали» по вертикали.
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelTripHistorySheet(
    vm: TravelViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val loc = Locale.getDefault()
    val selectedHeaderFmt = remember {
        DateTimeFormatter.ofPattern("EEE, d MMMM", loc)
    }
    val timeFmt = remember {
        DateTimeFormatter.ofPattern("HH:mm", loc)
    }

    var rawPoints by remember { mutableStateOf<List<TravelTripTrackPoint>>(emptyList()) }
    LaunchedEffect(Unit) {
        rawPoints = vm.tripTrackSnapshot()
    }

    val datesWithData = remember(rawPoints, zone) {
        tripTrackLocalDatesWithData(rawPoints, zone)
    }

    var visibleMonth by remember { mutableStateOf(YearMonth.from(LocalDate.now())) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    var refineTime by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf(LocalTime.MIDNIGHT) }
    var endTime by remember { mutableStateOf(LocalTime.of(23, 59)) }

    val dayPointsFull = remember(rawPoints, selectedDate, zone) {
        filterTripTrackForLocalDate(rawPoints, zone, selectedDate)
    }

    val filtered = remember(dayPointsFull, refineTime, selectedDate, startTime, endTime, zone, rawPoints) {
        if (!refineTime) dayPointsFull
        else filterTripTrackByLocalWindow(rawPoints, zone, selectedDate, startTime, endTime)
    }

    val distanceKm = remember(filtered) { tripTrackPathLengthMeters(filtered) / 1000.0 }
    val maxKmh = remember(filtered) {
        filtered.maxOfOrNull { it.speedMps * 3.6f } ?: 0f
    }
    val timeSpanText = remember(filtered, timeFmt) {
        if (filtered.size < 2) {
            filtered.firstOrNull()?.let { p ->
                val t = Instant.ofEpochMilli(p.timestampMs).atZone(zone).toLocalTime()
                timeFmt.format(t)
            }
        } else {
            val a = Instant.ofEpochMilli(filtered.first().timestampMs).atZone(zone).toLocalTime()
            val b = Instant.ofEpochMilli(filtered.last().timestampMs).atZone(zone).toLocalTime()
            "${timeFmt.format(a)} — ${timeFmt.format(b)}"
        }
    }

    val tripRecording by vm.tripHistoryEnabled.collectAsStateWithLifecycle()
    val replayMult by vm.tripHistoryReplaySpeedMultiplier.collectAsStateWithLifecycle()

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
            lineHeight = 18.sp,
        )

        HorizontalDivider()

        Text(
            stringResource(R.string.travel_trip_history_calendar_section),
            style = MaterialTheme.typography.titleSmall,
        )

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = {
                    visibleMonth = visibleMonth.minusMonths(1)
                    selectedDate = carrySelectionToMonth(selectedDate, visibleMonth)
                },
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.travel_trip_history_prev_month_cd))
            }
            Text(
                buildString {
                    append(
                        visibleMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, loc)
                            .replaceFirstChar { ch ->
                                if (ch.isLowerCase()) ch.titlecase(loc) else ch.toString()
                            },
                    )
                    append(" ")
                    append(visibleMonth.year)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(
                onClick = {
                    visibleMonth = visibleMonth.plusMonths(1)
                    selectedDate = carrySelectionToMonth(selectedDate, visibleMonth)
                },
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.travel_trip_history_next_month_cd))
            }
        }

        TripHistoryCalendarGrid(
            yearMonth = visibleMonth,
            datesWithData = datesWithData,
            selectedDate = selectedDate,
            onSelectDay = { selectedDate = it },
        )

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        val today = LocalDate.now()
        val headerLine = buildString {
            append(selectedDate.format(selectedHeaderFmt).replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase(loc) else c.toString()
            })
            if (selectedDate == today) {
                append(" · ")
                append(stringResource(R.string.travel_trip_history_today))
            }
        }
        Text(headerLine, style = MaterialTheme.typography.titleSmall)

        if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.travel_trip_history_no_data_day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            timeSpanText?.let { span ->
                Text(
                    stringResource(R.string.travel_trip_history_time_on_day_fmt, span),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.travel_trip_history_speed_legend_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.travel_trip_history_speed_legend_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
            TripHistorySpeedScaleLegend(Modifier.padding(vertical = 8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${TRIP_TRACK_COLOR_SCALE_MIN_KMH.toInt()} км/ч — медленнее",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${TRIP_TRACK_COLOR_SCALE_MAX_KMH.toInt()} км/ч — быстрее",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.travel_trip_history_replay_speed_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.travel_trip_history_replay_speed_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
            Slider(
                value = replayMult,
                onValueChange = { vm.setTripHistoryReplaySpeedMultiplier(it) },
                valueRange = 1f..120f,
                steps = 23,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.travel_trip_history_replay_mult_fmt, replayMult.roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    if (filtered.size < 2) return@Button
                    vm.startTripHistoryReplay(filtered)
                    centerOfTripPoints(filtered)?.let { vm.setCameraJump(it) }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = filtered.size >= 2,
            ) {
                Text(stringResource(R.string.travel_trip_history_replay_start))
            }
            OutlinedButton(
                onClick = { vm.stopTripHistoryReplay() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.travel_trip_history_replay_stop))
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.travel_trip_history_refine_time),
                style = MaterialTheme.typography.labelLarge,
            )
            Switch(checked = refineTime, onCheckedChange = { refineTime = it })
        }
        if (refineTime) {
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
        }

        Button(
            onClick = {
                if (filtered.size < 2) return@Button
                vm.setTripHistoryOverlay(filtered)
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
                if (filtered.size < 2) return@OutlinedButton
                vm.startTripTrackIntervalErase(filtered)
                centerOfTripPoints(filtered)?.let { vm.setCameraJump(it) }
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = filtered.size >= 2,
        ) {
            Text(stringResource(R.string.travel_trip_erase_start))
        }
        Text(
            stringResource(R.string.travel_trip_erase_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                vm.setTripHistoryOverlay(null)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.travel_trip_history_hide_track))
        }

        Spacer(Modifier.height(4.dp))

        TextButton(
            onClick = { showClearConfirm = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.travel_trip_history_clear_all))
        }

        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.travel_trip_history_close))
        }

        Spacer(Modifier.height(8.dp))
    }
}
