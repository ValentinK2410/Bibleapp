package com.example.bible.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.KidsUserMediaStorage
import com.example.bible.data.KidsUserStoriesStore
import com.example.bible.data.KidsUserStory
import com.example.bible.data.KidsUserStoryBlock
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private fun findOrphanImageIndices(blocks: List<KidsUserStoryBlock>): List<Int> {
    val result = mutableListOf<Int>()
    blocks.forEachIndexed { i, block ->
        if (!block.isImage) return@forEachIndexed
        val next = blocks.getOrNull(i + 1)
        if (next == null || next.isImage) result.add(i)
    }
    return result
}

@Composable
fun KidsUserStoryEditorScreen(
    storyId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = storyId?.let { KidsUserStoriesStore.find(context, it) }
    val isNew = storyId == "new" || existing == null
    val id = storyId.takeUnless { it == "new" } ?: UUID.randomUUID().toString()

    var title by remember { mutableStateOf(existing?.title ?: "Новая история") }
    var blocks by remember { mutableStateOf(existing?.blocks?.toList() ?: emptyList()) }
    var imageDurations by remember { mutableStateOf(existing?.imageDurations ?: emptyMap()) }

    var confirmDelete by remember { mutableStateOf(false) }
    var askDurations by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<Pair<String, List<KidsUserStoryBlock>>?>(null) }

    // Индекс, по которому нужно вставить картинку
    var imageInsertIndex by remember { mutableStateOf<Int?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val index = imageInsertIndex
        imageInsertIndex = null
        if (uri == null || index == null) return@rememberLauncherForActivityResult

        val path = KidsUserMediaStorage.copyUriIntoFilesDir(
            context, uri, KidsUserMediaStorage.IMAGES_SUBDIR, "jpg"
        )
        if (path == null) {
            Toast.makeText(context, "Не удалось сохранить изображение", Toast.LENGTH_SHORT).show()
        } else {
            val newBlock = KidsUserStoryBlock(KidsUserStoryBlock.IMAGE, image = path)
            blocks = blocks.toMutableList().apply { add(index, newBlock) }
            // Сдвигаем сохраненные длительности для блоков, которые оказались ниже
            imageDurations = imageDurations.mapKeys { (k, _) -> if (k >= index) k + 1 else k }
        }
    }

    fun insertText(index: Int) {
        val newBlock = KidsUserStoryBlock(KidsUserStoryBlock.TEXT, text = "")
        blocks = blocks.toMutableList().apply { add(index, newBlock) }
        imageDurations = imageDurations.mapKeys { (k, _) -> if (k >= index) k + 1 else k }
    }

    fun updateTextBlock(index: Int, text: String) {
        if (index in blocks.indices && !blocks[index].isImage) {
            blocks = blocks.toMutableList().apply { set(index, blocks[index].copy(text = text)) }
        }
    }

    fun moveBlock(fromIndex: Int, toIndex: Int) {
        if (fromIndex in blocks.indices && toIndex in blocks.indices) {
            blocks = blocks.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            // Корректно обновляем ключи длительностей при перемещении
            val newDurations = mutableMapOf<Int, Long>()
            imageDurations.forEach { (k, v) ->
                val newK = if (fromIndex < toIndex) {
                    when {
                        k == fromIndex -> toIndex
                        k in (fromIndex + 1)..toIndex -> k - 1
                        else -> k
                    }
                } else {
                    when {
                        k == fromIndex -> toIndex
                        k in toIndex until fromIndex -> k + 1
                        else -> k
                    }
                }
                newDurations[newK] = v
            }
            imageDurations = newDurations
        }
    }

    fun removeBlock(index: Int) {
        val block = blocks.getOrNull(index) ?: return
        if (block.isImage && block.image.isNotBlank()) File(context.filesDir, block.image).delete()
        blocks = blocks.toMutableList().apply { removeAt(index) }
        // Удаляем длительность и сдвигаем оставшиеся ключи
        imageDurations = imageDurations
            .filterKeys { it != index }
            .mapKeys { (k, _) -> if (k > index) k - 1 else k }
    }

    fun performSave(cleanTitle: String, cleanBlocks: List<KidsUserStoryBlock>, durations: Map<Int, Long>) {
        scope.launch {
            KidsUserStoriesStore.save(
                context,
                KidsUserStory(id = id, title = cleanTitle, blocks = cleanBlocks, imageDurations = durations),
            )
            Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
            onSaved(id)
        }
    }

    KidsSectionScaffold(
        title = if (isNew) "Новая история" else "Редактирование",
        onBack = onBack,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Название истории") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Text(
                "Блоки истории",
                fontWeight = MaterialTheme.typography.titleMedium.fontWeight,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Нажимайте на плюсики между блоками, чтобы добавить текст или картинку в нужное место.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            )

            // Интерактивный список с точками вставки
            blocks.forEachIndexed { index, block ->
                InsertionPoint(
                    onInsertText = { insertText(index) },
                    onInsertImage = { imageInsertIndex = index; imagePicker.launch("image/*") }
                )

                BlockItem(
                    block = block, index = index, context = context, durationMs = imageDurations[index],
                    onTextChange = { updateTextBlock(index, it) },
                    onMoveUp = { if (index > 0) moveBlock(index, index - 1) },
                    onMoveDown = { if (index < blocks.size - 1) moveBlock(index, index + 1) },
                    onRemove = { removeBlock(index) },
                )
            }

            // Точка вставки в самом конце
            InsertionPoint(
                onInsertText = { insertText(blocks.size) },
                onInsertImage = { imageInsertIndex = blocks.size; imagePicker.launch("image/*") }
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val cleanTitle = title.trim()
                    if (cleanTitle.isBlank()) { Toast.makeText(context, "Нужно название", Toast.LENGTH_SHORT).show(); return@Button }

                    val cleanBlocks = blocks.mapNotNull { block ->
                        when {
                            block.isImage && block.image.isNotBlank() && File(context.filesDir, block.image).isFile ->
                                KidsUserStoryBlock(KidsUserStoryBlock.IMAGE, image = block.image)
                            !block.isImage && block.text.isNotBlank() ->
                                KidsUserStoryBlock(KidsUserStoryBlock.TEXT, text = block.text.trim())
                            else -> null
                        }
                    }
                    if (cleanBlocks.isEmpty()) { Toast.makeText(context, "Добавьте хотя бы один блок", Toast.LENGTH_SHORT).show(); return@Button }

                    val orphans = findOrphanImageIndices(cleanBlocks)
                    if (orphans.isEmpty()) {
                        val durations = existing?.imageDurations?.filterKeys { it in cleanBlocks.indices && cleanBlocks[it].isImage } ?: emptyMap()
                        performSave(cleanTitle, cleanBlocks, durations)
                    } else {
                        imageDurations = orphans.associateWith { idx -> imageDurations[idx] ?: KidsUserStory.DEFAULT_IMAGE_DURATION_MS }
                        pendingSave = cleanTitle to cleanBlocks
                        askDurations = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }

            if (!isNew) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Удалить историю", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (askDurations && pendingSave != null) {
        DurationDialog(
            blocks = pendingSave!!.second, durations = imageDurations,
            onDurationsChange = { imageDurations = it },
            onDismiss = { askDurations = false; pendingSave = null },
            onConfirm = {
                askDurations = false
                val (cleanTitle, cleanBlocks) = pendingSave!!
                pendingSave = null
                performSave(cleanTitle, cleanBlocks, imageDurations)
            },
        )
    }

    if (confirmDelete && storyId != "new") {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить историю?", fontWeight = MaterialTheme.typography.titleMedium.fontWeight) },
            text = { Text("\"$title\" будет удалена безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { KidsUserStoriesStore.delete(context, id); onBack() }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun InsertionPoint(
    onInsertText: () -> Unit,
    onInsertImage: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable { expanded = true },
        contentAlignment = Alignment.Center
    ) {
        // Тонкая линия на фоне
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp
        )

        // Кнопка с плюсом
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(24.dp),
            shadowElevation = 2.dp
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Добавить блок",
                modifier = Modifier.padding(4.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // Меню выбора действия
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Добавить текст") },
                onClick = { expanded = false; onInsertText() }
            )
            DropdownMenuItem(
                text = { Text("Добавить картинку") },
                onClick = { expanded = false; onInsertImage() }
            )
        }
    }
}

@Composable
private fun DurationDialog(
    blocks: List<KidsUserStoryBlock>, durations: Map<Int, Long>,
    onDurationsChange: (Map<Int, Long>) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit,
) {
    val orphans = remember(blocks) { findOrphanImageIndices(blocks) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Длительность показа картинок") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Для картинок без текста/звука задайте время показа в миллисекундах " +
                            "(${KidsUserStory.MIN_IMAGE_DURATION_MS} … ${KidsUserStory.MAX_IMAGE_DURATION_MS}). " +
                            "По умолчанию — ${KidsUserStory.DEFAULT_IMAGE_DURATION_MS} мс.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                orphans.forEach { idx ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("Блок ${idx + 1}:", modifier = Modifier.width(90.dp))
                        OutlinedTextField(
                            value = (durations[idx] ?: KidsUserStory.DEFAULT_IMAGE_DURATION_MS).toString(),
                            onValueChange = { v ->
                                val ms = v.toLongOrNull()?.coerceIn(KidsUserStory.MIN_IMAGE_DURATION_MS, KidsUserStory.MAX_IMAGE_DURATION_MS) ?: KidsUserStory.DEFAULT_IMAGE_DURATION_MS
                                onDurationsChange(durations + (idx to ms))
                            },
                            singleLine = true, modifier = Modifier.weight(1f), suffix = { Text("мс") },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun BlockItem(
    block: KidsUserStoryBlock, index: Int, context: android.content.Context, durationMs: Long?,
    onTextChange: (String) -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit, onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Блок ${index + 1} ${if (block.isImage) "📷" else "📝"}",
                fontWeight = MaterialTheme.typography.titleSmall.fontWeight, fontSize = 14.sp,
            )
            Row {
                IconButton(onClick = onMoveUp, enabled = index > 0) { Text("⬆️") }
                IconButton(onClick = onMoveDown, enabled = true) { Text("⬇️") }
                IconButton(onClick = onRemove) { Text("🗑️") }
            }
        }

        if (block.isImage) {
            val file = File(context.filesDir, block.image)
            val bitmap = remember(block.image) { if (file.exists()) decodeKidsStoryBitmap(file) else null }
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp))
            }
            if (durationMs != null) {
                Text("Показ: ${durationMs} мс (без текста)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            OutlinedTextField(
                value = block.text, onValueChange = onTextChange, modifier = Modifier.fillMaxWidth(),
                minLines = 3, placeholder = { Text("Введите текст...") },
            )
        }
    }
}