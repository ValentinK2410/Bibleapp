package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Палитра раздела «Детям»: мёд, глина, шалфей и припылённый синий — одна семья на всех плитках. */
internal val KidsPalette: List<List<Color>> = listOf(
    listOf(Color(0xFFE0A15A), Color(0xFFC46A3A)),
    listOf(Color(0xFF6FA184), Color(0xFF3E7A68)),
    listOf(Color(0xFF6A94C4), Color(0xFF3E6EA8)),
    listOf(Color(0xFFD4897A), Color(0xFFB85C58)),
    listOf(Color(0xFFC4A35A), Color(0xFF8C7A3A)),
    listOf(Color(0xFF8E9A6A), Color(0xFF5E7A52)),
    listOf(Color(0xFFB08AAB), Color(0xFF7A5E86)),
    listOf(Color(0xFF7EB0B6), Color(0xFF4E7C86)),
)

internal fun kidsGradient(index: Int): List<Color> = KidsPalette[Math.floorMod(index, KidsPalette.size)]

internal val KidsVowelGradient = listOf(Color(0xFFF43F5E), Color(0xFFF97316))
internal val KidsConsonantGradient = listOf(Color(0xFF3B82F6), Color(0xFF6366F1))
internal val KidsSignGradient = listOf(Color(0xFF94A3B8), Color(0xFF64748B))
internal val KidsNightGradient = listOf(Color(0xFF5C3D34), Color(0xFF8C5A3C), Color(0xFFC4895A))

@Composable
internal fun KidsBigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: List<Color> = KidsWarmHoney,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (enabled) Brush.linearGradient(gradient) else Brush.linearGradient(KidsSignGradient),
            )
            .kidsPressable(onClick = onClick, enabled = enabled)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

/** Цветная плашка с подсказкой или статусом игры. */
@Composable
internal fun KidsBanner(
    text: String,
    modifier: Modifier = Modifier,
    gradient: List<Color> = KidsNightGradient,
    fontSize: Int = 17,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(gradient))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 5).sp,
        )
    }
}

/** Вкладки раздела «Детям»: цветные таблетки с прокруткой вбок. */
@Composable
internal fun KidsTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    val tabLefts = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<Int, Int>() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.LaunchedEffect(selected, tabLefts[selected]) {
        val left = tabLefts[selected] ?: return@LaunchedEffect
        val margin = with(density) { 48.dp.roundToPx() }
        scroll.animateScrollTo((left - margin).coerceAtLeast(0))
    }
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { index, title ->
            Box(
                Modifier.onGloballyPositioned { tabLefts[index] = it.positionInParent().x.toInt() },
            ) {
                TmGradientPill(
                    text = title,
                    selected = index == selected,
                    gradient = kidsGradient(index),
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}
