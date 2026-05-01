package com.example.bible.telecom

import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager

/**
 * Subscription id для телефонного аккаунта (на части SDK только через reflection).
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
 * Сначала пробуем системный метод (может быть недоступен как hidden API),
 * затем сопоставляем [TelecomManager.getCallCapablePhoneAccounts] — без этого система
 * часто показывает диалог выбора SIM.
 */
fun TelecomManager.phoneAccountHandleForSubscriptionId(subscriptionId: Int): PhoneAccountHandle? {
    if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return null
    runCatching {
        val m = TelecomManager::class.java.getMethod(
            "getPhoneAccountHandleForSubscriptionId",
            Int::class.javaPrimitiveType,
        )
        (m.invoke(this, subscriptionId) as? PhoneAccountHandle)?.let { return it }
    }
    for (account in callCapablePhoneAccounts ?: emptyList()) {
        if (subscriptionIdForPhoneAccount(account) == subscriptionId) return account
    }
    return null
}
