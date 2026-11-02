package com.example.bible.ui.housecare

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.bible.data.housecare.CleaningReport
import com.example.bible.data.housecare.HouseCarePhotoStorage
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
    var viewingReport by remember { mutableStateOf<CleaningReport?>(null) }
    var viewingInitialTab by remember { mutableIntStateOf(0) }

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
                    zone?.let { zoneDateLabel(it) },
                ).joinToString(" · ")
                Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (report == null) {
                item {
                    GroupCard(container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)) {
                        Text(
                            "Начните уборку: сделайте фото «до», по окончании — фото «после». " +
                                "Точное время между снимками автоматически попадёт в статистику недели.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }

            if (report != null && report.beforePhoto != null && report.afterPhoto != null) {
                item {
                    BeforeAfterComparisonCard(
                        report = report,
                        onOpenFull = { tab ->
                            viewingInitialTab = tab
                            viewingReport = report
                        },
                    )
                }
            }

            item {
                PhotoSlot(
                    label = "Фото до",
                    kind = HouseCarePhotoStorage.KIND_BEFORE,
                    photo = report?.beforePhoto,
                    timeLabel = report?.beforeAtMs?.let(::formatTimeMs),
                    onPreview = {
                        if (report != null) {
                            viewingInitialTab = 1
                            viewingReport = report
                        }
                    },
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
                        "Уборка идёт — снимите результат, чтобы зафиксировать время и поставить галочку"
                    } else {
                        null
                    },
                    onPreview = {
                        if (report != null) {
                            viewingInitialTab = 2
                            viewingReport = report
                        }
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
                                "Новое фото «до» начнёт следующий цикл, а этот отчёт сохранится в истории.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cleaningGreen,
                        )
                    }
                }
            }

            if (task.history.isNotEmpty()) {
                item { HouseCareSectionTitle("История уборок (${task.history.size})") }
                items(task.history, key = { it.id }) { old ->
                    HistoryRow(
                        report = old,
                        onOpenPhotos = {
                            viewingInitialTab = 0
                            viewingReport = old
                        },
                    )
                }
            }
        }
    }

    viewingReport?.let { target ->
        ReportPhotosDialog(
            report = target,
            title = listOfNotNull(zone?.name, task?.name).joinToString(" · "),
            initialTab = viewingInitialTab,
            onDismiss = { viewingReport = null },
        )
    }
}

/** Наглядная карточка сравнения снимков «До» и «После» рядом друг с другом. */
@Composable
private fun BeforeAfterComparisonCard(
    report: CleaningReport,
    onOpenFull: (initialTab: Int) -> Unit,
) {
    val context = LocalContext.current
    val beforeFile = HouseCarePhotoStorage.resolve(context, report.beforePhoto)
    val afterFile = HouseCarePhotoStorage.resolve(context, report.afterPhoto)
    if (beforeFile == null || afterFile == null) return

    GroupCard(container = cleaningGreen.copy(alpha = 0.08f)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Результат «До и После»",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(formatDurationMs(report.durationMs), PillTone.Done)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComparisonThumbBox(
                    file = beforeFile,
                    label = "До" + (report.beforeAtMs?.let { " · ${formatTimeMs(it)}" } ?: ""),
                    isAfter = false,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenFull(0) },
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = cleaningGreen,
                    modifier = Modifier.size(18.dp),
                )
                ComparisonThumbBox(
                    file = afterFile,
                    label = "После" + (report.afterAtMs?.let { " · ${formatTimeMs(it)}" } ?: ""),
                    isAfter = true,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenFull(0) },
                )
            }
            Text(
                "Нажмите на фото, чтобы открыть полноэкранное сравнение с приближением",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ComparisonThumbBox(
    file: File,
    label: String,
    isAfter: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .height(132.dp)
            .clip(shape)
            .border(
                1.dp,
                if (isAfter) cleaningGreen.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = file,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(
                    if (isAfter) cleaningGreen.copy(alpha = 0.88f) else Color.Black.copy(alpha = 0.65f),
                    RoundedCornerShape(50),
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
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
    onPreview: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    GroupCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (timeLabel != null) {
                    StatusPill(timeLabel, PillTone.Muted)
                }
                if (photo != null) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Удалить $label",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
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
                        .size(190.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onPreview),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SlotButton(
                        icon = {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = if (kind == HouseCarePhotoStorage.KIND_BEFORE) "Переснять «до»" else "Переснять «после»",
                        modifier = Modifier.weight(1f),
                        onClick = onCamera,
                    )
                    SlotButton(
                        icon = {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = "Галерея",
                        modifier = Modifier.weight(1f),
                        onClick = onGallery,
                    )
                }
            } else {
                Text(
                    hint ?: "Сделайте снимок или выберите готовый из галереи.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SlotButton(
                        icon = {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = "Камера",
                        modifier = Modifier.weight(1f),
                        onClick = onCamera,
                    )
                    SlotButton(
                        icon = {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = "Из галереи",
                        modifier = Modifier.weight(1f),
                        onClick = onGallery,
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotButton(
    icon: @Composable () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        icon()
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun InfoRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    GroupCard(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
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
    GroupCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Время уборки",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatDurationMs(durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
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
private fun HistoryRow(
    report: CleaningReport,
    onOpenPhotos: () -> Unit,
) {
    val context = LocalContext.current
    val hasAnyPhoto = HouseCarePhotoStorage.resolve(context, report.beforePhoto) != null ||
        HouseCarePhotoStorage.resolve(context, report.afterPhoto) != null
    GroupCard(
        modifier = if (hasAnyPhoto) {
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onOpenPhotos)
        } else {
            Modifier
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReportThumbnailsPair(report = report, onClick = onOpenPhotos)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    formatDateMs(report.dateMs),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    listOfNotNull(
                        report.beforeAtMs?.let { "до ${formatTimeMs(it)}" },
                        report.afterAtMs?.let { "после ${formatTimeMs(it)}" },
                    ).joinToString(" · ").ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(formatDurationMs(report.durationMs), PillTone.Done)
        }
    }
}
