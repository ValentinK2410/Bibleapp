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

private data class FaithPlatformStep(
    val x: Float,
    val y: Float,
    val width: Float = 72f,
    val height: Float = 20f,
    val hasSpring: Boolean = false,
)

private data class FaithStoryMilestone(
    val id: Int,
    val x: Float,
    val y: Float,
    val title: String,
    val subtitle: String,
    val isScriptureScroll: Boolean,
    val verse: HeroFaithVerse? = null,
    val collected: Boolean = false,
)

private data class FaithDoubtObstacle(
    val id: Int,
    val x: Float,
    val minX: Float,
    val maxX: Float,
    val speed: Float,
    val dir: Int = -1,
    val label: String,
    val dispelled: Boolean = false,
)

private data class FaithSparkle(
    val x: Float,
    val y: Float,
    val color: Color,
    val alpha: Float = 1f,
)

@Composable
fun KidsHeroesOfFaithGameScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val prefs = remember { context.getSharedPreferences("kids_heroes_faith_prefs", Context.MODE_PRIVATE) }
    val speak = rememberKidsSpeaker(rate = 0.92f)

    var campaignIdx by remember { mutableIntStateOf(0) } // 0..11 (12 героев веры)
    val campaign = HEROES_OF_FAITH_CAMPAIGNS[campaignIdx.coerceIn(0, HEROES_OF_FAITH_CAMPAIGNS.lastIndex)]

    var running by remember { mutableStateOf(true) }
    var stageVictory by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var hurtCooldown by remember { mutableFloatStateOf(0f) }
    var shieldWaveTimer by remember { mutableFloatStateOf(0f) }
    var winRound by remember { mutableIntStateOf(0) }
    var bannerText by remember { mutableStateOf<String?>(null) }

    // Сохранённые выученные стихи и пройденные герои веры
    val memorizedVerseIds = remember {
        val saved = prefs.getStringSet("memorized_faith_verses", emptySet()) ?: emptySet()
        mutableStateListOf<Int>().apply {
            saved.mapNotNull { it.toIntOrNull() }.forEach { add(it) }
        }
    }
    val completedHeroKeys = remember {
        val saved = prefs.getStringSet("completed_heroes", emptySet()) ?: emptySet()
        mutableStateListOf<String>().apply { addAll(saved) }
    }

    var activeQuizVerse by remember { mutableStateOf<HeroFaithVerse?>(null) }
    var selectedQuizOption by remember { mutableStateOf<String?>(null) }
    var quizAnsweredCorrect by remember { mutableStateOf(false) }
    var showHeroesCatalogDialog by remember { mutableStateOf(false) }
    var showAllVersesDialog by remember { mutableStateOf(false) }

    // Координаты героя веры
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

    // Перетаскиваемые плавающие кнопки управления
    var dpadOffsetX by remember { mutableFloatStateOf(prefs.getFloat("dpad_ox", 0f)) }
    var dpadOffsetY by remember { mutableFloatStateOf(prefs.getFloat("dpad_oy", 0f)) }
    var jumpOffsetX by remember { mutableFloatStateOf(prefs.getFloat("jump_ox", 0f)) }
    var jumpOffsetY by remember { mutableFloatStateOf(prefs.getFloat("jump_oy", 0f)) }

    val platforms = remember { mutableStateListOf<FaithPlatformStep>() }
    val milestones = remember { mutableStateListOf<FaithStoryMilestone>() }
    val obstacles = remember { mutableStateListOf<FaithDoubtObstacle>() }
    val sparkles = remember { mutableStateListOf<FaithSparkle>() }

    val levelGoalX = 1520f

    fun openVerseStudy(verse: HeroFaithVerse) {
        selectedQuizOption = null
        quizAnsweredCorrect = memorizedVerseIds.contains(verse.id)
        activeQuizVerse = verse
        speak(verse.ttsText)
    }

    fun loadHeroCampaign(targetIdx: Int) {
        val idx = targetIdx.coerceIn(0, HEROES_OF_FAITH_CAMPAIGNS.lastIndex)
        campaignIdx = idx
        val c = HEROES_OF_FAITH_CAMPAIGNS[idx]
        heroX = 50f
        heroY = 0f
        heroVy = 0f
        facingRight = true
        moveLeftHeld = false
        moveRightHeld = false
        airJumpUsed = false
        cameraX = 0f
        hurtCooldown = 0f
        shieldWaveTimer = 0f
        stageVictory = false
        running = true
        bannerText = "${c.emoji} ${c.name}: ${c.eraTitle}"

        platforms.clear()
        milestones.clear()
        obstacles.clear()
        sparkles.clear()

        platforms.addAll(
            listOf(
                FaithPlatformStep(x = 230f, y = 54f, width = 78f),
                FaithPlatformStep(x = 490f, y = 48f, width = 66f, hasSpring = true),
                FaithPlatformStep(x = 730f, y = 88f, width = 84f),
                FaithPlatformStep(x = 1010f, y = 58f, width = 78f),
                FaithPlatformStep(x = 1250f, y = 84f, width = 80f),
            ),
        )

        val m0 = c.collectibleNames.getOrElse(0) { "Шаг веры" to "Доверие Богу" }
        val m1 = c.collectibleNames.getOrElse(1) { "Верность" to "Слово Божие" }
        val m2 = c.collectibleNames.getOrElse(2) { "Молитва" to "Сила свыше" }
        val m3 = c.collectibleNames.getOrElse(3) { "Победа веры" to "Благословение Господне" }

        milestones.addAll(
            listOf(
                FaithStoryMilestone(1, x = 165f, y = 0f, title = m0.first, subtitle = m0.second, isScriptureScroll = false),
                FaithStoryMilestone(2, x = 265f, y = 74f, title = c.verses[0].ref, subtitle = c.verses[0].deepMeaningTitle, isScriptureScroll = true, verse = c.verses[0]),
                FaithStoryMilestone(3, x = 570f, y = 0f, title = m1.first, subtitle = m1.second, isScriptureScroll = false),
                FaithStoryMilestone(4, x = 770f, y = 108f, title = c.verses.last().ref, subtitle = c.verses.last().deepMeaningTitle, isScriptureScroll = true, verse = c.verses.last()),
                FaithStoryMilestone(5, x = 930f, y = 0f, title = m2.first, subtitle = m2.second, isScriptureScroll = false),
                FaithStoryMilestone(6, x = 1290f, y = 104f, title = m3.first, subtitle = m3.second, isScriptureScroll = false),
            ),
        )

        val d0 = c.doubtLabels.getOrElse(0) { "«Сомнение»" }
        val d1 = c.doubtLabels.getOrElse(1) { "«Страх»" }
        obstacles.addAll(
            listOf(
                FaithDoubtObstacle(1, x = 390f, minX = 320f, maxX = 465f, speed = 60f, label = d0),
                FaithDoubtObstacle(2, x = 860f, minX = 760f, maxX = 985f, speed = 66f, label = d1),
                FaithDoubtObstacle(3, x = 1170f, minX = 1100f, maxX = 1235f, speed = 70f, label = d0),
            ),
        )
    }

    LaunchedEffect(Unit) {
        loadHeroCampaign(0)
    }

    fun triggerFaithShieldAction() {
        shieldWaveTimer = 0.75f
        KidsGameAudio.play(KidsSfx.TAP)
        for (i in obstacles.indices) {
            val ob = obstacles[i]
            if (!ob.dispelled && abs(ob.x - heroX) < 92f) {
                obstacles[i] = ob.copy(dispelled = true)
                score += 25
                sparkles.add(FaithSparkle(ob.x, 42f, Color(0xFFFEF08A)))
                bannerText = "🛡️ Верою побеждено сомнение: ${ob.label}"
                KidsGameAudio.play(KidsSfx.CORRECT)
            }
        }
    }

    fun triggerJump() {
        if (activeQuizVerse != null) return
        if (stageVictory) {
            val nextIdx = (campaignIdx + 1) % HEROES_OF_FAITH_CAMPAIGNS.size
            loadHeroCampaign(nextIdx)
            return
        }
        if (heroVy <= 5f && (!airJumpUsed || heroY <= 2f)) {
            if (heroY > 3f) {
                airJumpUsed = true
                heroVy = 495f
                sparkles.add(FaithSparkle(heroX, heroY + 12f, Color(0xFFFEF08A)))
            } else {
                airJumpUsed = false
                heroVy = 540f
            }
            KidsGameAudio.play(KidsSfx.TAP)
        } else if (!airJumpUsed) {
            airJumpUsed = true
            heroVy = 495f
            sparkles.add(FaithSparkle(heroX, heroY + 12f, Color(0xFFFEF08A)))
            KidsGameAudio.play(KidsSfx.TAP)
        }
    }

    // 60 FPS игровой цикл
    LaunchedEffect(running, stageVictory, campaignIdx, activeQuizVerse, showHeroesCatalogDialog, showAllVersesDialog) {
        if (!running || stageVictory || activeQuizVerse != null || showHeroesCatalogDialog || showAllVersesDialog) {
            return@LaunchedEffect
        }
        var lastNanos = 0L
        while (running && !stageVictory && activeQuizVerse == null && !showHeroesCatalogDialog && !showAllVersesDialog) {
            withFrameNanos { now ->
                if (lastNanos == 0L) {
                    lastNanos = now
                    return@withFrameNanos
                }
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNanos = now
                animTime += dt

                if (hurtCooldown > 0f) hurtCooldown = (hurtCooldown - dt).coerceAtLeast(0f)
                if (shieldWaveTimer > 0f) shieldWaveTimer = (shieldWaveTimer - dt).coerceAtLeast(0f)

                val moveDir = when {
                    moveLeftHeld && !moveRightHeld -> -1
                    moveRightHeld && !moveLeftHeld -> 1
                    else -> 0
                }
                if (moveDir != 0) {
                    facingRight = moveDir > 0
                    val speed = if (shieldWaveTimer > 0f) 210f else 180f
                    heroX = (heroX + moveDir * speed * dt).coerceIn(18f, levelGoalX + 100f)
                }

                val prevY = heroY
                heroY += heroVy * dt
                heroVy -= 1300f * dt

                var supportY = 0f
                val halfW = 14f
                if (heroVy <= 0f) {
                    for (p in platforms) {
                        val topY = p.y + p.height
                        val inX = heroX + halfW > p.x + 2f && heroX - halfW < p.x + p.width - 2f
                        if (inX && prevY >= topY - 7f && heroY <= topY) {
                            supportY = max(supportY, topY)
                            if (p.hasSpring) {
                                heroY = topY + 2f
                                heroVy = 635f
                                airJumpUsed = false
                                sparkles.add(FaithSparkle(p.x + 28f, topY + 16f, Color(0xFF38BDF8)))
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

                // Сбор вех истории и свитков Писания
                for (i in milestones.indices) {
                    val m = milestones[i]
                    if (m.collected) continue
                    if (abs(heroX - m.x) < 30f && abs(heroY - m.y) < 42f) {
                        milestones[i] = m.copy(collected = true)
                        sparkles.add(FaithSparkle(m.x, m.y + 32f, Color(0xFFFEF08A)))
                        if (m.isScriptureScroll && m.verse != null) {
                            score += 35
                            KidsGameAudio.play(KidsSfx.WIN)
                            openVerseStudy(m.verse)
                        } else {
                            score += 25
                            bannerText = "${campaign.collectibleEmoji} ${m.title} — ${m.subtitle}"
                            KidsGameAudio.play(KidsSfx.CORRECT)
                        }
                    }
                }

                // Препятствия сомнения и страха
                for (i in obstacles.indices) {
                    val ob = obstacles[i]
                    if (ob.dispelled) continue
                    var nx = ob.x + ob.dir * ob.speed * dt
                    var ndir = ob.dir
                    if (nx <= ob.minX) {
                        nx = ob.minX
                        ndir = 1
                    } else if (nx >= ob.maxX) {
                        nx = ob.maxX
                        ndir = -1
                    }
                    obstacles[i] = ob.copy(x = nx, dir = ndir)

                    if (abs(heroX - nx) < 25f && heroY < 36f) {
                        val stomped = (heroVy < -20f && prevY >= 24f) || shieldWaveTimer > 0f
                        if (stomped) {
                            obstacles[i] = ob.copy(dispelled = true)
                            heroVy = 415f
                            airJumpUsed = false
                            score += 25
                            sparkles.add(FaithSparkle(nx, 38f, Color(0xFF34D399)))
                            bannerText = "🛡️ Преодолено верою: ${ob.label}"
                            KidsGameAudio.play(KidsSfx.CORRECT)
                        } else if (hurtCooldown <= 0f) {
                            hurtCooldown = 1.4f
                            heroVy = 300f
                            bannerText = "🛡️ Нажми «${campaign.actionButtonLabel}» или перепрыгни: ${ob.label}"
                            KidsGameAudio.play(KidsSfx.WRONG)
                        }
                    }
                }

                val sIter = sparkles.listIterator()
                while (sIter.hasNext()) {
                    val sp = sIter.next()
                    val na = sp.alpha - dt * 1.1f
                    if (na <= 0f) sIter.remove() else sIter.set(sp.copy(y = sp.y + dt * 28f, alpha = na))
                }

                // Достижение памятника веры в конце пути
                if (heroX >= levelGoalX) {
                    score += 90
                    if (!completedHeroKeys.contains(campaign.key)) {
                        completedHeroKeys.add(campaign.key)
                        prefs.edit().putStringSet("completed_heroes", completedHeroKeys.toSet()).apply()
                    }
                    winRound++
                    stageVictory = true
                    KidsGameAudio.play(KidsSfx.WIN)
                }

                val targetCam = (heroX - worldViewportW * 0.30f).coerceIn(0f, max(0f, levelGoalX + 120f - worldViewportW))
                cameraX += (targetCam - cameraX) * (dt * 8f).coerceIn(0f, 1f)
            }
        }
    }

    KidsGameMusic(KidsMusicTrack.GAMES)
    KidsGameWinReward(
        game = KidsGames.HEROES_OF_FAITH,
        won = winRound > 0,
        points = 25,
        round = winRound,
    )

    val allVersesCount = remember { HEROES_OF_FAITH_CAMPAIGNS.sumOf { it.verses.size } }

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

                // Небо эпохи выбранного Героя Веры
                drawRect(brush = Brush.verticalGradient(campaign.skyColors), size = size)

                // Звёзды обетования (для Авраама, Еноха, Гедеона, Даниила) или Солнце правды
                for (sIdx in 0..9) {
                    val sx = ((sIdx * 110f) % (size.width / u)) * u
                    val sy = (24f + (sIdx % 3) * 22f) * u
                    drawCircle(
                        color = Color(0xFFFEF08A).copy(alpha = 0.55f),
                        radius = (if (sIdx % 2 == 0) 3.2f else 2.2f) * u,
                        center = Offset(sx, sy),
                    )
                }

                // Дальние холмы и горы Библейской земли
                val parallax = cameraX * 0.32f
                for (hIdx in 0..6) {
                    val hx = (hIdx * 260f - parallax % 260f) * u
                    val hillW = 240f * u
                    val hillH = (if (hIdx % 2 == 0) 86f else 64f) * u
                    drawRoundRect(
                        color = campaign.groundColor.copy(alpha = 0.34f),
                        topLeft = Offset(hx - hillW * 0.5f, groundY - hillH),
                        size = Size(hillW, hillH * 1.4f),
                        cornerRadius = CornerRadius(hillW * 0.5f, hillH),
                    )
                }

                // Платформы-ступени веры
                for (p in platforms) {
                    val px = (p.x - cameraX) * u
                    if (px > -100f * u && px < size.width + 80f * u) {
                        val py = groundY - (p.y + p.height) * u
                        drawRoundRect(
                            color = Color(0xFF78350F),
                            topLeft = Offset(px, py),
                            size = Size(p.width * u, p.height * u),
                            cornerRadius = CornerRadius(6f * u, 6f * u),
                        )
                        drawRoundRect(
                            color = Color(0xFFF59E0B),
                            topLeft = Offset(px - 2f * u, py),
                            size = Size((p.width + 4f) * u, 6f * u),
                            cornerRadius = CornerRadius(4f * u, 4f * u),
                        )
                        if (p.hasSpring) {
                            val jet = (7f + abs(sin(animTime * 5f)) * 9f) * u
                            drawRoundRect(
                                color = Color(0xFF38BDF8),
                                topLeft = Offset(px + p.width * 0.35f * u, py - jet),
                                size = Size(p.width * 0.30f * u, jet),
                                cornerRadius = CornerRadius(4f * u, 4f * u),
                            )
                        }
                    }
                }

                // Вехи истории и золотые свитки Писания (`📜`)
                for (m in milestones) {
                    if (!m.collected) {
                        val mx = (m.x - cameraX) * u
                        if (mx > -70f * u && mx < size.width + 70f * u) {
                            val my = groundY - m.y * u
                            val pulse = sin(animTime * 5f + m.id) * 3f * u
                            drawCircle(
                                color = if (m.isScriptureScroll) Color(0xFFFDE047).copy(alpha = 0.45f) else Color(0xFF34D399).copy(alpha = 0.40f),
                                radius = 18f * u + pulse,
                                center = Offset(mx, my - 18f * u),
                            )
                            drawRoundRect(
                                color = if (m.isScriptureScroll) Color(0xFFF59E0B) else campaign.heroMantleColor,
                                topLeft = Offset(mx - 10f * u, my - 28f * u),
                                size = Size(20f * u, 20f * u),
                                cornerRadius = CornerRadius(5f * u, 5f * u),
                            )
                        }
                    }
                }

                // Преграды сомнения и страха
                for (ob in obstacles) {
                    if (!ob.dispelled) {
                        val ox = (ob.x - cameraX) * u
                        if (ox > -70f * u && ox < size.width + 70f * u) {
                            val shift = sin(animTime * 8f + ob.id) * 3f * u
                            drawRoundRect(
                                color = Color(0xFF312E81).copy(alpha = 0.88f),
                                topLeft = Offset(ox - 14f * u + shift, groundY - 26f * u),
                                size = Size(28f * u, 20f * u),
                                cornerRadius = CornerRadius(8f * u, 8f * u),
                            )
                            drawCircle(Color(0xFFF87171), 2.8f * u, Offset(ox - 5f * u + shift, groundY - 17f * u))
                            drawCircle(Color(0xFFF87171), 2.8f * u, Offset(ox + 5f * u + shift, groundY - 17f * u))
                        }
                    }
                }

                // Памятник веры на финише (Ковчег Ноя с радугой, Жертвенник Илии с огнём, Стены Иерихона и т.д.)
                val goalScreenX = (levelGoalX - cameraX) * u
                if (goalScreenX > -200f * u && goalScreenX < size.width + 220f * u) {
                    drawHeroGoalMonument(
                        kind = campaign.monumentKind,
                        x = goalScreenX,
                        groundY = groundY,
                        u = u,
                        animTime = animTime,
                    )
                }

                // Герой веры со Щитом Веры!
                val heroScreenX = (heroX - cameraX) * u
                val heroScreenBottomY = groundY - heroY * u
                drawFaithHeroCharacter(
                    x = heroScreenX,
                    bottomY = heroScreenBottomY,
                    u = u,
                    robeColor = campaign.heroRobeColor,
                    mantleColor = campaign.heroMantleColor,
                    facingRight = facingRight,
                    shieldActive = shieldWaveTimer > 0f,
                    animTime = animTime,
                )

                for (sp in sparkles) {
                    val sx = (sp.x - cameraX) * u
                    val sy = groundY - sp.y * u
                    drawCircle(
                        color = sp.color.copy(alpha = sp.alpha.coerceIn(0f, 1f)),
                        radius = 7f * u,
                        center = Offset(sx, sy),
                    )
                }

                // Земля
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(campaign.groundColor, Color(0xFF0F172A)),
                        startY = groundY,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, groundY),
                    size = Size(size.width, size.height - groundY),
                )
                drawLine(
                    color = Color(0xFFFDE047).copy(alpha = 0.7f),
                    start = Offset(0f, groundY),
                    end = Offset(size.width, groundY),
                    strokeWidth = 3f * u,
                )
            }

            // Баннер подсказки
            if (activeQuizVerse == null && !showHeroesCatalogDialog && !showAllVersesDialog) {
                bannerText?.let { msg ->
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(3800)
                        if (bannerText == msg) bannerText = null
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .safeDrawingPadding()
                            .padding(top = if (isLandscape) 54.dp else 72.dp, start = 14.dp, end = 14.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF1E1B4B).copy(alpha = 0.92f))
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

            // Верхняя панель: Назад, Выбор из 12 Героев Веры, Кнопка «📖 Стихи (X/24)», Звук
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
                        .background(Color(0xFF0F172A).copy(alpha = 0.80f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFD97706))
                            .clickable { showHeroesCatalogDialog = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "${campaign.emoji} ${campaign.index}/12 · ${campaign.name} ▼",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }

                // Кнопка всех 24 стихов и глубин смысла
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF065F46).copy(alpha = 0.90f))
                        .border(1.dp, Color(0xFFFDE047).copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                        .clickable { showAllVersesDialog = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "Стихи веры",
                        tint = Color(0xFFFDE047),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Стихи: ${memorizedVerseIds.size}/$allVersesCount",
                        color = Color(0xFFFEF08A),
                        fontWeight = FontWeight.Black,
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
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable { loadHeroCampaign(campaignIdx) },
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

            // Горизонтальная лента быстрого переключения 12 Героев Веры
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 44.dp, start = 10.dp, end = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                for (hIdx in HEROES_OF_FAITH_CAMPAIGNS.indices) {
                    val h = HEROES_OF_FAITH_CAMPAIGNS[hIdx]
                    val selected = (hIdx == campaignIdx)
                    val done = completedHeroKeys.contains(h.key)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                when {
                                    selected -> Color(0xFFD97706)
                                    done -> Color(0xFF065F46).copy(alpha = 0.85f)
                                    else -> Color(0xFF0F172A).copy(alpha = 0.65f)
                                },
                            )
                            .border(
                                1.dp,
                                if (selected) Color(0xFFFDE047) else Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(999.dp),
                            )
                            .clickable { loadHeroCampaign(hIdx) }
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "${h.emoji} ${h.name}${if (done) " ✓" else ""}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
            }

            // === ОКНО ИЗУЧЕНИЯ И ЗАПОМИНАНИЯ СТИХА ГЕРОЯ ВЕРЫ ===
            activeQuizVerse?.let { v ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
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

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1E293B))
                                .padding(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "💡 Глубина смысла (${campaign.name}):",
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

                        Text(
                            text = "🧠 Проверь память сердца — какое ключевое слово пропущено?",
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
                                                            "memorized_faith_verses",
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

            // === КАТАЛОГ ВСЕХ 12 ГЕРОЕВ ВЕРЫ ===
            if (showHeroesCatalogDialog) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.70f))
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
                                text = "🛡️ Выбери Героя Веры (Евреям 11)",
                                color = Color(0xFFFDE047),
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                            )
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f))
                                    .clickable { showHeroesCatalogDialog = false },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                            }
                        }

                        for (hIdx in HEROES_OF_FAITH_CAMPAIGNS.indices) {
                            val h = HEROES_OF_FAITH_CAMPAIGNS[hIdx]
                            val done = completedHeroKeys.contains(h.key)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (hIdx == campaignIdx) Color(0xFF065F46) else Color(0xFF1E293B))
                                    .border(
                                        1.dp,
                                        if (done) Color(0xFF34D399) else Color.White.copy(alpha = 0.15f),
                                        RoundedCornerShape(16.dp),
                                    )
                                    .clickable {
                                        showHeroesCatalogDialog = false
                                        loadHeroCampaign(hIdx)
                                    }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "${h.index}. ${h.emoji} ${h.name}",
                                        color = Color(0xFFFEF08A),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        text = if (done) "🏆 Пройдено" else "Открыть ▶",
                                        color = if (done) Color(0xFF34D399) else Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                    )
                                }
                                Text(
                                    text = h.eraTitle,
                                    color = Color(0xFFBAE6FD),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                )
                                Text(
                                    text = h.shortSummary,
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                }
            }

            // === СОКРОВИЩНИЦА ВСЕХ 24 СТИХОВ И СМЫСЛОВ ГЕРОЕВ ВЕРЫ ===
            if (showAllVersesDialog) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.70f))
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
                                text = "📖 Все стихи Героев Веры (${memorizedVerseIds.size}/$allVersesCount)",
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
                                    .clickable { showAllVersesDialog = false },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                            }
                        }

                        for (h in HEROES_OF_FAITH_CAMPAIGNS) {
                            for (v in h.verses) {
                                val learned = memorizedVerseIds.contains(v.id)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (learned) Color(0xFF064E3B) else Color(0xFF1E293B))
                                        .clickable {
                                            showAllVersesDialog = false
                                            openVerseStudy(v)
                                        }
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "${if (learned) "⭐ " else "📜 "}${h.emoji} ${h.name} · ${v.ref}",
                                        color = Color(0xFFFEF08A),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                    )
                                    Text(
                                        text = v.fullText,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                    )
                                    Text(
                                        text = "💡 ${v.deepMeaningTitle}: ${v.deepMeaningBody}",
                                        color = Color.White.copy(alpha = 0.82f),
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Окно победы в истории героя веры
            if (stageVictory && activeQuizVerse == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.94f))
                        .border(2.dp, Color(0xFFFDE047), RoundedCornerShape(26.dp))
                        .clickable {
                            val nextIdx = (campaignIdx + 1) % HEROES_OF_FAITH_CAMPAIGNS.size
                            loadHeroCampaign(nextIdx)
                        }
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${campaign.emoji} Подвиг веры пройден: ${campaign.name}!",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = campaign.shortSummary,
                            color = Color(0xFFFEF08A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        val nextHero = HEROES_OF_FAITH_CAMPAIGNS[(campaignIdx + 1) % HEROES_OF_FAITH_CAMPAIGNS.size]
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF059669))
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "Следующий герой: ${nextHero.emoji} ${nextHero.name} ▶",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            // Плавающий левый пульт движения: ◀ ВЛЕВО | ВПРАВО ▶ + ручка ✥
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
                        .pointerInput(campaignIdx, running) {
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
                        .background(if (moveRightHeld) Color(0xFFD97706) else Color.Transparent)
                        .pointerInput(campaignIdx, running) {
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

            // Плавающий правый блок: Духовное действие Героя Веры + ▲ ПРЫЖОК + ручка ✥
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFD97706))
                        .pointerInput(campaignIdx, running) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                triggerFaithShieldAction()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = campaign.actionButtonLabel,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (canDoubleJump) Color(0xFF0284C7) else Color(0xFF059669))
                        .pointerInput(campaignIdx, running, stageVictory) {
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

/** Отрисовка Героя Веры со Щитом Веры и в историческом облачении. */
private fun DrawScope.drawFaithHeroCharacter(
    x: Float,
    bottomY: Float,
    u: Float,
    robeColor: Color,
    mantleColor: Color,
    facingRight: Boolean,
    shieldActive: Boolean,
    animTime: Float,
) {
    val dir = if (facingRight) 1f else -1f
    val stride = sin(animTime * 10f) * 4f * u

    if (shieldActive) {
        drawCircle(
            color = Color(0xFFFEF08A).copy(alpha = 0.45f),
            radius = 30f * u,
            center = Offset(x, bottomY - 24f * u),
        )
        drawCircle(
            color = Color(0xFFF59E0B),
            radius = 30f * u,
            center = Offset(x, bottomY - 24f * u),
            style = Stroke(width = 2.4f * u),
        )
    }

    drawRoundRect(
        color = Color(0xFF78350F),
        topLeft = Offset(x - 5f * u + stride, bottomY - 9f * u),
        size = Size(4.5f * u, 9f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )
    drawRoundRect(
        color = Color(0xFF78350F),
        topLeft = Offset(x + 1f * u - stride, bottomY - 9f * u),
        size = Size(4.5f * u, 9f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )

    drawRoundRect(
        color = robeColor,
        topLeft = Offset(x - 10f * u, bottomY - 31f * u),
        size = Size(20f * u, 23f * u),
        cornerRadius = CornerRadius(5f * u, 5f * u),
    )
    drawRoundRect(
        color = mantleColor,
        topLeft = Offset(x - 11f * u, bottomY - 30f * u),
        size = Size(13f * u, 21f * u),
        cornerRadius = CornerRadius(4f * u, 4f * u),
    )

    drawCircle(
        color = Color(0xFFFDE68A),
        radius = 8.5f * u,
        center = Offset(x, bottomY - 37f * u),
    )
    drawCircle(
        color = Color(0xFF1C1917),
        radius = 1.6f * u,
        center = Offset(x + dir * 3.2f * u, bottomY - 38f * u),
    )

    // Золотой Щит Веры в руке
    val sx = x + dir * 11f * u
    drawRoundRect(
        color = Color(0xFFFACC15),
        topLeft = Offset(sx - 4f * u, bottomY - 28f * u),
        size = Size(8f * u, 15f * u),
        cornerRadius = CornerRadius(3f * u, 3f * u),
    )
}

/** Отрисовка уникального библейского памятника на финише каждого Героя Веры. */
private fun DrawScope.drawHeroGoalMonument(
    kind: HeroMonumentKind,
    x: Float,
    groundY: Float,
    u: Float,
    animTime: Float,
) {
    val glow = (46f + sin(animTime * 4f) * 5f) * u
    drawCircle(
        color = Color(0xFFFEF08A).copy(alpha = 0.35f),
        radius = glow,
        center = Offset(x, groundY - 62f * u),
    )
    when (kind) {
        HeroMonumentKind.ARK_AND_RAINBOW -> {
            // Радуга Завета и Ковчег Ноя
            val arcCenter = Offset(x, groundY)
            drawCircle(Color(0xFFF87171).copy(alpha = 0.65f), 74f * u, arcCenter, style = Stroke(5f * u))
            drawCircle(Color(0xFFFACC15).copy(alpha = 0.65f), 66f * u, arcCenter, style = Stroke(5f * u))
            drawCircle(Color(0xFF34D399).copy(alpha = 0.65f), 58f * u, arcCenter, style = Stroke(5f * u))
            drawRoundRect(
                color = Color(0xFF78350F),
                topLeft = Offset(x - 42f * u, groundY - 40f * u),
                size = Size(84f * u, 40f * u),
                cornerRadius = CornerRadius(10f * u, 10f * u),
            )
            drawRoundRect(
                color = Color(0xFF92400E),
                topLeft = Offset(x - 28f * u, groundY - 58f * u),
                size = Size(56f * u, 20f * u),
                cornerRadius = CornerRadius(5f * u, 5f * u),
            )
        }

        HeroMonumentKind.CARMEL_FIRE_ALTAR, HeroMonumentKind.STARRY_ALTAR -> {
            // Каменный жертвенник и огонь/свет с неба
            drawRoundRect(
                color = Color(0xFF57534E),
                topLeft = Offset(x - 32f * u, groundY - 36f * u),
                size = Size(64f * u, 36f * u),
                cornerRadius = CornerRadius(6f * u, 6f * u),
            )
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFFEF08A), Color(0xFFF97316))),
                topLeft = Offset(x - 16f * u, groundY - 125f * u),
                size = Size(32f * u, 92f * u),
                cornerRadius = CornerRadius(12f * u, 12f * u),
            )
        }

        HeroMonumentKind.JERICHO_WALLS -> {
            // Стены Иерихона с червлёной верёвкой спасения
            drawRoundRect(
                color = Color(0xFFB45309),
                topLeft = Offset(x - 36f * u, groundY - 76f * u),
                size = Size(72f * u, 76f * u),
                cornerRadius = CornerRadius(6f * u, 6f * u),
            )
            drawLine(
                color = Color(0xFFDC2626),
                start = Offset(x, groundY - 66f * u),
                end = Offset(x, groundY - 12f * u),
                strokeWidth = 4f * u,
                cap = StrokeCap.Round,
            )
        }

        else -> {
            // Венец жизни и свет Божий
            drawRoundRect(
                color = Color(0xFFD97706),
                topLeft = Offset(x - 28f * u, groundY - 72f * u),
                size = Size(56f * u, 72f * u),
                cornerRadius = CornerRadius(8f * u, 8f * u),
            )
            drawCircle(
                color = Color(0xFFFDE047),
                radius = 18f * u,
                center = Offset(x, groundY - 88f * u),
            )
        }
    }
}
