package com.example.bible

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.bible.data.BiblePreferences
import com.example.bible.data.BibleRepository
import com.example.bible.ui.BibleApp
import com.example.bible.ui.SharedMediaImportQueue
import com.example.bible.ui.extractShareIntentUri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractShareIntentUri(intent)?.let { SharedMediaImportQueue.offer(it) }
        val repository = BibleRepository(applicationContext)
        val preferences = BiblePreferences(applicationContext)
        setContent {
            BibleApp(repository = repository, preferences = preferences)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractShareIntentUri(intent)?.let { SharedMediaImportQueue.offer(it) }
    }
}
