package com.example.bible.ui

import android.content.res.Configuration
import java.io.File
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.example.bible.R
import com.example.bible.data.KidsCountries
import com.example.bible.data.KidsCountryItem
import com.example.bible.data.KidsPicturedItem
import com.example.bible.data.KidsPicturedRoutes
import com.example.bible.data.KidsTopicItem
import com.example.bible.data.KidsUserSectionsState
import java.util.LinkedHashSet
import java.util.Locale

/** Раскладка плиток с фото: квадрат или широкая ячейка (рыбы —1 колонка в портрете, 2 в альбоме). */
enum class KidsPicturedTileStyle {
    Square,
    FishWide,
}

internal object KidsPicturedRouteUi {
    fun title(route: String, state: KidsUserSectionsState?): String {
        state?.customHub?.find { it.route == route }?.title?.let { return it }
        return when (route) {
            "kids_animals" -> "Животные"
            "kids_fish" -> "Рыбы"
            "kids_snakes" -> "Змеи"
            "kids_insects" -> "Насекомые"
            "kids_trees" -> "Деревья"
            "kids_plants" -> "Растения"
            else -> "Альбом"
        }
    }

    fun tileStyle(route: String, state: KidsUserSectionsState?): KidsPicturedTileStyle {
        if (route == "kids_fish") return KidsPicturedTileStyle.FishWide
        val tile = state?.customHub?.find { it.route == route }?.tileStyle?.lowercase()
            ?: return KidsPicturedTileStyle.Square
        return when (tile) {
            "fish", "fishwide", "wide" -> KidsPicturedTileStyle.FishWide
            else -> KidsPicturedTileStyle.Square
        }
    }

    fun playRawOnTap(route: String): Boolean = route in setOf(
        "kids_animals",
        "kids_fish",
        "kids_snakes",
        "kids_insects",
    )
}

private data class KidsSeasonCard(
    val title: String,
    val speak: String,
    @DrawableRes val imageRes: Int,
)

private val kidsSeasonCards: List<KidsSeasonCard> = listOf(
    KidsSeasonCard("Зима", "Зима", R.drawable.kids_season_winter),
    KidsSeasonCard("Весна", "Весна", R.drawable.kids_season_spring),
    KidsSeasonCard("Лето", "Лето", R.drawable.kids_season_summer),
    KidsSeasonCard("Осень", "Осень", R.drawable.kids_season_autumn),
)

/** Два региональных символа Unicode → эмодзи флага (ISO 3166-1 alpha-2). */
private fun isoToFlagEmoji(isoCode: String): String {
    val u = isoCode.uppercase(Locale.ROOT)
    if (u.length != 2) return ""
    val a = u[0]
    val b = u[1]
    if (a !in 'A'..'Z' || b !in 'A'..'Z') return ""
    val first = 0x1F1E6 + (a.code - 'A'.code)
    val second = 0x1F1E6 + (b.code - 'A'.code)
    return String(intArrayOf(first, second), 0, 2)
}

/** Подбирает локаль TTS для приветствия; при отсутствии голоса — запасные теги (без отдельных аудиофайлов). */
private fun prepareTtsForCountryHello(tts: TextToSpeech, primaryLocaleTag: String) {
    val chain = LinkedHashSet<String>()
    chain.add(primaryLocaleTag)
    val dash = primaryLocaleTag.indexOf('-')
    if (dash > 0) {
        val langOnly = primaryLocaleTag.substring(0, dash)
        if (langOnly.isNotEmpty()) chain.add(langOnly)
    }
    when {
        primaryLocaleTag.startsWith("tk") -> {
            chain.add("tr-TR")
            chain.add("ru-RU")
        }
        primaryLocaleTag.startsWith("ne") -> chain.add("hi-IN")
        primaryLocaleTag == "fa-AF" -> {
            chain.add("fa-IR")
            chain.add("ar-SA")
        }
        primaryLocaleTag.startsWith("kk") -> chain.add("ru-RU")
        primaryLocaleTag.startsWith("be") -> chain.add("ru-RU")
        primaryLocaleTag.startsWith("uk") -> chain.add("ru-RU")
        primaryLocaleTag.startsWith("mn") -> chain.add("zh-CN")
        primaryLocaleTag.startsWith("fil") -> chain.add("tl-PH")
        primaryLocaleTag == "mi-NZ" -> chain.add("en-NZ")
        primaryLocaleTag == "sq-XK" -> chain.add("sq-AL")
        primaryLocaleTag.startsWith("sr-ME") -> chain.add("sr-RS")
    }
    chain.add("en-US")
    for (tag in chain) {
        val res = tts.setLanguage(Locale.forLanguageTag(tag))
        if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
            return
        }
    }
    tts.language = Locale.US
}

/** Пауза между русским названием страны и «привет» на языке страны (мс). */
private const val KidsCountryHelloPauseMs = 550L

/**
 * Озвучка страны: сначала название по-русски, пауза, затем приветствие на родном языке.
 * Отменяет отложенное приветствие при повторном вызове, стопе или прерывании.
 */
private class KidsCountryHelloTtsCoordinator(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private var pendingHello: Runnable? = null

    /** Активный id фразы с названием страны (уникален на каждое нажатие). */
    private var activeNameUtteranceId: String? = null

    fun cancelPendingHello() {
        pendingHello?.let { handler.removeCallbacks(it) }
        pendingHello = null
        activeNameUtteranceId = null
    }

    fun speakCountryNameThenHello(tts: TextToSpeech, country: KidsCountryItem) {
        cancelPendingHello()
        val nameForTts = country.speak
        val nameUtteranceId = "kids_country_ru_${country.isoCode}_${System.nanoTime()}"
        activeNameUtteranceId = nameUtteranceId
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (utteranceId != nameUtteranceId || utteranceId != activeNameUtteranceId) return
                scheduleHello(tts, country)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                cancelPendingHello()
            }

            @Suppress("DEPRECATION")
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onError(utteranceId, TextToSpeech.ERROR)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId != nameUtteranceId || utteranceId != activeNameUtteranceId) return
                scheduleHello(tts, country)
            }
        })
        val ruRes = tts.setLanguage(Locale.forLanguageTag("ru-RU"))
        if (ruRes == TextToSpeech.LANG_MISSING_DATA || ruRes == TextToSpeech.LANG_NOT_SUPPORTED) {
            @Suppress("DEPRECATION")
            tts.language = Locale("ru", "RU")
        }
        tts.speak(nameForTts, TextToSpeech.QUEUE_FLUSH, null, nameUtteranceId)
    }

    private fun scheduleHello(tts: TextToSpeech, country: KidsCountryItem) {
        if (pendingHello != null) return
        val r = Runnable {
            pendingHello = null
            activeNameUtteranceId = null
            prepareTtsForCountryHello(tts, country.ttsLocaleTag)
            tts.speak(
                country.helloNative,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "kids_hello_${country.isoCode}_${System.nanoTime()}",
            )
        }
        pendingHello = r
        handler.postDelayed(r, KidsCountryHelloPauseMs)
    }
}

/** Визуальные градиенты карточек «Детям» (в духе экрана «Медиа»). */
internal enum class KidsHubCardVisual {
    Pictures,
    Musician,
    Pesnopenie,
    Videos,
    Audios,
}

private data class KidsHubCardBrushes(
    val cardBrush: Brush,
    val iconBrush: Brush,
    val iconTint: Color,
)

private fun buildKidsHubBrushes(style: KidsHubCardVisual, cs: ColorScheme): KidsHubCardBrushes {
    return when (style) {
        KidsHubCardVisual.Pictures -> KidsHubCardBrushes(
            cardBrush = Brush.linearGradient(
                listOf(
                    cs.primary.copy(alpha = 0.12f),
                    cs.primaryContainer.copy(alpha = 0.48f),
                    cs.surface,
                ),
            ),
            iconBrush = Brush.linearGradient(
                listOf(cs.primary, cs.primary.copy(alpha = 0.88f)),
            ),
            iconTint = cs.onPrimary,
        )
        KidsHubCardVisual.Musician -> KidsHubCardBrushes(
            cardBrush = Brush.linearGradient(
                listOf(
                    cs.tertiary.copy(alpha = 0.10f),
                    cs.tertiaryContainer.copy(alpha = 0.45f),
                    cs.surface,
                ),
            ),
            iconBrush = Brush.linearGradient(
                listOf(cs.tertiary, cs.secondary.copy(alpha = 0.92f)),
            ),
            iconTint = cs.onTertiary,
        )
        KidsHubCardVisual.Pesnopenie -> KidsHubCardBrushes(
            cardBrush = Brush.linearGradient(
                listOf(
                    cs.secondary.copy(alpha = 0.11f),
                    cs.secondaryContainer.copy(alpha = 0.42f),
                    cs.surface,
                ),
            ),
            iconBrush = Brush.linearGradient(
                listOf(cs.secondary, cs.primary.copy(alpha = 0.85f)),
            ),
            iconTint = cs.onSecondary,
        )
        KidsHubCardVisual.Videos -> KidsHubCardBrushes(
            cardBrush = Brush.linearGradient(
                listOf(
                    cs.primary.copy(alpha = 0.07f),
                    cs.surfaceContainerHighest.copy(alpha = 0.88f),
                    cs.surface,
                ),
            ),
            iconBrush = Brush.linearGradient(
                listOf(cs.primary, cs.secondary.copy(alpha = 0.95f)),
            ),
            iconTint = cs.onPrimary,
        )
        KidsHubCardVisual.Audios -> KidsHubCardBrushes(
            cardBrush = Brush.linearGradient(
                listOf(
                    cs.tertiary.copy(alpha = 0.07f),
                    cs.primaryContainer.copy(alpha = 0.38f),
                    cs.surface,
                ),
            ),
            iconBrush = Brush.linearGradient(
                listOf(cs.primary, cs.tertiary.copy(alpha = 0.9f)),
            ),
            iconTint = cs.onPrimary,
        )
    }
}

@Composable
private fun rememberKidsHubCardBrushes(style: KidsHubCardVisual): KidsHubCardBrushes {
    val cs = MaterialTheme.colorScheme
    return remember(style, cs) {
        buildKidsHubBrushes(style, cs)
    }
}

internal data class KidsHubRow(
    val title: String,
    val subtitle: String,
    val route: String,
    val icon: ImageVector,
    val cardStyle: KidsHubCardVisual,
    @DrawableRes val imageRes: Int? = null,
    val emojiThumb: String? = null,
)

internal fun kidsHubCardVisualFromToken(token: String): KidsHubCardVisual = when (token) {
    "Musician" -> KidsHubCardVisual.Musician
    "Pesnopenie" -> KidsHubCardVisual.Pesnopenie
    "Videos" -> KidsHubCardVisual.Videos
    "Audios" -> KidsHubCardVisual.Audios
    else -> KidsHubCardVisual.Pictures
}

internal fun kidsHubCardVisualToToken(v: KidsHubCardVisual): String = when (v) {
    KidsHubCardVisual.Pictures -> "Pictures"
    KidsHubCardVisual.Musician -> "Musician"
    KidsHubCardVisual.Pesnopenie -> "Pesnopenie"
    KidsHubCardVisual.Videos -> "Videos"
    KidsHubCardVisual.Audios -> "Audios"
}

internal fun kidsHubDefaultRows(): List<KidsHubRow> = listOf(
    KidsHubRow(
        title = "Алфавит",
        subtitle = "Буквы, слоги и примеры слов",
        route = "azbuka",
        icon = Icons.AutoMirrored.Filled.MenuBook,
        cardStyle = KidsHubCardVisual.Pictures,
        emojiThumb = "🔤",
    ),
    KidsHubRow(
        title = "Цифры",
        subtitle = "Счёт, фигуры и задачки",
        route = "cifry",
        icon = Icons.Filled.Numbers,
        cardStyle = KidsHubCardVisual.Musician,
        emojiThumb = "🔢",
    ),
    KidsHubRow(
        title = "Игры",
        subtitle = "Крестики-нолики и другие настольные игры",
        route = "kids_games",
        icon = Icons.Filled.Extension,
        cardStyle = KidsHubCardVisual.Videos,
        emojiThumb = "🎮",
    ),
    KidsHubRow(
        title = "Цвета",
        subtitle = "Названия цветов и оттенков",
        route = "kids_colors",
        icon = Icons.Filled.Palette,
        cardStyle = KidsHubCardVisual.Pesnopenie,
        emojiThumb = "🎨",
    ),
    KidsHubRow(
        title = "Времена года",
        subtitle = "Зима, весна, лето и осень",
        route = "kids_seasons",
        icon = Icons.Filled.WbSunny,
        cardStyle = KidsHubCardVisual.Videos,
        imageRes = R.drawable.kids_season_spring,
    ),
    KidsHubRow(
        title = "Страны",
        subtitle = "Флаги и приветствия на языках мира",
        route = "kids_countries",
        icon = Icons.Filled.Public,
        cardStyle = KidsHubCardVisual.Audios,
        emojiThumb = "🌍",
    ),
    KidsHubRow(
        title = "Животные",
        subtitle = "Фотографии и звуки зверей и птиц",
        route = "kids_animals",
        icon = Icons.Filled.Pets,
        cardStyle = KidsHubCardVisual.Pictures,
        imageRes = R.drawable.kids_anml_medved,
    ),
    KidsHubRow(
        title = "Рыбы",
        subtitle = "Речные и морские обитатели",
        route = "kids_fish",
        icon = Icons.Filled.SetMeal,
        cardStyle = KidsHubCardVisual.Musician,
        imageRes = R.drawable.kids_fish_klovn,
    ),
    KidsHubRow(
        title = "Змеи",
        subtitle = "Змеи с иллюстрациями",
        route = "kids_snakes",
        icon = Icons.Filled.Nature,
        cardStyle = KidsHubCardVisual.Pesnopenie,
        imageRes = R.drawable.kids_snake_kobra,
    ),
    KidsHubRow(
        title = "Насекомые",
        subtitle = "Бабочки, жуки, пауки и другое",
        route = "kids_insects",
        icon = Icons.Filled.BugReport,
        cardStyle = KidsHubCardVisual.Videos,
        imageRes = R.drawable.kids_ins_babochka,
    ),
    KidsHubRow(
        title = "Деревья",
        subtitle = "Лиственные, хвойные и плодовые",
        route = "kids_trees",
        icon = Icons.Filled.Park,
        cardStyle = KidsHubCardVisual.Audios,
        imageRes = R.drawable.kids_tree_bereza,
    ),
    KidsHubRow(
        title = "Растения",
        subtitle = "Цветы, травы и культурные растения",
        route = "kids_plants",
        icon = Icons.Filled.LocalFlorist,
        cardStyle = KidsHubCardVisual.Pictures,
        imageRes = R.drawable.kids_plant_tulpan,
    ),
)

internal fun mergeKidsHubRows(defaults: List<KidsHubRow>, state: KidsUserSectionsState?): List<KidsHubRow> {
    if (state == null) return defaults
    val tailored = defaults
        .filter { it.route !in state.hiddenRoutes }
        .map { row ->
            row.copy(
                title = state.titleOverrides[row.route] ?: row.title,
                subtitle = state.subtitleOverrides[row.route] ?: row.subtitle,
            )
        }
    val byRoute = tailored.associateBy { it.route }.toMutableMap()
    for (entry in state.customHub) {
        byRoute[entry.route] = KidsHubRow(
            title = entry.title,
            subtitle = entry.subtitle,
            route = entry.route,
            icon = Icons.Filled.Star,
            cardStyle = kidsHubCardVisualFromToken(entry.cardStyle),
            imageRes = null,
            emojiThumb = entry.emojiThumb,
        )
    }
    val order = state.order
    if (order == null) {
        val defaultRoutes = tailored.map { it.route }.toSet()
        val extraCustom = state.customHub.map { it.route }.filter { it !in defaultRoutes }
        val extras = extraCustom.mapNotNull { byRoute[it] }
        return tailored + extras
    }
    val out = mutableListOf<KidsHubRow>()
    val used = mutableSetOf<String>()
    for (r in order) {
        byRoute[r]?.let {
            out.add(it)
            used.add(r)
        }
    }
    for (row in tailored) {
        if (row.route !in used) {
            out.add(row)
            used.add(row.route)
        }
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsSeasonsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val ttsState = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val e = engine ?: return@TextToSpeech
                e.language = Locale.forLanguageTag("ru-RU")
                e.setSpeechRate(0.9f)
                e.setPitch(1.08f)
            }
            if (status == TextToSpeech.SUCCESS) {
                ttsState.value = engine
            }
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
            ttsState.value = null
        }
    }
    val speak: (String) -> Unit = { text ->
        ttsState.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kids_season_${System.nanoTime()}")
    }
    var preview by remember { mutableStateOf<KidsSeasonCard?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Времена года", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { ttsState.value?.stop() }) {
                        Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.audio_stop))
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(kidsSeasonCards, key = { it.title }) { season ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            preview = season
                            speak(season.speak)
                        },
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                ) {
                    Column {
                        Image(
                            painter = painterResource(season.imageRes),
                            contentDescription = season.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            season.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    preview?.let { season ->
        Dialog(
            onDismissRequest = { preview = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            season.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        IconButton(onClick = { preview = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                        }
                    }
                    Image(
                        painter = painterResource(season.imageRes),
                        contentDescription = season.title,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        contentScale = ContentScale.Fit,
                    )
                    TextButton(
                        onClick = { speak(season.speak) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text("Прослушать ещё раз")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsCountriesScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val ttsState = remember { mutableStateOf<TextToSpeech?>(null) }
    val countryHelloTts = remember { KidsCountryHelloTtsCoordinator() }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val e = engine ?: return@TextToSpeech
                e.language = Locale.forLanguageTag("ru-RU")
                e.setSpeechRate(0.9f)
                e.setPitch(1.08f)
            }
            if (status == TextToSpeech.SUCCESS) {
                ttsState.value = engine
            }
        }
        onDispose {
            countryHelloTts.cancelPendingHello()
            engine?.stop()
            engine?.shutdown()
            ttsState.value = null
        }
    }
    val speakHello: (KidsCountryItem) -> Unit = { country ->
        ttsState.value?.let { tts ->
            countryHelloTts.speakCountryNameThenHello(tts, country)
        }
    }
    val countries = remember { KidsCountries.all }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columnCount = if (isLandscape) 4 else 2
    val gridPadding = if (isLandscape) 8.dp else 12.dp
    val tileSpacing = if (isLandscape) 6.dp else 10.dp
    val cardShape = if (isLandscape) RoundedCornerShape(12.dp) else RoundedCornerShape(16.dp)
    val flagFontSize = if (isLandscape) 34.sp else 64.sp
    val namePadding = if (isLandscape) 4.dp else 12.dp
    val nameStyle = if (isLandscape) {
        MaterialTheme.typography.labelLarge
    } else {
        MaterialTheme.typography.titleMedium
    }
    val helloStyle = if (isLandscape) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.bodySmall
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Страны", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            countryHelloTts.cancelPendingHello()
                            ttsState.value?.stop()
                        },
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.audio_stop))
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(gridPadding),
            horizontalArrangement = Arrangement.spacedBy(tileSpacing),
            verticalArrangement = Arrangement.spacedBy(tileSpacing),
        ) {
            items(countries, key = { "${it.isoCode}-${it.nameRu}" }) { country ->
                val flag = isoToFlagEmoji(country.isoCode)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { speakHello(country) },
                    shape = cardShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isLandscape) 2.dp else 3.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                flag,
                                fontSize = flagFontSize,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Text(
                            country.nameRu,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = namePadding, vertical = namePadding / 2),
                            style = nameStyle,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = if (isLandscape) 2 else 3,
                        )
                        Text(
                            country.helloNative,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = namePadding, end = namePadding, bottom = namePadding),
                            style = helloStyle,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = if (isLandscape) 2 else 3,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KidsPicturedTileImage(
    item: KidsPicturedItem,
    emojiSize: TextUnit,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    imageBackdrop: Color? = null,
) {
    val context = LocalContext.current
    val customFile = item.customImagePath?.let { File(context.filesDir, it) }?.takeIf { it.isFile }
    when {
        customFile != null -> {
            if (imageBackdrop != null && contentScale == ContentScale.Fit) {
                Box(
                    modifier = modifier.background(imageBackdrop),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(customFile).crossfade(true).build(),
                        contentDescription = item.label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                    )
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(customFile).crossfade(true).build(),
                    contentDescription = item.label,
                    modifier = modifier,
                    contentScale = contentScale,
                )
            }
        }
        item.imageRes != null -> {
            if (imageBackdrop != null && contentScale == ContentScale.Fit) {
                Box(
                    modifier = modifier.background(imageBackdrop),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(item.imageRes),
                        contentDescription = item.label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                    )
                }
            } else {
                Image(
                    painter = painterResource(item.imageRes),
                    contentDescription = item.label,
                    modifier = modifier,
                    contentScale = contentScale,
                )
            }
        }
        else -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Text(
                    item.emoji,
                    fontSize = emojiSize,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsPicturedGridScreen(
    title: String,
    items: List<KidsPicturedItem>,
    onBack: () -> Unit,
    showDetailDialog: Boolean = true,
    playRawSoundsOnTap: Boolean = false,
    tileStyle: KidsPicturedTileStyle = KidsPicturedTileStyle.Square,
) {
    val context = LocalContext.current
    val ttsState = remember { mutableStateOf<TextToSpeech?>(null) }
    var sfxPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    fun stopSfx() {
        sfxPlayer?.let { mp ->
            sfxPlayer = null
            mp.setOnCompletionListener(null)
            try {
                mp.stop()
            } catch (_: IllegalStateException) {
            }
            mp.release()
        }
    }
    fun playSfx(item: KidsPicturedItem) {
        stopSfx()
        val customPath = item.customSoundPath?.let { File(context.filesDir, it).absolutePath }
            ?.takeIf { File(it).isFile }
        val mp = if (customPath != null) {
            runCatching {
                MediaPlayer().apply {
                    setDataSource(customPath)
                    prepare()
                }
            }.getOrNull()
        } else {
            val res = item.soundRes ?: return
            MediaPlayer.create(context, res)
        } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val p = item.soundPitch.coerceIn(0.5f, 2f)
            mp.playbackParams = PlaybackParams().setPitch(p)
        }
        mp.setOnCompletionListener { player ->
            if (sfxPlayer === player) {
                sfxPlayer = null
                player.release()
            }
        }
        sfxPlayer = mp
        mp.start()
    }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val e = engine ?: return@TextToSpeech
                e.language = Locale.forLanguageTag("ru-RU")
                e.setSpeechRate(0.9f)
                e.setPitch(1.08f)
            }
            if (status == TextToSpeech.SUCCESS) {
                ttsState.value = engine
            }
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
            ttsState.value = null
        }
    }
    DisposableEffect(Unit) {
        onDispose { stopSfx() }
    }
    val speak: (String) -> Unit = { text ->
        ttsState.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kids_pic_${System.nanoTime()}")
    }
    var preview by remember { mutableStateOf<KidsPicturedItem?>(null) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columnCount = when (tileStyle) {
        KidsPicturedTileStyle.Square -> if (isLandscape) 4 else 2
        KidsPicturedTileStyle.FishWide -> if (isLandscape) 2 else 1
    }
    val imageAspectRatio = when (tileStyle) {
        KidsPicturedTileStyle.Square -> 1f
        KidsPicturedTileStyle.FishWide -> 16f / 10f
    }
    val imageContentScale = when (tileStyle) {
        KidsPicturedTileStyle.Square -> ContentScale.Crop
        KidsPicturedTileStyle.FishWide -> ContentScale.Fit
    }
    val imageBackdrop = when (tileStyle) {
        KidsPicturedTileStyle.Square -> null
        KidsPicturedTileStyle.FishWide -> MaterialTheme.colorScheme.surfaceVariant
    }
    val gridPadding = if (isLandscape) 6.dp else 12.dp
    val tileSpacing = if (isLandscape) 4.dp else 10.dp
    val cardShape = if (isLandscape) RoundedCornerShape(10.dp) else RoundedCornerShape(16.dp)
    val cardElevation = if (isLandscape) 2.dp else 3.dp
    val emojiSize = if (isLandscape) 28.sp else 64.sp
    val labelPadding = if (isLandscape) 3.dp else 12.dp
    val labelStyle = if (isLandscape) {
        MaterialTheme.typography.labelLarge
    } else {
        MaterialTheme.typography.titleMedium
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        ttsState.value?.stop()
                        stopSfx()
                    }) {
                        Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.audio_stop))
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(gridPadding),
            horizontalArrangement = Arrangement.spacedBy(tileSpacing),
            verticalArrangement = Arrangement.spacedBy(tileSpacing),
        ) {
            items(
                items,
                key = { item ->
                    "${item.itemKey}\u0000${item.customImagePath ?: ""}\u0000${item.customSoundPath ?: ""}\u0000${item.imageRes ?: 0}"
                },
            ) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (showDetailDialog) {
                                preview = item
                            }
                            speak(item.speak)
                            if (playRawSoundsOnTap || item.customSoundPath != null) {
                                playSfx(item)
                            }
                        },
                    shape = cardShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                ) {
                    Column {
                        KidsPicturedTileImage(
                            item = item,
                            emojiSize = emojiSize,
                            contentScale = imageContentScale,
                            imageBackdrop = imageBackdrop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(imageAspectRatio),
                        )
                        Text(
                            item.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(labelPadding),
                            style = labelStyle,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = if (isLandscape) 2 else 3,
                        )
                    }
                }
            }
        }
    }

    if (showDetailDialog) {
        preview?.let { item ->
            Dialog(
                onDismissRequest = { preview = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    val detailScale = if (tileStyle == KidsPicturedTileStyle.FishWide) {
                        ContentScale.Fit
                    } else {
                        ContentScale.Crop
                    }
                    val detailBackdrop = if (tileStyle == KidsPicturedTileStyle.FishWide) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        null
                    }
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = if (item.detailFullScreen) {
                            Alignment.Start
                        } else {
                            Alignment.CenterHorizontally
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                            IconButton(onClick = { preview = null }) {
                                Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                            }
                        }
                        if (item.detailFullScreen) {
                            KidsPicturedTileImage(
                                item = item,
                                emojiSize = 120.sp,
                                contentScale = detailScale,
                                imageBackdrop = detailBackdrop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                KidsPicturedTileImage(
                                    item = item,
                                    emojiSize = 96.sp,
                                    contentScale = imageContentScale,
                                    imageBackdrop = imageBackdrop,
                                    modifier = Modifier
                                        .widthIn(max = 420.dp)
                                        .fillMaxWidth(0.92f)
                                        .aspectRatio(imageAspectRatio),
                                )
                            }
                        }
                        TextButton(
                            onClick = { speak(item.speak) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text("Прослушать ещё раз")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KidsHubThumbnail(
    imageRes: Int?,
    emojiThumb: String?,
    icon: ImageVector,
    iconBrush: Brush,
    iconTint: Color,
) {
    val thumbShape = RoundedCornerShape(16.dp)
    val mod = Modifier.size(72.dp)
    when {
        imageRes != null -> {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                modifier = mod.clip(thumbShape),
                contentScale = ContentScale.Crop,
            )
        }
        emojiThumb != null -> {
            Box(
                modifier = mod
                    .clip(thumbShape)
                    .background(iconBrush),
                contentAlignment = Alignment.Center,
            ) {
                Text(emojiThumb, style = MaterialTheme.typography.headlineMedium)
            }
        }
        else -> {
            Box(
                modifier = mod
                    .clip(thumbShape)
                    .background(iconBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KidsHubSectionCard(
    row: KidsHubRow,
    onClick: () -> Unit,
) {
    val brushes = rememberKidsHubCardBrushes(row.cardStyle)
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp,
        ),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brushes.cardBrush),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KidsHubThumbnail(
                    imageRes = row.imageRes,
                    emojiThumb = row.emojiThumb,
                    icon = row.icon,
                    iconBrush = brushes.iconBrush,
                    iconTint = brushes.iconTint,
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                ) {
                    Text(
                        row.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        row.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 1.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsHubScreen(
    navController: NavHostController,
    onBack: () -> Unit,
    hubState: KidsUserSectionsState? = null,
    onEditSections: (() -> Unit)? = null,
) {
    val rows = remember(hubState) {
        mergeKidsHubRows(kidsHubDefaultRows(), hubState)
    }
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Детям", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (onEditSections != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.kids_hub_menu_cd))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(rows, key = { it.route }) { row ->
                KidsHubSectionCard(
                    row = row,
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
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsTopicScreen(
    title: String,
    topicItems: List<KidsTopicItem>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val ttsState = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val e = engine ?: return@TextToSpeech
                e.language = Locale.forLanguageTag("ru-RU")
                e.setSpeechRate(0.9f)
                e.setPitch(1.08f)
            }
            if (status == TextToSpeech.SUCCESS) {
                ttsState.value = engine
            }
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
            ttsState.value = null
        }
    }
    val speak: (String) -> Unit = { text ->
        ttsState.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kids_${System.nanoTime()}")
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { ttsState.value?.stop() }) {
                        Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.audio_stop))
                    }
                },
            )
        },
    ) { padding ->
        val colorTiles = remember(topicItems) { topicItems.all { it.swatchArgb != null } }
        val isLandscape =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val colorColumns = if (isLandscape) 4 else 2
        val gridPad = if (colorTiles && isLandscape) 6.dp else 12.dp
        val tileGap = if (colorTiles && isLandscape) 6.dp else 8.dp
        LazyVerticalGrid(
            columns = if (colorTiles) GridCells.Fixed(colorColumns) else GridCells.Adaptive(minSize = 148.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(gridPad),
            horizontalArrangement = Arrangement.spacedBy(tileGap),
            verticalArrangement = Arrangement.spacedBy(tileGap),
        ) {
            items(topicItems, key = { it.label }) { item ->
                if (colorTiles && item.swatchArgb != null) {
                    val bg = Color(item.swatchArgb.toInt())
                    val onBg = if (bg.luminance() > 0.55f) {
                        Color(0xFF1A1A1A)
                    } else {
                        Color.White
                    }
                    val needsEdge = item.swatchArgb == 0xFFFFFFFFUL ||
                        bg.luminance() > 0.92f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { speak(item.speak) },
                            colors = CardDefaults.cardColors(containerColor = bg),
                            border = if (needsEdge) {
                                BorderStroke(2.dp, Color(0xFF757575).copy(alpha = 0.65f))
                            } else {
                                BorderStroke(1.dp, Color.Black.copy(alpha = 0.12f))
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    item.label,
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    color = onBg,
                                    maxLines = 3,
                                    lineHeight = 18.sp,
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { speak(item.speak) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            item.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
