package com.example.bible.ui.housecare

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.bible.data.housecare.CleaningReport
import com.example.bible.data.housecare.HouseCarePhotoStorage
import com.example.bible.data.housecare.WeekDays
import com.example.bible.ui.theme.OutlinedTextField
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Раздел зоны уборки: фото «до» и «после», дата и время уборки.
 * Есть фото «после» — раздел убран, а разница времени между снимками идёт в статистику.
 */
@Composable
fun CleaningTaskScreen(
    viewModel: HouseCareViewModel,
    taskId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by viewModel.data.collectAsStateWithLifecycle()
    val task = data.taskById(taskId)
    val zone = task?.let { data.zoneById(it.zoneId) }
    val report = task?.active

    var pendingKind by remember { mutableStateOf<String?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    fun applyPhoto(kind: String, fileName: String) {
        when (kind) {
            HouseCarePhotoStorage.KIND_BEFORE -> viewModel.setBeforePhoto(taskId, fileName)
            else -> viewModel.setAfterPhoto(taskId, fileName)
        }
    }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val kind = pendingKind
        val file = pendingFile
        pendingKind = null
        pendingFile = null
        if (file == null || kind == null) return@rememberLauncherForActivityResult
        if (success && file.exists()) applyPhoto(kind, file.name) else file.delete()
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val kind = pendingKind
        pendingKind = null
        if (uri == null || kind == null) return@rememberLauncherForActivityResult
        val dest = viewModel.createPhotoFile(kind)
        scope.launch {
            val copied = withContext(Dispatchers.IO) {
                HouseCarePhotoStorage.copyUriToJpegFile(context, uri, dest)
            }
            if (copied) applyPhoto(kind, dest.name)
        }
    }

    fun requestCamera(kind: String) {
        val file = viewModel.createPhotoFile(kind)
        pendingFile = file
        pendingKind = kind
        takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.provider", file))
    }

    fun requestGallery(kind: String) {
        pendingKind = kind
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    HouseCareScaffold(title = task?.name ?: "Раздел", onBack = onBack) { padding ->
        if (task == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Раздел удалён.", style = MaterialTheme.typography.bodyMedium)
            }
            return@HouseCareScaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val floorName = zone?.let { data.floorById(it.floorId)?.name }.orEmpty()
                val path = listOfNotNull(
                    floorName.takeIf { it.isNotBlank() },
                    zone?.name,
                    zone?.let { WeekDays.fullLabel(it.dayOfWeek) },
                ).joinToString(" · ")
                Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (report == null) {
                item {
                    Text(
                        "Начните уборку: сделайте фото «до», по окончании — фото «после». " +
                            "Время между снимками попадёт в статистику.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                PhotoSlot(
                    label = "Фото до",
                    kind = HouseCarePhotoStorage.KIND_BEFORE,
                    photo = report?.beforePhoto,
                    timeLabel = report?.beforeAtMs?.let(::formatTimeMs),
                    onCamera = { requestCamera(HouseCarePhotoStorage.KIND_BEFORE) },
                    onGallery = { requestGallery(HouseCarePhotoStorage.KIND_BEFORE) },
                    onRemove = { viewModel.removePhoto(taskId, HouseCarePhotoStorage.KIND_BEFORE) },
                )
            }

            item {
                PhotoSlot(
                    label = "Фото после",
                    kind = HouseCarePhotoStorage.KIND_AFTER,
                    photo = report?.afterPhoto,
                    timeLabel = report?.afterAtMs?.let(::formatTimeMs),
                    hint = if (report != null && report.beforePhoto != null && !report.isFinished) {
                        "Уборка идёт — снимите результат, чтобы поставить галочку"
                    } else {
                        null
                    },
                    onCamera = { requestCamera(HouseCarePhotoStorage.KIND_AFTER) },
                    onGallery = { requestGallery(HouseCarePhotoStorage.KIND_AFTER) },
                    onRemove = { viewModel.removePhoto(taskId, HouseCarePhotoStorage.KIND_AFTER) },
                )
            }

            if (report != null) {
                item {
                    InfoRow(
                        title = "Дата уборки",
                        value = formatDateMs(report.dateMs),
                        onClick = {
                            val day = Instant.ofEpochMilli(report.dateMs).atZone(ZoneId.systemDefault()).toLocalDate()
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val picked = java.time.LocalDate.of(year, month + 1, dayOfMonth)
                                        .atStartOfDay(ZoneId.systemDefault())
                                        .toInstant()
                                        .toEpochMilli()
                                    viewModel.setReportDate(taskId, picked)
                                },
                                day.year,
                                day.monthValue - 1,
                                day.dayOfMonth,
                            ).show()
                        },
                    )
                }
                item {
                    if (!report.isFinished && report.beforeAtMs != null) {
                        RunningDurationRow(startedAtMs = report.beforeAtMs)
                    } else {
                        DurationEditorRow(
                            durationMs = report.durationMs,
                            isManual = report.durationMsOverride != null,
                            onMinutes = { minutes -> viewModel.setDurationMinutes(taskId, minutes) },
                        )
                    }
                }
                if (report.isFinished) {
                    item {
                        Text(
                            "Раздел убран ${formatDateMs(report.dateMs)} · ${formatDurationMs(report.durationMs)}. " +
                                "Новое фото «до» начнёт следующий цикл.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cleaningGreen,
                        )
                    }
                }
            }

            if (task.history.isNotEmpty()) {
                item { HouseCareSectionTitle("История уборок") }
                items(task.history, key = { it.id }) { old -> HistoryRow(old) }
            }
        }
    }
}

@Composable
private fun PhotoSlot(
    label: String,
    kind: String,
    photo: String?,
    timeLabel: String?,
    hint: String? = null,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (timeLabel != null) {
                    Text(timeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (photo != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Удалить $label")
                    }
                }
            }
            val file = HouseCarePhotoStorage.resolve(context, photo)
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(180.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                TextButton(onClick = onCamera, modifier = Modifier.fillMaxWidth()) {
                    Text(if (kind == HouseCarePhotoStorage.KIND_BEFORE) "Переснять «до»" else "Переснять «после»")
                }
            } else {
                Text(
                    hint ?: "Сделайте снимок или выберите готовый из галереи.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SlotButton(icon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) }, text = "Камера", onClick = onCamera)
                    SlotButton(icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) }, text = "Из галереи", onClick = onGallery)
                }
            }
        }
    }
}

@Composable
private fun SlotButton(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun InfoRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Пока фото «после» нет, показываем сколько уже прошло с момента фото «до». */
@Composable
private fun RunningDurationRow(startedAtMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    InfoRow(title = "Время уборки", value = formatDurationMs(now - startedAtMs), onClick = {})
}

@Composable
private fun DurationEditorRow(
    durationMs: Long,
    isManual: Boolean,
    onMinutes: (Int?) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf((durationMs / 60_000L).coerceAtLeast(0).toString()) }
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Время уборки", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(formatDurationMs(durationMs), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = {
                    draft = (durationMs / 60_000L).coerceAtLeast(0).toString()
                    editing = !editing
                }) { Text(if (editing) "Отмена" else "Изменить") }
            }
            Text(
                if (isManual) "Время указано вручную." else "Посчитано между фото «до» и «после».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (editing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.filter(Char::isDigit).take(4) },
                        label = { Text("Минуты") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        onMinutes(draft.toIntOrNull())
                        editing = false
                    }) { Text("Ок") }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(report: CleaningReport) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(formatDateMs(report.dateMs), style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(
                        report.beforeAtMs?.let { "до ${formatTimeMs(it)}" },
                        report.afterAtMs?.let { "после ${formatTimeMs(it)}" },
                    ).joinToString(" · ").ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatDurationMs(report.durationMs), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
