package com.example.bible.auto

/** Можно ли пробовать управление исполнителями. */
enum class ObdComfortSupportLevel {
    READ_ONLY,
    EXPERIMENTAL,
}

data class ObdComfortSupport(
    val level: ObdComfortSupportLevel,
    val summaryRu: String,
    val detailRu: String = "",
    val strategies: List<ObdComfortStrategyId> = emptyList(),
) {
    val allowsExpertAttempts: Boolean get() = level == ObdComfortSupportLevel.EXPERIMENTAL
}

object ObdComfortSupportEvaluator {

    fun evaluate(profile: ObdVehicleProfile?, protocolDescription: String?): ObdComfortSupport {
        val strategies = profile?.let { ObdComfortCatalog.strategiesFor(it, protocolDescription) }.orEmpty()
        val isCan = protocolDescription?.uppercase()?.let {
            it.contains("CAN") || it.contains("15765")
        } == true
        val proto = protocolDescription ?: "неизвестен"

        if (strategies.isEmpty()) {
            return ObdComfortSupport(
                level = ObdComfortSupportLevel.READ_ONLY,
                summaryRu = "Нет стратегий комфорта для этого профиля.",
                detailRu = "Протокол: $proto",
            )
        }

        return ObdComfortSupport(
            level = ObdComfortSupportLevel.EXPERIMENTAL,
            summaryRu = "Экспериментальное управление — выберите стратегию ниже.",
            detailRu = buildString {
                appendLine("Протокол: $proto")
                if (!isCan) {
                    appendLine("Подключение не по CAN — стратегии UCH/VAG могут не сработать.")
                }
                appendLine("На части авто нужна разблокировка UDS (27 xx) — ELM327 не всегда справляется.")
                appendLine("Используйте только на стоящем авто.")
            }.trim(),
            strategies = strategies,
        )
    }
}
