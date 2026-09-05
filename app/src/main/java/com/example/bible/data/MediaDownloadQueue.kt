package com.example.bible.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Одна загрузка в фоновой очереди. Пустой [title] — название узнаём у платформы перед скачиванием. */
data class MediaDownloadTask(
    val url: String,
    val title: String,
    val audioOnly: Boolean,
    val videoQuality: Int,
    val skipIfExists: Boolean,
    val importAsAudio: Boolean,
    /** Идентификатор задачи: он же id процесса yt-dlp, по нему ставим на паузу и отменяем. */
    val id: String = UUID.randomUUID().toString(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("url", url)
        .put("title", title)
        .put("audioOnly", audioOnly)
        .put("quality", videoQuality)
        .put("skip", skipIfExists)
        .put("asAudio", importAsAudio)
        .put("id", id)

    companion object {
        fun fromJson(o: JSONObject) = MediaDownloadTask(
            url = o.optString("url"),
            title = o.optString("title"),
            audioOnly = o.optBoolean("audioOnly"),
            videoQuality = o.optInt("quality", 720),
            skipIfExists = o.optBoolean("skip", true),
            importAsAudio = o.optBoolean("asAudio"),
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
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

enum class MediaDownloadItemStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    DONE,
    SKIPPED,
    FAILED,
    ;

    /** Работа по задаче закончена: она больше не займёт слот загрузки. */
    val terminal: Boolean get() = this == DONE || this == SKIPPED || this == FAILED
}

/** Состояние одной загрузки в очереди — то, что видно строкой на экране и в шторке. */
data class MediaDownloadItem(
    val task: MediaDownloadTask,
    val status: MediaDownloadItemStatus = MediaDownloadItemStatus.QUEUED,
    val title: String = task.title,
    /** Проценты текущего файла; отрицательное значение — прогресс ещё неизвестен. */
    val progress: Float = -1f,
    val etaSec: Long = 0,
    val message: String = "",
) {
    val id: String get() = task.id

    /** Что показать пользователю: название, а пока его нет — ссылка. */
    val label: String get() = title.ifBlank { task.title.ifBlank { task.url } }
}

/** Что показывают экран загрузки и уведомление, пока файлы качаются в фоновом сервисе. */
data class MediaDownloadState(
    val items: List<MediaDownloadItem> = emptyList(),
    /** Сколько файлов качаем одновременно. */
    val parallel: Int = DEFAULT_PARALLEL,
    /** Последний успешно скачанный файл — чтобы было видно, что именно уже сохранено. */
    val lastCompleted: String? = null,
    val failures: List<String> = emptyList(),
    val finishedMessage: String? = null,
    val error: String? = null,
) {
    val active: List<MediaDownloadItem>
        get() = items.filter { it.status == MediaDownloadItemStatus.RUNNING }

    val queued: Int get() = items.count { it.status == MediaDownloadItemStatus.QUEUED }
    val paused: Int get() = items.count { it.status == MediaDownloadItemStatus.PAUSED }
    val downloaded: Int get() = items.count { it.status == MediaDownloadItemStatus.DONE }
    val skipped: Int get() = items.count { it.status == MediaDownloadItemStatus.SKIPPED }
    val failed: Int get() = items.count { it.status == MediaDownloadItemStatus.FAILED }
    val total: Int get() = items.size

    /** Идёт активная работа: есть что качать прямо сейчас или в ближайшую очередь. */
    val running: Boolean get() = active.isNotEmpty() || queued > 0

    /** В очереди ещё есть задачи, пусть даже все на паузе, — экран показывает панель загрузки. */
    val hasWork: Boolean get() = running || paused > 0

    /** Сколько файлов ещё предстоит скачать, включая те, что качаются сейчас. */
    val remaining: Int get() = queued + paused + active.size

    /** Средний прогресс активных загрузок; отрицательное значение — неизвестен. */
    val progress: Float
        get() {
            val known = active.map { it.progress }.filter { it >= 0f }
            return if (known.isEmpty()) -1f else known.sum() / known.size
        }

    /** Общий прогресс очереди с учётом уже завершённых файлов. */
    val overallProgress: Float
        get() {
            if (items.isEmpty()) return -1f
            val done = items.count { it.status.terminal } * 100f
            val current = active.sumOf { it.progress.coerceAtLeast(0f).toDouble() }.toFloat()
            return ((done + current) / items.size).coerceIn(0f, 100f)
        }

    val currentTitle: String get() = active.firstOrNull()?.label.orEmpty()

    companion object {
        const val DEFAULT_PARALLEL = 2
        const val MAX_PARALLEL = 3
    }
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

    /** Точечное изменение одной строки очереди: остальные загрузки не трогаем. */
    fun updateItem(id: String, block: (MediaDownloadItem) -> MediaDownloadItem) {
        _state.value = _state.value.copy(
            items = _state.value.items.map { if (it.id == id) block(it) else it },
        )
    }

    fun item(id: String): MediaDownloadItem? = _state.value.items.firstOrNull { it.id == id }

    fun removeItem(id: String) {
        _state.value = _state.value.copy(items = _state.value.items.filterNot { it.id == id })
    }

    fun clearResult() {
        _state.value = _state.value.copy(
            finishedMessage = null,
            error = null,
            failures = emptyList(),
        )
    }

    /** Убирает из списка уже завершённые строки — очередь остаётся только с живыми задачами. */
    fun clearFinished() {
        _state.value = _state.value.copy(
            items = _state.value.items.filterNot { it.status.terminal },
            finishedMessage = null,
            error = null,
            failures = emptyList(),
            lastCompleted = null,
        )
    }
}
