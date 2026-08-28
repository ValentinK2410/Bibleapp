package com.example.bible.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Поиск уже лежащих на телефоне аудиофайлов, в том числе в каталоге Яндекс Музыки
 * `Android/data/ru.yandex.music/files`, если система его отдаёт.
 */
object LocalDeviceAudioScan {

    const val YANDEX_FILES_RELATIVE = "Android/data/ru.yandex.music/files"

    data class ScanOutcome(
        val tracks: List<LegalAudioTrack>,
        val hint: String?,
    )

    fun yandexInitialTreeUri(): Uri =
        DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Android/data/ru.yandex.music/files",
        )

    suspend fun scanYandexMusicFiles(query: String, limit: Int = 80): ScanOutcome =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dirs = candidateYandexDirs()
            val existing = dirs.filter { it.isDirectory }
            if (existing.isEmpty()) {
                return@withContext ScanOutcome(
                    emptyList(),
                    "Папка $YANDEX_FILES_RELATIVE недоступна этому приложению (так устроен Android). Нажмите «Выбрать папку» — если системный проводник её покажет.",
                )
            }
            val readable = existing.filter { it.canRead() }
            if (readable.isEmpty()) {
                return@withContext ScanOutcome(
                    emptyList(),
                    "Папка Яндекс Музыки есть, но прочитать её нельзя. Выберите её через «Выбрать папку».",
                )
            }
            val tracks = readable.flatMap { scanFileTree(it, query, "yandex_local", limit) }
                .distinctBy { it.id }
                .take(limit)
            val hint = when {
                tracks.isEmpty() ->
                    "Папка найдена, но обычных mp3/ogg/m4a нет. Офлайн-кэш Яндекса часто в закрытом формате. Можно выбрать папку вручную."
                else ->
                    "На устройстве (Яндекс Музыка): ${tracks.size}"
            }
            ScanOutcome(tracks, hint)
        }

    fun scanDocumentTree(
        context: Context,
        treeUri: Uri,
        query: String,
        limit: Int = 80,
    ): List<LegalAudioTrack> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val out = ArrayList<LegalAudioTrack>(limit)
        walkDocuments(root, query, out, limit, 0)
        return out
    }

    private fun candidateYandexDirs(): List<File> {
        val roots = listOfNotNull(
            Environment.getExternalStorageDirectory(),
            File("/storage/emulated/0"),
            File("/sdcard"),
        ).distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
        return roots.flatMap { root ->
            listOf(
                File(root, YANDEX_FILES_RELATIVE),
                File(root, "$YANDEX_FILES_RELATIVE/Music"),
                File(root, "$YANDEX_FILES_RELATIVE/music"),
            )
        }
    }

    private fun scanFileTree(
        dir: File,
        query: String,
        origin: String,
        limit: Int,
    ): List<LegalAudioTrack> {
        val out = ArrayList<LegalAudioTrack>(limit)
        walkFiles(dir, query, origin, out, limit, 0)
        return out
    }

    private fun walkFiles(
        dir: File,
        query: String,
        origin: String,
        out: MutableList<LegalAudioTrack>,
        limit: Int,
        depth: Int,
    ) {
        if (out.size >= limit || depth > 10) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (out.size >= limit) return
            when {
                f.isDirectory -> walkFiles(f, query, origin, out, limit, depth + 1)
                f.isFile && f.length() > 64 && MediaCatalogPaths.isLikelyAudioFileName(f.name) -> {
                    if (matchesQuery(f.name, query)) {
                        out.add(
                            LegalAudioTrack(
                                title = f.nameWithoutExtension.ifBlank { f.name },
                                creator = "",
                                fileUrl = f.absolutePath,
                                pageUrl = f.absolutePath,
                                license = "на устройстве",
                                origin = origin,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun walkDocuments(
        dir: DocumentFile,
        query: String,
        out: MutableList<LegalAudioTrack>,
        limit: Int,
        depth: Int,
    ) {
        if (out.size >= limit || depth > 10) return
        for (child in dir.listFiles()) {
            if (out.size >= limit) return
            when {
                child.isDirectory -> walkDocuments(child, query, out, limit, depth + 1)
                child.isFile -> {
                    val name = child.name.orEmpty()
                    val uri = child.uri.toString()
                    val mime = child.type.orEmpty()
                    val audio = MediaCatalogPaths.isLikelyAudioFileName(name) || mime.startsWith("audio/")
                    if (audio && child.length() > 64 && matchesQuery(name, query)) {
                        out.add(
                            LegalAudioTrack(
                                title = name.substringBeforeLast('.').ifBlank { name },
                                creator = "",
                                fileUrl = uri,
                                pageUrl = uri,
                                license = "на устройстве",
                                origin = "device",
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun matchesQuery(fileName: String, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        val hay = fileName.lowercase()
        return q.split(Regex("\\s+")).filter { it.isNotEmpty() }.all { hay.contains(it) }
    }
}
