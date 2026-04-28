package com.example.bible.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

/** Тафсир ас-Саади (рус.) и Ибн Касира (араб.), данные spa5k/tafsir_api (Quran.com). */
object QuranTafsirApi {

    private const val BASE =
        "https://cdn.jsdelivr.net/gh/spa5k/tafsir_api@main/tafsir"
    private const val SAADI_SLUG = "ru-tafseer-al-saddi"
    private const val IBN_KATHIR_AR_SLUG = "ar-tafsir-ibn-kathir"
    private const val CONNECT_TIMEOUT_MS = 22_000
    private const val READ_TIMEOUT_MS = 45_000

    suspend fun fetchSaadiRussian(surah: Int, ayah: Int): String? =
        fetchTafsirJsonText("$BASE/$SAADI_SLUG/$surah/$ayah.json")

    suspend fun fetchIbnKathirArabic(surah: Int, ayah: Int): String? =
        fetchTafsirJsonText("$BASE/$IBN_KATHIR_AR_SLUG/$surah/$ayah.json")

    private suspend fun fetchTafsirJsonText(urlStr: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val raw = reader.readText()
                    val jo = JSONObject(raw)
                    jo.optString("text").trim().takeIf { it.isNotEmpty() }
                }
            } catch (_: Exception) {
                null
            }
        }
}

/**
 * Подсказочный перевод арабского на русский для песочницы (внешний бесплатный API).
 * Нужен интернет; результат не является толкованием Корана.
 */
object QuranArabicSandboxTranslateApi {

    private const val CONNECT_TIMEOUT_MS = 14_000
    private const val READ_TIMEOUT_MS = 22_000
    private const val HTTP_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) BibleApp QuranSandboxTranslate/1.0"

    suspend fun translateArToRu(arabic: String): String? =
        myMemoryTranslate(arabic.trim(), "ar|ru")

    private suspend fun myMemoryTranslate(query: String, langpair: String): String? =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext null
            try {
                val enc = URLEncoder.encode(q, Charsets.UTF_8.name())
                val url = URL("https://api.mymemory.translated.net/get?q=$enc&langpair=$langpair")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", HTTP_USER_AGENT)
                    setRequestProperty("Accept", "application/json")
                }
                val code = conn.responseCode
                val stream =
                    if (code in 200..299) {
                        conn.inputStream
                    } else {
                        conn.errorStream ?: return@withContext null
                    }
                stream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val jo = JSONObject(reader.readText())
                    if (jo.optBoolean("quotaFinished")) return@withContext null
                    val rd = jo.optJSONObject("responseData") ?: return@withContext null
                    val t = rd.optString("translatedText").trim()
                    if (t.isEmpty()) return@withContext null
                    if (t.contains("MYMEMORY WARNING", ignoreCase = true)) return@withContext null
                    t
                }
            } catch (_: Exception) {
                null
            }
        }
}

/** Аудио аята: alquran.cloud → islamic.network (чтец ar.alafasy, чаще — Мишари аль-Афаси). */
object QuranAyahAudioApi {

    private const val CONNECT_TIMEOUT_MS = 18_000
    private const val READ_TIMEOUT_MS = 30_000

    private const val HTTP_USER_AGENT =
        "BibleApp/1.0 (Android; QuranAyah; +https://alquran.cloud)"

    /**
     * Список URL для потока (основной + [audioSecondary]). Пустой — сеть/API недоступны или нет данных.
     */
    suspend fun fetchAlafasyAudioUrls(surah: Int, ayah: Int): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.alquran.cloud/v1/ayah/$surah:$ayah/ar.alafasy")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", HTTP_USER_AGENT)
                    setRequestProperty("Accept", "application/json")
                }
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val root = JSONObject(reader.readText())
                    if (root.optInt("code") != 200) return@withContext emptyList()
                    val data = root.getJSONObject("data")
                    val out = LinkedHashSet<String>()
                    data.optString("audio").trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
                    val sec = data.optJSONArray("audioSecondary")
                    if (sec != null) {
                        for (i in 0 until sec.length()) {
                            sec.optString(i).trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
                        }
                    }
                    out.toList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    suspend fun fetchAlafasyAudioUrl(surah: Int, ayah: Int): String? =
        fetchAlafasyAudioUrls(surah, ayah).firstOrNull()
}

/**
 * Воспроизведение потокового MP3 аята. Один экземпляр на экран читалки.
 */
class QuranAyahStreamingPlayer(context: Context) {

    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var prepareTimeoutRunnable: Runnable? = null

    private val _activeAyahKey = MutableStateFlow<Pair<Int, Int>?>(null)
    /** Какой аят сейчас проигрывается MP3 (сура, аят); null — нет воспроизведения. */
    val activeAyahKey: StateFlow<Pair<Int, Int>?> = _activeAyahKey.asStateFlow()

    /** Заголовки для CDN: без User-Agent часть устройств получает пустой ответ / сбой MediaPlayer. */
    private val streamHeaders: Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile) BibleApp QuranStream/1.0",
    )

    fun stop() {
        runOnMain {
            try {
                player?.stop()
            } catch (_: Exception) {
            }
            releaseInternal()
        }
    }

    fun release() {
        runOnMain { releaseInternal() }
    }

    private fun releaseInternal() {
        prepareTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        prepareTimeoutRunnable = null
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        _activeAyahKey.value = null
    }

    /**
     * Потоковое MP3 по списку URL (основной и запасные битрейты). [onError] — на UI-потоке, если все варианты не удались.
     */
    fun playStreamUrls(urls: List<String>, surah: Int, ayah: Int, onError: () -> Unit) {
        if (urls.isEmpty()) {
            runOnMain { onError() }
            return
        }
        runOnMain { playStreamUrlsOnMain(urls, 0, surah, ayah, onError) }
    }

    /**
     * @param onError вызывается в UI-потоке при сбое подготовки/воспроизведения.
     */
    fun playUrl(surah: Int, ayah: Int, url: String, onError: () -> Unit) {
        playStreamUrls(listOf(url), surah, ayah, onError)
    }

    /** Воспроизведение сохранённого MP3 (абсолютный путь). */
    fun playLocalFile(surah: Int, ayah: Int, absolutePath: String, onError: () -> Unit) {
        runOnMain {
            releaseInternal()
            val mp = try {
                MediaPlayer()
            } catch (_: Exception) {
                onError()
                return@runOnMain
            }
            player = mp
            val terminal = AtomicBoolean(false)
            val timeoutMs = 28_000L
            val timeoutRunnable = Runnable {
                if (terminal.compareAndSet(false, true)) {
                    releaseInternal()
                    onError()
                }
            }
            prepareTimeoutRunnable = timeoutRunnable
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                mp.setAudioAttributes(attrs)
                mp.setDataSource(absolutePath)
            } catch (_: Exception) {
                if (terminal.compareAndSet(false, true)) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    prepareTimeoutRunnable = null
                    releaseInternal()
                    onError()
                }
                return@runOnMain
            }
            mp.setOnPreparedListener {
                if (terminal.compareAndSet(false, true)) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    prepareTimeoutRunnable = null
                    _activeAyahKey.value = surah to ayah
                    it.start()
                } else {
                    runCatching { it.release() }
                }
            }
            mp.setOnCompletionListener {
                mainHandler.removeCallbacks(timeoutRunnable)
                prepareTimeoutRunnable = null
                releaseInternal()
            }
            mp.setOnErrorListener { _, _, _ ->
                if (terminal.compareAndSet(false, true)) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    prepareTimeoutRunnable = null
                    releaseInternal()
                    onError()
                }
                true
            }
            mainHandler.postDelayed(timeoutRunnable, timeoutMs)
            try {
                mp.prepareAsync()
            } catch (_: Exception) {
                if (terminal.compareAndSet(false, true)) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    prepareTimeoutRunnable = null
                    releaseInternal()
                    onError()
                }
            }
        }
    }

    private fun playStreamUrlsOnMain(urls: List<String>, index: Int, surah: Int, ayah: Int, onError: () -> Unit) {
        if (index >= urls.size) {
            onError()
            return
        }
        releaseInternal()
        val url = urls[index]
        val mp = try {
            MediaPlayer()
        } catch (_: Exception) {
            playStreamUrlsOnMain(urls, index + 1, surah, ayah, onError)
            return
        }
        player = mp
        val terminal = AtomicBoolean(false)
        val timeoutMs = 28_000L
        val timeoutRunnable = Runnable {
            if (terminal.compareAndSet(false, true)) {
                releaseInternal()
                playStreamUrlsOnMain(urls, index + 1, surah, ayah, onError)
            }
        }
        prepareTimeoutRunnable = timeoutRunnable
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            mp.setAudioAttributes(attrs)
            mp.setDataSource(appContext, Uri.parse(url), streamHeaders)
        } catch (_: Exception) {
            if (terminal.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutRunnable)
                prepareTimeoutRunnable = null
                releaseInternal()
                playStreamUrlsOnMain(urls, index + 1, surah, ayah, onError)
            }
            return
        }
        mp.setOnPreparedListener {
            if (terminal.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutRunnable)
                prepareTimeoutRunnable = null
                _activeAyahKey.value = surah to ayah
                it.start()
            } else {
                runCatching { it.release() }
            }
        }
        mp.setOnCompletionListener {
            mainHandler.removeCallbacks(timeoutRunnable)
            prepareTimeoutRunnable = null
            releaseInternal()
        }
        mp.setOnErrorListener { _, _, _ ->
            if (terminal.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutRunnable)
                prepareTimeoutRunnable = null
                releaseInternal()
                playStreamUrlsOnMain(urls, index + 1, surah, ayah, onError)
            }
            true
        }
        mainHandler.postDelayed(timeoutRunnable, timeoutMs)
        try {
            mp.prepareAsync()
        } catch (_: Exception) {
            if (terminal.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutRunnable)
                prepareTimeoutRunnable = null
                releaseInternal()
                playStreamUrlsOnMain(urls, index + 1, surah, ayah, onError)
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
