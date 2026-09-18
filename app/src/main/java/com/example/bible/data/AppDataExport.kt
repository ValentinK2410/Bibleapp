package com.example.bible.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val MANIFEST = "manifest.json"
private const val DS_SETTINGS = "datastore_settings.json"
private const val DS_USER = "datastore_user.json"
private const val DS_FULL = "datastore.json"

/** Ключи настроек читалки (без закладок и контента). */
private val SETTINGS_KEYS = setOf(
    "translation",
    "reader_font_scale",
    "video_library_title_scale",
    "dark_mode",
    "audio_narrator_id",
    "reading_plan_completed_dates",
    "reading_plan_reminder_h",
    "reading_plan_reminder_m",
    "books_main_menu_order",
    "media_home_section_order",
    "bible_app_theme_preset",
    "translation_tab_colors_json",
)

/** Пользовательский контент в DataStore. */
private val USER_DATA_KEYS = setOf(
    "bookmarks",
    "text_highlights_json",
    "reading_history_json",
    "reading_trace_json",
    "user_notes_json",
    "user_songs_json",
    "user_song_tags",
    "user_song_playlists_json",
    "pv_hymn_overlays_json",
    "bookmark_tags_json",
    "user_bible_images_json",
    "user_bible_videos_json",
    "user_bible_video_thoughts_json",
    "user_bible_audios_json",
    "user_semantic_lexicon_json",
    "lexicon_preset_enabled",
    "lexicon_preset_tones",
    "lexicon_user_enabled",
    "lexicon_user_tones",
    "word_span_media_json",
    "bible_search_history_json",
    "quran_reading_history_json",
    "quran_reading_trace_json",
)

/**
 * Выбор компонентов для обмена архивом (офлайн, между устройствами).
 * Пути к файлам в JSON при экспорте делаются относительными к [Context.getFilesDir].
 */
data class ExportBundleOptions(
    val settings: Boolean = true,
    val userData: Boolean = true,
    /** Файлы песен (songs_audio / songs_video); имеет смысл вместе с данными песен. */
    val songsMedia: Boolean = true,
    /** Кэш studybible.ru: комментарии к главам, сравнения, ссылки, Стронг, API-комментарии. */
    val studyOffline: Boolean = true,
    /** Проекты таймкодов и вложения. */
    val timemark: Boolean = true,
    val verseAttachments: Boolean = true,
    /** Скачанная озвучка глав — может занимать много места. */
    val bibleAudio: Boolean = false,
) {
    fun anySelected(): Boolean =
        settings || userData || songsMedia || studyOffline || timemark || verseAttachments || bibleAudio
}

/**
 * Детальный выбор для экрана «Поделиться приложением» (флешка, внешний накопитель).
 * Импорт на другом устройстве — через раздел резервной копии (тот же формат ZIP).
 */
data class ShareExportOptions(
    val appSettings: Boolean = true,
    /** Закладки, подсветки, история чтения, журнал, теги закладок. */
    val readerBookmarksHighlightsHistory: Boolean = true,
    val personalNotes: Boolean = true,
    /** Вложения к стихам (файлы) + индекс. */
    val verseAttachmentsAndComments: Boolean = true,
    /** Пользовательский лексикон и настройки семантической подсветки. */
    val semanticLexicon: Boolean = true,
    /** Привязки медиа к выделенным фрагментам в стихах. */
    val wordSpanLinks: Boolean = true,
    val bibleCatalogImages: Boolean = true,
    val bibleCatalogVideos: Boolean = true,
    val bibleCatalogAudios: Boolean = true,
    /** Тексты песен, теги, таймкоды строк ([SongItem.lyricCues]). */
    val songTextsTagsAndLyricCues: Boolean = true,
    /** Каталоги songs_audio / songs_video. */
    val songMediaFiles: Boolean = true,
    /** Проекты таймкодов к главам Библии. */
    val timemarkBibleProjects: Boolean = true,
    /** Кэш studybible.ru: словари, комментарии, сравнения и т.д. */
    val studyOfflineMaterials: Boolean = true,
    val bibleDownloadedAudio: Boolean = false,
    /**
     * Какие подпапки `bible_audio/<id>` включить в архив.
     * `null` при [bibleDownloadedAudio] = true означает **все** подпапки с файлами (как раньше).
     * Непустой набор — только перечисленные дикторы/дорожки.
     */
    val bibleAudioNarratorIds: Set<String>? = null,
    val quranSearchHistory: Boolean = true,
    /** Плейлисты видео/аудио в медиатеке. */
    val userMediaPlaylists: Boolean = false,
    /** Заметки к видео. */
    val userVideoThoughts: Boolean = false,
    /** Плейлисты песен (тексты и аккорды в user_songs_json). */
    val userSongPlaylists: Boolean = false,
    /** Микроблог: записи и картинки. */
    val microblogPosts: Boolean = false,
    /** Копия установленного APK в папку bundled_app/ архива (для установки на другом устройстве). */
    val includeInstalledApk: Boolean = true,
    /** Метаданные переносимого пакета (версии модулей для сервера/флешки). */
    val portableBundleMeta: PortableBundleMeta? = null,
) {
    fun anySelected(): Boolean = listOf(
        includeInstalledApk,
        appSettings,
        readerBookmarksHighlightsHistory,
        personalNotes,
        verseAttachmentsAndComments,
        semanticLexicon,
        wordSpanLinks,
        bibleCatalogImages,
        bibleCatalogVideos,
        bibleCatalogAudios,
        songTextsTagsAndLyricCues,
        songMediaFiles,
        userSongPlaylists,
        timemarkBibleProjects,
        studyOfflineMaterials,
        bibleDownloadedAudio,
        quranSearchHistory,
        userMediaPlaylists,
        userVideoThoughts,
        microblogPosts,
    ).any { it }
}

/** События прогресса для экрана «Поделиться приложением». */
sealed interface ExportShareProgressEvent {
    /** Очередной файл добавлен в ZIP ([ordinal] — порядковый номер, [pathInArchive] — путь внутри архива). */
    data class ZipFileAdded(val ordinal: Int, val pathInArchive: String) : ExportShareProgressEvent

    /** Копирование готового ZIP на носитель (SAF). */
    data class CopyToStorage(val bytesDone: Long, val bytesTotal: Long) : ExportShareProgressEvent
}

object AppDataExport {

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun postExportProgress(
        callback: ((ExportShareProgressEvent) -> Unit)?,
        event: ExportShareProgressEvent,
    ) {
        if (callback == null) return
        mainHandler.post { callback.invoke(event) }
    }

    private fun postCopyBytesProgress(
        callback: ((Long, Long) -> Unit)?,
        done: Long,
        total: Long,
    ) {
        if (callback == null) return
        mainHandler.post { callback.invoke(done, total) }
    }

    /** Id дикторов (`bible_audio/<id>`), в папках которых есть хотя бы один файл. */
    fun listLocalBibleAudioNarratorIds(context: Context): List<String> {
        val root = File(context.filesDir, "bible_audio")
        if (!root.isDirectory) return emptyList()
        return root.listFiles()?.asSequence()
            ?.filter { it.isDirectory }
            ?.filter { dir -> dir.walkTopDown().any { f -> f.isFile } }
            ?.map { it.name }
            ?.sorted()
            ?.toList()
            ?: emptyList()
    }

    suspend fun exportZip(context: Context, preferences: BiblePreferences, options: ExportBundleOptions): File =
        withContext(Dispatchers.IO) {
            require(options.anySelected()) { "nothing selected" }
            val zipFile = File(context.cacheDir, "bible_share_${System.currentTimeMillis()}.zip")
            val snapshot = preferences.preferencesSnapshot()
            val manifest = JSONObject().apply {
                put("format", "bible_app_export")
                put("version", 2)
                put("settings", options.settings)
                put("userData", options.userData)
                put("songsMedia", options.songsMedia)
                put("studyOffline", options.studyOffline)
                put("timemark", options.timemark)
                put("verseAttachments", options.verseAttachments)
                put("bibleAudio", options.bibleAudio)
            }
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zos.putNextEntry(ZipEntry(MANIFEST))
                zos.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                fun putJson(name: String, jo: JSONObject) {
                    if (jo.length() == 0) return
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(jo.toString().toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }

                if (options.settings) {
                    putJson(DS_SETTINGS, filterJson(snapshot, SETTINGS_KEYS))
                }
                if (options.userData || options.songsMedia) {
                    val keys = mutableSetOf<String>()
                    if (options.userData) keys.addAll(USER_DATA_KEYS)
                    if (options.songsMedia && !options.userData) keys.add("user_songs_json")
                    var userJo = filterJson(snapshot, keys)
                    if (options.songsMedia && userJo.has("user_songs_json")) {
                        val raw = userJo.getString("user_songs_json")
                        userJo.put("user_songs_json", relativizeSongsJson(context, raw))
                    }
                    putJson(DS_USER, userJo)
                }

                fun addDirIf(rel: String, enabled: Boolean) {
                    if (!enabled) return
                    val dir = File(context.filesDir, rel)
                    if (!dir.isDirectory) return
                    dir.walkTopDown().filter { it.isFile }.forEach { f ->
                        val path = f.relativeTo(context.filesDir).path.replace(File.separatorChar, '/')
                        zos.putNextEntry(ZipEntry("files/$path"))
                        FileInputStream(f).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }

                if (options.studyOffline) addDirIf("study_cache", true)
                if (options.songsMedia) {
                    addDirIf("songs_audio", true)
                    addDirIf("songs_video", true)
                }
                if (options.timemark) {
                    addDirIf("timemark_projects", true)
                    addDirIf("timemark_audio", true)
                    addDirIf("timemark_attachments", true)
                }
                if (options.verseAttachments) {
                    val indexFile = File(context.filesDir, "verse_attachments_index.json")
                    if (indexFile.isFile) {
                        zos.putNextEntry(ZipEntry("verse_attachments_index.json"))
                        zos.write(indexFile.readBytes())
                        zos.closeEntry()
                    }
                    addDirIf("verse_attachments", true)
                }
                if (options.userData) {
                    addDirIf(MediaCatalogPaths.ROOT, true)
                    addDirIf("media_library", true)
                    addDirIf("video_library", true)
                    addDirIf("audio_library", true)
                }
                if (options.bibleAudio) addDirIf("bible_audio", true)
            }
            zipFile
        }

    suspend fun exportShareZip(
        context: Context,
        preferences: BiblePreferences,
        options: ShareExportOptions,
        onProgress: ((ExportShareProgressEvent) -> Unit)? = null,
    ): File =
        withContext(Dispatchers.IO) {
            require(options.anySelected()) { "nothing selected" }
            val zipFile = File(context.cacheDir, "bible_share_pack_${System.currentTimeMillis()}.zip")
            val snapshot = preferences.preferencesSnapshot()
            val userKeys = linkedSetOf<String>()
            if (options.readerBookmarksHighlightsHistory) {
                userKeys += setOf(
                    "bookmarks",
                    "text_highlights_json",
                    "reading_history_json",
                    "reading_trace_json",
                    "bookmark_tags_json",
                    "bible_search_history_json",
                    "quran_reading_history_json",
                    "quran_reading_trace_json",
                )
            }
            if (options.personalNotes) {
                userKeys += "user_notes_json"
                userKeys += "note_custom_kinds_json"
            }
            if (options.semanticLexicon) {
                userKeys += setOf(
                    "user_semantic_lexicon_json",
                    "lexicon_preset_enabled",
                    "lexicon_preset_tones",
                    "lexicon_user_enabled",
                    "lexicon_user_tones",
                )
            }
            if (options.wordSpanLinks) userKeys += "word_span_media_json"
            if (options.bibleCatalogImages) userKeys += "user_bible_images_json"
            if (options.bibleCatalogVideos) userKeys += "user_bible_videos_json"
            if (options.bibleCatalogAudios) userKeys += "user_bible_audios_json"
            if (options.songTextsTagsAndLyricCues) {
                userKeys += "user_songs_json"
                userKeys += "user_song_tags"
            }
            if (options.userSongPlaylists) userKeys += "user_song_playlists_json"
            if (options.userMediaPlaylists) userKeys += "user_media_playlists_json"
            if (options.userVideoThoughts) userKeys += "user_bible_video_thoughts_json"
            if (options.songMediaFiles && "user_songs_json" !in userKeys) userKeys += "user_songs_json"
            if (options.quranSearchHistory) {
                userKeys += "quran_search_history_json"
                userKeys += "quran_reading_history_json"
                userKeys += "quran_reading_trace_json"
            }

            val manifest = JSONObject().apply {
                put("format", "bible_app_export")
                put("version", 3)
                put("shareExport", true)
                put("appSettings", options.appSettings)
                put("readerBookmarksHighlightsHistory", options.readerBookmarksHighlightsHistory)
                put("personalNotes", options.personalNotes)
                put("verseAttachmentsAndComments", options.verseAttachmentsAndComments)
                put("semanticLexicon", options.semanticLexicon)
                put("wordSpanLinks", options.wordSpanLinks)
                put("bibleCatalogImages", options.bibleCatalogImages)
                put("bibleCatalogVideos", options.bibleCatalogVideos)
                put("bibleCatalogAudios", options.bibleCatalogAudios)
                put("songTextsTagsAndLyricCues", options.songTextsTagsAndLyricCues)
                put("songMediaFiles", options.songMediaFiles)
                put("timemarkBibleProjects", options.timemarkBibleProjects)
                put("studyOfflineMaterials", options.studyOfflineMaterials)
                put("bibleDownloadedAudio", options.bibleDownloadedAudio)
                if (options.bibleDownloadedAudio) {
                    if (options.bibleAudioNarratorIds == null) {
                        put("bibleAudioExportScope", "all")
                    } else {
                        put("bibleAudioExportScope", "selected")
                        put(
                            "bibleAudioNarratorIds",
                            JSONArray(options.bibleAudioNarratorIds.sorted().toList()),
                        )
                    }
                }
                put("quranSearchHistory", options.quranSearchHistory)
                put("includeInstalledApk", options.includeInstalledApk)
                put("userMediaPlaylists", options.userMediaPlaylists)
                put("userVideoThoughts", options.userVideoThoughts)
                put("userSongPlaylists", options.userSongPlaylists)
                put("microblogPosts", options.microblogPosts)
            }
            options.portableBundleMeta?.let { PortableExchange.attachPortableMetaToManifest(manifest, it) }

            var zipOrdinal = 0
            fun bumpZipProgress(pathInArchive: String) {
                zipOrdinal++
                postExportProgress(onProgress, ExportShareProgressEvent.ZipFileAdded(zipOrdinal, pathInArchive))
            }

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zos.putNextEntry(ZipEntry(MANIFEST))
                zos.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                bumpZipProgress(MANIFEST)

                fun putJson(name: String, jo: JSONObject) {
                    if (jo.length() == 0) return
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(jo.toString().toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                    bumpZipProgress(name)
                }

                if (options.appSettings) {
                    putJson(DS_SETTINGS, filterJson(snapshot, SETTINGS_KEYS))
                }
                if (userKeys.isNotEmpty()) {
                    var userJo = filterJson(snapshot, userKeys)
                    if (options.songMediaFiles && userJo.has("user_songs_json")) {
                        val raw = userJo.getString("user_songs_json")
                        userJo.put("user_songs_json", relativizeSongsJson(context, raw))
                    }
                    putJson(DS_USER, userJo)
                }

                fun addDirIf(rel: String, enabled: Boolean) {
                    if (!enabled) return
                    val dir = File(context.filesDir, rel)
                    if (!dir.isDirectory) return
                    dir.walkTopDown().filter { it.isFile }.forEach { f ->
                        val path = f.relativeTo(context.filesDir).path.replace(File.separatorChar, '/')
                        val inArchive = "files/$path"
                        zos.putNextEntry(ZipEntry(inArchive))
                        FileInputStream(f).use { it.copyTo(zos) }
                        zos.closeEntry()
                        bumpZipProgress(inArchive)
                    }
                }

                val needCatalog = options.bibleCatalogImages ||
                    options.bibleCatalogVideos ||
                    options.bibleCatalogAudios
                if (needCatalog) {
                    addDirIf(MediaCatalogPaths.ROOT, true)
                    addDirIf("media_library", true)
                    addDirIf("video_library", true)
                    addDirIf("audio_library", true)
                }
                if (options.studyOfflineMaterials) addDirIf("study_cache", true)
                if (options.songMediaFiles) {
                    addDirIf("songs_audio", true)
                    addDirIf("songs_video", true)
                }
                if (options.timemarkBibleProjects) {
                    addDirIf("timemark_projects", true)
                    addDirIf("timemark_audio", true)
                    addDirIf("timemark_attachments", true)
                }
                if (options.verseAttachmentsAndComments) {
                    val indexFile = File(context.filesDir, "verse_attachments_index.json")
                    if (indexFile.isFile) {
                        zos.putNextEntry(ZipEntry("verse_attachments_index.json"))
                        zos.write(indexFile.readBytes())
                        zos.closeEntry()
                        bumpZipProgress("verse_attachments_index.json")
                    }
                    addDirIf("verse_attachments", true)
                }
                if (options.bibleDownloadedAudio) {
                    val ids = options.bibleAudioNarratorIds
                    if (ids == null) {
                        addDirIf("bible_audio", true)
                    } else {
                        for (nid in ids.sorted()) {
                            addDirIf("bible_audio/$nid", true)
                        }
                    }
                }
                if (options.microblogPosts) {
                    val postsJson = exportMicroblogPostsJson(context)
                    if (postsJson != null) {
                        zos.putNextEntry(ZipEntry("microblog_posts.json"))
                        zos.write(postsJson.toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                        bumpZipProgress("microblog_posts.json")
                    }
                    addDirIf(MediaCatalogPaths.MICROBLOG, true)
                }

                if (options.includeInstalledApk) {
                    try {
                        val ai: ApplicationInfo = context.packageManager.getApplicationInfo(
                            context.packageName,
                            0,
                        )
                        val apkPath = ai.publicSourceDir ?: ai.sourceDir
                        if (!apkPath.isNullOrBlank()) {
                            val apkFile = File(apkPath)
                            if (apkFile.isFile) {
                                val label = context.packageManager.getApplicationLabel(ai).toString()
                                    .replace(Regex("""[^\w.\-]+"""), "_").trim('_').ifBlank { "BibleApp" }
                                val entry = "bundled_app/${label}_${context.packageName}.apk"
                                zos.putNextEntry(ZipEntry(entry))
                                FileInputStream(apkFile).use { it.copyTo(zos) }
                                zos.closeEntry()
                                bumpZipProgress(entry)
                            }
                        }
                    } catch (_: Exception) {
                        // нет доступа к APK (редко) — архив данных всё равно полезен
                    }
                }
            }
            zipFile
        }

    /**
     * Копирует готовый ZIP в выбранную пользователем папку (дерево документов), например флешку.
     */
    fun copyZipToUserFolder(
        context: Context,
        treeUri: Uri,
        zipFile: File,
        fileBaseName: String,
        onCopyProgress: ((bytesDone: Long, bytesTotal: Long) -> Unit)? = null,
        subFolderRelative: String? = PortableExchange.USB_FOLDER,
    ): Boolean {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        val folder = if (subFolderRelative.isNullOrBlank()) {
            root
        } else {
            ensureSubFolder(root, subFolderRelative) ?: root
        }
        val safeName = fileBaseName.replace(Regex("""[^\w.\-]+"""), "_").trim('_').ifBlank { "bible_export" }
        val displayName = "$safeName.zip"
        val out = folder.createFile("application/zip", displayName)
            ?: folder.createFile("application/octet-stream", displayName)
            ?: run {
                val existing = folder.findFile(displayName)
                if (existing != null && existing.isFile) existing else null
            }
            ?: return false
        val totalBytes = zipFile.length().coerceAtLeast(1L)
        postCopyBytesProgress(onCopyProgress, 0L, totalBytes)
        return try {
            context.contentResolver.openOutputStream(out.uri, "w")?.use { os ->
                FileInputStream(zipFile).use { input ->
                    val buf = ByteArray(256 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        os.write(buf, 0, n)
                        copied += n
                        postCopyBytesProgress(onCopyProgress, copied, totalBytes)
                    }
                }
                os.flush()
            } != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Полный архив как раньше: весь DataStore + вложения к стихам (+ manifest).
     */
    suspend fun exportFullLegacyStyleZip(context: Context, preferences: BiblePreferences): File =
        withContext(Dispatchers.IO) {
            val zipFile = File(context.cacheDir, "bible_backup_${System.currentTimeMillis()}.zip")
            val snapshot = preferences.preferencesSnapshot()
            val full = snapshotToJsonObject(snapshot)
            val manifest = JSONObject().apply {
                put("format", "bible_app_export")
                put("version", 2)
                put("fullDataStore", true)
            }
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zos.putNextEntry(ZipEntry(MANIFEST))
                zos.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                zos.putNextEntry(ZipEntry(DS_FULL))
                zos.write(full.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                val indexFile = File(context.filesDir, "verse_attachments_index.json")
                if (indexFile.isFile) {
                    zos.putNextEntry(ZipEntry("verse_attachments_index.json"))
                    zos.write(indexFile.readBytes())
                    zos.closeEntry()
                }
                val attRoot = File(context.filesDir, "verse_attachments")
                if (attRoot.isDirectory) {
                    attRoot.walkTopDown().filter { it.isFile }.forEach { f ->
                        val rel = f.relativeTo(context.filesDir).path.replace(File.separatorChar, '/')
                        zos.putNextEntry(ZipEntry("files/$rel"))
                        FileInputStream(f).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                val catalogRoot = File(context.filesDir, MediaCatalogPaths.ROOT)
                if (catalogRoot.isDirectory) {
                    catalogRoot.walkTopDown().filter { it.isFile }.forEach { f ->
                        val rel = f.relativeTo(context.filesDir).path.replace(File.separatorChar, '/')
                        zos.putNextEntry(ZipEntry("files/$rel"))
                        FileInputStream(f).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                val legacyMediaRoots = listOf("media_library", "video_library", "audio_library")
                for (rel in legacyMediaRoots) {
                    val root = File(context.filesDir, rel)
                    if (!root.isDirectory) continue
                    root.walkTopDown().filter { it.isFile }.forEach { f ->
                        val pathRel = f.relativeTo(context.filesDir).path.replace(File.separatorChar, '/')
                        zos.putNextEntry(ZipEntry("files/$pathRel"))
                        FileInputStream(f).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            zipFile
        }

    suspend fun importZip(context: Context, preferences: BiblePreferences, zipFile: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                var manifest: JSONObject? = null
                var dsFull: JSONObject? = null
                var dsSettings: JSONObject? = null
                var dsUser: JSONObject? = null

                ZipInputStream(FileInputStream(zipFile)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        when {
                            name.startsWith("bundled_app/") -> zis.drainZipEntry()
                            name == MANIFEST -> {
                                val bytes = zis.readBytes()
                                manifest = JSONObject(bytes.toString(Charsets.UTF_8))
                            }
                            name == DS_FULL -> {
                                val bytes = zis.readBytes()
                                dsFull = JSONObject(bytes.toString(Charsets.UTF_8))
                            }
                            name == DS_SETTINGS -> {
                                val bytes = zis.readBytes()
                                dsSettings = JSONObject(bytes.toString(Charsets.UTF_8))
                            }
                            name == DS_USER -> {
                                val bytes = zis.readBytes()
                                dsUser = JSONObject(bytes.toString(Charsets.UTF_8))
                            }
                            name == "verse_attachments_index.json" -> {
                                val bytes = zis.readBytes()
                                safeChildFile(context.filesDir, "verse_attachments_index.json")?.let { idx ->
                                    idx.parentFile?.mkdirs()
                                    idx.writeBytes(bytes)
                                }
                            }
                            name == "microblog_posts.json" -> {
                                val bytes = zis.readBytes()
                                importMicroblogPostsJson(context, String(bytes, Charsets.UTF_8))
                            }
                            name.startsWith("files/") -> {
                                val rel = name.removePrefix("files/")
                                    .replace('\\', '/')
                                    .trimStart('/')
                                val target = safeChildFile(context.filesDir, rel)
                                if (target == null) {
                                    zis.drainZipEntry()
                                } else if (entry.isDirectory) {
                                    target.mkdirs()
                                    zis.drainZipEntry()
                                } else {
                                    target.parentFile?.mkdirs()
                                    FileOutputStream(target).use { out ->
                                        zis.copyTo(out, bufferSize = 256 * 1024)
                                    }
                                }
                            }
                            else -> zis.drainZipEntry()
                        }
                        entry = zis.nextEntry
                    }
                }

                if (manifest == null && dsFull != null) {
                    preferences.replaceAllFromJson(dsFull)
                    VerseAttachmentStore.invalidateCache()
                    return@withContext true
                }

                if (manifest != null && manifest!!.optBoolean("fullDataStore", false) && dsFull != null) {
                    preferences.replaceAllFromJson(dsFull)
                    VerseAttachmentStore.invalidateCache()
                    return@withContext true
                }

                if (manifest == null) return@withContext false

                dsSettings?.let { preferences.mergePreferencesFromJson(it) }
                dsUser?.let { jo ->
                    val fixed = if (jo.has("user_songs_json")) {
                        val abs = absolutizeSongsJson(context, jo.getString("user_songs_json"))
                        JSONObject(jo.toString()).apply { put("user_songs_json", abs) }
                    } else {
                        jo
                    }
                    preferences.mergePreferencesFromJson(fixed)
                }

                VerseAttachmentStore.invalidateCache()
                true
            } catch (_: Throwable) {
                false
        }
    }

    fun readManifestFromZip(zipFile: File): JSONObject? = try {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == MANIFEST) {
                    return JSONObject(zis.readBytes().toString(Charsets.UTF_8))
                }
                zis.drainZipEntry()
                entry = zis.nextEntry
            }
        }
        null
    } catch (_: Exception) {
        null
    }

    private fun ensureSubFolder(root: DocumentFile, relative: String): DocumentFile? {
        var cur: DocumentFile = root
        for (part in relative.split('/')) {
            if (part.isBlank()) continue
            cur = cur.findFile(part) ?: cur.createDirectory(part) ?: return null
        }
        return cur
    }

    private suspend fun exportMicroblogPostsJson(context: Context): String? {
        return try {
            val posts = MicroblogRepository(context).listPosts()
            if (posts.isEmpty()) return null
            val arr = JSONArray()
            posts.forEach { p ->
                arr.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("title", p.title)
                        put("body", p.body)
                        put("spans", spansToJson(p.spans))
                        put("images", imagesToJson(p.images))
                        put("createdAtMs", p.createdAtMs)
                        put("updatedAtMs", p.updatedAtMs)
                    },
                )
            }
            JSONObject().apply { put("posts", arr) }.toString()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun importMicroblogPostsJson(context: Context, raw: String) {
        if (raw.isBlank()) return
        try {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("posts") ?: return
            val repo = MicroblogRepository(context)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val post = MicroblogPost(
                    id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    title = o.optString("title", ""),
                    body = o.optString("body", ""),
                    spans = spansFromJson(o.optString("spans", "[]")),
                    images = imagesFromJson(o.optString("images", "[]")),
                    createdAtMs = o.optLong("createdAtMs", System.currentTimeMillis()),
                    updatedAtMs = o.optLong("updatedAtMs", System.currentTimeMillis()),
                )
                repo.save(post)
            }
        } catch (_: Exception) {
        }
    }

    /** Пропуск содержимого записи ZIP без загрузки всего в память (APK в архиве и пр.). */
    private fun ZipInputStream.drainZipEntry() {
        val buf = ByteArray(256 * 1024)
        while (true) {
            val n = read(buf)
            if (n <= 0) break
        }
    }

    /**
     * Путь внутри [filesDir] без выхода за пределы каталога (защита от `../` в ZIP).
     */
    private fun safeChildFile(filesDir: File, relativePath: String): File? {
        if (relativePath.isBlank()) return null
        val norm = relativePath.replace('\\', '/').trimStart('/')
        if (norm.contains("..")) return null
        return try {
            val base = filesDir.canonicalFile
            val child = File(filesDir, norm).canonicalFile
            val basePath = base.path
            val childPath = child.path
            if (childPath == basePath || childPath.startsWith("$basePath/")) child else null
        } catch (_: Exception) {
            null
        }
    }

    private fun snapshotToJsonObject(snapshot: Preferences): JSONObject {
        val jo = JSONObject()
        snapshot.asMap().forEach { (key, value) ->
            val name = key.name
            when (value) {
                is String -> jo.put(name, value)
                is Boolean -> jo.put(name, value)
                is Int -> jo.put(name, value)
                is Long -> jo.put(name, value)
                is Float -> jo.put(name, value.toDouble())
                is Double -> jo.put(name, value)
                is Set<*> -> {
                    val arr = JSONArray()
                    @Suppress("UNCHECKED_CAST")
                    (value as Set<String>).forEach { arr.put(it) }
                    jo.put(name, arr)
                }
                else -> jo.put(name, value.toString())
            }
        }
        return jo
    }

    private fun filterJson(snapshot: Preferences, keys: Set<String>): JSONObject {
        val full = snapshotToJsonObject(snapshot)
        val out = JSONObject()
        val it = full.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (k in keys) out.put(k, full.get(k))
        }
        return out
    }

    private fun normBase(path: String): String {
        val p = path.trimEnd('/')
        return if (p.endsWith(File.separator)) p.dropLast(1) else p
    }

    private fun relativizeSongsJson(context: Context, json: String): String {
        if (json.isBlank()) return json
        val songs = SongItem.parseList(json)
        val base = normBase(context.filesDir.absolutePath)
        val updated = songs.map { s ->
            s.copy(
                audioPaths = s.audioPaths.map { p -> relativizePath(base, p) },
                videoPath = s.videoPath?.let { p -> relativizePath(base, p) },
            )
        }
        return SongItem.toJsonArray(updated)
    }

    private fun relativizePath(base: String, abs: String): String {
        val a = normBase(abs)
        if (a.startsWith(base)) {
            return a.removePrefix(base).trimStart('/')
        }
        return abs
    }

    private fun absolutizeSongsJson(context: Context, json: String): String {
        if (json.isBlank()) return json
        val songs = SongItem.parseList(json)
        val updated = songs.map { s ->
            s.copy(
                audioPaths = s.audioPaths.map { p -> absolutizeMediaPath(context, p) },
                videoPath = s.videoPath?.let { p -> absolutizeMediaPath(context, p) },
            )
        }
        return SongItem.toJsonArray(updated)
    }

    private fun absolutizeMediaPath(context: Context, path: String): String {
        if (path.startsWith("/") && File(path).exists()) return path
        return File(context.filesDir, path).absolutePath
    }
}
