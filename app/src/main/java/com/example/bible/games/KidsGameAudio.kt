package com.example.bible.games

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.example.bible.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class KidsSfx(val resId: Int, val volume: Float) {
    TAP(R.raw.kids_sfx_tap, 0.7f),
    PLACE(R.raw.kids_sfx_place, 0.85f),
    CAPTURE(R.raw.kids_sfx_capture, 0.8f),
    WIN(R.raw.kids_sfx_win, 0.9f),
    LOSE(R.raw.kids_sfx_lose, 0.75f),
    DRAW(R.raw.kids_sfx_draw, 0.75f),
    PIPE_ROTATE(R.raw.kids_sfx_pipe_rotate, 0.8f),
    PIPE_CONNECT(R.raw.kids_sfx_pipe_connect, 0.7f),
    WATER(R.raw.kids_sfx_water, 0.85f),
    CORRECT(R.raw.kids_sfx_correct, 0.85f),
    WRONG(R.raw.kids_sfx_wrong, 0.7f),
}

enum class KidsMusicTrack(val resId: Int) {
    GAMES(R.raw.kids_music_games),
    PIPES(R.raw.kids_music_pipes),
}

/** Фоновая музыка и короткие звуки детских игр; включение хранится между запусками. */
object KidsGameAudio {
    private const val PREFS = "kids_game_audio"
    private const val KEY_MUSIC = "music_on"
    private const val KEY_SFX = "sfx_on"
    private const val MUSIC_VOLUME = 0.32f

    private val _musicOn = MutableStateFlow(true)
    val musicOn: StateFlow<Boolean> = _musicOn.asStateFlow()
    private val _sfxOn = MutableStateFlow(true)
    val sfxOn: StateFlow<Boolean> = _sfxOn.asStateFlow()

    private var appContext: Context? = null
    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<KidsSfx, Int>()
    private var player: MediaPlayer? = null
    private var playerTrack: KidsMusicTrack? = null
    private var wantedTrack: KidsMusicTrack? = null

    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _musicOn.value = prefs.getBoolean(KEY_MUSIC, true)
        _sfxOn.value = prefs.getBoolean(KEY_SFX, true)
        val pool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        KidsSfx.entries.forEach { soundIds[it] = pool.load(ctx, it.resId, 1) }
        soundPool = pool
    }

    fun play(sfx: KidsSfx) {
        if (!_sfxOn.value) return
        val pool = soundPool ?: return
        val id = soundIds[sfx] ?: return
        pool.play(id, sfx.volume, sfx.volume, 1, 0, 1f)
    }

    fun setSfxOn(on: Boolean) {
        _sfxOn.value = on
        save(KEY_SFX, on)
    }

    fun setMusicOn(on: Boolean) {
        _musicOn.value = on
        save(KEY_MUSIC, on)
        if (on) wantedTrack?.let { startMusic(it) } else pauseMusic()
    }

    /** Экран игры просит свою мелодию; если музыка выключена, только запоминает выбор. */
    fun startMusic(track: KidsMusicTrack) {
        wantedTrack = track
        if (!_musicOn.value) return
        val ctx = appContext ?: return
        val current = player
        if (current != null && playerTrack == track) {
            if (!current.isPlaying) runCatching { current.start() }
            return
        }
        releasePlayer()
        val mp = MediaPlayer.create(ctx, track.resId) ?: return
        mp.isLooping = true
        mp.setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
        mp.start()
        player = mp
        playerTrack = track
    }

    fun pauseMusic() {
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    fun stopMusic(track: KidsMusicTrack) {
        if (wantedTrack == track) wantedTrack = null
        if (playerTrack == track) releasePlayer()
    }

    private fun releasePlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playerTrack = null
    }

    private fun save(key: String, value: Boolean) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(key, value)?.apply()
    }
}
