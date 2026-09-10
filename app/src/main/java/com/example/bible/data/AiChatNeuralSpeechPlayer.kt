package com.example.bible.data

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Очередь нейро-озвучки через SaluteSpeech: синтез в WAV и воспроизведение через [MediaPlayer].
 */
class AiChatNeuralSpeechPlayer(
    private val appContext: Context,
    private val authKeyProvider: () -> String,
    private val scopeProvider: () -> String,
    private val settingsProvider: () -> AiChatTtsSettings,
    private val systemFallback: (String) -> Unit,
    private val systemStop: () -> Unit,
) {
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var speakJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    fun speak(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        speakJob?.cancel()
        systemStop()
        stopPlayer()
        speakJob = playerScope.launch {
            val settings = settingsProvider()
            val authKey = authKeyProvider()
            if (authKey.isBlank()) {
                systemFallback(trimmed)
                return@launch
            }
            val voice = settings.resolvedNeuralVoice()
            val chunks = splitSpeechChunks(trimmed, maxSize = 3800)
            for ((index, chunk) in chunks.withIndex()) {
                if (!isActive) return@launch
                val result = withContext(Dispatchers.IO) {
                    SaluteSpeechClient.synthesize(
                        authKey = authKey,
                        text = chunk,
                        voice = voice,
                        intonation = settings.intonation,
                        scope = scopeProvider(),
                    )
                }
                if (!isActive) return@launch
                val played = result.fold(
                    onSuccess = { bytes -> playWavAndAwait(bytes) },
                    onFailure = {
                        if (index == 0) {
                            systemFallback(trimmed)
                        }
                        false
                    },
                )
                if (!played) return@launch
            }
        }
    }

    fun preview(text: String) = speak(text)

    fun stop() {
        speakJob?.cancel()
        speakJob = null
        stopPlayer()
    }

    fun release() {
        stop()
        playerScope.cancel()
    }

    private suspend fun playWavAndAwait(bytes: ByteArray): Boolean = suspendCancellableCoroutine { cont ->
        stopPlayer()
        val file = File(appContext.cacheDir, "salute_tts_${System.nanoTime()}.wav")
        runCatching {
            file.writeBytes(bytes)
            val player = MediaPlayer()
            mediaPlayer = player
            cont.invokeOnCancellation {
                runCatching {
                    player.stop()
                    player.release()
                }
                file.delete()
                if (mediaPlayer === player) mediaPlayer = null
            }
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                runCatching { it.release() }
                file.delete()
                if (mediaPlayer === player) mediaPlayer = null
                if (cont.isActive) cont.resume(true)
            }
            player.setOnErrorListener { mp, _, _ ->
                runCatching { mp.release() }
                file.delete()
                if (mediaPlayer === mp) mediaPlayer = null
                if (cont.isActive) cont.resume(false)
                true
            }
            player.prepare()
            player.start()
        }.onFailure {
            file.delete()
            if (cont.isActive) cont.resume(false)
        }
    }

    private fun stopPlayer() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }

    companion object {
        fun splitSpeechChunks(text: String, maxSize: Int): List<String> {
            if (text.length <= maxSize) return listOf(text)
            val result = mutableListOf<String>()
            var remaining = text
            while (remaining.isNotEmpty()) {
                if (remaining.length <= maxSize) {
                    result.add(remaining)
                    break
                }
                var cut = remaining.lastIndexOf('\n', maxSize).takeIf { it > maxSize / 3 }
                    ?: remaining.lastIndexOf('.', maxSize).takeIf { it > maxSize / 3 }
                    ?: remaining.lastIndexOf(' ', maxSize).takeIf { it > maxSize / 4 }
                if (cut == null || cut <= 0) cut = maxSize
                result.add(remaining.substring(0, cut).trim())
                remaining = remaining.substring(cut).trimStart()
            }
            return result
        }
    }
}

fun AiChatTtsSettings.resolvedNeuralVoice(): String =
    voiceName.trim().ifBlank { SaluteSpeechClient.DEFAULT_VOICE }
