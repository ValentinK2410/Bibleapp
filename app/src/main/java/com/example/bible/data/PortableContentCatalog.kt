package com.example.bible.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PortableCatalogItem(
    val id: String,
    val title: String,
    val version: Long,
    val downloadUrl: String,
    val sizeBytes: Long,
    val updatedAt: String,
    val moduleVersions: JSONObject,
)

data class PortableCatalogIndex(
    val updatedAt: String,
    val items: List<PortableCatalogItem>,
)

sealed class PortableCatalogDownloadResult {
    data class Ok(val message: String) : PortableCatalogDownloadResult()
    data class Err(val message: String) : PortableCatalogDownloadResult()
}

/** Каталог переносимых пакетов на GitHub (ZIP для импорта в приложение). */
object PortableContentCatalog {

    const val FORMAT = "bible_portable_catalog"
    private const val TAG = "PortableCatalog"
    private const val VERSION = 1

    val CATALOG_URLS = listOf(
        "https://raw.githubusercontent.com/ValentinK2410/Bibleapp/master/docs/portable/catalog.json",
        "https://github.com/ValentinK2410/Bibleapp/raw/master/docs/portable/catalog.json",
        "https://cdn.jsdelivr.net/gh/ValentinK2410/Bibleapp@master/docs/portable/catalog.json",
    )

    fun fetchCatalog(): PortableCatalogIndex {
        var lastError: Exception? = null
        for (url in CATALOG_URLS) {
            try {
                val raw = httpGet(url)
                return parseCatalog(raw)
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "catalog $url", e)
            }
        }
        throw lastError ?: IllegalStateException("catalog unavailable")
    }

    fun itemsNewerThan(
        catalog: PortableCatalogIndex,
        installed: Map<String, Long>,
    ): List<PortableCatalogItem> =
        catalog.items.filter { item ->
            val local = installed[item.id] ?: 0L
            item.version > local
        }

    fun parseCatalog(raw: String): PortableCatalogIndex {
        val root = JSONObject(raw)
        if (root.optString("format") != FORMAT) {
            throw IllegalArgumentException("wrong catalog format")
        }
        val arr = root.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            val url = o.optString("url").ifBlank { return@mapNotNull null }
            PortableCatalogItem(
                id = id,
                title = o.optString("title", id),
                version = o.optLong("version", 0L),
                downloadUrl = url,
                sizeBytes = o.optLong("sizeBytes", 0L),
                updatedAt = o.optString("updatedAt", ""),
                moduleVersions = o.optJSONObject("moduleVersions") ?: JSONObject(),
            )
        }
        return PortableCatalogIndex(
            updatedAt = root.optString("updatedAt", ""),
            items = items,
        )
    }

    fun downloadToCache(context: android.content.Context, item: PortableCatalogItem): java.io.File {
        val conn = URL(item.downloadUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 25_000
        conn.readTimeout = 120_000
        conn.instanceFollowRedirects = true
        conn.connect()
        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${conn.responseCode}")
        }
        val out = java.io.File(context.cacheDir, "portable_${item.id}_${item.version}.zip")
        conn.inputStream.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        conn.connect()
        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${conn.responseCode}")
        }
        return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
