package com.example.bible.sms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.bible.data.SmsReactionAction
import com.example.bible.data.SmsReactionActionKind
import com.example.bible.data.SmsReactionRepository
import com.example.bible.data.scenarioMatchesSms
import com.example.bible.data.normalizeSmsDigits
import kotlin.concurrent.thread

private const val TAG = "SmsReactionExecutor"

/**
 * Выполняет действия сценариев при входящей SMS (вызывается с главного потока из приёмника).
 */
object SmsReactionExecutor {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun handleIncomingSms(appContext: Context, originatingAddressRaw: String?, messageBodyFull: String) {
        val scenarios = try {
            SmsReactionRepository(appContext).load()
        } catch (e: Exception) {
            Log.w(TAG, "load scenarios", e)
            return
        }
        if (scenarios.isEmpty()) return
        val digits = originatingAddressRaw.orEmpty().filter { it.isDigit() }
        val matched = scenarios.filter { scenarioMatchesSms(it, digits, messageBodyFull) }
        if (matched.isEmpty()) return
        val app = appContext.applicationContext
        val dest = originatingAddressRaw?.trim().orEmpty()
        mainHandler.post {
            for (scenario in matched) {
                for (action in scenario.actions) {
                    runCatching {
                        runOneAction(app, action, dest, messageBodyFull)
                    }.onFailure {
                        Log.w(TAG, "action ${action.kind}", it)
                    }
                }
            }
        }
    }

    private fun runOneAction(app: Context, action: SmsReactionAction, smsSenderRaw: String, @Suppress("UNUSED_PARAMETER") bodyFull: String) {
        when (action.kind) {
            SmsReactionActionKind.FLASHLIGHT_SECONDS -> flashlightSeconds(app, action.param)
            SmsReactionActionKind.PLAY_MEDIA_URI -> playMediaUri(app, action.param)
            SmsReactionActionKind.OPEN_IMAGE_URI -> openImageUri(app, action.param)
            SmsReactionActionKind.CALLBACK_SENDER -> placeCall(app, smsSenderRaw)
            SmsReactionActionKind.CALLBACK_FIXED_NUMBER -> placeCall(app, action.param)
            SmsReactionActionKind.VIBRATE_CONTINUOUS_MS -> vibrateContinuous(app, action.param)
            SmsReactionActionKind.VIBRATE_PULSE_LOOP_MS -> vibratePulseLoop(app, action.param)
            SmsReactionActionKind.SEND_REPLY_SMS -> sendReplySms(app, smsSenderRaw, action.param)
        }
    }

    private fun flashlightSeconds(app: Context, param: String) {
        val sec = param.trim().toIntOrNull()?.coerceIn(1, 120) ?: 30
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "FLASHLIGHT: no CAMERA permission")
            return
        }
        val cm = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: run {
            Log.w(TAG, "FLASHLIGHT: no torch camera")
            return
        }
        runCatching {
            cm.setTorchMode(cameraId, true)
            mainHandler.postDelayed({
                runCatching { cm.setTorchMode(cameraId, false) }
            }, sec * 1000L)
        }.onFailure { Log.w(TAG, "torch", it) }
    }

    private fun playMediaUri(app: Context, uriStr: String) {
        val uri = uriStr.trim().takeIf { it.isNotEmpty() }?.let { Uri.parse(it) } ?: return
        thread(name = "sms-play-media") {
            runCatching {
                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    setDataSource(app, uri)
                    prepare()
                }
                mainHandler.post {
                    mp.start()
                    mp.setOnCompletionListener { it.release() }
                }
            }.onFailure {
                Log.w(TAG, "play media $uriStr", it)
            }
        }
    }

    private fun openImageUri(app: Context, uriStr: String) {
        val uri = uriStr.trim().takeIf { it.isNotEmpty() }?.let { Uri.parse(it) } ?: return
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            app.startActivity(intent)
        }.onFailure { Log.w(TAG, "open image", it) }
    }

    private fun placeCall(app: Context, numberRaw: String) {
        val digits = numberRaw.normalizeSmsDigits()
        if (digits.isEmpty()) return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "CALL: no CALL_PHONE permission")
            return
        }
        runCatching {
            val uri = Uri.fromParts("tel", digits, null)
            app.startActivity(
                Intent(Intent.ACTION_CALL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure { Log.w(TAG, "call", it) }
    }

    private fun vibrateContinuous(app: Context, paramMs: String) {
        val totalMs = paramMs.trim().toLongOrNull()?.coerceIn(300L, 60_000L) ?: 8000L
        val vibrator = ContextCompat.getSystemService(app, Vibrator::class.java) ?: return
        val patternMs = 600L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(longArrayOf(0L, patternMs), 0)
            vibrator.vibrate(effect)
            mainHandler.postDelayed({ vibrator.cancel() }, totalMs)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0L, patternMs), 0)
            mainHandler.postDelayed({
                @Suppress("DEPRECATION")
                vibrator.cancel()
            }, totalMs)
        }
    }

    /**
     * Импульсы: параметр — суммарная длительность сценария в мс (например 15000).
     */
    private fun vibratePulseLoop(app: Context, paramMs: String) {
        val totalMs = paramMs.trim().toLongOrNull()?.coerceIn(500L, 120_000L) ?: 15_000L
        val vibrator = ContextCompat.getSystemService(app, Vibrator::class.java) ?: return
        val pattern = longArrayOf(0L, 120L, 200L, 120L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            mainHandler.postDelayed({ vibrator.cancel() }, totalMs)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
            mainHandler.postDelayed({
                @Suppress("DEPRECATION")
                vibrator.cancel()
            }, totalMs)
        }
    }

    private fun sendReplySms(app: Context, smsSenderRaw: String, replyBody: String) {
        val text = replyBody.trim()
        if (text.isEmpty() || smsSenderRaw.isBlank()) return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SMS reply: no SEND_SMS permission")
            return
        }
        runCatching {
            val mgr = app.getSystemService(SmsManager::class.java)
            mgr.sendTextMessage(smsSenderRaw, null, text, null, null)
        }.onFailure { Log.w(TAG, "send sms reply", it) }
    }
}
