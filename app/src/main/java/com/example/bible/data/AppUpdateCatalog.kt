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

data class RemoteVersionCheckResult(
    val version: RemoteAppVersion,
    /** true — данные с сервера; false — встроенная копия из assets. */
    val fromNetwork: Boolean,
    val networkError: String? = null,
)

object AppUpdateCatalog {

    private const val BUNDLED_VERSION_ASSET = "app_version_remote.json"

    val VERSION_URLS = listOf(
        "https://raw.githubusercontent.com/ValentinK2410/Bibleapp/master/docs/app-version.json",
        "https://github.com/ValentinK2410/Bibleapp/raw/master/docs/app-version.json",
        "https://cdn.jsdelivr.net/gh/ValentinK2410/Bibleapp@master/docs/app-version.json",
    )
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

    fun loadBundledRemote(context: Context): RemoteAppVersion {
        val raw = context.assets.open(BUNDLED_VERSION_ASSET).bufferedReader().use { it.readText() }
        return parseRemote(raw)
    }

    /** Сначала сеть (несколько зеркал), при неудаче — встроенная копия. */
    fun checkRemote(context: Context): RemoteVersionCheckResult {
        val networkResult = runCatching { fetchRemote() }
        if (networkResult.isSuccess) {
            return RemoteVersionCheckResult(networkResult.getOrThrow(), fromNetwork = true)
        }
        val err = networkResult.exceptionOrNull()?.message
        return RemoteVersionCheckResult(
            version = loadBundledRemote(context),
            fromNetwork = false,
            networkError = networkErrorMessage(err),
        )
    }

    fun fetchRemote(): RemoteAppVersion {
        var lastError: Exception? = null
        for (url in VERSION_URLS) {
            runCatching { fetchFromUrl(url) }
                .onSuccess { return it }
                .onFailure { lastError = it as? Exception ?: Exception(it) }
        }
        throw lastError ?: RuntimeException("Не удалось проверить обновление")
    }

    private fun fetchFromUrl(url: String): RemoteAppVersion {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12_000
            conn.readTimeout = 12_000
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Bibleapp")
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.readText()
                .orEmpty()
            if (code !in 200..299) {
                throw RuntimeException("HTTP $code")
            }
            return parseRemote(body)
        } finally {
            conn.disconnect()
        }
    }

    fun networkErrorMessage(raw: String?): String {
        val msg = raw.orEmpty()
        return when {
            "No address associated with hostname" in msg ||
                "Unable to resolve host" in msg ||
                "DNS" in msg.uppercase() ||
                "Network is unreachable" in msg ||
                "Connection refused" in msg ||
                "ETIMEDOUT" in msg ||
                "timeout" in msg.lowercase() ->
                "Нет доступа к серверу обновлений. Проверьте интернет или VPN."
            "HTTP 403" in msg || "HTTP 404" in msg ->
                "Сервер обновлений временно недоступен. Попробуйте позже."
            else -> msg.ifBlank { "Не удалось проверить обновление" }
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
