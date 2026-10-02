package com.example.bible.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Незавершённая загрузка из очереди: задача, её состояние и число уже сделанных подходов. */
data class PendingMediaDownload(
    val task: MediaDownloadTask,
    val paused: Boolean = false,
    val attempts: Int = 0,
)

/** Что осталось докачать: задачи очереди и выбранная пользователем параллельность. */
data class PendingMediaDownloads(
    val downloads: List<PendingMediaDownload> = emptyList(),
    val parallel: Int = MediaDownloadState.DEFAULT_PARALLEL,
) {
    val isEmpty: Boolean get() = downloads.isEmpty()

    /** Задачи, которые нужно возобновить: поставленные на паузу ждут решения пользователя. */
    val resumable: List<PendingMediaDownload> get() = downloads.filterNot { it.paused }
}

/**
 * Помнит между запусками приложения, какие загрузки пользователь поставил в очередь и ещё
 * не получил файлом. Очередь сервиса живёт только в памяти процесса, поэтому без этого
 * списка после закрытия приложения недокачанные видео и плейлисты терялись.
 *
 * Хранилище синхронное: сервис дописывает его прямо из рабочих потоков загрузки.
 */
object MediaDownloadResumeStore {

    /** Больше попыток не делаем: ссылка, скорее всего, нерабочая. */
    const val MAX_ATTEMPTS = 3

    private const val PREFS = "media_download_resume"
    private const val KEY_TASKS = "tasks"
    private const val KEY_PARALLEL = "parallel"
    private const val KEY_USER_MEDIA = "user_media"
    private const val KEY_PLAYLISTS = "playlists"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ очередь сервиса

    fun pending(context: Context): PendingMediaDownloads {
        val p = prefs(context)
        return PendingMediaDownloads(
            downloads = parse(p.getString(KEY_TASKS, null)),
            parallel = p.getInt(KEY_PARALLEL, MediaDownloadState.DEFAULT_PARALLEL),
        )
    }

    /** Запоминает только что поставленные в очередь задачи. */
    fun remember(context: Context, tasks: List<MediaDownloadTask>, parallel: Int) {
        if (tasks.isEmpty()) return
        val known = pending(context).downloads
        val knownIds = known.map { it.task.id }.toSet()
        val added = tasks.filterNot { it.id in knownIds }.map { PendingMediaDownload(it) }
        write(context, known + added, parallel.takeIf { it > 0 })
    }

    /** Сохраняет список после восстановления: у возобновлённых задач растёт счётчик попыток. */
    fun rememberRestored(context: Context, downloads: List<PendingMediaDownload>) {
        write(context, downloads, null)
    }

    fun setPaused(context: Context, id: String, paused: Boolean) {
        val updated = pending(context).downloads.map {
            if (it.task.id == id) it.copy(paused = paused) else it
        }
        write(context, updated, null)
    }

    /** Загрузка завершилась или отменена — хранить её больше не нужно. */
    fun forget(context: Context, id: String) {
        val updated = pending(context).downloads.filterNot { it.task.id == id }
        write(context, updated, null)
    }

    fun forgetAll(context: Context) {
        write(context, emptyList(), null)
    }

    // ------------------------- отдельные записи и плейлисты раздела «Медиа»

    /** Ключ записи медиатеки, которую пользователь просил скачать. */
    fun userMediaKey(kind: UserMediaKind, mediaId: String): String =
        "${kind.name.lowercase()}:$mediaId"

    fun pendingUserMedia(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_USER_MEDIA, emptySet()).orEmpty().toSet()

    fun rememberUserMedia(context: Context, key: String) {
        val updated = pendingUserMedia(context) + key
        prefs(context).edit().putStringSet(KEY_USER_MEDIA, updated).apply()
    }

    fun forgetUserMedia(context: Context, key: String) {
        val updated = pendingUserMedia(context) - key
        prefs(context).edit().putStringSet(KEY_USER_MEDIA, updated).apply()
    }

    fun pendingPlaylists(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PLAYLISTS, emptySet()).orEmpty().toSet()

    /** Плейлист, который пользователь просил скачать целиком. */
    fun rememberPlaylist(context: Context, playlistId: String) {
        val updated = pendingPlaylists(context) + playlistId
        prefs(context).edit().putStringSet(KEY_PLAYLISTS, updated).apply()
    }

    fun forgetPlaylist(context: Context, playlistId: String) {
        val updated = pendingPlaylists(context) - playlistId
        prefs(context).edit().putStringSet(KEY_PLAYLISTS, updated).apply()
    }

    // ------------------------------------------------------------------ сериализация

    private fun write(context: Context, downloads: List<PendingMediaDownload>, parallel: Int?) {
        val array = JSONArray()
        for (entry in downloads) {
            array.put(
                entry.task.toJson()
                    .put("paused", entry.paused)
                    .put("attempts", entry.attempts),
            )
        }
        prefs(context).edit().apply {
            putString(KEY_TASKS, array.toString())
            parallel?.let { putInt(KEY_PARALLEL, it) }
        }.apply()
    }

    private fun parse(raw: String?): List<PendingMediaDownload> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o: JSONObject = array.optJSONObject(i) ?: return@mapNotNull null
                PendingMediaDownload(
                    task = MediaDownloadTask.fromJson(o),
                    paused = o.optBoolean("paused"),
                    attempts = o.optInt("attempts"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
