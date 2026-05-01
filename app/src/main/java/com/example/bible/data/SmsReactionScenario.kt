package com.example.bible.data

import android.telephony.SubscriptionManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

enum class SmsReactionActionKind {
    FLASHLIGHT_SECONDS,
    PLAY_MEDIA_URI,
    OPEN_IMAGE_URI,
    CALLBACK_SENDER,
    CALLBACK_FIXED_NUMBER,
    VIBRATE_CONTINUOUS_MS,
    VIBRATE_PULSE_LOOP_MS,
    SEND_REPLY_SMS,
}

/** Максимальная пауза перед следующим действием: 99 ч 59 мин 59 сек. */
const val SMS_REACTION_DELAY_MAX_MS: Long = (99L * 3600L + 59L * 60L + 59L) * 1000L

data class SmsReactionAction(
    val kind: SmsReactionActionKind,
    /** Смысл зависит от [kind]: секунды фонаря; URI медиа/картинки; фиксированный номер (цифры); длительность вибро мс; текст ответного SMS */
    val param: String = "",
    /**
     * После выполнения этого действия подождать столько миллисекунд перед следующим.
     * `0` — следующее действие запускается сразу (на том же главном потоке через [android.os.Handler]).
     */
    val delayBeforeNextMs: Long = 0L,
)

data class SmsReactionScenario(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val enabled: Boolean = true,
    /** Фрагменты номера (только цифры в паттерне); пусто — любой отправитель */
    val senderDigitPatterns: List<String> = emptyList(),
    /** Подстроки текста SMS */
    val bodyPhrases: List<String> = emptyList(),
    /** true — должны встретиться все фразы; false — хотя бы одна */
    val matchAllPhrases: Boolean = false,
    /**
     * Подписка SIM для действия «ответное SMS» в этом сценарии.
     * [SubscriptionManager.INVALID_SUBSCRIPTION_ID] — та же SIM, что входящее SMS.
     */
    val outboundSmsSubscriptionId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
    /**
     * Подписка SIM для действий «обратный звонок» и «звонок на номер».
     * [SubscriptionManager.INVALID_SUBSCRIPTION_ID] — та же SIM, что входящее SMS.
     */
    val outboundCallSubscriptionId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
    val actions: List<SmsReactionAction> = emptyList(),
)

fun String.normalizeSmsDigits(): String = filter { it.isDigit() }

/** Интервал ЧЧ:ММ или ЧЧ:ММ:СС → миллисекунды; пустая строка → 0. */
fun parseSmsReactionDelayHHMM(raw: String): Long {
    val t = raw.trim()
    if (t.isEmpty()) return 0L
    val parts = t.split(':').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size !in 2..3) return 0L
    val h = parts[0].toIntOrNull()?.coerceIn(0, 99) ?: return 0L
    val m = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: return 0L
    val s = if (parts.size == 3) {
        parts[2].toIntOrNull()?.coerceIn(0, 59) ?: return 0L
    } else {
        0
    }
    return ((h * 3600L + m * 60L + s) * 1000L).coerceAtMost(SMS_REACTION_DELAY_MAX_MS)
}

fun formatSmsReactionDelayHHMM(ms: Long): String {
    val capped = ms.coerceIn(0L, SMS_REACTION_DELAY_MAX_MS)
    if (capped <= 0L) return ""
    val totalSec = capped / 1000L
    val h = (totalSec / 3600).coerceAtMost(99)
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private val DelayHmCompleteRegex = Regex("^\\d{1,2}:\\d{2}$")
private val DelayHmsCompleteRegex = Regex("^\\d{1,2}:\\d{2}:\\d{2}$")

fun isCompleteSmsReactionDelayHHMM(raw: String): Boolean {
    val t = raw.trim()
    return DelayHmCompleteRegex.matches(t) || DelayHmsCompleteRegex.matches(t)
}

/** Цифры и «:», авторазделители как ЧЧ:ММ:СС (до 8 символов). */
fun sanitizeSmsReactionDelayHHMMInput(raw: String): String {
    val sb = StringBuilder()
    for (ch in raw) {
        when {
            ch.isDigit() -> {
                if (sb.length >= 8) continue
                val s = sb.toString()
                val colons = s.count { it == ':' }
                if (colons == 0 && s.filter { ch -> ch.isDigit() }.length == 2) sb.append(':')
                if (colons == 1) {
                    val afterFirst = s.substringAfter(':')
                    val minuteDigits = afterFirst.filter { ch -> ch.isDigit() }.length
                    if (minuteDigits == 2) sb.append(':')
                }
                if (sb.length < 8) sb.append(ch)
            }
            ch == ':' -> {
                val s = sb.toString()
                if (s.count { it == ':' } < 2 && s.isNotEmpty() && !s.endsWith(":")) {
                    sb.append(':')
                }
            }
        }
    }
    return sb.toString().take(8)
}

fun scenarioMatchesSms(
    scenario: SmsReactionScenario,
    originatingDigits: String,
    bodyRaw: String,
): Boolean {
    if (!scenario.enabled) return false
    val patterns = scenario.senderDigitPatterns.map { it.normalizeSmsDigits() }.filter { it.isNotEmpty() }
    val senderOk = when {
        patterns.isEmpty() -> true
        originatingDigits.isEmpty() -> false
        else -> patterns.any { p ->
            originatingDigits.endsWith(p) || originatingDigits.contains(p)
        }
    }
    if (!senderOk) return false
    val phrases = scenario.bodyPhrases.map { it.trim() }.filter { it.isNotEmpty() }
    if (phrases.isEmpty()) return true
    val bodyLower = bodyRaw.lowercase(Locale.getDefault())
    val hits = phrases.map { bodyLower.contains(it.lowercase(Locale.getDefault())) }
    return if (scenario.matchAllPhrases) hits.all { it } else hits.any { it }
}

private object SmsReactionJsonKeys {
    const val SCENARIOS = "scenarios"
    const val ID = "id"
    const val TITLE = "title"
    const val ENABLED = "enabled"
    const val SENDERS = "senders"
    const val PHRASES = "phrases"
    const val MATCH_ALL = "matchAll"
    const val OUTBOUND_SMS_SUBSCRIPTION_ID = "outboundSmsSubscriptionId"
    const val OUTBOUND_CALL_SUBSCRIPTION_ID = "outboundCallSubscriptionId"
    /** Старый ключ: одно значение на SMS и звонок */
    const val OUTBOUND_SUBSCRIPTION_ID_LEGACY = "outboundSubscriptionId"
    const val ACTIONS = "actions"
    const val KIND = "kind"
    const val PARAM = "param"
    const val DELAY_BEFORE_NEXT_MS = "delayBeforeNextMs"
}

object SmsReactionJson {
    fun scenariosToJson(list: List<SmsReactionScenario>): String =
        JSONObject().apply {
            put(SmsReactionJsonKeys.SCENARIOS, JSONArray().apply {
                for (s in list) put(scenarioToJson(s))
            })
        }.toString()

    fun parseScenarios(json: String): List<SmsReactionScenario> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(json)
            val arr = root.optJSONArray(SmsReactionJsonKeys.SCENARIOS) ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    scenarioFromJson(o)?.let { add(it) }
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun scenarioToJson(s: SmsReactionScenario): JSONObject =
        JSONObject().apply {
            put(SmsReactionJsonKeys.ID, s.id)
            put(SmsReactionJsonKeys.TITLE, s.title)
            put(SmsReactionJsonKeys.ENABLED, s.enabled)
            put(SmsReactionJsonKeys.SENDERS, JSONArray(s.senderDigitPatterns))
            put(SmsReactionJsonKeys.PHRASES, JSONArray(s.bodyPhrases))
            put(SmsReactionJsonKeys.MATCH_ALL, s.matchAllPhrases)
            put(SmsReactionJsonKeys.OUTBOUND_SMS_SUBSCRIPTION_ID, s.outboundSmsSubscriptionId)
            put(SmsReactionJsonKeys.OUTBOUND_CALL_SUBSCRIPTION_ID, s.outboundCallSubscriptionId)
            put(SmsReactionJsonKeys.ACTIONS, JSONArray().apply {
                for (a in s.actions) put(actionToJson(a))
            })
        }

    private fun actionToJson(a: SmsReactionAction): JSONObject =
        JSONObject().apply {
            put(SmsReactionJsonKeys.KIND, a.kind.name)
            put(SmsReactionJsonKeys.PARAM, a.param)
            if (a.delayBeforeNextMs > 0L) {
                put(SmsReactionJsonKeys.DELAY_BEFORE_NEXT_MS, a.delayBeforeNextMs)
            }
        }

    private fun scenarioFromJson(o: JSONObject): SmsReactionScenario? {
        val id = o.optString(SmsReactionJsonKeys.ID).trim().ifBlank { UUID.randomUUID().toString() }
        val title = o.optString(SmsReactionJsonKeys.TITLE).trim().ifBlank { return null }
        val enabled = o.optBoolean(SmsReactionJsonKeys.ENABLED, true)
        val senders = o.optJSONArray(SmsReactionJsonKeys.SENDERS)?.toStringList().orEmpty()
        val phrases = o.optJSONArray(SmsReactionJsonKeys.PHRASES)?.toStringList().orEmpty()
        val matchAll = o.optBoolean(SmsReactionJsonKeys.MATCH_ALL, false)
        var outboundSms =
            o.optInt(SmsReactionJsonKeys.OUTBOUND_SMS_SUBSCRIPTION_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        var outboundCall =
            o.optInt(SmsReactionJsonKeys.OUTBOUND_CALL_SUBSCRIPTION_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        if (outboundSms == SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
            outboundCall == SubscriptionManager.INVALID_SUBSCRIPTION_ID
        ) {
            val legacy =
                o.optInt(SmsReactionJsonKeys.OUTBOUND_SUBSCRIPTION_ID_LEGACY, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            if (legacy != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                outboundSms = legacy
                outboundCall = legacy
            }
        }
        val actionsArr = o.optJSONArray(SmsReactionJsonKeys.ACTIONS) ?: JSONArray()
        val actions = buildList {
            for (i in 0 until actionsArr.length()) {
                val ao = actionsArr.optJSONObject(i) ?: continue
                actionFromJson(ao)?.let { add(it) }
            }
        }
        return SmsReactionScenario(
            id,
            title,
            enabled,
            senders,
            phrases,
            matchAll,
            outboundSms,
            outboundCall,
            actions,
        )
    }

    private fun actionFromJson(o: JSONObject): SmsReactionAction? {
        val kindName = o.optString(SmsReactionJsonKeys.KIND).trim()
        val kind = runCatching { SmsReactionActionKind.valueOf(kindName) }.getOrNull() ?: return null
        val param = o.optString(SmsReactionJsonKeys.PARAM, "")
        val delayMs = o.optLong(SmsReactionJsonKeys.DELAY_BEFORE_NEXT_MS, 0L)
            .coerceIn(0L, SMS_REACTION_DELAY_MAX_MS)
        return SmsReactionAction(kind, param, delayMs)
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) {
            val s = optString(i).trim()
            if (s.isNotEmpty()) add(s)
        }
    }
}
