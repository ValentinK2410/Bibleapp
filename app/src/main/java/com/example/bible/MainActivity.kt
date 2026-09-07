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
import com.example.bible.ui.BibleApp
import com.example.bible.ui.SharedMediaImportQueue
import com.example.bible.ui.extractShareIntentUri

class MainActivity : ComponentActivity() {
    private val pendingStartRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractShareIntentUri(intent)?.let { SharedMediaImportQueue.offer(it) }
        pendingStartRoute.value = intent.getStringExtra(EXTRA_START_ROUTE)
        val repository = BibleRepository(applicationContext)
        val preferences = BiblePreferences(applicationContext)
        setContent {
            val startRoute by pendingStartRoute
            BibleApp(
                repository = repository,
                preferences = preferences,
                startRoute = startRoute,
                onStartRouteConsumed = { pendingStartRoute.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractShareIntentUri(intent)?.let { SharedMediaImportQueue.offer(it) }
        pendingStartRoute.value = intent.getStringExtra(EXTRA_START_ROUTE)
    }

    companion object {
        const val EXTRA_START_ROUTE = "start_route"
    }
}
