package com.example.bible.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.KidsGames
import com.example.bible.games.KidsGameAudio
import com.example.bible.games.KidsMusicTrack
import com.example.bible.games.KidsSfx
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Ключевой библейский стих Бытия 1–3 с духовным смыслом и интерактивным заданием на запоминание.
 */
private data class GenesisWisdomVerse(
    val id: Int,
    val stage: Int,
    val ref: String,
    val fullText: String,
    val deepMeaningTitle: String,
    val deepMeaningBody: String,
    val quizPromptWithBlank: String,
    val correctWord: String,
    val options: List<String>,
    val ttsText: String,
)

private val GENESIS_WISDOM_VERSES = listOf(
    GenesisWisdomVerse(
        id = 1,
        stage = 1,
        ref = "Бытие 1:27",
        fullText = "«И сотворил Бог человека по образу Своему, по образу Божию сотворил его; мужчину и женщину сотворил их.»",
        deepMeaningTitle = "Образ Божий в каждом человеке",
        deepMeaningBody = "Человек не случайная пылинка во Вселенной. Господь вложил в нас Свой образ: способность любить, творить добро, мыслить, иметь свободную волю и общаться с Творцом как дети с любящим Отцом.",
        quizPromptWithBlank = "«И сотворил Бог человека по [ ... ] Своему, по образу Божию сотворил его»",
        correctWord = "образу",
        options = listOf("образу", "случаю", "облаку"),
        ttsText = "Бытие, глава первая, стих двадцать седьмой. И сотворил Бог человека по образу Своему, по образу Божию сотворил его; мужчину и женщину сотворил их.",
    ),
    GenesisWisdomVerse(
        id = 2,
        stage = 1,
        ref = "Бытие 2:15",
        fullText = "«И взял Господь Бог человека, и поселил его в саду Едемском, чтобы возделывать его и хранить его.»",
        deepMeaningTitle = "Призвание хранить и созидать",
        deepMeaningBody = "Даже в совершенном Эдемском саду у человека было высокое призвание — «возделывать и хранить». Труд, забота о Божьем мире и хранение своего сердца в чистоте — это благословение, данное ещё до грехопадения.",
        quizPromptWithBlank = "«Поселил его в саду Едемском, чтобы возделывать его и [ ... ] его»",
        correctWord = "хранить",
        options = listOf("хранить", "забыть", "сломать"),
        ttsText = "Бытие, глава вторая, стих пятнадцатый. И взял Господь Бог человека, и поселил его в саду Едемском, чтобы возделывать его и хранить его.",
    ),
    GenesisWisdomVerse(
        id = 3,
        stage = 2,
        ref = "Бытие 2:18",
        fullText = "«И сказал Господь Бог: не хорошо быть человеку одному; сотворим ему помощника, соответственного ему.»",
        deepMeaningTitle = "Дар семьи, дружбы и взаимной любви",
        deepMeaningBody = "Бог есть Любовь, поэтому человек создан для общения, единства и взаимопомощи. Ева была создана из ребра Адама — близко к его сердцу, как верная помощница и равная спутница жизни.",
        quizPromptWithBlank = "«Не хорошо быть человеку [ ... ]; сотворим ему помощника, соответственного ему»",
        correctWord = "одному",
        options = listOf("одному", "доброму", "в саду"),
        ttsText = "Бытие, глава вторая, стих восемнадцатый. И сказал Господь Бог: не хорошо быть человеку одному; сотворим ему помощника, соответственного ему.",
    ),
    GenesisWisdomVerse(
        id = 4,
        stage = 2,
        ref = "Бытие 2:19",
        fullText = "«Господь Бог образовал из земли всех животных полевых и всех птиц небесных, и привёл к человеку, чтобы видеть, как он назовёт их.»",
        deepMeaningTitle = "Мудрость и ответственность перед творением",
        deepMeaningBody = "Дать имя в Библии означает понять суть и проявить заботу. Господь доверил человеку беречь животный мир и с мудростью управлять землёй, не как жестокий хозяин, а как добрый садовник и пастырь.",
        quizPromptWithBlank = "«Привёл к человеку, чтобы видеть, как он [ ... ] их»",
        correctWord = "назовёт",
        options = listOf("назовёт", "прогонит", "спрячет"),
        ttsText = "Бытие, глава вторая, стих девятнадцатый. Господь Бог привёл животных к человеку, чтобы видеть, как он назовёт их.",
    ),
    GenesisWisdomVerse(
        id = 5,
        stage = 3,
        ref = "Бытие 2:16–17",
        fullText = "«От всякого дерева в саду ты будешь есть, а от дерева познания добра и зла не ешь от него.»",
        deepMeaningTitle = "Свобода воли и доверие Слову Божьему",
        deepMeaningBody = "Бог подарил человеку весь прекрасный сад и дал лишь одну заповедь. Почему? Потому что настоящая любовь невозможна без свободного выбора доверять Богу и слушаться Его Слова.",
        quizPromptWithBlank = "«От всякого дерева в саду ты будешь есть, а от дерева познания добра и зла [ ... ] от него»",
        correctWord = "не ешь",
        options = listOf("не ешь", "срывай", "прячься"),
        ttsText = "Бытие, глава вторая, стихи шестнадцатый и семнадцатый. От всякого дерева в саду ты будешь есть, а от дерева познания добра и зла не ешь от него.",
    ),
    GenesisWisdomVerse(
        id = 6,
        stage = 3,
        ref = "Бытие 3:1",
        fullText = "«Змей был хитрее всех зверей полевых... И сказал змей жене: подлинно ли сказал Бог: не ешьте ни от какого дерева в раю?»",
        deepMeaningTitle = "Как начинается искушение",
        deepMeaningBody = "Обрати внимание: змей начинает искушение с того, что искажает Слово Божие и сеет сомнение в Божьей любви («Подлинно ли сказал Бог? Будто Бог запретил вам всё!»). Поэтому так важно точно помнить Писание наизусть!",
        quizPromptWithBlank = "«И сказал змей жене: [ ... ] ли сказал Бог: не ешьте ни от какого дерева в раю?»",
        correctWord = "подлинно",
        options = listOf("подлинно", "громко", "давно"),
        ttsText = "Бытие, глава третья, стих первый. И сказал змей жене: подлинно ли сказал Бог: не ешьте ни от какого дерева в раю?",
    ),
    GenesisWisdomVerse(
        id = 7,
        stage = 4,
        ref = "Бытие 3:9",
        fullText = "«И воззвал Господь Бог к Адаму и сказал ему: где ты?»",
        deepMeaningTitle = "Голос Божьей любви, ищущий человека",
        deepMeaningBody = "Всеведущий Бог знал, в каких кустах спрятался Адам. Вопрос «Где ты?» — это не вопрос географии, а призыв к совести и покаянию: «Что случилось с твоим сердцем? Выйди из тени страха и самооправдания навстречу Богу!»",
        quizPromptWithBlank = "«И воззвал Господь Бог к Адаму и сказал ему: [ ... ] ты?»",
        correctWord = "где",
        options = listOf("где", "кто", "зачем"),
        ttsText = "Бытие, глава третья, стих девятый. И воззвал Господь Бог к Адаму и сказал ему: где ты?",
    ),
    GenesisWisdomVerse(
        id = 8,
        stage = 5,
        ref = "Бытие 3:15 и 3:21",
        fullText = "«Семя жены поразит змия в голову... И сделал Господь Бог Адаму и жене его одежды кожаные и одел их.»",
        deepMeaningTitle = "Первое Евангелие (Протоевангелие) и Одежды Милости",
        deepMeaningBody = "Даже после падения Бог не оставил людей без надежды! В Бытии 3:15 звучит первое в Библии пророчество о Спасителе Иисусе Христе, Который победит власть греха и змия, а кожаные одежды стали прообразом искупительной жертвы и покрова Божьего прощения.",
        quizPromptWithBlank = "«И сделал Господь Бог Адаму и жене его [ ... ] кожаные и одел их»",
        correctWord = "одежды",
        options = listOf("одежды", "шатры", "скрижали"),
        ttsText = "Бытие, глава третья, стихи пятнадцатый и двадцать первый. Семя жены поразит змия в голову. И сделал Господь Бог Адаму и жене его одежды кожаные и одел их.",
    ),
)

/** Каменный уступ или цветущая терраса Эдемского сада, на которую можно запрыгивать. */
private data class EdenTerrace(
    val x: Float,
    val y: Float,
    val width: Float = 66f,
    val height: Float = 20f,
    val hasFountainSpring: Boolean = false,
)

/** Интерактивный объект в Эдемском саду (зависит от главы 1..5). */
private enum class EdenItemKind {
    GARDEN_TREE_TO_WATER, // Гл. 1: молодая яблоня/маслина, которую нужно полить живой водой
    ANIMAL_TO_NAME,       // Гл. 2: животное, которому Адам даёт имя
    FRUIT_OF_LIFE,        // Гл. 3: чистый золотой плод Дерева Жизни
    FORBIDDEN_FRUIT,      // Гл. 3: запретный плод (его нужно обходить или отражать Словом!)
    LIGHT_OF_REPENTANCE,  // Гл. 4: луч искреннего покаяния на зов Божий «Где ты?»
    PROMISE_LAMP,         // Гл. 5: светильник Обетования о Спасителе (Быт. 3:15, 3:21)
    SCRIPTURE_SCROLL,     // Золотой свиток Писания с глубоким смыслом и проверкой памяти
}

private data class EdenInteractiveObj(
    val id: Int,
    val x: Float,
    val y: Float,
    val kind: EdenItemKind,
    val title: String,
    val subtitle: String = "",
    val emoji: String = "🌿",
    val verseId: Int = 1,
    val completed: Boolean = false,
)

/** Облачко сомнения змея («Подлинно ли сказал Бог?..»), которое рассеивается Щитом Истины Писания. */
private data class DoubtWhisper(
    val id: Int,
    val x: Float,
    val minX: Float,
    val maxX: Float,
    val speed: Float,
    val dir: Int = -1,
    val label: String,
    val dispelled: Boolean = false,
)

private data class EdenFloatingNote(
    val x: Float,
    val y: Float,
    val text: String,
    val color: Color,
    val alpha: Float = 1f,
)

@Composable
fun KidsAdamEveGameScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val prefs = remember { context.getSharedPreferences("kids_adameve_prefs", Context.MODE_PRIVATE) }
    val speak = rememberKidsSpeaker(rate = 0.92f)

    // 5 смысловых глав по Бытию 1–3:
    // 1 = Образ Божий и хранение Эдемского сада (4 реки Эдема, полив деревьев)
    // 2 = Наречение имён животным и сотворение Евы (дар семьи)
    // 3 = Заповедь Божия и испытание у дерева познания (Щит Слова против сомнений змея)
    // 4 = Голос Божий в прохладе дня: «Адам, где ты?» (выход к свету покаяния вместо пряток)
    // 5 = Первое Евангелие (Быт. 3:15) и Одежды Милости (Быт. 3:21)
    var stage by remember { mutableIntStateOf(1) }
    var running by remember { mutableStateOf(true) }
    var stageVictory by remember { mutableStateOf(false) }
    var allChaptersComplete by remember { mutableStateOf(false) }

    var score by remember { mutableIntStateOf(0) }
    var hearts by remember { mutableIntStateOf(3) }
    var hurtCooldown by remember { mutableFloatStateOf(0f) }
    var wordShieldTimer by remember { mutableFloatStateOf(0f) } // Волна «Истина Слова Божьего»
    var winRound by remember { mutableIntStateOf(0) }
    var bannerText by remember { mutableStateOf<String?>("Глава 1: Возделывай и храни Эдемский сад, открывай свитки Писания!") }

    // Какие стихи Бытия 1–3 игрок уже выучил и проверил в тренажёре памяти
    val memorizedVerseIds = remember {
        val saved = prefs.getStringSet("memorized_verses", emptySet()) ?: emptySet()
        mutableStateListOf<Int>().apply {
            saved.mapNotNull { it.toIntOrNull() }.forEach { add(it) }
        }
    }

    // Активное окно изучения и запоминания стиха Писания (с пропущенным словом и глубиной смысла)
    var activeQuizVerse by remember { mutableStateOf<GenesisWisdomVerse?>(null) }
    var selectedQuizOption by remember { mutableStateOf<String?>(null) }
    var quizAnsweredCorrect by remember { mutableStateOf(false) }
    var showWisdomBookDialog by remember { mutableStateOf(false) }

    // Управление героями (Адам и Ева идут вместе; можно переключать ведущего героя Адам ⇄ Ева)
    var leadIsEve by remember { mutableStateOf(false) }
    var heroX by remember { mutableFloatStateOf(52f) }
    var heroY by remember { mutableFloatStateOf(0f) }
    var heroVy by remember { mutableFloatStateOf(0f) }
    var facingRight by remember { mutableStateOf(true) }
    var moveLeftHeld by remember { mutableStateOf(false) }
    var moveRightHeld by remember { mutableStateOf(false) }
    var airJumpUsed by remember { mutableStateOf(false) }
    var animTime by remember { mutableFloatStateOf(0f) }
    var cameraX by remember { mutableFloatStateOf(0f) }
    var worldViewportW by remember { mutableFloatStateOf(460f) }

    // Плавающие перетаскиваемые кнопки
    var dpadOffsetX by remember { mutableFloatStateOf(prefs.getFloat("dpad_ox", 0f)) }
    var dpadOffsetY by remember { mutableFloatStateOf(prefs.getFloat("dpad_oy", 0f)) }
    var jumpOffsetX by remember { mutableFloatStateOf(prefs.getFloat("jump_ox", 0f)) }
    var jumpOffsetY by remember { mutableFloatStateOf(prefs.getFloat("jump_oy", 0f)) }

    val terraces = remember { mutableStateListOf<EdenTerrace>() }
    val objects = remember { mutableStateListOf<EdenInteractiveObj>() }
    val whispers = remember { mutableStateListOf<DoubtWhisper>() }
    val notes = remember { mutableStateListOf<EdenFloatingNote>() }

    fun levelGoalX(st: Int): Float = when (st) {
        1 -> 1420f
        2 -> 1520f
        3 -> 1620f
        4 -> 1480f
        else -> 1560f
    }

    fun openVerseStudyModal(verseId: Int) {
        val v = GENESIS_WISDOM_VERSES.find { it.id == verseId } ?: GENESIS_WISDOM_VERSES.first()
        selectedQuizOption = null
        quizAnsweredCorrect = memorizedVerseIds.contains(v.id)
        activeQuizVerse = v
        speak(v.ttsText)
    }

    fun buildStage(targetStage: Int, resetScore: Boolean) {
        stage = targetStage.coerceIn(1, 5)
        heroX = 50f
        heroY = 0f
        heroVy = 0f
        facingRight = true
        moveLeftHeld = false
        moveRightHeld = false
        airJumpUsed = false
        cameraX = 0f
        hurtCooldown = 0f
        wordShieldTimer = 0f
        stageVictory = false
        allChaptersComplete = false
        running = true
        if (resetScore) {
            score = 0
            hearts = 3
        } else {
            hearts = max(hearts, 3)
        }

        terraces.clear()
        objects.clear()
        whispers.clear()
        notes.clear()

        when (stage) {
            1 -> {
                bannerText = "🌳 Глава 1 (Быт. 1:27, 2:15): Возделывай и храни Эдемский сад у 4 рек!"
                terraces.addAll(
                    listOf(
                        EdenTerrace(x = 220f, y = 52f, width = 76f),
                        EdenTerrace(x = 460f, y = 44f, width = 62f, hasFountainSpring = true),
                        EdenTerrace(x = 680f, y = 84f, width = 82f),
                        EdenTerrace(x = 960f, y = 56f, width = 74f),
                        EdenTerrace(x = 1180f, y = 82f, width = 78f),
                    ),
                )
                objects.addAll(
                    listOf(
                        EdenInteractiveObj(1, x = 160f, y = 0f, kind = EdenItemKind.GARDEN_TREE_TO_WATER, title = "Смоковница у реки Фисон", subtitle = "Полита живой водой!", emoji = "🌱"),
                        EdenInteractiveObj(2, x = 255f, y = 72f, kind = EdenItemKind.SCRIPTURE_SCROLL, title = "Бытие 1:27 — Образ Божий", emoji = "📜", verseId = 1),
                        EdenInteractiveObj(3, x = 410f, y = 0f, kind = EdenItemKind.GARDEN_TREE_TO_WATER, title = "Олива у реки Гихон", subtitle = "Растёт во славу Творца!", emoji = "🫒"),
                        EdenInteractiveObj(4, x = 720f, y = 104f, kind = EdenItemKind.SCRIPTURE_SCROLL, title = "Бытие 2:15 — Возделывать и хранить", emoji = "📜", verseId = 2),
                        EdenInteractiveObj(5, x = 860f, y = 0f, kind = EdenItemKind.GARDEN_TREE_TO_WATER, title = "Гранатовое дерево у Хиддекеля", subtitle = "Цветёт в Эдеме!", emoji = "🌺"),
                        EdenInteractiveObj(6, x = 1120f, y = 0f, kind = EdenItemKind.GARDEN_TREE_TO_WATER, title = "Виноградник у реки Евфрат", subtitle = "Приносит добрый плод!", emoji = "🍇"),
                    ),
                )
            }

            2 -> {
                bannerText = "🦁 Глава 2 (Быт. 2:18–24): Назови имена животным и узнай Божий замысел о семье!"
                terraces.addAll(
                    listOf(
                        EdenTerrace(x = 250f, y = 54f, width = 78f),
                        EdenTerrace(x = 540f, y = 50f, width = 66f, hasFountainSpring = true),
                        EdenTerrace(x = 760f, y = 88f, width = 84f),
                        EdenTerrace(x = 1060f, y = 58f, width = 78f),
                        EdenTerrace(x = 1280f, y = 86f, width = 80f),
                    ),
                )
                objects.addAll(
                    listOf(
                        EdenInteractiveObj(11, x = 175f, y = 0f, kind = EdenItemKind.ANIMAL_TO_NAME, title = "Кроткий Ягнёнок", subtitle = "Имя: Агнец (символ кротости и чистоты)", emoji = "🐑"),
                        EdenInteractiveObj(12, x = 285f, y = 74f, kind = EdenItemKind.SCRIPTURE_SCROLL, title = "Бытие 2:18 — Помощница и семья", emoji = "📜", verseId = 3),
                        EdenInteractiveObj(13, x = 445f, y = 0f, kind = EdenItemKind.ANIMAL_TO_NAME, title = "Белый Голубь", subtitle = "Имя: Голубь (символ мира и чистоты)", emoji = "🕊️"),
                        EdenInteractiveObj(14, x = 670f, y = 0f, kind = EdenItemKind.ANIMAL_TO_NAME, title = "Мирный Лев Эдема", subtitle = "Имя: Лев (символ благородства и силы)", emoji = "🦁"),
                        EdenInteractiveObj(15, x = 800f, y = 108f, kind = EdenItemKind.SCRIPTURE_SCROLL, title = "Бытие 2:19 — Имена всему творению", emoji = "📜", verseId = 4),
                        EdenInteractiveObj(16, x = 980f, y = 0f, kind = EdenItemKind.ANIMAL_TO_NAME, title = "Быстрый Олень", subtitle = "Имя: Елень (символ жажды живой воды)", emoji = "🦌"),
                        EdenInteractiveObj(17, x = 1320f, y = 106f, kind = EdenItemKind.ANIMAL_TO_NAME, title = "Небесный Орёл", subtitle = "Имя: Орёл (символ духовной высоты)", emoji = "🦅"),
                    ),
                )
            }

            3 -> {
                bannerText = "🛡️ Глава 3 (Быт. 2:16–17, 3:1): Отражай сомнения змея Словом Божьим и обходи запретный плод!"
                terraces.addAll(
                    listOf(
                        EdenTerrace(x = 240f, y = 56f, width = 76f),
                        EdenTerrace(x = 510f, y = 52f, width = 66f, hasFountainSpring = true),
                        EdenTerrace(x = 760f, y = 90f, width = 84f),
                        EdenTerrace(x = 1040f, y = 58f, width = 78f),
                        EdenTerrace(x = 1320f, y = 84f, width = 80f),
                    ),
                )
                objects.addAll(
                    listOf(
                        EdenInteractiveObj(21, x = 165f, y = 0f, kind = EdenItemKind.FRUIT_OF_LIFE, title = "Плод Дерева Жизни", subtitle = "Разрешённый Божий дар!", emoji = "🍎"),
                        EdenInteractiveObj(22, x = 275f, y = 76f, kind = EdenItemKind.SCRIPTURE_SCROLL, title = "Бытие 2:16–17 — Заповедь Божия", emoji = "📜", verseId = 5),
                        EdenInteractiveObj(23, x = 430f, y = 0f, kind = EdenItemKind.FORBIDDEN_FRUIT, title = "Запретный плод", subtitle = "Не прикасайся — перепрыгни!", emoji = "🐍"),
                        EdenInteractiveObj(24, x = 620f, y = 0f, kind = EdenItemKind.FRUIT_OF_LIFE, title = "Золотой плод Эдема", subtitle = "Благословенная пища!", emoji = "🍐"),
                        EdenInteractiveObj(25, x = 800f, y = 110f, kind = EdenItemKind.SCRIPTURE_SCROLL, title = "Бытие 3:1 — Как распознать ложь змея", emoji = "📜", verseId = 6),
                        EdenInteractiveObj(26, x = 940f, y = 0f, kind = EdenItemKind.FORBIDDEN_FRUIT, title = "Искушение у дерева", subtitle = "Обойди или отрази Словом!", emoji = "🐍"),
                        EdenInteractiveObj(27, x = 1180f, y = 0f, kind = EdenItemKind.FRUIT_OF_LIFE, title = "Плод радости и мира", subtitle = "Верность Божьему Слову!", emoji = "🍊"),
                    ),
                )
                whispers.addAll(
                    listOf(
                        DoubtWhisper(1, x = 370f, minX = 310f, maxX = 480f, speed = 58f, label = "«Подлинно ли сказал Бог?»"),
                        DoubtWhisper(2, x = 880f, minX = 780f, maxX = 1010f, speed = 68f, label = "«Нет, не умрёте...»"),
                        DoubtWhisper(3, x = 1260f, minX = 1140f, maxX = 1380f, speed = 72f, label = "«Будете как боги без Бога»"),
                    ),
                )
            }

            4 -> {
                bannerText = "🌅 Глава 4 (Быт. 3:8–13): На зов Божий «Адам, где ты?» выходи к Свету Покаяния, а не прячься!"
                terraces.addAll(
                    listOf(
                        EdenTerrace(x = 230f, y = 54f, width = 76f),
                        EdenTerrace(x = 490f, y = 50f, width = 66f, hasFountainSpring = true),
                        EdenTerrace(x = 730f, y = 88f, width = 84f),
                        EdenTerrace(x = 1010f, y = 60f, width = 78f),
                        EdenTerrace(x = 1250f, y = 84f, width = 78f),
                    ),
                )
                objects.addAll(
                    listOf(
                        EdenInteractiveObj(31, x = 180f, y = 0f, kind = EdenItemKind.LIGHT_OF_REPENTANCE, title = "Выход из тени страха", subtitle = "Господи, я слышу голос Твой!", emoji = "🕯️"),
                        EdenInteractiveObj(32, x = 265f, y = 74f, kind = EdenItemKind.SCRIPTURE_SCROLL, title = "Бытие 3:9 — «Адам, где ты?»", emoji = "📜", verseId = 7),
                        EdenInteractiveObj(33, x = 580f, y = 0f, kind = EdenItemKind.LIGHT_OF_REPENTANCE, title = "Отказ от перекладывания вины", subtitle = "Честное сердце перед Богом!", emoji = "🙏"),
                        EdenInteractiveObj(34, x = 770f, y = 108f, kind = EdenItemKind.LIGHT_OF_REPENTANCE, title = "Свет совести и правды", subtitle = "Бог ищет человека с любовью!", emoji = "✨"),
                        EdenInteractiveObj(35, x = 1120f, y = 0f, kind = EdenItemKind.LIGHT_OF_REPENTANCE, title = "Слеза искреннего покаяния", subtitle = "Покаяние открывает путь к прощению!", emoji = "💧"),
                    ),
                )
                whispers.addAll(
                    listOf(
                        DoubtWhisper(11, x = 410f, minX = 330f, maxX = 470f, speed = 60f, label = "«Спрячься в кустах!»"),
                        DoubtWhisper(12, x = 910f, minX = 820f, maxX = 995f, speed = 66f, label = "«Обвини другого!»"),
                    ),
                )
            }

            else -> {
                bannerText = "✝️ Глава 5 (Быт. 3:15, 3:21): Первое Евангелие о Спасителе и Одежды Божьей Милости!"
                terraces.addAll(
                    listOf(
                        EdenTerrace(x = 240f, y = 54f, width = 76f),
                        EdenTerrace(x = 510f, y = 52f, width = 66f, hasFountainSpring = true),
                        EdenTerrace(x = 760f, y = 90f, width = 84f),
                        EdenTerrace(x = 1050f, y = 60f, width = 80f),
                        EdenTerrace(x = 1310f, y = 88f, width = 82f),
                    ),
                )
                objects.addAll(
                    listOf(
                        EdenInteractiveObj(41, x = 185f, y = 0f, kind = EdenItemKind.PROMISE_LAMP, title = "Обетование Бытия 3:15", subtitle = "Грядущий Спаситель победит змия!", emoji = "🌟"),
                        EdenInteractiveObj(42, x = 275f, y = 74f, kind = EdenItemKind.SCRIPTURE_SCROLL, title = "Бытие 3:15 и 3:21 — Протоевангелие", emoji = "📜", verseId = 8),
                        EdenInteractiveObj(43, x = 620f, y = 0f, kind = EdenItemKind.PROMISE_LAMP, title = "Одежды кожаные (Быт. 3:21)", subtitle = "Покров Божьей милости и прощения!", emoji = "🧥"),
                        EdenInteractiveObj(44, x = 800f, y = 110f, kind = EdenItemKind.PROMISE_LAMP, title = "Надежда искупления", subtitle = "Христос вновь откроет Дерево Жизни!", emoji = "🕊️"),
                        EdenInteractiveObj(45, x = 1190f, y = 0f, kind = EdenItemKind.PROMISE_LAMP, title = "Свет веры на пути в мир", subtitle = "Божья любовь сильнее падения!", emoji = "🔥"),
                    ),
                )
                whispers.addAll(
                    listOf(
                        DoubtWhisper(21, x = 430f, minX = 350f, maxX = 490f, speed = 62f, label = "«Прощения нет...»"),
                        DoubtWhisper(22, x = 950f, minX = 860f, maxX = 1030f, speed = 68f, label = "«Бог забыл вас...»"),
                    ),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        buildStage(targetStage = 1, resetScore = true)
    }

    // Действие «📜 ИСТИНА / 🌿 БЛАГОСЛОВИТЬ»: рассеивает сомнения змея Словом Божьим и активирует объекты рядом
    fun triggerWisdomAction() {
        wordShieldTimer = 0.75f
        KidsGameAudio.play(KidsSfx.TAP)

        // Рассеиваем облачка сомнения змея вокруг Адама и Евы Словом Божьим!
        for (i in whispers.indices) {
            val w = whispers[i]
            if (!w.dispelled && abs(w.x - heroX) < 88f) {
                whispers[i] = w.copy(dispelled = true)
                score += 25
                notes.add(EdenFloatingNote(w.x, 48f, "📜 Словом Божьим побеждено! +25", Color(0xFFFEF08A)))
                bannerText = "✨ Истина Писания рассеяла ложь: ${w.label}"
                KidsGameAudio.play(KidsSfx.CORRECT)
            }
        }
    }

    fun triggerJump() {
        if (activeQuizVerse != null) return
        if (stageVictory) {
            if (allChaptersComplete) {
                buildStage(targetStage = 1, resetScore = true)
            } else {
                buildStage(targetStage = stage + 1, resetScore = false)
            }
            return
        }
        if (heroVy <= 5f && (!airJumpUsed || heroY <= 2f)) {
            if (heroY > 3f) {
                airJumpUsed = true
                heroVy = 490f
                notes.add(EdenFloatingNote(heroX, heroY + 14f, "🕊️", Color(0xFFFEF08A)))
            } else {
                airJumpUsed = false
                heroVy = 535f
            }
            KidsGameAudio.play(KidsSfx.TAP)
        } else if (!airJumpUsed) {
            airJumpUsed = true
            heroVy = 490f
            notes.add(EdenFloatingNote(heroX, heroY + 14f, "🕊️", Color(0xFFFEF08A)))
            KidsGameAudio.play(KidsSfx.TAP)
        }
    }

    // 60 FPS игровой цикл Эдемского сада (приостанавливается, когда открыто окно изучения стиха)
    LaunchedEffect(running, stageVictory, stage, activeQuizVerse, showWisdomBookDialog) {
        if (!running || stageVictory || activeQuizVerse != null || showWisdomBookDialog) return@LaunchedEffect
        var lastNanos = 0L
        while (running && !stageVictory && activeQuizVerse == null && !showWisdomBookDialog) {
            withFrameNanos { now ->
                if (lastNanos == 0L) {
                    lastNanos = now
                    return@withFrameNanos
                }
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNanos = now
                animTime += dt

                if (hurtCooldown > 0f) hurtCooldown = (hurtCooldown - dt).coerceAtLeast(0f)
                if (wordShieldTimer > 0f) wordShieldTimer = (wordShieldTimer - dt).coerceAtLeast(0f)

                val moveDir = when {
                    moveLeftHeld && !moveRightHeld -> -1
                    moveRightHeld && !moveLeftHeld -> 1
                    else -> 0
                }
                if (moveDir != 0) {
                    facingRight = moveDir > 0
                    val walkSpeed = if (wordShieldTimer > 0f) 205f else 178f
                    heroX = (heroX + moveDir * walkSpeed * dt).coerceIn(18f, levelGoalX(stage) + 100f)
                }

                // Вертикальная физика и приземление на террасы Эдема
                val prevY = heroY
                heroY += heroVy * dt
                heroVy -= 1300f * dt

                var supportY = 0f
                val halfW = 14f
                if (heroVy <= 0f) {
                    for (t in terraces) {
                        val topY = t.y + t.height
                        val inX = heroX + halfW > t.x + 2f && heroX - halfW < t.x + t.width - 2f
                        if (inX && prevY >= topY - 7f && heroY <= topY) {
                            supportY = max(supportY, topY)
                            if (t.hasFountainSpring) {
                                heroY = topY + 2f
                                heroVy = 630f
                                airJumpUsed = false
                                notes.add(EdenFloatingNote(t.x + 24f, topY + 18f, "💦 Источник Эдема!", Color(0xFF38BDF8)))
                                KidsGameAudio.play(KidsSfx.TAP)
                            }
                        }
                    }
                }
                if (heroVy <= 0f && heroY <= supportY) {
                    heroY = supportY
                    heroVy = 0f
                    airJumpUsed = false
                }

                // Взаимодействие с деревьями, животными, плодами и свитками Писания
                for (i in objects.indices) {
                    val obj = objects[i]
                    if (obj.completed) continue
                    val dx = abs(heroX - obj.x)
                    val dy = abs(heroY - obj.y)
                    if (dx < 30f && dy < 42f) {
                        when (obj.kind) {
                            EdenItemKind.FORBIDDEN_FRUIT -> {
                                if (wordShieldTimer > 0f) {
                                    objects[i] = obj.copy(completed = true)
                                    score += 20
                                    notes.add(EdenFloatingNote(obj.x, obj.y + 42f, "🛡️ Искушение отвергнуто!", Color(0xFF34D399)))
                                    KidsGameAudio.play(KidsSfx.CORRECT)
                                } else if (hurtCooldown <= 0f) {
                                    hurtCooldown = 1.5f
                                    heroVy = 300f
                                    bannerText = "⚠️ Помни заповедь Быт. 2:17: «От дерева познания добра и зла не ешь!»"
                                    notes.add(EdenFloatingNote(obj.x, obj.y + 42f, "Обойди запретный плод!", Color(0xFFF87171)))
                                    KidsGameAudio.play(KidsSfx.WRONG)
                                }
                            }

                            EdenItemKind.SCRIPTURE_SCROLL -> {
                                objects[i] = obj.copy(completed = true)
                                score += 35
                                KidsGameAudio.play(KidsSfx.WIN)
                                openVerseStudyModal(obj.verseId)
                            }

                            else -> {
                                objects[i] = obj.copy(completed = true)
                                score += 25
                                bannerText = "${obj.emoji} ${obj.title} — ${obj.subtitle}"
                                notes.add(EdenFloatingNote(obj.x, obj.y + 44f, "${obj.emoji} +25", Color(0xFFFEF08A)))
                                KidsGameAudio.play(KidsSfx.CORRECT)
                            }
                        }
                    }
                }

                // Движение облачков сомнения змея (в главах 3–5)
                for (i in whispers.indices) {
                    val w = whispers[i]
                    if (w.dispelled) continue
                    var nx = w.x + w.dir * w.speed * dt
                    var ndir = w.dir
                    if (nx <= w.minX) {
                        nx = w.minX
                        ndir = 1
                    } else if (nx >= w.maxX) {
                        nx = w.maxX
                        ndir = -1
                    }
                    whispers[i] = w.copy(x = nx, dir = ndir)

                    if (abs(heroX - nx) < 25f && heroY < 38f) {
                        val jumpedOnTop = (heroVy < -20f && prevY >= 24f) || wordShieldTimer > 0f
                        if (jumpedOnTop) {
                            whispers[i] = w.copy(dispelled = true)
                            heroVy = 410f
                            airJumpUsed = false
                            score += 25
                            notes.add(EdenFloatingNote(nx, 44f, "📜 Ложь побеждена Словом! +25", Color(0xFF34D399)))
                            KidsGameAudio.play(KidsSfx.CORRECT)
                        } else if (hurtCooldown <= 0f) {
                            hurtCooldown = 1.5f
                            heroVy = 300f
                            bannerText = "🛡️ Нажми «📜 ИСТИНА» или перепрыгни сомнение змея: ${w.label}"
                            KidsGameAudio.play(KidsSfx.WRONG)
                        }
                    }
                }

                // Всплывающие подписи
                val iter = notes.listIterator()
                while (iter.hasNext()) {
                    val n = iter.next()
                    val na = n.alpha - dt * 0.9f
                    if (na <= 0f) iter.remove() else iter.set(n.copy(y = n.y + dt * 26f, alpha = na))
                }

                // Достижение конца главы
                val goalX = levelGoalX(stage)
                if (heroX >= goalX) {
                    score += 80
                    winRound++
                    stageVictory = true
                    allChaptersComplete = (stage >= 5)
                    KidsGameAudio.play(KidsSfx.WIN)
                }

                val targetCam = (heroX - worldViewportW * 0.30f).coerceIn(0f, max(0f, goalX + 120f - worldViewportW))
                cameraX += (targetCam - cameraX) * (dt * 8f).coerceIn(0f, 1f)
            }
        }
    }

    KidsGameMusic(KidsMusicTrack.GAMES)
    KidsGameWinReward(
        game = KidsGames.ADAM_EVE,
        won = winRound > 0,
        points = 25,
        round = winRound,
    )

    val goodObjectsTotal = objects.count { it.kind != EdenItemKind.FORBIDDEN_FRUIT }
    val goodObjectsDone = objects.count { it.kind != EdenItemKind.FORBIDDEN_FRUIT && it.completed }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Полноэкранный холст Эдемского сада с единым масштабом `u`
            Canvas(modifier = Modifier.fillMaxSize()) {
                val isLandCanvas = size.width > size.height
                val u = if (isLandCanvas) {
                    (size.height / 290f).coerceAtLeast(1f)
                } else {
                    (size.width / 360f).coerceAtLeast(1f)
                }
                worldViewportW = size.width / u

                val groundRatio = if (isLandCanvas) 0.80f else 0.72f
                val groundY = size.height * groundRatio

                // Небо Эдемского сада в зависимости от духовной атмосферы главы
                val skyColors = when (stage) {
                    1 -> listOf(Color(0xFF38BDF8), Color(0xFFBAE6FD), Color(0xFFFEF9C3))
                    2 -> listOf(Color(0xFF0284C7), Color(0xFFA7F3D0), Color(0xFFFEF3C7))
                    3 -> listOf(Color(0xFF0369A1), Color(0xFFFDE68A), Color(0xFFBBF7D0))
                    4 -> listOf(Color(0xFF312E81), Color(0xFF6366F1), Color(0xFFFDE68A)) // вечерняя прохлада дня (Быт. 3:8)
                    else -> listOf(Color(0xFF1E3A8A), Color(0xFFF59E0B), Color(0xFFFEF08A)) // рассвет Обетования о Спасителе
                }
                drawRect(brush = Brush.verticalGradient(skyColors), size = size)

                // Солнечные лучи Божьего благословения в небе Эдема
                val sunCenter = Offset(size.width * 0.78f, groundY - 155f * u)
                drawCircle(
                    color = Color(0xFFFEF08A).copy(alpha = 0.38f),
                    radius = 44f * u,
                    center = sunCenter,
                )
                drawCircle(
                    color = Color(0xFFFACC15).copy(alpha = 0.85f),
                    radius = 24f * u,
                    center = sunCenter,
                )

                // Зелёные холмы Эдемского сада и 4 водопада рек (Фисон, Гихон, Хиддекель, Евфрат)
                val parallax = cameraX * 0.30f
                for (hIdx in 0..6) {
                    val hx = (hIdx * 260f - parallax % 260f) * u
                    val hillW = 240f * u
                    val hillH = (if (hIdx % 2 == 0) 82f else 64f) * u
                    drawRoundRect(
                        color = if (stage == 4) Color(0xFF065F46).copy(alpha = 0.45f) else Color(0xFF16A34A).copy(alpha = 0.32f),
                        topLeft = Offset(hx - hillW * 0.5f, groundY - hillH),
                        size = Size(hillW, hillH * 1.4f),
                        cornerRadius = CornerRadius(hillW * 0.5f, hillH),
                    )
                }

                // Цветущие террасы Эдема и источники живой воды
                for (t in terraces) {
                    val tx = (t.x - cameraX) * u
                    if (tx > -100f * u && tx < size.width + 80f * u) {
                        val ty = groundY - (t.y + t.height) * u
                        drawEdenTerrace(tx, ty, t.width * u, t.height * u, u, t.hasFountainSpring, animTime)
                    }
                }

                // Деревья, животные, свитки Писания, лучи покаяния и светильники Обетования
                for (obj in objects) {
                    val ox = (obj.x - cameraX) * u
                    if (ox > -80f * u && ox < size.width + 80f * u) {
                        val oy = groundY - obj.y * u
                        drawEdenInteractiveObject(ox, oy, u, obj, animTime)
                    }
                }

                // Облачка сомнения змея (в главах 3–5)
                for (w in whispers) {
                    if (!w.dispelled) {
                        val wx = (w.x - cameraX) * u
                        if (wx > -80f * u && wx < size.width + 80f * u) {
                            drawDoubtWhisper(wx, groundY, u, animTime)
                        }
                    }
                }

                // Финал главы: Дерево Жизни у рек Эдема и Врата Завета
                val goalScreenX = (levelGoalX(stage) - cameraX) * u
                if (goalScreenX > -180f * u && goalScreenX < size.width + 200f * u) {
                    drawTreeOfLifeGoal(goalScreenX, groundY, u, stage, animTime)
                }

                // Адам и Ева идут вместе по Эдемскому саду!
                val heroScreenX = (heroX - cameraX) * u
                val heroScreenBottomY = groundY - heroY * u
                val companionOffset = (if (facingRight) -24f else 24f) * u
                val companionHop = abs(sin(animTime * 6.5f)) * 4f * u

                // Спутник (Ева или Адам) идёт рядом
                drawEdenPerson(
                    x = heroScreenX + companionOffset,
                    bottomY = groundY - (heroY * 0.65f) * u - companionHop,
                    u = u,
                    isEve = !leadIsEve,
                    facingRight = facingRight,
                    clothedInGrace = (stage == 5),
                    wordShieldActive = false,
                    animTime = animTime,
                )

                // Ведущий герой (Адам или Ева)
                drawEdenPerson(
                    x = heroScreenX,
                    bottomY = heroScreenBottomY,
                    u = u,
                    isEve = leadIsEve,
                    facingRight = facingRight,
                    clothedInGrace = (stage == 5),
                    wordShieldActive = wordShieldTimer > 0f,
                    animTime = animTime,
                )

                // Всплывающие искры благословения
                for (n in notes) {
                    val nx = (n.x - cameraX) * u
                    val ny = groundY - n.y * u
                    drawCircle(
                        color = n.color.copy(alpha = (n.alpha * 0.85f).coerceIn(0f, 1f)),
                        radius = 7f * u,
                        center = Offset(nx, ny),
                    )
                }

                // Изумрудная трава Эдема и голубая река внизу
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF16A34A), Color(0xFF15803D), Color(0xFF0284C7)),
                        startY = groundY,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, groundY),
                    size = Size(size.width, size.height - groundY),
                )
                drawLine(
                    color = Color(0xFF22C55E),
                    start = Offset(0f, groundY),
                    end = Offset(size.width, groundY),
                    strokeWidth = 3.5f * u,
                )
            }

            // Баннер подсказки
            if (activeQuizVerse == null && !showWisdomBookDialog) {
                bannerText?.let { msg ->
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(3800)
                        if (bannerText == msg) bannerText = null
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .safeDrawingPadding()
                            .padding(top = if (isLandscape) 52.dp else 68.dp, start = 14.dp, end = 14.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF064E3B).copy(alpha = 0.92f))
                            .border(1.5.dp, Color(0xFFFDE047), RoundedCornerShape(999.dp))
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = msg,
                            color = Color(0xFFFEF9C3),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Верхняя панель: Назад, Переключатель 5 глав, Кнопка «📖 СТИХИ И СМЫСЛ», Счётчик выученных стихов
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.76f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    for (ch in 1..5) {
                        val active = (stage == ch)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (active) Color(0xFF059669) else Color.White.copy(alpha = 0.12f))
                                .clickable { buildStage(targetStage = ch, resetScore = false) }
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "$ch",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                // Кнопка открытия Сокровищницы Стихов и Глубины Смысла + прогресс главы
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF065F46).copy(alpha = 0.90f))
                        .border(1.dp, Color(0xFFFDE047).copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                        .clickable { showWisdomBookDialog = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "Стихи и смысл",
                        tint = Color(0xFFFDE047),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Стихи: ${memorizedVerseIds.size}/${GENESIS_WISDOM_VERSES.size}",
                        color = Color(0xFFFEF08A),
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = "🌿 $goodObjectsDone/$goodObjectsTotal",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = "⭐ $score",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }

                // Переключение Адам ⇄ Ева, Сброс и Звук
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.72f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable { leadIsEve = !leadIsEve }
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = if (leadIsEve) "Ева" else "Адам",
                            color = Color(0xFFFEF08A),
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable { buildStage(targetStage = stage, resetScore = false) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Сначала",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    KidsAudioToggles()
                }
            }

            // Полоска прогресса главы
            val progressFrac = (heroX / levelGoalX(stage)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 46.dp, start = 28.dp, end = 28.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.25f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFrac)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF34D399), Color(0xFFFDE047)),
                            ),
                        ),
                )
            }

            // === ИНТЕРАКТИВНОЕ ОКНО ИЗУЧЕНИЯ СТИХА И ГЛУБИНЫ СМЫСЛА (ПРИ ОТКРЫТИИ СВИТКА 📜) ===
            activeQuizVerse?.let { v ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFF0F172A))
                            .border(2.dp, Color(0xFFFDE047), RoundedCornerShape(26.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "📜 ${v.ref} · ${v.deepMeaningTitle}",
                                color = Color(0xFFFDE047),
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF059669))
                                        .clickable { speak(v.ttsText) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Прослушать стих",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .clickable { activeQuizVerse = null },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Закрыть",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        }

                        // Стих Писания
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF064E3B))
                                .padding(12.dp),
                        ) {
                            Text(
                                text = v.fullText,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                            )
                        }

                        // Глубина духовного смысла
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1E293B))
                                .padding(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "💡 Глубина смысла:",
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    text = v.deepMeaningBody,
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                )
                            }
                        }

                        // Интерактивное задание: вставь пропущенное слово Писания, чтобы запомнить стих!
                        Text(
                            text = "🧠 Проверь память сердца — какое слово пропущено?",
                            color = Color(0xFFFEF08A),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = v.quizPromptWithBlank,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (opt in v.options) {
                                val isSelected = (selectedQuizOption == opt)
                                val isRight = (opt == v.correctWord)
                                val bg = when {
                                    quizAnsweredCorrect && isRight -> Color(0xFF059669)
                                    isSelected && !isRight -> Color(0xFFDC2626)
                                    else -> Color(0xFF334155)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(bg)
                                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                        .clickable {
                                            selectedQuizOption = opt
                                            if (isRight) {
                                                quizAnsweredCorrect = true
                                                if (!memorizedVerseIds.contains(v.id)) {
                                                    memorizedVerseIds.add(v.id)
                                                    score += 50
                                                    prefs.edit()
                                                        .putStringSet(
                                                            "memorized_verses",
                                                            memorizedVerseIds.map { it.toString() }.toSet(),
                                                        )
                                                        .apply()
                                                }
                                                KidsGameAudio.play(KidsSfx.WIN)
                                            } else {
                                                KidsGameAudio.play(KidsSfx.WRONG)
                                            }
                                        }
                                        .padding(vertical = 10.dp, horizontal = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = opt,
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }

                        if (quizAnsweredCorrect) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "🌟 Стих сохранён в сердце! (+50 очков)",
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0xFF059669))
                                        .clickable { activeQuizVerse = null }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = "Продолжить путь ▶",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // === ОКНО «📖 СОКРОВИЩНИЦА СТИХОВ И СМЫСЛОВ БЫТИЯ 1–3» ===
            if (showWisdomBookDialog) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.68f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFF0F172A))
                            .border(2.dp, Color(0xFFFDE047), RoundedCornerShape(26.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "📖 Ключевые стихи и смысл Бытия 1–3 (${memorizedVerseIds.size}/${GENESIS_WISDOM_VERSES.size})",
                                color = Color(0xFFFDE047),
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f))
                                    .clickable { showWisdomBookDialog = false },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                            }
                        }

                        for (v in GENESIS_WISDOM_VERSES) {
                            val learned = memorizedVerseIds.contains(v.id)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (learned) Color(0xFF064E3B) else Color(0xFF1E293B))
                                    .clickable {
                                        showWisdomBookDialog = false
                                        openVerseStudyModal(v.id)
                                    }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "${if (learned) "⭐ Выучено · " else "📜 "}${v.ref} — ${v.deepMeaningTitle}",
                                        color = Color(0xFFFEF08A),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                    )
                                    Text(
                                        text = "Гл.${v.stage}",
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                    )
                                }
                                Text(
                                    text = v.fullText,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                )
                                Text(
                                    text = v.deepMeaningBody,
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                )
                            }
                        }
                    }
                }
            }

            // Окно завершения главы
            if (stageVictory && activeQuizVerse == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.94f))
                        .border(2.dp, Color(0xFFFDE047), RoundedCornerShape(26.dp))
                        .clickable {
                            if (allChaptersComplete) {
                                buildStage(targetStage = 1, resetScore = true)
                            } else {
                                buildStage(targetStage = stage + 1, resetScore = false)
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val title = when (stage) {
                            1 -> "🌳 Глава 1 пройдена: Сад возделан!"
                            2 -> "🦁 Глава 2 пройдена: Имена даны, семья создана!"
                            3 -> "🛡️ Глава 3 пройдена: Верность Слову Божьему!"
                            4 -> "🌅 Глава 4 пройдена: Сердце открыто перед Богом!"
                            else -> "✝️🌟 ВСЕ 5 ГЛАВ БЫТИЯ 1–3 ПРОЙДЕНЫ!"
                        }
                        val sub = when (stage) {
                            1 -> "Ты узнал о сотворении человека по образу Божию (Быт. 1:27) и призвании хранить сад (Быт. 2:15)."
                            2 -> "Ты узнал о Божьем замысле о семье (Быт. 2:18) и заботе обо всём творении (Быт. 2:19)."
                            3 -> "Ты увидел, как важно хранить заповедь Божию (Быт. 2:16–17) и распознавать уловки лукавого (Быт. 3:1)."
                            4 -> "На любящий зов Божий «Адам, где ты?» (Быт. 3:9) мы отвечаем искренним покаянием!"
                            else -> "Даже после падения Бог дал Первое Евангелие о Спасителе Христе (Быт. 3:15) и одел людей в одежды милости (Быт. 3:21)!"
                        }
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = sub,
                            color = Color(0xFFFEF08A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF059669))
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = if (allChaptersComplete) "Пройти с 1-й главы" else "Следующая глава ▶",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            // Плавающий левый пульт движения: ◀ ВЛЕВО | ВПРАВО ▶ + ручка перетаскивания ✥
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .safeDrawingPadding()
                    .padding(start = 12.dp, bottom = if (isLandscape) 12.dp else 20.dp)
                    .offset { IntOffset(dpadOffsetX.roundToInt(), dpadOffsetY.roundToInt()) }
                    .shadow(8.dp, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF1E293B).copy(alpha = 0.88f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(999.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    prefs.edit()
                                        .putFloat("dpad_ox", dpadOffsetX)
                                        .putFloat("dpad_oy", dpadOffsetY)
                                        .apply()
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                dpadOffsetX = (dpadOffsetX + dragAmount.x).coerceIn(-40f, 500f)
                                dpadOffsetY = (dpadOffsetY + dragAmount.y).coerceIn(-500f, 60f)
                            }
                        }
                        .padding(start = 10.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.OpenWith,
                        contentDescription = "Переместить кнопки хода",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(16.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (moveLeftHeld) Color(0xFF059669) else Color.Transparent)
                        .pointerInput(stage, running) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                moveLeftHeld = true
                                waitForUpOrCancellation()
                                moveLeftHeld = false
                            }
                        }
                        .padding(horizontal = 15.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Влево",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(26.dp)
                        .background(Color.White.copy(alpha = 0.25f)),
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (moveRightHeld) Color(0xFF059669) else Color.Transparent)
                        .pointerInput(stage, running) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                moveRightHeld = true
                                waitForUpOrCancellation()
                                moveRightHeld = false
                            }
                        }
                        .padding(horizontal = 15.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Вправо",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            // Плавающий правый блок: 📜 ИСТИНА СЛОВА + ▲ ПРЫЖОК + ручка ✥
            val canDoubleJump = heroY > 3f && !airJumpUsed
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(end = 12.dp, bottom = if (isLandscape) 12.dp else 20.dp)
                    .offset { IntOffset(jumpOffsetX.roundToInt(), jumpOffsetY.roundToInt()) }
                    .shadow(8.dp, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.50f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Кнопка «📜 ИСТИНА» (отражает сомнения змея Словом Божьим и благословляет сад)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFD97706))
                        .pointerInput(stage, running) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                triggerWisdomAction()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "📜 ИСТИНА",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                // Кнопка ▲ ПРЫЖОК / ПРЫЖОК ×2
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (canDoubleJump) Color(0xFF0284C7) else Color(0xFF059669))
                        .pointerInput(stage, running, stageVictory) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                triggerJump()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = if (canDoubleJump) "ПРЫЖОК ×2" else "ПРЫЖОК",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }

                Box(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    prefs.edit()
                                        .putFloat("jump_ox", jumpOffsetX)
                                        .putFloat("jump_oy", jumpOffsetY)
                                        .apply()
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                jumpOffsetX = (jumpOffsetX + dragAmount.x).coerceIn(-500f, 40f)
                                jumpOffsetY = (jumpOffsetY + dragAmount.y).coerceIn(-500f, 60f)
                            }
                        }
                        .padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.OpenWith,
                        contentDescription = "Переместить кнопку прыжка",
                        tint = Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** Отрисовка цветущей террасы Эдема и источника живой воды. */
private fun DrawScope.drawEdenTerrace(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    u: Float,
    hasSpring: Boolean,
    animTime: Float,
) {
    drawRoundRect(
        color = Color(0xFF78350F),
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(6f * u, 6f * u),
    )
    drawRoundRect(
        color = Color(0xFF22C55E),
        topLeft = Offset(x - 2f * u, y),
        size = Size(w + 4f * u, 7f * u),
        cornerRadius = CornerRadius(4f * u, 4f * u),
    )
    if (hasSpring) {
        val jet = (7f + abs(sin(animTime * 5f)) * 9f) * u
        drawRoundRect(
            color = Color(0xFF38BDF8),
            topLeft = Offset(x + w * 0.35f, y - jet),
            size = Size(w * 0.30f, jet),
            cornerRadius = CornerRadius(4f * u, 4f * u),
        )
    }
}

/** Отрисовка деревьев, животных, свитков Писания и светильников Обетования в Эдеме. */
private fun DrawScope.drawEdenInteractiveObject(
    x: Float,
    bottomY: Float,
    u: Float,
    obj: EdenInteractiveObj,
    animTime: Float,
) {
    val pulse = sin(animTime * 5f + obj.id) * 3f * u
    when (obj.kind) {
        EdenItemKind.GARDEN_TREE_TO_WATER -> {
            drawRoundRect(
                color = Color(0xFF78350F),
                topLeft = Offset(x - 4f * u, bottomY - 34f * u),
                size = Size(8f * u, 34f * u),
                cornerRadius = CornerRadius(3f * u, 3f * u),
            )
            val crownColor = if (obj.completed) Color(0xFF16A34A) else Color(0xFF65A30D)
            drawCircle(crownColor, (if (obj.completed) 20f else 15f) * u, Offset(x, bottomY - 40f * u))
            if (obj.completed) {
                drawCircle(Color(0xFFFACC15), 4.5f * u, Offset(x - 6f * u, bottomY - 42f * u))
                drawCircle(Color(0xFFFACC15), 4.5f * u, Offset(x + 7f * u, bottomY - 38f * u))
            }
        }

        EdenItemKind.ANIMAL_TO_NAME -> {
            val bodyColor = if (obj.completed) Color(0xFFFDE047) else Color(0xFFF5F5F4)
            drawRoundRect(
                color = bodyColor,
                topLeft = Offset(x - 12f * u, bottomY - 22f * u),
                size = Size(24f * u, 16f * u),
                cornerRadius = CornerRadius(6f * u, 6f * u),
            )
            drawCircle(bodyColor, 8f * u, Offset(x + 10f * u, bottomY - 24f * u))
            if (obj.completed) {
                drawCircle(
                    color = Color(0xFF34D399).copy(alpha = 0.55f),
                    radius = 18f * u + pulse,
                    center = Offset(x, bottomY - 18f * u),
                )
            }
        }

        EdenItemKind.SCRIPTURE_SCROLL -> {
            if (!obj.completed) {
                drawCircle(
                    color = Color(0xFFFDE047).copy(alpha = 0.42f),
                    radius = 18f * u + pulse,
                    center = Offset(x, bottomY - 16f * u),
                )
                drawRoundRect(
                    color = Color(0xFFF59E0B),
                    topLeft = Offset(x - 11f * u, bottomY - 26f * u),
                    size = Size(22f * u, 20f * u),
                    cornerRadius = CornerRadius(4f * u, 4f * u),
                )
            }
        }

        EdenItemKind.FORBIDDEN_FRUIT -> {
            if (!obj.completed) {
                drawCircle(
                    color = Color(0xFFDC2626).copy(alpha = 0.35f),
                    radius = 16f * u + pulse,
                    center = Offset(x, bottomY - 16f * u),
                )
                drawCircle(
                    color = Color(0xFF991B1B),
                    radius = 10f * u,
                    center = Offset(x, bottomY - 16f * u),
                )
            }
        }

        else -> {
            if (!obj.completed) {
                drawCircle(
                    color = Color(0xFF34D399).copy(alpha = 0.40f),
                    radius = 16f * u + pulse,
                    center = Offset(x, bottomY - 18f * u),
                )
                drawCircle(
                    color = Color(0xFFFEF08A),
                    radius = 9f * u,
                    center = Offset(x, bottomY - 18f * u),
                )
            }
        }
    }
}

/** Облачко хитрого сомнения змея. */
private fun DrawScope.drawDoubtWhisper(
    x: Float,
    groundY: Float,
    u: Float,
    animTime: Float,
) {
    val slither = sin(animTime * 8f) * 3f * u
    drawRoundRect(
        color = Color(0xFF4C1D95).copy(alpha = 0.85f),
        topLeft = Offset(x - 14f * u + slither, groundY - 26f * u),
        size = Size(28f * u, 20f * u),
        cornerRadius = CornerRadius(9f * u, 9f * u),
    )
    drawCircle(Color(0xFFFACC15), 2.5f * u, Offset(x - 5f * u + slither, groundY - 18f * u))
    drawCircle(Color(0xFFFACC15), 2.5f * u, Offset(x + 5f * u + slither, groundY - 18f * u))
}

/** Отрисовка Адама и Евы (в 5-й главе — в одеждах Божьей милости по Быт. 3:21). */
private fun DrawScope.drawEdenPerson(
    x: Float,
    bottomY: Float,
    u: Float,
    isEve: Boolean,
    facingRight: Boolean,
    clothedInGrace: Boolean,
    wordShieldActive: Boolean,
    animTime: Float,
) {
    val dir = if (facingRight) 1f else -1f
    val stride = sin(animTime * 10f) * 4f * u

    if (wordShieldActive) {
        drawCircle(
            color = Color(0xFFFEF08A).copy(alpha = 0.45f),
            radius = 30f * u,
            center = Offset(x, bottomY - 22f * u),
        )
        drawCircle(
            color = Color(0xFFF59E0B),
            radius = 30f * u,
            center = Offset(x, bottomY - 22f * u),
            style = Stroke(width = 2.2f * u),
        )
    }

    // Ноги
    drawRoundRect(
        color = Color(0xFFD97706),
        topLeft = Offset(x - 5f * u + stride, bottomY - 9f * u),
        size = Size(4.5f * u, 9f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )
    drawRoundRect(
        color = Color(0xFFD97706),
        topLeft = Offset(x + 1f * u - stride, bottomY - 9f * u),
        size = Size(4.5f * u, 9f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )

    // Туника (в главах 1–4 — светло-изумрудная туника Эдема, в главе 5 — тёплые кожаные одежды милости Быт. 3:21)
    val tunicColor = when {
        clothedInGrace -> Color(0xFFB45309)
        isEve -> Color(0xFF10B981)
        else -> Color(0xFF059669)
    }
    drawRoundRect(
        color = tunicColor,
        topLeft = Offset(x - 9f * u, bottomY - 30f * u),
        size = Size(18f * u, 22f * u),
        cornerRadius = CornerRadius(5f * u, 5f * u),
    )

    // Голова и волосы
    drawCircle(
        color = Color(0xFFFDE68A),
        radius = 8f * u,
        center = Offset(x, bottomY - 36f * u),
    )
    drawRoundRect(
        color = if (isEve) Color(0xFF92400E) else Color(0xFF78350F),
        topLeft = Offset(x - 8f * u, bottomY - 44f * u),
        size = Size(16f * u, if (isEve) 14f * u else 7f * u),
        cornerRadius = CornerRadius(5f * u, 5f * u),
    )
    drawCircle(
        color = Color(0xFF1C1917),
        radius = 1.5f * u,
        center = Offset(x + dir * 3f * u, bottomY - 36.5f * u),
    )
}

/** Дерево Жизни и Врата Обетования на финише главы. */
private fun DrawScope.drawTreeOfLifeGoal(
    x: Float,
    groundY: Float,
    u: Float,
    stage: Int,
    animTime: Float,
) {
    val glow = (46f + sin(animTime * 4f) * 5f) * u
    drawCircle(
        color = Color(0xFFFEF08A).copy(alpha = 0.35f),
        radius = glow,
        center = Offset(x, groundY - 68f * u),
    )
    drawRoundRect(
        color = Color(0xFF78350F),
        topLeft = Offset(x - 10f * u, groundY - 68f * u),
        size = Size(20f * u, 68f * u),
        cornerRadius = CornerRadius(5f * u, 5f * u),
    )
    drawCircle(Color(0xFF15803D), 36f * u, Offset(x, groundY - 82f * u))
    drawCircle(Color(0xFF22C55E), 26f * u, Offset(x - 16f * u, groundY - 76f * u))
    drawCircle(Color(0xFF16A34A), 26f * u, Offset(x + 16f * u, groundY - 76f * u))
    // 12 золотых плодов Дерева Жизни
    for (i in -2..2) {
        drawCircle(
            color = if (stage == 5) Color(0xFFFEF08A) else Color(0xFFFACC15),
            radius = 5f * u,
            center = Offset(x + i * 11f * u, groundY - (78f + (i % 2) * 10f) * u),
        )
    }
}
