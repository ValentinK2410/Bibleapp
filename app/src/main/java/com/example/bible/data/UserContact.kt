package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Контакт пользователя для SMS и координат на карте «Мои путешествия». */
data class UserContact(
    val id: String,
    val fullName: String,
    val phone: String,
    val email: String,
    val notes: String,
    /** День рождения (календарная дата), в днях с эпохи ISO; null — не указана. */
    val birthEpochDay: Long?,
    val latitude: Double?,
    val longitude: Double?,
) {
    fun hasCoordinates(): Boolean =
        latitude != null && longitude != null &&
            latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

object UserContactJson {

    fun parseArray(raw: String): List<UserContact> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(trimmed)
            val out = ArrayList<UserContact>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                parseOne(o)?.let { out.add(it) }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseOne(o: JSONObject): UserContact? {
        val id = o.optString("id", "").trim().ifEmpty { UUID.randomUUID().toString() }
        val fullName = o.optString("fullName", "").trim().ifEmpty { o.optString("name", "").trim() }
        val phone = o.optString("phone", "").trim()
        val email = o.optString("email", "").trim()
        val notes = o.optString("notes", "").trim()
        val birthEpochDay =
            when {
                o.has("birthEpochDay") && !o.isNull("birthEpochDay") ->
                    o.optLong("birthEpochDay", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
                else -> birthdayEpochDayFromLegacyJson(o.optString("birthDate", "").trim())
            }
        val lat = o.takeIf { it.has("latitude") }?.optDouble("latitude", Double.NaN)?.takeIf { it.isFinite() }
        val lon = o.takeIf { it.has("longitude") }?.optDouble("longitude", Double.NaN)?.takeIf { it.isFinite() }
        return UserContact(
            id = id,
            fullName = fullName.ifEmpty { phone.ifEmpty { email.ifEmpty { id } } },
            phone = phone,
            email = email,
            notes = notes,
            birthEpochDay = birthEpochDay,
            latitude = lat,
            longitude = lon,
        )
    }

    fun toJson(contacts: List<UserContact>): String {
        val arr = JSONArray()
        contacts.forEach { c ->
            arr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("fullName", c.fullName)
                    put("phone", c.phone)
                    put("email", c.email)
                    put("notes", c.notes)
                    if (c.birthEpochDay != null) {
                        put("birthEpochDay", c.birthEpochDay)
                    }
                    if (c.latitude != null && c.longitude != null) {
                        put("latitude", c.latitude)
                        put("longitude", c.longitude)
                    }
                },
            )
        }
        return arr.toString()
    }
}

/** Локальное хранилище контактов приложения (не системная книга контактов Android). */
class ContactsRepository(context: android.content.Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<UserContact> =
        UserContactJson.parseArray(prefs.getString(KEY_JSON, "").orEmpty())

    @Synchronized
    fun save(contacts: List<UserContact>) {
        prefs.edit().putString(KEY_JSON, UserContactJson.toJson(contacts)).apply()
    }

    companion object {
        private const val PREFS_NAME = "app_user_contacts_v1"
        private const val KEY_JSON = "contacts_json"
    }
}
