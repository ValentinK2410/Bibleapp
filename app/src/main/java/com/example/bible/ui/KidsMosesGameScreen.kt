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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Каменный колодец вместо трубы Марио. На колодцы можно запрыгивать, а в особые — спускаться в Оазис! */
private data class DesertWell(
    val x: Float,
    val width: Float = 46f,
    val height: Float = 48f,
    val hasSpringWater: Boolean = false,
    val isSecretOasis: Boolean = false,
    val oasisVisited: Boolean = false,
)

private enum class MosesBlockKind {
    BRICK,
    QUESTION_VERSE,
    QUESTION_HEART,
    QUESTION_STAFF_GLOW,
    SINAI_STEP,
}

/** Висящие в воздухе блоки как в Марио (кирпичи, ступени Синая и золотые блоки с вопросом). */
private data class OverheadBlock(
    val id: Int,
    val x: Float,
    val y: Float, // высота нижней грани блока над землёй
    val width: Float = 32f,
    val height: Float = 28f,
    val kind: MosesBlockKind,
    val verseIndex: Int = 0,
    val used: Boolean = false,
    val bumpAnim: Float = 0f,
)

/** Скала в Мериве (Исход 17:6): при ударе посохом из неё бьёт вода и поднимает деревянный плот-мост! */
private data class MeribahRockPuzzle(
    val id: Int,
    val rockX: Float,
    val raftX: Float,
    val raftWidth: Float = 68f,
    val targetHeight: Float = 86f,
    val activated: Boolean = false,
    val currentRaftY: Float = 8f,
)

/** Предмет в подземном секретном оазисе внутри колодца. */
private data class OasisTreasure(
    val id: Int,
    val x: Float,
    val y: Float,
    val isPsalmScroll: Boolean = false,
    val collected: Boolean = false,
)

/** Воин фараона (вместо грибов/черепах Марио) — патрулирует между точками minX..maxX. */
private data class PharaohSoldier(
    val id: Int,
    val x: Float,
    val minX: Float,
    val maxX: Float,
    val speed: Float,
    val dir: Int = -1,
    val eliteChariot: Boolean = false,
    val defeated: Boolean = false,
    val defeatedY: Float = 0f,
)

/** Дом / шатёр семьи народа Божьего, которую Моисей собирает и выводит из Египта. */
private data class HebrewFamilySpot(
    val id: Int,
    val x: Float,
    val rescued: Boolean = false,
)

/** Всплывающий бонус из блока `?` или над спасённой семьёй. */
private data class FloatingPopup(
    val x: Float,
    val y: Float,
    val label: String,
    val color: Color,
    val alpha: Float = 1f,
)

/** Детские библейские стихи из книги Исход для золотых блоков со свитками (`?`). */
private data class ExodusVerseCard(
    val ref: String,
    val text: String,
    valttsText: String,
)

private val EXODUS_VERSES = listOf(
    ExodusVerseCard(
        ref = "Исход 3:12",
        text = "«Бог сказал Моисею: Я буду с тобою!»",
        valttsText = "Бог сказал Моисею: Я буду с тобою! Книга Исход, глава третья, стих двенадцатый.",
    ),
    ExodusVerseCard(
        ref = "Исход 13:21",
        text = "«Господь шёл пред ними днём в столпе облачном, а ночью в столпе огненном, светя им»",
        valttsText = "Господь шёл пред ними днём в столпе облачном, а ночью в столпе огненном, светя им.",
    ),
    ExodusVerseCard(
        ref = "Исход 14:14",
        text = "«Господь будет поборать за вас, а вы будьте спокойны!»",
        valttsText = "Господь будет поборать за вас, а вы будьте спокойны! Исход, глава четырнадцатая.",
    ),
    ExodusVerseCard(
        ref = "Исход 15:2",
        text = "«Господь — крепость моя и слава моя, Он был мне спасением!»",
        valttsText = "Господь — крепость моя и слава моя, Он был мне спасением!",
    ),
    ExodusVerseCard(
        ref = "Исход 17:6",
        text = "«Ударь в скалу посохом, и пойдёт из неё вода, и будет пить народ!»",
        valttsText = "Ударь в скалу посохом, и пойдёт из неё вода, и будет пить народ!",
    ),
    ExodusVerseCard(
        ref = "Исход 20:12",
        text = "«Почитай отца твоего и мать твою, чтобы продлились дни твои на земле»",
        valttsText = "Заповедь Божия: Почитай отца твоего и мать твою, чтобы продлились дни твои на земле.",
    ),
)

@Composable
fun KidsMosesGameScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val prefs = remember { context.getSharedPreferences("kids_moses_prefs", Context.MODE_PRIVATE) }
    val speak = rememberKidsSpeaker(rate = 0.92f)

    // 4 главы приключения Моисея:
    // 1 = Путь в Египет (Неопалимая купина, колодцы Мадиама -> Врата Египта)
    // 2 = Сбор народа Божьего в Египте (среди пирамид, секретных колодцев и стражи фараона)
    // 3 = Исход через Чермное море (Огненный столп и расступившееся море)
    // 4 = Гора Синай и Скрижали Завета (Скалы воды Меривы и ступени к вершине Синая)
    var stage by remember { mutableIntStateOf(1) }
    var running by remember { mutableStateOf(true) }
    var gameOver by remember { mutableStateOf(false) }
    var stageVictory by remember { mutableStateOf(false) }
    var fullExodusComplete by remember { mutableStateOf(false) }

    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(prefs.getInt("best_score", 0)) }
    var hearts by remember { mutableIntStateOf(3) }
    var hurtCooldown by remember { mutableFloatStateOf(0f) }
    var staffPowerTimer by remember { mutableFloatStateOf(0f) } // Сияние Посоха Божьего
    var staffWaveTimer by remember { mutableFloatStateOf(0f) }  // Взмах посохом (активация скалы / отпор страже)
    var staffWaveX by remember { mutableFloatStateOf(0f) }
    var winRound by remember { mutableIntStateOf(0) }
    var bannerText by remember { mutableStateOf<String?>("Глава 1: Иди к колодцам и дойди до врат Египта!") }
    var activeVerseCard by remember { mutableStateOf<ExodusVerseCard?>(null) }

    // Координаты Моисея на поверхности
    var mosesX by remember { mutableFloatStateOf(48f) }
    var mosesY by remember { mutableFloatStateOf(0f) }
    var mosesVy by remember { mutableFloatStateOf(0f) }
    var facingRight by remember { mutableStateOf(true) }
    var moveLeftHeld by remember { mutableStateOf(false) }
    var moveRightHeld by remember { mutableStateOf(false) }
    var airJumpUsed by remember { mutableStateOf(false) }
    var animTime by remember { mutableFloatStateOf(0f) }
    var cameraX by remember { mutableFloatStateOf(0f) }
    var worldViewportW by remember { mutableFloatStateOf(460f) }
    var seaPartingProgress by remember { mutableFloatStateOf(0f) }

    // Облачный и Огненный столп (Исход 13:21, 14:19)
    // true = ведёт вперёд и ускоряет; false = встал защитным щитом перед опасностью
    var pillarProtectiveMode by remember { mutableStateOf(false) }
    var pillarShieldFlash by remember { mutableFloatStateOf(0f) }

    // Секретный подземный оазис внутри колодца (как подземелье в трубе Марио!)
    var inUndergroundOasis by remember { mutableStateOf(false) }
    var returnSurfaceX by remember { mutableFloatStateOf(240f) }
    var standingOnSecretWellIndex by remember { mutableIntStateOf(-1) }

    // Перетаскиваемые плавающие кнопки управления
    var dpadOffsetX by remember { mutableFloatStateOf(prefs.getFloat("dpad_ox", 0f)) }
    var dpadOffsetY by remember { mutableFloatStateOf(prefs.getFloat("dpad_oy", 0f)) }
    var jumpOffsetX by remember { mutableFloatStateOf(prefs.getFloat("jump_ox", 0f)) }
    var jumpOffsetY by remember { mutableFloatStateOf(prefs.getFloat("jump_oy", 0f)) }

    val wells = remember { mutableStateListOf<DesertWell>() }
    val blocks = remember { mutableStateListOf<OverheadBlock>() }
    val rockPuzzles = remember { mutableStateListOf<MeribahRockPuzzle>() }
    val oasisTreasures = remember { mutableStateListOf<OasisTreasure>() }
    val soldiers = remember { mutableStateListOf<PharaohSoldier>() }
    val families = remember { mutableStateListOf<HebrewFamilySpot>() }
    val popups = remember { mutableStateListOf<FloatingPopup>() }

    fun levelGoalX(st: Int): Float = when (st) {
        1 -> 1580f
        2 -> 1840f
        3 -> 2050f
        else -> 1950f
    }

    fun populateOasisTreasures() {
        oasisTreasures.clear()
        oasisTreasures.addAll(
            listOf(
                OasisTreasure(1, x = 110f, y = 18f),
                OasisTreasure(2, x = 155f, y = 42f),
                OasisTreasure(3, x = 200f, y = 58f, isPsalmScroll = true),
                OasisTreasure(4, x = 245f, y = 42f),
                OasisTreasure(5, x = 290f, y = 18f),
            ),
        )
    }

    fun buildStage(targetStage: Int, resetTotalScore: Boolean) {
        stage = targetStage.coerceIn(1, 4)
        mosesX = 46f
        mosesY = 0f
        mosesVy = 0f
        facingRight = true
        moveLeftHeld = false
        moveRightHeld = false
        airJumpUsed = false
        cameraX = 0f
        hurtCooldown = 0f
        staffPowerTimer = 0f
        staffWaveTimer = 0f
        seaPartingProgress = 0f
        inUndergroundOasis = false
        standingOnSecretWellIndex = -1
        gameOver = false
        stageVictory = false
        fullExodusComplete = false
        running = true
        if (resetTotalScore) {
            score = 0
            hearts = 3
        } else {
            hearts = max(hearts, 3)
        }

        wells.clear()
        blocks.clear()
        rockPuzzles.clear()
        soldiers.clear()
        families.clear()
        popups.clear()
        populateOasisTreasures()

        when (stage) {
            1 -> {
                bannerText = "🔥 Глава 1: Неопалимая купина, колодцы с оазисами и скалы воды!"
                wells.addAll(
                    listOf(
                        DesertWell(x = 240f, height = 46f, isSecretOasis = true),
                        DesertWell(x = 490f, height = 56f, hasSpringWater = true),
                        DesertWell(x = 820f, height = 48f, isSecretOasis = true),
                        DesertWell(x = 1100f, height = 60f, hasSpringWater = true),
                        DesertWell(x = 1360f, height = 46f),
                    ),
                )
                rockPuzzles.add(
                    MeribahRockPuzzle(id = 1, rockX = 630f, raftX = 695f, targetHeight = 84f),
                )
                blocks.addAll(
                    listOf(
                        OverheadBlock(1, x = 150f, y = 66f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 0),
                        OverheadBlock(2, x = 182f, y = 66f, kind = MosesBlockKind.BRICK),
                        OverheadBlock(3, x = 350f, y = 72f, kind = MosesBlockKind.BRICK),
                        OverheadBlock(4, x = 382f, y = 72f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                        OverheadBlock(5, x = 414f, y = 72f, kind = MosesBlockKind.BRICK),
                        OverheadBlock(6, x = 710f, y = 135f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 4),
                        OverheadBlock(7, x = 742f, y = 135f, kind = MosesBlockKind.QUESTION_HEART),
                        OverheadBlock(8, x = 930f, y = 74f, kind = MosesBlockKind.BRICK),
                        OverheadBlock(9, x = 962f, y = 74f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 1),
                        OverheadBlock(10, x = 1220f, y = 70f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                    ),
                )
                soldiers.addAll(
                    listOf(
                        PharaohSoldier(1, x = 360f, minX = 295f, maxX = 465f, speed = 56f),
                        PharaohSoldier(2, x = 940f, minX = 875f, maxX = 1075f, speed = 66f),
                        PharaohSoldier(3, x = 1240f, minX = 1160f, maxX = 1335f, speed = 72f, eliteChariot = true),
                        PharaohSoldier(4, x = 1460f, minX = 1410f, maxX = 1530f, speed = 64f),
                    ),
                )
                families.addAll(
                    listOf(
                        HebrewFamilySpot(1, x = 575f),
                        HebrewFamilySpot(2, x = 900f),
                        HebrewFamilySpot(3, x = 1190f),
                        HebrewFamilySpot(4, x = 1435f),
                    ),
                )
            }

            2 -> {
                bannerText = "🏛️ Глава 2: Египет! Собери народ Божий, спускайся в колодцы-оазисы!"
                wells.addAll(
                    listOf(
                        DesertWell(x = 220f, height = 50f, isSecretOasis = true),
                        DesertWell(x = 460f, height = 62f, hasSpringWater = true),
                        DesertWell(x = 740f, height = 48f, isSecretOasis = true),
                        DesertWell(x = 1020f, height = 64f, hasSpringWater = true),
                        DesertWell(x = 1310f, height = 52f, isSecretOasis = true),
                        DesertWell(x = 1570f, height = 58f, hasSpringWater = true),
                    ),
                )
                rockPuzzles.add(
                    MeribahRockPuzzle(id = 2, rockX = 850f, raftX = 915f, targetHeight = 88f),
                )
                blocks.addAll(
                    listOf(
                        OverheadBlock(11, x = 140f, y = 68f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 1),
                        OverheadBlock(12, x = 330f, y = 74f, kind = MosesBlockKind.BRICK),
                        OverheadBlock(13, x = 362f, y = 74f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                        OverheadBlock(14, x = 580f, y = 70f, kind = MosesBlockKind.QUESTION_HEART),
                        OverheadBlock(15, x = 925f, y = 140f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 2),
                        OverheadBlock(16, x = 1150f, y = 74f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                        OverheadBlock(17, x = 1430f, y = 70f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 3),
                        OverheadBlock(18, x = 1462f, y = 70f, kind = MosesBlockKind.QUESTION_HEART),
                    ),
                )
                soldiers.addAll(
                    listOf(
                        PharaohSoldier(11, x = 350f, minX = 275f, maxX = 435f, speed = 66f),
                        PharaohSoldier(12, x = 600f, minX = 520f, maxX = 715f, speed = 74f, eliteChariot = true),
                        PharaohSoldier(13, x = 1160f, minX = 1080f, maxX = 1285f, speed = 78f, eliteChariot = true),
                        PharaohSoldier(14, x = 1450f, minX = 1370f, maxX = 1545f, speed = 76f),
                        PharaohSoldier(15, x = 1700f, minX = 1630f, maxX = 1790f, speed = 82f, eliteChariot = true),
                    ),
                )
                families.addAll(
                    listOf(
                        HebrewFamilySpot(11, x = 310f),
                        HebrewFamilySpot(12, x = 575f),
                        HebrewFamilySpot(13, x = 815f),
                        HebrewFamilySpot(14, x = 1130f),
                        HebrewFamilySpot(15, x = 1420f),
                        HebrewFamilySpot(16, x = 1675f),
                    ),
                )
            }

            3 -> {
                bannerText = "🌊 Глава 3: Огненный столп и переход через Чермное море!"
                wells.addAll(
                    listOf(
                        DesertWell(x = 240f, height = 52f, hasSpringWater = true),
                        DesertWell(x = 510f, height = 48f, isSecretOasis = true),
                        DesertWell(x = 790f, height = 64f, hasSpringWater = true),
                        DesertWell(x = 1090f, height = 52f, isSecretOasis = true),
                        DesertWell(x = 1360f, height = 60f, hasSpringWater = true),
                        DesertWell(x = 1640f, height = 48f),
                    ),
                )
                rockPuzzles.add(
                    MeribahRockPuzzle(id = 3, rockX = 635f, raftX = 700f, targetHeight = 88f),
                )
                blocks.addAll(
                    listOf(
                        OverheadBlock(21, x = 160f, y = 70f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                        OverheadBlock(22, x = 380f, y = 74f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 2),
                        OverheadBlock(23, x = 710f, y = 140f, kind = MosesBlockKind.QUESTION_HEART),
                        OverheadBlock(24, x = 940f, y = 76f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                        OverheadBlock(25, x = 1220f, y = 72f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 3),
                        OverheadBlock(26, x = 1500f, y = 74f, kind = MosesBlockKind.QUESTION_HEART),
                    ),
                )
                soldiers.addAll(
                    listOf(
                        PharaohSoldier(21, x = 380f, minX = 300f, maxX = 485f, speed = 74f, eliteChariot = true),
                        PharaohSoldier(22, x = 950f, minX = 855f, maxX = 1065f, speed = 80f, eliteChariot = true),
                        PharaohSoldier(23, x = 1230f, minX = 1150f, maxX = 1335f, speed = 80f),
                        PharaohSoldier(24, x = 1510f, minX = 1425f, maxX = 1615f, speed = 84f, eliteChariot = true),
                    ),
                )
                families.addAll(
                    listOf(
                        HebrewFamilySpot(21, x = 430f),
                        HebrewFamilySpot(22, x = 880f),
                        HebrewFamilySpot(23, x = 1190f),
                        HebrewFamilySpot(24, x = 1475f),
                        HebrewFamilySpot(25, x = 1710f),
                    ),
                )
            }

            else -> {
                bannerText = "⛰️ Глава 4: Гора Синай! Поднимись по уступам за Скрижалями Завета!"
                wells.addAll(
                    listOf(
                        DesertWell(x = 220f, height = 52f, isSecretOasis = true),
                        DesertWell(x = 580f, height = 60f, hasSpringWater = true),
                        DesertWell(x = 1020f, height = 56f, isSecretOasis = true),
                        DesertWell(x = 1420f, height = 64f, hasSpringWater = true),
                    ),
                )
                // Две скалы Меривы, поднимающие плоты к высоким ступеням Синая
                rockPuzzles.addAll(
                    listOf(
                        MeribahRockPuzzle(id = 41, rockX = 360f, raftX = 425f, targetHeight = 82f),
                        MeribahRockPuzzle(id = 42, rockX = 1160f, raftX = 1225f, targetHeight = 90f),
                    ),
                )
                // Каменные ступени восхождения на Синай и золотые блоки со стихами
                blocks.addAll(
                    listOf(
                        OverheadBlock(31, x = 150f, y = 66f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 4),
                        OverheadBlock(32, x = 505f, y = 92f, width = 56f, height = 20f, kind = MosesBlockKind.SINAI_STEP),
                        OverheadBlock(33, x = 670f, y = 76f, width = 56f, height = 20f, kind = MosesBlockKind.SINAI_STEP),
                        OverheadBlock(34, x = 750f, y = 114f, width = 62f, height = 20f, kind = MosesBlockKind.SINAI_STEP),
                        OverheadBlock(35, x = 765f, y = 166f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 5),
                        OverheadBlock(36, x = 890f, y = 74f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                        OverheadBlock(37, x = 1310f, y = 96f, width = 60f, height = 20f, kind = MosesBlockKind.SINAI_STEP),
                        OverheadBlock(38, x = 1520f, y = 82f, width = 62f, height = 20f, kind = MosesBlockKind.SINAI_STEP),
                        OverheadBlock(39, x = 1615f, y = 118f, width = 66f, height = 20f, kind = MosesBlockKind.SINAI_STEP),
                        OverheadBlock(40, x = 1630f, y = 172f, kind = MosesBlockKind.QUESTION_HEART),
                    ),
                )
                soldiers.addAll(
                    listOf(
                        PharaohSoldier(31, x = 310f, minX = 275f, maxX = 350f, speed = 64f),
                        PharaohSoldier(32, x = 860f, minX = 760f, maxX = 990f, speed = 76f, eliteChariot = true),
                        PharaohSoldier(33, x = 1350f, minX = 1295f, maxX = 1400f, speed = 74f),
                        PharaohSoldier(34, x = 1680f, minX = 1520f, maxX = 1780f, speed = 80f, eliteChariot = true),
                    ),
                )
                families.addAll(
                    listOf(
                        HebrewFamilySpot(31, x = 540f),
                        HebrewFamilySpot(32, x = 950f),
                        HebrewFamilySpot(33, x = 1380f),
                        HebrewFamilySpot(34, x = 1760f),
                    ),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        buildStage(targetStage = 1, resetTotalScore = true)
    }

    // Вход в секретный подземный оазис внутри колодца (или выход из него)
    fun enterOrExitOasis() {
        if (inUndergroundOasis) {
            inUndergroundOasis = false
            mosesX = returnSurfaceX + 54f
            mosesY = 62f
            mosesVy = 420f
            bannerText = "💦 Поток живой воды вынес Моисея из оазиса!"
            KidsGameAudio.play(KidsSfx.WIN)
            return
        }
        val idx = standingOnSecretWellIndex
        if (idx in wells.indices) {
            val w = wells[idx]
            wells[idx] = w.copy(oasisVisited = true)
            returnSurfaceX = w.x
            populateOasisTreasures()
            inUndergroundOasis = true
            mosesX = 52f
            mosesY = 0f
            mosesVy = 0f
            bannerText = "🌴 Подземный Оазис! Собери манну и свиток Псалма, а справа — гейзер наверх!"
            KidsGameAudio.play(KidsSfx.CORRECT)
        }
    }

    // Удар посохом Моисея: активирует скалу воды (Исх. 17:6), расступает море или останавливает воина
    fun triggerStaffAction() {
        if (inUndergroundOasis) {
            enterOrExitOasis()
            return
        }
        staffWaveTimer = 0.55f
        staffWaveX = mosesX + (if (facingRight) 28f else -28f)
        KidsGameAudio.play(KidsSfx.TAP)

        // 1. Проверяем, стоит ли Моисей рядом со Скалой Меривы (Исход 17:6)
        var activatedAnyRock = false
        for (i in rockPuzzles.indices) {
            val rp = rockPuzzles[i]
            if (!rp.activated && abs(mosesX - rp.rockX) < 62f) {
                rockPuzzles[i] = rp.copy(activated = true)
                activatedAnyRock = true
                score += 35
                popups.add(FloatingPopup(rp.rockX, 58f, "💧 Поток из скалы! +35", Color(0xFF38BDF8)))
                bannerText = "🪄 Исход 17:6 — Из скалы забила живая вода и подняла плот!"
                KidsGameAudio.play(KidsSfx.WIN)
            }
        }

        // 2. Проверяем, есть ли рядом воин фараона — свет посоха мирно обращает его в отступление
        for (i in soldiers.indices) {
            val s = soldiers[i]
            if (!s.defeated && abs(s.x - staffWaveX) < 52f && abs(mosesY) < 55f) {
                soldiers[i] = s.copy(defeated = true)
                score += 20
                popups.add(FloatingPopup(s.x, 46f, "✨ Свет посоха! +20", Color(0xFFFEF08A)))
                KidsGameAudio.play(KidsSfx.CORRECT)
            }
        }

        // 3. В главе 3 у Чермного моря взмах посохом сразу полностью расступает воды моря!
        if (stage == 3 && mosesX > levelGoalX(3) - 340f) {
            seaPartingProgress = 1f
            bannerText = "🌊 Моисей простёр посох — и воды Чермного моря расступились!"
            KidsGameAudio.play(KidsSfx.WIN)
        } else if (!activatedAnyRock && standingOnSecretWellIndex >= 0) {
            enterOrExitOasis()
        }
    }

    fun triggerJump() {
        if (gameOver) {
            buildStage(targetStage = stage, resetTotalScore = false)
            return
        }
        if (stageVictory) {
            if (fullExodusComplete) {
                buildStage(targetStage = 1, resetTotalScore = true)
            } else {
                buildStage(targetStage = stage + 1, resetTotalScore = false)
            }
            return
        }
        val pillarBoost = if (!pillarProtectiveMode) 1.06f else 1.0f
        if (mosesVy <= 5f && (!airJumpUsed || mosesY <= 2f)) {
            if (mosesY > 3f) {
                airJumpUsed = true
                mosesVy = 495f * pillarBoost
                popups.add(FloatingPopup(mosesX, mosesY + 12f, "✨ Прыжок посохом!", Color(0xFFFEF08A)))
            } else {
                airJumpUsed = false
                mosesVy = 540f * pillarBoost
            }
            KidsGameAudio.play(KidsSfx.TAP)
        } else if (!airJumpUsed) {
            airJumpUsed = true
            mosesVy = 495f * pillarBoost
            popups.add(FloatingPopup(mosesX, mosesY + 12f, "✨", Color(0xFFFEF08A)))
            KidsGameAudio.play(KidsSfx.TAP)
        }
    }

    // 60 FPS игровой цикл Марио-платформера
    LaunchedEffect(running, gameOver, stageVictory, stage) {
        if (!running || gameOver || stageVictory) return@LaunchedEffect
        var lastNanos = 0L
        while (running && !gameOver && !stageVictory) {
            withFrameNanos { now ->
                if (lastNanos == 0L) {
                    lastNanos = now
                    return@withFrameNanos
                }
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNanos = now
                animTime += dt

                if (hurtCooldown > 0f) hurtCooldown = (hurtCooldown - dt).coerceAtLeast(0f)
                if (staffPowerTimer > 0f) staffPowerTimer = (staffPowerTimer - dt).coerceAtLeast(0f)
                if (staffWaveTimer > 0f) staffWaveTimer = (staffWaveTimer - dt).coerceAtLeast(0f)
                if (pillarShieldFlash > 0f) pillarShieldFlash = (pillarShieldFlash - dt).coerceAtLeast(0f)

                val moveDir = when {
                    moveLeftHeld && !moveRightHeld -> -1
                    moveRightHeld && !moveLeftHeld -> 1
                    else -> 0
                }

                // === РЕЖИМ 1: ПОДЗЕМНЫЙ СЕКРЕТНЫЙ ОАЗИС ВНУТРИ КОЛОДЦА ===
                if (inUndergroundOasis) {
                    if (moveDir != 0) {
                        facingRight = moveDir > 0
                        mosesX = (mosesX + moveDir * 185f * dt).coerceIn(28f, 335f)
                    }
                    mosesY += mosesVy * dt
                    mosesVy -= 1300f * dt
                    if (mosesY <= 0f) {
                        mosesY = 0f
                        mosesVy = 0f
                        airJumpUsed = false
                    }
                    // Сбор сокровищ оазиса (манна и свиток Псалма)
                    for (i in oasisTreasures.indices) {
                        val t = oasisTreasures[i]
                        if (!t.collected && abs(mosesX - t.x) < 24f && abs(mosesY - t.y) < 34f) {
                            oasisTreasures[i] = t.copy(collected = true)
                            if (t.isPsalmScroll) {
                                score += 45
                                hearts = (hearts + 1).coerceAtMost(4)
                                val verse = EXODUS_VERSES[3]
                                activeVerseCard = verse
                                speak(verse.valttsText)
                            } else {
                                score += 15
                            }
                            KidsGameAudio.play(KidsSfx.CORRECT)
                        }
                    }
                    // Правый гейзер поднимает Моисея обратно на поверхность!
                    if (mosesX >= 320f) {
                        enterOrExitOasis()
                    }
                    return@withFrameNanos
                }

                // === РЕЖИМ 2: ОСНОВНОЙ МИР НА ПОВЕРХНОСТИ ===
                // Проверяем, есть ли близко впереди воин фараона -> Облачный/Огненный столп встаёт защитой!
                val nearestThreatDist = soldiers
                    .filter { !it.defeated }
                    .minOfOrNull { abs(it.x - mosesX) } ?: 999f
                pillarProtectiveMode = nearestThreatDist < 95f

                if (moveDir != 0) {
                    facingRight = moveDir > 0
                    val pillarSpeedMult = if (!pillarProtectiveMode) 1.12f else 1.0f
                    val walkSpeed = (if (staffPowerTimer > 0f) 215f else 175f) * pillarSpeedMult
                    var nextX = (mosesX + moveDir * walkSpeed * dt).coerceIn(16f, levelGoalX(stage) + 120f)
                    val mosesRadius = 13f
                    for (well in wells) {
                        val overlapsY = mosesY < well.height - 4f
                        val overlapsX = nextX + mosesRadius > well.x && nextX - mosesRadius < well.x + well.width
                        if (overlapsY && overlapsX) {
                            nextX = if (moveDir > 0) {
                                well.x - mosesRadius
                            } else {
                                well.x + well.width + mosesRadius
                            }
                        }
                    }
                    mosesX = nextX
                }

                // Анимация подъёма плотов у Скал Меривы (Исход 17:6)
                for (i in rockPuzzles.indices) {
                    val rp = rockPuzzles[i]
                    if (rp.activated && rp.currentRaftY < rp.targetHeight) {
                        rockPuzzles[i] = rp.copy(
                            currentRaftY = (rp.currentRaftY + dt * 68f).coerceAtMost(rp.targetHeight),
                        )
                    }
                }

                // Вертикальная физика, приземление на колодцы, плоты и блоки, удар по блокам `?`
                val prevY = mosesY
                val gravity = 1320f
                mosesY += mosesVy * dt
                mosesVy -= gravity * dt

                val mosesHalfW = 13f
                val mosesHeight = 46f

                // Удар головой/посохом снизу по блоку в воздухе (как в Марио!)
                if (mosesVy > 0f) {
                    for (i in blocks.indices) {
                        val b = blocks[i]
                        if (b.kind == MosesBlockKind.SINAI_STEP) continue
                        val inX = mosesX + mosesHalfW > b.x && mosesX - mosesHalfW < b.x + b.width
                        val hitBottom = prevY + mosesHeight <= b.y + 6f && mosesY + mosesHeight >= b.y
                        if (inX && hitBottom) {
                            mosesY = b.y - mosesHeight
                            mosesVy = -80f
                            if (!b.used) {
                                blocks[i] = b.copy(used = true, bumpAnim = 1f)
                                when (b.kind) {
                                    MosesBlockKind.BRICK -> {
                                        score += 10
                                        popups.add(FloatingPopup(b.x + 12f, b.y + 32f, "+10", Color(0xFFFDE68A)))
                                        KidsGameAudio.play(KidsSfx.TAP)
                                    }
                                    MosesBlockKind.QUESTION_VERSE -> {
                                        score += 30
                                        val verse = EXODUS_VERSES[b.verseIndex % EXODUS_VERSES.size]
                                        activeVerseCard = verse
                                        speak(verse.valttsText)
                                        popups.add(FloatingPopup(b.x + 12f, b.y + 34f, "📜 ${verse.ref}", Color(0xFFFEF08A)))
                                        KidsGameAudio.play(KidsSfx.CORRECT)
                                    }
                                    MosesBlockKind.QUESTION_HEART -> {
                                        hearts = (hearts + 1).coerceAtMost(4)
                                        score += 20
                                        popups.add(FloatingPopup(b.x + 12f, b.y + 34f, "💧 Вода жизни +❤️", Color(0xFF38BDF8)))
                                        KidsGameAudio.play(KidsSfx.CORRECT)
                                    }
                                    MosesBlockKind.QUESTION_STAFF_GLOW -> {
                                        staffPowerTimer = 7.5f
                                        score += 35
                                        bannerText = "🔥 Сияние Посоха Божьего! Воины фараона разбегаются!"
                                        popups.add(FloatingPopup(b.x + 12f, b.y + 34f, "✨ Сила Посоха!", Color(0xFFF59E0B)))
                                        KidsGameAudio.play(KidsSfx.WIN)
                                    }
                                    MosesBlockKind.SINAI_STEP -> Unit
                                }
                            } else {
                                blocks[i] = b.copy(bumpAnim = 0.6f)
                                KidsGameAudio.play(KidsSfx.TAP)
                            }
                        }
                    }
                }

                // Проверка приземления сверху на колодец, поднятый водой плот или блок
                var supportY = 0f
                var secretWellDetected = -1
                if (mosesVy <= 0f) {
                    for (idx in wells.indices) {
                        val well = wells[idx]
                        val inX = mosesX + mosesHalfW > well.x + 2f && mosesX - mosesHalfW < well.x + well.width - 2f
                        if (inX && prevY >= well.height - 6f && mosesY <= well.height) {
                            supportY = max(supportY, well.height)
                            if (well.isSecretOasis) {
                                secretWellDetected = idx
                            }
                            if (well.hasSpringWater) {
                                mosesY = well.height + 2f
                                mosesVy = 650f
                                airJumpUsed = false
                                popups.add(FloatingPopup(well.x + 18f, well.height + 22f, "💦 Источник!", Color(0xFF38BDF8)))
                                KidsGameAudio.play(KidsSfx.TAP)
                            }
                        }
                    }
                    // Приземление на деревянный плот, поднятый водой из Скалы Меривы
                    for (rp in rockPuzzles) {
                        val raftTop = rp.currentRaftY + 12f
                        val inX = mosesX + mosesHalfW > rp.raftX && mosesX - mosesHalfW < rp.raftX + rp.raftWidth
                        if (inX && prevY >= raftTop - 7f && mosesY <= raftTop) {
                            supportY = max(supportY, raftTop)
                        }
                    }
                    // Приземление на блоки и каменные ступени горы Синай
                    for (b in blocks) {
                        val topY = b.y + b.height
                        val inX = mosesX + mosesHalfW > b.x + 2f && mosesX - mosesHalfW < b.x + b.width - 2f
                        if (inX && prevY >= topY - 7f && mosesY <= topY) {
                            supportY = max(supportY, topY)
                        }
                    }
                }

                if (mosesVy <= 0f && mosesY <= supportY) {
                    mosesY = supportY
                    mosesVy = 0f
                    airJumpUsed = false
                }
                standingOnSecretWellIndex = if (mosesY > 5f) secretWellDetected else -1

                // Плавное затухание подпрыгивания блоков и всплывающих подписей
                for (i in blocks.indices) {
                    if (blocks[i].bumpAnim > 0f) {
                        blocks[i] = blocks[i].copy(bumpAnim = (blocks[i].bumpAnim - dt * 4f).coerceAtLeast(0f))
                    }
                }
                val popIter = popups.listIterator()
                while (popIter.hasNext()) {
                    val p = popIter.next()
                    val na = p.alpha - dt * 0.95f
                    if (na <= 0f) {
                        popIter.remove()
                    } else {
                        popIter.set(p.copy(y = p.y + dt * 28f, alpha = na))
                    }
                }

                // Сбор семей народа Божьего у домов
                for (i in families.indices) {
                    val fam = families[i]
                    if (!fam.rescued && abs(mosesX - fam.x) < 34f) {
                        families[i] = fam.copy(rescued = true)
                        score += 30
                        val rescuedNow = families.count { it.rescued }
                        popups.add(FloatingPopup(fam.x, 54f, "👨‍👩‍👧 Семья с нами! +30", Color(0xFF34D399)))
                        bannerText = "🙌 Семья народа Божьего присоединилась ($rescuedNow/${families.size})!"
                        KidsGameAudio.play(KidsSfx.CORRECT)
                    }
                }

                // Движение воинов фараона и проверка взаимодействия
                for (i in soldiers.indices) {
                    val s = soldiers[i]
                    if (s.defeated) {
                        if (s.defeatedY > -60f) {
                            soldiers[i] = s.copy(defeatedY = s.defeatedY - dt * 95f)
                        }
                        continue
                    }
                    var nx = s.x + s.dir * s.speed * dt
                    var ndir = s.dir
                    if (nx <= s.minX) {
                        nx = s.minX
                        ndir = 1
                    } else if (nx >= s.maxX) {
                        nx = s.maxX
                        ndir = -1
                    }
                    soldiers[i] = s.copy(x = nx, dir = ndir)

                    // Проверка столкновения с Моисеем
                    val dx = abs(mosesX - nx)
                    val soldierH = if (s.eliteChariot) 42f else 36f
                    if (dx < 24f && mosesY < soldierH) {
                        val stompedFromAbove = (mosesVy < -20f && prevY >= soldierH - 12f) || mosesY >= soldierH - 10f
                        if (stompedFromAbove || staffPowerTimer > 0f) {
                            soldiers[i] = soldiers[i].copy(defeated = true)
                            if (stompedFromAbove) {
                                mosesVy = 430f
                                airJumpUsed = false
                            }
                            score += 25
                            popups.add(FloatingPopup(nx, 44f, "🛡️ Воин отступил! +25", Color(0xFFFBBF24)))
                            KidsGameAudio.play(KidsSfx.CORRECT)
                        } else if (hurtCooldown <= 0f) {
                            // Если Облачный/Огненный столп в режиме защиты, первый удар отражается щитом Столпа!
                            if (pillarProtectiveMode && pillarShieldFlash <= 0f) {
                                soldiers[i] = soldiers[i].copy(dir = -ndir)
                                hurtCooldown = 1.4f
                                pillarShieldFlash = 1.0f
                                bannerText = "🛡️ Облачный/Огненный столп защитил Моисея от стражи!"
                                popups.add(FloatingPopup(mosesX, mosesY + 46f, "🛡️ Защита Столпа!", Color(0xFF38BDF8)))
                                KidsGameAudio.play(KidsSfx.CORRECT)
                            } else {
                                hearts -= 1
                                hurtCooldown = 1.6f
                                mosesVy = 320f
                                popups.add(FloatingPopup(mosesX, mosesY + 42f, "Ой! Стража фараона", Color(0xFFF87171)))
                                if (hearts <= 0) {
                                    gameOver = true
                                    running = false
                                    KidsGameAudio.play(KidsSfx.LOSE)
                                } else {
                                    KidsGameAudio.play(KidsSfx.WRONG)
                                }
                            }
                        }
                    }
                }

                // В 3-й главе около Чермного моря вода расступается по мере приближения Моисея!
                val goalX = levelGoalX(stage)
                if (stage == 3 && mosesX > goalX - 280f) {
                    seaPartingProgress = max(
                        seaPartingProgress,
                        ((mosesX - (goalX - 280f)) / 180f).coerceIn(0f, 1f),
                    )
                }

                // Проверка достижения финиша главы
                if (mosesX >= goalX) {
                    for (i in families.indices) {
                        if (!families[i].rescued) {
                            families[i] = families[i].copy(rescued = true)
                            score += 20
                        }
                    }
                    score += 100
                    if (score > bestScore) {
                        bestScore = score
                        prefs.edit().putInt("best_score", bestScore).apply()
                    }
                    winRound++
                    stageVictory = true
                    fullExodusComplete = (stage >= 4)
                    KidsGameAudio.play(KidsSfx.WIN)
                }

                // Плавное следование камеры за Моисеем
                val targetCam = (mosesX - worldViewportW * 0.30f).coerceIn(0f, max(0f, goalX + 120f - worldViewportW))
                cameraX += (targetCam - cameraX) * (dt * 8f).coerceIn(0f, 1f)
            }
        }
    }

    KidsGameMusic(KidsMusicTrack.GAMES)
    KidsGameWinReward(
        game = KidsGames.MOSES,
        won = winRound > 0,
        points = 25,
        round = winRound,
    )

    val rescuedCount = families.count { it.rescued }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Полноэкранный игровой холст с единым масштабом `u`
            Canvas(
                modifier = Modifier.fillMaxSize(),
            ) {
                val isLandCanvas = size.width > size.height
                val u = if (isLandCanvas) {
                    (size.height / 290f).coerceAtLeast(1f)
                } else {
                    (size.width / 360f).coerceAtLeast(1f)
                }
                worldViewportW = size.width / u

                val groundRatio = if (isLandCanvas) 0.80f else 0.72f
                val groundY = size.height * groundRatio

                // === ОТРИСОВКА СЕКРЕТНОГО ПОДЗЕМНОГО ОАЗИСА ВНУТРИ КОЛОДЦА ===
                if (inUndergroundOasis) {
                    drawUndergroundOasisScene(
                        groundY = groundY,
                        u = u,
                        mosesX = mosesX,
                        mosesY = mosesY,
                        facingRight = facingRight,
                        treasures = oasisTreasures,
                        animTime = animTime,
                    )
                    return@Canvas
                }

                // === ОТРИСОВКА ОСНОВНОГО МИРА (ГЛАВЫ 1–4) ===
                val skyColors = when (stage) {
                    1 -> listOf(Color(0xFF38BDF8), Color(0xFFBAE6FD), Color(0xFFFEF3C7))
                    2 -> listOf(Color(0xFF0284C7), Color(0xFFFDE68A), Color(0xFFFCD34D))
                    3 -> listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFFF59E0B))
                    else -> listOf(Color(0xFF1D4ED8), Color(0xFF7DD3FC), Color(0xFFFEF08A))
                }
                drawRect(brush = Brush.verticalGradient(skyColors), size = size)

                // Пирамиды (гл. 1-2) или вершины гор Хорив/Синай (гл. 3-4) на дальнем плане
                val parallax = cameraX * 0.35f
                for (pIdx in 0..6) {
                    val px = (pIdx * 280f - parallax % 280f) * u
                    val pyrH = (if (pIdx % 2 == 0) 98f else 72f) * u
                    val pyrW = pyrH * 1.65f
                    val pyrPath = Path().apply {
                        moveTo(px - pyrW * 0.5f, groundY)
                        lineTo(px, groundY - pyrH)
                        lineTo(px + pyrW * 0.5f, groundY)
                        close()
                    }
                    val mountainColor = when (stage) {
                        3 -> Color(0xFF4338CA).copy(alpha = 0.38f)
                        4 -> Color(0xFF475569).copy(alpha = 0.42f)
                        else -> Color(0xFFD97706).copy(alpha = 0.28f)
                    }
                    drawPath(path = pyrPath, color = mountainColor)
                }

                // Облачный столп днём (гл. 1, 2, 4) и Огненный столп ночью (гл. 3) — живой проводник и щит!
                val mosesScreenX = (mosesX - cameraX) * u
                val mosesScreenBottomY = groundY - mosesY * u
                drawGuidingPillar(
                    mosesScreenX = mosesScreenX,
                    groundY = groundY,
                    u = u,
                    isFirePillar = (stage == 3),
                    protectiveMode = pillarProtectiveMode,
                    shieldFlash = pillarShieldFlash,
                    animTime = animTime,
                )

                // В главе 1 на старте горит Неопалимая купина
                if (stage == 1) {
                    val bushScreenX = (18f - cameraX) * u
                    if (bushScreenX > -80f * u && bushScreenX < size.width) {
                        drawBurningBush(bushScreenX, groundY, u, animTime)
                    }
                }

                // Дома/шатры семей народа Божьего
                for (fam in families) {
                    val fx = (fam.x - cameraX) * u
                    if (fx > -90f * u && fx < size.width + 60f * u) {
                        drawHebrewHouse(fx, groundY, u, fam.rescued)
                    }
                }

                // Головоломки со Скалой Меривы (Исход 17:6) и поднимающимся на воде плотом
                for (rp in rockPuzzles) {
                    val rx = (rp.rockX - cameraX) * u
                    val raftScreenX = (rp.raftX - cameraX) * u
                    if (rx > -140f * u && rx < size.width + 140f * u) {
                        drawMeribahRockAndRaft(
                            rockX = rx,
                            raftX = raftScreenX,
                            raftW = rp.raftWidth * u,
                            raftY = rp.currentRaftY * u,
                            groundY = groundY,
                            u = u,
                            activated = rp.activated,
                            animTime = animTime,
                        )
                    }
                }

                // Каменные колодцы (включая секретные колодцы-оазисы с золотым ободом!)
                for (well in wells) {
                    val wx = (well.x - cameraX) * u
                    if (wx > -80f * u && wx < size.width + 60f * u) {
                        drawDesertWell(
                            x = wx,
                            groundY = groundY,
                            w = well.width * u,
                            h = well.height * u,
                            u = u,
                            hasSpring = well.hasSpringWater,
                            isSecretOasis = well.isSecretOasis,
                            oasisVisited = well.oasisVisited,
                            animTime = animTime,
                        )
                    }
                }

                // Висящие в воздухе блоки (кирпичи, ступени Синая и золотые `?` со стихами)
                for (b in blocks) {
                    val bx = (b.x - cameraX) * u
                    if (bx > -80f * u && bx < size.width + 80f * u) {
                        val bumpOffset = sin(b.bumpAnim * Math.PI).toFloat() * 7f * u
                        val byTop = groundY - (b.y + b.height) * u - bumpOffset
                        drawMarioStyleBlock(
                            x = bx,
                            y = byTop,
                            w = b.width * u,
                            h = b.height * u,
                            u = u,
                            kind = b.kind,
                            used = b.used,
                        )
                    }
                }

                // Финал уровня: Врата Египта (гл. 1-2), Чермное море (гл. 3) или Гора Синай со Скрижалями (гл. 4)
                val goalScreenX = (levelGoalX(stage) - cameraX) * u
                if (goalScreenX > -220f * u && goalScreenX < size.width + 240f * u) {
                    drawStageGoalMonument(
                        stage = stage,
                        x = goalScreenX,
                        groundY = groundY,
                        u = u,
                        seaParting = seaPartingProgress,
                        animTime = animTime,
                    )
                }

                // Спасённые люди из народа Божьего радостно идут цепочкой за Моисеем!
                for (idx in 0 until min(rescuedCount, 6)) {
                    val trailDir = if (facingRight) -1 else 1
                    val followerX = mosesScreenX + trailDir * (26f + idx * 20f) * u
                    val hop = abs(sin(animTime * 6f + idx * 0.9f)) * 4f * u
                    drawFollowerPerson(
                        x = followerX,
                        groundY = groundY - hop,
                        u = u,
                        index = idx,
                    )
                }

                // Воины фараона
                for (s in soldiers) {
                    val sx = (s.x - cameraX) * u
                    if (sx > -60f * u && sx < size.width + 60f * u) {
                        drawPharaohSoldier(
                            x = sx,
                            groundY = groundY - s.defeatedY * u,
                            u = u,
                            dir = s.dir,
                            elite = s.eliteChariot,
                            defeated = s.defeated,
                            animTime = animTime,
                        )
                    }
                }

                // Взмах посохом Моисея (световая волна)
                if (staffWaveTimer > 0f) {
                    val waveScreenX = (staffWaveX - cameraX) * u
                    drawCircle(
                        color = Color(0xFFFEF08A).copy(alpha = (staffWaveTimer * 1.6f).coerceIn(0f, 0.85f)),
                        radius = (34f - staffWaveTimer * 24f) * u,
                        center = Offset(waveScreenX, mosesScreenBottomY - 24f * u),
                    )
                }

                // Сам пророк Моисей с посохом!
                val blinkHide = hurtCooldown > 0f && ((hurtCooldown * 12f).toInt() % 2 == 1)
                if (!blinkHide) {
                    drawMosesHero(
                        x = mosesScreenX,
                        bottomY = mosesScreenBottomY,
                        u = u,
                        facingRight = facingRight,
                        jumping = mosesY > 2f,
                        staffGlowing = staffPowerTimer > 0f || staffWaveTimer > 0f,
                        animTime = animTime,
                    )
                }

                // Всплывающие искры наград
                for (p in popups) {
                    val px = (p.x - cameraX) * u
                    val py = groundY - p.y * u
                    drawCircle(
                        color = p.color.copy(alpha = (p.alpha * 0.85f).coerceIn(0f, 1f)),
                        radius = 7f * u,
                        center = Offset(px, py),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = (p.alpha * 0.9f).coerceIn(0f, 1f)),
                        radius = 3.5f * u,
                        center = Offset(px, py),
                    )
                }

                // Песчаная земля и камни дороги
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFD97706), Color(0xFFB45309), Color(0xFF78350F)),
                        startY = groundY,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, groundY),
                    size = Size(size.width, size.height - groundY),
                )
                drawLine(
                    color = Color(0xFF92400E),
                    start = Offset(0f, groundY),
                    end = Offset(size.width, groundY),
                    strokeWidth = 3f * u,
                )

                val pebbleSpacing = 42f * u
                val scrollPx = (cameraX * u) % pebbleSpacing
                var px = -scrollPx
                var pCount = 0
                while (px < size.width + pebbleSpacing) {
                    val py = groundY + (12f + (pCount % 3) * 8f) * u
                    drawRoundRect(
                        color = Color(0xFFFEF3C7).copy(alpha = 0.35f),
                        topLeft = Offset(px, py),
                        size = Size(14f * u, 5f * u),
                        cornerRadius = CornerRadius(3f * u, 3f * u),
                    )
                    px += pebbleSpacing
                    pCount++
                }
            }

            // Карточка открытого Библейского стиха из блока `?` с кнопкой озвучки 🔊
            activeVerseCard?.let { verse ->
                LaunchedEffect(verse) {
                    kotlinx.coroutines.delay(5800)
                    if (activeVerseCard == verse) activeVerseCard = null
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .safeDrawingPadding()
                        .padding(top = if (isLandscape) 52.dp else 68.dp, start = 14.dp, end = 14.dp)
                        .shadow(10.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF78350F), Color(0xFF92400E)),
                            ),
                        )
                        .border(1.5.dp, Color(0xFFFDE047), RoundedCornerShape(20.dp))
                        .clickable { speak(verse.valttsText) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("📜", fontSize = 22.sp)
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = verse.ref,
                            color = Color(0xFFFDE047),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = verse.text,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable { speak(verse.valttsText) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Озвучить стих",
                            tint = Color(0xFFFDE047),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Всплывающий баннер подсказки / события (показывается, когда нет карточки стиха)
            if (activeVerseCard == null) {
                bannerText?.let { msg ->
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(3400)
                        if (bannerText == msg) bannerText = null
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .safeDrawingPadding()
                            .padding(top = if (isLandscape) 54.dp else 72.dp, start = 16.dp, end = 16.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF78350F).copy(alpha = 0.90f))
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

            // Верхняя полупрозрачная панель (Назад, Выбор Главы 1..4, Сердечки, Народ Божий, Очки)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Кнопка Назад и быстрый переключатель глав 1·2·3·4
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.74f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    for (ch in 1..4) {
                        val active = (stage == ch)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (active) Color(0xFFD97706) else Color.White.copy(alpha = 0.12f))
                                .clickable { buildStage(targetStage = ch, resetTotalScore = false) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "Гл.$ch",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                // Счётчик сердечек, статуса Столпа, спасённого народа и очков
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF065F46).copy(alpha = 0.88f))
                        .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "❤️".repeat(hearts.coerceAtLeast(0)),
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = if (pillarProtectiveMode) "🛡️Столп" else if (stage == 3) "🔥Столп" else "☁️Столп",
                        color = Color(0xFFBAE6FD),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = "👨‍👩‍👧 $rescuedCount/${families.size}",
                        color = Color(0xFFFEF08A),
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = "⭐ $score",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }

                // Сброс и звук
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.68f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { buildStage(targetStage = stage, resetTotalScore = false) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Сначала",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    KidsAudioToggles()
                }
            }

            // Полоска прогресса пути до цели главы
            val progressFrac = if (inUndergroundOasis) 1f else (mosesX / levelGoalX(stage)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 46.dp, start = 28.dp, end = 28.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.28f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFrac)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFDE047), Color(0xFF34D399)),
                            ),
                        ),
                )
            }

            // Окно победы в главе или проигрыша
            if (gameOver || stageVictory) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.92f))
                        .border(2.dp, Color(0xFFFDE047).copy(alpha = 0.6f), RoundedCornerShape(26.dp))
                        .clickable {
                            if (gameOver) {
                                buildStage(targetStage = stage, resetTotalScore = false)
                            } else if (fullExodusComplete) {
                                buildStage(targetStage = 1, resetTotalScore = true)
                            } else {
                                buildStage(targetStage = stage + 1, resetTotalScore = false)
                            }
                        }
                        .padding(horizontal = 26.dp, vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val title = when {
                            gameOver -> "🛡️ Стража остановила путь!"
                            fullExodusComplete -> "⛰️📜 СКРИЖАЛИ ЗАВЕТА ПОЛУЧЕНЫ!"
                            stage == 1 -> "🏛️ Моисей вошёл в Египет!"
                            stage == 2 -> "🙌 Народ Божий собран в путь!"
                            else -> "🌊 Чермное море пройдено!"
                        }
                        val sub = when {
                            gameOver -> "Нажми сюда или «ПРЫЖОК», чтобы пройти главу $stage ещё раз"
                            fullExodusComplete -> "Все 4 главы пройдены: от Неопалимой купины до горы Синай! Очки: $score (+2 звезды ⭐)"
                            stage == 1 -> "Впереди Глава 2: собери семьи народа Божьего и найди подземные колодцы-оазисы!"
                            stage == 2 -> "Впереди Глава 3: Огненный столп и переход через Чермное море!"
                            else -> "Впереди Глава 4: поднимись на гору Синай за 10 заповедями Божьими!"
                        }
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 19.sp,
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
                                text = when {
                                    gameOver -> "Попробовать снова"
                                    fullExodusComplete -> "Играть с 1-й главы"
                                    else -> "Следующая глава ▶"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            // Плавающий левый пульт движения: ◀ ВЛЕВО | ▼ ОАЗИС | ВПРАВО ▶ + ручка перетаскивания ✥
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
                // Ручка перетаскивания ✥
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

                // Кнопка ◀ ВЛЕВО
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (moveLeftHeld) Color(0xFFD97706) else Color.Transparent)
                        .pointerInput(stage, running, gameOver, inUndergroundOasis) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                moveLeftHeld = true
                                waitForUpOrCancellation()
                                moveLeftHeld = false
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Влево",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }

                // Если Моисей стоит на секретном колодце или внутри Оазиса — кнопка ▼ ОАЗИС / ▲ НАВЕРХ
                if (standingOnSecretWellIndex >= 0 || inUndergroundOasis) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF0284C7))
                            .clickable { enterOrExitOasis() }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                if (inUndergroundOasis) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Оазис",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = if (inUndergroundOasis) "ВЫХОД" else "ОАЗИС",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(26.dp)
                            .background(Color.White.copy(alpha = 0.25f)),
                    )
                }

                // Кнопка ВПРАВО ▶
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (moveRightHeld) Color(0xFFD97706) else Color.Transparent)
                        .pointerInput(stage, running, gameOver, inUndergroundOasis) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                moveRightHeld = true
                                waitForUpOrCancellation()
                                moveRightHeld = false
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
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

            // Плавающий правый блок: 🪄 ПОСОХ (для скал воды Исх.17:6 и отпора страже) + ▲ ПРЫЖОК + ручка ✥
            val canDoubleJump = mosesY > 3f && !airJumpUsed
            val nearMeribahRock = rockPuzzles.any { !it.activated && abs(mosesX - it.rockX) < 65f }
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
                // Кнопка 🪄 ПОСОХ (светится голубым у Скалы Меривы!)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (nearMeribahRock) Color(0xFF0284C7) else Color(0xFFB45309),
                        )
                        .pointerInput(stage, running, gameOver, inUndergroundOasis) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                triggerStaffAction()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (nearMeribahRock) "🪄 СКАЛА!" else "🪄 ПОСОХ",
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
                        .background(
                            when {
                                staffPowerTimer > 0f -> Color(0xFFD97706)
                                canDoubleJump -> Color(0xFF0284C7)
                                else -> Color(0xFF059669)
                            },
                        )
                        .pointerInput(stage, running, gameOver, stageVictory, inUndergroundOasis) {
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

                // Ручка перетаскивания ✥
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

/** Рисуем Облачный столп днём и Огненный столп ночью (проводник и защитный щит). */
private fun DrawScope.drawGuidingPillar(
    mosesScreenX: Float,
    groundY: Float,
    u: Float,
    isFirePillar: Boolean,
    protectiveMode: Boolean,
    shieldFlash: Float,
    animTime: Float,
) {
    val sway = sin(animTime * 3.5f) * 6f * u
    val pillarX = (mosesScreenX + (if (protectiveMode) 36f else 68f) * u).coerceIn(40f * u, size.width - 50f * u)
    val topY = groundY - (if (protectiveMode) 115f else 175f) * u
    val bottomY = if (protectiveMode) groundY - 4f * u else groundY - 58f * u

    val outerColor = when {
        shieldFlash > 0f -> Color(0xFF38BDF8).copy(alpha = 0.65f)
        isFirePillar -> Color(0xFFF97316).copy(alpha = 0.45f)
        protectiveMode -> Color(0xFF38BDF8).copy(alpha = 0.48f)
        else -> Color.White.copy(alpha = 0.45f)
    }
    val coreColor = when {
        isFirePillar -> Color(0xFFFEF08A).copy(alpha = 0.80f)
        protectiveMode -> Color(0xFFE0F2FE).copy(alpha = 0.85f)
        else -> Color.White.copy(alpha = 0.78f)
    }

    // Колонна из клубящихся облачных/огненных сфер
    val steps = 5
    for (i in 0..steps) {
        val frac = i.toFloat() / steps
        val cy = topY + (bottomY - topY) * frac
        val cx = pillarX + sin(animTime * 4f + i) * 4f * u + sway * (1f - frac)
        val rad = (18f + sin(animTime * 3f + i) * 3f) * u
        drawCircle(color = outerColor, radius = rad * 1.35f, center = Offset(cx, cy))
        drawCircle(color = coreColor, radius = rad * 0.85f, center = Offset(cx, cy))
    }
}

/** Рисуем Скалу Меривы (Исход 17:6) и деревянный плот, поднимающийся на потоке живой воды. */
private fun DrawScope.drawMeribahRockAndRaft(
    rockX: Float,
    raftX: Float,
    raftW: Float,
    raftY: Float,
    groundY: Float,
    u: Float,
    activated: Boolean,
    animTime: Float,
) {
    // Скала Меривы
    val rockW = 42f * u
    val rockH = 54f * u
    drawRoundRect(
        color = Color(0xFF57534E),
        topLeft = Offset(rockX - rockW * 0.5f, groundY - rockH),
        size = Size(rockW, rockH),
        cornerRadius = CornerRadius(10f * u, 10f * u),
    )
    // Трещина в скале (светится голубым, подсказывая ударить посохом!)
    drawLine(
        color = if (activated) Color(0xFF38BDF8) else Color(0xFF7DD3FC).copy(alpha = 0.75f),
        start = Offset(rockX - 4f * u, groundY - rockH + 10f * u),
        end = Offset(rockX + 5f * u, groundY - 12f * u),
        strokeWidth = (if (activated) 4.5f else 2.5f) * u,
        cap = StrokeCap.Round,
    )

    // Если Моисей ударил посохом в скалу — бьёт водяной столб под плотом!
    if (activated) {
        val wave = sin(animTime * 8f) * 3f * u
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF7DD3FC), Color(0xFF0284C7)),
            ),
            topLeft = Offset(raftX + 10f * u, groundY - raftY),
            size = Size(raftW - 20f * u + wave, raftY),
            cornerRadius = CornerRadius(6f * u, 6f * u),
        )
    }

    // Деревянный плот-платформа
    val raftTopY = groundY - raftY - 12f * u
    drawRoundRect(
        color = Color(0xFF92400E),
        topLeft = Offset(raftX, raftTopY),
        size = Size(raftW, 12f * u),
        cornerRadius = CornerRadius(4f * u, 4f * u),
    )
    drawRoundRect(
        color = Color(0xFFD97706),
        topLeft = Offset(raftX + 4f * u, raftTopY + 2f * u),
        size = Size(raftW - 8f * u, 5f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )
}

/** Отрисовка Секретного Подземного Оазиса внутри колодца (как вход в трубу в Марио). */
private fun DrawScope.drawUndergroundOasisScene(
    groundY: Float,
    u: Float,
    mosesX: Float,
    mosesY: Float,
    facingRight: Boolean,
    treasures: List<OasisTreasure>,
    animTime: Float,
) {
    // Прохладный подземный грот с изумрудно-бирюзовым свечением воды
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF0F172A), Color(0xFF064E3B), Color(0xFF0284C7)),
        ),
        size = size,
    )
    // Каменный свод пещеры сверху
    drawRect(
        color = Color(0xFF334155),
        topLeft = Offset(0f, 0f),
        size = Size(size.width, 32f * u),
    )
    // Подземное озеро и каменный берег
    drawRect(
        color = Color(0xFF0369A1),
        topLeft = Offset(0f, groundY),
        size = Size(size.width, size.height - groundY),
    )
    drawLine(
        color = Color(0xFF38BDF8),
        start = Offset(0f, groundY),
        end = Offset(size.width, groundY),
        strokeWidth = 4f * u,
    )

    // Левая шахта входа из колодца
    drawRoundRect(
        color = Color(0xFF57534E),
        topLeft = Offset(24f * u, 0f),
        size = Size(44f * u, 70f * u),
        cornerRadius = CornerRadius(4f * u, 4f * u),
    )

    // Сокровища оазиса: золотые сосуды с манной и Свиток Псалма
    for (t in treasures) {
        if (!t.collected) {
            val tx = t.x * u
            val bob = sin(animTime * 5f + t.id) * 4f * u
            val ty = groundY - (t.y + 18f) * u + bob
            drawCircle(
                color = if (t.isPsalmScroll) Color(0xFFFEF08A) else Color(0xFFF59E0B),
                radius = (if (t.isPsalmScroll) 13f else 9f) * u,
                center = Offset(tx, ty),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.7f),
                radius = 4f * u,
                center = Offset(tx - 2f * u, ty - 2f * u),
            )
        }
    }

    // Правый водяной гейзер-лифт для выхода обратно на поверхность
    val exitX = 320f * u
    val jetH = (85f + sin(animTime * 7f) * 10f) * u
    drawRoundRect(
        color = Color(0xFF7DD3FC).copy(alpha = 0.85f),
        topLeft = Offset(exitX - 16f * u, groundY - jetH),
        size = Size(32f * u, jetH),
        cornerRadius = CornerRadius(8f * u, 8f * u),
    )

    // Моисей внутри оазиса
    drawMosesHero(
        x = mosesX * u,
        bottomY = groundY - mosesY * u,
        u = u,
        facingRight = facingRight,
        jumping = mosesY > 2f,
        staffGlowing = true,
        animTime = animTime,
    )
}

/** Рисуем каменный колодец вместо трубы Марио (с водой и золотым ободом для секретных оазисов). */
private fun DrawScope.drawDesertWell(
    x: Float,
    groundY: Float,
    w: Float,
    h: Float,
    u: Float,
    hasSpring: Boolean,
    isSecretOasis: Boolean,
    oasisVisited: Boolean,
    animTime: Float,
) {
    val topY = groundY - h
    drawRoundRect(
        color = if (isSecretOasis) Color(0xFF0F766E) else Color(0xFF78716C),
        topLeft = Offset(x, topY),
        size = Size(w, h),
        cornerRadius = CornerRadius(5f * u, 5f * u),
    )
    var rowY = topY + 12f * u
    while (rowY < groundY - 4f * u) {
        drawLine(
            color = Color(0xFF44403C).copy(alpha = 0.65f),
            start = Offset(x + 3f * u, rowY),
            end = Offset(x + w - 3f * u, rowY),
            strokeWidth = 1.6f * u,
        )
        rowY += 12f * u
    }
    // Каменный или золотой обод сверху (как шляпка трубы в Марио)
    drawRoundRect(
        color = when {
            isSecretOasis && !oasisVisited -> Color(0xFFF59E0B)
            isSecretOasis -> Color(0xFF0D9488)
            else -> Color(0xFF57534E)
        },
        topLeft = Offset(x - 4f * u, topY),
        size = Size(w + 8f * u, 11f * u),
        cornerRadius = CornerRadius(4f * u, 4f * u),
    )
    drawRoundRect(
        color = Color(0xFF38BDF8),
        topLeft = Offset(x + 4f * u, topY - 2f * u),
        size = Size(w - 8f * u, 5f * u),
        cornerRadius = CornerRadius(3f * u, 3f * u),
    )
    if (hasSpring) {
        val splashH = (6f + abs(sin(animTime * 5f)) * 9f) * u
        drawRoundRect(
            color = Color(0xFF7DD3FC),
            topLeft = Offset(x + w * 0.32f, topY - splashH),
            size = Size(w * 0.36f, splashH),
            cornerRadius = CornerRadius(4f * u, 4f * u),
        )
    }
}

/** Рисуем висящий в воздухе блок Марио (кирпичный, ступень Синая или золотой `?`). */
private fun DrawScope.drawMarioStyleBlock(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    u: Float,
    kind: MosesBlockKind,
    used: Boolean,
) {
    val bgColor = when {
        kind == MosesBlockKind.SINAI_STEP -> Color(0xFF64748B)
        used -> Color(0xFF78716C)
        kind == MosesBlockKind.BRICK -> Color(0xFFB45309)
        kind == MosesBlockKind.QUESTION_STAFF_GLOW -> Color(0xFFF59E0B)
        else -> Color(0xFFEAB308)
    }
    drawRoundRect(
        color = bgColor,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(4f * u, 4f * u),
    )
    drawRoundRect(
        color = Color(0xFF451A03).copy(alpha = 0.55f),
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(4f * u, 4f * u),
        style = Stroke(width = 1.8f * u),
    )
    if (!used && kind != MosesBlockKind.BRICK && kind != MosesBlockKind.SINAI_STEP) {
        drawRoundRect(
            color = Color(0xFFFEF9C3),
            topLeft = Offset(x + w * 0.34f, y + h * 0.22f),
            size = Size(w * 0.32f, h * 0.36f),
            cornerRadius = CornerRadius(2f * u, 2f * u),
        )
        drawCircle(
            color = Color(0xFFFEF9C3),
            radius = 2.4f * u,
            center = Offset(x + w * 0.5f, y + h * 0.76f),
        )
    }
}

/** Рисуем Моисея в плаще и с пастушьим посохом в руке. */
private fun DrawScope.drawMosesHero(
    x: Float,
    bottomY: Float,
    u: Float,
    facingRight: Boolean,
    jumping: Boolean,
    staffGlowing: Boolean,
    animTime: Float,
) {
    val dir = if (facingRight) 1f else -1f
    val stride = if (jumping) 4f * u else sin(animTime * 11f) * 4.5f * u

    if (staffGlowing) {
        drawCircle(
            color = Color(0xFFFDE047).copy(alpha = 0.38f + abs(sin(animTime * 8f)) * 0.25f),
            radius = 30f * u,
            center = Offset(x, bottomY - 24f * u),
        )
    }

    // Ноги в сандалиях
    drawRoundRect(
        color = Color(0xFF78350F),
        topLeft = Offset(x - 6f * u + stride, bottomY - 9f * u),
        size = Size(5f * u, 9f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )
    drawRoundRect(
        color = Color(0xFF78350F),
        topLeft = Offset(x + 1f * u - stride, bottomY - 9f * u),
        size = Size(5f * u, 9f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )

    // Хитон и бордово-терракотовый плащ Моисея
    drawRoundRect(
        color = Color(0xFFF5F5F4),
        topLeft = Offset(x - 10f * u, bottomY - 32f * u),
        size = Size(20f * u, 24f * u),
        cornerRadius = CornerRadius(5f * u, 5f * u),
    )
    drawRoundRect(
        color = Color(0xFF9A3412),
        topLeft = Offset(x - 11f * u, bottomY - 31f * u),
        size = Size(14f * u, 22f * u),
        cornerRadius = CornerRadius(4f * u, 4f * u),
    )

    // Голова и седая борода
    drawCircle(
        color = Color(0xFFFDE68A),
        radius = 8.5f * u,
        center = Offset(x, bottomY - 38f * u),
    )
    drawRoundRect(
        color = Color(0xFFE7E5E4),
        topLeft = Offset(x - 5f * u + dir * 2f * u, bottomY - 36f * u),
        size = Size(10f * u, 8f * u),
        cornerRadius = CornerRadius(4f * u, 4f * u),
    )
    drawCircle(
        color = Color(0xFF1C1917),
        radius = 1.6f * u,
        center = Offset(x + dir * 3.5f * u, bottomY - 39.5f * u),
    )

    // Пастуший посох Моисея
    val staffX = x + dir * 13f * u
    drawLine(
        color = Color(0xFF78350F),
        start = Offset(staffX, bottomY - 48f * u),
        end = Offset(staffX, bottomY - 2f * u),
        strokeWidth = 3.2f * u,
        cap = StrokeCap.Round,
    )
    drawCircle(
        color = if (staffGlowing) Color(0xFFFEF08A) else Color(0xFFD97706),
        radius = (if (staffGlowing) 6f else 4f) * u,
        center = Offset(staffX, bottomY - 49f * u),
    )
}

/** Рисуем воина фараона (с египетским головным убором немес, щитом и копьём). */
private fun DrawScope.drawPharaohSoldier(
    x: Float,
    groundY: Float,
    u: Float,
    dir: Int,
    elite: Boolean,
    defeated: Boolean,
    animTime: Float,
) {
    val walk = if (defeated) 0f else sin(animTime * 9f) * 3.5f * u
    val d = dir.toFloat()

    drawRoundRect(
        color = Color(0xFFB45309),
        topLeft = Offset(x - 6f * u + walk, groundY - 8f * u),
        size = Size(4.5f * u, 8f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )
    drawRoundRect(
        color = Color(0xFFB45309),
        topLeft = Offset(x + 2f * u - walk, groundY - 8f * u),
        size = Size(4.5f * u, 8f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )

    drawRoundRect(
        color = if (elite) Color(0xFFB91C1C) else Color(0xFF1D4ED8),
        topLeft = Offset(x - 9f * u, groundY - 27f * u),
        size = Size(18f * u, 19f * u),
        cornerRadius = CornerRadius(4f * u, 4f * u),
    )

    drawCircle(
        color = Color(0xFFF59E0B),
        radius = 8.5f * u,
        center = Offset(x, groundY - 34f * u),
    )
    drawCircle(
        color = Color(0xFFD97706),
        radius = 6.5f * u,
        center = Offset(x + d * 1.5f * u, groundY - 33f * u),
    )

    val shieldX = x + d * 9f * u
    drawRoundRect(
        color = Color(0xFFFBBF24),
        topLeft = Offset(shieldX - 4f * u, groundY - 26f * u),
        size = Size(8f * u, 15f * u),
        cornerRadius = CornerRadius(3f * u, 3f * u),
    )
    if (!defeated) {
        drawLine(
            color = Color(0xFF44403C),
            start = Offset(x - d * 8f * u, groundY - 40f * u),
            end = Offset(x + d * 14f * u, groundY - 18f * u),
            strokeWidth = 2.2f * u,
            cap = StrokeCap.Round,
        )
    }
}

/** Дом семьи народа Божьего в Египте. */
private fun DrawScope.drawHebrewHouse(
    x: Float,
    groundY: Float,
    u: Float,
    rescued: Boolean,
) {
    val w = 44f * u
    val h = 36f * u
    drawRoundRect(
        color = Color(0xFFFDE68A).copy(alpha = 0.88f),
        topLeft = Offset(x - w / 2f, groundY - h),
        size = Size(w, h),
        cornerRadius = CornerRadius(5f * u, 5f * u),
    )
    drawRoundRect(
        color = Color(0xFFB91C1C),
        topLeft = Offset(x - 8f * u, groundY - 22f * u),
        size = Size(16f * u, 22f * u),
        cornerRadius = CornerRadius(3f * u, 3f * u),
    )
    drawRoundRect(
        color = Color(0xFF78350F),
        topLeft = Offset(x - 6f * u, groundY - 20f * u),
        size = Size(12f * u, 20f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )
    if (rescued) {
        drawCircle(
            color = Color(0xFF34D399),
            radius = 6f * u,
            center = Offset(x, groundY - h - 9f * u),
        )
    }
}

/** Спасённый житель народа Божьего, радостно идущий следом за Моисеем. */
private fun DrawScope.drawFollowerPerson(
    x: Float,
    groundY: Float,
    u: Float,
    index: Int,
) {
    val robeColors = listOf(
        Color(0xFF0284C7),
        Color(0xFF059669),
        Color(0xFF7C3AED),
        Color(0xFFD97706),
        Color(0xFFDB2777),
        Color(0xFF0D9488),
    )
    val robe = robeColors[index % robeColors.size]
    drawRoundRect(
        color = robe,
        topLeft = Offset(x - 6f * u, groundY - 22f * u),
        size = Size(12f * u, 22f * u),
        cornerRadius = CornerRadius(4f * u, 4f * u),
    )
    drawCircle(
        color = Color(0xFFFDE68A),
        radius = 5.5f * u,
        center = Offset(x, groundY - 26f * u),
    )
}

/** Неопалимая купина на старте 1-й главы. */
private fun DrawScope.drawBurningBush(
    x: Float,
    groundY: Float,
    u: Float,
    animTime: Float,
) {
    val pulse = sin(animTime * 8f) * 3f * u
    drawCircle(
        color = Color(0xFFF97316).copy(alpha = 0.45f),
        radius = 22f * u + pulse,
        center = Offset(x, groundY - 20f * u),
    )
    drawCircle(
        color = Color(0xFFFACC15).copy(alpha = 0.75f),
        radius = 15f * u,
        center = Offset(x, groundY - 20f * u),
    )
    drawCircle(
        color = Color(0xFF15803D),
        radius = 11f * u,
        center = Offset(x, groundY - 15f * u),
    )
}

/** Финиш главы: Врата Египта (гл. 1-2), Чермное море (гл. 3) или Гора Синай со Скрижалями (гл. 4). */
private fun DrawScope.drawStageGoalMonument(
    stage: Int,
    x: Float,
    groundY: Float,
    u: Float,
    seaParting: Float,
    animTime: Float,
) {
    when (stage) {
        1, 2 -> {
            drawRoundRect(
                color = Color(0xFFD97706),
                topLeft = Offset(x - 24f * u, groundY - 92f * u),
                size = Size(20f * u, 92f * u),
                cornerRadius = CornerRadius(4f * u, 4f * u),
            )
            drawRoundRect(
                color = Color(0xFFD97706),
                topLeft = Offset(x + 16f * u, groundY - 92f * u),
                size = Size(20f * u, 92f * u),
                cornerRadius = CornerRadius(4f * u, 4f * u),
            )
            drawRoundRect(
                color = Color(0xFFF59E0B),
                topLeft = Offset(x - 30f * u, groundY - 102f * u),
                size = Size(72f * u, 14f * u),
                cornerRadius = CornerRadius(4f * u, 4f * u),
            )
        }
        3 -> {
            // Глава 3: Чермное море расступается двумя высокими водяными стенами!
            val wave = sin(animTime * 5f) * 5f * u
            val wallH = (35f + seaParting * 85f) * u
            val gap = (12f + seaParting * 56f) * u
            drawRoundRect(
                color = Color(0xFF0284C7).copy(alpha = 0.82f),
                topLeft = Offset(x - gap - 36f * u, groundY - wallH + wave),
                size = Size(36f * u, wallH),
                cornerRadius = CornerRadius(10f * u, 10f * u),
            )
            drawRoundRect(
                color = Color(0xFF0284C7).copy(alpha = 0.82f),
                topLeft = Offset(x + gap, groundY - wallH - wave),
                size = Size(36f * u, wallH),
                cornerRadius = CornerRadius(10f * u, 10f * u),
            )
            drawRoundRect(
                color = Color(0xFFFEF08A).copy(alpha = 0.45f),
                topLeft = Offset(x - 10f * u, groundY - 130f * u),
                size = Size(20f * u, 130f * u),
                cornerRadius = CornerRadius(8f * u, 8f * u),
            )
        }
        else -> {
            // Глава 4: Вершина Горы Синай и две сияющие каменные Скрижали Завета!
            val sinaiPeak = Path().apply {
                moveTo(x - 75f * u, groundY)
                lineTo(x + 10f * u, groundY - 120f * u)
                lineTo(x + 95f * u, groundY)
                close()
            }
            drawPath(sinaiPeak, Color(0xFF57534E))
            // Золотое сияние славы Божией на вершине Синая
            val glow = (36f + sin(animTime * 6f) * 5f) * u
            drawCircle(
                color = Color(0xFFFDE047).copy(alpha = 0.45f),
                radius = glow,
                center = Offset(x + 10f * u, groundY - 125f * u),
            )
            // Две голубовато-каменные Скрижали с 10 заповедями
            drawRoundRect(
                color = Color(0xFF38BDF8),
                topLeft = Offset(x - 8f * u, groundY - 142f * u),
                size = Size(16f * u, 26f * u),
                cornerRadius = CornerRadius(6f * u, 6f * u),
            )
            drawRoundRect(
                color = Color(0xFF0284C7),
                topLeft = Offset(x + 10f * u, groundY - 142f * u),
                size = Size(16f * u, 26f * u),
                cornerRadius = CornerRadius(6f * u, 6f * u),
            )
        }
    }
}
