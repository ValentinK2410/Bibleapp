package com.example.bible.map

import android.content.Context
import android.util.Log
import com.yandex.mapkit.MapKitFactory

/**
 * Отложенная однократная инициализация MapKit, чтобы сбой SDK или пустой ключ
 * не роняли всё приложение при старте.
 */
object MapKitBootstrap {

    private val lock = Any()

    @Volatile
    private var attempted = false

    @Volatile
    private var lastTriedKey: String? = null

    @Volatile
    var isReady: Boolean = false
        private set

    /**
     * @return true если MapKit готов к использованию (MapView, getInstance()).
     */
    fun ensure(context: Context, apiKey: String): Boolean {
        val key = apiKey.trim()
        if (key.isEmpty()) return false
        synchronized(lock) {
            if (isReady) return true
            if (attempted && lastTriedKey == key) return false
            lastTriedKey = key
            attempted = true
            isReady = runCatching {
                MapKitFactory.setApiKey(key)
                MapKitFactory.initialize(context.applicationContext)
                true
            }.onFailure { e ->
                Log.e(TAG, "MapKit init failed", e)
            }.getOrDefault(false)
            return isReady
        }
    }

    private const val TAG = "MapKitBootstrap"
}
