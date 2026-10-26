package com.example.bible.ui

import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import com.example.bible.data.KidsAgeMode
import com.example.bible.data.KidsBibleStories
import com.example.bible.data.KidsFamilyStore
import com.example.bible.data.KidsPicturedRoutes
import com.example.bible.data.KidsUserSectionsState
import com.example.bible.data.easterSeason
import com.example.bible.data.kidsStars
import com.example.bible.data.nextEaster
import java.time.LocalDate
import java.time.LocalTime
import com.example.bible.ui.theme.OutlinedTextField
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

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
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val mode = family.resolvedMode()
    val rows = remember(hubState) { mergeKidsHubRows(kidsHubDefaultRows(), hubState) }
    val activeChild = family.activeChild()
    val activeProgress = family.activeProgress()
    val easterDate = remember(family.easterCalendar) {
        nextEaster(LocalDate.now(), family.easterCalendar != "western")
    }
    val easterNow = easterSeason(LocalDate.now(), easterDate)
    val remindEvening = family.eveningReminder &&
        LocalTime.now().hour >= family.eveningHour &&
        activeProgress.eveningPrayedOn != LocalDate.now().toString()
    var restGate by remember { mutableStateOf(false) }
    var limitLifted by remember { mutableStateOf(false) }
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
        containerColor = MaterialTheme.colorScheme.background,
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
        KidsEnter {
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
                    stars = if (activeChild != null) kidsStars(activeProgress.points) else kidsStars(points),
                    letters = letters,
                    wins = if (activeChild != null) activeProgress.gameWins.values.sum() else wins.values.sum(),
                    stories = if (activeChild != null) {
                        activeProgress.stories.count { KidsBibleStories.byId(it) != null }
                    } else {
                        stories.count { KidsBibleStories.byId(it) != null }
                    },
                    locked = locked,
                    who = activeChild?.let { "${it.name}, ${it.age}" } ?: "Семья",
                    mode = mode,
                )
            }
            item(key = "kids", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Кто играет?", fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        family.children.forEach { child ->
                            val selected = child.id == family.activeChildId
                            val letter = child.name.trim().take(1).uppercase()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) Color(0xFFEA580C) else Color(0xFFFFEDD5))
                                    .clickable { scope.launch { KidsFamilyStore.setActiveChild(context, child.id) } }
                                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) Color.White.copy(alpha = 0.28f) else Color(0xFFE0A15A)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        letter,
                                        color = Color.White,
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        lineHeight = 18.sp,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    child.name,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                        Text(
                            "Родителям",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFE0E7FF))
                                .clickable { gateAction = { navController.navigate("kids_parents") } }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (mode == KidsAgeMode.SMALL) "🐣 Малыши" else "Малыши",
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (mode == KidsAgeMode.SMALL) Color(0xFFFDE68A) else Color(0xFFF8FAFC))
                                .clickable { gateAction = { scope.launch { KidsFamilyStore.setAgeMode(context, "small") } } }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        Text(
                            if (mode == KidsAgeMode.BIG) "🎒 Школьники" else "Школьники",
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (mode == KidsAgeMode.BIG) Color(0xFFDBEAFE) else Color(0xFFF8FAFC))
                                .clickable { gateAction = { scope.launch { KidsFamilyStore.setAgeMode(context, "big") } } }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            if (easterNow) {
                item(key = "easter", span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(Brush.linearGradient(KidsWarmSunset))
                            .clickable { navController.navigate("kids_story/easter") }
                            .padding(18.dp),
                    ) {
                        Text("🌅 Пасха близко", color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
                        Text("История из шести сцен. Праздник ${easterDate.dayOfMonth}.${easterDate.monthValue}.", color = Color.White)
                    }
                }
            }
            if (remindEvening) {
                item(key = "evening", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "🌙 Пора вечерней молитвы",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFE0E7FF))
                            .clickable { navController.navigate("kids_prayers") }
                            .padding(16.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            items(rows.size, key = { rows[it].route }) { index ->
                val row = rows[index]
                KidsHubTile(
                    row = row,
                    gradient = kidsTileColors(index),
                    onClick = {
                        if (row.route == "kids_parents") {
                            gateAction = { navController.navigate("kids_parents") }
                        } else if (KidsPicturedRoutes.isPicturedRoute(row.route)) {
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
                    "Подержи плитку пальцем — я скажу, что там",
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
    }

    val overLimit = family.timeLimitMinutes > 0 && family.todayMinutes() >= family.timeLimitMinutes && !limitLifted
    if (overLimit && !restGate) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Пора отдохнуть!") },
            text = { Text("На сегодня минут в разделе уже достаточно. Продолжить может взрослый.") },
            confirmButton = {
                TextButton(onClick = { restGate = true }) { Text("Взрослый рядом") }
            },
        )
    }
    if (restGate) {
        ParentGateDialog(
            onDismiss = { restGate = false },
            onPassed = {
                restGate = false
                limitLifted = true
            },
            hard = mode == KidsAgeMode.BIG,
        )
    }
    gateAction?.let { action ->
        ParentGateDialog(
            onDismiss = { gateAction = null },
            onPassed = {
                gateAction = null
                action()
            },
            hard = mode == KidsAgeMode.BIG,
        )
    }
}

@Composable
private fun KidsHeroCard(
    stars: Int,
    letters: Int,
    wins: Int,
    stories: Int,
    locked: Boolean,
    who: String,
    mode: KidsAgeMode,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .kidsLifted()
            .clip(KidsCardShape)
            .background(Brush.linearGradient(if (MaterialTheme.colorScheme.background.luminance() < 0.4f) KidsDusk else KidsWarmSunset))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Привет, $who",
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    if (locked) "Детский режим включён · ${mode.title}" else "Играй, читай и собирай звёзды · ${mode.title}",
                    color = Color.White.copy(alpha = 0.88f),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
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
                Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFF3D6))
                Text(
                    "$stars",
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    starsWord(stars),
                    color = Color.White.copy(alpha = 0.9f),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KidsHeroStat(Icons.Filled.SortByAlpha, "$letters/33", "букв", Modifier.weight(1f))
            KidsHeroStat(Icons.Filled.EmojiEvents, "$wins", "побед", Modifier.weight(1f))
            KidsHeroStat(Icons.AutoMirrored.Filled.MenuBook, "$stories/${KidsBibleStories.all.size}", "историй", Modifier.weight(1f))
        }
    }
}

@Composable
private fun KidsHeroStat(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(
            value,
            color = Color.White,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.85f),
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
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
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .kidsLifted(shape)
            .clip(shape)
            .background(Brush.linearGradient(gradient))
            .kidsPressable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .kidsPhotoMat(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                row.imageRes != null -> AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(row.imageRes)
                        .size(420)
                        .crossfade(kidsMotionEnabled())
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                !row.emojiThumb.isNullOrBlank() -> Text(
                    row.emojiThumb,
                    fontSize = 52.sp,
                    lineHeight = 58.sp,
                )
                else -> Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(row.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            row.title,
            color = Color.White,
            style = KidsTileTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** Простая проверка «для взрослых»: умножение, которое малыш не решит наугад. */
@Composable
internal fun ParentGateDialog(onDismiss: () -> Unit, onPassed: () -> Unit, hard: Boolean = false) {
    val a = remember { if (hard) Random.nextInt(12, 20) else Random.nextInt(6, 10) }
    val b = remember { if (hard) Random.nextInt(11, 20) else Random.nextInt(4, 10) }
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        title = { Text("Только для взрослых") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (hard) {
                        "Для школьников числа немного больше. Решите пример, чтобы открыть настройки и другие взрослые шаги."
                    } else {
                        "Решите пример, чтобы открыть настройки и другие взрослые шаги."
                    },
                )
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
