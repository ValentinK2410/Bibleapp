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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Палитра раздела «Детям»: одна и та же на плитках всех подразделов. */
internal val KidsPalette: List<List<Color>> = listOf(
    listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
    listOf(Color(0xFFF59E0B), Color(0xFFEC4899)),
    listOf(Color(0xFF10B981), Color(0xFF06B6D4)),
    listOf(Color(0xFFEF4444), Color(0xFFF97316)),
    listOf(Color(0xFF0EA5E9), Color(0xFF6366F1)),
    listOf(Color(0xFF22C55E), Color(0xFF84CC16)),
    listOf(Color(0xFFD946EF), Color(0xFF8B5CF6)),
    listOf(Color(0xFF14B8A6), Color(0xFF3B82F6)),
)

internal fun kidsGradient(index: Int): List<Color> = KidsPalette[Math.floorMod(index, KidsPalette.size)]

internal val KidsVowelGradient = listOf(Color(0xFFF43F5E), Color(0xFFF97316))
internal val KidsConsonantGradient = listOf(Color(0xFF3B82F6), Color(0xFF6366F1))
internal val KidsSignGradient = listOf(Color(0xFF94A3B8), Color(0xFF64748B))
internal val KidsNightGradient = listOf(Color(0xFF1E1B4B), Color(0xFF4C1D95), Color(0xFF0F766E))

@Composable
internal fun KidsBigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: List<Color> = listOf(Color(0xFFF59E0B), Color(0xFFEC4899)),
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (enabled) Brush.linearGradient(gradient) else Brush.linearGradient(KidsSignGradient),
            )
            .clickable(enabled = enabled, onClick = onClick)
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
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { index, title ->
            TmGradientPill(
                text = title,
                selected = index == selected,
                gradient = kidsGradient(index),
                onClick = { onSelect(index) },
            )
        }
    }
}
