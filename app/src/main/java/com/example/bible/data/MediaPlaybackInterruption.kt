package com.example.bible.data

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

private const val TAG = "MediaPlaybackIntr"

/**
 * Пауза при отключении наушников и входящем звонке; возобновление после вставки наушников и завершения звонка.
 */
object MediaPlaybackInterruption {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val slots = ConcurrentHashMap<String, PlayerSlot>()
    private val resumeAfterInterrupt = ConcurrentHashMap<String, Boolean>()

    private var blockedByHeadsetUnplug = false
    private var blockedByPhoneCall = false
    private var pendingAutoResume = false

    private var appContext: Context? = null
    private var noisyReceiver: BroadcastReceiver? = null
    private var telephonyManager: TelephonyManager? = null

    @Suppress("DEPRECATION")
    private var legacyPhoneListener: PhoneStateListener? = null

    @Volatile
    private var telephonyCallback31: TelephonyCallback? = null

    private data class PlayerSlot(
        val isPlaying: () -> Boolean,
        val pause: () -> Unit,
        val resume: () -> Unit,
    )

    fun install(application: Application) {
        if (appContext != null) return
        appContext = application.applicationContext
        registerBuiltInPlayers()
        registerNoisyReceiver(application)
        registerPhoneStateListener(application)
    }

    /** Динамические плееры (видео в диалоге, вложения к стиху и т.д.). */
    fun register(
        id: String,
        isPlaying: () -> Boolean,
        pause: () -> Unit,
        resume: () -> Unit,
    ): () -> Unit {
        slots[id] = PlayerSlot(isPlaying, pause, resume)
        return {
            slots.remove(id)
            resumeAfterInterrupt.remove(id)
        }
    }

    private fun registerBuiltInPlayers() {
        slots["bible_chapter_audio"] = PlayerSlot(
            isPlaying = { BibleAudioPlayer.state.value.isPlaying },
            pause = { BibleAudioPlayer.pauseIfPlaying() },
            resume = { BibleAudioPlayer.resumeAfterInterruption() },
        )
        slots["library_audio_holder"] = PlayerSlot(
            isPlaying = { AudioPlayerHolder.state.value.isPlaying },
            pause = { AudioPlayerHolder.pauseIfPlaying() },
            resume = { AudioPlayerHolder.resumeAfterInterruption() },
        )
    }

    private fun registerNoisyReceiver(app: Application) {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> onHeadsetUnplugged()
                    Intent.ACTION_HEADSET_PLUG -> {
                        val plugged = intent.getIntExtra("state", -1) == 1
                        if (plugged) onHeadsetPlugged()
                    }
                }
            }
        }
        noisyReceiver = receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            app.registerReceiver(receiver, filter)
        }
    }

    private fun registerPhoneStateListener(app: Application) {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_PHONE_STATE not granted — pause on incoming call disabled")
            return
        }
        val tm = app.getSystemService(TelephonyManager::class.java) ?: return
        telephonyManager = tm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val exec = Executor { mainHandler.post(it) }
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallState(state)
                }
            }
            telephonyCallback31 = cb
            tm.registerTelephonyCallback(exec, cb)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in API 31")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallState(state)
                }
            }
            legacyPhoneListener = listener
            @Suppress("DEPRECATION")
            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK,
            -> onPhoneCallActive()
            TelephonyManager.CALL_STATE_IDLE -> onPhoneCallIdle()
        }
    }

    private fun onHeadsetUnplugged() {
        mainHandler.post {
            blockedByHeadsetUnplug = true
            if (pauseAllPlaying()) {
                pendingAutoResume = true
            }
        }
    }

    private fun onHeadsetPlugged() {
        mainHandler.post {
            blockedByHeadsetUnplug = false
            tryAutoResume()
        }
    }

    private fun onPhoneCallActive() {
        mainHandler.post {
            blockedByPhoneCall = true
            if (pauseAllPlaying()) {
                pendingAutoResume = true
            }
        }
    }

    private fun onPhoneCallIdle() {
        mainHandler.post {
            blockedByPhoneCall = false
            tryAutoResume()
        }
    }

    private fun pauseAllPlaying(): Boolean {
        var any = false
        for ((id, slot) in slots) {
            if (slot.isPlaying()) {
                resumeAfterInterrupt[id] = true
                runCatching { slot.pause() }
                any = true
            } else {
                resumeAfterInterrupt.remove(id)
            }
        }
        return any
    }

    private fun tryAutoResume() {
        if (blockedByHeadsetUnplug || blockedByPhoneCall) return
        if (!pendingAutoResume) return
        pendingAutoResume = false
        for ((id, slot) in slots) {
            if (resumeAfterInterrupt.remove(id) == true) {
                runCatching { slot.resume() }
            }
        }
        resumeAfterInterrupt.clear()
    }
}
