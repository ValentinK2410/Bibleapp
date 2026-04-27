package com.example.bible.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Локальное сохранение текстов тафсира (ас-Саади рус., Ибн Касир араб.) по аятам.
 * Каталог: [Context.getFilesDir]/quran_tafsir/
 */
object QuranTafsirStorage {

    private const val MIN_TEXT_LEN = 8

    private fun dir(ctx: Context): File =
        File(ctx.filesDir, "quran_tafsir").also { it.mkdirs() }

    private fun saadiFile(ctx: Context, surah: Int, ayah: Int): File =
        File(dir(ctx), "saadi_${surah}_${ayah}.txt")

    private fun ibnKathirFile(ctx: Context, surah: Int, ayah: Int): File =
        File(dir(ctx), "ibnkathir_${surah}_${ayah}.txt")

    fun isSaadiCached(ctx: Context, surah: Int, ayah: Int): Boolean {
        val f = saadiFile(ctx, surah, ayah)
        return f.isFile && f.length() >= MIN_TEXT_LEN
    }

    fun isIbnKathirCached(ctx: Context, surah: Int, ayah: Int): Boolean {
        val f = ibnKathirFile(ctx, surah, ayah)
        return f.isFile && f.length() >= MIN_TEXT_LEN
    }

    /** Оба тафсира для аята сохранены (для индикатора «комментарий скачан»). */
    fun isVerseCommentaryFullyCached(ctx: Context, surah: Int, ayah: Int): Boolean =
        isSaadiCached(ctx, surah, ayah) && isIbnKathirCached(ctx, surah, ayah)

    fun readSaadi(ctx: Context, surah: Int, ayah: Int): String? {
        val f = saadiFile(ctx, surah, ayah)
        if (!f.isFile || f.length() < MIN_TEXT_LEN) return null
        return runCatching { f.readText(Charsets.UTF_8).trim().takeIf { it.length >= MIN_TEXT_LEN } }.getOrNull()
    }

    fun readIbnKathir(ctx: Context, surah: Int, ayah: Int): String? {
        val f = ibnKathirFile(ctx, surah, ayah)
        if (!f.isFile || f.length() < MIN_TEXT_LEN) return null
        return runCatching { f.readText(Charsets.UTF_8).trim().takeIf { it.length >= MIN_TEXT_LEN } }.getOrNull()
    }

    /** Все аяты из [verses] имеют оба тафсира в кэше. */
    fun isSurahCommentaryFullyCached(ctx: Context, surah: Int, verses: List<QuranVerse>): Boolean {
        if (verses.isEmpty()) return false
        for (v in verses) {
            if (!isVerseCommentaryFullyCached(ctx, surah, v.number)) return false
        }
        return true
    }

    suspend fun downloadSaadiIfNeeded(ctx: Context, surah: Int, ayah: Int): Boolean =
        withContext(Dispatchers.IO) {
            if (isSaadiCached(ctx, surah, ayah)) return@withContext true
            val t = QuranTafsirApi.fetchSaadiRussian(surah, ayah) ?: return@withContext false
            runCatching {
                saadiFile(ctx, surah, ayah).writeText(t, Charsets.UTF_8)
            }.isSuccess
        }

    suspend fun downloadIbnKathirIfNeeded(ctx: Context, surah: Int, ayah: Int): Boolean =
        withContext(Dispatchers.IO) {
            if (isIbnKathirCached(ctx, surah, ayah)) return@withContext true
            val t = QuranTafsirApi.fetchIbnKathirArabic(surah, ayah) ?: return@withContext false
            runCatching {
                ibnKathirFile(ctx, surah, ayah).writeText(t, Charsets.UTF_8)
            }.isSuccess
        }

    /** Скачать оба тафсира для одного аята; true только если оба в кэше после вызова. */
    suspend fun downloadVerseCommentaries(ctx: Context, surah: Int, ayah: Int): Boolean {
        val s = downloadSaadiIfNeeded(ctx, surah, ayah)
        val k = downloadIbnKathirIfNeeded(ctx, surah, ayah)
        return s && k
    }

    /**
     * Скачать комментарии для всех аятов суры.
     * @return сколько аятов получили оба тафсира успешно.
     */
    suspend fun downloadSurahCommentaries(ctx: Context, surah: Int, verses: List<QuranVerse>): Int =
        withContext(Dispatchers.IO) {
            var ok = 0
            for (v in verses) {
                if (downloadVerseCommentaries(ctx, surah, v.number)) ok++
            }
            ok
        }
}
