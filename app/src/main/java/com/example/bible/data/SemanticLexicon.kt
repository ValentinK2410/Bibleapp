package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Ось для фильтрации пресет-базы и группировки правил в UI.
 * Свет / тьма, позитив / негатив, добро / зло — задаются явно для каждого правила.
 */
enum class LexiconTone(
    val id: String,
    val labelRu: String,
    val sortKey: Int,
) {
    LIGHT("light", "Светлая сторона (Бог, небо, истина)", 0),
    POSITIVE("positive", "Позитивное (радость, мир, утешение)", 1),
    GOOD("good", "Добро (любовь, милость, правда)", 2),
    NEUTRAL("neutral", "Нейтральное (описательное)", 3),
    NEGATIVE("negative", "Негативное (скорбь, страх, беда)", 4),
    EVIL("evil", "Зло (грех, ложь, насилие)", 5),
    DARK("dark", "Тёмная сторона (сатана, ад, смерть)", 6),
    ;

    companion object {
        fun fromId(id: String): LexiconTone? = entries.find { it.id == id }
    }
}

/**
 * Медиа к правилу лексикона: URL, локальный URI, или id из «Медиа → картинки/видео».
 */
data class LexiconMediaRefs(
    val audioUrl: String? = null,
    val audioFileUri: String? = null,
    /** Ссылка на запись в «Медиа → Аудио». */
    val audioLibraryId: String? = null,
    val imageUrl: String? = null,
    val imageFileUri: String? = null,
    val videoUrl: String? = null,
    val videoFileUri: String? = null,
    val imageLibraryId: String? = null,
    val videoLibraryId: String? = null,
) {
    fun hasAny(): Boolean = listOf(
        audioUrl, audioFileUri, audioLibraryId, imageUrl, imageFileUri, videoUrl, videoFileUri,
        imageLibraryId, videoLibraryId,
    ).any { !it.isNullOrBlank() }

    fun toJson(): JSONObject = JSONObject().apply {
        audioUrl?.takeIf { it.isNotBlank() }?.let { put("audioUrl", it) }
        audioFileUri?.takeIf { it.isNotBlank() }?.let { put("audioFileUri", it) }
        audioLibraryId?.takeIf { it.isNotBlank() }?.let { put("audioLibraryId", it) }
        imageUrl?.takeIf { it.isNotBlank() }?.let { put("imageUrl", it) }
        imageFileUri?.takeIf { it.isNotBlank() }?.let { put("imageFileUri", it) }
        videoUrl?.takeIf { it.isNotBlank() }?.let { put("videoUrl", it) }
        videoFileUri?.takeIf { it.isNotBlank() }?.let { put("videoFileUri", it) }
        imageLibraryId?.takeIf { it.isNotBlank() }?.let { put("imageLibraryId", it) }
        videoLibraryId?.takeIf { it.isNotBlank() }?.let { put("videoLibraryId", it) }
    }

    companion object {
        fun fromJson(j: JSONObject?): LexiconMediaRefs {
            if (j == null) return LexiconMediaRefs()
            return LexiconMediaRefs(
                audioUrl = j.optString("audioUrl", "").takeIf { it.isNotBlank() },
                audioFileUri = j.optString("audioFileUri", "").takeIf { it.isNotBlank() },
                audioLibraryId = j.optString("audioLibraryId", "").takeIf { it.isNotBlank() },
                imageUrl = j.optString("imageUrl", "").takeIf { it.isNotBlank() },
                imageFileUri = j.optString("imageFileUri", "").takeIf { it.isNotBlank() },
                videoUrl = j.optString("videoUrl", "").takeIf { it.isNotBlank() },
                videoFileUri = j.optString("videoFileUri", "").takeIf { it.isNotBlank() },
                imageLibraryId = j.optString("imageLibraryId", "").takeIf { it.isNotBlank() },
                videoLibraryId = j.optString("videoLibraryId", "").takeIf { it.isNotBlank() },
            )
        }
    }
}

/**
 * Одно правило подсветки: группа слов, метка смысла, свой цвет и способ отображения.
 */
data class SemanticLexiconRule(
    val id: String,
    val wordsRu: Set<String>,
    val wordsEn: Set<String>,
    /** Человекочитаемая метка: что означает совпадение в контексте анализа. */
    val senseLabel: String,
    val colorArgb: Long,
    val displayStyle: SemanticDisplayStyle,
    val tone: LexiconTone,
    val enabled: Boolean = true,
    val isPreset: Boolean = false,
    val media: LexiconMediaRefs = LexiconMediaRefs(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("wordsRu", JSONArray(wordsRu.sorted()))
        put("wordsEn", JSONArray(wordsEn.sorted()))
        put("senseLabel", senseLabel)
        put("colorArgb", colorArgb)
        put("displayStyle", displayStyle.name)
        put("tone", tone.id)
        put("enabled", enabled)
        put("isPreset", isPreset)
        if (media.hasAny()) put("media", media.toJson())
    }

    companion object {
        fun fromJson(j: JSONObject): SemanticLexiconRule? = try {
            val ruArr = j.getJSONArray("wordsRu")
            val enArr = j.getJSONArray("wordsEn")
            val ru = (0 until ruArr.length()).map { ruArr.getString(it).trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            val en = (0 until enArr.length()).map { enArr.getString(it).trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            val tone = LexiconTone.fromId(j.getString("tone")) ?: LexiconTone.NEUTRAL
            val mediaJo = if (j.has("media")) j.getJSONObject("media") else null
            SemanticLexiconRule(
                id = j.getString("id"),
                wordsRu = ru,
                wordsEn = en,
                senseLabel = j.optString("senseLabel", "").ifBlank { "Без подписи" },
                colorArgb = j.getLong("colorArgb"),
                displayStyle = SemanticDisplayStyle.valueOf(j.getString("displayStyle")),
                tone = tone,
                enabled = j.optBoolean("enabled", true),
                isPreset = j.optBoolean("isPreset", false),
                media = LexiconMediaRefs.fromJson(mediaJo),
            )
        } catch (_: Exception) {
            null
        }

        fun parseList(json: String): List<SemanticLexiconRule> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<SemanticLexiconRule>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

internal fun SemanticLexiconRule.wordsForTranslation(translation: TranslationId): Set<String> {
    val ru = wordsRu
    val en = wordsEn
    return when (translation) {
        TranslationId.WEB -> en + ru.filter { it.length <= 3 }
        else -> ru + en.filter { it.length <= 4 }
    }
}

fun SemanticLexiconRule.copyWithNewId(): SemanticLexiconRule =
    copy(id = "user_" + UUID.randomUUID().toString().replace("-", ""), isPreset = false)
