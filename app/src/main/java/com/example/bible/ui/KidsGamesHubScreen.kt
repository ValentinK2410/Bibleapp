package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.AzbukaProgressRepository
import com.example.bible.data.KidsAgeMode
import com.example.bible.data.KidsFamilyStore
import com.example.bible.data.KidsGames

private data class KidsGameTile(
    val key: String,
    val title: String,
    val emoji: String,
    val subtitle: String,
    val gradient: List<Color>,
    val winLabel: String,
    val school: Boolean = false,
    val open: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsGamesHubScreen(
    onBack: () -> Unit,
    onOpenTicTacToe: () -> Unit,
    onOpenCheckers: () -> Unit,
    onOpenGo: () -> Unit,
    onOpenPipePuzzle: () -> Unit,
    onOpenBrainRing: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    val wins by repo.gameWins.collectAsStateWithLifecycle(initialValue = emptyMap())
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val young = family.resolvedMode() == KidsAgeMode.SMALL
    val games = listOf(
        KidsGameTile(
            KidsGames.BRAIN_RING, "Брейн-ринг", "⏱️", "1000 вопросов из Библии. Новые запоминаются",
            kidsGradient(0), "раундов", school = true, open = onOpenBrainRing,
        ),
        KidsGameTile(
            KidsGames.TIC_TAC_TOE, "Крестики\nнолики", "⭕", "С компьютером или вдвоём, поле до 6×6",
            kidsGradient(1), "побед", open = onOpenTicTacToe,
        ),
        KidsGameTile(
            KidsGames.CHECKERS, "Шашки", "⚫", "Русские правила, с компьютером или вдвоём",
            kidsGradient(2), "побед", school = true, open = onOpenCheckers,
        ),
        KidsGameTile(
            KidsGames.GO, "Го", "🔵", "Окружай камни соперника",
            kidsGradient(3), "партий", school = true, open = onOpenGo,
        ),
        KidsGameTile(
            KidsGames.PIPES, "Водопровод", "🚰", "Поверни трубы от крана до стока",
            kidsGradient(4), "решено", open = onOpenPipePuzzle,
        ),
    )
    KidsGameMusic(com.example.bible.games.KidsMusicTrack.GAMES)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Игры", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = { KidsAudioToggles() },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(KidsNightGradient))
                        .padding(18.dp),
                ) {
                    Text("🎮 Выбери игру", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Всего побед: ${wins.values.sum()} · за победу 2 звезды ⭐\n🎵 Музыку и звуки можно выключить вверху справа",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 14.sp,
                    )
                }
            }
            items(games, key = { it.key }) { game ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(game.gradient))
                        .kidsPressable(onClick = game.open)
                        .padding(14.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.35f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .padding(10.dp),
                    ) {
                        when (game.key) {
                            KidsGames.TIC_TAC_TOE -> TicTacToeArt(Modifier.fillMaxSize())
                            KidsGames.CHECKERS -> CheckersArt(Modifier.fillMaxSize())
                            KidsGames.GO -> GoArt(Modifier.fillMaxSize())
                            KidsGames.BRAIN_RING -> BrainRingArt(Modifier.fillMaxSize())
                            else -> PipePreviewArt(Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        game.title,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        lineHeight = 21.sp,
                        minLines = 2,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        game.subtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        minLines = 3,
                        maxLines = 3,
                    )
                    if (young && game.school) {
                        Spacer(Modifier.height(6.dp))
                        Text("Для 6–10, можно открыть", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "🏆 ${wins[game.key] ?: 0} ${game.winLabel}",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}
