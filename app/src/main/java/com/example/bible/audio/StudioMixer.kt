package com.example.bible.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Все дорожки студии звучат через один канал.
 * Отдельный плеер на каждую запись упирается в предел системы (на этом телефоне около 48).
 */
class StudioMixer {
    var onStopped: (String) -> Unit = {}

    private val main = Handler(Looper.getMainLooper())
    private val decoder = Executors.newFixedThreadPool(2)
    private val lock = Object()
    private val voices = LinkedHashMap<String, Voice>()
    private val pending = HashSet<String>()
    private val token = HashMap<String, Int>()
    private val loopWanted = HashMap<String, Boolean>()
    private val pcmCache = LinkedHashMap<CacheKey, ShortArray>()
    private var pcmBytes = 0

    @Volatile private var closed = false
    @Volatile private var running = false
    private var mixThread: Thread? = null
    private var track: AudioTrack? = null

    fun playWhileHeld(key: String, file: File): Boolean {
        if (closed || !file.exists()) return false
        begin(key, file, loop = false)
        return true
    }

    /** true — запись сейчас включена. */
    fun toggle(key: String, file: File, loop: Boolean): Boolean {
        if (closed || !file.exists()) return false
        val active = synchronized(lock) { key in voices || key in pending }
        if (active) {
            stop(key)
            return false
        }
        begin(key, file, loop)
        return true
    }

    fun setLoop(key: String, loop: Boolean) {
        synchronized(lock) {
            loopWanted[key] = loop
            voices[key]?.loop = loop
        }
    }

    fun stop(key: String) {
        synchronized(lock) {
            token[key] = (token[key] ?: 0) + 1
            voices.remove(key)
            pending.remove(key)
            lock.notifyAll()
        }
    }

    fun release() {
        closed = true
        running = false
        synchronized(lock) {
            voices.clear()
            pending.clear()
            token.clear()
            lock.notifyAll()
        }
        decoder.shutdownNow()
        mixThread?.join(400)
        mixThread = null
        releaseTrack()
    }

    private fun begin(key: String, file: File, loop: Boolean) {
        val ready = peekCache(file)
        if (ready != null) {
            synchronized(lock) {
                token[key] = (token[key] ?: 0) + 1
                pending.remove(key)
                loopWanted[key] = loop
                voices[key] = Voice(ready, 0, loop)
            }
            wake()
            return
        }
        val mine = synchronized(lock) {
            val next = (token[key] ?: 0) + 1
            token[key] = next
            voices.remove(key)
            pending.add(key)
            loopWanted[key] = loop
            next
        }
        decoder.execute {
            val pcm = runCatching { cachedPcm(file) }.getOrNull()
            val started = synchronized(lock) {
                if (closed || token[key] != mine) {
                    pending.remove(key)
                    false
                } else if (pcm == null || pcm.isEmpty()) {
                    pending.remove(key)
                    false
                } else {
                    pending.remove(key)
                    voices[key] = Voice(pcm, 0, loopWanted[key] ?: loop)
                    true
                }
            }
            if (started) {
                wake()
            } else if (!closed && pcm == null) {
                synchronized(lock) {
                    if (token[key] == mine) main.post { onStopped(key) }
                }
            }
        }
    }

    private fun wake() {
        synchronized(lock) {
            if (mixThread?.isAlive != true) {
                running = true
                mixThread = Thread(::mixLoop, "studio-mixer").apply {
                    isDaemon = true
                    start()
                }
            }
            lock.notifyAll()
        }
    }

    private fun mixLoop() {
        val output = openTrack() ?: return
        val frames = FRAME_COUNT
        val mix = IntArray(frames)
        val out = ShortArray(frames)
        while (running) {
            val ended = ArrayList<String>(4)
            var active = false
            synchronized(lock) {
                while (running && voices.isEmpty()) {
                    runCatching { output.pause() }
                    lock.wait(300)
                }
                if (!running || voices.isEmpty()) return@synchronized
                active = true
                for (i in mix.indices) mix[i] = 0
                val iterator = voices.iterator()
                while (iterator.hasNext()) {
                    val (key, voice) = iterator.next()
                    val pcm = voice.pcm
                    var finished = false
                    for (i in 0 until frames) {
                        if (voice.pos >= pcm.size) {
                            if (voice.loop && pcm.isNotEmpty()) voice.pos = 0
                            else {
                                finished = true
                                break
                            }
                        }
                        mix[i] += pcm[voice.pos].toInt()
                        voice.pos++
                    }
                    if (finished) {
                        iterator.remove()
                        pending.remove(key)
                        ended.add(key)
                    }
                }
            }
            if (!active) continue
            var peak = 1
            for (sample in mix) {
                val level = abs(sample)
                if (level > peak) peak = level
            }
            val gain = if (peak <= 32767) 1f else 32767f / peak
            for (i in mix.indices) out[i] = (mix[i] * gain).toInt().toShort()
            if (output.playState != AudioTrack.PLAYSTATE_PLAYING) {
                runCatching { output.play() }
            }
            val wrote = runCatching { output.write(out, 0, frames) }.getOrDefault(-1)
            if (wrote < 0) break
            if (ended.isNotEmpty()) main.post { ended.forEach(onStopped) }
        }
    }

    private fun openTrack(): AudioTrack? {
        track?.let { return it }
        val min = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) return null
        val created = try {
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
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(min.coerceAtLeast(FRAME_COUNT * 2) * 2)
                .build()
        } catch (_: Exception) {
            return null
        }
        if (created.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { created.release() }
            return null
        }
        track = created
        return created
    }

    private fun releaseTrack() {
        val current = track
        track = null
        if (current != null) {
            runCatching { current.pause() }
            runCatching { current.flush() }
            runCatching { current.release() }
        }
    }

    private fun peekCache(file: File): ShortArray? {
        val key = CacheKey(file.absolutePath, file.length(), file.lastModified())
        return synchronized(pcmCache) { pcmCache[key] }
    }

    private fun cachedPcm(file: File): ShortArray? {
        val key = CacheKey(file.absolutePath, file.length(), file.lastModified())
        synchronized(pcmCache) {
            pcmCache[key]?.let { return it }
        }
        val pcm = decode(file) ?: return null
        synchronized(pcmCache) {
            pcmCache[key] = pcm
            pcmBytes += pcm.size * 2
            val used = HashSet<ShortArray>()
            synchronized(lock) { voices.values.forEach { used.add(it.pcm) } }
            val entries = pcmCache.entries.iterator()
            while (pcmBytes > CACHE_BYTES && pcmCache.size > 1 && entries.hasNext()) {
                val entry = entries.next()
                if (entry.value === pcm || entry.value in used) continue
                pcmBytes -= entry.value.size * 2
                entries.remove()
            }
        }
        return pcm
    }

    private fun decode(file: File): ShortArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            val source = format ?: return null
            extractor.selectTrack(trackIndex)
            val mime = source.getString(MediaFormat.KEY_MIME) ?: return null
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(source, null, null, 0)
            decoder.start()
            val chunks = ArrayList<ShortArray>()
            var total = 0
            var sampleRate = SAMPLE_RATE
            var channels = 1
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone && !closed) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)
                        val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = decoder.outputFormat
                        if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                        if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            encoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    else -> if (outIndex >= 0) {
                        val buffer = decoder.getOutputBuffer(outIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val mono = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                val floats = FloatArray(buffer.remaining() / 4)
                                buffer.order(ByteOrder.nativeOrder()).asFloatBuffer().get(floats)
                                downmixFloat(floats, channels)
                            } else {
                                val shorts = ShortArray(buffer.remaining() / 2)
                                buffer.order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts)
                                downmixShort(shorts, channels)
                            }
                            if (mono.isNotEmpty()) {
                                chunks.add(mono)
                                total += mono.size
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            if (total == 0) return null
            val joined = ShortArray(total)
            var position = 0
            for (chunk in chunks) {
                chunk.copyInto(joined, position)
                position += chunk.size
            }
            return resample(joined, sampleRate, SAMPLE_RATE)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun downmixShort(input: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return input
        val frames = input.size / channels
        return ShortArray(frames) { frame ->
            var sum = 0
            for (channel in 0 until channels) sum += input[frame * channels + channel]
            (sum / channels).toShort()
        }
    }

    private fun downmixFloat(input: FloatArray, channels: Int): ShortArray {
        val frames = input.size / channels
        return ShortArray(frames) { frame ->
            var sum = 0f
            for (channel in 0 until channels) sum += input[frame * channels + channel]
            (sum / channels * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun resample(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (srcRate == dstRate || input.isEmpty() || srcRate <= 0) return input
        val outLen = (input.size.toLong() * dstRate / srcRate).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        val step = srcRate.toDouble() / dstRate
        for (i in out.indices) {
            val position = i * step
            val left = position.toInt().coerceIn(0, input.lastIndex)
            val right = (left + 1).coerceAtMost(input.lastIndex)
            val fraction = (position - left).toFloat()
            val sample = input[left] * (1f - fraction) + input[right] * fraction
            out[i] = sample.toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private class Voice(
        val pcm: ShortArray,
        var pos: Int,
        var loop: Boolean,
    )

    private data class CacheKey(val path: String, val length: Long, val modified: Long)

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val FRAME_COUNT = 960
        const val CACHE_BYTES = 64 * 1024 * 1024
    }
}
