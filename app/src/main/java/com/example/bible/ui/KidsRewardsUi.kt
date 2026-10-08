package com.example.bible.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.bible.data.AzbukaProgressRepository

/** Начисляет очки, когда [won] становится true; каждая новая победа засчитывается заново. */
@Composable
internal fun KidsGameWinReward(game: String, won: Boolean, points: Int = 20) {
    val context = LocalContext.current
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    LaunchedEffect(won) {
        if (!won) return@LaunchedEffect
        repo.registerGameWin(game, points)
        val stars = points / 10
        Toast.makeText(
            context,
            "Молодец! +$stars ${starsWord(stars)} ⭐",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

internal fun starsWord(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "звезда"
        mod10 in 2..4 && mod100 !in 12..14 -> "звезды"
        else -> "звёзд"
    }
}
