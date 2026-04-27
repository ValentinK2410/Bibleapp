package com.example.bible

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.bible.data.BiblePreferences
import com.example.bible.data.BibleRepository
import com.example.bible.ui.BibleApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = BibleRepository(applicationContext)
        val preferences = BiblePreferences(applicationContext)
        setContent {
            BibleApp(repository = repository, preferences = preferences)
        }
    }
}
