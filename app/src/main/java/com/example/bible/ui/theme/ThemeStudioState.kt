package com.example.bible.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject

data class FieldLook(
    val heightDp: Float = 56f,
    val widthFraction: Float = 1f,
    val backgroundArgb: Int? = null,
    val textArgb: Int? = null,
    val borderArgb: Int? = null,
    val borderWidthDp: Float = 1.5f,
    val alpha: Float = 1f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("h", heightDp.toDouble())
        put("w", widthFraction.toDouble())
        put("bw", borderWidthDp.toDouble())
        put("a", alpha.toDouble())
        backgroundArgb?.let { put("bg", it) }
        textArgb?.let { put("fg", it) }
        borderArgb?.let { put("bd", it) }
    }

    companion object {
        fun parse(obj: JSONObject): FieldLook = FieldLook(
            heightDp = obj.optDouble("h", 56.0).toFloat().coerceIn(40f, 220f),
            widthFraction = obj.optDouble("w", 1.0).toFloat().coerceIn(0.35f, 1f),
            backgroundArgb = if (obj.has("bg")) obj.optInt("bg") else null,
            textArgb = if (obj.has("fg")) obj.optInt("fg") else null,
            borderArgb = if (obj.has("bd")) obj.optInt("bd") else null,
            borderWidthDp = obj.optDouble("bw", 1.5).toFloat().coerceIn(0f, 8f),
            alpha = obj.optDouble("a", 1.0).toFloat().coerceIn(0.15f, 1f),
        )
    }
}

data class ThemeStudioState(
    val textScale: Float = 1f,
    val colorOverrides: Map<String, Int> = emptyMap(),
    val fields: Map<String, FieldLook> = emptyMap(),
) {
    fun toJson(): String = JSONObject().apply {
        put("textScale", textScale.toDouble())
        put("colors", JSONObject().apply {
            colorOverrides.forEach { (key, value) -> put(key, value) }
        })
        put("fields", JSONObject().apply {
            fields.forEach { (key, look) -> put(key, look.toJson()) }
        })
    }.toString()

    companion object {
        fun parse(raw: String?): ThemeStudioState {
            if (raw.isNullOrBlank()) return ThemeStudioState()
            return try {
                val obj = JSONObject(raw)
                val colors = obj.optJSONObject("colors")
                val colorMap = mutableMapOf<String, Int>()
                val allowed = ColorRoles.keys.map { it.first }.toSet()
                colors?.keys()?.forEach { key ->
                    if (key in allowed) colorMap[key] = colors.optInt(key)
                }
                val fieldsObj = obj.optJSONObject("fields")
                val fieldMap = mutableMapOf<String, FieldLook>()
                fieldsObj?.keys()?.forEach { key ->
                    fieldsObj.optJSONObject(key)?.let { fieldMap[key] = FieldLook.parse(it) }
                }
                ThemeStudioState(
                    textScale = obj.optDouble("textScale", 1.0).toFloat().coerceIn(0.8f, 1.8f),
                    colorOverrides = colorMap,
                    fields = fieldMap,
                )
            } catch (_: Exception) {
                ThemeStudioState()
            }
        }
    }
}

data class ThemeFieldController(
    val looks: Map<String, FieldLook> = emptyMap(),
    val save: (String, FieldLook) -> Unit = { _, _ -> },
)

val LocalThemeFieldController = staticCompositionLocalOf { ThemeFieldController() }

object ColorRoles {
    val keys: List<Pair<String, String>> = listOf(
        "primary" to "Основной",
        "onPrimary" to "Текст на основном",
        "primaryContainer" to "Контейнер",
        "secondary" to "Второй",
        "tertiary" to "Третий",
        "background" to "Фон",
        "onBackground" to "Текст на фоне",
        "surface" to "Поверхность",
        "onSurface" to "Текст поверхности",
        "surfaceVariant" to "Полутон поверхности",
        "outline" to "Обводка",
        "surfaceContainerLow" to "Нижний полутон",
        "surfaceContainer" to "Средний полутон",
        "surfaceContainerHigh" to "Верхний полутон",
        "error" to "Ошибка",
    )
}

fun ColorScheme.withOverrides(overrides: Map<String, Int>): ColorScheme {
    if (overrides.isEmpty()) return this
    fun pick(key: String, fallback: Color): Color =
        overrides[key]?.let { Color(it) } ?: fallback
    return copy(
        primary = pick("primary", primary),
        onPrimary = pick("onPrimary", onPrimary),
        primaryContainer = pick("primaryContainer", primaryContainer),
        secondary = pick("secondary", secondary),
        tertiary = pick("tertiary", tertiary),
        background = pick("background", background),
        onBackground = pick("onBackground", onBackground),
        surface = pick("surface", surface),
        onSurface = pick("onSurface", onSurface),
        surfaceVariant = pick("surfaceVariant", surfaceVariant),
        outline = pick("outline", outline),
        surfaceContainerLow = pick("surfaceContainerLow", surfaceContainerLow),
        surfaceContainer = pick("surfaceContainer", surfaceContainer),
        surfaceContainerHigh = pick("surfaceContainerHigh", surfaceContainerHigh),
        error = pick("error", error),
    )
}

fun ColorScheme.roleColor(key: String): Color = when (key) {
    "primary" -> primary
    "onPrimary" -> onPrimary
    "primaryContainer" -> primaryContainer
    "secondary" -> secondary
    "tertiary" -> tertiary
    "background" -> background
    "onBackground" -> onBackground
    "surface" -> surface
    "onSurface" -> onSurface
    "surfaceVariant" -> surfaceVariant
    "outline" -> outline
    "surfaceContainerLow" -> surfaceContainerLow
    "surfaceContainer" -> surfaceContainer
    "surfaceContainerHigh" -> surfaceContainerHigh
    "error" -> error
    else -> primary
}

fun Color.toStoredArgb(): Int = toArgb()
