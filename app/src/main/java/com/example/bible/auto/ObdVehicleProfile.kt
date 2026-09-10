package com.example.bible.auto

/** Шаг подбора протокола OBD для ELM327. */
data class ObdProtocolAttempt(
    val setProtocol: String,
    val prepCommands: List<String> = listOf("ATPC"),
    val probeCommands: List<String> = listOf("0100"),
    val delayAfterProtocolMs: Long = 600,
    val probeTimeoutMs: Long = 14_000L,
    val labelRu: String = setProtocol,
)

/**
 * Профиль автомобиля: протоколы подключения и приоритетные PID для опроса.
 * Разные профили — разные машины; что-то сработает, что-то нет (это нормально для OBD).
 */
enum class ObdVehicleProfile(
    val titleRu: String,
    val attempts: List<ObdProtocolAttempt>,
    /** PID mode 01, опрашиваются первыми (если ECU поддерживает). */
    val priorityPids: List<String>,
    val initDelayMs: Long = 1_000L,
    val profileHintRu: String = "",
) {
    UNIVERSAL(
        titleRu = "Универсальный (любое авто)",
        attempts = listOf(
            ObdProtocolAttempt(
                setProtocol = "ATSP0",
                prepCommands = emptyList(),
                delayAfterProtocolMs = 300,
                probeTimeoutMs = 40_000L,
                labelRu = "Авто (ATSP0)",
            ),
        ),
        priorityPids = listOf("0C", "0D", "05", "04", "0F", "11", "42", "2F", "0B", "10", "1F", "31", "33", "46", "5C"),
        profileHintRu = "Автоподбор протокола. Подходит для первой диагностики.",
    ),
    LARGUS_LOGAN_2015(
        titleRu = "Lada Largus / Logan I (~2015, K7M)",
        initDelayMs = 1_500L,
        attempts = listOf(
            ObdProtocolAttempt("ATSP0", listOf("ATPC"), delayAfterProtocolMs = 400, probeTimeoutMs = 40_000L, labelRu = "Авто (ATSP0)"),
            ObdProtocolAttempt("ATSP5", listOf("ATPC"), delayAfterProtocolMs = 900, probeTimeoutMs = 16_000L, labelRu = "KWP fast (ATSP5)"),
            ObdProtocolAttempt("ATSP4", listOf("ATPC", "ATIIA33"), delayAfterProtocolMs = 1_200, probeTimeoutMs = 18_000L, labelRu = "KWP slow (ATSP4)"),
            ObdProtocolAttempt("ATSP3", listOf("ATPC", "ATIIA33", "ATIB10"), delayAfterProtocolMs = 1_500, probeTimeoutMs = 20_000L, labelRu = "ISO 9141 (ATSP3)"),
            ObdProtocolAttempt("ATSP6", listOf("ATPC"), delayAfterProtocolMs = 600, probeTimeoutMs = 12_000L, labelRu = "CAN 500k (ATSP6)"),
            ObdProtocolAttempt("ATSP8", listOf("ATPC"), delayAfterProtocolMs = 600, probeTimeoutMs = 12_000L, labelRu = "CAN 250k (ATSP8)"),
        ),
        priorityPids = listOf("0C", "0D", "05", "04", "0F", "11", "42", "0B", "2F", "10", "1F", "33"),
        profileHintRu = "Двигатель Renault на K-Line. Обороты/температура обычно работают; MAF может быть «нет данных».",
    ),
    LOGAN2_SANDERO_CAN(
        titleRu = "Logan II / Sandero / Stepway (CAN)",
        initDelayMs = 1_200L,
        attempts = listOf(
            ObdProtocolAttempt("ATSP6", listOf("ATPC"), delayAfterProtocolMs = 600, probeTimeoutMs = 14_000L, labelRu = "CAN 500k (ATSP6)"),
            ObdProtocolAttempt("ATSP8", listOf("ATPC"), delayAfterProtocolMs = 600, probeTimeoutMs = 14_000L, labelRu = "CAN 250k (ATSP8)"),
            ObdProtocolAttempt("ATSP0", listOf("ATPC"), delayAfterProtocolMs = 400, probeTimeoutMs = 40_000L, labelRu = "Авто (ATSP0)"),
            ObdProtocolAttempt("ATSP5", listOf("ATPC"), delayAfterProtocolMs = 900, probeTimeoutMs = 16_000L, labelRu = "KWP fast (ATSP5)"),
        ),
        priorityPids = listOf("0C", "0D", "05", "04", "0F", "11", "42", "2F", "46", "0B", "10"),
        profileHintRu = "Logan 2 на CAN. Комфорт — стратегия Renault UCH (CAN 745).",
    ),
    VAG_PQ35(
        titleRu = "VAG (VW / Skoda / Seat, PQ35)",
        attempts = listOf(
            ObdProtocolAttempt("ATSP6", listOf("ATPC"), delayAfterProtocolMs = 500, probeTimeoutMs = 12_000L, labelRu = "CAN 500k (ATSP6)"),
            ObdProtocolAttempt("ATSP0", listOf("ATPC"), delayAfterProtocolMs = 400, probeTimeoutMs = 35_000L, labelRu = "Авто (ATSP0)"),
        ),
        priorityPids = listOf("0C", "0D", "05", "04", "0F", "11", "42", "2F", "46", "5C"),
        profileHintRu = "CAN VAG. Комфорт — стратегия BCM 7A0 (эксперимент).",
    ),
}

object ObdInitHelper {
    fun isProbeSuccessful(response: String): Boolean {
        val upper = response.uppercase()
        if (upper.contains("UNABLE TO CONNECT")) return false
        if (upper.contains("NO DATA") && !containsEcuPayload(upper)) return false
        if (upper.contains("BUS INIT") && upper.contains("ERROR") && !containsEcuPayload(upper)) return false
        return containsEcuPayload(upper)
    }

    private fun containsEcuPayload(upper: String): Boolean {
        val normalized = upper.replace("SEARCHING...", " ")
        val tokens = normalized
            .split(Regex("[^0-9A-F]+"))
            .filter { it.length == 2 }
        if (tokens.contains("41") && tokens.size >= 3) return true
        if (tokens.contains("49") && tokens.size >= 3) return true
        if (Regex("41\\s*00").containsMatchIn(normalized)) return true
        return false
    }

    fun failureHint(profile: ObdVehicleProfile, log: String = ""): String = buildString {
        appendLine("Не удалось связаться с блоком двигателя (ECU).")
        appendLine()
        appendLine(profile.profileHintRu)
        appendLine()
        appendLine("Общие проверки:")
        appendLine("• Зажигание ВКЛ, лучше завести мотор.")
        appendLine("• ELM327 v1.5 (PIC18F25K80), не v2.1-клон.")
        appendLine("• Закройте Torque/Car Scanner.")
        if (log.uppercase().contains("CAN ERROR")) {
            appendLine()
            appendLine("CAN ERROR на всех протоколах: проверьте разъём, предохранитель OBD, качество адаптера.")
        }
    }
}
