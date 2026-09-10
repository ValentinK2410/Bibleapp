package com.example.bible.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.example.bible.data.BibleBook
import com.example.bible.data.BibleCanon
import com.example.bible.data.BibleChapter
import com.example.bible.data.BibleLibrary
import com.example.bible.data.TranslationId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
sealed interface BibleChapterLoadState {
    data object Loading : BibleChapterLoadState
    data class Ready(val chapter: BibleChapter, val bookName: String) : BibleChapterLoadState
    data object NotFound : BibleChapterLoadState
}

@Immutable
sealed interface BibleBookShellState {
    data object Loading : BibleBookShellState
    data class Ready(val book: BibleBook) : BibleBookShellState
    /** Локального текста нет — онлайн-перевод или legacy. */
    data class Fallback(val book: BibleBook, val isOnlineOnly: Boolean) : BibleBookShellState
}

@Composable
fun rememberLoadedChapter(
    library: BibleLibrary,
    translation: TranslationId,
    bookId: String,
    chapterNum: Int,
): BibleChapterLoadState {
    var state by remember(translation, bookId, chapterNum) {
        mutableStateOf<BibleChapterLoadState>(BibleChapterLoadState.Loading)
    }
    LaunchedEffect(library, translation, bookId, chapterNum) {
        state = BibleChapterLoadState.Loading
        state = withContext(Dispatchers.IO) {
            val chapter = library.loadChapter(translation, bookId, chapterNum)
            if (chapter != null) {
                val name = library.bookName(translation, bookId)
                    ?: BibleCanon.displayName(
                        BibleCanon.byId(bookId) ?: BibleCanon.allBooks.first(),
                        translation,
                    )
                BibleChapterLoadState.Ready(chapter, name)
            } else {
                BibleChapterLoadState.NotFound
            }
        }
    }
    return state
}

@Composable
fun rememberBookShell(
    library: BibleLibrary,
    translation: TranslationId,
    bookId: String,
): BibleBookShellState {
    var state by remember(translation, bookId) {
        mutableStateOf<BibleBookShellState>(BibleBookShellState.Loading)
    }
    LaunchedEffect(library, translation, bookId) {
        state = BibleBookShellState.Loading
        state = withContext(Dispatchers.IO) {
            val shell = library.loadBookShell(translation, bookId)
            if (shell != null) {
                BibleBookShellState.Ready(shell)
            } else {
                val canon = BibleCanon.byId(bookId)
                if (canon != null) {
                    val book = BibleBook(
                        id = canon.id,
                        name = BibleCanon.displayName(canon, translation),
                        chapters = (1..canon.chapters).map { n ->
                            com.example.bible.data.BibleChapter(number = n, verses = emptyList())
                        },
                    )
                    val online = library.isOnlineOnly(translation, bookId)
                    BibleBookShellState.Fallback(book, online)
                } else {
                    BibleBookShellState.Fallback(
                        BibleBook(id = bookId, name = bookId, chapters = emptyList()),
                        isOnlineOnly = translation.onlineCode != null,
                    )
                }
            }
        }
    }
    return state
}

@Composable
fun rememberBookName(
    library: BibleLibrary,
    translation: TranslationId,
    bookId: String,
): String? {
    var name by remember(translation, bookId) { mutableStateOf<String?>(null) }
    LaunchedEffect(library, translation, bookId) {
        name = withContext(Dispatchers.IO) {
            library.bookName(translation, bookId)
                ?: BibleCanon.byId(bookId)?.let { BibleCanon.displayName(it, translation) }
        }
    }
    return name
}
