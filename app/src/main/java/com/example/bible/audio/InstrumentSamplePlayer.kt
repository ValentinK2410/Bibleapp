package com.example.bible.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.example.bible.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.pow

private const val TAG = "InstrumentSamplePlayer"

/**
 * Записанные сэмплы (не синтез): пианино и смычковый тембр.
 *
 * Пианино: Salamander Grand Piano (Alexander Holm), CC-BY 3.0 — [dough-samples](https://github.com/felixroos/dough-samples).
 *
 * «Скрипка»: сэмплы виолончели (смычок arco), Philharmonia — [philharmonia-samples](https://github.com/skratchdot/philharmonia-samples).
 *
 * Два якоря (MIDI 60 и 72), сдвиг высоты через [SoundPool] playbackRate 0.5…2.
 */
object InstrumentSamplePlayer {

    private val sustainStream = AtomicInteger(0)

    @Volatile
    private var pool: SoundPool? = null

    @Volatile
    private var pianoC4 = 0

    @Volatile
    private var pianoC5 = 0

    @Volatile
    private var bowedC4 = 0

    @Volatile
    private var bowedC5 = 0

    @Volatile
    private var loadFailed = false

    private var sustainCtx: Context? = null

    @Volatile
    private var sustainTimbre: NoteTimbre? = null

    private var sustainVol = 0.45f

    @Volatile
    private var sustainAnchorSampleId = 0

    fun ensureLoaded(context: Context) {
        if (pool != null || loadFailed) return
        synchronized(this) {
            if (pool != null || loadFailed) return
            val app = context.applicationContext
            val latch = CountDownLatch(4)
            val loadErrors = AtomicInteger(0)
            val sp = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .build()
            sp.setOnLoadCompleteListener { _, _, status ->
                if (status != 0) {
                    loadErrors.incrementAndGet()
                    Log.w(TAG, "onLoadComplete status=$status")
                }
                latch.countDown()
            }
            val idP4 = sp.load(app, R.raw.inst_piano_c4, 1)
            val idP5 = sp.load(app, R.raw.inst_piano_c5, 1)
            val idV4 = sp.load(app, R.raw.inst_violin_c4, 1)
            val idV5 = sp.load(app, R.raw.inst_violin_c5, 1)
            if (idP4 == 0 || idP5 == 0 || idV4 == 0 || idV5 == 0) {
                Log.e(TAG, "SoundPool.load returned 0")
                sp.release()
                loadFailed = true
                return
            }
            if (!latch.await(20, TimeUnit.SECONDS)) {
                Log.e(TAG, "SoundPool load timeout")
                sp.release()
                loadFailed = true
                return
            }
            if (loadErrors.get() > 0) {
                Log.e(TAG, "SoundPool load had errors")
                sp.release()
                loadFailed = true
                return
            }
            pianoC4 = idP4
            pianoC5 = idP5
            bowedC4 = idV4
            bowedC5 = idV5
            pool = sp
        }
    }

    fun isReady(): Boolean = pool != null && !loadFailed

    private fun sampleIdFor(timbre: NoteTimbre, midi: Int): Pair<Int, Int> {
        pool ?: return 0 to 60
        val (lowId, highId, lowMidi, highMidi) = when (timbre) {
            NoteTimbre.PIANO -> SamplePair(pianoC4, pianoC5, 60, 72)
            NoteTimbre.VIOLIN -> SamplePair(bowedC4, bowedC5, 60, 72)
            NoteTimbre.SINE -> return 0 to 60
        }
        val m = midi.coerceIn(0, 127)
        return if (abs(m - lowMidi) <= abs(m - highMidi)) {
            lowId to lowMidi
        } else {
            highId to highMidi
        }
    }

    private data class SamplePair(val lowId: Int, val highId: Int, val lowMidi: Int, val highMidi: Int)

    private fun rateFor(midi: Int, baseMidi: Int): Float =
        2f.pow((midi - baseMidi) / 12f).coerceIn(0.5f, 2f)

    fun playNoteBlocking(context: Context, midi: Int, durationMs: Int, timbre: NoteTimbre, volume: Float) {
        if (timbre == NoteTimbre.SINE) return
        ensureLoaded(context)
        val p = pool ?: return
        val vol = volume.coerceIn(0.15f, 1f)
        val (sampleId, baseMidi) = sampleIdFor(timbre, midi)
        if (sampleId == 0) return
        val rate = rateFor(midi.coerceIn(0, 127), baseMidi)
        val streamId = p.play(sampleId, vol, vol, 1, 0, rate)
        if (streamId == 0) {
            Log.w(TAG, "play returned 0")
            return
        }
        try {
            Thread.sleep(durationMs.coerceIn(40, 4000).toLong())
        } finally {
            p.stop(streamId)
        }
    }

    fun startSustain(context: Context, midi: Int, timbre: NoteTimbre, volume: Float) {
        if (timbre == NoteTimbre.SINE) return
        stopSustain()
        ensureLoaded(context)
        val p = pool ?: return
        sustainCtx = context.applicationContext
        sustainTimbre = timbre
        sustainVol = volume.coerceIn(0.15f, 1f)
        val (sampleId, baseMidi) = sampleIdFor(timbre, midi)
        if (sampleId == 0) return
        sustainAnchorSampleId = sampleId
        val rate = rateFor(midi.coerceIn(0, 127), baseMidi)
        val streamId = p.play(sampleId, sustainVol, sustainVol, 1, -1, rate)
        sustainStream.set(streamId)
    }

    fun setSustainMidi(midi: Int) {
        val timbre = sustainTimbre ?: return
        val p = pool ?: return
        val m = midi.coerceIn(0, 127)
        val (sampleId, baseMidi) = sampleIdFor(timbre, m)
        if (sampleId == 0) return
        if (sampleId != sustainAnchorSampleId) {
            val sid = sustainStream.getAndSet(0)
            if (sid != 0) {
                try {
                    p.stop(sid)
                } catch (_: Exception) {
                }
            }
            sustainAnchorSampleId = sampleId
            val rate = rateFor(m, baseMidi)
            val streamId = p.play(sampleId, sustainVol, sustainVol, 1, -1, rate)
            sustainStream.set(streamId)
        } else {
            val sid = sustainStream.get()
            if (sid != 0) {
                p.setRate(sid, rateFor(m, baseMidi))
            }
        }
    }

    fun stopSustain() {
        val p = pool
        val sid = sustainStream.getAndSet(0)
        if (p != null && sid != 0) {
            try {
                p.stop(sid)
            } catch (_: Exception) {
            }
        }
        sustainCtx = null
        sustainTimbre = null
        sustainAnchorSampleId = 0
    }
}
