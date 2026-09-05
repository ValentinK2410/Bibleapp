package com.example.bible.data

import java.text.Collator
import java.util.Locale

/** Как упорядочить найденные видео и аудио в библиотеке. */
enum class MediaLibrarySort {
    NEWEST,
    OLDEST,
    TITLE_AZ,
    TITLE_ZA,
    LAST_PLAYED,
    ;

    fun labelRu(kind: UserMediaKind): String = when (this) {
        NEWEST -> "Новые"
        OLDEST -> "Старые"
        TITLE_AZ -> "А–Я"
        TITLE_ZA -> "Я–А"
        LAST_PLAYED -> if (kind == UserMediaKind.AUDIO) "Недавно слушали" else "Недавно смотрели"
    }

    companion object {
        fun fromName(raw: String?): MediaLibrarySort =
            entries.find { it.name == raw } ?: NEWEST
    }
}

fun <T> Iterable<T>.sortedByMediaLibrary(
    sort: MediaLibrarySort,
    title: (T) -> String,
    addedAt: (T) -> Long,
    lastPlayedAt: (T) -> Long = { 0L },
): List<T> {
    val collator = Collator.getInstance(Locale("ru", "RU")).apply {
        strength = Collator.PRIMARY
    }
    return when (sort) {
        MediaLibrarySort.NEWEST -> sortedByDescending(addedAt)
        MediaLibrarySort.OLDEST -> sortedBy(addedAt)
        MediaLibrarySort.TITLE_AZ -> sortedWith { a, b -> collator.compare(title(a), title(b)) }
        MediaLibrarySort.TITLE_ZA -> sortedWith { a, b -> collator.compare(title(b), title(a)) }
        MediaLibrarySort.LAST_PLAYED ->
            sortedWith(
                compareByDescending<T>(lastPlayedAt).thenByDescending(addedAt),
            )
    }
}
