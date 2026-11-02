package com.example.bible.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.random.Random

private enum class DinoObstacleKind {
    CACTUS_SMALL,
    CACTUS_TALL,
    CACTUS_TRIPLE,
    PTERODACTYL,
}

private data class DinoObstacle(
    val id: Long,
    val x: Float, // in virtual units (0..1000)
    val kind: DinoObstacleKind,
    val flyLevel: Int = 0, // 0 = ground, 1 = mid (can duck or jump), 2 = high (walk under / duck)
)

private data class DinoCloud(
    val x: Float,
    val y: Float,
    val scale: Float,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsDinoGameScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val defaultEasy = family.resolvedMode() == KidsAgeMode.SMALL
    val prefs = remember { context.getSharedPreferences("kids_dino_prefs", Context.MODE_PRIVATE) }

    var easyMode by remember { mutableStateOf(defaultEasy) }
    var running by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var ducking by remember { mutableStateOf(false) }

    // Virtual coordinate system: width = 1000f, groundY = 380f, height = 450f
    var dinoY by remember { mutableFloatStateOf(0f) } // height above ground (0 = on ground)
    var dinoVy by remember { mutableFloatStateOf(0f) }
    var distance by remember { mutableFloatStateOf(0f) }
    var score by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(prefs.getInt("high_score", 0)) }
    var animTick by remember { mutableFloatStateOf(0f) }
    var rewardedHundreds by remember { mutableIntStateOf(0) }
    var winRound by remember { mutableIntStateOf(0) }
    var milestoneBanner by remember { mutableStateOf<String?>(null) }
    var nextObstacleId by remember { mutableLongStateOf(1L) }

    val obstacles = remember { mutableStateListOf<DinoObstacle>() }
    val clouds = remember {
        mutableStateListOf(
            DinoCloud(180f, 85f, 1.0f),
            DinoCloud(520f, 60f, 1.25f),
            DinoCloud(840f, 105f, 0.9f),
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
                x = 980f,
                kind = DinoObstacleKind.CACTUS_SMALL,
            ),
        )
        KidsGameAudio.play(KidsSfx.TAP)
    }

    fun triggerJump() {
        if (gameOver) {
            resetAndStart()
            return
        }
        if (!running) {
            resetAndStart()
            return
        }
        if (dinoY <= 4f) {
            ducking = false
            dinoVy = if (easyMode) 960f else 1020f
            KidsGameAudio.play(KidsSfx.TAP)
        }
    }

    // 60 FPS game loop
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

                val baseSpeed = if (easyMode) 370f else 460f
                val maxSpeed = if (easyMode) 680f else 880f
                val speed = (baseSpeed + score * 1.45f).coerceAtMost(maxSpeed)

                animTick += dt * (speed / 70f)
                distance += speed * dt * 0.025f
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

                // Physics for Dino jump / gravity
                val gravity = if (ducking && dinoY > 0f) 4200f else 2550f
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
                    val nx = c.x - speed * 0.18f * dt
                    clouds[i] = if (nx < -140f) {
                        c.copy(x = 1100f + Random.nextFloat() * 150f, y = Random.nextFloat() * 90f + 45f)
                    } else {
                        c.copy(x = nx)
                    }
                }

                // Move obstacles
                val iter = obstacles.listIterator()
                while (iter.hasNext()) {
                    val ob = iter.next()
                    val obSpeed = if (ob.kind == DinoObstacleKind.PTERODACTYL) speed * 1.12f else speed
                    val nx = ob.x - obSpeed * dt
                    if (nx < -140f) {
                        iter.remove()
                    } else {
                        iter.set(ob.copy(x = nx))
                    }
                }

                // Spawn new obstacle if needed
                val lastX = obstacles.maxOfOrNull { it.x } ?: 0f
                val minGap = if (easyMode) 430f else 350f
                if (obstacles.isEmpty() || lastX < 1000f - minGap - Random.nextFloat() * 240f) {
                    val canSpawnBird = score >= 35
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
                            x = max(1060f, lastX + minGap + Random.nextFloat() * 220f),
                            kind = kind,
                            flyLevel = flyLevel,
                        ),
                    )
                }

                // Collision check in virtual coords (groundY = 380f, dinoX = 120f)
                val dinoW = if (ducking && dinoY <= 2f) 86f else 64f
                val dinoH = if (ducking && dinoY <= 2f) 52f else 88f
                val dinoRect = Rect(
                    left = 120f + 12f,
                    top = 380f - dinoY - dinoH + 10f,
                    right = 120f + dinoW - 12f,
                    bottom = 380f - dinoY - 6f,
                )

                for (ob in obstacles) {
                    val obRect = when (ob.kind) {
                        DinoObstacleKind.CACTUS_SMALL -> Rect(ob.x + 6f, 380f - 68f, ob.x + 42f, 380f)
                        DinoObstacleKind.CACTUS_TALL -> Rect(ob.x + 6f, 380f - 94f, ob.x + 48f, 380f)
                        DinoObstacleKind.CACTUS_TRIPLE -> Rect(ob.x + 6f, 380f - 78f, ob.x + 92f, 380f)
                        DinoObstacleKind.PTERODACTYL -> {
                            val bottomY = if (ob.flyLevel == 1) 380f - 62f else 380f - 18f
                            Rect(ob.x + 8f, bottomY - 42f, ob.x + 76f, bottomY)
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Динозаврик 🦖", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { resetAndStart() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Заново")
                    }
                    KidsAudioToggles()
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top bar with High Score, Current Score, and Speed Mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            if (isNight) listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                            else listOf(Color(0xFF059669), Color(0xFF0D9488)),
                        ),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "СЧЁТ: ${score.toString().padStart(5, '0')}",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                    )
                    Text(
                        "РЕКОРД: ${highScore.toString().padStart(5, '0')} · Каждые 100 очков = ⭐+2",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = easyMode,
                        onClick = { easyMode = true },
                        label = { Text("Легко", fontWeight = FontWeight.Bold) },
                    )
                    FilterChip(
                        selected = !easyMode,
                        onClick = { easyMode = false },
                        label = { Text("Быстро", fontWeight = FontWeight.Bold) },
                    )
                }
            }

            milestoneBanner?.let { banner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFEF3C7))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(banner, color = Color(0xFF92400E), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }

            // Main Chrome Dino Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .pointerInput(running, gameOver, easyMode) {
                        detectTapGestures(
                            onTap = { triggerJump() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val sx = size.width / 1000f
                    val sy = size.height / 450f
                    val groundPx = 380f * sy

                    // Sky background (Day / Night transition like Chrome)
                    val skyBrush = if (isNight) {
                        Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))
                    } else {
                        Brush.verticalGradient(listOf(Color(0xFFFFFBEB), Color(0xFFFEF3C7)))
                    }
                    drawRect(skyBrush)

                    // Sun or Moon & Stars
                    if (isNight) {
                        drawCircle(Color(0xFFFDE68A), radius = 26f * sy, center = Offset(860f * sx, 80f * sy))
                        drawCircle(Color(0xFF0F172A), radius = 22f * sy, center = Offset(870f * sx, 74f * sy))
                        val starPositions = listOf(140f to 60f, 310f to 95f, 490f to 50f, 680f to 85f)
                        starPositions.forEach { (stX, stY) ->
                            drawCircle(Color.White.copy(alpha = 0.75f), radius = 3.5f * sy, center = Offset(stX * sx, stY * sy))
                        }
                    } else {
                        drawCircle(Color(0xFFF59E0B).copy(alpha = 0.85f), radius = 32f * sy, center = Offset(860f * sx, 82f * sy))
                    }

                    // Clouds
                    val cloudColor = if (isNight) Color(0xFF334155) else Color.White.copy(alpha = 0.92f)
                    clouds.forEach { c ->
                        drawRoundRect(
                            color = cloudColor,
                            topLeft = Offset(c.x * sx, c.y * sy),
                            size = Size(95f * c.scale * sx, 30f * c.scale * sy),
                            cornerRadius = CornerRadius(16f * sy, 16f * sy),
                        )
                        drawCircle(
                            color = cloudColor,
                            radius = 22f * c.scale * sy,
                            center = Offset((c.x + 32f * c.scale) * sx, (c.y + 4f * c.scale) * sy),
                        )
                        drawCircle(
                            color = cloudColor,
                            radius = 18f * c.scale * sy,
                            center = Offset((c.x + 64f * c.scale) * sx, (c.y + 8f * c.scale) * sy),
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
                        strokeWidth = 4f * sy,
                        cap = StrokeCap.Round,
                    )
                    val scrollOffset = (animTick * 42f) % 120f
                    for (k in 0..10) {
                        val px = ((k * 120f - scrollOffset + 1200f) % 1100f) * sx
                        drawLine(
                            color = groundColor.copy(alpha = 0.45f),
                            start = Offset(px, groundPx + 14f * sy),
                            end = Offset(px + 22f * sx, groundPx + 14f * sy),
                            strokeWidth = 3f * sy,
                            cap = StrokeCap.Round,
                        )
                        drawCircle(
                            color = groundColor.copy(alpha = 0.35f),
                            radius = 2.5f * sy,
                            center = Offset(px + 55f * sx, groundPx + 24f * sy),
                        )
                    }

                    // Draw Obstacles (Cacti & Pterodactyls)
                    obstacles.forEach { ob ->
                        drawObstacle(ob, sx, sy, groundPx, animTick, isNight)
                    }

                    // Draw Chrome T-Rex Dino
                    drawDino(
                        dinoX = 120f,
                        dinoY = dinoY,
                        ducking = ducking && dinoY <= 2f,
                        gameOver = gameOver,
                        animTick = animTick,
                        sx = sx,
                        sy = sy,
                        groundPx = groundPx,
                        isNight = isNight,
                    )
                }

                // Start or Game Over overlay card
                if (!running || gameOver) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.86f))
                            .clickable { resetAndStart() }
                            .padding(horizontal = 28.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            if (gameOver) "💥 ОЙ, КАКТУС!" else "🦖 ДИНОЗАВРИК",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            if (gameOver) {
                                "Твой счёт: $score · Рекорд: $highScore\nНажми на экран, чтобы побежать снова!"
                            } else {
                                "Нажимай на экран или кнопку «Прыжок»,\nчтобы перепрыгивать кактусы и птиц!"
                            },
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { resetAndStart() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(
                                if (gameOver) "🔄 Играть ещё раз" else "▶️ Старт!",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }

            // Big kid-friendly Jump & Duck controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { triggerJump() },
                    modifier = Modifier
                        .weight(1.4f)
                        .height(64.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                    Text(
                        " ПРЫЖОК",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                    )
                }

                Button(
                    onClick = {
                        if (!running || gameOver) {
                            resetAndStart()
                        } else {
                            ducking = !ducking
                            if (dinoY > 0f && ducking) {
                                dinoVy = -700f
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (ducking) Color(0xFFD97706) else Color(0xFF475569),
                    ),
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    Text(
                        if (ducking) " ВСТАТЬ" else " ПРИСЕСТЬ",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawDino(
    dinoX: Float,
    dinoY: Float,
    ducking: Boolean,
    gameOver: Boolean,
    animTick: Float,
    sx: Float,
    sy: Float,
    groundPx: Float,
    isNight: Boolean,
) {
    val bodyColor = if (isNight) Color(0xFF34D399) else Color(0xFF047857)
    val bellyColor = if (isNight) Color(0xFFA7F3D0) else Color(0xFF6EE7B7)
    val basePxX = dinoX * sx
    val basePxY = groundPx - dinoY * sy

    if (ducking) {
        // Ducking horizontal T-Rex
        val topY = basePxY - 54f * sy
        // Body
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(basePxX, topY + 10f * sy),
            size = Size(62f * sx, 30f * sy),
            cornerRadius = CornerRadius(8f * sx, 8f * sy),
        )
        // Head stretched forward
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(basePxX + 48f * sx, topY + 4f * sy),
            size = Size(40f * sx, 26f * sy),
            cornerRadius = CornerRadius(6f * sx, 6f * sy),
        )
        // Eye
        drawCircle(Color.White, radius = 4.5f * sy, center = Offset(basePxX + 72f * sx, topY + 12f * sy))
        drawCircle(Color(0xFF111827), radius = 2.3f * sy, center = Offset(basePxX + 73f * sx, topY + 12f * sy))
    } else {
        // Standing / jumping T-Rex in classic Chrome silhouette
        val topY = basePxY - 90f * sy
        // Tail
        val tail = Path().apply {
            moveTo(basePxX + 12f * sx, topY + 38f * sy)
            lineTo(basePxX - 16f * sx, topY + 24f * sy)
            lineTo(basePxX - 8f * sx, topY + 58f * sy)
            close()
        }
        drawPath(tail, bodyColor)
        // Torso
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(basePxX + 6f * sx, topY + 24f * sy),
            size = Size(38f * sx, 44f * sy),
            cornerRadius = CornerRadius(8f * sx, 8f * sy),
        )
        // Belly highlight
        drawRoundRect(
            color = bellyColor.copy(alpha = 0.5f),
            topLeft = Offset(basePxX + 28f * sx, topY + 32f * sy),
            size = Size(12f * sx, 30f * sy),
            cornerRadius = CornerRadius(5f * sx, 5f * sy),
        )
        // Head & Snout
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(basePxX + 20f * sx, topY),
            size = Size(44f * sx, 30f * sy),
            cornerRadius = CornerRadius(7f * sx, 7f * sy),
        )
        // Mouth line
        drawLine(
            color = Color(0xFF064E3B),
            start = Offset(basePxX + 44f * sx, topY + 22f * sy),
            end = Offset(basePxX + 62f * sx, topY + 22f * sy),
            strokeWidth = 2.5f * sy,
        )
        // Eye (surprised wide eye on Game Over)
        val eyeCenter = Offset(basePxX + 36f * sx, topY + 10f * sy)
        drawCircle(Color.White, radius = if (gameOver) 6f * sy else 4.5f * sy, center = eyeCenter)
        drawCircle(Color(0xFF111827), radius = 2.5f * sy, center = eyeCenter + Offset(1f * sx, 0f))
        // Tiny arm
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(basePxX + 42f * sx, topY + 38f * sy),
            size = Size(14f * sx, 7f * sy),
            cornerRadius = CornerRadius(3f * sx, 3f * sy),
        )
    }

    // Alternating running legs
    val step = ((animTick * 2.4f).toInt() % 2 == 0)
    val leftLegLift = if (dinoY > 2f) 4f else if (step) 8f else 0f
    val rightLegLift = if (dinoY > 2f) 4f else if (!step) 8f else 0f
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(basePxX + 12f * sx, basePxY - (22f + leftLegLift) * sy),
        size = Size(10f * sx, 22f * sy),
        cornerRadius = CornerRadius(3f * sx, 3f * sy),
    )
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(basePxX + 28f * sx, basePxY - (22f + rightLegLift) * sy),
        size = Size(10f * sx, 22f * sy),
        cornerRadius = CornerRadius(3f * sx, 3f * sy),
    )
}

private fun DrawScope.drawObstacle(
    ob: DinoObstacle,
    sx: Float,
    sy: Float,
    groundPx: Float,
    animTick: Float,
    isNight: Boolean,
) {
    val cactusColor = if (isNight) Color(0xFF22C55E) else Color(0xFF15803D)
    val xPx = ob.x * sx

    fun drawSingleCactus(cx: Float, h: Float, w: Float) {
        val top = groundPx - h * sy
        // Main stem
        drawRoundRect(
            color = cactusColor,
            topLeft = Offset(cx, top),
            size = Size(w * sx, h * sy),
            cornerRadius = CornerRadius(6f * sx, 6f * sy),
        )
        // Left branch
        drawRoundRect(
            color = cactusColor,
            topLeft = Offset(cx - 10f * sx, top + h * 0.28f * sy),
            size = Size(12f * sx, h * 0.34f * sy),
            cornerRadius = CornerRadius(4f * sx, 4f * sy),
        )
        // Right branch
        drawRoundRect(
            color = cactusColor,
            topLeft = Offset(cx + (w - 2f) * sx, top + h * 0.18f * sy),
            size = Size(12f * sx, h * 0.36f * sy),
            cornerRadius = CornerRadius(4f * sx, 4f * sy),
        )
    }

    when (ob.kind) {
        DinoObstacleKind.CACTUS_SMALL -> drawSingleCactus(xPx + 10f * sx, 68f, 22f)
        DinoObstacleKind.CACTUS_TALL -> drawSingleCactus(xPx + 10f * sx, 94f, 26f)
        DinoObstacleKind.CACTUS_TRIPLE -> {
            drawSingleCactus(xPx + 6f * sx, 72f, 20f)
            drawSingleCactus(xPx + 36f * sx, 82f, 22f)
            drawSingleCactus(xPx + 66f * sx, 64f, 18f)
        }
        DinoObstacleKind.PTERODACTYL -> {
            val birdColor = if (isNight) Color(0xFFFBBF24) else Color(0xFF7C3AED)
            val bottomY = if (ob.flyLevel == 1) groundPx - 62f * sy else groundPx - 18f * sy
            val cy = bottomY - 22f * sy
            // Body & Beak
            drawRoundRect(
                color = birdColor,
                topLeft = Offset(xPx + 14f * sx, cy - 8f * sy),
                size = Size(54f * sx, 18f * sy),
                cornerRadius = CornerRadius(6f * sx, 6f * sy),
            )
            val beak = Path().apply {
                moveTo(xPx + 18f * sx, cy - 6f * sy)
                lineTo(xPx - 4f * sx, cy + 2f * sy)
                lineTo(xPx + 18f * sx, cy + 8f * sy)
                close()
            }
            drawPath(beak, birdColor)
            // Flapping wing
            val wingUp = ((animTick * 2f).toInt() % 2 == 0)
            val wing = Path().apply {
                moveTo(xPx + 28f * sx, cy)
                lineTo(xPx + 42f * sx, if (wingUp) cy - 26f * sy else cy + 24f * sy)
                lineTo(xPx + 56f * sx, cy)
                close()
            }
            drawPath(wing, birdColor)
        }
    }
}
