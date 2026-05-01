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

data class SmsReactionAction(
    val kind: SmsReactionActionKind,
    /** Смысл зависит от [kind]: секунды фонаря; URI медиа/картинки; фиксированный номер (цифры); длительность вибро мс; текст ответного SMS */
    val param: String = "",
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
     * Подписка SIM для исходящих действий сценария (ответное SMS, обратный/фиксированный звонок).
     * [SubscriptionManager.INVALID_SUBSCRIPTION_ID] — использовать ту же SIM, на которую пришло SMS (extras `"subscription"`).
     */
    val outboundSubscriptionId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
    val actions: List<SmsReactionAction> = emptyList(),
)

fun String.normalizeSmsDigits(): String = filter { it.isDigit() }

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
    const val OUTBOUND_SUBSCRIPTION_ID = "outboundSubscriptionId"
    const val ACTIONS = "actions"
    const val KIND = "kind"
    const val PARAM = "param"
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
            put(SmsReactionJsonKeys.OUTBOUND_SUBSCRIPTION_ID, s.outboundSubscriptionId)
            put(SmsReactionJsonKeys.ACTIONS, JSONArray().apply {
                for (a in s.actions) put(actionToJson(a))
            })
        }

    private fun actionToJson(a: SmsReactionAction): JSONObject =
        JSONObject().apply {
            put(SmsReactionJsonKeys.KIND, a.kind.name)
            put(SmsReactionJsonKeys.PARAM, a.param)
        }

    private fun scenarioFromJson(o: JSONObject): SmsReactionScenario? {
        val id = o.optString(SmsReactionJsonKeys.ID).trim().ifBlank { UUID.randomUUID().toString() }
        val title = o.optString(SmsReactionJsonKeys.TITLE).trim().ifBlank { return null }
        val enabled = o.optBoolean(SmsReactionJsonKeys.ENABLED, true)
        val senders = o.optJSONArray(SmsReactionJsonKeys.SENDERS)?.toStringList().orEmpty()
        val phrases = o.optJSONArray(SmsReactionJsonKeys.PHRASES)?.toStringList().orEmpty()
        val matchAll = o.optBoolean(SmsReactionJsonKeys.MATCH_ALL, false)
        val outboundSub =
            o.optInt(SmsReactionJsonKeys.OUTBOUND_SUBSCRIPTION_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        val actionsArr = o.optJSONArray(SmsReactionJsonKeys.ACTIONS) ?: JSONArray()
        val actions = buildList {
            for (i in 0 until actionsArr.length()) {
                val ao = actionsArr.optJSONObject(i) ?: continue
                actionFromJson(ao)?.let { add(it) }
            }
        }
        return SmsReactionScenario(id, title, enabled, senders, phrases, matchAll, outboundSub, actions)
    }

    private fun actionFromJson(o: JSONObject): SmsReactionAction? {
        val kindName = o.optString(SmsReactionJsonKeys.KIND).trim()
        val kind = runCatching { SmsReactionActionKind.valueOf(kindName) }.getOrNull() ?: return null
        val param = o.optString(SmsReactionJsonKeys.PARAM, "")
        return SmsReactionAction(kind, param)
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) {
            val s = optString(i).trim()
            if (s.isNotEmpty()) add(s)
        }
    }
}
