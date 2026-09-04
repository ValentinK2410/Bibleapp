package com.example.bible.data

import java.io.File
import java.util.Locale

/**
 * Пропуск повторной загрузки: сравниваем **название файла** (как у yt-dlp: заголовок ролика),
 * а не URL источника. Ссылка на плейлист YouTube не должна блокировать другой плейлист.
 */
object MediaDownloadDedup {

    fun stemKey(nameOrTitle: String): String {
        val trimmed = nameOrTitle.trim()
        if (trimmed.isEmpty()) return ""
        val base = basenameIfAbsolutePath(trimmed)
        if (base.isEmpty()) return ""
        val ext = base.substringAfterLast('.', missingDelimiterValue = "")
        val stem =
            if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) {
                base.substringBeforeLast('.')
            } else {
                base
            }
        return stem
            .trim()
            .take(100)
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun collectStems(
        titles: Iterable<String>,
        fileNames: Iterable<String> = emptyList(),
        dirs: Iterable<File> = emptyList(),
    ): Set<String> {
        val out = mutableSetOf<String>()
        fun add(raw: String) {
            val k = stemKey(raw)
            if (k.isNotEmpty()) out.add(k)
        }
        titles.forEach(::add)
        fileNames.forEach(::add)
        for (dir in dirs) {
            dir.listFiles()?.forEach { f ->
                if (f.isFile) add(f.name)
            }
        }
        return out
    }

    /** Только абсолютный путь режем до имени файла; «Гимн / часть 1» — это заголовок, не путь. */
    private fun basenameIfAbsolutePath(raw: String): String {
        val isAbs =
            raw.startsWith("/") ||
                raw.startsWith("\\") ||
                (raw.length >= 3 && raw[1] == ':' && (raw[2] == '\\' || raw[2] == '/'))
        return if (isAbs) {
            raw.substringAfterLast('/').substringAfterLast('\\')
        } else {
            raw
        }
    }
}
