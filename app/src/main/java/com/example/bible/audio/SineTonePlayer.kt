package com.example.bible.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.bible.music.MusicTheoryUtils
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TAG = "SineTonePlayer"

private const val SAMPLE_RATE = 44100

/**
 * Воспроизведение для раздела «Ноты».
 * [NoteTimbre.SINE] — синтез; пианино и смычок — записанные сэмплы ([InstrumentSamplePlayer]), при сбое загрузки — запасной синтез.
 */
object SineTonePlayer {

    private val executor = Executors.newSingleThreadExecutor()

    private val sustainActive = AtomicBoolean(false)
    private var sustainThread: Thread? = null

    private val sustainUsesSamples = AtomicBoolean(false)

    @Volatile
    private var instrumentContext: Context? = null

    @Volatile
    private var sustainHz: Double = 440.0

    @Volatile
    private var sustainTimbre: NoteTimbre = NoteTimbre.SINE

    private var sustainSampleIdx: Long = 0L

    /** Вызовите из экрана «Ноты» (Application context), чтобы пианино/скрипка играли сэмплами. */
    fun bindInstrumentSampleContext(context: Context) {
        instrumentContext = context.applicationContext
    }

    /**
     * Непрерывный тон (песочница: удержание и перетаскивание по стану).
     */
    fun startSustain(midi: Int, volume: Float = 0.45f, timbre: NoteTimbre = NoteTimbre.SINE) {
        stopSustain()
        val m = midi.coerceIn(0, 127)
        val vol = volume.coerceIn(0.12f, 0.95f)
        val ctx = instrumentContext
        if (timbre != NoteTimbre.SINE && ctx != null) {
            InstrumentSamplePlayer.ensureLoaded(ctx)
            if (InstrumentSamplePlayer.isReady()) {
                sustainUsesSamples.set(true)
                InstrumentSamplePlayer.startSustain(ctx, m, timbre, vol)
                return
            }
        }
        sustainUsesSamples.set(false)
        sustainHz = MusicTheoryUtils.hzFromMidi(m)
        sustainTimbre = timbre
        sustainSampleIdx = 0L
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) {
            Log.w(TAG, "getMinBufferSize failed (sustain)")
            return
        }
        val bufSize = maxOf(minBuf, 8192)
        val track = try {
            AudioTrack.Builder()
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
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack sustain build failed", e)
            return
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.w(TAG, "AudioTrack sustain not initialized")
            track.release()
            return
        }
        sustainActive.set(true)
        val amp = 32767.0 * vol
        val chunk = 512
        val buffer = ShortArray(chunk)
        val thread = Thread({
            try {
                track.play()
                while (sustainActive.get()) {
                    for (i in 0 until chunk) {
                        val hz = sustainHz
                        val t = sustainSampleIdx / SAMPLE_RATE.toDouble()
                        sustainSampleIdx++
                        val raw = synthesizeSample(t, hz, SAMPLE_RATE, sustainTimbre)
                        buffer[i] = (amp * raw).roundToInt().coerceIn(-32768, 32767).toShort()
                    }
                    if (!sustainActive.get()) break
                    var offset = 0
                    while (offset < chunk && sustainActive.get()) {
                        val written = track.write(buffer, offset, chunk - offset)
                        if (written <= 0) {
                            Log.w(TAG, "sustain write returned $written")
                            break
                        }
                        offset += written
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "sustain loop failed", e)
            } finally {
                try {
                    track.stop()
                } catch (_: Exception) {
                }
                track.release()
            }
        }, "SineSustain")
        sustainThread = thread
        thread.start()
    }

    fun setSustainMidi(midi: Int) {
        if (sustainUsesSamples.get()) {
            InstrumentSamplePlayer.setSustainMidi(midi)
        } else {
            sustainHz = MusicTheoryUtils.hzFromMidi(midi.coerceIn(0, 127))
        }
    }

    fun stopSustain() {
        InstrumentSamplePlayer.stopSustain()
        sustainUsesSamples.set(false)
        sustainActive.set(false)
        sustainThread?.interrupt()
        try {
            sustainThread?.join(400)
        } catch (_: InterruptedException) {
        }
        sustainThread = null
    }

    fun playMidiNote(
        midi: Int,
        durationMs: Int,
        volume: Float = 0.45f,
        timbre: NoteTimbre = NoteTimbre.SINE,
    ) {
        val m = midi.coerceIn(0, 127)
        val dur = durationMs.coerceIn(40, 4000)
        val vol = volume.coerceIn(0.12f, 0.95f)
        executor.execute {
            playOrSampleNote(m, dur, vol, timbre)
        }
    }

    fun playMidiNoteBlocking(
        midi: Int,
        durationMs: Int,
        volume: Float = 0.45f,
        timbre: NoteTimbre = NoteTimbre.SINE,
    ) {
        val m = midi.coerceIn(0, 127)
        val dur = durationMs.coerceIn(40, 4000)
        val vol = volume.coerceIn(0.12f, 0.95f)
        playOrSampleNote(m, dur, vol, timbre)
    }

    private fun playOrSampleNote(midi: Int, durationMs: Int, volume: Float, timbre: NoteTimbre) {
        val ctx = instrumentContext
        if (timbre != NoteTimbre.SINE && ctx != null) {
            InstrumentSamplePlayer.ensureLoaded(ctx)
            if (InstrumentSamplePlayer.isReady()) {
                InstrumentSamplePlayer.playNoteBlocking(ctx, midi, durationMs, timbre, volume)
                return
            }
        }
        playHz(MusicTheoryUtils.hzFromMidi(midi), durationMs, volume, timbre)
    }

    private fun maxHarmonics(hz: Double, sampleRate: Int): Int {
        if (hz <= 0) return 1
        return (sampleRate / (2.0 * hz)).toInt().coerceIn(1, 12)
    }

    /** Значение сэмпла −1..1 без огибающей. */
    private fun synthesizeSample(
        tSec: Double,
        hz: Double,
        sampleRate: Int,
        timbre: NoteTimbre,
    ): Double {
        val maxK = maxHarmonics(hz, sampleRate)
        return when (timbre) {
            NoteTimbre.SINE -> sin(2.0 * PI * hz * tSec)
            NoteTimbre.PIANO -> {
                var s = 0.0
                var norm = 0.0
                for (k in 1..maxK) {
                    val w = 1.0 / (k * k)
                    norm += w
                    s += w * sin(2.0 * PI * hz * k * tSec)
                }
                (s / norm).coerceIn(-1.0, 1.0)
            }
            NoteTimbre.VIOLIN -> {
                var s = 0.0
                var norm = 0.0
                for (k in 1..maxK) {
                    val w = if (k % 2 == 1) 1.0 / k else 0.35 / k
                    norm += w
                    s += w * sin(2.0 * PI * hz * k * tSec)
                }
                val vib = 1.0 + 0.004 * sin(2.0 * PI * 5.0 * tSec)
                ((s / norm) * vib).coerceIn(-1.0, 1.0)
            }
        }
    }

    private fun playHz(frequencyHz: Double, durationMs: Int, volume: Float, timbre: NoteTimbre) {
        stopSustain()
        val numSamples = SAMPLE_RATE * durationMs / 1000
        if (numSamples <= 0) return
        val buffer = ShortArray(numSamples)
        val amp = 32767.0 * volume
        val durSec = durationMs / 1000.0
        for (i in 0 until numSamples) {
            val t = i / SAMPLE_RATE.toDouble()
            val raw = synthesizeSample(t, frequencyHz, SAMPLE_RATE, timbre)
            val env = when (timbre) {
                NoteTimbre.SINE -> {
                    val fadeIn = min(1.0, i / (0.005 * SAMPLE_RATE))
                    val fadeOut = if (i > numSamples - 80) (numSamples - i) / 80.0 else 1.0
                    fadeIn * fadeOut
                }
                NoteTimbre.PIANO -> {
                    val attack = min(1.0, t / 0.008)
                    val decay = exp(-3.0 * t / durSec.coerceAtLeast(0.05))
                    val tail = if (i > numSamples - 120) (numSamples - i) / 120.0 else 1.0
                    attack * decay * tail
                }
                NoteTimbre.VIOLIN -> {
                    val attack = min(1.0, t / 0.055)
                    val tail = if (i > numSamples - 140) (numSamples - i) / 140.0 else 1.0
                    attack * tail
                }
            }
            val s = amp * env * raw
            buffer[i] = s.roundToInt().coerceIn(-32768, 32767).toShort()
        }
        val bytesTotal = buffer.size * 2
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) {
            Log.w(TAG, "getMinBufferSize failed")
            return
        }
        val bufSize = maxOf(minBuf, bytesTotal, 4096)
        val track = try {
            AudioTrack.Builder()
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
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack build failed", e)
            return
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.w(TAG, "AudioTrack not initialized")
            track.release()
            return
        }
        try {
            track.play()
            var offset = 0
            val samples = buffer.size
            while (offset < samples) {
                val written = track.write(buffer, offset, samples - offset)
                if (written <= 0) {
                    Log.w(TAG, "AudioTrack.write returned $written")
                    break
                }
                offset += written
            }
            Thread.sleep(durationMs.toLong() + 100L)
        } catch (e: Exception) {
            Log.e(TAG, "play failed", e)
        } finally {
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
        }
    }
}
