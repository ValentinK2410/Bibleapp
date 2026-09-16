package com.example.bible.data

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.media.session.MediaButtonReceiver
import com.example.bible.receiver.AppMediaButtonReceiver
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "AppMediaButtonSession"
private const val HEADSET_MULTI_TAP_MS = 450L

/**
 * Кнопки наушников (AVRCP / MEDIA_BUTTON): пауза, play/pause, следующий трек.
 * Двойной тап по кнопке наушников — пауза; тройной — «далее» (как в YouTube у части гарнитур).
 */
object AppMediaButtonSession {

    data class Controls(
        val title: () -> String,
        val isPlaying: () -> Boolean,
        val playPause: () -> Unit,
        val pause: () -> Unit,
        val resume: () -> Unit = playPause,
        val skipToNext: (() -> Unit)? = null,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val slots = ConcurrentHashMap<String, Controls>()
    private var appContext: Context? = null
    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    private var hasAudioFocus = false

    @Suppress("DEPRECATION")
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> mainHandler.post {
                activeControls()?.pause()
                refreshPlaybackState()
            }
        }
    }

    private var hookTapCount = 0
    private val hookTapRunnable = Runnable { dispatchHookTapCount() }

    fun install(application: Application) {
        if (appContext != null) return
        val ctx = application.applicationContext
        appContext = ctx
        audioManager = ctx.getSystemService(AudioManager::class.java)
        installBuiltInControls()
        val pending = PendingIntent.getBroadcast(
            ctx,
            0,
            Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(
                ComponentName(ctx, AppMediaButtonReceiver::class.java),
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSessionCompat(ctx, "BibleAppMedia").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setMediaButtonReceiver(pending)
            setCallback(sessionCallback)
            isActive = false
        }
    }

    fun onMediaButtonIntent(intent: Intent) {
        val session = mediaSession ?: return
        MediaButtonReceiver.handleIntent(session, intent)
    }

    /** Подключить управление для активного плеера (видео, озвучка, песня). */
    fun register(
        id: String,
        controls: Controls,
    ): () -> Unit {
        slots[id] = controls
        refreshPlaybackState()
        return {
            slots.remove(id)
            refreshPlaybackState()
        }
    }

    fun refreshPlaybackState() {
        mainHandler.post { updateSessionState() }
    }

    private fun installBuiltInControls() {
        slots["bible_chapter_audio"] = Controls(
            title = {
                val st = BibleAudioPlayer.state.value
                if (st.bookId.isBlank()) "" else "${st.bookId} ${st.chapter}"
            },
            isPlaying = { BibleAudioPlayer.state.value.isPlaying },
            playPause = { BibleAudioPlayer.togglePlay() },
            pause = { BibleAudioPlayer.pauseIfPlaying() },
            resume = {
                if (BibleAudioPlayer.state.value.isPlaying) {
                    BibleAudioPlayer.togglePlay()
                } else {
                    BibleAudioPlayer.resumeAfterInterruption()
                    if (!BibleAudioPlayer.state.value.isPlaying) {
                        BibleAudioPlayer.togglePlay()
                    }
                }
            },
            skipToNext = { BibleAudioPlayer.skipToNextChapter() },
        )
        slots["library_audio_holder"] = Controls(
            title = { AudioPlayerHolder.state.value.title },
            isPlaying = { AudioPlayerHolder.state.value.isPlaying },
            playPause = { AudioPlayerHolder.togglePlay() },
            pause = { AudioPlayerHolder.pauseIfPlaying() },
            resume = {
                if (AudioPlayerHolder.state.value.isPlaying) {
                    AudioPlayerHolder.togglePlay()
                } else {
                    AudioPlayerHolder.resumeAfterInterruption()
                    if (!AudioPlayerHolder.state.value.isPlaying) {
                        AudioPlayerHolder.togglePlay()
                    }
                }
            },
            skipToNext = { AudioPlayerHolder.onSkipToNext?.invoke() },
        )
    }

    private fun activeControls(): Controls? {
        if (slots.isEmpty()) return null
        val priority = listOf(
            "library_video_player",
            "media_playlist_audio",
            "attachment_video_preview",
            "attachment_audio_preview",
        )
        for (key in priority) {
            slots[key]?.let { return it }
        }
        if (BibleAudioPlayer.state.value.bookId.isNotBlank()) {
            slots["bible_chapter_audio"]?.let { return it }
        }
        if (AudioPlayerHolder.state.value.audioPath.isNotBlank()) {
            slots["library_audio_holder"]?.let { return it }
        }
        return slots.values.firstOrNull()
    }

    private fun updateSessionState() {
        val session = mediaSession ?: return
        val controls = activeControls()
        if (controls == null) {
            session.isActive = false
            releaseAudioFocus()
            return
        }
        acquireAudioFocus()
        val playing = runCatching { controls.isPlaying() }.getOrDefault(false)
        var actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE
        if (controls.skipToNext != null) {
            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1f,
                )
                .build(),
        )
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, controls.title())
                .build(),
        )
        session.isActive = true
    }

    @Suppress("DEPRECATION")
    private fun acquireAudioFocus() {
        if (hasAudioFocus) return
        val am = audioManager ?: return
        val granted = am.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN,
        )
        hasAudioFocus = granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun releaseAudioFocus() {
        if (!hasAudioFocus) return
        audioManager?.abandonAudioFocus(audioFocusChangeListener)
        hasAudioFocus = false
    }

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            mainHandler.post {
                activeControls()?.resume()
                refreshPlaybackState()
            }
        }

        override fun onPause() {
            mainHandler.post {
                activeControls()?.pause()
                refreshPlaybackState()
            }
        }

        override fun onSkipToNext() {
            mainHandler.post {
                activeControls()?.skipToNext?.invoke()
                refreshPlaybackState()
            }
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
            val keyEvent = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                ?: return super.onMediaButtonEvent(mediaButtonEvent)
            if (keyEvent.action != KeyEvent.ACTION_DOWN) {
                return true
            }
            return when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    mainHandler.post {
                        activeControls()?.resume()
                        refreshPlaybackState()
                    }
                    true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    mainHandler.post {
                        activeControls()?.pause()
                        refreshPlaybackState()
                    }
                    true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    mainHandler.post {
                        activeControls()?.skipToNext?.invoke()
                        refreshPlaybackState()
                    }
                    true
                }
                KeyEvent.KEYCODE_HEADSETHOOK,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                -> {
                    mainHandler.post { scheduleHeadsetHookTap() }
                    true
                }
                else -> super.onMediaButtonEvent(mediaButtonEvent)
            }
        }
    }

    private fun scheduleHeadsetHookTap() {
        hookTapCount++
        mainHandler.removeCallbacks(hookTapRunnable)
        mainHandler.postDelayed(hookTapRunnable, HEADSET_MULTI_TAP_MS)
    }

    private fun dispatchHookTapCount() {
        val controls = activeControls() ?: run {
            hookTapCount = 0
            return
        }
        val taps = hookTapCount
        hookTapCount = 0
        when {
            taps >= 3 -> controls.skipToNext?.invoke()
            taps == 2 -> {
                if (controls.isPlaying()) {
                    controls.pause()
                } else {
                    controls.resume()
                }
            }
            taps == 1 -> {
                if (controls.isPlaying()) {
                    controls.pause()
                } else {
                    controls.resume()
                }
            }
        }
        refreshPlaybackState()
    }
}
