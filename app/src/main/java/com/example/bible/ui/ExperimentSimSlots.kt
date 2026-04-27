package com.example.bible.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimSlot(
    val subscriptionId: Int,
    val label: String,
    val phoneAccountHandle: PhoneAccountHandle?,
)

/**
 * Список SIM строится от [TelecomManager.callCapablePhoneAccounts], чтобы у каждого пункта
 * был реальный [PhoneAccountHandle] — иначе звонок уходит без аккаунта и система снова
 * показывает выбор SIM.
 */
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

    val handles = telecom.callCapablePhoneAccounts ?: emptyList()
    val fromAccounts = handles.mapNotNull { handle ->
        val subId = telecom.subscriptionIdForAccount(handle)
        if (!telecom.isSimSubscriptionLine(handle, subId)) return@mapNotNull null
        val info = infosBySubId[subId]
        val name = info?.displayName?.toString()?.trim().orEmpty()
        val slotIdx = info?.simSlotIndex
        val label = when {
            name.isNotEmpty() && slotIdx != null && slotIdx >= 0 ->
                "$name (SIM ${slotIdx + 1})"
            name.isNotEmpty() -> name
            slotIdx != null && slotIdx >= 0 -> "SIM ${slotIdx + 1}"
            subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID -> "SIM ($subId)"
            else -> handle.id?.toString() ?: "SIM"
        }
        SimSlot(
            subscriptionId = subId,
            label = label,
            phoneAccountHandle = handle,
        )
    }

    if (fromAccounts.isNotEmpty()) return fromAccounts

    // Запасной путь, если аккаунтов нет (редко)
    val infos = subMgr.activeSubscriptionInfoList ?: return emptyList()
    if (infos.isEmpty()) return emptyList()
    return infos.map { info ->
        val handle = telecom.findHandleForSubscription(info.subscriptionId)
        val name = info.displayName?.toString()?.trim().orEmpty()
        val slotNum = info.simSlotIndex + 1
        val label = when {
            name.isNotEmpty() -> "$name (SIM $slotNum)"
            else -> "SIM $slotNum"
        }
        SimSlot(
            subscriptionId = info.subscriptionId,
            label = label,
            phoneAccountHandle = handle,
        )
    }
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

private fun TelecomManager.subscriptionIdForAccount(account: PhoneAccountHandle): Int {
    return try {
        val method = TelecomManager::class.java.getMethod(
            "getPhoneAccountSubscriptionId",
            PhoneAccountHandle::class.java,
        )
        (method.invoke(this, account) as? Int)
            ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
    } catch (_: Exception) {
        SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }
}

private fun TelecomManager.findHandleForSubscription(subscriptionId: Int): PhoneAccountHandle? {
    for (account in callCapablePhoneAccounts) {
        if (subscriptionIdForAccount(account) == subscriptionId) return account
    }
    return null
}
