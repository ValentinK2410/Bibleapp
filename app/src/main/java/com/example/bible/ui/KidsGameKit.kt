package com.example.bible.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.games.KidsGameAudio
import com.example.bible.games.KidsSfx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class KidsAnswerState { Idle, Correct, Wrong, Dimmed }

internal val KidsCorrectGradient = listOf(Color(0xFF22C55E), Color(0xFF10B981))
internal val KidsWrongGradient = listOf(Color(0xFFEF4444), Color(0xFFF97316))

/** Серия верных ответов в играх Азбуки и счётчик для звёздного салюта. */
object KidsGameStreak {
    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak.asStateFlow()
    private val _best = MutableStateFlow(0)
    val best: StateFlow<Int> = _best.asStateFlow()
    private val _bursts = MutableStateFlow(0)
    val bursts: StateFlow<Int> = _bursts.asStateFlow()

    /** Звук, серия и салют для одного ответа. */
    fun answered(correct: Boolean) {
        if (correct) {
            val s = _streak.value + 1
            _streak.value = s
            if (s > _best.value) _best.value = s
            _bursts.value++
            KidsGameAudio.play(if (s > 0 && s % 5 == 0) KidsSfx.WIN else KidsSfx.CORRECT)
        } else {
            _streak.value = 0
            KidsGameAudio.play(KidsSfx.WRONG)
        }
    }
}

/** Карточка вопроса: эмодзи, подпись, крупное задание и кнопки «послушать». */
@Composable
internal fun KidsQuestionCard(
    emoji: String,
    label: String,
    prompt: String,
    modifier: Modifier = Modifier,
    promptSize: Int = 30,
    subtitle: String? = null,
    gradient: List<Color> = KidsNightGradient,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(gradient))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White.copy(alpha = 0.82f), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            KidsStreakBadge()
        }
        Text(
            prompt,
            color = Color.White,
            fontSize = promptSize.sp,
            lineHeight = (promptSize + 6).sp,
            fontWeight = FontWeight.Black,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, lineHeight = 17.sp)
        }
        if (actions.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions.forEach { (text, onClick) ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                            .clickable(onClick = onClick)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun KidsStreakBadge() {
    val streak by KidsGameStreak.streak.collectAsStateWithLifecycle()
    if (streak < 2) return
    val pop = remember { Animatable(1f) }
    LaunchedEffect(streak) {
        pop.snapTo(1.35f)
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f))
    }
    Box(
        Modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEF4444))))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text("🔥 $streak подряд", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
    }
}

/** Плитка ответа: прыгает при появлении, качается при ошибке, светится и получает звезду при верном ответе. */
@Composable
internal fun KidsAnswerTile(
    text: String,
    index: Int,
    state: KidsAnswerState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 88.dp,
    fontSize: Int = 22,
    subtitle: String? = null,
    appearKey: Any = Unit,
) {
    val appear = remember(appearKey) { Animatable(0.6f) }
    LaunchedEffect(appearKey) {
        appear.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f))
    }
    val shake = remember(appearKey) { Animatable(0f) }
    LaunchedEffect(state) {
        if (state == KidsAnswerState.Wrong) {
            for (x in listOf(-12f, 10f, -8f, 5f, -2f, 0f)) shake.animateTo(x, tween(55))
        }
    }
    val pop by animateFloatAsState(
        targetValue = when (state) {
            KidsAnswerState.Correct -> 1.06f
            KidsAnswerState.Dimmed -> 0.95f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 300f),
        label = "answerPop",
    )
    val gradient = when (state) {
        KidsAnswerState.Correct -> KidsCorrectGradient
        KidsAnswerState.Wrong -> KidsWrongGradient
        else -> kidsGradient(index)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                val s = appear.value * pop
                scaleX = s
                scaleY = s
                translationX = shake.value * density
                alpha = if (state == KidsAnswerState.Dimmed) 0.5f else 1f
            }
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(gradient))
            .border(
                if (state == KidsAnswerState.Correct) 3.dp else 0.dp,
                Color.White,
                RoundedCornerShape(24.dp),
            )
            .clickable(enabled = enabled && state != KidsAnswerState.Dimmed, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text,
                color = Color.White,
                fontSize = fontSize.sp,
                lineHeight = (fontSize + 4).sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1)
            }
        }
        when (state) {
            KidsAnswerState.Correct -> Text("⭐", fontSize = 22.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp))
            KidsAnswerState.Wrong -> Text("✖", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp))
            else -> Unit
        }
    }
}

/** Яркая плашка с итогом ответа. */
@Composable
internal fun KidsAnswerBanner(correct: Boolean, text: String, modifier: Modifier = Modifier) {
    KidsBanner(
        (if (correct) "🎉 " else "💡 ") + text,
        modifier = modifier,
        gradient = if (correct) KidsCorrectGradient else listOf(Color(0xFFF59E0B), Color(0xFFF97316)),
        fontSize = 16,
    )
}

/** Звёздочки разлетаются из центра при каждом верном ответе. Кладётся поверх игры. */
@Composable
internal fun BoxScope.KidsCorrectBurst() {
    val bursts by KidsGameStreak.bursts.collectAsStateWithLifecycle()
    if (bursts == 0) return
    val progress = remember(bursts) { Animatable(0f) }
    val seeds = remember(bursts) {
        List(14) { Triple(Random.nextFloat() * 360f, 0.6f + Random.nextFloat() * 0.6f, kidsGradient(it).first()) }
    }
    LaunchedEffect(bursts) {
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    if (progress.value >= 1f) return
    Canvas(Modifier.matchParentSize()) {
        val p = progress.value
        val c = Offset(size.width / 2f, size.height * 0.45f)
        val maxR = size.minDimension * 0.55f
        seeds.forEach { (angle, speed, color) ->
            val a = Math.toRadians(angle.toDouble())
            val r = maxR * speed * p
            val pos = c + Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat() + 120f * p * p)
            val s = 16f * (1f - p * 0.6f)
            val star = Path().apply {
                for (k in 0 until 10) {
                    val rr = if (k % 2 == 0) s else s * 0.45f
                    val aa = Math.toRadians(-90.0 + k * 36.0)
                    val pt = pos + Offset((cos(aa) * rr).toFloat(), (sin(aa) * rr).toFloat())
                    if (k == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
                close()
            }
            drawPath(star, color.copy(alpha = 1f - p))
        }
    }
}
