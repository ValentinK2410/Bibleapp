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
import androidx.compose.foundation.horizontalScroll
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

/** Каменный колодец (или островок камышей Нила в Сцене 1). */
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

/** Висящие в воздухе блоки (кирпичи, ступени Хорива/Синая и золотые блоки со стихами Писания). */
private data class OverheadBlock(
    val id: Int,
    val x: Float,
    val y: Float,
    val width: Float = 32f,
    val height: Float = 28f,
    val kind: MosesBlockKind,
    val verseIndex: Int = 0,
    val used: Boolean = false,
    val bumpAnim: Float = 0f,
)

/** Скала в Мериве (Исход 17:6): при ударе посохом из неё бьёт живая вода и поднимает плот-мост! */
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

/** Препятствие / патруль сцены: крокодил Нила (Сцена 1), стражник или колесница фараона, воин Амалика. */
private data class PharaohSoldier(
    val id: Int,
    val x: Float,
    val minX: Float,
    val maxX: Float,
    val speed: Float,
    val dir: Int = -1,
    val eliteChariot: Boolean = false,
    val isNileCrocodile: Boolean = false,
    val defeated: Boolean = false,
    val defeatedY: Float = 0f,
)

/** Дом семьи народа Божьего (в Сцене 3 помазывается кровью Пасхального Агнца на косяках дверей) или лилия/омер манны. */
private data class HebrewFamilySpot(
    val id: Int,
    val x: Float,
    val rescued: Boolean = false,
)

/** Всплывающий текст над событием. */
private data class FloatingPopup(
    val x: Float,
    val y: Float,
    val label: String,
    val color: Color,
    val alpha: Float = 1f,
)

@Composable
fun KidsMosesGameScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val prefs = remember { context.getSharedPreferences("kids_moses_prefs", Context.MODE_PRIVATE) }
    val speak = rememberKidsSpeaker(rate = 0.92f)

    // 6 уникальных сюжетных сцен книги Исход:
    // 1 = Корзинка в Ниле (Исход 1–2)
    // 2 = Колодец Мадиама и Неопалимая купина (Исход 2–4)
    // 3 = Пасха в Египте: кровь Агнца на косяках дверей (Исход 5–12)
    // 4 = Чермное море и Столп Облачный и Огненный (Исход 13–15)
    // 5 = Пустыня: Скала в Мериве и Поддержка рук Моисея Аароном и Ором (Исход 16–17)
    // 6 = Гора Синай и Две Скрижали 10 Заповедей (Исход 19–20, 34)
    var stage by remember { mutableIntStateOf(1) }
    val sceneInfo = remember(stage) { MOSES_SCENES[(stage - 1).coerceIn(0, MOSES_SCENES.lastIndex)] }

    var running by remember { mutableStateOf(true) }
    var gameOver by remember { mutableStateOf(false) }
    var stageVictory by remember { mutableStateOf(false) }
    var fullExodusComplete by remember { mutableStateOf(false) }

    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(prefs.getInt("best_score", 0)) }
    var hearts by remember { mutableIntStateOf(3) }
    var hurtCooldown by remember { mutableFloatStateOf(0f) }
    var staffPowerTimer by remember { mutableFloatStateOf(0f) } // Сияние Посоха / укрытие в камышах
    var staffWaveTimer by remember { mutableFloatStateOf(0f) }
    var staffWaveX by remember { mutableFloatStateOf(0f) }
    var winRound by remember { mutableIntStateOf(0) }
    var bannerText by remember { mutableStateOf<String?>(sceneInfo.objectiveHint) }

    // Модальные окна Писания, Диалога выбора верой и 10 Заповедей
    var activeVerseCard by remember { mutableStateOf<ExodusVerseCard?>(null) }
    var showFaithChoiceDialog by remember { mutableStateOf(false) }
    var faithChoiceAnswered by remember { mutableStateOf(false) }
    var selectedFaithOptionIndex by remember { mutableStateOf<Int?>(null) }
    var showVersesTreasury by remember { mutableStateOf(false) }
    var showCommandmentsModal by remember { mutableStateOf(false) }
    var activeCommandmentQuizIndex by remember { mutableIntStateOf(0) }
    var commandmentQuizFeedback by remember { mutableStateOf<String?>(null) }
    val masteredCommandments = remember { mutableStateListOf<Int>() }

    // Специальные состояния механик для каждой из 6 сцен:
    // Сцена 2: снята ли обувь у Неопалимой купины
    var sandalsRemovedAtBush by remember { mutableStateOf(false) }
    // Сцена 4: прогресс разделения Чермного моря и режим Огненного столпа
    var seaPartingProgress by remember { mutableFloatStateOf(0f) }
    var pillarProtectiveMode by remember { mutableStateOf(false) }
    var pillarShieldFlash by remember { mutableFloatStateOf(0f) }
    // Сцена 5: молитва на холме в Рефидиме (высота рук Моисея 0..1 и прогресс победы 0..1)
    var rephidimHandsLevel by remember { mutableFloatStateOf(0.85f) }
    var rephidimVictoryProgress by remember { mutableFloatStateOf(0f) }

    // Координаты героя
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

    // Секретный подземный оазис внутри колодца
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
        1 -> 1520f
        2 -> 1620f
        3 -> 1820f
        4 -> 1980f
        5 -> 1860f
        else -> 1920f
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
        stage = targetStage.coerceIn(1, 6)
        val info = MOSES_SCENES[stage - 1]
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
        sandalsRemovedAtBush = false
        rephidimHandsLevel = 0.85f
        rephidimVictoryProgress = 0f
        inUndergroundOasis = false
        standingOnSecretWellIndex = -1
        showFaithChoiceDialog = false
        faithChoiceAnswered = false
        selectedFaithOptionIndex = null
        activeVerseCard = null
        gameOver = false
        stageVictory = false
        fullExodusComplete = false
        running = true
        bannerText = info.objectiveHint

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
                // Сцена 1: Корзинка в Ниле среди камышей и нильских крокодилов
                wells.addAll(
                    listOf(
                        DesertWell(x = 230f, height = 40f, hasSpringWater = true),
                        DesertWell(x = 490f, height = 44f, isSecretOasis = true),
                        DesertWell(x = 770f, height = 42f, hasSpringWater = true),
                        DesertWell(x = 1040f, height = 44f, isSecretOasis = true),
                        DesertWell(x = 1290f, height = 40f, hasSpringWater = true),
                    ),
                )
                blocks.addAll(
                    listOf(
                        OverheadBlock(1, x = 160f, y = 66f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 0),
                        OverheadBlock(2, x = 360f, y = 70f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                        OverheadBlock(3, x = 620f, y = 68f, kind = MosesBlockKind.QUESTION_HEART),
                        OverheadBlock(4, x = 900f, y = 70f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 1),
                        OverheadBlock(5, x = 1170f, y = 68f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                    ),
                )
                soldiers.addAll(
                    listOf(
                        PharaohSoldier(1, x = 350f, minX = 285f, maxX = 455f, speed = 52f, isNileCrocodile = true),
                        PharaohSoldier(2, x = 640f, minX = 550f, maxX = 735f, speed = 58f, isNileCrocodile = true),
                        PharaohSoldier(3, x = 920f, minX = 830f, maxX = 1010f, speed = 62f, isNileCrocodile = true),
                        PharaohSoldier(4, x = 1190f, minX = 1105f, maxX = 1265f, speed = 64f, isNileCrocodile = true),
                    ),
                )
                families.addAll(
                    listOf(
                        HebrewFamilySpot(1, x = 310f),
                        HebrewFamilySpot(2, x = 600f),
                        HebrewFamilySpot(3, x = 880f),
                        HebrewFamilySpot(4, x = 1150f),
                        HebrewFamilySpot(5, x = 1380f),
                    ),
                )
            }

            2 -> {
                // Сцена 2: Мадиамские колодцы и восхождение к Неопалимой купине на Хориве
                wells.addAll(
                    listOf(
                        DesertWell(x = 230f, height = 48f, isSecretOasis = true),
                        DesertWell(x = 490f, height = 56f, hasSpringWater = true),
                        DesertWell(x = 810f, height = 50f, isSecretOasis = true),
                        DesertWell(x = 1120f, height = 58f, hasSpringWater = true),
                        DesertWell(x = 1360f, height = 48f),
                    ),
                )
                rockPuzzles.add(
                    MeribahRockPuzzle(id = 1, rockX = 620f, raftX = 685f, targetHeight = 84f),
                )
                blocks.addAll(
                    listOf(
                        OverheadBlock(11, x = 150f, y = 66f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 2),
                        OverheadBlock(12, x = 350f, y = 72f, kind = MosesBlockKind.BRICK),
                        OverheadBlock(13, x = 382f, y = 72f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                        OverheadBlock(14, x = 700f, y = 134f, kind = MosesBlockKind.QUESTION_HEART),
                        OverheadBlock(15, x = 950f, y = 74f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 3),
                        OverheadBlock(16, x = 1220f, y = 84f, width = 56f, height = 20f, kind = MosesBlockKind.SINAI_STEP),
                        OverheadBlock(17, x = 1310f, y = 116f, width = 56f, height = 20f, kind = MosesBlockKind.SINAI_STEP),
                    ),
                )
                soldiers.addAll(
                    listOf(
                        PharaohSoldier(11, x = 360f, minX = 290f, maxX = 460f, speed = 56f),
                        PharaohSoldier(12, x = 940f, minX = 870f, maxX = 1080f, speed = 64f),
                        PharaohSoldier(13, x = 1250f, minX = 1175f, maxX = 1335f, speed = 68f),
                    ),
                )
                families.addAll(
                    listOf(
                        HebrewFamilySpot(11, x = 545f),
                        HebrewFamilySpot(12, x = 885f),
                        HebrewFamilySpot(13, x = 1190f),
                        HebrewFamilySpot(14, x = 1440f),
                    ),
                )
            }

            3 -> {
                // Сцена 3: Пасха в Египте — помазание косяков дверей 6 домов Израиля пучком иссопа
                wells.addAll(
                    listOf(
                        DesertWell(x = 220f, height = 50f, isSecretOasis = true),
                        DesertWell(x = 510f, height = 58f, hasSpringWater = true),
                        DesertWell(x = 830f, height = 48f, isSecretOasis = true),
                        DesertWell(x = 1160f, height = 60f, hasSpringWater = true),
                        DesertWell(x = 1480f, height = 50f, isSecretOasis = true),
                    ),
                )
                blocks.addAll(
                    listOf(
                        OverheadBlock(21, x = 145f, y = 68f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 4),
                        OverheadBlock(22, x = 380f, y = 72f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                        OverheadBlock(23, x = 680f, y = 72f, kind = MosesBlockKind.QUESTION_HEART),
                        OverheadBlock(24, x = 990f, y = 74f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 5),
                        OverheadBlock(25, x = 1320f, y = 72f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                    ),
                )
                soldiers.addAll(
                    listOf(
                        PharaohSoldier(21, x = 370f, minX = 285f, maxX = 475f, speed = 64f),
                        PharaohSoldier(22, x = 690f, minX = 585f, maxX = 795f, speed = 72f, eliteChariot = true),
                        PharaohSoldier(23, x = 1010f, minX = 900f, maxX = 1125f, speed = 74f),
                        PharaohSoldier(24, x = 1340f, minX = 1235f, maxX = 1445f, speed = 78f, eliteChariot = true),
                        PharaohSoldier(25, x = 1640f, minX = 1555f, maxX = 1740f, speed = 76f),
                    ),
                )
                families.addAll(
                    listOf(
                        HebrewFamilySpot(21, x = 320f),
                        HebrewFamilySpot(22, x = 620f),
                        HebrewFamilySpot(23, x = 940f),
                        HebrewFamilySpot(24, x = 1270f),
                        HebrewFamilySpot(25, x = 1580f),
                        HebrewFamilySpot(26, x = 1710f),
                    ),
                )
            }

            4 -> {
                // Сцена 4: Огненный столп и разделение Чермного моря
                wells.addAll(
                    listOf(
                        DesertWell(x = 240f, height = 52f, hasSpringWater = true),
                        DesertWell(x = 520f, height = 48f, isSecretOasis = true),
                        DesertWell(x = 810f, height = 62f, hasSpringWater = true),
                        DesertWell(x = 1120f, height = 52f, isSecretOasis = true),
                        DesertWell(x = 1410f, height = 58f, hasSpringWater = true),
                    ),
                )
                rockPuzzles.add(
                    MeribahRockPuzzle(id = 4, rockX = 640f, raftX = 705f, targetHeight = 86f),
                )
                blocks.addAll(
                    listOf(
                        OverheadBlock(31, x = 160f, y = 70f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 6),
                        OverheadBlock(32, x = 400f, y = 74f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                        OverheadBlock(33, x = 720f, y = 138f, kind = MosesBlockKind.QUESTION_HEART),
                        OverheadBlock(34, x = 980f, y = 74f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                        OverheadBlock(35, x = 1270f, y = 72f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 7),
                    ),
                )
                soldiers.addAll(
                    listOf(
                        PharaohSoldier(31, x = 380f, minX = 300f, maxX = 485f, speed = 74f, eliteChariot = true),
                        PharaohSoldier(32, x = 960f, minX = 865f, maxX = 1085f, speed = 80f, eliteChariot = true),
                        PharaohSoldier(33, x = 1280f, minX = 1190f, maxX = 1375f, speed = 82f, eliteChariot = true),
                        PharaohSoldier(34, x = 1560f, minX = 1475f, maxX = 1665f, speed = 84f, eliteChariot = true),
                    ),
                )
                families.addAll(
                    listOf(
                        HebrewFamilySpot(31, x = 440f),
                        HebrewFamilySpot(32, x = 900f),
                        HebrewFamilySpot(33, x = 1220f),
                        HebrewFamilySpot(34, x = 1520f),
                        HebrewFamilySpot(35, x = 1740f),
                    ),
                )
            }

            5 -> {
                // Сцена 5: Пустыня Син, Скала в Мериве (Исх. 17:6) и Молитва на холме в Рефидиме с Аароном и Ором (Исх. 17:12)
                wells.addAll(
                    listOf(
                        DesertWell(x = 220f, height = 50f, isSecretOasis = true),
                        DesertWell(x = 560f, height = 58f, hasSpringWater = true),
                        DesertWell(x = 960f, height = 52f, isSecretOasis = true),
                        DesertWell(x = 1320f, height = 58f, hasSpringWater = true),
                    ),
                )
                rockPuzzles.addAll(
                    listOf(
                        MeribahRockPuzzle(id = 51, rockX = 370f, raftX = 435f, targetHeight = 84f),
                        MeribahRockPuzzle(id = 52, rockX = 1090f, raftX = 1155f, targetHeight = 88f),
                    ),
                )
                blocks.addAll(
                    listOf(
                        OverheadBlock(41, x = 155f, y = 68f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 8),
                        OverheadBlock(42, x = 450f, y = 136f, kind = MosesBlockKind.QUESTION_HEART),
                        OverheadBlock(43, x = 760f, y = 74f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                        OverheadBlock(44, x = 1170f, y = 140f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 9),
                        OverheadBlock(45, x = 1450f, y = 86f, width = 62f, height = 20f, kind = MosesBlockKind.SINAI_STEP),
                    ),
                )
                soldiers.addAll(
                    listOf(
                        PharaohSoldier(41, x = 310f, minX = 265f, maxX = 355f, speed = 64f),
                        PharaohSoldier(42, x = 780f, minX = 680f, maxX = 910f, speed = 74f),
                        PharaohSoldier(43, x = 1420f, minX = 1360f, maxX = 1530f, speed = 76f),
                    ),
                )
                families.addAll(
                    listOf(
                        HebrewFamilySpot(41, x = 510f),
                        HebrewFamilySpot(42, x = 840f),
                        HebrewFamilySpot(43, x = 1260f),
                        HebrewFamilySpot(44, x = 1560f),
                    ),
                )
            }

            else -> {
                // Сцена 6: Восхождение на Гору Синай и Две Скрижали 10 Заповедей
                wells.addAll(
                    listOf(
                        DesertWell(x = 220f, height = 52f, isSecretOasis = true),
                        DesertWell(x = 580f, height = 60f, hasSpringWater = true),
                        DesertWell(x = 1020f, height = 56f, isSecretOasis = true),
                        DesertWell(x = 1420f, height = 64f, hasSpringWater = true),
                    ),
                )
                rockPuzzles.addAll(
                    listOf(
                        MeribahRockPuzzle(id = 61, rockX = 360f, raftX = 425f, targetHeight = 82f),
                        MeribahRockPuzzle(id = 62, rockX = 1160f, raftX = 1225f, targetHeight = 90f),
                    ),
                )
                blocks.addAll(
                    listOf(
                        OverheadBlock(51, x = 150f, y = 66f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 10),
                        OverheadBlock(52, x = 505f, y = 92f, width = 56f, height = 20f, kind = MosesBlockKind.SINAI_STEP),
                        OverheadBlock(53, x = 670f, y = 76f, width = 56f, height = 20f, kind = MosesBlockKind.SINAI_STEP),
                        OverheadBlock(54, x = 750f, y = 114f, width = 62f, height = 20f, kind = MosesBlockKind.SINAI_STEP),
                        OverheadBlock(55, x = 765f, y = 166f, kind = MosesBlockKind.QUESTION_VERSE, verseIndex = 11),
                        OverheadBlock(56, x = 890f, y = 74f, kind = MosesBlockKind.QUESTION_STAFF_GLOW),
                        OverheadBlock(57, x = 1310f, y = 96f, width = 60f, height = 20f, kind = MosesBlockKind.SINAI_STEP),
                        OverheadBlock(58, x = 1520f, y = 82f, width = 62f, height = 20f, kind = MosesBlockKind.SINAI_STEP),
                        OverheadBlock(59, x = 1615f, y = 118f, width = 66f, height = 20f, kind = MosesBlockKind.SINAI_STEP),
                        OverheadBlock(60, x = 1630f, y = 172f, kind = MosesBlockKind.QUESTION_HEART),
                    ),
                )
                soldiers.addAll(
                    listOf(
                        PharaohSoldier(51, x = 310f, minX = 275f, maxX = 350f, speed = 64f),
                        PharaohSoldier(52, x = 860f, minX = 760f, maxX = 990f, speed = 74f),
                        PharaohSoldier(53, x = 1350f, minX = 1295f, maxX = 1400f, speed = 74f),
                    ),
                )
                families.addAll(
                    listOf(
                        HebrewFamilySpot(51, x = 540f),
                        HebrewFamilySpot(52, x = 950f),
                        HebrewFamilySpot(53, x = 1380f),
                        HebrewFamilySpot(54, x = 1720f),
                    ),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        buildStage(targetStage = 1, resetTotalScore = true)
    }

    fun completeCurrentSceneAfterFaithChoice() {
        val choice = sceneInfo.faithChoice
        faithChoiceAnswered = true
        showFaithChoiceDialog = false
        bannerText = choice.miracleBanner
        score += 100
        if (score > bestScore) {
            bestScore = score
            prefs.edit().putInt("best_score", bestScore).apply()
        }
        if (stage == 4) {
            seaPartingProgress = 1f
        }
        if (stage < 6) {
            stageVictory = true
            winRound++
            KidsGameAudio.play(KidsSfx.WIN)
        } else {
            // В 6-й сцене после ответа веры открываем Скрижали 10 Заповедей, а также засчитываем победу!
            stageVictory = true
            fullExodusComplete = true
            showCommandmentsModal = true
            winRound++
            KidsGameAudio.play(KidsSfx.WIN)
        }
    }

    fun openFaithChoiceForCurrentScene() {
        if (!showFaithChoiceDialog && !faithChoiceAnswered) {
            selectedFaithOptionIndex = null
            showFaithChoiceDialog = true
            speak(sceneInfo.faithChoice.ttsText)
            KidsGameAudio.play(KidsSfx.CORRECT)
        }
    }

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

    // Контекстное действие уникальной механики каждой из 6 сцен
    fun triggerSceneAction() {
        if (inUndergroundOasis) {
            enterOrExitOasis()
            return
        }
        staffWaveTimer = 0.55f
        staffWaveX = mosesX + (if (facingRight) 28f else -28f)
        KidsGameAudio.play(KidsSfx.TAP)

        // 1. Проверяем скалы Меривы (Исход 17:6)
        var activatedAnyRock = false
        for (i in rockPuzzles.indices) {
            val rp = rockPuzzles[i]
            if (!rp.activated && abs(mosesX - rp.rockX) < 68f) {
                rockPuzzles[i] = rp.copy(activated = true)
                activatedAnyRock = true
                score += 35
                popups.add(FloatingPopup(rp.rockX, 58f, "💧 Вода из скалы! +35", Color(0xFF38BDF8)))
                bannerText = "🪄 Исход 17:6 — Из скалы забила живая вода и подняла мост!"
                KidsGameAudio.play(KidsSfx.WIN)
            }
        }

        // 2. Уникальные действия по номерам сцен (1..6)
        when (stage) {
            1 -> {
                // Сцена 1: укрытие корзинки в камышах Нила и призыв Мариам
                staffPowerTimer = max(staffPowerTimer, 4.5f)
                popups.add(FloatingPopup(mosesX, mosesY + 42f, "🌿 Укрыт в камышах!", Color(0xFF34D399)))
                bannerText = "🌿 Корзинка укрыта в тихих камышах под присмотром Мариам!"
            }

            2 -> {
                // Сцена 2: снятие обуви на святой земле у Неопалимой купины
                if (mosesX > levelGoalX(2) - 320f || mosesX < 180f) {
                    sandalsRemovedAtBush = true
                    staffPowerTimer = max(staffPowerTimer, 6.0f)
                    popups.add(FloatingPopup(mosesX, mosesY + 46f, "🩴 Земля святая! (Исх. 3:5)", Color(0xFFFDE047)))
                    bannerText = "🔥 Моисей снял обувь на святой земле у Неопалимой купины!"
                    KidsGameAudio.play(KidsSfx.WIN)
                    if (mosesX > levelGoalX(2) - 260f) {
                        openFaithChoiceForCurrentScene()
                    }
                }
            }

            3 -> {
                // Сцена 3: помазание косяков и перекладин дверей пучком иссопа (Исход 12:13, 22)
                var markedAnyDoor = false
                for (i in families.indices) {
                    val f = families[i]
                    if (!f.rescued && abs(mosesX - f.x) < 62f) {
                        families[i] = f.copy(rescued = true)
                        markedAnyDoor = true
                        score += 40
                        val markedCount = families.count { it.rescued }
                        popups.add(
                            FloatingPopup(
                                f.x,
                                52f,
                                "🌿 Кровь Агнца на двери! ($markedCount/${families.size})",
                                Color(0xFFF87171),
                            ),
                        )
                        bannerText = "🌿 Дом $markedCount из ${families.size} защищён знаком Пасхального Агнца (Исход 12:13)!"
                        KidsGameAudio.play(KidsSfx.WIN)
                    }
                }
                if (!markedAnyDoor) {
                    popups.add(FloatingPopup(mosesX, mosesY + 42f, "🌿 Подойди к дому Израиля!", Color(0xFFFDE047)))
                }
            }

            4 -> {
                // Сцена 4: у берега моря простираем жезл и открываем диалог веры (Исход 14:13-16)
                if (mosesX > levelGoalX(4) - 340f) {
                    seaPartingProgress = 1f
                    openFaithChoiceForCurrentScene()
                } else {
                    pillarProtectiveMode = !pillarProtectiveMode
                    popups.add(
                        FloatingPopup(
                            mosesX,
                            mosesY + 48f,
                            if (pillarProtectiveMode) "🔥 Столп стал щитом!" else "☁️ Столп ведёт вперёд!",
                            Color(0xFFF97316),
                        ),
                    )
                }
            }

            5 -> {
                // Сцена 5: на холме в Рефидиме Аарон и Ор поддерживают руки Моисея в молитве!
                rephidimHandsLevel = (rephidimHandsLevel + 0.32f).coerceAtMost(1f)
                rephidimVictoryProgress = (rephidimVictoryProgress + 0.22f).coerceAtMost(1f)
                popups.add(
                    FloatingPopup(
                        mosesX,
                        mosesY + 52f,
                        "🙌 Аарон и Ор держат руки! ${(rephidimVictoryProgress * 100).roundToInt()}%",
                        Color(0xFFFDE047),
                    ),
                )
                bannerText = "🙌 Исход 17:12 — Аарон и Ор поддерживают руки Моисея до захода солнца!"
                if (rephidimVictoryProgress >= 1f && mosesX > levelGoalX(5) - 360f) {
                    openFaithChoiceForCurrentScene()
                }
            }

            6 -> {
                // Сцена 6: открываем интерактивные Скрижали 10 Заповедей
                showCommandmentsModal = true
                speak("Десять Заповедей Божьих на двух каменных скрижалях Синайского Завета.")
            }
        }

        // Отпор патрулю светом посоха
        for (i in soldiers.indices) {
            val s = soldiers[i]
            if (!s.defeated && abs(s.x - staffWaveX) < 54f && abs(mosesY) < 55f) {
                soldiers[i] = s.copy(defeated = true)
                score += 20
                popups.add(
                    FloatingPopup(
                        s.x,
                        46f,
                        if (s.isNileCrocodile) "🌿 Крокодил уплыл! +20" else "✨ Свет Божий! +20",
                        Color(0xFFFEF08A),
                    ),
                )
                KidsGameAudio.play(KidsSfx.CORRECT)
            }
        }

        if (!activatedAnyRock && standingOnSecretWellIndex >= 0) {
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
        if (!running || showFaithChoiceDialog || showCommandmentsModal || showVersesTreasury || activeVerseCard != null) return

        if (mosesY <= 2f) {
            mosesVy = 435f
            airJumpUsed = false
            KidsGameAudio.play(KidsSfx.TAP)
        } else if (!airJumpUsed || staffPowerTimer > 0f) {
            mosesVy = 405f
            airJumpUsed = true
            KidsGameAudio.play(KidsSfx.TAP)
        }
    }

    // Главный игровой цикл
    val modalPaused = showFaithChoiceDialog || showCommandmentsModal || showVersesTreasury || activeVerseCard != null
    LaunchedEffect(running, gameOver, stageVictory, stage, inUndergroundOasis, modalPaused) {
        if (!running || gameOver || stageVictory || modalPaused) return@LaunchedEffect
        var lastNanos = 0L
        while (running && !gameOver && !stageVictory && !modalPaused) {
            val now = withFrameNanos { it }
            if (lastNanos == 0L) {
                lastNanos = now
                continue
            }
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastNanos = now
            animTime += dt

            if (hurtCooldown > 0f) hurtCooldown = max(0f, hurtCooldown - dt)
            if (staffPowerTimer > 0f) staffPowerTimer = max(0f, staffPowerTimer - dt)
            if (staffWaveTimer > 0f) staffWaveTimer = max(0f, staffWaveTimer - dt)
            if (pillarShieldFlash > 0f) pillarShieldFlash = max(0f, pillarShieldFlash - dt)

            // В Сцене 5 (Рефидим) руки Моисея постепенно тяжелеют, пока Аарон и Ор их поддерживают
            if (stage == 5 && !inUndergroundOasis) {
                rephidimHandsLevel = (rephidimHandsLevel - dt * 0.055f).coerceIn(0.18f, 1f)
                if (mosesX > levelGoalX(5) - 340f && rephidimHandsLevel > 0.55f) {
                    rephidimVictoryProgress = (rephidimVictoryProgress + dt * 0.18f).coerceAtMost(1f)
                }
            }

            // Горизонтальное движение
            val baseSpeed = when {
                staffPowerTimer > 0f -> 208f
                !pillarProtectiveMode -> 182f
                else -> 165f
            }
            val prevX = mosesX
            if (moveLeftHeld && !moveRightHeld) {
                facingRight = false
                mosesX = max(20f, mosesX - baseSpeed * dt)
            } else if (moveRightHeld && !moveLeftHeld) {
                facingRight = true
                val maxAllowedX = if (inUndergroundOasis) 345f else levelGoalX(stage) + 40f
                mosesX = min(maxAllowedX, mosesX + baseSpeed * dt)
            }

            // Режим подземного оазиса внутри колодца
            if (inUndergroundOasis) {
                val prevY = mosesY
                mosesY += mosesVy * dt
                mosesVy -= 920f * dt
                if (mosesY <= 0f) {
                    mosesY = 0f
                    mosesVy = 0f
                    airJumpUsed = false
                }
                for (i in oasisTreasures.indices) {
                    val t = oasisTreasures[i]
                    if (!t.collected && abs(mosesX - t.x) < 24f && abs(mosesY - t.y) < 28f) {
                        oasisTreasures[i] = t.copy(collected = true)
                        if (t.isPsalmScroll) {
                            score += 50
                            hearts = min(5, hearts + 1)
                            val verse = EXODUS_VERSES[(stage * 2 - 1).coerceIn(0, EXODUS_VERSES.lastIndex)]
                            activeVerseCard = verse
                            speak(verse.ttsText)
                            popups.add(FloatingPopup(t.x, t.y + 20f, "📜 Свиток Исхода! +50 ❤️", Color(0xFFFEF08A)))
                            KidsGameAudio.play(KidsSfx.WIN)
                        } else {
                            score += 20
                            popups.add(FloatingPopup(t.x, t.y + 18f, "🍯 Манна +20", Color(0xFF34D399)))
                            KidsGameAudio.play(KidsSfx.CORRECT)
                        }
                    }
                }
                if (mosesX >= 312f && prevY >= 0f) {
                    enterOrExitOasis()
                }
                continue
            }

            // Блокируем проход сквозь боковые стенки колодцев
            for (well in wells) {
                val wellHalfW = well.width / 2f + 10f
                if (mosesY < well.height - 6f) {
                    if (prevX <= well.x - wellHalfW && mosesX > well.x - wellHalfW) {
                        mosesX = well.x - wellHalfW
                    } else if (prevX >= well.x + wellHalfW && mosesX < well.x + wellHalfW) {
                        mosesX = well.x + wellHalfW
                    }
                }
            }

            // Анимация подъёма плотов над Скалами Меривы
            for (i in rockPuzzles.indices) {
                val rp = rockPuzzles[i]
                if (rp.activated && rp.currentRaftY < rp.targetHeight) {
                    val nextY = min(rp.targetHeight, rp.currentRaftY + 75f * dt)
                    rockPuzzles[i] = rp.copy(currentRaftY = nextY)
                }
            }

            // Вертикальная физика и приземление на колодцы, плоты Меривы и блоки
            val prevY = mosesY
            val nextY = mosesY + mosesVy * dt
            var landedSurfaceY = 0f
            var currentSecretWellIdx = -1

            for (wIdx in wells.indices) {
                val well = wells[wIdx]
                val wellHalfW = well.width / 2f + 8f
                if (abs(mosesX - well.x) <= wellHalfW && prevY >= well.height - 8f && mosesVy <= 0f) {
                    landedSurfaceY = max(landedSurfaceY, well.height)
                    if (well.isSecretOasis) {
                        currentSecretWellIdx = wIdx
                    }
                }
            }
            standingOnSecretWellIndex = currentSecretWellIdx

            for (rp in rockPuzzles) {
                val raftTop = rp.currentRaftY + 12f
                if (mosesX >= rp.raftX - 8f && mosesX <= rp.raftX + rp.raftWidth + 8f) {
                    if (prevY >= raftTop - 10f && mosesVy <= 0f) {
                        landedSurfaceY = max(landedSurfaceY, raftTop)
                    }
                }
            }

            for (i in blocks.indices) {
                val b = blocks[i]
                val blockTop = b.y + b.height
                val inX = mosesX >= b.x - 10f && mosesX <= b.x + b.width + 10f
                if (inX && prevY >= blockTop - 8f && mosesVy <= 0f) {
                    landedSurfaceY = max(landedSurfaceY, blockTop)
                }
                val headTopPrev = prevY + 42f
                val headTopNext = nextY + 42f
                if (inX && mosesVy > 0f && headTopPrev <= b.y + 6f && headTopNext >= b.y) {
                    mosesVy = -60f
                    if (!b.used && b.kind != MosesBlockKind.SINAI_STEP) {
                        val updatedKindUsed = b.kind != MosesBlockKind.BRICK
                        blocks[i] = b.copy(used = updatedKindUsed, bumpAnim = 1f)
                        when (b.kind) {
                            MosesBlockKind.QUESTION_VERSE -> {
                                score += 30
                                val verse = EXODUS_VERSES[b.verseIndex % EXODUS_VERSES.size]
                                activeVerseCard = verse
                                bannerText = "📜 ${verse.ref}: ${verse.text}"
                                speak(verse.ttsText)
                                popups.add(FloatingPopup(b.x, b.y + 32f, "📜 ${verse.ref} +30", Color(0xFFFDE047)))
                                KidsGameAudio.play(KidsSfx.WIN)
                            }
                            MosesBlockKind.QUESTION_HEART -> {
                                hearts = min(5, hearts + 1)
                                score += 20
                                popups.add(FloatingPopup(b.x, b.y + 32f, "❤️ Манна небесная!", Color(0xFFF87171)))
                                KidsGameAudio.play(KidsSfx.CORRECT)
                            }
                            MosesBlockKind.QUESTION_STAFF_GLOW -> {
                                staffPowerTimer = 6.5f
                                score += 40
                                popups.add(
                                    FloatingPopup(
                                        b.x,
                                        b.y + 32f,
                                        if (stage == 1) "🌿 Божья охрана на Ниле!" else "🪄 Сияние Посоха Божьего!",
                                        Color(0xFF38BDF8),
                                    ),
                                )
                                bannerText = if (stage == 1) {
                                    "🌿 Ангел Господень хранит корзинку Моисея на водах Нила!"
                                } else {
                                    "🪄 Сияние Посоха Божьего! Моисей защищён!"
                                }
                                KidsGameAudio.play(KidsSfx.WIN)
                            }
                            MosesBlockKind.BRICK -> {
                                score += 5
                                KidsGameAudio.play(KidsSfx.TAP)
                            }
                            MosesBlockKind.SINAI_STEP -> Unit
                        }
                    } else {
                        blocks[i] = b.copy(bumpAnim = 1f)
                        KidsGameAudio.play(KidsSfx.TAP)
                    }
                }
            }

            if (nextY <= landedSurfaceY && mosesVy <= 0f) {
                mosesY = landedSurfaceY
                mosesVy = 0f
                airJumpUsed = false
            } else {
                mosesY = nextY
                mosesVy -= 960f * dt
            }

            for (i in blocks.indices) {
                val b = blocks[i]
                if (b.bumpAnim > 0f) {
                    blocks[i] = b.copy(bumpAnim = max(0f, b.bumpAnim - dt * 5f))
                }
            }

            // Дома народа Божьего / лилии Нила / омеры манны
            for (i in families.indices) {
                val f = families[i]
                if (!f.rescued && abs(mosesX - f.x) < 28f && mosesY < 48f) {
                    families[i] = f.copy(rescued = true)
                    score += 35
                    val rescuedCount = families.count { it.rescued }
                    val label = when (stage) {
                        1 -> "🌸 Лилия Нила! ($rescuedCount/${families.size})"
                        3 -> "🌿 Дверь помазана кровью Агнца! ($rescuedCount/${families.size})"
                        5 -> "🍯 Омер манны собран! ($rescuedCount/${families.size})"
                        else -> "🙌 Семья спасена! +35"
                    }
                    popups.add(FloatingPopup(f.x, 48f, label, Color(0xFF34D399)))
                    bannerText = when (stage) {
                        3 -> "🌿 Дом $rescuedCount из ${families.size} защищён знаком Пасхального Агнца (Исход 12:13)!"
                        else -> "Собрано семей и знамений: $rescuedCount из ${families.size}!"
                    }
                    KidsGameAudio.play(KidsSfx.CORRECT)
                }
            }

            // Патрули: крокодилы Нила, воины и колесницы фараона
            for (i in soldiers.indices) {
                val s = soldiers[i]
                if (s.defeated) {
                    if (s.defeatedY < 120f) {
                        soldiers[i] = s.copy(defeatedY = s.defeatedY + 220f * dt)
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

                val dx = abs(mosesX - nx)
                if (dx < 23f) {
                    val stompFromAbove = mosesVy < -40f && prevY >= 24f && mosesY <= 38f
                    if (stompFromAbove || staffPowerTimer > 0f) {
                        soldiers[i] = s.copy(defeated = true, x = nx)
                        if (stompFromAbove) mosesVy = 340f
                        score += 25
                        popups.add(
                            FloatingPopup(
                                nx,
                                42f,
                                if (s.isNileCrocodile) "🌿 Крокодил отступил! +25" else "✨ Стражник отступил! +25",
                                Color(0xFFFBBF24),
                            ),
                        )
                        KidsGameAudio.play(KidsSfx.CORRECT)
                    } else if (mosesY < 26f && hurtCooldown <= 0f) {
                        if (pillarProtectiveMode && pillarShieldFlash <= 0f) {
                            soldiers[i] = s.copy(defeated = true, x = nx)
                            pillarShieldFlash = 1.2f
                            popups.add(FloatingPopup(nx, 48f, "☁️🔥 Щит Столпа Божьего!", Color(0xFF38BDF8)))
                            bannerText = "☁️🔥 Столп Божий встал между станом Египетским и станом Израильским (Исх. 14:20)!"
                            KidsGameAudio.play(KidsSfx.WIN)
                        } else {
                            hearts -= 1
                            hurtCooldown = 1.5f
                            mosesVy = 260f
                            KidsGameAudio.play(KidsSfx.WRONG)
                            if (hearts <= 0) {
                                gameOver = true
                                running = false
                                if (score > bestScore) {
                                    bestScore = score
                                    prefs.edit().putInt("best_score", bestScore).apply()
                                }
                            }
                        }
                    }
                }
            }

            // Анимация всплывающих подсказок
            val iter = popups.listIterator()
            while (iter.hasNext()) {
                val p = iter.next()
                val na = p.alpha - dt * 0.72f
                if (na <= 0f) {
                    iter.remove()
                } else {
                    iter.set(p.copy(y = p.y + 28f * dt, alpha = na))
                }
            }

            // В Сцене 4 Чермное море начинает расступаться при приближении
            val goalX = levelGoalX(stage)
            if (stage == 4 && mosesX > goalX - 280f) {
                val targetParting = ((mosesX - (goalX - 280f)) / 180f).coerceIn(0f, 1f)
                seaPartingProgress = max(seaPartingProgress, targetParting)
            }

            // Камера плавно следует за Моисеем
            val targetCam = (mosesX - worldViewportW * 0.30f).coerceIn(0f, max(0f, goalX + 130f - worldViewportW))
            cameraX += (targetCam - cameraX) * min(1f, dt * 8f)

            // Достижение кульминации сцены -> открываем «Диалог выбора верой»!
            if (mosesX >= goalX - 30f) {
                if (!faithChoiceAnswered) {
                    openFaithChoiceForCurrentScene()
                } else {
                    completeCurrentSceneAfterFaithChoice()
                }
            }
        }
    }

    KidsGameMusic(KidsMusicTrack.FAITH)
    KidsCelebrateWinEffect(gameKey = KidsGames.MOSES, round = winRound, points = 30)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0F172A),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 1. Полноэкранный холст активной сцены (1..6)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val groundY = h * 0.80f
                val u = (h / 250f).coerceIn(1f, 3.6f)
                worldViewportW = (w / u).coerceAtLeast(320f)

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

                // Небо текущей сцены
                val isNightPassoverOrSea = stage == 3 || stage == 4
                drawRect(
                    brush = Brush.verticalGradient(sceneInfo.skyColors),
                    size = size,
                )

                // Солнце или луна и звёзды
                if (isNightPassoverOrSea || stage == 6) {
                    drawCircle(
                        color = Color(0xFFFEF08A),
                        radius = 20f * u,
                        center = Offset(w * 0.82f, h * 0.17f),
                    )
                    for (sIdx in 0..8) {
                        val sx = ((sIdx * 137) % 100) / 100f * w
                        val sy = ((sIdx * 73) % 40) / 100f * h + 14f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.45f + 0.4f * abs(sin(animTime * 2f + sIdx))),
                            radius = 2f * u,
                            center = Offset(sx, sy),
                        )
                    }
                } else {
                    drawCircle(
                        color = Color(0xFFFEF08A),
                        radius = 24f * u,
                        center = Offset(w * 0.82f, h * 0.17f),
                    )
                }

                // Силуэты пирамид Египта, гор Хорива/Синая или камышовых берегов Нила
                for (i in 0..4) {
                    val px = ((i * 420f) - cameraX * 0.35f) * u
                    val pyrPath = Path().apply {
                        moveTo(px, groundY)
                        lineTo(px + 95f * u, groundY - (if (stage >= 5) 105f else 75f) * u)
                        lineTo(px + 190f * u, groundY)
                        close()
                    }
                    drawPath(
                        path = pyrPath,
                        color = when (stage) {
                            1 -> Color(0xFF15803D).copy(alpha = 0.28f)
                            3, 4 -> Color(0xFF312E81).copy(alpha = 0.45f)
                            5, 6 -> Color(0xFF44403C).copy(alpha = 0.42f)
                            else -> Color(0xFFD97706).copy(alpha = 0.28f)
                        },
                    )
                }

                // В Сцене 1 рисуем берег Нила и бегущую вдоль берега сестру Мариам!
                if (stage == 1) {
                    drawRect(
                        color = Color(0xFF15803D).copy(alpha = 0.65f),
                        topLeft = Offset(0f, groundY - 16f * u),
                        size = Size(w, 16f * u),
                    )
                    val miriamScreenX = ((mosesX - 24f - cameraX) * u).coerceIn(24f * u, w - 40f * u)
                    drawMiriamSister(
                        x = miriamScreenX,
                        bankY = groundY - 12f * u,
                        u = u,
                        animTime = animTime,
                    )
                }

                // В Сцене 2 на старте и у финиша горит Неопалимая купина
                if (stage == 2) {
                    val bushStartX = (88f - cameraX) * u
                    if (bushStartX > -60f * u && bushStartX < w + 60f * u) {
                        drawBurningBush(x = bushStartX, groundY = groundY, u = u, animTime = animTime)
                    }
                }

                // Облачный и Огненный столп (Исход 13:21, 14:19) в сценах 3, 4, 5, 6
                if (stage >= 3) {
                    drawGuidingPillar(
                        mosesScreenX = (mosesX - cameraX) * u,
                        groundY = groundY,
                        u = u,
                        isFirePillar = isNightPassoverOrSea,
                        protectiveMode = pillarProtectiveMode,
                        shieldFlash = pillarShieldFlash,
                        animTime = animTime,
                    )
                }

                // Земля или река Нил (в Сцене 1)
                drawRect(
                    color = sceneInfo.groundColor,
                    topLeft = Offset(0f, groundY),
                    size = Size(w, h - groundY),
                )
                drawLine(
                    color = if (stage == 1) Color(0xFF38BDF8) else Color(0xFF78350F),
                    start = Offset(0f, groundY),
                    end = Offset(w, groundY),
                    strokeWidth = 4f * u,
                )

                // Скалы Меривы и поднимающиеся на воде плоты
                for (rp in rockPuzzles) {
                    val rx = (rp.rockX - cameraX) * u
                    val rfx = (rp.raftX - cameraX) * u
                    if (rfx + rp.raftWidth * u > -40f && rx < w + 60f) {
                        drawMeribahRockAndRaft(
                            rockX = rx,
                            raftX = rfx,
                            raftW = rp.raftWidth * u,
                            raftY = rp.currentRaftY * u,
                            groundY = groundY,
                            u = u,
                            activated = rp.activated,
                            animTime = animTime,
                        )
                    }
                }

                // Дома семей Израиля (в Сцене 3 с дверными косяками для крови Агнца) / лилии Нила / омеры манны
                for (f in families) {
                    val fx = (f.x - cameraX) * u
                    if (fx > -50f * u && fx < w + 50f * u) {
                        when (stage) {
                            1 -> drawNileLilySpot(x = fx, groundY = groundY, u = u, collected = f.rescued, animTime = animTime)
                            5 -> drawMannaOmerSpot(x = fx, groundY = groundY, u = u, collected = f.rescued, animTime = animTime)
                            else -> drawHebrewHouse(
                                x = fx,
                                groundY = groundY,
                                u = u,
                                rescued = f.rescued,
                                showPassoverBloodMarks = stage == 3 && f.rescued,
                            )
                        }
                    }
                }

                // Колодцы (или камышовые островки в Сцене 1)
                for (well in wells) {
                    val wx = (well.x - cameraX - well.width / 2f) * u
                    if (wx + well.width * u > -20f && wx < w + 20f) {
                        if (stage == 1) {
                            drawPapyrusReedIsland(
                                x = wx,
                                groundY = groundY,
                                w = well.width * u,
                                h = well.height * u,
                                u = u,
                                animTime = animTime,
                            )
                        } else {
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
                }

                // Висящие блоки
                for (b in blocks) {
                    val bx = (b.x - cameraX) * u
                    if (bx + b.width * u > -20f && bx < w + 20f) {
                        val bumpOffset = b.bumpAnim * 7f * u
                        val by = groundY - (b.y + b.height) * u - bumpOffset
                        drawMarioStyleBlock(
                            x = bx,
                            y = by,
                            w = b.width * u,
                            h = b.height * u,
                            u = u,
                            kind = b.kind,
                            used = b.used,
                        )
                    }
                }

                // Финишный памятник каждой из 6 сцен
                val gx = (levelGoalX(stage) - cameraX) * u
                if (gx > -120f * u && gx < w + 120f * u) {
                    drawStageGoalMonument(
                        stage = stage,
                        x = gx,
                        groundY = groundY,
                        u = u,
                        seaParting = seaPartingProgress,
                        rephidimHandsLevel = rephidimHandsLevel,
                        animTime = animTime,
                    )
                }

                // Патрули: крокодилы Нила или воины фараона
                for (s in soldiers) {
                    val sx = (s.x - cameraX) * u
                    if (sx > -40f * u && sx < w + 40f * u) {
                        if (s.isNileCrocodile) {
                            drawNileCrocodile(
                                x = sx,
                                groundY = groundY + s.defeatedY * u,
                                u = u,
                                dir = s.dir,
                                animTime = animTime,
                            )
                        } else {
                            drawPharaohSoldier(
                                x = sx,
                                groundY = groundY + s.defeatedY * u,
                                u = u,
                                dir = s.dir,
                                elite = s.eliteChariot,
                                defeated = s.defeated,
                                animTime = animTime,
                            )
                        }
                    }
                }

                // Спасённые семьи народа Божьего, идущие за Моисеем (в сценах 2, 3, 4, 6)
                if (stage != 1) {
                    val rescuedCount = families.count { it.rescued }
                    for (idx in 0 until min(rescuedCount, 6)) {
                        val followerWorldX = mosesX - (idx + 1) * 20f * (if (facingRight) 1f else -1f)
                        val fx = (followerWorldX - cameraX) * u
                        val bounce = abs(sin(animTime * 7f + idx)) * 4f * u
                        drawFollowerPerson(
                            x = fx,
                            groundY = groundY - bounce,
                            u = u,
                            index = idx,
                        )
                    }
                }

                // Сам герой сцены: в Сцене 1 — тростниковая корзинка с младенцем Моисеем, в Сценах 2..6 — пророк Моисей
                val mosesScreenX = (mosesX - cameraX) * u
                val mosesBottomY = groundY - mosesY * u
                val blinkHide = hurtCooldown > 0f && ((animTime * 16f).toInt() % 2 == 0)
                if (!blinkHide) {
                    if (stage == 1) {
                        drawNileReedBasket(
                            x = mosesScreenX,
                            bottomY = mosesBottomY,
                            u = u,
                            sheltered = staffPowerTimer > 0f,
                            animTime = animTime,
                        )
                    } else {
                        drawMosesHero(
                            x = mosesScreenX,
                            bottomY = mosesBottomY,
                            u = u,
                            facingRight = facingRight,
                            jumping = mosesY > 2f,
                            staffGlowing = staffPowerTimer > 0f || staffWaveTimer > 0f,
                            sandalsRemoved = sandalsRemovedAtBush,
                            animTime = animTime,
                        )
                    }
                }

                // Взмах посоха / пучка иссопа
                if (staffWaveTimer > 0f) {
                    val waveScreenX = (staffWaveX - cameraX) * u
                    drawCircle(
                        color = if (stage == 3) {
                            Color(0xFFF87171).copy(alpha = staffWaveTimer)
                        } else {
                            Color(0xFFFEF08A).copy(alpha = staffWaveTimer)
                        },
                        radius = (34f - staffWaveTimer * 25f) * u,
                        center = Offset(waveScreenX, mosesBottomY - 22f * u),
                        style = Stroke(width = 3.5f * u),
                    )
                }

                // Всплывающие круги событий
                for (p in popups) {
                    val px = (p.x - cameraX) * u
                    val py = groundY - p.y * u
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha * 0.85f),
                        radius = 12f * u,
                        center = Offset(px, py),
                    )
                }
            }

            // 2. Компактная верхняя панель управления
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Назад + текущая сцена
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.76f))
                            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        Text(
                            text = if (inUndergroundOasis) {
                                "🌴 Оазис · ❤️".repeat(1) + "❤️".repeat((hearts - 1).coerceAtLeast(0))
                            } else {
                                "${sceneInfo.emoji} Сцена $stage/6 · ${"❤️".repeat(hearts.coerceAtLeast(0))}"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }

                    // Быстрые кнопки: Выбор верой, Свитки Исхода и 10 Заповедей
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Кнопка «Диалог выбора верой» текущей сцены
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (faithChoiceAnswered) Color(0xFF059669) else Color(0xFFD97706),
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                                .clickable {
                                    selectedFaithOptionIndex = null
                                    showFaithChoiceDialog = true
                                    speak(sceneInfo.faithChoice.ttsText)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = if (faithChoiceAnswered) "✓ Выбор веры" else "💬 Выбор веры",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }

                        // Кнопка «10 Заповедей» (доступна всегда, особенно в Сцене 6)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF1E3A8A).copy(alpha = 0.90f))
                                .border(1.dp, Color(0xFF7DD3FC).copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                                .clickable {
                                    showCommandmentsModal = true
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "⛰️ 10 Заповедей",
                                color = Color(0xFFFEF08A),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }

                        // Сокровищница 12 стихов книги Исход
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF0F172A).copy(alpha = 0.78f))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                                .clickable { showVersesTreasury = true }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "📜 Свитки · ⭐ $score",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }

                        // Сброс и звук
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
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { buildStage(targetStage = stage, resetTotalScore = false) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Сначала",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            KidsAudioToggles()
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Горизонтальный переключатель всех 6 уникальных сцен книги Исход
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (sc in MOSES_SCENES) {
                        val selected = sc.number == stage
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (selected) Color(0xFFF59E0B) else Color(0xFF0F172A).copy(alpha = 0.72f),
                                )
                                .border(
                                    1.dp,
                                    if (selected) Color.White else Color.White.copy(alpha = 0.22f),
                                    RoundedCornerShape(999.dp),
                                )
                                .clickable { buildStage(targetStage = sc.number, resetTotalScore = false) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "${sc.emoji} ${sc.shortTitle}",
                                color = if (selected) Color(0xFF0F172A) else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Баннер подсказки текущей сцены + в Сцене 5 индикатор поднятых рук Моисея с Аароном и Ором
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val bText = bannerText
                    if (bText != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF0F172A).copy(alpha = 0.80f))
                                .border(1.dp, Color(0xFFFDE047).copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                                .clickable { speak(bText) }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Озвучить",
                                tint = Color(0xFFFDE047),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = bText,
                                color = Color(0xFFFEF08A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // 3. Модальное окно «Диалог выбора верой» перед ключевым чудом каждой из 6 сцен
            if (showFaithChoiceDialog) {
                val fc = sceneInfo.faithChoice
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.68f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                            .width(560.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFF0F172A))
                            .border(2.dp, Color(0xFFF59E0B), RoundedCornerShape(26.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${sceneInfo.emoji} ${fc.situationTitle}",
                                color = Color(0xFFFDE047),
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFF1E293B))
                                    .clickable { speak(fc.ttsText) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = "🔊 Слушать",
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = fc.situationDescription,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Выбери ответ веры по Писанию (${fc.speakerLabel}):",
                            color = Color(0xFF93C5FD),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(6.dp))

                        fc.options.forEachIndexed { idx, opt ->
                            val isSelected = selectedFaithOptionIndex == idx
                            val bg = when {
                                !isSelected -> Color(0xFF1E293B)
                                opt.isFaithAnswer -> Color(0xFF065F46)
                                else -> Color(0xFF7F1D1D)
                            }
                            val borderCol = when {
                                !isSelected -> Color.White.copy(alpha = 0.22f)
                                opt.isFaithAnswer -> Color(0xFF34D399)
                                else -> Color(0xFFF87171)
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(bg)
                                    .border(1.5.dp, borderCol, RoundedCornerShape(16.dp))
                                    .clickable {
                                        selectedFaithOptionIndex = idx
                                        if (opt.isFaithAnswer) {
                                            KidsGameAudio.play(KidsSfx.WIN)
                                            speak("${opt.feedback} ${fc.keyVerseText}")
                                        } else {
                                            KidsGameAudio.play(KidsSfx.WRONG)
                                            speak(opt.feedback)
                                        }
                                    }
                                    .padding(12.dp),
                            ) {
                                Text(
                                    text = opt.text,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp,
                                )
                                if (isSelected) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = opt.feedback,
                                        color = if (opt.isFaithAnswer) Color(0xFFA7F3D0) else Color(0xFFFECACA),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }

                        // Когда выбран верный ответ веры — открываем стих Писания и блок «💡 Глубина смысла»
                        val chosenOpt = selectedFaithOptionIndex?.let { fc.options.getOrNull(it) }
                        if (chosenOpt?.isFaithAnswer == true) {
                            Spacer(Modifier.height(10.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF1E1B4B))
                                    .border(1.dp, Color(0xFFFDE047).copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                            ) {
                                Text(
                                    text = "📖 ${fc.keyVerseRef}",
                                    color = Color(0xFFFDE047),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = fc.keyVerseText,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "💡 Глубина смысла: ${fc.deepMeaning}",
                                    color = Color(0xFFBAE6FD),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0xFF059669))
                                        .clickable { completeCurrentSceneAfterFaithChoice() }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = if (stage < 6) "Совершить чудо верой ▶" else "Открыть Скрижали 10 Заповедей ▶",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0xFF334155))
                                        .clickable { showFaithChoiceDialog = false }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                ) {
                                    Text(
                                        text = "Вернуться к сцене",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Модальное окно открытого золотого свитка со стихом из книги Исход
            val openedVerse = activeVerseCard
            if (openedVerse != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { activeVerseCard = null },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .width(500.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF0F172A))
                            .border(2.dp, Color(0xFFFDE047), RoundedCornerShape(24.dp))
                            .clickable(enabled = false) {}
                            .padding(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "📜 ${openedVerse.ref}",
                                color = Color(0xFFFDE047),
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFF1E293B))
                                    .clickable { speak(openedVerse.ttsText) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = "🔊 Озвучить",
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = openedVerse.text,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "💡 Глубина смысла: ${openedVerse.deepMeaning}",
                            color = Color(0xFFBAE6FD),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .align(Alignment.End)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF059669))
                                .clickable { activeVerseCard = null }
                                .padding(horizontal = 18.dp, vertical = 8.dp),
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

            // 5. Интерактивная мастерская «Две Каменные Скрижали 10 Заповедей» (Исход 20:2–17)
            if (showCommandmentsModal) {
                val currentCmd = TEN_COMMANDMENTS_LIST[activeCommandmentQuizIndex.coerceIn(0, TEN_COMMANDMENTS_LIST.lastIndex)]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .width(600.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFF0F172A))
                            .border(2.dp, Color(0xFF38BDF8), RoundedCornerShape(26.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "⛰️ Скрижали Завета: 10 Заповедей (${masteredCommandments.size}/10)",
                                color = Color(0xFFFDE047),
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFF334155))
                                    .clickable { showCommandmentsModal = false }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = "Закрыть ✕",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Полоска выбора заповеди I..X
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TEN_COMMANDMENTS_LIST.forEachIndexed { index, cmd ->
                                val isCurr = index == activeCommandmentQuizIndex
                                val isDone = cmd.number in masteredCommandments
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            when {
                                                isCurr -> Color(0xFFF59E0B)
                                                isDone -> Color(0xFF059669)
                                                else -> Color(0xFF1E293B)
                                            },
                                        )
                                        .clickable {
                                            activeCommandmentQuizIndex = index
                                            commandmentQuizFeedback = null
                                            speak(cmd.ttsText)
                                        }
                                        .padding(horizontal = 11.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        text = "${cmd.roman} (${cmd.number})",
                                        color = if (isCurr) Color(0xFF0F172A) else Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Карточка выбранной заповеди
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF7DD3FC).copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                                .padding(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Заповедь ${currentCmd.roman} (${currentCmd.number}-я заповедь)",
                                    color = Color(0xFFFDE047),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0xFF0F172A))
                                        .clickable { speak(currentCmd.ttsText) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = "🔊 Озвучить",
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = currentCmd.fullScriptureText,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "💡 ${currentCmd.childMeaning}",
                                color = Color(0xFFBAE6FD),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "🧠 На какую из двух Каменных Скрижалей поместить эту заповедь?",
                            color = Color(0xFFFEF08A),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF1E3A8A))
                                    .border(1.5.dp, Color(0xFF60A5FA), RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (currentCmd.tabletNumber == 1) {
                                            if (currentCmd.number !in masteredCommandments) {
                                                masteredCommandments.add(currentCmd.number)
                                                score += 25
                                            }
                                            commandmentQuizFeedback = "✓ Верно! Заповедь ${currentCmd.roman} — на Первой скрижали (Любовь к Богу)!"
                                            KidsGameAudio.play(KidsSfx.WIN)
                                            if (activeCommandmentQuizIndex < 9) activeCommandmentQuizIndex++
                                        } else {
                                            commandmentQuizFeedback = "Подумай: заповеди 5–10 учат любви к родителям и ближнему (Вторая скрижаль)!"
                                            KidsGameAudio.play(KidsSfx.WRONG)
                                        }
                                    }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "📜 Скрижаль I\nЛюбовь к Богу (I–IV)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF065F46))
                                    .border(1.5.dp, Color(0xFF34D399), RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (currentCmd.tabletNumber == 2) {
                                            if (currentCmd.number !in masteredCommandments) {
                                                masteredCommandments.add(currentCmd.number)
                                                score += 25
                                            }
                                            commandmentQuizFeedback = "✓ Верно! Заповедь ${currentCmd.roman} — на Второй скрижали (Любовь к ближнему)!"
                                            KidsGameAudio.play(KidsSfx.WIN)
                                            if (activeCommandmentQuizIndex < 9) activeCommandmentQuizIndex++
                                        } else {
                                            commandmentQuizFeedback = "Подумай: первые 4 заповеди посвящены любви к Господу Богу (Первая скрижаль)!"
                                            KidsGameAudio.play(KidsSfx.WRONG)
                                        }
                                    }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "📜 Скрижаль II\nЛюбовь к ближнему (V–X)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        val fb = commandmentQuizFeedback
                        if (fb != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = fb,
                                color = Color(0xFFA7F3D0),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            // 6. Сокровищница 12 ключевых стихов книги Исход
            if (showVersesTreasury) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .clickable { showVersesTreasury = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .width(560.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF0F172A))
                            .border(2.dp, Color(0xFFFDE047), RoundedCornerShape(24.dp))
                            .clickable(enabled = false) {}
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "📜 12 ключевых стихов книги Исход",
                                color = Color(0xFFFDE047),
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFF334155))
                                    .clickable { showVersesTreasury = false }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                            ) {
                                Text("Закрыть ✕", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        for (v in EXODUS_VERSES) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF1E293B))
                                    .clickable { speak(v.ttsText) }
                                    .padding(12.dp),
                            ) {
                                Text(
                                    text = "Сцена ${v.sceneNumber} · ${v.ref} 🔊",
                                    color = Color(0xFFFDE047),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = v.text,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = "💡 ${v.deepMeaning}",
                                    color = Color(0xFFBAE6FD),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }

            // 7. Окно победы в сцене или проигрыша
            if ((gameOver || stageVictory) && !showFaithChoiceDialog && !showCommandmentsModal) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.94f))
                        .border(2.dp, Color(0xFFFDE047).copy(alpha = 0.65f), RoundedCornerShape(26.dp))
                        .clickable {
                            if (gameOver) {
                                buildStage(targetStage = stage, resetTotalScore = false)
                            } else if (fullExodusComplete) {
                                buildStage(targetStage = 1, resetTotalScore = true)
                            } else {
                                buildStage(targetStage = stage + 1, resetTotalScore = false)
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val title = when {
                            gameOver -> "🛡️ Испытание на пути!"
                            fullExodusComplete -> "⛰️📜 ВСЕ 6 СЦЕН КНИГИ ИСХОД ПРОЙДЕНЫ!"
                            else -> "${sceneInfo.emoji} ${sceneInfo.fullTitle} пройдена!"
                        }
                        val nextInfo = MOSES_SCENES.getOrNull(stage)
                        val sub = when {
                            gameOver -> "Нажми сюда или «ПРЫЖОК», чтобы пройти Сцену $stage ещё раз"
                            fullExodusComplete -> "От корзинки в Ниле и Пасхального Агнца до Чермного моря и 10 Заповедей Синая! Очки: $score"
                            nextInfo != null -> "${sceneInfo.faithChoice.miracleBanner}\nВпереди ${nextInfo.fullTitle} (${nextInfo.bibleRef})"
                            else -> sceneInfo.faithChoice.miracleBanner
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
                                text = when {
                                    gameOver -> "Попробовать сцену снова"
                                    fullExodusComplete -> "Начать путешествие с 1-й сцены"
                                    else -> "Перейти к Сцене ${stage + 1} ▶"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            // 8. Плавающий левый пульт движения: ◀ ВЛЕВО | ▼ ОАЗИС | ВПРАВО ▶ + ручка ✥
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

            // 9. Плавающий правый блок: контекстная кнопка чуда сцены + ▲ ПРЫЖОК + ручка ✥
            val canDoubleJump = mosesY > 3f && !airJumpUsed
            val nearMeribahRock = rockPuzzles.any { !it.activated && abs(mosesX - it.rockX) < 68f }
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (nearMeribahRock) Color(0xFF0284C7) else Color(0xFFB45309),
                        )
                        .pointerInput(stage, running, gameOver, inUndergroundOasis) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                triggerSceneAction()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (nearMeribahRock) {
                            "💧 СКАЛА!"
                        } else {
                            "${sceneInfo.actionEmoji} ${sceneInfo.actionLabel}"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }

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

/** Сцена 1: Тростниковая осмоленная корзинка с младенцем Моисеем на волнах Нила. */
private fun DrawScope.drawNileReedBasket(
    x: Float,
    bottomY: Float,
    u: Float,
    sheltered: Boolean,
    animTime: Float,
) {
    val bob = sin(animTime * 5f) * 3f * u
    val cy = bottomY - 12f * u + bob
    if (sheltered) {
        drawCircle(
            color = Color(0xFF34D399).copy(alpha = 0.38f),
            radius = 28f * u,
            center = Offset(x, cy),
        )
    }
    // Плетёная тростниковая корзинка
    drawRoundRect(
        color = Color(0xFFD97706),
        topLeft = Offset(x - 18f * u, cy - 10f * u),
        size = Size(36f * u, 18f * u),
        cornerRadius = CornerRadius(9f * u, 9f * u),
    )
    drawRoundRect(
        color = Color(0xFF78350F),
        topLeft = Offset(x - 16f * u, cy + 2f * u),
        size = Size(32f * u, 5f * u),
        cornerRadius = CornerRadius(4f * u, 4f * u),
    )
    // Младенец Моисей в белых пеленах внутри корзинки
    drawCircle(
        color = Color(0xFFF5F5F4),
        radius = 8f * u,
        center = Offset(x - 2f * u, cy - 11f * u),
    )
    drawCircle(
        color = Color(0xFFFDE68A),
        radius = 6f * u,
        center = Offset(x + 2f * u, cy - 12f * u),
    )
}

/** Сцена 1: Сестра Мариам, наблюдающая за корзинкой Моисея с берега Нила (Исход 2:4). */
private fun DrawScope.drawMiriamSister(
    x: Float,
    bankY: Float,
    u: Float,
    animTime: Float,
) {
    val bounce = abs(sin(animTime * 6f)) * 3f * u
    drawRoundRect(
        color = Color(0xFF0284C7),
        topLeft = Offset(x - 6f * u, bankY - 22f * u - bounce),
        size = Size(12f * u, 22f * u),
        cornerRadius = CornerRadius(4f * u, 4f * u),
    )
    drawCircle(
        color = Color(0xFFFDE68A),
        radius = 6f * u,
        center = Offset(x, bankY - 27f * u - bounce),
    )
}

/** Сцена 1: Островок густого папируса и камыша на реке Нил. */
private fun DrawScope.drawPapyrusReedIsland(
    x: Float,
    groundY: Float,
    w: Float,
    h: Float,
    u: Float,
    animTime: Float,
) {
    val topY = groundY - h
    drawRoundRect(
        color = Color(0xFF15803D),
        topLeft = Offset(x, topY),
        size = Size(w, h),
        cornerRadius = CornerRadius(8f * u, 8f * u),
    )
    for (i in 0..3) {
        val sx = x + (i + 0.5f) * (w / 4f)
        val sway = sin(animTime * 4f + i) * 3f * u
        drawLine(
            color = Color(0xFF4ADE80),
            start = Offset(sx, groundY),
            end = Offset(sx + sway, topY - 10f * u),
            strokeWidth = 3f * u,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = Color(0xFF92400E),
            radius = 3.5f * u,
            center = Offset(sx + sway, topY - 10f * u),
        )
    }
}

/** Сцена 1: Нильский крокодил в воде. */
private fun DrawScope.drawNileCrocodile(
    x: Float,
    groundY: Float,
    u: Float,
    dir: Int,
    animTime: Float,
) {
    val d = dir.toFloat()
    val bob = sin(animTime * 6f) * 2f * u
    drawRoundRect(
        color = Color(0xFF166534),
        topLeft = Offset(x - 18f * u, groundY - 14f * u + bob),
        size = Size(36f * u, 12f * u),
        cornerRadius = CornerRadius(6f * u, 6f * u),
    )
    drawCircle(
        color = Color(0xFFFACC15),
        radius = 2.6f * u,
        center = Offset(x + d * 10f * u, groundY - 15f * u + bob),
    )
}

/** Сцена 1: Белая нильская лилия Божьей охраны. */
private fun DrawScope.drawNileLilySpot(
    x: Float,
    groundY: Float,
    u: Float,
    collected: Boolean,
    animTime: Float,
) {
    if (collected) return
    val bob = sin(animTime * 4f + x) * 3f * u
    drawCircle(
        color = Color(0xFF22C55E),
        radius = 10f * u,
        center = Offset(x, groundY - 8f * u + bob),
    )
    drawCircle(
        color = Color(0xFFFEF9C3),
        radius = 6.5f * u,
        center = Offset(x, groundY - 12f * u + bob),
    )
}

/** Сцена 5: Сосуд (омер) с небесной манной в пустыне (Исход 16:15, 33). */
private fun DrawScope.drawMannaOmerSpot(
    x: Float,
    groundY: Float,
    u: Float,
    collected: Boolean,
    animTime: Float,
) {
    val alpha = if (collected) 0.35f else 1f
    val bob = if (collected) 0f else sin(animTime * 4f + x) * 3f * u
    drawRoundRect(
        color = Color(0xFFF59E0B).copy(alpha = alpha),
        topLeft = Offset(x - 9f * u, groundY - 22f * u + bob),
        size = Size(18f * u, 20f * u),
        cornerRadius = CornerRadius(6f * u, 6f * u),
    )
    drawCircle(
        color = Color(0xFFFEF9C3).copy(alpha = alpha),
        radius = 7f * u,
        center = Offset(x, groundY - 23f * u + bob),
    )
}

/** Облачный столп днём и Огненный столп ночью. */
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
    val pillarX = (mosesScreenX + (if (protectiveMode) -38f else 68f) * u).coerceIn(40f * u, size.width - 50f * u)
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

/** Скала Меривы (Исход 17:6) и деревянный плот на потоке живой воды. */
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
    val rockW = 42f * u
    val rockH = 54f * u
    drawRoundRect(
        color = Color(0xFF57534E),
        topLeft = Offset(rockX - rockW * 0.5f, groundY - rockH),
        size = Size(rockW, rockH),
        cornerRadius = CornerRadius(10f * u, 10f * u),
    )
    drawLine(
        color = if (activated) Color(0xFF38BDF8) else Color(0xFF7DD3FC).copy(alpha = 0.75f),
        start = Offset(rockX - 4f * u, groundY - rockH + 10f * u),
        end = Offset(rockX + 5f * u, groundY - 12f * u),
        strokeWidth = (if (activated) 4.5f else 2.5f) * u,
        cap = StrokeCap.Round,
    )

    if (activated) {
        val wave = sin(animTime * 8f) * 3f * u
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF7DD3FC), Color(0xFF0284C7))),
            topLeft = Offset(raftX + 10f * u, groundY - raftY),
            size = Size(raftW - 20f * u + wave, raftY),
            cornerRadius = CornerRadius(6f * u, 6f * u),
        )
    }

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

/** Секретный Подземный Оазис внутри колодца. */
private fun DrawScope.drawUndergroundOasisScene(
    groundY: Float,
    u: Float,
    mosesX: Float,
    mosesY: Float,
    facingRight: Boolean,
    treasures: List<OasisTreasure>,
    animTime: Float,
) {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF0F172A), Color(0xFF064E3B), Color(0xFF0284C7)),
        ),
        size = size,
    )
    drawRect(
        color = Color(0xFF334155),
        topLeft = Offset(0f, 0f),
        size = Size(size.width, 32f * u),
    )
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
    drawRoundRect(
        color = Color(0xFF57534E),
        topLeft = Offset(24f * u, 0f),
        size = Size(44f * u, 70f * u),
        cornerRadius = CornerRadius(4f * u, 4f * u),
    )

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

    val exitX = 320f * u
    val jetH = (85f + sin(animTime * 7f) * 10f) * u
    drawRoundRect(
        color = Color(0xFF7DD3FC).copy(alpha = 0.85f),
        topLeft = Offset(exitX - 16f * u, groundY - jetH),
        size = Size(32f * u, jetH),
        cornerRadius = CornerRadius(8f * u, 8f * u),
    )

    drawMosesHero(
        x = mosesX * u,
        bottomY = groundY - mosesY * u,
        u = u,
        facingRight = facingRight,
        jumping = mosesY > 2f,
        staffGlowing = true,
        sandalsRemoved = false,
        animTime = animTime,
    )
}

/** Каменный колодец. */
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

/** Висящий блок. */
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

/** Пророк Моисей с пастушьим посохом Божиим. */
private fun DrawScope.drawMosesHero(
    x: Float,
    bottomY: Float,
    u: Float,
    facingRight: Boolean,
    jumping: Boolean,
    staffGlowing: Boolean,
    sandalsRemoved: Boolean,
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

    // Ноги (босые на святой земле у Неопалимой купины или в сандалиях)
    val footColor = if (sandalsRemoved) Color(0xFFFDE68A) else Color(0xFF78350F)
    drawRoundRect(
        color = footColor,
        topLeft = Offset(x - 6f * u + stride, bottomY - 9f * u),
        size = Size(5f * u, 9f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )
    drawRoundRect(
        color = footColor,
        topLeft = Offset(x + 1f * u - stride, bottomY - 9f * u),
        size = Size(5f * u, 9f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )

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

/** Воин фараона. */
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

/** Дом семьи народа Божьего в Египте (в Сцене 3 с алыми знаками Пасхального Агнца на косяках и перекладине двери!). */
private fun DrawScope.drawHebrewHouse(
    x: Float,
    groundY: Float,
    u: Float,
    rescued: Boolean,
    showPassoverBloodMarks: Boolean,
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
        color = Color(0xFF78350F),
        topLeft = Offset(x - 7f * u, groundY - 21f * u),
        size = Size(14f * u, 21f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )
    if (showPassoverBloodMarks) {
        // Оба косяка и верхняя перекладина двери помазаны кровью Пасхального Агнца (Исход 12:7, 13)
        val markColor = Color(0xFFDC2626)
        drawLine(markColor, Offset(x - 8f * u, groundY - 21f * u), Offset(x - 8f * u, groundY - 2f * u), 3.2f * u, StrokeCap.Round)
        drawLine(markColor, Offset(x + 8f * u, groundY - 21f * u), Offset(x + 8f * u, groundY - 2f * u), 3.2f * u, StrokeCap.Round)
        drawLine(markColor, Offset(x - 9f * u, groundY - 22f * u), Offset(x + 9f * u, groundY - 22f * u), 3.6f * u, StrokeCap.Round)
    }
    if (rescued) {
        drawCircle(
            color = if (showPassoverBloodMarks) Color(0xFFFDE047) else Color(0xFF34D399),
            radius = 6f * u,
            center = Offset(x, groundY - h - 9f * u),
        )
    }
}

/** Спасённый житель народа Божьего, идущий следом за Моисеем. */
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

/** Неопалимая купина (Исход 3:2). */
private fun DrawScope.drawBurningBush(
    x: Float,
    groundY: Float,
    u: Float,
    animTime: Float,
) {
    val pulse = sin(animTime * 8f) * 3f * u
    drawCircle(
        color = Color(0xFFF97316).copy(alpha = 0.50f),
        radius = 25f * u + pulse,
        center = Offset(x, groundY - 22f * u),
    )
    drawCircle(
        color = Color(0xFFFACC15).copy(alpha = 0.80f),
        radius = 17f * u,
        center = Offset(x, groundY - 22f * u),
    )
    drawCircle(
        color = Color(0xFF15803D),
        radius = 12f * u,
        center = Offset(x, groundY - 16f * u),
    )
}

/** Финишный памятник каждой из 6 сцен книги Исход. */
private fun DrawScope.drawStageGoalMonument(
    stage: Int,
    x: Float,
    groundY: Float,
    u: Float,
    seaParting: Float,
    rephidimHandsLevel: Float,
    animTime: Float,
) {
    when (stage) {
        1 -> {
            // Сцена 1: Купальня и шатёр дочери фараона на берегу Нила (Исход 2:5)
            drawRoundRect(
                color = Color(0xFFFDE68A),
                topLeft = Offset(x - 28f * u, groundY - 76f * u),
                size = Size(56f * u, 76f * u),
                cornerRadius = CornerRadius(8f * u, 8f * u),
            )
            drawRoundRect(
                color = Color(0xFF0284C7),
                topLeft = Offset(x - 34f * u, groundY - 86f * u),
                size = Size(68f * u, 14f * u),
                cornerRadius = CornerRadius(6f * u, 6f * u),
            )
            // Фигура дочери фараона, протягивающей руки к корзинке Моисея
            drawRoundRect(
                color = Color(0xFFF8FAFC),
                topLeft = Offset(x - 8f * u, groundY - 34f * u),
                size = Size(16f * u, 34f * u),
                cornerRadius = CornerRadius(5f * u, 5f * u),
            )
            drawCircle(
                color = Color(0xFFFDE047),
                radius = 8f * u,
                center = Offset(x, groundY - 41f * u),
            )
        }

        2 -> {
            // Сцена 2: Гора Божия Хорив и пылающая Неопалимая купина на вершине
            val horebPeak = Path().apply {
                moveTo(x - 65f * u, groundY)
                lineTo(x, groundY - 95f * u)
                lineTo(x + 65f * u, groundY)
                close()
            }
            drawPath(horebPeak, Color(0xFF78350F))
            drawBurningBush(x = x, groundY = groundY - 82f * u, u = u * 1.25f, animTime = animTime)
        }

        3 -> {
            // Сцена 3: Врата выхода из Египта с Пасхальным знамением
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
                color = Color(0xFFDC2626),
                topLeft = Offset(x - 30f * u, groundY - 102f * u),
                size = Size(72f * u, 14f * u),
                cornerRadius = CornerRadius(4f * u, 4f * u),
            )
        }

        4 -> {
            // Сцена 4: Чермное море расступается двумя высокими водяными стенами!
            val wave = sin(animTime * 5f) * 5f * u
            val wallH = (35f + seaParting * 85f) * u
            val gap = (12f + seaParting * 56f) * u
            drawRoundRect(
                color = Color(0xFF0284C7).copy(alpha = 0.85f),
                topLeft = Offset(x - gap - 36f * u, groundY - wallH + wave),
                size = Size(36f * u, wallH),
                cornerRadius = CornerRadius(10f * u, 10f * u),
            )
            drawRoundRect(
                color = Color(0xFF0284C7).copy(alpha = 0.85f),
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

        5 -> {
            // Сцена 5: Холм в Рефидиме, камень под Моисеем и Аарон и Ор по обе стороны (Исход 17:12)
            val hill = Path().apply {
                moveTo(x - 70f * u, groundY)
                lineTo(x, groundY - 72f * u)
                lineTo(x + 70f * u, groundY)
                close()
            }
            drawPath(hill, Color(0xFF92400E))
            // Камень под Моисеем на вершине холма
            drawRoundRect(
                color = Color(0xFF78716C),
                topLeft = Offset(x - 16f * u, groundY - 86f * u),
                size = Size(32f * u, 16f * u),
                cornerRadius = CornerRadius(5f * u, 5f * u),
            )
            // Фигуры Аарона (слева) и Ора (справа), поддерживающих поднятые руки!
            val armLift = rephidimHandsLevel * 18f * u
            drawRoundRect(
                color = Color(0xFF0284C7),
                topLeft = Offset(x - 28f * u, groundY - 98f * u),
                size = Size(12f * u, 26f * u),
                cornerRadius = CornerRadius(4f * u, 4f * u),
            )
            drawRoundRect(
                color = Color(0xFF059669),
                topLeft = Offset(x + 16f * u, groundY - 98f * u),
                size = Size(12f * u, 26f * u),
                cornerRadius = CornerRadius(4f * u, 4f * u),
            )
            // Знамя «Иегова-Нисси» над холмом
            drawLine(
                color = Color(0xFFFDE047),
                start = Offset(x - 20f * u, groundY - 92f * u - armLift),
                end = Offset(x + 20f * u, groundY - 92f * u - armLift),
                strokeWidth = 4f * u,
                cap = StrokeCap.Round,
            )
        }

        else -> {
            // Сцена 6: Вершина Горы Синай и две каменные Скрижали 10 Заповедей!
            val sinaiPeak = Path().apply {
                moveTo(x - 75f * u, groundY)
                lineTo(x + 10f * u, groundY - 120f * u)
                lineTo(x + 95f * u, groundY)
                close()
            }
            drawPath(sinaiPeak, Color(0xFF57534E))
            val glow = (36f + sin(animTime * 6f) * 5f) * u
            drawCircle(
                color = Color(0xFFFDE047).copy(alpha = 0.45f),
                radius = glow,
                center = Offset(x + 10f * u, groundY - 125f * u),
            )
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
