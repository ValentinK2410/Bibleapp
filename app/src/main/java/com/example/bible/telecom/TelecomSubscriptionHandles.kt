package com.example.bible.telecom

import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager

/**
 * Subscription id для телефонного аккаунта ([TelecomManager.getPhoneAccountSubscriptionId], в stubs часто через reflection).
 */
fun TelecomManager.subscriptionIdForPhoneAccount(account: PhoneAccountHandle): Int {
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

/**
 * [PhoneAccountHandle] для исходящего звонка с указанной SIM.
 *
 * Цепочка: SubscriptionManager (на части версий/OEM), затем TelecomManager hidden,
 * затем перебор [TelecomManager.getCallCapablePhoneAccounts].
 */
fun Context.resolvePhoneAccountHandleForSubscription(subscriptionId: Int): PhoneAccountHandle? {
    if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return null
    val telecom = getSystemService(TelecomManager::class.java) ?: return null

    runCatching {
        val sm = getSystemService(SubscriptionManager::class.java) ?: return@runCatching
        val m = SubscriptionManager::class.java.getMethod(
            "getPhoneAccountHandleForSubscriptionId",
            Int::class.javaPrimitiveType,
        )
        (m.invoke(sm, subscriptionId) as? PhoneAccountHandle)?.let { return it }
    }

    runCatching {
        val m = TelecomManager::class.java.getMethod(
            "getPhoneAccountHandleForSubscriptionId",
            Int::class.javaPrimitiveType,
        )
        (m.invoke(telecom, subscriptionId) as? PhoneAccountHandle)?.let { return it }
    }

    for (account in telecom.callCapablePhoneAccounts ?: emptyList()) {
        if (telecom.subscriptionIdForPhoneAccount(account) == subscriptionId) return account
    }
    return null
}
