package com.example.bible.auto

/** Стратегия управления исполнителями — зависит от марки и шины. */
enum class ObdComfortStrategyId(val titleRu: String, val hintRu: String) {
    RENAULT_UCH_CAN(
        titleRu = "Renault UCH (CAN 745)",
        hintRu = "Для Largus/Logan II с CAN. UDS на блок кузова 0x745. Может потребоваться заведённый двигатель.",
    ),
    GENERIC_ECU_7E0(
        titleRu = "Универсальный ECU (7E0)",
        hintRu = "Запись в блок двигателя — редко управляет стёклами, но иногда срабатывает на других марках.",
    ),
    VW_BCM_7A0(
        titleRu = "VAG BCM (7A0)",
        hintRu = "Эксперимент для VAG-платформы по CAN.",
    ),
}

data class ObdComfortCommandSet(
    val strategy: ObdComfortStrategyId,
    val action: ObdComfortAction,
    val steps: List<String>,
)

object ObdComfortCatalog {

    fun strategiesFor(profile: ObdVehicleProfile, protocolDescription: String?): List<ObdComfortStrategyId> {
        val isCan = protocolDescription?.uppercase()?.let {
            it.contains("CAN") || it.contains("15765")
        } == true
        return when (profile) {
            ObdVehicleProfile.LARGUS_LOGAN_2015,
            ObdVehicleProfile.LOGAN2_SANDERO_CAN,
            -> buildList {
                if (isCan) add(ObdComfortStrategyId.RENAULT_UCH_CAN)
                add(ObdComfortStrategyId.GENERIC_ECU_7E0)
            }
            ObdVehicleProfile.VAG_PQ35,
            -> listOf(ObdComfortStrategyId.VW_BCM_7A0, ObdComfortStrategyId.GENERIC_ECU_7E0)
            ObdVehicleProfile.UNIVERSAL,
            -> listOf(
                ObdComfortStrategyId.GENERIC_ECU_7E0,
                ObdComfortStrategyId.RENAULT_UCH_CAN,
                ObdComfortStrategyId.VW_BCM_7A0,
            )
        }
    }

    fun commands(strategy: ObdComfortStrategyId, action: ObdComfortAction): List<String> {
        val uchHeader = listOf("AT SH 745", "AT H1", "AT CAF0", "AT CFC1", "10 03")
        val ecuHeader = listOf("AT SH 7E0", "AT H1", "AT CAF0")
        val vagHeader = listOf("AT SH 7A0", "AT H1", "AT CAF0", "10 03")
        return when (strategy) {
            ObdComfortStrategyId.RENAULT_UCH_CAN -> uchHeader + when (action) {
                ObdComfortAction.CENTRAL_LOCK -> listOf("30 01 00 03", "31 01 00 01")
                ObdComfortAction.CENTRAL_UNLOCK -> listOf("30 01 00 03", "31 01 00 02")
                ObdComfortAction.WINDOW_DRIVER_UP -> listOf("31 01 20 01")
                ObdComfortAction.WINDOW_DRIVER_DOWN -> listOf("31 01 20 02")
                ObdComfortAction.WINDOW_ALL_UP -> listOf("31 01 20 03")
            }
            ObdComfortStrategyId.VW_BCM_7A0 -> vagHeader + when (action) {
                ObdComfortAction.CENTRAL_LOCK -> listOf("2F 01 03 01")
                ObdComfortAction.CENTRAL_UNLOCK -> listOf("2F 01 03 00")
                ObdComfortAction.WINDOW_DRIVER_UP -> listOf("2F 01 04 01")
                ObdComfortAction.WINDOW_DRIVER_DOWN -> listOf("2F 01 04 00")
                ObdComfortAction.WINDOW_ALL_UP -> listOf("2F 01 05 01")
            }
            ObdComfortStrategyId.GENERIC_ECU_7E0 -> ecuHeader + when (action) {
                ObdComfortAction.CENTRAL_LOCK -> listOf("2E F1 80 01")
                ObdComfortAction.CENTRAL_UNLOCK -> listOf("2E F1 80 00")
                ObdComfortAction.WINDOW_DRIVER_UP -> listOf("2E F1 90 01")
                ObdComfortAction.WINDOW_DRIVER_DOWN -> listOf("2E F1 90 00")
                ObdComfortAction.WINDOW_ALL_UP -> listOf("2E F1 91 01")
            }
        }
    }
}
