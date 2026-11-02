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
import com.example.bible.data.KidsBiblePinStore
import com.example.bible.data.KidsBibleStories
import com.example.bible.data.KidsFamilyStore
import com.example.bible.data.KidsGames
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

private enum class KidsHubCategory(val id: String, val label: String) {
    ALL("all", "✨ Все"),
    BIBLE("bible", "📖 Библия и вера"),
    LEARN("learn", "🔤 Учимся"),
    WORLD("world", "🌍 Мир вокруг"),
    PLAY("play", "🎮 Игры и творчество"),
}

private fun kidsHubRouteCategory(route: String): KidsHubCategory = when (route) {
    "kids_bible",
    "kids_stories",
    "kids_story/easter",
    "kids_prayers",
    "kids_verse",
    "kids_songs",
    "kids_heroes",
    "kids_map",
    "kids_values",
    -> KidsHubCategory.BIBLE
    "azbuka",
    "cifry",
    "kids_colors",
    "kids_seasons",
    -> KidsHubCategory.LEARN
    "kids_countries",
    "kids_animals",
    "kids_fish",
    "kids_snakes",
    "kids_insects",
    "kids_trees",
    "kids_plants",
    "kids_space",
    -> KidsHubCategory.WORLD
    "kids_games",
    "kids_coloring",
    -> KidsHubCategory.PLAY
    else -> if (KidsPicturedRoutes.isPicturedRoute(route)) KidsHubCategory.WORLD else KidsHubCategory.ALL
}

private data class KidsBadgeItem(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val current: Int,
    val target: Int,
) {
    val unlocked: Boolean get() = current >= target
}

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
    val allRows = remember(hubState) { mergeKidsHubRows(kidsHubDefaultRows(), hubState) }
    val activeChild = family.activeChild()
    val activeProgress = family.activeProgress()
    val biblePinRead = remember(family) { KidsBiblePinStore.readIds(context) }

    val effectiveStars = if (activeChild != null) kidsStars(activeProgress.points) else kidsStars(points)
    val effectiveLetters = if (activeChild != null && activeProgress.lettersOpened.isNotEmpty()) {
        activeProgress.lettersOpened.size
    } else {
        letters
    }
    val effectiveWinsMap = if (activeChild != null) activeProgress.gameWins else wins
    val effectiveWins = effectiveWinsMap.values.sum()
    val effectiveStories = if (activeChild != null) {
        activeProgress.stories.count { KidsBibleStories.byId(it) != null }
    } else {
        stories.count { KidsBibleStories.byId(it) != null }
    }
    val effectiveBibleBooks = if (activeChild != null) {
        activeProgress.bibleBooksRead.size
    } else {
        (biblePinRead + activeProgress.bibleBooksRead).size
    }

    var selectedCategory by remember { mutableStateOf(KidsHubCategory.ALL) }
    var filterByAge by remember { mutableStateOf(false) }
    var showRewardsDialog by remember { mutableStateOf(false) }

    val rows = remember(allRows, selectedCategory, filterByAge, mode) {
        val targetBand = if (mode == KidsAgeMode.SMALL) "small" else "big"
        val byCat = if (selectedCategory == KidsHubCategory.ALL) {
            allRows
        } else {
            allRows.filter { kidsHubRouteCategory(it.route) == selectedCategory }
        }
        val byAge = if (filterByAge) {
            byCat.filter { it.ageBand == "both" || it.ageBand == targetBand }
        } else {
            byCat.sortedBy { row ->
                when (row.ageBand) {
                    targetBand -> 0
                    "both" -> 1
                    else -> 2
                }
            }
        }
        byAge
    }

    val badges = remember(
        effectiveStars,
        effectiveStories,
        effectiveBibleBooks,
        effectiveLetters,
        effectiveWins,
        effectiveWinsMap,
        activeProgress,
    ) {
        listOf(
            KidsBadgeItem("first_star", "🌟", "Первая звезда", "Заработать первую звезду в разделе «Детям»", effectiveStars, 1),
            KidsBadgeItem("story_1", "📖", "Слушатель историй", "Прочитать первую библейскую историю", effectiveStories, 1),
            KidsBadgeItem("story_5", "📚", "Знаток историй", "Прочитать 5 библейских историй", effectiveStories, 5),
            KidsBadgeItem("bible_1", "✝️", "Чтец Детской Библии", "Прочитать книгу на полке «Детская Библия»", effectiveBibleBooks, 1),
            KidsBadgeItem("bible_5", "🏛️", "Хранитель Писания", "Прочитать 5 книг в «Детской Библии»", effectiveBibleBooks, 5),
            KidsBadgeItem("letters_10", "🔤", "Друг Азбуки", "Открыть 10 букв в Алфавите", effectiveLetters, 10),
            KidsBadgeItem("letters_33", "🎓", "Мастер Алфавита", "Открыть все 33 буквы русского алфавита", effectiveLetters, 33),
            KidsBadgeItem("verse_1", "💌", "Стих в сердце", "Выучить стих недели", activeProgress.versesDone.size, 1),
            KidsBadgeItem("coloring_1", "🎨", "Юный художник", "Раскрасить картинку в раскрасках", activeProgress.coloring.size, 1),
            KidsBadgeItem("values_1", "💛", "Доброе сердце", "Выполнить доброе дело в разделе «Ценности»", activeProgress.valuesDone.size, 1),
            KidsBadgeItem("game_1", "🎮", "Первая победа", "Победить в любой детской игре", effectiveWins, 1),
            KidsBadgeItem("moses_1", "🪄", "Посох Моисея", "Пройти главу в игре «Путь Моисея»", effectiveWinsMap[KidsGames.MOSES] ?: 0, 1),
            KidsBadgeItem("dino_1", "🦖", "Укротитель динозавра", "Набрать 100 очков в игре «Динозаврик»", effectiveWinsMap[KidsGames.DINO] ?: 0, 1),
            KidsBadgeItem("game_10", "🏆", "Чемпион игр", "Одержать 10 побед в играх", effectiveWins, 10),
        )
    }

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
                    IconButton(onClick = { showRewardsDialog = true }) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = "Мои награды", tint = Color(0xFFD97706))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.kids_hub_menu_cd))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Мои награды 🏆") },
                            leadingIcon = { Icon(Icons.Filled.EmojiEvents, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                showRewardsDialog = true
                            },
                        )
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
                    stars = effectiveStars,
                    letters = effectiveLetters,
                    wins = effectiveWins,
                    stories = effectiveStories,
                    bibleBooks = effectiveBibleBooks,
                    unlockedBadges = badges.count { it.unlocked },
                    totalBadges = badges.size,
                    locked = locked,
                    who = activeChild?.let { "${it.name}, ${it.age}" } ?: "Семья",
                    mode = mode,
                    onOpenRewards = { showRewardsDialog = true },
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        Text(
                            if (mode == KidsAgeMode.SMALL) "🐣 Малыши 3–5" else "Малыши 3–5",
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (mode == KidsAgeMode.SMALL) Color(0xFFFDE68A) else Color(0xFFF8FAFC))
                                .clickable { gateAction = { scope.launch { KidsFamilyStore.setAgeMode(context, "small") } } }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            fontWeight = if (mode == KidsAgeMode.SMALL) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            if (mode == KidsAgeMode.BIG) "🎒 Школьники 6–10" else "Школьники 6–10",
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (mode == KidsAgeMode.BIG) Color(0xFFDBEAFE) else Color(0xFFF8FAFC))
                                .clickable { gateAction = { scope.launch { KidsFamilyStore.setAgeMode(context, "big") } } }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            fontWeight = if (mode == KidsAgeMode.BIG) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            if (filterByAge) "✓ Только для ${if (mode == KidsAgeMode.SMALL) "малышей" else "школьников"}" else "По возрасту",
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (filterByAge) Color(0xFFDCFCE7) else Color(0xFFF1F5F9))
                                .clickable { filterByAge = !filterByAge }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            fontWeight = if (filterByAge) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        KidsHubCategory.entries.forEach { category ->
                            val selected = selectedCategory == category
                            Text(
                                category.label,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) Color(0xFF7C3AED) else Color(0xFFEDE9FE))
                                    .clickable { selectedCategory = category }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
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

    if (showRewardsDialog) {
        KidsRewardsDialog(
            who = activeChild?.name ?: "Семья",
            stars = effectiveStars,
            badges = badges,
            onSpeak = speak,
            onDismiss = { showRewardsDialog = false },
        )
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
    bibleBooks: Int,
    unlockedBadges: Int,
    totalBadges: Int,
    locked: Boolean,
    who: String,
    mode: KidsAgeMode,
    onOpenRewards: () -> Unit,
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
                    .clickable(onClick = onOpenRewards)
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
            KidsHeroStat(
                Icons.AutoMirrored.Filled.MenuBook,
                if (bibleBooks > 0) "$stories + $bibleBooks кн." else "$stories/${KidsBibleStories.all.size}",
                if (bibleBooks > 0) "историй и книг" else "историй",
                Modifier.weight(1f),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.22f))
                .clickable(onClick = onOpenRewards)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "🏆 Мои награды и медали",
                color = Color.White,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
            )
            Text(
                "$unlockedBadges из $totalBadges →",
                color = Color(0xFFFFF3D6),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun KidsRewardsDialog(
    who: String,
    stars: Int,
    badges: List<KidsBadgeItem>,
    onSpeak: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val unlockedCount = badges.count { it.unlocked }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("🏆 Мои награды · $who", fontWeight = FontWeight.Black, fontSize = 21.sp)
                Text(
                    "Открыто медалей: $unlockedCount из ${badges.size} · ⭐ $stars ${starsWord(stars)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            "Нажми на медаль, чтобы послушать про неё!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(badges.size) { idx ->
                        val b = badges[idx]
                        val bg = if (b.unlocked) {
                            Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEA580C)))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1)))
                        }
                        val fg = if (b.unlocked) Color.White else Color(0xFF334155)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(bg)
                                .clickable {
                                    val status = if (b.unlocked) "Награда получена!" else "Пока закрыто. Прогресс ${b.current} из ${b.target}."
                                    onSpeak("${b.title}. ${b.description}. $status")
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = if (b.unlocked) 0.25f else 0.55f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (b.unlocked) b.emoji else "🔒", fontSize = 24.sp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(b.title, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text(b.description, color = fg.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 17.sp)
                                Text(
                                    if (b.unlocked) "✓ Получено!" else "Прогресс: ${b.current.coerceAtMost(b.target)} / ${b.target}",
                                    color = fg,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть", fontWeight = FontWeight.Bold) }
        },
    )
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
        val art = Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
        if (row.imageRes != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(row.imageRes)
                    .size(420)
                    .crossfade(kidsMotionEnabled())
                    .build(),
                contentDescription = null,
                modifier = art.kidsPhotoMat(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                art
                    .clip(KidsPhotoShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                if (!row.emojiThumb.isNullOrBlank()) {
                    Text(row.emojiThumb, fontSize = 56.sp, lineHeight = 64.sp)
                } else {
                    Icon(
                        row.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp),
                    )
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
