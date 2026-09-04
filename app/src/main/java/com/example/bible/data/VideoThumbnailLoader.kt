package com.example.bible.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import java.io.File
import java.io.FileOutputStream

/** Кадр-превью видео: не берёт чёрный интро с 0:00, кэширует JPEG рядом с файлом. */
object VideoThumbnailLoader {
    private const val MAX_MEMORY = 48
    private val memory = object : LinkedHashMap<String, Bitmap>(MAX_MEMORY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > MAX_MEMORY
    }

    /** Микросекунды: после чёрного интро у лекций кадр часто ближе к 5–20 с. */
    private val probeTimesUs = longArrayOf(
        1_000_000L,
        2_000_000L,
        3_000_000L,
        5_000_000L,
        8_000_000L,
        12_000_000L,
        20_000_000L,
        500_000L,
    )

    fun load(file: File): Bitmap? {
        if (!file.exists() || file.length() < 64) return null
        val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
        synchronized(memory) {
            memory[key]?.let { return it }
        }
        val cacheFile = cacheFileFor(file)
        if (cacheFile.exists() && cacheFile.lastModified() >= file.lastModified() && cacheFile.length() > 64) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.ensureSoftware()?.takeIf { !it.isMostlyBlack() }?.let { cached ->
                putMemory(key, cached)
                return cached
            }
        }
        val extracted = extractUsableFrame(file)?.ensureSoftware() ?: return null
        putMemory(key, extracted)
        runCatching {
            cacheFile.parentFile?.mkdirs()
            FileOutputStream(cacheFile).use { out ->
                extracted.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
        }
        return extracted
    }

    private fun cacheFileFor(file: File): File {
        val dir = File(file.parentFile, ".thumbs")
        return File(dir, "${file.name}.jpg")
    }

    private fun putMemory(key: String, bmp: Bitmap) {
        synchronized(memory) { memory[key] = bmp }
    }

    private fun extractUsableFrame(file: File): Bitmap? {
        var best: Bitmap? = null
        var bestScore = -1
        fun consider(frame: Bitmap?) {
            if (frame == null) return
            val score = frame.averageLuminance()
            if (score > bestScore) {
                bestScore = score
                best = frame
            }
        }

        consider(extractViaThumbnailUtils(file))

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.embeddedPicture?.let { bytes ->
                consider(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            val times = buildList {
                addAll(probeTimesUs.toList())
                if (durationMs > 6_000L) add((durationMs / 4L) * 1000L)
                if (durationMs > 2_000L) add((durationMs / 2L) * 1000L)
            }.distinct().filter { durationMs <= 0L || it <= durationMs * 1000L }
            for (timeUs in times) {
                consider(frameAt(retriever, timeUs))
                if (bestScore >= 40) break
            }
        } catch (_: Exception) {
            consider(extractViaThumbnailUtils(file))
        } finally {
            runCatching { retriever.release() }
        }
        return best?.takeIf { bestScore >= 16 }
    }

    private fun frameAt(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 27) {
            retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST,
                320,
                180,
            )
        } else {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        }
    } catch (_: Exception) {
        null
    }

    private fun extractViaThumbnailUtils(file: File): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            ThumbnailUtils.createVideoThumbnail(file, Size(320, 180), CancellationSignal())
        } else {
            @Suppress("DEPRECATION")
            ThumbnailUtils.createVideoThumbnail(
                file.absolutePath,
                MediaStore.Video.Thumbnails.MINI_KIND,
            )
        }
    } catch (_: Exception) {
        null
    }

    private fun Bitmap.ensureSoftware(): Bitmap {
        if (Build.VERSION.SDK_INT >= 26 && config == Bitmap.Config.HARDWARE) {
            return copy(Bitmap.Config.ARGB_8888, false) ?: this
        }
        return this
    }

    private fun Bitmap.averageLuminance(): Int {
        val software = try {
            ensureSoftware()
        } catch (_: Exception) {
            return 0
        }
        if (software.width <= 0 || software.height <= 0) return 0
        return try {
            val samples = 10
            val stepX = (software.width / samples).coerceAtLeast(1)
            val stepY = (software.height / samples).coerceAtLeast(1)
            var total = 0
            var sum = 0
            var y = 0
            while (y < software.height) {
                var x = 0
                while (x < software.width) {
                    val c = software.getPixel(x, y)
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    sum += (r * 299 + g * 587 + b * 114) / 1000
                    total++
                    x += stepX
                }
                y += stepY
            }
            if (total == 0) 0 else sum / total
        } catch (_: Exception) {
            0
        }
    }

    private fun Bitmap.isMostlyBlack(): Boolean = averageLuminance() < 16
}
