package com.example.bible.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.KidsAgeMode
import com.example.bible.data.KidsFamilyStore
import com.example.bible.data.KidsGames
import com.example.bible.games.KidsGameAudio
import com.example.bible.games.KidsMusicTrack
import com.example.bible.games.KidsSfx
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

private enum class DinoObstacleKind {
    CACTUS_SMALL,
    CACTUS_TALL,
    CACTUS_TRIPLE,
    PTERODACTYL,
}

private data class DinoObstacle(
    val id: Long,
    val x: Float, // uniform virtual units
    val kind: DinoObstacleKind,
    val flyLevel: Int = 0, // 0 = low, 1 = mid (duck under or jump over)
)

private data class DinoCloud(
    val x: Float,
    val yFrac: Float, // fraction of sky height (0.12f..0.48f)
    val scale: Float,
)

@Composable
fun KidsDinoGameScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val defaultEasy = family.resolvedMode() == KidsAgeMode.SMALL
    val prefs = remember { context.getSharedPreferences("kids_dino_prefs", Context.MODE_PRIVATE) }

    var easyMode by remember { mutableStateOf(defaultEasy) }
    var running by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var ducking by remember { mutableStateOf(false) }

    // Uniform world coordinate system: 1 unit = `u` pixels on BOTH X and Y axes!
    // This guarantees 1:1 aspect ratio with zero stretching or squashing in any screen orientation.
    var worldWidthUnits by remember { mutableFloatStateOf(if (isLandscape) 680f else 380f) }
    var dinoY by remember { mutableFloatStateOf(0f) } // height above ground in uniform units
    var dinoVy by remember { mutableFloatStateOf(0f) }
    var distance by remember { mutableFloatStateOf(0f) }
    var score by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(prefs.getInt("high_score", 0)) }
    var animTick by remember { mutableFloatStateOf(0f) }
    var rewardedHundreds by remember { mutableIntStateOf(0) }
    var winRound by remember { mutableIntStateOf(0) }
    var milestoneBanner by remember { mutableStateOf<String?>(null) }
    var nextObstacleId by remember { mutableLongStateOf(1L) }

    // Draggable offsets for the floating buttons (persisted in memory while playing)
    var jumpBtnOffsetX by remember { mutableFloatStateOf(0f) }
    var jumpBtnOffsetY by remember { mutableFloatStateOf(0f) }
    var duckBtnOffsetX by remember { mutableFloatStateOf(0f) }
    var duckBtnOffsetY by remember { mutableFloatStateOf(0f) }

    val dinoX = if (isLandscape) 78f else 44f

    val obstacles = remember { mutableStateListOf<DinoObstacle>() }
    val clouds = remember {
        mutableStateListOf(
            DinoCloud(90f, 0.20f, 1.0f),
            DinoCloud(260f, 0.14f, 1.2f),
            DinoCloud(450f, 0.28f, 0.9f),
        )
    }

    fun resetAndStart() {
        obstacles.clear()
        dinoY = 0f
        dinoVy = 0f
        distance = 0f
        score = 0
        rewardedHundreds = 0
        milestoneBanner = null
        ducking = false
        gameOver = false
        running = true
        obstacles.add(
            DinoObstacle(
                id = nextObstacleId++,
                x = max(worldWidthUnits + 30f, 420f),
                kind = DinoObstacleKind.CACTUS_SMALL,
            ),
        )
        KidsGameAudio.play(KidsSfx.TAP)
    }

    fun triggerJump() {
        if (gameOver || !running) {
            resetAndStart()
            return
        }
        if (dinoY <= 3f) {
            ducking = false
            dinoVy = if (easyMode) 540f else 575f
            KidsGameAudio.play(KidsSfx.TAP)
        }
    }

    // 60 FPS uniform physics loop
    LaunchedEffect(running, gameOver, easyMode) {
        if (!running || gameOver) return@LaunchedEffect
        var lastNanos = 0L
        while (running && !gameOver) {
            withFrameNanos { now ->
                if (lastNanos == 0L) {
                    lastNanos = now
                    return@withFrameNanos
                }
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNanos = now

                val baseSpeed = if (easyMode) 215f else 265f
                val maxSpeed = if (easyMode) 390f else 490f
                val speed = (baseSpeed + score * 0.85f).coerceAtMost(maxSpeed)

                animTick += dt * (speed / 40f)
                distance += speed * dt * 0.045f
                val newScore = distance.toInt()
                if (newScore != score) {
                    score = newScore
                    if (score > highScore) {
                        highScore = score
                        prefs.edit().putInt("high_score", highScore).apply()
                    }
                    val hundreds = score / 100
                    if (hundreds > rewardedHundreds) {
                        rewardedHundreds = hundreds
                        winRound++
                        milestoneBanner = "🎉 ${hundreds * 100} очков! +2 звезды ⭐"
                        KidsGameAudio.play(KidsSfx.WIN)
                    }
                }

                // Uniform gravity & jump arc (max jump height ~ 105 units, easily clears tallest 52-unit cactus)
                val gravity = if (ducking && dinoY > 0f) 2600f else 1450f
                if (dinoY > 0f || dinoVy > 0f) {
                    dinoY += dinoVy * dt
                    dinoVy -= gravity * dt
                    if (dinoY <= 0f) {
                        dinoY = 0f
                        dinoVy = 0f
                    }
                }

                // Clouds movement
                for (i in clouds.indices) {
                    val c = clouds[i]
                    val nx = c.x - speed * 0.16f * dt
                    clouds[i] = if (nx < -90f) {
                        c.copy(
                            x = worldWidthUnits + 50f + Random.nextFloat() * 90f,
                            yFrac = Random.nextFloat() * 0.30f + 0.12f,
                        )
                    } else {
                        c.copy(x = nx)
                    }
                }

                // Obstacles movement
                val iter = obstacles.listIterator()
                while (iter.hasNext()) {
                    val ob = iter.next()
                    val obSpeed = if (ob.kind == DinoObstacleKind.PTERODACTYL) speed * 1.1f else speed
                    val nx = ob.x - obSpeed * dt
                    if (nx < -90f) {
                        iter.remove()
                    } else {
                        iter.set(ob.copy(x = nx))
                    }
                }

                // Spawn obstacles off the right edge of the current screen width
                val lastX = obstacles.maxOfOrNull { it.x } ?: 0f
                val minGap = if (easyMode) 235f else 190f
                if (obstacles.isEmpty() || lastX < worldWidthUnits - minGap - Random.nextFloat() * 110f) {
                    val canSpawnBird = score >= 30
                    val r = Random.nextFloat()
                    val kind = when {
                        canSpawnBird && r < 0.28f -> DinoObstacleKind.PTERODACTYL
                        r < 0.56f -> DinoObstacleKind.CACTUS_SMALL
                        r < 0.82f -> DinoObstacleKind.CACTUS_TALL
                        else -> DinoObstacleKind.CACTUS_TRIPLE
                    }
                    val flyLevel = if (kind == DinoObstacleKind.PTERODACTYL) {
                        if (Random.nextBoolean()) 1 else 0
                    } else {
                        0
                    }
                    obstacles.add(
                        DinoObstacle(
                            id = nextObstacleId++,
                            x = max(worldWidthUnits + 35f, lastX + minGap + Random.nextFloat() * 115f),
                            kind = kind,
                            flyLevel = flyLevel,
                        ),
                    )
                }

                // Hitbox check in uniform virtual units (ground is y = 0, up is positive)
                val isDuckOnGround = ducking && dinoY <= 2f
                val dinoW = if (isDuckOnGround) 52f else 40f
                val dinoH = if (isDuckOnGround) 28f else 48f
                val dinoRect = Rect(
                    left = dinoX + 7f,
                    top = -(dinoY + dinoH - 5f),
                    right = dinoX + dinoW - 7f,
                    bottom = -(dinoY + 3f),
                )

                for (ob in obstacles) {
                    val obRect = when (ob.kind) {
                        DinoObstacleKind.CACTUS_SMALL -> Rect(ob.x + 4f, -36f, ob.x + 22f, 0f)
                        DinoObstacleKind.CACTUS_TALL -> Rect(ob.x + 4f, -48f, ob.x + 26f, 0f)
                        DinoObstacleKind.CACTUS_TRIPLE -> Rect(ob.x + 4f, -42f, ob.x + 52f, 0f)
                        DinoObstacleKind.PTERODACTYL -> {
                            val bottomH = if (ob.flyLevel == 1) 33f else 10f
                            Rect(ob.x + 5f, -(bottomH + 22f), ob.x + 42f, -bottomH)
                        }
                    }
                    if (dinoRect.overlaps(obRect)) {
                        gameOver = true
                        running = false
                        KidsGameAudio.play(KidsSfx.LOSE)
                        break
                    }
                }
            }
        }
    }

    KidsGameMusic(KidsMusicTrack.GAMES)
    KidsGameWinReward(
        game = KidsGames.DINO,
        won = winRound > 0,
        points = 20,
        round = winRound,
    )

    val isNight = (score / 250) % 2 == 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Full-screen Game Canvas (both in landscape and portrait!)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(running, gameOver, easyMode) {
                        detectTapGestures(
                            onTap = { triggerJump() },
                        )
                    },
            ) {
                // UNIFORM scale factor `u`: 1 virtual unit = `u` pixels on BOTH X and Y!
                val minDim = min(size.width, size.height)
                val u = (minDim / 310f).coerceAtLeast(1f)
                worldWidthUnits = size.width / u

                // Ground sits cleanly above the bottom floating buttons
                val groundBottomUnits = if (size.width > size.height) 62f else 98f
                val groundPx = (size.height - groundBottomUnits * u).coerceIn(size.height * 0.55f, size.height * 0.86f)

                // Sky background (Day / Night transition)
                val skyBrush = if (isNight) {
                    Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFFFFFBEB), Color(0xFFFEF3C7)))
                }
                drawRect(skyBrush)

                // Sun or Moon & Stars (uniform radius `u` so sun/moon is ALWAYS a true circle!)
                val sunX = size.width - 68f * u
                val sunY = (groundPx * 0.26f).coerceAtLeast(56f * u)
                if (isNight) {
                    drawCircle(Color(0xFFFDE68A), radius = 18f * u, center = Offset(sunX, sunY))
                    drawCircle(Color(0xFF0F172A), radius = 15f * u, center = Offset(sunX + 6f * u, sunY - 4f * u))
                    val starFracs = listOf(0.18f to 0.18f, 0.36f to 0.28f, 0.55f to 0.16f, 0.72f to 0.25f)
                    starFracs.forEach { (fx, fy) ->
                        drawCircle(
                            Color.White.copy(alpha = 0.75f),
                            radius = 2.2f * u,
                            center = Offset(size.width * fx, groundPx * fy),
                        )
                    }
                } else {
                    drawCircle(Color(0xFFF59E0B).copy(alpha = 0.88f), radius = 22f * u, center = Offset(sunX, sunY))
                }

                // Clouds (drawn with uniform `u` in both width and height)
                val cloudColor = if (isNight) Color(0xFF334155) else Color.White.copy(alpha = 0.92f)
                clouds.forEach { c ->
                    val cx = c.x * u
                    val cy = (groundPx * c.yFrac).coerceAtLeast(42f * u)
                    val s = c.scale * u
                    drawRoundRect(
                        color = cloudColor,
                        topLeft = Offset(cx, cy),
                        size = Size(54f * s, 16f * s),
                        cornerRadius = CornerRadius(9f * s, 9f * s),
                    )
                    drawCircle(
                        color = cloudColor,
                        radius = 12f * s,
                        center = Offset(cx + 18f * s, cy + 2f * s),
                    )
                    drawCircle(
                        color = cloudColor,
                        radius = 10f * s,
                        center = Offset(cx + 36f * s, cy + 4f * s),
                    )
                }

                // Desert Ground & scrolling pebbles
                val groundColor = if (isNight) Color(0xFF94A3B8) else Color(0xFF78350F)
                val sandFill = if (isNight) Color(0xFF0B1120) else Color(0xFFFDE68A)
                drawRect(
                    color = sandFill,
                    topLeft = Offset(0f, groundPx),
                    size = Size(size.width, size.height - groundPx),
                )
                drawLine(
                    color = groundColor,
                    start = Offset(0f, groundPx),
                    end = Offset(size.width, groundPx),
                    strokeWidth = 2.6f * u,
                    cap = StrokeCap.Round,
                )
                val scrollOffset = (animTick * 26f) % 80f
                val pebbleCount = (worldWidthUnits / 75f).toInt() + 3
                for (k in 0..pebbleCount) {
                    val px = (k * 75f - scrollOffset) * u
                    drawLine(
                        color = groundColor.copy(alpha = 0.45f),
                        start = Offset(px, groundPx + 9f * u),
                        end = Offset(px + 14f * u, groundPx + 9f * u),
                        strokeWidth = 2f * u,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = groundColor.copy(alpha = 0.35f),
                        radius = 1.6f * u,
                        center = Offset(px + 34f * u, groundPx + 16f * u),
                    )
                }

                // Obstacles (drawn with uniform `u`)
                obstacles.forEach { ob ->
                    drawObstacleUniform(ob, u, groundPx, animTick, isNight)
                }

                // Chrome T-Rex Dino (drawn with uniform `u`)
                drawDinoUniform(
                    dinoX = dinoX,
                    dinoY = dinoY,
                    ducking = ducking && dinoY <= 2f,
                    gameOver = gameOver,
                    animTick = animTick,
                    u = u,
                    groundPx = groundPx,
                    isNight = isNight,
                )
            }

            // Floating Top HUD (compact in landscape so the game fills the whole screen, neat in portrait)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 12.dp, vertical = if (isLandscape) 6.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left floating pill: Back + Title + Score
                    Surface(
                        color = Color(0xFF0F172A).copy(alpha = 0.72f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(999.dp),
                        shadowElevation = 4.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 4.dp, end = 14.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White,
                                )
                            }
                            Text(
                                text = if (isLandscape) "🦖" else "Динозаврик 🦖",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Text(
                                text = "СЧЁТ ${score.toString().padStart(4, '0')} · РЕК ${highScore.toString().padStart(4, '0')}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = Color(0xFFFDE68A),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }

                    // Right floating pill: Speed toggle + Restart + Audio
                    Surface(
                        color = Color(0xFF0F172A).copy(alpha = 0.72f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(999.dp),
                        shadowElevation = 4.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (easyMode) Color(0xFF10B981) else Color(0xFFF59E0B))
                                    .clickable { easyMode = !easyMode }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = if (easyMode) "Легко" else "Быстро",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            IconButton(
                                onClick = { resetAndStart() },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Заново",
                                    tint = Color.White,
                                )
                            }
                            KidsAudioToggles()
                        }
                    }
                }

                milestoneBanner?.let { banner ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFFEF3C7).copy(alpha = 0.95f))
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = banner,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Start or Game Over overlay card in the center
            if (!running || gameOver) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.86f))
                        .clickable { resetAndStart() }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (gameOver) "💥 ОЙ, КАКТУС!" else "🦖 ДИНОЗАВРИК",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Text(
                        text = if (gameOver) {
                            "Счёт: $score · Рекорд: $highScore · Каждые 100 очков = ⭐+2"
                        } else {
                            "Прыгай через кактусы и приседай от птиц! (Кнопки можно двигать)"
                        },
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(2.dp))
                    Button(
                        onClick = { resetAndStart() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = if (gameOver) "🔄 Играть ещё раз" else "▶️ Старт!",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Floating Left Control: ПРИСЕСТЬ (supports tap, hold, and drag to reposition!)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .safeDrawingPadding()
                    .padding(start = 16.dp, bottom = 14.dp)
                    .offset { IntOffset(duckBtnOffsetX.roundToInt(), duckBtnOffsetY.roundToInt()) }
                    .shadow(8.dp, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (ducking) Color(0xFFD97706).copy(alpha = 0.92f)
                        else Color(0xFF334155).copy(alpha = 0.80f),
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                    .pointerInput(running, gameOver) {
                        detectTapGestures(
                            onPress = {
                                if (!running || gameOver) {
                                    resetAndStart()
                                } else {
                                    ducking = true
                                    if (dinoY > 0f) {
                                        dinoVy = -420f
                                    }
                                    tryAwaitRelease()
                                    ducking = false
                                }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            duckBtnOffsetX = (duckBtnOffsetX + dragAmount.x).coerceIn(-80f, 400f)
                            duckBtnOffsetY = (duckBtnOffsetY + dragAmount.y).coerceIn(-360f, 40f)
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "ПРИСЕСТЬ",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }

            // Floating Right Control: ПРЫЖОК (supports tap and drag to reposition!)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(end = 16.dp, bottom = 14.dp)
                    .offset { IntOffset(jumpBtnOffsetX.roundToInt(), jumpBtnOffsetY.roundToInt()) }
                    .shadow(8.dp, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF059669).copy(alpha = 0.86f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                    .pointerInput(running, gameOver, easyMode) {
                        detectTapGestures(
                            onPress = { triggerJump() },
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            jumpBtnOffsetX = (jumpBtnOffsetX + dragAmount.x).coerceIn(-400f, 80f)
                            jumpBtnOffsetY = (jumpBtnOffsetY + dragAmount.y).coerceIn(-360f, 40f)
                        }
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                    Text(
                        text = "ПРЫЖОК",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawDinoUniform(
    dinoX: Float,
    dinoY: Float,
    ducking: Boolean,
    gameOver: Boolean,
    animTick: Float,
    u: Float,
    groundPx: Float,
    isNight: Boolean,
) {
    val bodyColor = if (isNight) Color(0xFF34D399) else Color(0xFF047857)
    val bellyColor = if (isNight) Color(0xFFA7F3D0) else Color(0xFF6EE7B7)
    val basePxX = dinoX * u
    val basePxY = groundPx - dinoY * u

    if (ducking) {
        // Ducking horizontal T-Rex (52x28 uniform units)
        val topY = basePxY - 29f * u
        // Tail
        val tail = Path().apply {
            moveTo(basePxX + 4f * u, topY + 10f * u)
            lineTo(basePxX - 10f * u, topY + 5f * u)
            lineTo(basePxX - 4f * u, topY + 19f * u)
            close()
        }
        drawPath(tail, bodyColor)
        // Body
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(basePxX, topY + 5f * u),
            size = Size(34f * u, 16f * u),
            cornerRadius = CornerRadius(5f * u, 5f * u),
        )
        // Head stretched forward
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(basePxX + 26f * u, topY + 2f * u),
            size = Size(24f * u, 15f * u),
            cornerRadius = CornerRadius(4f * u, 4f * u),
        )
        // Eye
        drawCircle(Color.White, radius = 2.6f * u, center = Offset(basePxX + 40f * u, topY + 7f * u))
        drawCircle(Color(0xFF111827), radius = 1.4f * u, center = Offset(basePxX + 40.7f * u, topY + 7f * u))
    } else {
        // Standing / jumping T-Rex (40x48 uniform units — classic square proportions!)
        val topY = basePxY - 48f * u
        // Tail
        val tail = Path().apply {
            moveTo(basePxX + 7f * u, topY + 20f * u)
            lineTo(basePxX - 9f * u, topY + 13f * u)
            lineTo(basePxX - 4f * u, topY + 32f * u)
            close()
        }
        drawPath(tail, bodyColor)
        // Torso
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(basePxX + 4f * u, topY + 13f * u),
            size = Size(22f * u, 24f * u),
            cornerRadius = CornerRadius(5f * u, 5f * u),
        )
        // Belly highlight
        drawRoundRect(
            color = bellyColor.copy(alpha = 0.5f),
            topLeft = Offset(basePxX + 17f * u, topY + 17f * u),
            size = Size(7f * u, 16f * u),
            cornerRadius = CornerRadius(3f * u, 3f * u),
        )
        // Head & Snout
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(basePxX + 12f * u, topY),
            size = Size(25f * u, 17f * u),
            cornerRadius = CornerRadius(4.5f * u, 4.5f * u),
        )
        // Mouth line
        drawLine(
            color = Color(0xFF064E3B),
            start = Offset(basePxX + 25f * u, topY + 12.5f * u),
            end = Offset(basePxX + 36f * u, topY + 12.5f * u),
            strokeWidth = 1.6f * u,
        )
        // Eye
        val eyeCenter = Offset(basePxX + 21f * u, topY + 6f * u)
        drawCircle(Color.White, radius = if (gameOver) 3.4f * u else 2.6f * u, center = eyeCenter)
        drawCircle(Color(0xFF111827), radius = 1.4f * u, center = eyeCenter + Offset(0.6f * u, 0f))
        // Tiny arm
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(basePxX + 24f * u, topY + 21f * u),
            size = Size(8f * u, 4f * u),
            cornerRadius = CornerRadius(2f * u, 2f * u),
        )
    }

    // Alternating running legs
    val step = ((animTick * 2.4f).toInt() % 2 == 0)
    val leftLegLift = if (dinoY > 2f) 2f else if (step) 4.5f else 0f
    val rightLegLift = if (dinoY > 2f) 2f else if (!step) 4.5f else 0f
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(basePxX + 7f * u, basePxY - (12f + leftLegLift) * u),
        size = Size(5.5f * u, 12f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(basePxX + 16f * u, basePxY - (12f + rightLegLift) * u),
        size = Size(5.5f * u, 12f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )
}

private fun DrawScope.drawObstacleUniform(
    ob: DinoObstacle,
    u: Float,
    groundPx: Float,
    animTick: Float,
    isNight: Boolean,
) {
    val cactusColor = if (isNight) Color(0xFF22C55E) else Color(0xFF15803D)
    val xPx = ob.x * u

    fun drawSingleCactus(cx: Float, h: Float, w: Float) {
        val top = groundPx - h * u
        // Main stem
        drawRoundRect(
            color = cactusColor,
            topLeft = Offset(cx, top),
            size = Size(w * u, h * u),
            cornerRadius = CornerRadius(3.5f * u, 3.5f * u),
        )
        // Left branch
        drawRoundRect(
            color = cactusColor,
            topLeft = Offset(cx - 5.5f * u, top + h * 0.28f * u),
            size = Size(6.5f * u, h * 0.34f * u),
            cornerRadius = CornerRadius(2.5f * u, 2.5f * u),
        )
        // Right branch
        drawRoundRect(
            color = cactusColor,
            topLeft = Offset(cx + (w - 1f) * u, top + h * 0.18f * u),
            size = Size(6.5f * u, h * 0.36f * u),
            cornerRadius = CornerRadius(2.5f * u, 2.5f * u),
        )
    }

    when (ob.kind) {
        DinoObstacleKind.CACTUS_SMALL -> drawSingleCactus(xPx + 6f * u, 36f, 12f)
        DinoObstacleKind.CACTUS_TALL -> drawSingleCactus(xPx + 6f * u, 48f, 14f)
        DinoObstacleKind.CACTUS_TRIPLE -> {
            drawSingleCactus(xPx + 4f * u, 38f, 11f)
            drawSingleCactus(xPx + 20f * u, 43f, 12f)
            drawSingleCactus(xPx + 36f * u, 34f, 10f)
        }
        DinoObstacleKind.PTERODACTYL -> {
            val birdColor = if (isNight) Color(0xFFFBBF24) else Color(0xFF7C3AED)
            val bottomY = if (ob.flyLevel == 1) groundPx - 33f * u else groundPx - 10f * u
            val cy = bottomY - 12f * u
            // Body & Beak
            drawRoundRect(
                color = birdColor,
                topLeft = Offset(xPx + 8f * u, cy - 4.5f * u),
                size = Size(30f * u, 10f * u),
                cornerRadius = CornerRadius(3.5f * u, 3.5f * u),
            )
            val beak = Path().apply {
                moveTo(xPx + 10f * u, cy - 3.5f * u)
                lineTo(xPx - 2f * u, cy + 1f * u)
                lineTo(xPx + 10f * u, cy + 4.5f * u)
                close()
            }
            drawPath(beak, birdColor)
            // Flapping wing
            val wingUp = ((animTick * 2f).toInt() % 2 == 0)
            val wing = Path().apply {
                moveTo(xPx + 16f * u, cy)
                lineTo(xPx + 24f * u, if (wingUp) cy - 14f * u else cy + 13f * u)
                lineTo(xPx + 32f * u, cy)
                close()
            }
            drawPath(wing, birdColor)
        }
    }
}
