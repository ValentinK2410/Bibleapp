package com.example.bible.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.games.KidsGameAudio
import com.example.bible.games.KidsMusicTrack

/** Играет [track], пока экран виден; на паузе приложения музыка останавливается. */
@Composable
internal fun KidsGameMusic(track: KidsMusicTrack) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(track, lifecycleOwner) {
        KidsGameAudio.init(context)
        KidsGameAudio.startMusic(track)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> KidsGameAudio.startMusic(track)
                Lifecycle.Event.ON_PAUSE -> KidsGameAudio.pauseMusic()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            KidsGameAudio.stopMusic(track)
        }
    }
}

/** Две кнопки для верхней панели игры: музыка и звуки. */
@Composable
internal fun KidsAudioToggles() {
    val context = LocalContext.current
    KidsGameAudio.init(context)
    val musicOn by KidsGameAudio.musicOn.collectAsStateWithLifecycle()
    val sfxOn by KidsGameAudio.sfxOn.collectAsStateWithLifecycle()
    val onTint = MaterialTheme.colorScheme.primary
    val offTint = Color(0xFF94A3B8)
    IconButton(onClick = { KidsGameAudio.setMusicOn(!musicOn) }) {
        Icon(
            if (musicOn) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
            contentDescription = if (musicOn) "Выключить музыку" else "Включить музыку",
            tint = if (musicOn) onTint else offTint,
        )
    }
    IconButton(onClick = { KidsGameAudio.setSfxOn(!sfxOn) }) {
        Icon(
            if (sfxOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            contentDescription = if (sfxOn) "Выключить звуки" else "Включить звуки",
            tint = if (sfxOn) onTint else offTint,
        )
    }
}
