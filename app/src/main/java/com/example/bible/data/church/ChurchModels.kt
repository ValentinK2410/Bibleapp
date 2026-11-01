package com.example.bible.data.church

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class ChurchParticipant(
    val id: String = UUID.randomUUID().toString(),
    val lastName: String = "",
    val firstName: String = "",
    val patronymic: String = "",
    val position: String = "",
    /** Дата рождения в формате yyyy-MM-dd; пустая строка — дата не указана. */
    val birthDate: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun displayName(): String = listOf(lastName, firstName, patronymic)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("lastName", lastName)
        put("firstName", firstName)
        put("patronymic", patronymic)
        put("position", position)
        put("birthDate", birthDate)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(j: JSONObject): ChurchParticipant = ChurchParticipant(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            lastName = j.optString("lastName", ""),
            firstName = j.optString("firstName", ""),
            patronymic = j.optString("patronymic", ""),
            position = j.optString("position", ""),
            birthDate = j.optString("birthDate", ""),
            createdAt = j.optLong("createdAt", System.currentTimeMillis()),
        )

        fun parseList(json: String): List<ChurchParticipant> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<ChurchParticipant>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

/** Дата рождения карточки; пустое или испорченное значение означает «не указана». */
val ChurchParticipant.birthLocalDate: LocalDate?
    get() = runCatching { LocalDate.parse(birthDate) }
        .getOrNull()
        ?.takeIf { it.year >= 1900 && !it.isAfter(LocalDate.now()) }
