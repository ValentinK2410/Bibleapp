package com.example.bible.games.pipes

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.media.PlaybackParams
import com.example.bible.R

enum class PipeRotateSound {
    PAGE_FLIP,
    SPIDER_WEB,
}

/** Звуки «Водопровод»: перелистывание/паутина при повороте и всплеск при победе. */
class PipePuzzleSoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null

    fun playRotate(style: PipeRotateSound = PipeRotateSound.PAGE_FLIP) {
        when (style) {
            PipeRotateSound.PAGE_FLIP -> play(
                resId = R.raw.game_pipe_page_flip,
                volume = 0.78f,
                pitch = 1f,
            )
            PipeRotateSound.SPIDER_WEB -> play(
                resId = R.raw.game_pipe_web,
                volume = 0.72f,
                pitch = 1f,
            )
        }
    }

    fun playWin() {
        play(
            resId = R.raw.kids_fish_splash,
            volume = 0.72f,
            pitch = 1.05f,
        )
    }

    fun stop() {
        player?.let { mp ->
            player = null
            mp.setOnCompletionListener(null)
            try {
                mp.stop()
            } catch (_: IllegalStateException) {
            }
            mp.release()
        }
    }

    private fun play(resId: Int, volume: Float, pitch: Float) {
        stop()
        val mp = MediaPlayer.create(appContext, resId) ?: return
        mp.setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mp.playbackParams = PlaybackParams().setPitch(pitch.coerceIn(0.5f, 2f))
        }
        mp.setOnCompletionListener { player ->
            player.release()
            if (this.player === player) {
                this.player = null
            }
        }
        player = mp
        mp.start()
    }
}
