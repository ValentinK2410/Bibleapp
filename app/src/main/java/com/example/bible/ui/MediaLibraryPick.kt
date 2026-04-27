package com.example.bible.ui

import com.example.bible.data.BibleUserAudio
import com.example.bible.data.BibleUserImage
import com.example.bible.data.BibleUserVideo

/** Выбор записи из раздела «Медиа» (картинки, видео или аудио). */
sealed class MediaLibraryPick {
    data class Image(val image: BibleUserImage) : MediaLibraryPick()
    data class Video(val video: BibleUserVideo) : MediaLibraryPick()
    data class Audio(val audio: BibleUserAudio) : MediaLibraryPick()
}
