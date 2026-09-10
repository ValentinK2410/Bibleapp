package com.example.bible.auto

data class ObdPidDefinition(
    val mode: String = "01",
    val pid: String,
    val titleRu: String,
    val unit: String,
    val parser: (bytes: List<Int>) -> String,
)

object ObdPidDefinitions {
    val standard: List<ObdPidDefinition> = listOf(
        ObdPidDefinition(
            pid = "0C",
            titleRu = "Обороты двигателя",
            unit = "об/мин",
            parser = { b ->
                if (b.size < 2) "—"
                else ((b[0] * 256 + b[1]) / 4).toString()
            },
        ),
        ObdPidDefinition(
            pid = "0D",
            titleRu = "Скорость",
            unit = "км/ч",
            parser = { b -> b.firstOrNull()?.toString() ?: "—" },
        ),
        ObdPidDefinition(
            pid = "05",
            titleRu = "Температура охлаждения",
            unit = "°C",
            parser = { b -> b.firstOrNull()?.let { (it - 40).toString() } ?: "—" },
        ),
        ObdPidDefinition(
            pid = "0F",
            titleRu = "Температура воздуха на впуске",
            unit = "°C",
            parser = { b -> b.firstOrNull()?.let { (it - 40).toString() } ?: "—" },
        ),
        ObdPidDefinition(
            pid = "46",
            titleRu = "Температура окружающей среды",
            unit = "°C",
            parser = { b -> b.firstOrNull()?.let { (it - 40).toString() } ?: "—" },
        ),
        ObdPidDefinition(
            pid = "5C",
            titleRu = "Температура масла",
            unit = "°C",
            parser = { b -> b.firstOrNull()?.let { (it - 40).toString() } ?: "—" },
        ),
        ObdPidDefinition(
            pid = "11",
            titleRu = "Положение дросселя",
            unit = "%",
            parser = { b -> b.firstOrNull()?.let { (it * 100 / 255).toString() } ?: "—" },
        ),
        ObdPidDefinition(
            pid = "04",
            titleRu = "Нагрузка двигателя",
            unit = "%",
            parser = { b -> b.firstOrNull()?.let { (it * 100 / 255).toString() } ?: "—" },
        ),
        ObdPidDefinition(
            pid = "2F",
            titleRu = "Уровень топлива",
            unit = "%",
            parser = { b -> b.firstOrNull()?.let { (it * 100 / 255).toString() } ?: "—" },
        ),
        ObdPidDefinition(
            pid = "42",
            titleRu = "Напряжение бортсети",
            unit = "В",
            parser = { b ->
                if (b.size < 2) "—"
                else "%.1f".format((b[0] * 256 + b[1]) / 1000.0)
            },
        ),
        ObdPidDefinition(
            pid = "10",
            titleRu = "Расход воздуха (MAF)",
            unit = "г/с",
            parser = { b ->
                if (b.size < 2) "—"
                else "%.2f".format((b[0] * 256 + b[1]) / 100.0)
            },
        ),
        ObdPidDefinition(
            pid = "0B",
            titleRu = "Давление во впускном коллекторе",
            unit = "кПа",
            parser = { b -> b.firstOrNull()?.toString() ?: "—" },
        ),
        ObdPidDefinition(
            pid = "1F",
            titleRu = "Время работы с запуска",
            unit = "с",
            parser = { b ->
                if (b.size < 2) "—"
                else (b[0] * 256 + b[1]).toString()
            },
        ),
        ObdPidDefinition(
            pid = "31",
            titleRu = "Пробег после сброса ошибок",
            unit = "км",
            parser = { b ->
                if (b.size < 2) "—"
                else (b[0] * 256 + b[1]).toString()
            },
        ),
        ObdPidDefinition(
            pid = "33",
            titleRu = "Давление в барометрическом датчике",
            unit = "кПа",
            parser = { b -> b.firstOrNull()?.toString() ?: "—" },
        ),
    )

    fun parseResponse(raw: String, def: ObdPidDefinition): String {
        if (ObdHexParser.isNoData(raw)) return "нет данных"
        if (ObdHexParser.isError(raw)) return "ошибка"
        val mode = def.mode.toIntOrNull(16) ?: 1
        val bytes = ObdHexParser.dataBytesForModePid(raw, mode, def.pid) ?: return "—"
        if (bytes.isEmpty()) return "—"
        return def.parser(bytes)
    }

    fun byPid(pid: String): ObdPidDefinition? =
        standard.find { it.pid.equals(pid, ignoreCase = true) }
}
