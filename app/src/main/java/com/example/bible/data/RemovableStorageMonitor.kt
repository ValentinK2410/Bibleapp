package com.example.bible.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.os.Build
import android.os.storage.StorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Событие: подключён или отключён съёмный накопитель (часто OTG-флешка). */
data class RemovableStorageEvent(
    val mounted: Boolean,
    val label: String,
)

object RemovableStorageMonitor {

    private val _events = MutableStateFlow<RemovableStorageEvent?>(null)
    val events = _events.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    fun install(context: Context) {
        if (receiver != null) return
        val app = context.applicationContext
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_MEDIA_MOUNTED -> {
                        val path = intent.data?.path.orEmpty()
                        _events.value = RemovableStorageEvent(
                            mounted = true,
                            label = path.ifBlank { "Накопитель" },
                        )
                    }
                    Intent.ACTION_MEDIA_UNMOUNTED, Intent.ACTION_MEDIA_EJECT -> {
                        _events.value = RemovableStorageEvent(mounted = false, label = "")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addDataScheme("file")
        }
        @Suppress("DEPRECATION")
        app.registerReceiver(receiver, filter)
        probeRemovable(app)
    }

    fun consumeEvent() {
        _events.value = null
    }

    private fun probeRemovable(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val sm = context.getSystemService(StorageManager::class.java) ?: return
        for (vol in sm.storageVolumes) {
            if (!vol.isRemovable) continue
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vol.state == "mounted") {
                _events.value = RemovableStorageEvent(
                    mounted = true,
                    label = vol.getDescription(context)?.toString().orEmpty().ifBlank { "Флешка" },
                )
                break
            }
        }
    }
}
