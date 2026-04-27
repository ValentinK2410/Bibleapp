package com.example.bible.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Вход для «маршрутизации» сигнала датчиков к озвучке, тону, цвету, графику, фонарю.
 * Порядок значений enum совпадает со списком на экране.
 */
enum class SensorLabInput(val id: Int) {
    MicRms(0),
    LightLux(1),
    AccelMagnitude(2),
    GyroMagnitude(3),
    LinearAccelMagnitude(4),
    GravityMagnitude(5),
    MagnetMagnitude(6),
    CompassAzimuth(7),
    PressureHpa(8),
    Proximity(9),
    RelHumidity(10),
    AmbientTemp(11),
    StepCounter(12),
    ;

    companion object {
        val entriesList: List<SensorLabInput> = enumValues<SensorLabInput>().toList()
        fun fromId(id: Int): SensorLabInput = enumValues<SensorLabInput>().getOrNull(id) ?: MicRms
    }
}

object SensorLabInputMapping {

    fun toUnit01(
        input: SensorLabInput,
        micDb: Float,
        light: Float,
        accel: Triple<Float, Float, Float>,
        gyro: Triple<Float, Float, Float>,
        linearAcc: Triple<Float, Float, Float>,
        gravity: Triple<Float, Float, Float>,
        magnet: Triple<Float, Float, Float>,
        compassAzimuthDeg: Float,
        pressureHpa: Float,
        proxRaw: Float,
        relHumidity: Float,
        ambientTempC: Float,
        stepsSinceBoot: Float?,
    ): Float = when (input) {
        SensorLabInput.MicRms -> micDb.coerceIn(0f, 1f)
        SensorLabInput.LightLux -> {
            val l = if (light <= 0f) 0f else kotlin.math.ln(1f + light) / kotlin.math.ln(1f + 30_000f)
            l.coerceIn(0f, 1f)
        }
        SensorLabInput.AccelMagnitude -> mag01(accel, maxMag = 25f)
        SensorLabInput.GyroMagnitude -> mag01(gyro, maxMag = 12f)
        SensorLabInput.LinearAccelMagnitude -> mag01(linearAcc, maxMag = 25f)
        SensorLabInput.GravityMagnitude -> mag01(gravity, maxMag = 12f)
        SensorLabInput.MagnetMagnitude -> mag01(magnet, maxMag = 100f)
        SensorLabInput.CompassAzimuth -> (compassAzimuthDeg / 360f).coerceIn(0f, 1f)
        SensorLabInput.PressureHpa -> {
            val p = pressureHpa
            if (p <= 0f) 0.5f
            else ((p - 960f) / 120f).coerceIn(0f, 1f)
        }
        SensorLabInput.Proximity -> {
            val t = (proxRaw * 0.1f)
            t.coerceIn(0f, 1f)
        }
        SensorLabInput.RelHumidity -> (relHumidity / 100f).coerceIn(0f, 1f)
        SensorLabInput.AmbientTemp -> ((ambientTempC + 10f) / 50f).coerceIn(0f, 1f)
        SensorLabInput.StepCounter -> {
            val s = stepsSinceBoot ?: 0f
            val frac = s - 1000f * floor(s / 1000f)
            (frac / 1000f).coerceIn(0f, 1f)
        }
    }

    private fun mag01(
        t: Triple<Float, Float, Float>,
        maxMag: Float,
    ): Float {
        val m = hypot(t.first, hypot(t.second, t.third))
        return tanh(m / (maxMag * 0.4f)) // мягкое сжатие
    }
}

/**
 * Частота тона для сопоставления: нижняя граница в среднечастотном диапазоне — на тонких динамиках
 * низ (≈200 Гц) и тихий уровень (малые нормировки вроде 0.2) почти не слышны; здесь 450–2000+ Гц.
 */
fun sensorLabToneFrequencyHz(norm01: Float): Float {
    val n = norm01.coerceIn(0f, 1f)
    return 450f + 1650f * n
}

/**
 * Амплитуда PCM для синуса: достаточно громко при типичной громкости «Медиа» на телефоне.
 */
private const val TONE_SINE_AMP = 0.68f

/**
 * Потоковый тон (синус) с изменяемой частотой — для визуализации «тона = датчик».
 */
private const val TAG_TONE = "SensorLabTone"

class SensorLabToneSynthesizer(
    private val appContext: Context,
) {
    @Volatile
    var frequencyHz: Float = 220f
        set(value) {
            field = value.coerceIn(40f, 4000f)
        }

    @Volatile
    private var running: Boolean = false
    private var track: AudioTrack? = null
    private var playThread: Thread? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    // Не останавливаем тон при LOSS (озвучка/TTS в том же разделе иначе глушит канал).
    @Suppress("DEPRECATION")
    private val legacyFocusListener: AudioManager.OnAudioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { }

    private fun requestStreamAudioFocus() {
        if (audioManager == null) {
            audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioFocusRequest = req
            }
            val r = am.requestAudioFocus(audioFocusRequest!!)
            if (r != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG_TONE, "requestAudioFocus returned $r (playing anyway)")
            }
        } else {
            @Suppress("DEPRECATION")
            val r = am.requestAudioFocus(
                legacyFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
            if (r != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG_TONE, "requestAudioFocus (legacy) returned $r (playing anyway)")
            }
        }
    }

    private fun abandonStreamAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = audioFocusRequest
            if (req != null) {
                runCatching { am.abandonAudioFocusRequest(req) }
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { am.abandonAudioFocus(legacyFocusListener) }
        }
    }

    @Synchronized
    fun start() {
        if (running) return
        requestStreamAudioFocus()
        val sampleRate = 44_100
        val ch = AudioFormat.CHANNEL_OUT_MONO
        val enc = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = try {
            AudioTrack.getMinBufferSize(sampleRate, ch, enc)
        } catch (_: Exception) {
            0
        }
        if (minBuf <= 0) {
            Log.w(TAG_TONE, "getMinBufferSize failed")
            abandonStreamAudioFocus()
            return
        }
        val bufBytes = maxOf(minBuf * 2, 16_384)
        val t: AudioTrack = try {
            // Порядок и без LOW_LATENCY — как в рабочем SineTonePlayer; у части девайсов low-latency ломал поток.
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(enc)
                        .setSampleRate(sampleRate)
                        .setChannelMask(ch)
                        .build(),
                )
                .setBufferSizeInBytes(bufBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.w(TAG_TONE, "AudioTrack build failed", e)
            abandonStreamAudioFocus()
            return
        }
        if (t.state != AudioTrack.STATE_INITIALIZED) {
            Log.w(TAG_TONE, "AudioTrack not initialized")
            runCatching { t.release() }
            abandonStreamAudioFocus()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            t.setVolume(1f)
        }
        try {
            t.play()
        } catch (e: Exception) {
            Log.w(TAG_TONE, "AudioTrack play failed", e)
            runCatching { t.release() }
            abandonStreamAudioFocus()
            return
        }
        track = t
        running = true
        val buf = ShortArray(1024)
        val srD = sampleRate.toDouble()
        val th = Thread(
            {
                var phase = 0.0
                while (running) {
                    try {
                        val f = frequencyHz.toDouble()
                        for (i in buf.indices) {
                            val s = sin(phase) * TONE_SINE_AMP
                            buf[i] = (s * Short.MAX_VALUE).toInt().toShort()
                            phase += (Math.PI * 2.0) * f / srD
                        }
                        var off = 0
                        val tr: AudioTrack? = track
                        if (tr == null) break
                        while (off < buf.size && running) {
                            val w = tr.write(buf, off, buf.size - off)
                            if (w < 0) {
                                Log.w(TAG_TONE, "write failed: $w")
                                break
                            }
                            if (w == 0) {
                                try {
                                    Thread.sleep(2)
                                } catch (_: InterruptedException) {
                                    break
                                }
                                continue
                            }
                            off += w
                        }
                    } catch (e: Exception) {
                        Log.w(TAG_TONE, "synth loop", e)
                        break
                    }
                }
            },
            "SensorLabTone",
        )
        playThread = th
        th.priority = android.os.Process.THREAD_PRIORITY_AUDIO
        th.start()
    }

    @Synchronized
    fun stop() {
        running = false
        val joinMs = 600L
        runCatching { playThread?.join(joinMs) }
        playThread = null
        val tr = track
        track = null
        runCatching {
            tr?.stop()
            tr?.release()
        }
        abandonStreamAudioFocus()
    }
}
