package com.example.bible.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Что лежит на GitHub: по этому файлу приложение понимает, есть ли более новая сборка. */
data class RemoteAppVersion(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
)

data class WhatsNewHighlight(
    val title: String,
    val items: List<String>,
)

data class WhatsNewRelease(
    val versionName: String,
    val date: String,
    val highlights: List<WhatsNewHighlight>,
    val fixes: List<String>,
)

object AppUpdateCatalog {

    const val VERSION_URL =
        "https://raw.githubusercontent.com/ValentinK2410/Bibleapp/master/docs/app-version.json"
    const val DEFAULT_APK_URL =
        "https://github.com/ValentinK2410/Bibleapp/releases/latest/download/Bible.apk"
    const val RELEASES_PAGE = "https://github.com/ValentinK2410/Bibleapp/releases/latest"
    const val SITE_URL = "https://github.com/ValentinK2410/Bibleapp"

    fun parseRemote(raw: String): RemoteAppVersion {
        val o = JSONObject(raw)
        return RemoteAppVersion(
            versionCode = o.optInt("versionCode", 0),
            versionName = o.optString("versionName").ifBlank { "—" },
            apkUrl = o.optString("apkUrl").ifBlank { DEFAULT_APK_URL },
            notes = o.optString("notes").trim(),
        )
    }

    fun fetchRemote(): RemoteAppVersion {
        val conn = URL(VERSION_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12_000
            conn.readTimeout = 12_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Bibleapp")
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.readText()
                .orEmpty()
            if (code !in 200..299) {
                throw RuntimeException("Не удалось проверить обновление ($code)")
            }
            return parseRemote(body)
        } finally {
            conn.disconnect()
        }
    }

    fun loadWhatsNew(context: Context): List<WhatsNewRelease> {
        val raw = context.assets.open("whats_new.json").bufferedReader().use { it.readText() }
        return parseWhatsNew(raw)
    }

    fun parseWhatsNew(raw: String): List<WhatsNewRelease> {
        val arr = JSONObject(raw).optJSONArray("releases") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            WhatsNewRelease(
                versionName = o.optString("versionName"),
                date = o.optString("date"),
                highlights = o.optJSONArray("highlights").toHighlights(),
                fixes = o.optJSONArray("fixes").toStringList(),
            )
        }
    }

    private fun JSONArray?.toHighlights(): List<WhatsNewHighlight> {
        val arr = this ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            WhatsNewHighlight(
                title = o.optString("title"),
                items = o.optJSONArray("items").toStringList(),
            )
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        val arr = this ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optString(i).trim().takeIf { it.isNotEmpty() }
        }
    }
}
