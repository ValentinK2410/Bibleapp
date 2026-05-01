package com.example.bible.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.example.bible.R
import com.example.bible.telecom.resolvePhoneAccountHandleForSubscription
import com.example.bible.telecom.subscriptionIdForPhoneAccount

data class SimSlot(
    val subscriptionId: Int,
    val label: String,
    val phoneAccountHandle: PhoneAccountHandle?,
)

/** Как в редакторе сценария: `SIM N · оператор` ([R.string.experiment_sms_sim_slot_fmt]). */
@SuppressLint("MissingPermission")
private fun Context.formatSimSlotLabel(
    info: SubscriptionInfo?,
    subId: Int,
    handle: PhoneAccountHandle?,
): String {
    val telecom = getSystemService(TelecomManager::class.java)
    val phoneAccount = handle?.let { h ->
        runCatching { telecom?.getPhoneAccount(h) }.getOrNull()
    }
    val accountLabel = phoneAccount?.label?.toString()?.trim().orEmpty()
        .takeIf { it.isNotBlank() && !isMisleadingSlotDigitLabel(it, info?.simSlotIndex) }.orEmpty()

    val slotNum = info?.simSlotIndex?.takeIf { it >= 0 }?.plus(1)
    if (slotNum != null && info != null) {
        val carrier = info.carrierName?.toString()?.trim().orEmpty()
        val carrierLabel = carrier.ifBlank { getString(R.string.experiment_sms_sim_carrier_unknown) }
        return getString(R.string.experiment_sms_sim_slot_fmt, slotNum, carrierLabel)
    }

    if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
        return getString(R.string.experiment_sim_unknown_subscription_fmt, subId)
    }

    val pa = phoneAccount
    val fromAccount = sequenceOf(
        pa?.label?.toString()?.trim(),
        pa?.shortDescription?.toString()?.trim(),
        pa?.address?.schemeSpecificPart?.trim(),
    )
        .mapNotNull { text ->
            text?.takeIf { s ->
                s.isNotBlank() && !isMisleadingSlotDigitLabel(s, info?.simSlotIndex)
            }
        }
        .firstOrNull()
    return fromAccount ?: accountLabel.ifBlank { getString(R.string.experiment_sim_generic) }
}

private fun isMisleadingSlotDigitLabel(text: String, simSlotIndex: Int?): Boolean {
    val d = text.trim()
    if (!d.matches(Regex("^\\d+$"))) return false
    val n = d.toIntOrNull() ?: return false
    val slot = simSlotIndex ?: return false
    return n == slot + 1 || n == slot
}

/**
 * Список SIM: сначала активные подписки ([SubscriptionManager]) — слот и оператор как в редакторе сценариев;
 * для звонков подставляется [PhoneAccountHandle], если аккаунт в [TelecomManager.callCapablePhoneAccounts].
 * Запасной путь — перебор call-capable аккаунтов (если список подписок пуст).
 */
@SuppressLint("MissingPermission")
fun Context.loadSimSlots(): List<SimSlot> {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }
    val telecom = getSystemService(TelecomManager::class.java) ?: return emptyList()
    val subMgr = getSystemService(SubscriptionManager::class.java) ?: return emptyList()
    val infosBySubId = subMgr.activeSubscriptionInfoList
        ?.associateBy { it.subscriptionId }
        .orEmpty()

    val capableHandles = telecom.callCapablePhoneAccounts?.toSet().orEmpty()

    // Главный путь — как в редакторе сценариев SMS: активные подписки дают слот и оператора,
    // даже если reflection для subscriptionId по PhoneAccountHandle не срабатывает.
    val infosSorted = subMgr.activeSubscriptionInfoList.orEmpty().sortedBy { it.simSlotIndex }
    val fromSubscriptions = infosSorted.map { info ->
        val resolved = resolvePhoneAccountHandleForSubscription(info.subscriptionId)
        val handleForDial =
            resolved?.takeIf { capableHandles.isEmpty() || it in capableHandles }
        SimSlot(
            subscriptionId = info.subscriptionId,
            label = formatSimSlotLabel(info, info.subscriptionId, resolved),
            phoneAccountHandle = handleForDial,
        )
    }

    if (fromSubscriptions.isNotEmpty()) return fromSubscriptions

    val handles = telecom.callCapablePhoneAccounts ?: emptyList()
    val fromAccounts = handles.mapNotNull { handle ->
        val subId = telecom.subscriptionIdForPhoneAccount(handle)
        if (!telecom.isSimSubscriptionLine(handle, subId)) return@mapNotNull null
        val info = infosBySubId[subId]
        val label = formatSimSlotLabel(info, subId, handle)
        SimSlot(
            subscriptionId = subId,
            label = label,
            phoneAccountHandle = handle,
        )
    }.sortedWith(
        compareBy(
            { infosBySubId[it.subscriptionId]?.simSlotIndex?.takeIf { s -> s >= 0 } ?: Int.MAX_VALUE },
            { it.subscriptionId },
        ),
    )

    return fromAccounts
}

private fun TelecomManager.isSimSubscriptionLine(handle: PhoneAccountHandle, subId: Int): Boolean {
    if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return true
    val account = try {
        getPhoneAccount(handle)
    } catch (_: SecurityException) {
        return true
    } catch (_: Exception) {
        return true
    }
    if (account == null) return true
    return (account.capabilities and PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) != 0
}

