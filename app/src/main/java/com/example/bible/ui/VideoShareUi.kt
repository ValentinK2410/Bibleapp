package com.example.bible.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.bible.data.BibleUserVideo
import com.example.bible.data.VideoSharePackage
import com.example.bible.data.VideoThought
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class LibraryVideoShareAction {
    VIDEO,
    THOUGHTS,
    VIDEO_WITH_THOUGHTS,
}

data class LibraryVideoShareState(
    val busyMessage: String?,
    val share: (
        video: BibleUserVideo,
        file: File?,
        thoughts: List<VideoThought>,
        action: LibraryVideoShareAction,
    ) -> Unit,
)

@Composable
fun rememberLibraryVideoShareState(): LibraryVideoShareState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    return LibraryVideoShareState(
        busyMessage = busy,
        share = { video, file, thoughts, action ->
            when (action) {
                LibraryVideoShareAction.VIDEO -> shareVideoFileOnly(context, file)
                LibraryVideoShareAction.THOUGHTS ->
                    shareVideoThoughts(context, scope, video, thoughts) { busy = it }
                LibraryVideoShareAction.VIDEO_WITH_THOUGHTS ->
                    shareVideoWithThoughtsForApp(context, scope, video, file, thoughts) { busy = it }
            }
        },
    )
}

@Composable
fun LibraryVideoShareBusyOverlay(state: LibraryVideoShareState) {
    ReceivedMediaImportBusyOverlay(state.busyMessage)
}

private fun shareVideoFileOnly(context: android.content.Context, file: File?) {
    if (file == null || !file.exists()) {
        Toast.makeText(context, "Файл видео не найден", Toast.LENGTH_SHORT).show()
        return
    }
    shareMediaFile(context, file, "video/*")
}

private fun shareVideoThoughts(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    video: BibleUserVideo,
    thoughts: List<VideoThought>,
    onBusy: (String?) -> Unit,
) {
    if (thoughts.isEmpty()) {
        Toast.makeText(context, "Нет заметок для отправки", Toast.LENGTH_SHORT).show()
        return
    }
    scope.launch {
        onBusy("Готовим заметки…")
        try {
            val jsonFile = withContext(Dispatchers.IO) {
                VideoSharePackage.exportThoughtsJson(context, video, thoughts)
            }
            val text = VideoSharePackage.thoughtsPlainText(video.title, thoughts)
            val jsonUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                jsonFile,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, jsonUri)
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, "Заметки: ${video.title}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, context.getString(com.example.bible.R.string.video_share_thoughts_chooser)),
            )
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Не удалось поделиться", Toast.LENGTH_LONG).show()
        } finally {
            onBusy(null)
        }
    }
}

private fun shareVideoWithThoughtsForApp(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    video: BibleUserVideo,
    file: File?,
    thoughts: List<VideoThought>,
    onBusy: (String?) -> Unit,
) {
    val hasFile = file != null && file.exists() && file.length() > 64
    if (!hasFile && thoughts.isEmpty()) {
        Toast.makeText(context, "Нечего отправить", Toast.LENGTH_SHORT).show()
        return
    }
    if (!hasFile) {
        Toast.makeText(
            context,
            "Локального файла нет — в архиве будут заметки и ссылка на видео.",
            Toast.LENGTH_LONG,
        ).show()
    }
    scope.launch {
        onBusy("Готовим архив…")
        try {
            val zip = withContext(Dispatchers.IO) {
                VideoSharePackage.exportVideoWithThoughtsZip(context, video, file, thoughts)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                zip,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, video.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(
                    send,
                    context.getString(com.example.bible.R.string.video_share_app_pack_chooser),
                ),
            )
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Не удалось поделиться", Toast.LENGTH_LONG).show()
        } finally {
            onBusy(null)
        }
    }
}
