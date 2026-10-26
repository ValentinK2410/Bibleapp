package com.example.bible.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.KidsSongs
import com.example.bible.data.KidsUserMediaStorage
import com.example.bible.data.KidsUserSongsStore
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.Charset
import java.util.UUID

@Composable
fun KidsSongParentEditor(
    songId: String?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val builtIn = songId?.let { KidsSongs.byId(it) }
    val existing = songId?.let { KidsUserSongsStore.find(context, it) }
    val userMade = builtIn == null
    var title by remember {
        mutableStateOf(
            when {
                existing?.userMade == true -> existing.title
                builtIn != null -> builtIn.title
                else -> ""
            },
        )
    }
    var lyrics by remember {
        mutableStateOf(
            existing?.lyrics
                ?: builtIn?.lines?.joinToString("\n") { it.text }
                ?: "",
        )
    }
    var audio by remember { mutableStateOf(existing?.audio.orEmpty()) }
    val addedNow = remember { mutableSetOf<String>() }
    var confirmDelete by remember { mutableStateOf(false) }

    fun dropAdded() {
        addedNow.forEach { File(context.filesDir, it).delete() }
        addedNow.clear()
    }

    val textPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = readParentSongText(context, uri)
        if (text.isNullOrBlank()) {
            Toast.makeText(context, "В файле нет текста", Toast.LENGTH_SHORT).show()
        } else {
            lyrics = text
            if (title.isBlank()) {
                title = KidsUserMediaStorage.displayName(context, uri)
                    ?.substringBeforeLast('.')
                    ?.trim()
                    .orEmpty()
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = KidsUserMediaStorage.copyUriIntoFilesDir(
            context,
            uri,
            KidsUserMediaStorage.SONG_AUDIO_SUBDIR,
            "m4a",
        )
        if (path == null) {
            Toast.makeText(context, "Не удалось сохранить фонограмму", Toast.LENGTH_SHORT).show()
        } else {
            audio = audio + path
            addedNow += path
        }
    }

    KidsSectionScaffold(
        title = if (userMade && existing == null) "Новая песня" else "Текст и фонограмма",
        onBack = {
            dropAdded()
            onDone()
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Это видит только взрослый после примера. Файлы остаются на телефоне.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            if (userMade) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            } else {
                Text(builtIn?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = lyrics,
                onValueChange = { lyrics = it },
                label = { Text("Текст песни") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
            )
            TextButton(onClick = { textPicker.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) }) {
                Text("Загрузить текст из файла")
            }
            Text("Фонограммы", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            if (audio.isEmpty()) {
                Text(
                    "Пока нет. Можно добавить одну или несколько.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            audio.forEach { rel ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        File(rel).name,
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                    )
                    TextButton(onClick = {
                        audio = audio.filterNot { it == rel }
                        if (rel in addedNow) {
                            File(context.filesDir, rel).delete()
                            addedNow -= rel
                        }
                    }) { Text("Убрать") }
                }
            }
            TextButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                Text("Добавить фонограмму")
            }
            Button(
                onClick = {
                    val id = songId ?: "user-${UUID.randomUUID()}"
                    val text = lyrics.trim()
                    if (userMade && title.isBlank()) {
                        Toast.makeText(context, "Нужно название", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (text.isBlank() && audio.isEmpty()) {
                        Toast.makeText(context, "Добавьте текст или фонограмму", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val builtInText = builtIn?.lines?.joinToString("\n") { it.text }?.trim()
                    val storedLyrics = when {
                        userMade -> text
                        text.isBlank() || text == builtInText -> null
                        else -> text
                    }
                    scope.launch {
                        KidsUserSongsStore.save(
                            context,
                            KidsUserSongsStore.Entry(
                                id = id,
                                title = if (userMade) title.trim() else "",
                                lyrics = storedLyrics,
                                audio = audio,
                                userMade = userMade,
                            ),
                        )
                        addedNow.clear()
                        Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
            if (existing != null) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(if (userMade) "Удалить песню" else "Убрать свой текст и фонограммы")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (userMade) "Удалить песню?" else "Вернуть песню приложения?") },
            text = { Text("Свой текст и фонограммы будут стёрты с телефона.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val id = existing?.id ?: return@TextButton
                    scope.launch {
                        KidsUserSongsStore.delete(context, id)
                        addedNow.clear()
                        onDone()
                    }
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }
}

private fun readParentSongText(context: android.content.Context, uri: Uri): String? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.isEmpty()) return null
    val utf = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
    val broken = utf.count { it == '\uFFFD' }
    if (broken > 2) {
        return bytes.toString(Charset.forName("windows-1251")).trim().ifBlank { null }
    }
    return utf.ifBlank { null }
}
