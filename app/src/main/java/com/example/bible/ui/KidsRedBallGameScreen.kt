package com.example.bible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.KidsGames
import com.example.bible.games.KidsGameAudio
import com.example.bible.games.KidsSfx
import com.example.bible.games.RedBallSim
import kotlinx.coroutines.isActive

@Composable
fun KidsRedBallGameScreen(onBack: () -> Unit) {
    val sim = remember { RedBallSim() }
    val held = remember { intArrayOf(0, 0) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(sim) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.033f)
                    val move = (held[1] - held[0]).toFloat()
                    sim.step(dt, move)
                }
                last = now
                tick++
            }
        }
    }
    LaunchedEffect(tick) {
        if (sim.consumeLose()) KidsGameAudio.play(KidsSfx.LOSE)
        if (sim.consumeSpring()) KidsGameAudio.play(KidsSfx.PLACE)
        if (sim.consumeWin()) KidsGameAudio.play(KidsSfx.WIN)
    }
    KidsGameMusic(com.example.bible.games.KidsMusicTrack.GAMES)
    KidsGameWinReward(KidsGames.RED_BALL, sim.won, points = 20, round = if (sim.won) 1 else 0)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF7DD3FC)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val frame = tick
            drawLevel(sim)
        }
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color(0xFF0F172A))
            }
            Text("Красный мяч  ·  уровень 1", fontWeight = FontWeight.Black, color = Color(0xFF0F172A), fontSize = 18.sp)
        }
        IconButton(
            onClick = { sim.reset() },
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopEnd),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Заново", tint = Color(0xFF0F172A))
        }
        if (sim.won || sim.dead) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.94f))
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (sim.won) "Дверь открыта!" else "Ещё разок",
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    color = Color(0xFF7F1D1D),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (sim.won) "Мяч докатился сам, как надо" else "Ящик закрывает яму, пружина подбрасывает наверх",
                    color = Color(0xFF44403C),
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFDC2626))
                        .kidsPressable(onClick = { sim.reset() })
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Text("Ещё раз", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HoldPad("◀", onHold = { held[0] = if (it) 1 else 0 })
            Text("держи", fontWeight = FontWeight.Black, color = Color(0xFF7F1D1D), fontSize = 16.sp)
            HoldPad("▶", onHold = { held[1] = if (it) 1 else 0 })
        }
    }
}

@Composable
private fun HoldPad(label: String, onHold: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(92.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.88f))
            .pointerInput(onHold) {
                awaitEachGesture {
                    awaitFirstDown()
                    onHold(true)
                    waitForUpOrCancellation()
                    onHold(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color(0xFFB91C1C))
    }
}

private fun DrawScope.drawLevel(sim: RedBallSim) {
    val viewH = 620f
    val scale = size.height / viewH
    val viewW = size.width / scale
    val camX = (sim.ballX - viewW * 0.38f).coerceIn(0f, RedBallSim.WORLD_W - viewW)
    val camY = (sim.ballY - viewH * 0.62f).coerceIn(180f, RedBallSim.WORLD_H - viewH)
    withTransform({
        scale(scale, scale, pivot = Offset.Zero)
        translate(-camX, -camY)
    }) {
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFF7DD3FC), Color(0xFFE0F2FE), Color(0xFFD9F99D)),
                startY = camY,
                endY = camY + viewH,
            ),
            topLeft = Offset(camX, camY),
            size = Size(viewW, viewH),
        )
        drawCloud(camX + viewW * 0.15f, 260f, 90f)
        drawCloud(camX + viewW * 0.55f, 220f, 70f)
        drawCircle(Color(0xFFFDE68A), 46f, Offset(camX + viewW * 0.82f, camY + 70f))
        for (solid in RedBallSim.SOLIDS) {
            if (solid.t <= 0f || solid.l < 0f) continue
            drawDirt(solid.l, solid.t, solid.r - solid.l, solid.b - solid.t)
        }
        val slope = RedBallSim.SLOPES.first()
        val hill = Path().apply {
            moveTo(slope.x0, slope.y0)
            lineTo(slope.x1, slope.y1)
            lineTo(slope.x1, 980f)
            lineTo(slope.x0, 980f)
            close()
        }
        drawPath(hill, Color(0xFF92400E))
        drawPath(
            Path().apply {
                moveTo(slope.x0, slope.y0)
                lineTo(slope.x1, slope.y1)
            },
            Color(0xFF16A34A),
            style = Stroke(width = 18f),
        )
        for (spike in RedBallSim.SPIKES) {
            val path = Path().apply {
                moveTo(spike.x, spike.top)
                lineTo(spike.x - spike.w / 2f, spike.top + spike.h)
                lineTo(spike.x + spike.w / 2f, spike.top + spike.h)
                close()
            }
            drawPath(path, Color(0xFFDC2626))
        }
        drawCrate(sim.crateX, sim.crateY, RedBallSim.CRATE)
        drawSpring()
        drawDoor()
        drawBall(sim.ballX, sim.ballY, RedBallSim.BALL_R, sim.ballAngle)
    }
}

private fun DrawScope.drawCloud(x: Float, y: Float, w: Float) {
    drawCircle(Color.White.copy(alpha = 0.9f), w * 0.28f, Offset(x, y))
    drawCircle(Color.White.copy(alpha = 0.9f), w * 0.36f, Offset(x + w * 0.28f, y - w * 0.08f))
    drawCircle(Color.White.copy(alpha = 0.9f), w * 0.24f, Offset(x + w * 0.55f, y))
}

private fun DrawScope.drawDirt(x: Float, y: Float, w: Float, h: Float) {
    drawRoundRect(Color(0xFF92400E), Offset(x, y), Size(w, h), CornerRadius(8f, 8f))
    drawRoundRect(Color(0xFF16A34A), Offset(x, y), Size(w, 16f), CornerRadius(8f, 8f))
}

private fun DrawScope.drawCrate(cx: Float, cy: Float, side: Float) {
    val x = cx - side / 2f
    val y = cy - side / 2f
    drawRoundRect(Color(0xFFB45309), Offset(x, y), Size(side, side), CornerRadius(8f, 8f))
    drawRoundRect(
        Color(0xFF78350F),
        Offset(x, y),
        Size(side, side),
        CornerRadius(8f, 8f),
        style = Stroke(width = 6f),
    )
    drawLine(Color(0xFF78350F), Offset(x + 8f, y + 8f), Offset(x + side - 8f, y + side - 8f), 5f)
    drawLine(Color(0xFF78350F), Offset(x + side - 8f, y + 8f), Offset(x + 8f, y + side - 8f), 5f)
}

private fun DrawScope.drawSpring() {
    val s = RedBallSim.SPRING
    drawRoundRect(Color(0xFFCA8A04), Offset(s.l, s.b - 10f), Size(s.r - s.l, 10f), CornerRadius(4f, 4f))
    var y = s.b - 16f
    while (y > s.t) {
        drawArc(
            Color(0xFFFACC15),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(s.l + 6f, y - 14f),
            size = Size(s.r - s.l - 12f, 22f),
            style = Stroke(width = 5f),
        )
        y -= 14f
    }
}

private fun DrawScope.drawDoor() {
    val d = RedBallSim.DOOR
    drawRoundRect(Color(0xFF7C2D12), Offset(d.l, d.t), Size(d.r - d.l, d.b - d.t), CornerRadius(18f, 18f))
    drawRoundRect(
        Color(0xFFFBBF24),
        Offset(d.l + 10f, d.t + 14f),
        Size(d.r - d.l - 20f, d.b - d.t - 28f),
        CornerRadius(12f, 12f),
        style = Stroke(width = 6f),
    )
    drawCircle(Color(0xFFFBBF24), 7f, Offset(d.r - 24f, (d.t + d.b) / 2f))
    drawCircle(Color(0xFFFDE68A), 16f, Offset((d.l + d.r) / 2f, d.t - 28f))
}

private fun DrawScope.drawBall(x: Float, y: Float, r: Float, angle: Float) {
    drawCircle(Color(0xFF7F1D1D).copy(alpha = 0.25f), r, Offset(x + 4f, y + 8f))
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFECACA), Color(0xFFEF4444), Color(0xFF991B1B)),
            center = Offset(x - r * 0.35f, y - r * 0.4f),
            radius = r * 1.8f,
        ),
        r,
        Offset(x, y),
    )
    rotate(Math.toDegrees(angle.toDouble()).toFloat(), Offset(x, y)) {
        drawArc(
            Color(0xFF7F1D1D),
            startAngle = -20f,
            sweepAngle = 40f,
            useCenter = false,
            topLeft = Offset(x - r, y - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = r * 0.28f),
        )
    }
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.18f, Offset(x - r * 0.35f, y - r * 0.38f))
}
