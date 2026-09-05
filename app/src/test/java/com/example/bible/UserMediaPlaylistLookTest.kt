package com.example.bible

import com.example.bible.data.PlaylistLook
import org.junit.Assert.assertEquals
import org.junit.Test

class UserMediaPlaylistLookTest {

    @Test
    fun unknownLookFallsBackToInk() {
        assertEquals(PlaylistLook.INK, PlaylistLook.fromId(null))
        assertEquals(PlaylistLook.INK, PlaylistLook.fromId("nope"))
        assertEquals(PlaylistLook.GOLD, PlaylistLook.fromId("gold"))
    }
}
