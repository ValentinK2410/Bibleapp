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
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.bible.data.SmsReactionAction
import com.example.bible.data.SmsReactionActionKind
import com.example.bible.data.SMS_REACTION_DELAY_MAX_MS
import com.example.bible.data.SmsReactionRepository
import com.example.bible.data.SmsReactionScenario
import com.example.bible.data.scenarioMatchesSms
import com.example.bible.data.normalizeSmsDigits
import com.example.bible.telecom.resolvePhoneAccountHandleForSubscription
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
     * Первое действие выполняется синхронно в [BroadcastReceiver.onReceive]; следующие могут быть отложены через [mainHandler].
     * Для больших пауз звонки/SMS из фона могут блокироваться системой — это ограничение Android.
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
            val smsSub = resolveChosenSubscription(scenario.outboundSmsSubscriptionId, smsSubscriptionId)
            val callSub = resolveChosenSubscription(scenario.outboundCallSubscriptionId, smsSubscriptionId)
            scheduleScenarioActions(app, scenario, dest, messageBodyFull, smsSub, callSub)
        }
    }

    private fun scheduleScenarioActions(
        app: Context,
        scenario: SmsReactionScenario,
        smsSenderRaw: String,
        bodyFull: String,
        smsSubscriptionId: Int,
        callSubscriptionId: Int,
    ) {
        val actions = scenario.actions
        if (actions.isEmpty()) return
        fun step(index: Int) {
            if (index >= actions.size) return
            val action = actions[index]
            runCatching {
                runOneAction(app, action, smsSenderRaw, bodyFull, smsSubscriptionId, callSubscriptionId)
            }.onFailure {
                Log.w(TAG, "action ${action.kind}", it)
            }
            val next = index + 1
            if (next >= actions.size) return
            val delayMs = action.delayBeforeNextMs.coerceIn(0L, SMS_REACTION_DELAY_MAX_MS)
            val runNext = Runnable { step(next) }
            if (delayMs <= 0L) {
                mainHandler.post(runNext)
            } else {
                mainHandler.postDelayed(runNext, delayMs)
            }
        }
        step(0)
    }

    private fun resolveChosenSubscription(chosenSubscriptionId: Int, incomingSubscriptionId: Int): Int {
        return if (chosenSubscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            chosenSubscriptionId
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
        callSubscriptionId: Int,
    ) {
        when (action.kind) {
            SmsReactionActionKind.FLASHLIGHT_SECONDS -> flashlightSeconds(app, action.param)
            SmsReactionActionKind.PLAY_MEDIA_URI -> playMediaUri(app, action.param)
            SmsReactionActionKind.OPEN_IMAGE_URI -> openImageUri(app, action.param)
            SmsReactionActionKind.CALLBACK_SENDER -> placeCall(app, smsSenderRaw, callSubscriptionId)
            SmsReactionActionKind.CALLBACK_FIXED_NUMBER -> placeCall(app, action.param, callSubscriptionId)
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
        val telecom = app.getSystemService(TelecomManager::class.java)
        val handle = app.resolvePhoneAccountHandleForSubscription(subscriptionId)
        if (handle == null && subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.w(TAG, "CALL: PhoneAccountHandle=null sub=$subscriptionId; нужны SIM и часто READ_PHONE_STATE")
        }

        if (telecom != null && handle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val permitted =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching { telecom.isOutgoingCallPermitted(handle) }.getOrElse { true }
                } else {
                    true
                }
            if (permitted) {
                runCatching {
                    telecom.placeCall(
                        uri,
                        Bundle().apply {
                            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                        },
                    )
                    return
                }.onFailure { Log.w(TAG, "TelecomManager.placeCall sub=$subscriptionId", it) }
            } else {
                Log.w(TAG, "CALL: isOutgoingCallPermitted=false sub=$subscriptionId, пробуем ACTION_CALL")
            }
        }

        val dialerPkg = telecom?.defaultDialerPackage?.takeIf { !it.isNullOrBlank() }
        fun launchCallIntent(pkg: String?): Boolean =
            runCatching {
                app.startActivity(
                    Intent(Intent.ACTION_CALL, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (handle != null) {
                            putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                        }
                        enrichIntentDualSimFromSubscription(app, subscriptionId)
                        pkg?.let { setPackage(it) }
                    },
                )
                true
            }.getOrElse {
                Log.w(TAG, "ACTION_CALL pkg=$pkg sub=$subscriptionId", it)
                false
            }

        if (launchCallIntent(dialerPkg)) return
        launchCallIntent(null)
    }

    /** Типичные extras для двух SIM (Samsung/MTK и др.), когда системный dialer их учитывает. */
    private fun Intent.enrichIntentDualSimFromSubscription(app: Context, subscriptionId: Int) {
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return
        runCatching {
            val sm = app.getSystemService(SubscriptionManager::class.java) ?: return@runCatching
            @SuppressLint("MissingPermission")
            val info = sm.getActiveSubscriptionInfo(subscriptionId) ?: return@runCatching
            val slot = info.simSlotIndex
            if (slot < 0) return@runCatching
            putExtra("com.android.phone.extra.slot", slot)
            putExtra("com.android.phone.extra.slotIndex", slot)
            putExtra("Subscription", subscriptionId)
            putExtra("subscription", subscriptionId)
            putExtra("slot", slot)
            putExtra("simSlot", slot)
            putExtra("slot_id", slot)
        }
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
