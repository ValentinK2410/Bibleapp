package com.example.bible.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.bible.R
import com.example.bible.data.KidsBibleStory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun ang(turns: Float): Float = (turns.toDouble() * PI).toFloat()

internal fun kidsStoryImage(id: String): Int = when (id) {
    "creation" -> R.drawable.kids_story_creation
    "noah" -> R.drawable.kids_story_noah
    "abraham" -> R.drawable.kids_story_abraham
    "joseph" -> R.drawable.kids_story_joseph
    "moses-sea" -> R.drawable.kids_story_moses_sea
    "david" -> R.drawable.kids_story_david
    "daniel" -> R.drawable.kids_story_daniel
    "jonah" -> R.drawable.kids_story_jonah
    "nativity" -> R.drawable.kids_story_nativity
    "five-loaves" -> R.drawable.kids_story_five_loaves
    "lost-sheep" -> R.drawable.kids_story_lost_sheep
    else -> R.drawable.kids_story_creation
}

/** Движение картинки под смысл абзаца. У каждой истории пять абзацев. */
private enum class StoryBeat {
    Night, Dawn, Garden, Creatures, Family,
    Rain, Ark, Pairs, Flood, Rainbow,
    Stars, Journey, Promise,
    Coat, Descent, Egypt, Reunion,
    March, Fear, PartSea, Song,
    Flock, Giant, Sling, Victory,
    Prayer, Lions, Angel,
    Sail, Storm, Whale, Shore, City,
    Travel, Stable, Manger, Angels, Shepherds,
    Crowd, Gift, Multiply, Baskets,
    Wander, Search, Carry, Together,
    Heart,
}

private fun beatsOf(storyId: String): List<StoryBeat> = when (storyId) {
    "creation" -> listOf(StoryBeat.Night, StoryBeat.Dawn, StoryBeat.Garden, StoryBeat.Creatures, StoryBeat.Family)
    "noah" -> listOf(StoryBeat.Rain, StoryBeat.Ark, StoryBeat.Pairs, StoryBeat.Flood, StoryBeat.Rainbow)
    "abraham" -> listOf(StoryBeat.Journey, StoryBeat.Stars, StoryBeat.Stars, StoryBeat.Promise, StoryBeat.Family)
    "joseph" -> listOf(StoryBeat.Coat, StoryBeat.Descent, StoryBeat.Egypt, StoryBeat.Journey, StoryBeat.Reunion)
    "moses-sea" -> listOf(StoryBeat.March, StoryBeat.Fear, StoryBeat.Prayer, StoryBeat.PartSea, StoryBeat.Song)
    "david" -> listOf(StoryBeat.Flock, StoryBeat.Giant, StoryBeat.Sling, StoryBeat.Sling, StoryBeat.Victory)
    "daniel" -> listOf(StoryBeat.Prayer, StoryBeat.Fear, StoryBeat.Lions, StoryBeat.Night, StoryBeat.Angel)
    "jonah" -> listOf(StoryBeat.City, StoryBeat.Sail, StoryBeat.Storm, StoryBeat.Whale, StoryBeat.Shore)
    "nativity" -> listOf(StoryBeat.Travel, StoryBeat.Stable, StoryBeat.Manger, StoryBeat.Angels, StoryBeat.Shepherds)
    "five-loaves" -> listOf(StoryBeat.Crowd, StoryBeat.Gift, StoryBeat.Prayer, StoryBeat.Multiply, StoryBeat.Baskets)
    "lost-sheep" -> listOf(StoryBeat.Flock, StoryBeat.Wander, StoryBeat.Search, StoryBeat.Carry, StoryBeat.Together)
    else -> listOf(StoryBeat.Heart)
}

/**
 * Иллюстрация истории, которая оживает под смысл текущего абзаца:
 * свет, дождь, море, звёзды, хлеб и остальные сюжеты рисуются поверх картины.
 */
@Composable
internal fun KidsStoryStage(
    story: KidsBibleStory,
    sceneIndex: Int,
    lessonMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val beats = beatsOf(story.id)
    val beat = if (lessonMode) StoryBeat.Heart else beats[sceneIndex.coerceIn(0, beats.lastIndex)]
    val transition = rememberInfiniteTransition(label = "story-stage")
    val loop by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4800, easing = LinearEasing)),
        label = "loop",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(modifier.clipToBounds()) {
        Image(
            painter = painterResource(kidsStoryImage(story.id)),
            contentDescription = story.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val bob = when (beat) {
                        StoryBeat.Ark, StoryBeat.Sail, StoryBeat.Storm, StoryBeat.Whale -> sin(ang(loop * 2f)) * 14f
                        else -> sin(ang(loop * 2f)) * 6f
                    }
                    val zoom = 1.06f + pulse * 0.04f
                    scaleX = zoom
                    scaleY = zoom
                    translationY = bob
                    translationX = cos(ang(loop * 2f)) * 8f
                },
        )
        Canvas(Modifier.fillMaxSize()) {
            drawBeat(beat, loop, pulse)
        }
    }
}

private fun DrawScope.drawBeat(beat: StoryBeat, t: Float, pulse: Float) {
    val w = size.width
    val h = size.height
    when (beat) {
        StoryBeat.Night -> {
            drawRect(Color.Black.copy(alpha = 0.28f + (1f - pulse) * 0.22f))
            repeat(10) { i ->
                val a = 0.25f + ((sin(ang(t * 2f + i)) + 1f) / 2f) * 0.75f
                star(
                    Offset(w * ((i * 67 % 90) / 100f + 0.05f), h * ((i * 19 % 24) / 100f + 0.04f)),
                    w * 0.016f,
                    Color.White.copy(alpha = a),
                )
            }
        }
        StoryBeat.Dawn -> {
            val cx = w * 0.5f
            val cy = h * 0.22f
            drawCircle(Color(0xFFFFF3C4).copy(alpha = 0.35f + pulse * 0.25f), w * 0.16f, Offset(cx, cy))
            repeat(10) { i ->
                val a = ang(i / 10f * 2f + t * 2f)
                drawLine(
                    Color(0xFFFDE68A).copy(alpha = 0.85f),
                    Offset(cx, cy),
                    Offset(cx + cos(a).toFloat() * w * 0.42f, cy + sin(a).toFloat() * h * 0.28f),
                    strokeWidth = 8f,
                )
            }
        }
        StoryBeat.Garden -> {
            repeat(6) { i ->
                val x = w * (0.12f + i * 0.15f)
                val grow = (0.35f + pulse * 0.65f) * (0.7f + (i % 3) * 0.15f)
                val stem = h * 0.22f * grow
                val base = h * 0.92f
                drawLine(Color(0xFF16A34A), Offset(x, base), Offset(x, base - stem), strokeWidth = 6f)
                val petal = listOf(Color(0xFFF472B6), Color(0xFFFBBF24), Color(0xFFFB7185), Color(0xFFA78BFA))
                drawCircle(petal[i % 4].copy(alpha = 0.95f), 16f + pulse * 8f, Offset(x, base - stem))
            }
        }
        StoryBeat.Creatures -> {
            repeat(3) { i ->
                val x = ((t + i * 0.28f) % 1f) * (w + 80f) - 40f
                val y = h * (0.62f + i * 0.1f) + sin(ang((t + i) * 2f)) * 10f
                fish(Offset(x, y), w * 0.11f, Color(0xFF38BDF8), flip = false)
            }
            repeat(3) { i ->
                val x = w - ((t * 0.8f + i * 0.3f) % 1f) * (w + 60f)
                val y = h * (0.12f + i * 0.08f) + sin(ang((t + i) * 2f)) * 12f
                drawCircle(Color(0xFFF8FAFC), 10f, Offset(x, y))
                drawCircle(Color(0xFFF8FAFC), 7f, Offset(x + 14f, y - 6f))
                drawLine(Color(0xFF94A3B8), Offset(x - 8f, y), Offset(x - 22f, y - 8f), strokeWidth = 3f)
            }
        }
        StoryBeat.Family, StoryBeat.Together, StoryBeat.Heart -> {
            person(Offset(w * 0.38f, h * 0.72f), w * 0.12f, Color(0xFFFBBF24))
            person(Offset(w * 0.58f, h * 0.74f), w * 0.1f, Color(0xFF86EFAC))
            repeat(5) { i ->
                val p = (t + i * 0.18f) % 1f
                heart(
                    Offset(w * (0.25f + i * 0.12f), h * (0.7f - p * 0.55f)),
                    w * 0.07f,
                    Color(0xFFF472B6).copy(alpha = 1f - p),
                )
            }
        }
        StoryBeat.Rain, StoryBeat.Flood -> rain(t, if (beat == StoryBeat.Flood) 28 else 16, beat == StoryBeat.Flood)
        StoryBeat.Ark, StoryBeat.Sail -> {
            wave(h * 0.78f, t, Color(0xFF38BDF8).copy(alpha = 0.85f))
            if (beat == StoryBeat.Sail) rain(t, 8, false)
        }
        StoryBeat.Pairs -> {
            repeat(4) { i ->
                val x = ((t * 0.6f + i * 0.22f) % 1f) * w
                val y = h * 0.78f
                drawCircle(Color(0xFFFBBF24), 14f, Offset(x, y))
                drawCircle(Color(0xFFFB7185), 14f, Offset(x + 28f, y))
            }
        }
        StoryBeat.Rainbow -> {
            rain(t, 6, false)
            val colors = listOf(0xFFEF4444, 0xFFF97316, 0xFFEAB308, 0xFF22C55E, 0xFF3B82F6)
            colors.forEachIndexed { i, c ->
                drawArc(
                    Color(c).copy(alpha = 0.9f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.1f, h * (0.28f + i * 0.025f)),
                    size = Size(w * 0.8f, h * 0.7f),
                    style = Stroke(width = 10f),
                )
            }
        }
        StoryBeat.Stars, StoryBeat.Promise, StoryBeat.Angels -> {
            repeat(14) { i ->
                val tw = 0.35f + ((sin(ang(t * 4f + i)) + 1f) / 2f) * 0.65f
                star(
                    Offset(w * ((i * 53 % 92) / 100f + 0.04f), h * ((i * 29 % 48) / 100f + 0.06f)),
                    if (i == 3) w * 0.045f * (0.8f + pulse * 0.4f) else w * 0.016f,
                    Color(0xFFFEF9C3).copy(alpha = tw),
                )
            }
            if (beat == StoryBeat.Promise) {
                drawCircle(Color(0xFFFFF7ED).copy(alpha = 0.45f + pulse * 0.3f), w * 0.08f, Offset(w * 0.5f, h * 0.72f))
            }
        }
        StoryBeat.Journey, StoryBeat.Travel, StoryBeat.Shepherds -> {
            val x = w * (0.08f + t * 0.75f)
            person(Offset(x, h * 0.75f), w * 0.09f, Color(0xFFFDE68A))
            if (beat == StoryBeat.Shepherds) {
                drawCircle(Color.White, 12f, Offset(x + w * 0.08f, h * 0.8f))
                star(Offset(w * 0.72f, h * 0.16f), w * 0.05f, Color(0xFFFDE68A))
            }
        }
        StoryBeat.Coat -> {
            val colors = listOf(0xFFEF4444, 0xFFF97316, 0xFFEAB308, 0xFF22C55E, 0xFF3B82F6, 0xFFA855F7)
            colors.forEachIndexed { i, c ->
                val shift = sin(ang(t * 2f + i * 0.4f)) * 8f
                drawRoundRect(
                    Color(c).copy(alpha = 0.9f),
                    topLeft = Offset(w * 0.62f + shift, h * (0.42f + i * 0.06f)),
                    size = Size(w * 0.22f, h * 0.05f),
                    cornerRadius = CornerRadius(12f, 12f),
                )
            }
        }
        StoryBeat.Descent -> {
            val y = h * (0.2f + t * 0.55f)
            person(Offset(w * 0.5f, y), w * 0.09f, Color(0xFF93C5FD))
        }
        StoryBeat.Egypt -> {
            drawPath(
                Path().apply {
                    moveTo(w * 0.15f, h * 0.78f)
                    lineTo(w * 0.32f, h * 0.48f)
                    lineTo(w * 0.49f, h * 0.78f)
                    close()
                },
                Color(0xFFF59E0B).copy(alpha = 0.85f),
            )
            drawCircle(Color(0xFFFDE68A), w * 0.06f, Offset(w * 0.78f, h * 0.22f + pulse * 8f))
        }
        StoryBeat.Reunion -> {
            val meet = pulse
            person(Offset(w * (0.28f + meet * 0.1f), h * 0.74f), w * 0.1f, Color(0xFFFBBF24))
            person(Offset(w * (0.72f - meet * 0.1f), h * 0.74f), w * 0.1f, Color(0xFF86EFAC))
            heart(Offset(w * 0.5f, h * (0.42f - pulse * 0.08f)), w * 0.1f, Color(0xFFF472B6))
        }
        StoryBeat.March -> {
            repeat(5) { i ->
                val x = ((t * 0.5f + i * 0.12f) % 1f) * w
                person(Offset(x, h * 0.76f), w * 0.055f, Color(0xFFFDE68A))
            }
        }
        StoryBeat.Fear -> {
            wave(h * 0.7f, t, Color(0xFF0284C7).copy(alpha = 0.8f))
            person(Offset(w * 0.32f, h * 0.62f), w * 0.07f, Color(0xFFFDE68A))
            person(Offset(w * 0.42f, h * 0.64f), w * 0.06f, Color(0xFFFDBA74))
        }
        StoryBeat.PartSea -> {
            val gap = 0.18f + pulse * 0.28f
            drawRect(Color(0xFF0284C7).copy(alpha = 0.82f), Offset(0f, h * 0.55f), Size(w * (0.5f - gap), h * 0.45f))
            drawRect(Color(0xFF0369A1).copy(alpha = 0.82f), Offset(w * (0.5f + gap), h * 0.55f), Size(w * (0.5f - gap), h * 0.45f))
            person(Offset(w * 0.5f, h * 0.78f), w * 0.07f, Color(0xFFFDE68A))
        }
        StoryBeat.Song -> {
            repeat(6) { i ->
                val p = (t + i * 0.15f) % 1f
                val x = w * (0.2f + i * 0.12f)
                val y = h * (0.75f - p * 0.6f)
                drawCircle(Color(0xFFFDE68A).copy(alpha = 1f - p), 8f, Offset(x, y))
                drawLine(Color(0xFFFDE68A).copy(alpha = 1f - p), Offset(x + 8f, y), Offset(x + 8f, y - 22f), strokeWidth = 3f)
            }
        }
        StoryBeat.Flock -> {
            repeat(6) { i ->
                val x = w * (0.15f + (i % 3) * 0.18f) + sin(ang(t * 2f + i)) * 6f
                val y = h * (0.62f + i / 3 * 0.12f)
                drawCircle(Color.White, 16f, Offset(x, y))
                drawCircle(Color(0xFF64748B), 3f, Offset(x + 6f, y - 2f))
            }
        }
        StoryBeat.Giant -> {
            person(Offset(w * 0.72f, h * 0.58f), w * 0.2f, Color(0xFF94A3B8))
            person(Offset(w * 0.28f, h * 0.78f), w * 0.07f, Color(0xFFFDE68A))
        }
        StoryBeat.Sling -> {
            person(Offset(w * 0.22f, h * 0.76f), w * 0.08f, Color(0xFFFDE68A))
            person(Offset(w * 0.78f, h * 0.58f), w * 0.16f, Color(0xFF94A3B8))
            val stoneT = t
            val sx = w * (0.28f + stoneT * 0.48f)
            val sy = h * (0.7f - sin(ang(stoneT)) * 0.35f)
            drawCircle(Color(0xFF44403C), 10f, Offset(sx, sy))
        }
        StoryBeat.Victory -> {
            person(Offset(w * 0.4f, h * 0.72f), w * 0.09f, Color(0xFFFDE68A))
            repeat(8) { i ->
                val a = ang(i / 8f * 2f + t * 2f)
                drawCircle(Color(0xFFFDE68A), 5f, Offset(w * 0.4f + cos(a).toFloat() * w * 0.18f, h * 0.45f + sin(a).toFloat() * 30f))
            }
        }
        StoryBeat.Prayer -> {
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFFFFF7ED).copy(alpha = 0.55f), Color.Transparent)),
                Offset(w * 0.38f, 0f),
                Size(w * 0.24f, h * 0.55f),
            )
            person(Offset(w * 0.5f, h * 0.78f), w * 0.08f, Color(0xFFFDE68A))
        }
        StoryBeat.Lions -> {
            repeat(3) { i ->
                val x = w * (0.25f + i * 0.22f)
                drawOval(Color(0xFFF59E0B).copy(alpha = 0.9f), Offset(x, h * 0.7f), Size(w * 0.16f, h * 0.1f))
                val z = (t + i * 0.2f) % 1f
                drawCircle(Color.White.copy(alpha = 1f - z), 6f, Offset(x + w * 0.08f, h * 0.62f - z * 28f))
            }
        }
        StoryBeat.Angel -> {
            drawCircle(Color.White.copy(alpha = 0.35f + pulse * 0.35f), w * 0.12f, Offset(w * 0.5f, h * 0.28f))
            drawCircle(Color(0xFFFDE68A).copy(alpha = 0.9f), w * 0.04f, Offset(w * 0.5f, h * 0.28f))
            person(Offset(w * 0.5f, h * 0.78f), w * 0.08f, Color(0xFFFDE68A))
        }
        StoryBeat.Storm -> {
            rain(t, 24, true)
            wave(h * 0.72f, t, Color(0xFF1D4ED8).copy(alpha = 0.75f))
            drawLine(Color(0xFFFDE68A), Offset(w * 0.3f, h * 0.05f), Offset(w * 0.55f, h * 0.4f), strokeWidth = 6f)
        }
        StoryBeat.Whale -> {
            val x = w * (0.15f + t * 0.55f)
            drawOval(Color(0xFF2563EB).copy(alpha = 0.9f), Offset(x, h * 0.62f), Size(w * 0.42f, h * 0.2f))
            person(Offset(x + w * 0.16f, h * 0.58f - pulse * 10f), w * 0.05f, Color(0xFFFDE68A))
        }
        StoryBeat.Shore -> {
            wave(h * 0.8f, t, Color(0xFF38BDF8).copy(alpha = 0.8f))
            person(Offset(w * (0.35f + pulse * 0.15f), h * 0.7f), w * 0.08f, Color(0xFFFDE68A))
        }
        StoryBeat.City -> {
            repeat(4) { i ->
                drawRoundRect(
                    Color(0xFFFDE68A).copy(alpha = 0.8f),
                    Offset(w * (0.15f + i * 0.18f), h * (0.5f + (i % 2) * 0.06f)),
                    Size(w * 0.12f, h * 0.28f),
                    CornerRadius(8f, 8f),
                )
            }
        }
        StoryBeat.Stable, StoryBeat.Manger -> {
            star(Offset(w * 0.5f, h * 0.14f), w * (0.04f + pulse * 0.03f), Color(0xFFFDE68A))
            drawCircle(Color(0xFFFFF7ED).copy(alpha = 0.45f + pulse * 0.35f), w * 0.1f, Offset(w * 0.5f, h * 0.62f))
        }
        StoryBeat.Crowd -> {
            repeat(8) { i ->
                person(Offset(w * (0.12f + (i % 4) * 0.2f), h * (0.62f + i / 4 * 0.16f)), w * 0.05f, Color(0xFFFDE68A))
            }
        }
        StoryBeat.Gift -> {
            val x = w * (0.15f + t * 0.4f)
            person(Offset(x, h * 0.74f), w * 0.08f, Color(0xFF86EFAC))
            drawRoundRect(Color(0xFFFBBF24), Offset(x + 8f, h * 0.66f), Size(28f, 18f), CornerRadius(6f, 6f))
        }
        StoryBeat.Multiply -> {
            val n = 2 + (t * 10).toInt()
            repeat(n.coerceAtMost(12)) { i ->
                val col = i % 4
                val row = i / 4
                drawCircle(Color(0xFFFBBF24), 16f, Offset(w * (0.28f + col * 0.14f), h * (0.55f + row * 0.12f)))
                if (i % 5 == 4) fish(Offset(w * (0.3f + col * 0.1f), h * 0.78f), 36f, Color(0xFF38BDF8), false)
            }
        }
        StoryBeat.Baskets -> {
            repeat(6) { i ->
                val x = w * (0.15f + (i % 3) * 0.25f)
                val y = h * (0.58f + i / 3 * 0.16f)
                drawArc(Color(0xFFD97706), 200f, 140f, false, Offset(x, y), Size(w * 0.16f, h * 0.12f), style = Stroke(8f))
                drawCircle(Color(0xFFFBBF24), 7f, Offset(x + w * 0.06f, y))
            }
        }
        StoryBeat.Wander -> {
            repeat(4) { i -> drawCircle(Color.White, 14f, Offset(w * (0.55f + i * 0.08f), h * 0.72f)) }
            val x = w * (0.15f + sin(ang(t * 2f)) * 0.12f + 0.1f)
            drawCircle(Color.White, 16f, Offset(x, h * (0.5f + pulse * 0.08f)))
        }
        StoryBeat.Search -> {
            val x = w * (0.2f + t * 0.55f)
            person(Offset(x, h * 0.72f), w * 0.08f, Color(0xFFFDE68A))
            drawCircle(Color.White, 28f, Offset(x + 30f, h * 0.66f), style = Stroke(width = 4f))
        }
        StoryBeat.Carry -> {
            val x = w * (0.25f + t * 0.45f)
            person(Offset(x, h * 0.74f), w * 0.09f, Color(0xFFFDE68A))
            drawCircle(Color.White, 16f, Offset(x + 6f, h * 0.6f))
        }
    }
}

private fun DrawScope.rain(t: Float, count: Int, heavy: Boolean) {
    val w = size.width
    val h = size.height
    repeat(count) { i ->
        val x = w * ((i * 47 % 100) / 100f)
        val speed = if (heavy) 1.6f else 1f
        val y = ((t * speed + i * 0.083f) % 1f) * h
        drawLine(
            Color.White.copy(alpha = if (heavy) 0.7f else 0.55f),
            Offset(x, y),
            Offset(x - 8f, y + if (heavy) 26f else 16f),
            strokeWidth = if (heavy) 3f else 2f,
        )
    }
}

private fun DrawScope.wave(base: Float, t: Float, color: Color) {
    val w = size.width
    val h = size.height
    val path = Path()
    path.moveTo(0f, h)
    path.lineTo(0f, base)
    var x = 0f
    while (x <= w) {
        val y = base + sin(ang((x / w) * 4f + t * 2f)) * 14f
        path.lineTo(x, y)
        x += 12f
    }
    path.lineTo(w, h)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.person(c: Offset, s: Float, color: Color) {
    drawCircle(color, s * 0.32f, c + Offset(0f, -s * 0.85f))
    drawRoundRect(
        color,
        topLeft = Offset(c.x - s * 0.38f, c.y - s * 0.45f),
        size = Size(s * 0.76f, s),
        cornerRadius = CornerRadius(s * 0.2f, s * 0.2f),
    )
}

private fun DrawScope.fish(c: Offset, s: Float, color: Color, flip: Boolean) {
    val dir = if (flip) -1f else 1f
    drawOval(color, Offset(c.x - s * 0.45f, c.y - s * 0.22f), Size(s * 0.9f, s * 0.44f))
    val tail = Path().apply {
        moveTo(c.x - dir * s * 0.4f, c.y)
        lineTo(c.x - dir * s * 0.75f, c.y - s * 0.22f)
        lineTo(c.x - dir * s * 0.75f, c.y + s * 0.22f)
        close()
    }
    drawPath(tail, color)
    drawCircle(Color.White, s * 0.06f, c + Offset(dir * s * 0.18f, -s * 0.04f))
}

private fun DrawScope.heart(c: Offset, s: Float, color: Color) {
    drawCircle(color, s * 0.28f, c + Offset(-s * 0.16f, -s * 0.05f))
    drawCircle(color, s * 0.28f, c + Offset(s * 0.16f, -s * 0.05f))
    drawPath(
        Path().apply {
            moveTo(c.x - s * 0.4f, c.y)
            lineTo(c.x + s * 0.4f, c.y)
            lineTo(c.x, c.y + s * 0.48f)
            close()
        },
        color,
    )
}

private fun DrawScope.star(c: Offset, r: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else r * 0.42f
        val a = Math.toRadians(-90.0 + i * 36.0)
        val x = c.x + cos(a).toFloat() * rad
        val y = c.y + sin(a).toFloat() * rad
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}
