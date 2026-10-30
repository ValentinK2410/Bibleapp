package com.example.bible.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.bible.data.KidsUserStoriesStore
import com.example.bible.data.KidsUserStory
import com.example.bible.data.KidsUserStoryBlock
import com.example.bible.data.KidsUserMediaStorage
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

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
    
    var title by remember {
        mutableStateOf(
            existing?.title ?: "Новая история"
        )
    }
    
    var blocks by remember {
        mutableStateOf(
            existing?.blocks?.toList() ?: emptyList()
        )
    }
    
    var confirmDelete by remember { mutableStateOf(false) }
    
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = KidsUserMediaStorage.copyUriIntoFilesDir(
            context,
            uri,
            KidsUserMediaStorage.IMAGES_SUBDIR,
            "jpg"
        )
        if (path == null) {
            Toast.makeText(context, "Не удалось сохранить изображение", Toast.LENGTH_SHORT).show()
        } else {
            blocks = blocks + KidsUserStoryBlock(KidsUserStoryBlock.IMAGE, image = path)
        }
    }
    
    fun addTextBlock() {
        blocks = blocks + KidsUserStoryBlock(KidsUserStoryBlock.TEXT, text = "")
    }
    
    fun updateTextBlock(index: Int, text: String) {
        if (index in blocks.indices && !blocks[index].isImage) {
            blocks = blocks.toMutableList().apply {
                set(index, blocks[index].copy(text = text))
            }
        }
    }
    
    fun moveBlock(fromIndex: Int, toIndex: Int) {
        if (fromIndex in blocks.indices && toIndex in blocks.indices) {
            blocks = blocks.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        }
    }
    
    fun removeBlock(index: Int) {
        val block = blocks.getOrNull(index)
        if (block?.isImage == true && block.image.isNotBlank()) {
            File(context.filesDir, block.image).delete()
        }
        blocks = blocks.toMutableList().apply {
            removeAt(index)
        }
    }
    
    KidsSectionScaffold(
        title = if (isNew) "Новая история" else "Редактирование",
        onBack = onBack,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название истории") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            
            Text("Блоки истории", fontWeight = MaterialTheme.typography.titleMedium.fontWeight)
            Text(
                "Добавляйте картинки и текст в том порядке, как хотите, чтобы их видели. Перетаскивайте блоки, чтобы изменить порядок.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            
            blocks.forEachIndexed { index, block ->
                BlockItem(
                    block = block,
                    index = index,
                    context = context,
                    onTextChange = { updateTextBlock(index, it) },
                    onMoveUp = { if (index > 0) moveBlock(index, index - 1) },
                    onMoveDown = { if (index < blocks.size - 1) moveBlock(index, index + 1) },
                    onRemove = { removeBlock(index) },
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("➕ Картинка")
                }
                Button(
                    onClick = { addTextBlock() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("➕ Текст")
                }
            }
            
            Button(
                onClick = {
                    val cleanTitle = title.trim()
                    if (cleanTitle.isBlank()) {
                        Toast.makeText(context, "Нужно название", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val cleanBlocks = blocks.mapNotNull { block ->
                        when {
                            block.isImage && block.image.isNotBlank() && File(context.filesDir, block.image).isFile ->
                                KidsUserStoryBlock(KidsUserStoryBlock.IMAGE, image = block.image)
                            !block.isImage && block.text.isNotBlank() ->
                                KidsUserStoryBlock(KidsUserStoryBlock.TEXT, text = block.text.trim())
                            else -> null
                        }
                    }
                    if (cleanBlocks.isEmpty()) {
                        Toast.makeText(context, "Добавьте хотя бы один блок", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        KidsUserStoriesStore.save(
                            context,
                            KidsUserStory(
                                id = id,
                                title = cleanTitle,
                                blocks = cleanBlocks,
                            )
                        )
                        Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                        onSaved(id)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить")
            }
            
            if (!isNew) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Удалить историю", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    
    if (confirmDelete && storyId != "new") {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить историю?", fontWeight = MaterialTheme.typography.titleMedium.fontWeight) },
            text = { Text("\"$title\" будет удалена безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        KidsUserStoriesStore.delete(context, id)
                        onBack()
                    }
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun BlockItem(
    block: KidsUserStoryBlock,
    index: Int,
    context: android.content.Context,
    onTextChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Блок ${index + 1} ${if (block.isImage) "📷" else "📝"}",
                fontWeight = MaterialTheme.typography.titleSmall.fontWeight,
                fontSize = 14.sp,
            )
            Row {
                IconButton(onClick = onMoveUp, enabled = index > 0) {
                    Text("⬆️")
                }
                IconButton(onClick = onMoveDown, enabled = index < 100) {
                    Text("⬇️")
                }
                IconButton(onClick = onRemove) {
                    Text("🗑️")
                }
            }
        }
        
        if (block.isImage) {
            val file = File(context.filesDir, block.image)
            val bitmap = remember(block.image) { 
                if (file.exists()) decodeKidsStoryBitmap(file) else null 
            }
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(vertical = 8.dp),
                )
            }
        } else {
            OutlinedTextField(
                value = block.text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                placeholder = { Text("Введите текст...") },
            )
        }
    }
}

@Composable
private fun IconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.padding(horizontal = 2.dp),
    ) {
        content()
    }
}
