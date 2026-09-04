package com.example.bible.data.db

/**
 * Координация фоновой индексации и поиска: пока идёт поиск, индексатор ждёт.
 * Поиск никогда не блокируется на индексации.
 */
internal object BibleSearchDbLock {

    @Volatile
    private var activeSearches = 0

    fun onSearchStarted() {
        synchronized(this) { activeSearches++ }
    }

    fun onSearchFinished() {
        synchronized(this) {
            if (activeSearches > 0) activeSearches--
        }
    }

    fun shouldPauseIndexing(): Boolean = activeSearches > 0

    inline fun <T> withWriteLock(block: () -> T): T = synchronized(this, block)
}
