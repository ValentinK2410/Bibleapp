package com.example.bible.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Одна загрузка в фоновой очереди. Пустой [title] — название узнаём у платформы перед скачиванием. */
data class MediaDownloadTask(
    val url: String,
    val title: String,
    val audioOnly: Boolean,
    val videoQuality: Int,
    val skipIfExists: Boolean,
    val importAsAudio: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("url", url)
        .put("title", title)
        .put("audioOnly", audioOnly)
        .put("quality", videoQuality)
        .put("skip", skipIfExists)
        .put("asAudio", importAsAudio)

    companion object {
        fun fromJson(o: JSONObject) = MediaDownloadTask(
            url = o.optString("url"),
            title = o.optString("title"),
            audioOnly = o.optBoolean("audioOnly"),
            videoQuality = o.optInt("quality", 720),
            skipIfExists = o.optBoolean("skip", true),
            importAsAudio = o.optBoolean("asAudio"),
        )

        fun listToJson(tasks: List<MediaDownloadTask>): String =
            JSONArray().apply { tasks.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(raw: String?): List<MediaDownloadTask> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::fromJson) }
            }.getOrDefault(emptyList())
        }
    }
}

/** Что показывает экран загрузки, пока файлы качаются в фоновом сервисе. */
data class MediaDownloadState(
    val running: Boolean = false,
    val statusText: String = "",
    /** Проценты текущего файла; отрицательное значение — прогресс неизвестен. */
    val progress: Float = -1f,
    /** Название файла, который качается прямо сейчас. */
    val currentTitle: String = "",
    /** Последний успешно скачанный файл — чтобы было видно, что именно уже сохранено. */
    val lastCompleted: String? = null,
    val index: Int = 0,
    val total: Int = 0,
    val downloaded: Int = 0,
    val skipped: Int = 0,
    val failures: List<String> = emptyList(),
    val finishedMessage: String? = null,
    val error: String? = null,
) {
    /** Сколько файлов ещё в очереди после текущего. */
    val remaining: Int get() = (total - index).coerceAtLeast(0)
}

/**
 * Общая точка наблюдения: [com.example.bible.service.MediaDownloadService] пишет, экран читает.
 * Живёт вне composition, поэтому загрузка продолжается после выхода с экрана и из приложения.
 */
object MediaDownloadQueue {

    private val _state = MutableStateFlow(MediaDownloadState())
    val state: StateFlow<MediaDownloadState> = _state.asStateFlow()

    fun update(block: (MediaDownloadState) -> MediaDownloadState) {
        _state.value = block(_state.value)
    }

    fun clearResult() {
        _state.value = _state.value.copy(
            finishedMessage = null,
            error = null,
            failures = emptyList(),
        )
    }
}
