package com.example.bible.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val MAPS_DOWNLOAD_UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 BibleApp/1.0"

object BibleMapsStorage {

    private fun extensionForUrl(url: String): String {
        val path = url.substringBefore('?').substringAfterLast('/')
        val ext = path.substringAfterLast('.', "")
        return if (ext.length in 2..5) ext.lowercase() else "img"
    }

    fun localFile(context: Context, def: BibleMapDefinition): File {
        val dir = File(context.filesDir, "bible_maps")
        dir.mkdirs()
        return File(dir, "${def.id}.${extensionForUrl(def.remoteUrl)}")
    }

    fun isCached(context: Context, def: BibleMapDefinition): Boolean {
        val f = localFile(context, def)
        return f.exists() && f.length() > 1024L
    }

    suspend fun download(context: Context, def: BibleMapDefinition): File = withContext(Dispatchers.IO) {
        val dest = localFile(context, def)
        if (dest.exists() && dest.length() > 1024L) return@withContext dest

        val url = def.remoteUrl
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", MAPS_DOWNLOAD_UA)
        conn.setRequestProperty("Accept", "image/*,*/*")
        if ("wikimedia.org" in url || "wikipedia.org" in url) {
            conn.setRequestProperty("Referer", "https://commons.wikimedia.org/")
        }
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        conn.connect()

        if (conn.responseCode != 200) {
            throw Exception("HTTP ${conn.responseCode}")
        }

        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        conn.inputStream.use { input ->
            tmp.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        tmp.renameTo(dest)
        dest
    }

    fun clearCache(context: Context, def: BibleMapDefinition) {
        localFile(context, def).takeIf { it.exists() }?.delete()
    }
}
