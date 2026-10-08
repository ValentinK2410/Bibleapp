package com.example.bible.data

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Санскритское чтение глав (Т. С. Ранганатхан, Internet Archive).
 * Файл 01 — вступительные шлоки, главы 1…18 лежат в файлах 02…19.
 */
object GitaAudio {
    private const val ARCHIVE =
        "https://archive.org/download/SrimadBhagavadGita_201712/Srimad%20Bhagavad%20Gita"

    private val chapterFiles = listOf(
        "02 Arjuna Vishada Yogam.mp3",
        "03 Sankhya Yogam.mp3",
        "04 Karma Yogam.mp3",
        "05 Gnana Karma Sanyasa Yogam.mp3",
        "06 Sanyasa Yogam.mp3",
        "07 Dhyana Yogam.mp3",
        "08 Gnana Vignana Yogam.mp3",
        "09 Akshara Brahma Yogam.mp3",
        "10 Raja Vidhya Raja Guhya Yogam.mp3",
        "11 Vibhoodhi Yogam.mp3",
        "12 Vishwaroopa Darsana Yogam.mp3",
        "13 Bhakthi Yogam.mp3",
        "14 Kshetra Kshetragna Vibhaga Yogam.mp3",
        "15 Gunatraya Vibhaga Yogam.mp3",
        "16 Purushothama Yogam.mp3",
        "17 Dhaivasura Sampath Vibhaga Yogam.mp3",
        "18 Sraddhatraya Vibhaga Yogam.mp3",
        "19 Moksha Sanyasa Yogam.mp3",
    )

    fun localFile(context: Context, chapterId: Int): File =
        File(context.filesDir, "gita_audio/chapter_$chapterId.mp3")

    fun isReady(context: Context, chapterId: Int): Boolean {
        val file = localFile(context, chapterId)
        return file.exists() && file.length() > 1024
    }

    fun readyCount(context: Context): Int =
        (1..18).count { isReady(context, it) }

    suspend fun downloadChapter(context: Context, chapterId: Int): Boolean = withContext(Dispatchers.IO) {
        if (chapterId !in 1..18) return@withContext false
        if (isReady(context, chapterId)) return@withContext true
        val dest = localFile(context, chapterId)
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        try {
            val name = chapterFiles[chapterId - 1].replace(" ", "%20")
            val conn = URL("$ARCHIVE/$name").openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "BibleSqlite")
            conn.connect()
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext false
            }
            conn.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            if (tmp.length() <= 1024) {
                tmp.delete()
                return@withContext false
            }
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
            true
        } catch (_: Exception) {
            tmp.delete()
            false
        }
    }

    fun play(context: Context, chapterId: Int): Boolean {
        val file = localFile(context.applicationContext, chapterId)
        if (!file.exists() || file.length() <= 1024) return false
        stop()
        return try {
            val player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener { stop() }
            player.prepare()
            player.start()
            active = player
            playingChapter = chapterId
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        try {
            active?.stop()
        } catch (_: Exception) {
        }
        try {
            active?.release()
        } catch (_: Exception) {
        }
        active = null
        playingChapter = null
    }

    fun playingChapterId(): Int? = if (active?.isPlaying == true) playingChapter else null

    private var active: MediaPlayer? = null
    private var playingChapter: Int? = null
}
