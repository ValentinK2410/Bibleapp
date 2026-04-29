package com.example.bible.data.travel

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val Context.friendPeerDataStore by preferencesDataStore(name = "friend_peer_location")

private object FriendPeerKeys {
    /** HTTPS URL, отдающий JSON с полями latitude/longitude (или lat/lng). */
    val POLL_URL = stringPreferencesKey("friend_peer_poll_url")
    val POLL_INTERVAL_SEC = intPreferencesKey("friend_peer_poll_interval_sec")
    val POLL_ENABLED = booleanPreferencesKey("friend_peer_poll_enabled")
}

class FriendPeerLocationRepository(
    context: Context,
) {
    private val app = context.applicationContext

    val pollUrl: Flow<String> = app.friendPeerDataStore.data.map { prefs ->
        prefs[FriendPeerKeys.POLL_URL]?.trim().orEmpty()
    }

    val pollIntervalSec: Flow<Int> = app.friendPeerDataStore.data.map { prefs ->
        (prefs[FriendPeerKeys.POLL_INTERVAL_SEC] ?: 20).coerceIn(5, 300)
    }

    val pollEnabled: Flow<Boolean> = app.friendPeerDataStore.data.map { prefs ->
        prefs[FriendPeerKeys.POLL_ENABLED] ?: false
    }

    suspend fun setPollUrl(url: String) {
        app.friendPeerDataStore.edit { prefs ->
            prefs[FriendPeerKeys.POLL_URL] = url.trim()
        }
    }

    suspend fun setPollIntervalSec(sec: Int) {
        app.friendPeerDataStore.edit { prefs ->
            prefs[FriendPeerKeys.POLL_INTERVAL_SEC] = sec.coerceIn(5, 300)
        }
    }

    suspend fun setPollEnabled(enabled: Boolean) {
        app.friendPeerDataStore.edit { prefs ->
            prefs[FriendPeerKeys.POLL_ENABLED] = enabled
        }
    }
}

suspend fun fetchFriendPeerLocationFromHttpsJsonUrl(urlString: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL(urlString.trim())
        if (url.protocol != "https") {
            error("Только HTTPS")
        }
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json, text/plain;q=0.9, */*;q=0.8")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader().use { it?.readText().orEmpty() }
            if (code !in 200..299) {
                error("HTTP $code")
            }
            body
        } finally {
            conn.disconnect()
        }
    }
}

suspend fun FriendPeerLocationRepository.pollOnce(): FriendPeerLocation? {
    val url = pollUrl.first().trim()
    if (url.isEmpty() || !url.startsWith("https://")) return null
    val text = fetchFriendPeerLocationFromHttpsJsonUrl(url).getOrNull() ?: return null
    return parseFriendPeerLocationJson(text)
}
