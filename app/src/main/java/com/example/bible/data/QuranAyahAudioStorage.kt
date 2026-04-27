package com.example.bible.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Локальное сохранение MP3 чтения аята (ar.alafasy) для офлайн-прослушивания.
 * Файлы в [Context.getFilesDir]/quran_audio/.
 */
object QuranAyahAudioStorage {

    private const val MIN_VALID_BYTES = 512L
    private const val CONNECT_MS = 25_000
    private const val READ_MS = 120_000

    private const val DL_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) BibleApp QuranAyahDownload/1.0"

    private fun dir(ctx: Context): File =
        File(ctx.filesDir, "quran_audio").also { it.mkdirs() }

    /** Готовый файл аята или null. */
    fun localFileIfReady(ctx: Context, surah: Int, ayah: Int): File? {
        val f = File(dir(ctx), "alafasy_${surah}_${ayah}.mp3")
        return if (f.isFile && f.length() >= MIN_VALID_BYTES) f else null
    }

    fun isDownloaded(ctx: Context, surah: Int, ayah: Int): Boolean =
        localFileIfReady(ctx, surah, ayah) != null

    /** Все аяты с номерами от 1 до [ayahCount] для суры [surah] есть в кэше. */
    fun isSurahFullyCached(ctx: Context, surah: Int, ayahCount: Int): Boolean {
        if (ayahCount <= 0) return false
        for (ayah in 1..ayahCount) {
            if (!isDownloaded(ctx, surah, ayah)) return false
        }
        return true
    }

    /**
     * Скачивает первый удачный URL из [QuranAyahAudioApi.fetchAlafasyAudioUrls].
     * @return true если файл записан и не пустой.
     */
    suspend fun downloadAyah(ctx: Context, surah: Int, ayah: Int): Boolean =
        withContext(Dispatchers.IO) {
            val urls = QuranAyahAudioApi.fetchAlafasyAudioUrls(surah, ayah)
            if (urls.isEmpty()) return@withContext false
            val baseDir = dir(ctx)
            val target = File(baseDir, "alafasy_${surah}_${ayah}.mp3")
            val temp = File(baseDir, "${target.name}.part")
            for (urlStr in urls) {
                try {
                    runCatching { temp.delete() }
                    val url = URL(urlStr)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = CONNECT_MS
                        readTimeout = READ_MS
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", DL_USER_AGENT)
                        setRequestProperty("Accept", "*/*")
                    }
                    conn.inputStream.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (temp.length() < MIN_VALID_BYTES) {
                        temp.delete()
                        continue
                    }
                    if (target.exists()) target.delete()
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                    return@withContext true
                } catch (_: Exception) {
                    runCatching { temp.delete() }
                }
            }
            false
        }

    /**
     * Скачивает MP3 (ar.alafasy) для всех аятов всех сур, где есть тексты в [repository].
     * Уже существующие в кэше файлы пропускаются (сеть не трогаем).
     * [onProgress] вызывается не на каждом аяте (примерно раз в 8), и на [ensureActive] при отмене.
     * @return сколько аятов в кэше после прохода (с учётом уже лежащих и новых).
     */
    suspend fun downloadEntireQuranAlafasyMp3(
        ctx: Context,
        repository: QuranRepository,
        onProgress: suspend (processed: Int, total: Int, savedInCache: Int) -> Unit = { _, _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        val index = repository.loadIndex() ?: return@withContext 0
        val total = index.sumOf { it.totalVerses }
        if (total <= 0) return@withContext 0
        var savedInCache = 0
        var processed = 0
        for (s in index) {
            val content = repository.loadSurah(s.number) ?: continue
            for (v in content.verses) {
                ensureActive()
                if (isDownloaded(ctx, s.number, v.number)) {
                    savedInCache++
                } else if (downloadAyah(ctx, s.number, v.number)) {
                    savedInCache++
                }
                processed++
                if (processed % 8 == 0 || processed == total) {
                    onProgress(processed, total, savedInCache)
                }
            }
        }
        onProgress(processed, total, savedInCache)
        savedInCache
    }
}
