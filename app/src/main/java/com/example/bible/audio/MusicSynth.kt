package com.example.bible.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Короткие звуки для метронома и эталонные тона для тюнера, синтезируются на лету. */
object MusicSynth {
    const val SAMPLE_RATE = 44100

    enum class ClickSound(val label: String) {
        CLASSIC("Классика"),
        WOOD("Дерево"),
        SOFT("Мягкий"),
        DIGITAL("Цифровой"),
    }

    enum class ClickLevel { ACCENT, NORMAL, SUB }

    /** Один щелчок длительностью несколько десятков миллисекунд. */
    fun click(sound: ClickSound, level: ClickLevel): ShortArray {
        val (freq, ms, volume) = when (sound) {
            ClickSound.CLASSIC -> Triple(if (level == ClickLevel.ACCENT) 1760.0 else 1175.0, 28, 0.9)
            ClickSound.WOOD -> Triple(if (level == ClickLevel.ACCENT) 1100.0 else 800.0, 22, 1.0)
            ClickSound.SOFT -> Triple(if (level == ClickLevel.ACCENT) 880.0 else 660.0, 70, 0.75)
            ClickSound.DIGITAL -> Triple(if (level == ClickLevel.ACCENT) 2000.0 else 1500.0, 18, 0.55)
        }
        val gain = volume * when (level) {
            ClickLevel.ACCENT -> 1.0
            ClickLevel.NORMAL -> 0.7
            ClickLevel.SUB -> 0.35
        }
        val n = SAMPLE_RATE * ms / 1000
        val decay = 5.0 / n
        return ShortArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-i * decay) * (if (i < 40) i / 40.0 else 1.0)
            val v = when (sound) {
                ClickSound.CLASSIC -> sin(2 * PI * freq * t)
                ClickSound.WOOD -> 0.6 * sin(2 * PI * freq * t) + 0.4 * sin(2 * PI * freq * 2.76 * t)
                ClickSound.SOFT -> sin(2 * PI * freq * t) * (1 - i.toDouble() / n)
                ClickSound.DIGITAL -> if (sin(2 * PI * freq * t) >= 0) 1.0 else -1.0
            }
            (v * env * gain * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    /** Струнный тон с обертонами, плавным входом и затуханием. */
    fun pluckTone(hz: Double, seconds: Double = 2.2): ShortArray {
        val n = (SAMPLE_RATE * seconds).toInt()
        return ShortArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (i / (SAMPLE_RATE * 0.01)).coerceAtMost(1.0)
            val env = attack * exp(-t * 1.6)
            val v = 0.62 * sin(2 * PI * hz * t) +
                0.25 * sin(2 * PI * hz * 2 * t) * exp(-t * 2.5) +
                0.13 * sin(2 * PI * hz * 3 * t) * exp(-t * 3.5)
            (v * env * 0.85 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    @Volatile
    private var toneTrack: AudioTrack? = null

    /** Проигрывает эталонную ноту; новый вызов обрывает предыдущий тон. */
    fun playReference(hz: Double) {
        val pcm = pluckTone(hz)
        stopReference()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.play()
        toneTrack = track
    }

    fun stopReference() {
        toneTrack?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        toneTrack = null
    }

    fun newStreamTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuf.coerceAtLeast(2048))
            .build()
    }
}
