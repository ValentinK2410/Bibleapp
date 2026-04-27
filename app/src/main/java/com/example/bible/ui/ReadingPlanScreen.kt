package com.example.bible.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.ReadingPlan
import com.example.bible.data.ReadingPlanProgress
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Suppress("DEPRECATION")
private val RU_LOCALE = java.util.Locale("ru")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingPlanScreen(
    onBack: () -> Unit,
    onOpenPassage: (bookId: String, chapter: Int) -> Unit = { _, _ -> },
    completedDates: Set<String> = emptySet(),
    onMarkDayCompleted: (LocalDate, Boolean) -> Unit = { _, _ -> },
    reminderTime: Pair<Int, Int>? = null,
    onReminderChange: (Int, Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    LaunchedEffect(reminderTime) {
        if (reminderTime == null) {
            ReadingPlanScheduler.cancel(context)
        } else {
            ReadingPlanScheduler.update(context, reminderTime.first, reminderTime.second)
        }
    }

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showReminderPicker by remember { mutableStateOf(false) }
    val reading = remember(selectedDate) { ReadingPlan.forDate(selectedDate) }
    val progress = remember(selectedDate) { ReadingPlan.progressPercent(selectedDate) }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("d MMMM, EEEE", RU_LOCALE)
    }
    val iso = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    val today = LocalDate.now()
    val selectedKey = remember(selectedDate) { selectedDate.format(iso) }
    val isSelectedDone = selectedKey in completedDates
    val missed = remember(completedDates, today) {
        ReadingPlanProgress.countMissedDays(completedDates, today)
    }
    val firstMissed = remember(completedDates, today) {
        ReadingPlanProgress.firstMissedDate(completedDates, today)
    }

    if (showReminderPicker) {
        ReminderTimeDialog(
            initialHour = reminderTime?.first ?: 8,
            initialMinute = reminderTime?.second ?: 0,
            onDismiss = { showReminderPicker = false },
            onConfirm = { h, m ->
                onReminderChange(h, m)
                showReminderPicker = false
            },
            onDisable = {
                onReminderChange(-1, 0)
                showReminderPicker = false
            },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.reading_plan)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showReminderPicker = true }) {
                        Icon(
                            Icons.Default.Notifications,
                            stringResource(R.string.reading_plan_reminder),
                            tint = if (reminderTime != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.reading_plan_card_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.reading_plan_day_progress, reading.dayOfYear),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )
                    Text(
                        "$progress%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (missed > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.reading_plan_missed, missed),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        if (firstMissed != null) {
                            TextButton(
                                onClick = {
                                    selectedDate = firstMissed
                                },
                            ) {
                                Text(stringResource(R.string.reading_plan_catch_up))
                            }
                        }
                    }
                    reminderTime?.let { (h, m) ->
                        Text(
                            stringResource(R.string.reading_plan_reminder_time, h, m),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onMarkDayCompleted(selectedDate, !isSelectedDone) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (isSelectedDone) {
                        stringResource(R.string.reading_plan_unmark_done)
                    } else {
                        stringResource(R.string.reading_plan_mark_done)
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    selectedDate = selectedDate.minusDays(1)
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Предыдущий день")
                }
                Text(
                    selectedDate.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = {
                    selectedDate = selectedDate.plusDays(1)
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Следующий день")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.reading_plan_today_passages),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            reading.passages.forEach { passage ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onOpenPassage(passage.bookId, passage.chapter) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            passage.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Открыть",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (selectedDate != LocalDate.now()) {
                Text(
                    stringResource(R.string.reading_plan_nav_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReminderTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
    onDisable: () -> Unit,
) {
    var hour by remember { mutableStateOf(initialHour) }
    var minute by remember { mutableStateOf(initialMinute) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reading_plan_reminder)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.reading_plan_reminder_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { hour = (hour + 23) % 24 }) { Text("−") }
                    Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { hour = (hour + 1) % 24 }) { Text("+") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { minute = (minute + 55) % 60 }) { Text("−5") }
                    TextButton(onClick = { minute = (minute + 5) % 60 }) { Text("+5") }
                }
                TextButton(onClick = onDisable) {
                    Text(stringResource(R.string.reading_plan_reminder_off))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour, minute) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
