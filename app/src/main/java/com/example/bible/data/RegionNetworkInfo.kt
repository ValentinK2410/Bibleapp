package com.example.bible.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Регион по настройкам системы (язык/страна). */
data class SystemRegionInfo(
    val countryCodeIso: String,
    val countryDisplayName: String,
    val languageTag: String,
)

/**
 * Приблизительное местоположение по внешнему IP (HTTPS ipapi.co).
 * Не GPS.
 */
data class IpGeoInfo(
    val countryName: String,
    val countryCode: String,
    val cityOrRegion: String,
)

object RegionNetworkInfo {

    fun systemRegion(): SystemRegionInfo {
        val loc = Locale.getDefault()
        val code = loc.country.orEmpty().ifBlank { "—" }
        val countryName = loc.getDisplayCountry(loc).ifBlank { code }
        val lang = loc.toLanguageTag()
        return SystemRegionInfo(
            countryCodeIso = code,
            countryDisplayName = countryName,
            languageTag = lang,
        )
    }

    /**
     * Запрос геолокации по IP. Без ключа: ограничение сервиса, только для справки.
     */
    suspend fun fetchIpGeoApproximate(): Result<IpGeoInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://ipapi.co/json/").openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "BibleApp/1.0 (Android)")
            conn.connectTimeout = 12_000
            conn.readTimeout = 12_000
            conn.connect()
            if (conn.responseCode !in 200..299) {
                error("HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val o = JSONObject(body)
            if (o.optBoolean("error", false) || o.has("reason")) {
                error(o.optString("reason", o.optString("message", "unknown")))
            }
            val country = o.optString("country_name").ifBlank { o.optString("country") }
            val code = o.optString("country_code").ifBlank { o.optString("country") }
            val city = o.optString("city")
            val region = o.optString("region")
            val place = listOf(city, region).filter { it.isNotBlank() }.joinToString(", ")
                .ifBlank { "—" }
            IpGeoInfo(
                countryName = country.ifBlank { "—" },
                countryCode = code.ifBlank { "—" },
                cityOrRegion = place,
            )
        }
    }
}
