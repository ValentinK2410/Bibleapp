package com.example.bible.data

enum class TranslationId(
    val code: String,
    val labelRu: String,
    val shortLabel: String,
    /** Папка в assets: bible/{assetsFolder}/ */
    val assetsFolder: String,
    /** Устарело: тексты всех переводов в assets; null для всех. */
    val onlineCode: String? = null,
) {
    WEB("WEB", "WEB (англ.)", "WEB", "WEB"),
    SYNODAL("SYN", "Синодальный", "СИН", "SYN"),
    NRT("NRT", "Новый русский", "НРП", "NRT"),
    RBO("RBO", "РБО", "РБО", "RBO"),
    BTI("BTI", "Кулаковых", "КУЛ", "BTI"),
    INTERLINEAR("PODSTR", "Подстрочный (Винокуров)", "ПОДСТР", "PODSTR"),
    ;

    companion object {
        fun fromCode(code: String): TranslationId =
            entries.find { it.code == code } ?: SYNODAL
    }
}
