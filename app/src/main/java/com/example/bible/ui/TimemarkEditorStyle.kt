package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val TmHeroGradient = listOf(Color(0xFF1E1B4B), Color(0xFF4C1D95), Color(0xFF0F766E))
internal val TmProjectGradient = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
internal val TmAudioGradient = listOf(Color(0xFFEC4899), Color(0xFFF97316))
internal val TmVersesGradient = listOf(Color(0xFF10B981), Color(0xFF06B6D4))
internal val TmMarksGradient = listOf(Color(0xFFF59E0B), Color(0xFFEF4444))

@Composable
internal fun TmHeroCard(
    title: String,
    subtitle: String,
    stats: List<Pair<String, String>>,
    hint: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(TmHeroGradient))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "РЕДАКТОР ТАЙМКОДОВ",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    title,
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                stats.forEach { (value, label) ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }
        Text(
            hint,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TmSectionCard(
    icon: ImageVector,
    title: String,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerLow)
            .border(1.dp, gradient.first().copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TmIconBadge(icon, gradient)
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        content()
    }
}

@Composable
internal fun TmIconBadge(icon: ImageVector, gradient: List<Color>, size: Int = 34) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size((size * 0.56f).dp))
    }
}

@Composable
internal fun TmGradientPill(
    text: String,
    selected: Boolean,
    gradient: List<Color>,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    val base = Modifier
        .clip(shape)
        .clickable(onClick = onClick)
    Box(
        modifier = if (selected) {
            base.background(Brush.linearGradient(gradient))
        } else {
            base
                .background(gradient.first().copy(alpha = 0.08f))
                .border(1.dp, gradient.first().copy(alpha = 0.28f), shape)
        }.padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Color.White else gradient.first(),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
internal fun TmPlayButton(
    playing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .shadow(if (enabled) 10.dp else 0.dp, CircleShape, ambientColor = TmAudioGradient.first(), spotColor = TmAudioGradient.first())
            .clip(CircleShape)
            .background(
                if (enabled) {
                    Brush.linearGradient(TmAudioGradient)
                } else {
                    Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFF64748B)))
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
internal fun TmVerseRow(
    number: Int,
    text: String,
    selected: Boolean,
    marked: Boolean,
    textStyle: TextStyle,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val accent = TmVersesGradient.first()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.12f) else scheme.surfaceContainerLow)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent else scheme.outline.copy(alpha = 0.14f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    if (marked) {
                        Brush.linearGradient(TmMarksGradient)
                    } else {
                        Brush.linearGradient(TmVersesGradient)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (number > 99) 10.sp else 12.sp,
            )
        }
        Text(text, style = textStyle, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun TmCueRow(
    time: String,
    verse: Int,
    note: String?,
    attachments: String?,
    hasImage: Boolean,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerLow)
            .border(1.dp, TmMarksGradient.first().copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(TmMarksGradient))
                .padding(horizontal = 9.dp, vertical = 6.dp),
        ) {
            Text(
                time,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("стих $verse", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                if (hasImage) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = TmMarksGradient.first(),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            if (!note.isNullOrBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!attachments.isNullOrBlank()) {
                Text(attachments, fontSize = 11.sp, color = scheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = scheme.error.copy(alpha = 0.8f))
        }
    }
}
