package com.example.bible

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.example.bible.data.BiblePreferences
import com.example.bible.data.BibleRepository
import com.example.bible.deeplink.BibleDeepLinks
import com.example.bible.deeplink.DeepLinkRequest
import com.example.bible.ui.BibleApp
import com.example.bible.ui.SharedMediaImportQueue
import com.example.bible.ui.extractShareIntentUri

class MainActivity : ComponentActivity() {
    private val pendingDeepLink = mutableStateOf<DeepLinkRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        acceptIntent(intent)
        val repository = BibleRepository(applicationContext)
        val preferences = BiblePreferences(applicationContext)
        setContent {
            val deepLink by pendingDeepLink
            BibleApp(
                repository = repository,
                preferences = preferences,
                deepLink = deepLink,
                onDeepLinkConsumed = { pendingDeepLink.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
    }

    /**
     * Холодный старт приходит в [onCreate], повторное открытие при уже живой
     * activity (launchMode=singleTop) — в [onNewIntent].
     */
    private fun acceptIntent(intent: Intent?) {
        if (intent == null) return
        val data = intent.dataString
        val appLink = BibleDeepLinks.isAppLink(data, BuildConfig.DEEP_LINK_HOST)
        if (!appLink) {
            extractShareIntentUri(intent)?.let { SharedMediaImportQueue.offer(it) }
        }
        pendingDeepLink.value = BibleDeepLinks.requestFrom(
            dataString = if (appLink) data else null,
            startRouteExtra = intent.getStringExtra(EXTRA_START_ROUTE),
            httpsHost = BuildConfig.DEEP_LINK_HOST,
        )
    }

    companion object {
        const val EXTRA_START_ROUTE = "start_route"
    }
}
