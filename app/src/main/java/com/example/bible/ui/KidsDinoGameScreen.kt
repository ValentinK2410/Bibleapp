package com.example.bible.ui

import android.app.Activity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenWith
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    val x: Float, // uniform virtual units (1:1 aspect ratio)
    val kind: DinoObstacleKind,
    val flyLevel: Int = 0, // 0 = low, 1 = mid (duck under or jump over)
)

private data class DinoCloud(
    val x: Float,
    val yFrac: Float, // fraction of sky height (0.14f..0.46f)
    val scale: Float,
)

@Composable
fun KidsDinoGameScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // True full-screen immersive mode in landscape orientation
    DisposableEffect(view, isLandscape) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (isLandscape && controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val defaultEasy = family.resolvedMode() == KidsAgeMode.SMALL
    val prefs = remember { context.getSharedPreferences("kids_dino_prefs", Context.MODE_PRIVATE) }

    var easyMode by remember { mutableStateOf(defaultEasy) }
    var running by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var ducking by remember { mutableStateOf(false) }
    var airJumpUsed by remember { mutableStateOf(false) }
    var airPuffY by remember { mutableFloatStateOf(0f) }
    var airPuffAlpha by remember { mutableFloatStateOf(0f) }

    // Uniform world coordinate system: 1 unit = `u` pixels on BOTH X and Y axes.
    // Guarantees 1:1 proportions with zero stretching or squashing in any orientation.
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

    // Saved floating button offsets (persisted in SharedPreferences so user placement is remembered)
    var jumpBtnOffsetX by remember { mutableFloatStateOf(prefs.getFloat("jump_ox", 0f)) }
    var jumpBtnOffsetY by remember { mutableFloatStateOf(prefs.getFloat("jump_oy", 0f)) }
    var duckBtnOffsetX by remember { mutableFloatStateOf(prefs.getFloat("duck_ox", 0f)) }
    var duckBtnOffsetY by remember { mutableFloatStateOf(prefs.getFloat("duck_oy", 0f)) }

    val dinoX = if (isLandscape) 72f else 38f

    val obstacles = remember { mutableStateListOf<DinoObstacle>() }
    val clouds = remember {
        mutableStateListOf(
            DinoCloud(80f, 0.22f, 1.0f),
            DinoCloud(230f, 0.16f, 1.15f),
            DinoCloud(390f, 0.28f, 0.9f),
        )
    }

    fun resetAndStart() {
        obstacles.clear()
        dinoY = 0f
        dinoVy = 0f
        airJumpUsed = false
        airPuffAlpha = 0f
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
                x = max(worldWidthUnits + 30f, 390f),
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
            // First jump from ground
            ducking = false
            airJumpUsed = false
            dinoVy = if (easyMode) 545f else 580f
            KidsGameAudio.play(KidsSfx.TAP)
        } else if (!airJumpUsed) {
            // Single jump in mid-air (resets only after landing on the ground)
            ducking = false
            airJumpUsed = true
            airPuffY = dinoY
            airPuffAlpha = 1f
            dinoVy = if (easyMode) 500f else 530f
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

                val baseSpeed = if (easyMode) 205f else 255f
                val maxSpeed = if (easyMode) 380f else 480f
                val speed = (baseSpeed + score * 0.85f).coerceAtMost(maxSpeed)

                animTick += dt * (speed / 38f)
                if (airPuffAlpha > 0f) {
                    airPuffAlpha = (airPuffAlpha - dt * 3.5f).coerceAtLeast(0f)
                }
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

                // Uniform gravity & jump arc
                val gravity = if (ducking && dinoY > 0f) 2600f else 1450f
                if (dinoY > 0f || dinoVy > 0f) {
                    dinoY += dinoVy * dt
                    dinoVy -= gravity * dt
                    if (dinoY <= 0f) {
                        dinoY = 0f
                        dinoVy = 0f
                        airJumpUsed = false
                    }
                }

                // Clouds movement
                for (i in clouds.indices) {
                    val c = clouds[i]
                    val nx = c.x - speed * 0.16f * dt
                    clouds[i] = if (nx < -90f) {
                        c.copy(
                            x = worldWidthUnits + 40f + Random.nextFloat() * 80f,
                            yFrac = Random.nextFloat() * 0.28f + 0.15f,
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

                // Spawn obstacles off the right edge of the screen
                val lastX = obstacles.maxOfOrNull { it.x } ?: 0f
                val minGap = if (easyMode) 225f else 185f
                if (obstacles.isEmpty() || lastX < worldWidthUnits - minGap - Random.nextFloat() * 95f) {
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
                            x = max(worldWidthUnits + 30f, lastX + minGap + Random.nextFloat() * 100f),
                            kind = kind,
                            flyLevel = flyLevel,
                        ),
                    )
                }

                // Hitbox check in uniform virtual units (ground is y = 0, up is negative)
                val isDuckOnGround = ducking && dinoY <= 2f
                val dinoW = if (isDuckOnGround) 50f else 42f
                val dinoH = if (isDuckOnGround) 26f else 46f
                val dinoRect = Rect(
                    left = dinoX + 8f,
                    top = -(dinoY + dinoH - 5f),
                    right = dinoX + dinoW - 8f,
                    bottom = -(dinoY + 4f),
                )

                for (ob in obstacles) {
                    val obRect = when (ob.kind) {
                        DinoObstacleKind.CACTUS_SMALL -> Rect(ob.x + 4f, -34f, ob.x + 20f, 0f)
                        DinoObstacleKind.CACTUS_TALL -> Rect(ob.x + 4f, -46f, ob.x + 24f, 0f)
                        DinoObstacleKind.CACTUS_TRIPLE -> Rect(ob.x + 4f, -40f, ob.x + 48f, 0f)
                        DinoObstacleKind.PTERODACTYL -> {
                            val bottomH = if (ob.flyLevel == 1) 32f else 10f
                            Rect(ob.x + 5f, -(bottomH + 20f), ob.x + 40f, -bottomH)
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
            // Full-screen Game Canvas (100% of screen in both landscape and portrait)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(running, gameOver, easyMode) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            triggerJump()
                        }
                    },
            ) {
                val isLandCanvas = size.width > size.height
                // UNIFORM scale factor `u`: 1 virtual unit = `u` pixels on BOTH X and Y!
                // In portrait: 360 units wide so there is plenty of horizontal runway.
                // In landscape: 300 units tall so the whole screen height is used naturally.
                val u = if (isLandCanvas) {
                    (size.height / 300f).coerceAtLeast(1f)
                } else {
                    (size.width / 360f).coerceAtLeast(1f)
                }
                worldWidthUnits = size.width / u

                // Ground position:
                // In landscape: near bottom (78% of screen height) so the sky and runner fill the screen.
                // In portrait: at 64% of screen height so the game view has natural landscape-like proportions
                // and the floating buttons sit comfortably in the sand below the ground line!
                val groundPx = if (isLandCanvas) {
                    (size.height - 64f * u).coerceIn(size.height * 0.68f, size.height * 0.84f)
                } else {
                    (size.height * 0.64f).coerceIn(size.height * 0.55f, size.height * 0.75f)
                }

                // Sky background (Day / Night transition)
                val skyBrush = if (isNight) {
                    Brush.verticalGradient(
                        listOf(Color(0xFF0F172A), Color(0xFF1E293B)),
                        startY = 0f,
                        endY = groundPx,
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(Color(0xFFFEF9C3), Color(0xFFFDE68A).copy(alpha = 0.55f)),
                        startY = 0f,
                        endY = groundPx,
                    )
                }
                drawRect(skyBrush, topLeft = Offset.Zero, size = Size(size.width, groundPx))

                // Sun or Moon & Stars (uniform radius `u` -> always a true circle, never oval!)
                val sunX = size.width - 62f * u
                val sunY = (groundPx * 0.30f).coerceAtLeast(62f * u)
                if (isNight) {
                    drawCircle(Color(0xFFFDE68A), radius = 18f * u, center = Offset(sunX, sunY))
                    drawCircle(Color(0xFF131D31), radius = 15f * u, center = Offset(sunX + 6f * u, sunY - 4f * u))
                    val starFracs = listOf(0.16f to 0.22f, 0.34f to 0.32f, 0.54f to 0.20f, 0.72f to 0.28f)
                    starFracs.forEach { (fx, fy) ->
                        drawCircle(
                            Color.White.copy(alpha = 0.75f),
                            radius = 2.2f * u,
                            center = Offset(size.width * fx, groundPx * fy),
                        )
                    }
                } else {
                    drawCircle(Color(0xFFF59E0B).copy(alpha = 0.90f), radius = 21f * u, center = Offset(sunX, sunY))
                    drawCircle(Color(0xFFFDE047).copy(alpha = 0.35f), radius = 28f * u, center = Offset(sunX, sunY))
                }

                // Soft desert dunes along the horizon (drawn with uniform `u`)
                val duneColor = if (isNight) Color(0xFF1E293B) else Color(0xFFFCD34D).copy(alpha = 0.45f)
                val duneScroll = (animTick * 6f) % 220f
                val duneCount = (worldWidthUnits / 180f).toInt() + 3
                for (d in -1..duneCount) {
                    val dx = (d * 180f - duneScroll) * u
                    val dunePath = Path().apply {
                        moveTo(dx, groundPx)
                        quadraticTo(dx + 95f * u, groundPx - 28f * u, dx + 190f * u, groundPx)
                        close()
                    }
                    drawPath(dunePath, duneColor)
                }

                // Clouds (drawn with uniform `u` in both width and height)
                val cloudColor = if (isNight) Color(0xFF334155) else Color.White.copy(alpha = 0.92f)
                clouds.forEach { c ->
                    val cx = c.x * u
                    val cy = (groundPx * c.yFrac).coerceAtLeast(52f * u)
                    val s = c.scale * u
                    drawRoundRect(
                        color = cloudColor,
                        topLeft = Offset(cx, cy),
                        size = Size(52f * s, 15f * s),
                        cornerRadius = CornerRadius(8f * s, 8f * s),
                    )
                    drawCircle(
                        color = cloudColor,
                        radius = 11.5f * s,
                        center = Offset(cx + 17f * s, cy + 2f * s),
                    )
                    drawCircle(
                        color = cloudColor,
                        radius = 9.5f * s,
                        center = Offset(cx + 34f * s, cy + 3.5f * s),
                    )
                }

                // Desert Ground & scrolling pebbles
                val groundColor = if (isNight) Color(0xFF94A3B8) else Color(0xFF78350F)
                val sandBrush = if (isNight) {
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B1120), Color(0xFF020617)),
                        startY = groundPx,
                        endY = size.height,
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(Color(0xFFFDE68A), Color(0xFFF59E0B).copy(alpha = 0.45f)),
                        startY = groundPx,
                        endY = size.height,
                    )
                }
                drawRect(
                    brush = sandBrush,
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
                val pebbleCount = (worldWidthUnits / 72f).toInt() + 3
                for (k in 0..pebbleCount) {
                    val px = (k * 72f - scrollOffset) * u
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

                // Air-jump cloud puff ring when double jump is triggered in mid-air
                if (airPuffAlpha > 0f) {
                    val puffProgress = 1f - airPuffAlpha
                    val puffCenterX = (dinoX + 20f) * u
                    val puffCenterY = groundPx - (airPuffY - 6f * puffProgress) * u
                    val puffColor = if (isNight) {
                        Color(0xFF38BDF8).copy(alpha = airPuffAlpha * 0.75f)
                    } else {
                        Color.White.copy(alpha = airPuffAlpha * 0.9f)
                    }
                    drawOval(
                        color = puffColor,
                        topLeft = Offset(puffCenterX - (18f + 16f * puffProgress) * u, puffCenterY - 5f * u),
                        size = Size((36f + 32f * puffProgress) * u, 10f * u),
                    )
                    drawCircle(
                        color = puffColor,
                        radius = (6f - 2f * puffProgress) * u,
                        center = Offset(puffCenterX - (14f + 12f * puffProgress) * u, puffCenterY),
                    )
                    drawCircle(
                        color = puffColor,
                        radius = (6f - 2f * puffProgress) * u,
                        center = Offset(puffCenterX + (14f + 12f * puffProgress) * u, puffCenterY),
                    )
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

            // Floating Top HUD:
            // In Landscape: single ultra-compact floating bar so the game occupies the entire screen.
            // In Portrait: 2 neat floating rows so neither pill ever squeezes or wraps text!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 10.dp, vertical = if (isLandscape) 6.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left floating pill: Back + Title (+ Score in Landscape)
                    Surface(
                        color = Color(0xFF0F172A).copy(alpha = 0.76f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(999.dp),
                        shadowElevation = 4.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White,
                                )
                            }
                            Text(
                                text = "Динозаврик 🦖",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                            if (isLandscape) {
                                Text(
                                    text = "·  СЧЁТ ${score.toString().padStart(5, '0')}  РЕК ${highScore.toString().padStart(5, '0')}",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = Color(0xFFFDE68A),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }

                    // Right floating pill: Speed toggle + Restart + Audio
                    Surface(
                        color = Color(0xFF0F172A).copy(alpha = 0.76f),
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

                // In Portrait: dedicated compact floating score pill on the second row (zero text squeezing)
                if (!isLandscape) {
                    Surface(
                        color = Color(0xFF065F46).copy(alpha = 0.88f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(999.dp),
                        shadowElevation = 3.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "СЧЁТ: ${score.toString().padStart(5, '0')}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Text(
                                text = "РЕКОРД: ${highScore.toString().padStart(5, '0')} · ⭐+2 за 100",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFFD1FAE5),
                                maxLines = 1,
                                softWrap = false,
                            )
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
                            softWrap = false,
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
                        .background(Color(0xFF0F172A).copy(alpha = 0.88f))
                        .clickable { resetAndStart() }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = if (gameOver) "💥 ОЙ, КАКТУС!" else "🦖 ДИНОЗАВРИК",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = if (gameOver) {
                            "Счёт: $score · Рекорд: $highScore · Каждые 100 очков = ⭐+2"
                        } else {
                            "Доступен двойной прыжок (1 раз в воздухе до приземления)! Кнопки можно двигать за ✥"
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
                            softWrap = false,
                        )
                    }
                }
            }

            // Floating Left Control: ПРИСЕСТЬ (instant press-and-hold + drag handle ✥)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .safeDrawingPadding()
                    .padding(start = 14.dp, bottom = if (isLandscape) 12.dp else 22.dp)
                    .offset { IntOffset(duckBtnOffsetX.roundToInt(), duckBtnOffsetY.roundToInt()) }
                    .shadow(8.dp, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (ducking) Color(0xFFD97706).copy(alpha = 0.94f)
                        else Color(0xFF1E293B).copy(alpha = 0.84f),
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.48f), RoundedCornerShape(999.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Drag grip handle
                Box(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    prefs.edit()
                                        .putFloat("duck_ox", duckBtnOffsetX)
                                        .putFloat("duck_oy", duckBtnOffsetY)
                                        .apply()
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                duckBtnOffsetX = (duckBtnOffsetX + dragAmount.x).coerceIn(-40f, 500f)
                                duckBtnOffsetY = (duckBtnOffsetY + dragAmount.y).coerceIn(-500f, 60f)
                            }
                        }
                        .padding(start = 12.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.OpenWith,
                        contentDescription = "Переместить кнопку",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(16.dp),
                    )
                }

                // Instant press-and-hold action zone
                Row(
                    modifier = Modifier
                        .pointerInput(running, gameOver) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                if (!running || gameOver) {
                                    resetAndStart()
                                } else {
                                    ducking = true
                                    if (dinoY > 0f) {
                                        dinoVy = -440f
                                    }
                                    waitForUpOrCancellation()
                                    ducking = false
                                }
                            }
                        }
                        .padding(start = 4.dp, end = 18.dp, top = 13.dp, bottom = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "ПРИСЕСТЬ",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }

            // Floating Right Control: ПРЫЖОК (instant touch response + drag handle ✥)
            val canAirJumpNow = dinoY > 3f && !airJumpUsed
            val jumpExhaustedInAir = dinoY > 3f && airJumpUsed
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(end = 14.dp, bottom = if (isLandscape) 12.dp else 22.dp)
                    .offset { IntOffset(jumpBtnOffsetX.roundToInt(), jumpBtnOffsetY.roundToInt()) }
                    .shadow(8.dp, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        when {
                            canAirJumpNow -> Color(0xFF0284C7).copy(alpha = 0.92f)
                            jumpExhaustedInAir -> Color(0xFF475569).copy(alpha = 0.78f)
                            else -> Color(0xFF059669).copy(alpha = 0.90f)
                        },
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.50f), RoundedCornerShape(999.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Instant jump action zone
                Row(
                    modifier = Modifier
                        .pointerInput(running, gameOver, easyMode) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                triggerJump()
                            }
                        }
                        .padding(start = 18.dp, end = 6.dp, top = 13.dp, bottom = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = if (canAirJumpNow) "ПРЫЖОК ×2" else "ПРЫЖОК",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }

                // Drag grip handle
                Box(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    prefs.edit()
                                        .putFloat("jump_ox", jumpBtnOffsetX)
                                        .putFloat("jump_oy", jumpBtnOffsetY)
                                        .apply()
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                jumpBtnOffsetX = (jumpBtnOffsetX + dragAmount.x).coerceIn(-500f, 40f)
                                jumpBtnOffsetY = (jumpBtnOffsetY + dragAmount.y).coerceIn(-500f, 60f)
                            }
                        }
                        .padding(start = 6.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.OpenWith,
                        contentDescription = "Переместить кнопку",
                        tint = Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(16.dp),
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
    val darkShade = if (isNight) Color(0xFF059669) else Color(0xFF064E3B)
    val bellyColor = if (isNight) Color(0xFFA7F3D0) else Color(0xFF6EE7B7)
    val basePxX = dinoX * u
    val basePxY = groundPx - dinoY * u

    // Helper to draw pixel-art style rounded blocks with uniform `u`
    fun block(x: Float, yFromTop: Float, w: Float, h: Float, topRef: Float, color: Color = bodyColor, r: Float = 2.5f) {
        drawRoundRect(
            color = color,
            topLeft = Offset(basePxX + x * u, topRef + yFromTop * u),
            size = Size(w * u, h * u),
            cornerRadius = CornerRadius(r * u, r * u),
        )
    }

    if (ducking) {
        // Classic Chrome T-Rex ducking pose (50x26 uniform units — sleek horizontal runner)
        val topY = basePxY - 26f * u
        // Tail
        block(-10f, 4f, 12f, 6f, topY)
        block(-4f, 7f, 10f, 8f, topY)
        // Horizontal torso
        block(4f, 5f, 26f, 13f, topY, r = 4f)
        // Belly stripe
        block(10f, 13f, 16f, 4f, topY, color = bellyColor.copy(alpha = 0.55f))
        // Forward-leaning head & snout
        block(26f, 2f, 22f, 12f, topY, r = 3.5f)
        block(32f, 12f, 14f, 3.5f, topY, color = darkShade)
        // Eye
        drawCircle(Color.White, radius = 2.5f * u, center = Offset(basePxX + 35f * u, topY + 6.5f * u))
        drawCircle(Color(0xFF111827), radius = 1.3f * u, center = Offset(basePxX + 35.6f * u, topY + 6.5f * u))
        // Tiny arm
        block(24f, 16f, 6f, 3.5f, topY)
    } else {
        // Classic Chrome T-Rex standing/running pose (42x46 uniform units — true 1:1 proportions!)
        val topY = basePxY - 46f * u
        // Stepped tail
        block(-8f, 15f, 8f, 6f, topY)
        block(-4f, 18f, 10f, 9f, topY)
        block(1f, 22f, 9f, 10f, topY)
        // Main torso & neck
        block(6f, 15f, 20f, 20f, topY, r = 4.5f)
        block(12f, 10f, 14f, 9f, topY, r = 3f)
        // Belly highlight
        block(18f, 18f, 7f, 14f, topY, color = bellyColor.copy(alpha = 0.5f), r = 3f)
        // T-Rex boxy head & snout
        block(15f, 0f, 24f, 13f, topY, r = 3.5f)
        // Lower jaw
        block(23f, 12.5f, 13f, 3.2f, topY, color = darkShade, r = 1.5f)
        // Eye
        val eyeCenter = Offset(basePxX + 23f * u, topY + 5.2f * u)
        drawCircle(Color.White, radius = if (gameOver) 3.2f * u else 2.5f * u, center = eyeCenter)
        drawCircle(Color(0xFF111827), radius = 1.3f * u, center = eyeCenter + Offset(0.6f * u, 0f))
        // T-Rex tiny front arm + claw
        block(25f, 20f, 8f, 3.8f, topY, r = 1.8f)
        block(30f, 22f, 3f, 4f, topY, r = 1.2f)
    }

    // Alternating running legs
    val step = ((animTick * 2.4f).toInt() % 2 == 0)
    val leftLegLift = if (dinoY > 2f) 2f else if (step) 4.5f else 0f
    val rightLegLift = if (dinoY > 2f) 2f else if (!step) 4.5f else 0f
    // Left leg + foot
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(basePxX + 9f * u, basePxY - (11f + leftLegLift) * u),
        size = Size(5f * u, 11f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(basePxX + 9f * u, basePxY - (3.2f + leftLegLift) * u),
        size = Size(7.5f * u, 3.2f * u),
        cornerRadius = CornerRadius(1.5f * u, 1.5f * u),
    )
    // Right leg + foot
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(basePxX + 18f * u, basePxY - (11f + rightLegLift) * u),
        size = Size(5f * u, 11f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(basePxX + 18f * u, basePxY - (3.2f + rightLegLift) * u),
        size = Size(7.5f * u, 3.2f * u),
        cornerRadius = CornerRadius(1.5f * u, 1.5f * u),
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
            cornerRadius = CornerRadius((w * 0.4f) * u, (w * 0.4f) * u),
        )
        // Left arm (horizontal connector + vertical tip)
        val leftY = top + h * 0.36f * u
        drawRoundRect(
            color = cactusColor,
            topLeft = Offset(cx - 5.5f * u, leftY + 5f * u),
            size = Size(6.5f * u, 4f * u),
            cornerRadius = CornerRadius(2f * u, 2f * u),
        )
        drawRoundRect(
            color = cactusColor,
            topLeft = Offset(cx - 5.5f * u, leftY - 3f * u),
            size = Size(4.2f * u, 12f * u),
            cornerRadius = CornerRadius(2f * u, 2f * u),
        )
        // Right arm (horizontal connector + vertical tip)
        val rightY = top + h * 0.24f * u
        drawRoundRect(
            color = cactusColor,
            topLeft = Offset(cx + (w - 1f) * u, rightY + 6f * u),
            size = Size(6.5f * u, 4f * u),
            cornerRadius = CornerRadius(2f * u, 2f * u),
        )
        drawRoundRect(
            color = cactusColor,
            topLeft = Offset(cx + (w + 1.3f) * u, rightY - 3f * u),
            size = Size(4.2f * u, 13f * u),
            cornerRadius = CornerRadius(2f * u, 2f * u),
        )
    }

    when (ob.kind) {
        DinoObstacleKind.CACTUS_SMALL -> drawSingleCactus(xPx + 6f * u, 34f, 10f)
        DinoObstacleKind.CACTUS_TALL -> drawSingleCactus(xPx + 6f * u, 46f, 12f)
        DinoObstacleKind.CACTUS_TRIPLE -> {
            drawSingleCactus(xPx + 4f * u, 36f, 9.5f)
            drawSingleCactus(xPx + 19f * u, 42f, 10.5f)
            drawSingleCactus(xPx + 35f * u, 32f, 9f)
        }
        DinoObstacleKind.PTERODACTYL -> {
            val birdColor = if (isNight) Color(0xFFFBBF24) else Color(0xFF7C3AED)
            val bottomY = if (ob.flyLevel == 1) groundPx - 32f * u else groundPx - 10f * u
            val cy = bottomY - 11f * u
            // Body & Beak
            drawRoundRect(
                color = birdColor,
                topLeft = Offset(xPx + 8f * u, cy - 4f * u),
                size = Size(28f * u, 9f * u),
                cornerRadius = CornerRadius(3.5f * u, 3.5f * u),
            )
            val beak = Path().apply {
                moveTo(xPx + 10f * u, cy - 3.5f * u)
                lineTo(xPx - 2f * u, cy + 1f * u)
                lineTo(xPx + 10f * u, cy + 4f * u)
                close()
            }
            drawPath(beak, birdColor)
            // Flapping wing
            val wingUp = ((animTick * 2f).toInt() % 2 == 0)
            val wing = Path().apply {
                moveTo(xPx + 15f * u, cy)
                lineTo(xPx + 23f * u, if (wingUp) cy - 13f * u else cy + 12f * u)
                lineTo(xPx + 30f * u, cy)
                close()
            }
            drawPath(wing, birdColor)
        }
    }
}
