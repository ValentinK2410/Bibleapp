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
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.bible.data.SmsReactionAction
import com.example.bible.data.SmsReactionActionKind
import com.example.bible.data.SmsReactionRepository
import com.example.bible.data.SmsReactionScenario
import com.example.bible.data.scenarioMatchesSms
import com.example.bible.data.normalizeSmsDigits
import kotlin.concurrent.thread

private const val TAG = "SmsReactionExecutor"

/**
 * Выполняет действия сценариев при входящей SMS (вызывается с главного потока из приёмника).
 */
object SmsReactionExecutor {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * @param smsSubscriptionId подписка SIM из extras интента SMS_RECEIVED ([Intent] `"subscription"`); для отправки ответа на той же SIM.
     *
     * Действия выполняются сразу внутри жизненного цикла [BroadcastReceiver.onReceive]: если отложить через Handler,
     * к этому моменту ограничения фона уже блокируют [ACTION_CALL] и часть других активностей (Android 10+).
     */
    fun handleIncomingSms(
        appContext: Context,
        originatingAddressRaw: String?,
        messageBodyFull: String,
        smsSubscriptionId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
    ) {
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
        for (scenario in matched) {
            val outboundSub = effectiveOutboundSubscription(scenario, smsSubscriptionId)
            for (action in scenario.actions) {
                runCatching {
                    runOneAction(app, action, dest, messageBodyFull, outboundSub)
                }.onFailure {
                    Log.w(TAG, "action ${action.kind}", it)
                }
            }
        }
    }

    private fun effectiveOutboundSubscription(
        scenario: SmsReactionScenario,
        incomingSubscriptionId: Int,
    ): Int {
        val chosen = scenario.outboundSubscriptionId
        return if (chosen != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            chosen
        } else {
            incomingSubscriptionId
        }
    }

    private fun runOneAction(
        app: Context,
        action: SmsReactionAction,
        smsSenderRaw: String,
        @Suppress("UNUSED_PARAMETER") bodyFull: String,
        smsSubscriptionId: Int,
    ) {
        when (action.kind) {
            SmsReactionActionKind.FLASHLIGHT_SECONDS -> flashlightSeconds(app, action.param)
            SmsReactionActionKind.PLAY_MEDIA_URI -> playMediaUri(app, action.param)
            SmsReactionActionKind.OPEN_IMAGE_URI -> openImageUri(app, action.param)
            SmsReactionActionKind.CALLBACK_SENDER -> placeCall(app, smsSenderRaw, smsSubscriptionId)
            SmsReactionActionKind.CALLBACK_FIXED_NUMBER -> placeCall(app, action.param, smsSubscriptionId)
            SmsReactionActionKind.VIBRATE_CONTINUOUS_MS -> vibrateContinuous(app, action.param)
            SmsReactionActionKind.VIBRATE_PULSE_LOOP_MS -> vibratePulseLoop(app, action.param)
            SmsReactionActionKind.SEND_REPLY_SMS -> sendReplySms(app, smsSenderRaw, action.param, smsSubscriptionId)
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

    @SuppressLint("MissingPermission")
    private fun placeCall(app: Context, numberRaw: String, subscriptionId: Int) {
        val digits = numberRaw.normalizeSmsDigits()
        if (digits.isEmpty()) return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "CALL: no CALL_PHONE permission")
            return
        }
        val uri = Uri.fromParts("tel", digits, null)
        val handle = phoneAccountHandleForSubscription(app, subscriptionId)
        if (handle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val telecom = app.getSystemService(TelecomManager::class.java)
                    ?: throw IllegalStateException("no TelecomManager")
                telecom.placeCall(
                    uri,
                    Bundle().apply {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                    },
                )
                return
            }.onFailure { Log.w(TAG, "TelecomManager.placeCall sub=$subscriptionId", it) }
        }
        runCatching {
            app.startActivity(
                Intent(Intent.ACTION_CALL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure { Log.w(TAG, "call", it) }
    }

    /** На части сборок SDK символ скрыт; через reflection с безопасным fallback на ACTION_CALL. */
    private fun phoneAccountHandleForSubscription(app: Context, subscriptionId: Int): PhoneAccountHandle? {
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return null
        val telecom = app.getSystemService(TelecomManager::class.java) ?: return null
        return runCatching {
            val m = TelecomManager::class.java.getMethod(
                "getPhoneAccountHandleForSubscriptionId",
                Int::class.javaPrimitiveType,
            )
            m.invoke(telecom, subscriptionId) as? PhoneAccountHandle
        }.getOrNull()
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

    private fun sendReplySms(
        app: Context,
        smsSenderRaw: String,
        replyBody: String,
        smsSubscriptionId: Int,
    ) {
        val text = replyBody.trim()
        val destination = smsSenderRaw.trim()
        if (text.isEmpty() || destination.isBlank()) {
            Log.w(TAG, "SMS reply: empty destination or body")
            return
        }
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SMS reply: no SEND_SMS permission")
            return
        }
        runCatching {
            val mgr = smsManagerForSubscription(app, smsSubscriptionId)
            mgr.sendTextMessage(destination, null, text, null, null)
        }.onFailure { Log.w(TAG, "send sms reply", it) }
    }

    private fun smsManagerForSubscription(app: Context, subscriptionId: Int): SmsManager {
        if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
        ) {
            runCatching {
                return SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }.onFailure { Log.w(TAG, "SmsManager for sub=$subscriptionId", it) }
        }
        app.getSystemService(SmsManager::class.java)?.let { return it }
        @Suppress("DEPRECATION")
        return SmsManager.getDefault()
    }
}
