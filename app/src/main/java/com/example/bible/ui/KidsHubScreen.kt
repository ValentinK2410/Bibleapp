package com.example.bible.ui

import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.bible.R
import com.example.bible.data.AzbukaProgressRepository
import com.example.bible.data.KidsBibleStories
import com.example.bible.data.KidsPicturedRoutes
import com.example.bible.data.KidsUserSectionsState
import com.example.bible.data.kidsStars
import com.example.bible.ui.theme.OutlinedTextField
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

private val KidsTileGradients = listOf(
    listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
    listOf(Color(0xFFF59E0B), Color(0xFFEC4899)),
    listOf(Color(0xFF10B981), Color(0xFF06B6D4)),
    listOf(Color(0xFFEF4444), Color(0xFFF97316)),
    listOf(Color(0xFF0EA5E9), Color(0xFF6366F1)),
    listOf(Color(0xFF22C55E), Color(0xFF84CC16)),
    listOf(Color(0xFFD946EF), Color(0xFF8B5CF6)),
    listOf(Color(0xFF14B8A6), Color(0xFF3B82F6)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsHubScreen(
    navController: NavHostController,
    onBack: () -> Unit,
    hubState: KidsUserSectionsState? = null,
    onEditSections: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    val points by repo.points.collectAsStateWithLifecycle(initialValue = 0)
    val letters by repo.lettersOpenedCount.collectAsStateWithLifecycle(initialValue = 0)
    val wins by repo.gameWins.collectAsStateWithLifecycle(initialValue = emptyMap())
    val stories by repo.storiesRead.collectAsStateWithLifecycle(initialValue = emptySet())
    val locked by repo.kidsLock.collectAsStateWithLifecycle(initialValue = false)
    val rows = remember(hubState) { mergeKidsHubRows(kidsHubDefaultRows(), hubState) }
    var menuOpen by remember { mutableStateOf(false) }
    var gateAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.forLanguageTag("ru-RU")
                engine?.setSpeechRate(0.9f)
                engine?.setPitch(1.08f)
                tts = engine
            }
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
            tts = null
        }
    }
    val speak: (String) -> Unit = { text ->
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kids_hub_${System.nanoTime()}")
    }

    val tryLeave: () -> Unit = {
        if (locked) gateAction = onBack else onBack()
    }
    BackHandler(enabled = locked) { gateAction = onBack }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Детям", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = tryLeave) {
                        Icon(
                            if (locked) Icons.Filled.Lock else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.kids_hub_menu_cd))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Детский режим") },
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            trailingIcon = { if (locked) Icon(Icons.Filled.Check, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                if (locked) {
                                    gateAction = { scope.launch { repo.setKidsLock(false) } }
                                } else {
                                    scope.launch { repo.setKidsLock(true) }
                                }
                            },
                        )
                        if (onEditSections != null && !locked) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.kids_edit_sections_menu)) },
                                onClick = {
                                    menuOpen = false
                                    onEditSections()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                KidsHeroCard(
                    stars = kidsStars(points),
                    letters = letters,
                    wins = wins.values.sum(),
                    stories = stories.count { KidsBibleStories.byId(it) != null },
                    locked = locked,
                )
            }
            items(rows.size, key = { rows[it].route }) { index ->
                val row = rows[index]
                KidsHubTile(
                    row = row,
                    gradient = KidsTileGradients[index % KidsTileGradients.size],
                    onClick = {
                        if (KidsPicturedRoutes.isPicturedRoute(row.route)) {
                            navController.navigate("kids_album?r=${android.net.Uri.encode(row.route)}") {
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate(row.route) {
                                launchSingleTop = row.route == "azbuka" || row.route == "cifry"
                            }
                        }
                    },
                    onLongClick = { speak(row.title) },
                )
            }
            item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Подержи плитку пальцем — я скажу, что там 🔊",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }

    gateAction?.let { action ->
        ParentGateDialog(
            onDismiss = { gateAction = null },
            onPassed = {
                gateAction = null
                action()
            },
        )
    }
}

@Composable
private fun KidsHeroCard(stars: Int, letters: Int, wins: Int, stories: Int, locked: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6))))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Привет! 👋", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(
                    if (locked) "Детский режим включён 🔒" else "Играй, читай и собирай звёзды",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 14.sp,
                )
            }
            Column(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.22f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("⭐ $stars", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(starsWord(stars), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KidsHeroStat("🔤", "$letters/33", "букв", Modifier.weight(1f))
            KidsHeroStat("🏆", "$wins", "побед", Modifier.weight(1f))
            KidsHeroStat("📖", "$stories/${KidsBibleStories.all.size}", "историй", Modifier.weight(1f))
        }
    }
}

@Composable
private fun KidsHeroStat(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 20.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KidsHubTile(
    row: KidsHubRow,
    gradient: List<Color>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(gradient))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                row.imageRes != null -> AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(row.imageRes)
                        .size(420)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                row.emojiThumb != null -> Text(row.emojiThumb, fontSize = 56.sp)
                else -> Icon(row.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            row.title,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            lineHeight = 21.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** Простая проверка «для взрослых»: умножение, которое малыш не решит наугад. */
@Composable
internal fun ParentGateDialog(onDismiss: () -> Unit, onPassed: () -> Unit) {
    val a = remember { Random.nextInt(6, 10) }
    val b = remember { Random.nextInt(4, 10) }
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        title = { Text("Для взрослых") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Чтобы выйти из детского режима, решите пример:")
                Text("$a × $b = ?", fontSize = 28.sp, fontWeight = FontWeight.Black)
                OutlinedTextField(
                    value = answer,
                    onValueChange = {
                        answer = it.filter(Char::isDigit).take(3)
                        wrong = false
                    },
                    singleLine = true,
                    isError = wrong,
                    label = { Text(if (wrong) "Неверно, попробуйте ещё" else "Ответ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (answer.toIntOrNull() == a * b) onPassed() else wrong = true
            }) { Text("Готово") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Остаться") }
        },
    )
}
