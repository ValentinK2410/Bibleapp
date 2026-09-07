package com.example.bible.data

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Короткое прослушивание образца озвучки перед массовой загрузкой. */
object BibleAudioPreviewPlayer {

    private const val PREVIEW_MS = 28_000

    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var stopRunnable: Runnable? = null

    private val _playingNarratorId = MutableStateFlow<String?>(null)
    val playingNarratorId: StateFlow<String?> = _playingNarratorId.asStateFlow()

    fun previewSample(context: Context, narratorId: String, onError: (String) -> Unit) {
        if (_playingNarratorId.value == narratorId) {
            stop()
            return
        }
        stop()
        BibleAudioPlayer.stopForNavigation()

        val narrator = BibleAudioNarrators.byId(narratorId)
        val (bookId, chapter) = previewChapterForNarrator(narrator)
        val local = localAudioFile(context, narrator.id, bookId, chapter)
        val bundled = openBundledBibleChapterAudio(context, narrator.id, bookId, chapter)
        val remoteUrl = when {
            local.exists() && local.length() > 1024 -> null
            bundled != null -> null
            narrator.id == "web" -> webChapterAudioUrl(bookId, chapter)
            narrator.id == "hebrew-ot" -> hebrewOtChapterAudioUrl(bookId, chapter)
            narrator.id == "greek-nt" -> greekNtChapterAudioUrl(bookId, chapter)
            else -> chapterAudioUrl(narrator, bookId, chapter)
        }

        if (!local.exists() && bundled == null && remoteUrl.isNullOrBlank()) {
            onError("Образец недоступен")
            return
        }

        try {
            val mp = MediaPlayer()
            player = mp
            when {
                local.exists() && local.length() > 1024 -> mp.setDataSource(local.absolutePath)
                bundled != null -> bundled.use {
                    mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                else -> mp.setDataSource(remoteUrl!!)
            }
            mp.setOnPreparedListener { prepared ->
                if (player !== prepared) return@setOnPreparedListener
                _playingNarratorId.value = narratorId
                prepared.start()
                val r = Runnable { stop() }
                stopRunnable = r
                mainHandler.postDelayed(r, PREVIEW_MS.toLong())
            }
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ ->
                stop()
                onError("Не удалось воспроизвести образец")
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            stop()
            onError(e.message ?: "Ошибка воспроизведения")
        }
    }

    fun stop() {
        stopRunnable?.let { mainHandler.removeCallbacks(it) }
        stopRunnable = null
        _playingNarratorId.value = null
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {}
        player = null
    }
}

/** Книга и глава для короткого образца озвучки. */
fun previewChapterForNarrator(narrator: AudioNarrator): Pair<String, Int> =
    when (narrator.id) {
        "greek-nt" -> "matthew" to 1
        else -> "genesis" to 1
    }
