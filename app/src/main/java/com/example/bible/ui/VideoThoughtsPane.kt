package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.example.bible.data.VideoThought

private val PaneBg = Color(0xFF161616)
private val PaneCard = Color(0xFF2A2A2A)
private val HandleBg = Color(0xFF2E2E2E)
private val SplitMin = 0.28f
private val SplitMax = 0.78f

@Composable
fun VideoThoughtsSplitLayout(
    landscape: Boolean,
    splitFraction: Float,
    onSplitFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    video: @Composable () -> Unit,
    notes: @Composable () -> Unit,
) {
    var local by remember {
        mutableFloatStateOf(splitFraction.coerceIn(SplitMin, SplitMax))
    }
    var sizePx by remember { mutableIntStateOf(1) }
    LaunchedEffect(splitFraction) {
        local = splitFraction.coerceIn(SplitMin, SplitMax)
    }
    val videoWeight = local.coerceIn(SplitMin, SplitMax)
    val notesWeight = (1f - videoWeight).coerceAtLeast(0.01f)

    if (landscape) {
        Row(
            modifier
                .fillMaxSize()
                .onSizeChanged { sizePx = it.width },
        ) {
            Box(Modifier.weight(videoWeight).fillMaxHeight()) { video() }
            VideoThoughtsDragHandle(
                horizontal = true,
                onDragDeltaPx = { dx ->
                    if (sizePx > 0) local = (local + dx / sizePx).coerceIn(SplitMin, SplitMax)
                },
                onDragEnd = { onSplitFractionChange(local) },
            )
            Box(Modifier.weight(notesWeight).fillMaxHeight()) { notes() }
        }
    } else {
        Column(
            modifier
                .fillMaxSize()
                .onSizeChanged { sizePx = it.height },
        ) {
            Box(Modifier.weight(videoWeight).fillMaxWidth()) { video() }
            VideoThoughtsDragHandle(
                horizontal = false,
                onDragDeltaPx = { dy ->
                    if (sizePx > 0) local = (local + dy / sizePx).coerceIn(SplitMin, SplitMax)
                },
                onDragEnd = { onSplitFractionChange(local) },
            )
            Box(Modifier.weight(notesWeight).fillMaxWidth()) { notes() }
        }
    }
}

@Composable
private fun VideoThoughtsDragHandle(
    horizontal: Boolean,
    onDragDeltaPx: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val handleMod = if (horizontal) {
        Modifier.fillMaxHeight().width(16.dp)
    } else {
        Modifier.fillMaxWidth().height(16.dp)
    }
    Box(
        handleMod
            .background(HandleBg)
            .pointerInput(horizontal) {
                if (horizontal) {
                    detectHorizontalDragGestures(
                        onDragEnd = { onDragEnd() },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            onDragDeltaPx(amount)
                        },
                    )
                } else {
                    detectVerticalDragGestures(
                        onDragEnd = { onDragEnd() },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            onDragDeltaPx(amount)
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            if (horizontal) {
                Modifier.width(3.dp).height(40.dp)
            } else {
                Modifier.width(40.dp).height(3.dp)
            }.background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
fun VideoThoughtsSidePane(
    thoughts: List<VideoThought>,
    draft: String,
    onDraftChange: (String) -> Unit,
    attachCurrentTime: Boolean,
    onAttachCurrentTimeChange: (Boolean) -> Unit,
    currentTimeLabel: String,
    canSave: Boolean,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onSeek: (Int) -> Unit,
    onDelete: (String) -> Unit,
    formatTime: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(PaneBg)
            .imePadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Мысли",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Скрыть мысли", tint = Color.White)
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            placeholder = { Text("Запишите мысль — не обязательно ставить на паузу") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedPlaceholderColor = Color.White.copy(alpha = 0.45f),
                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.45f),
                cursorColor = Color.White,
                focusedBorderColor = Color.White.copy(alpha = 0.55f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
            ),
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = attachCurrentTime,
                onClick = { onAttachCurrentTimeChange(!attachCurrentTime) },
                label = { Text("К кадру $currentTimeLabel") },
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = Color.White,
                    selectedLabelColor = Color.White,
                    selectedContainerColor = Color(0xFFFF4B3E).copy(alpha = 0.35f),
                ),
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSave, enabled = canSave) {
                Text("Сохранить", color = if (canSave) Color.White else Color.White.copy(alpha = 0.4f))
            }
        }
        Text(
            if (thoughts.isEmpty()) "Пока пусто. Список не закрывает плеер — его можно скрыть крестиком или кнопкой в плеере."
            else "Нажмите мысль со временем, чтобы перейти к кадру.",
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            thoughts.forEach { thought ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PaneCard)
                        .then(
                            if (thought.positionMs != null) {
                                Modifier.clickable { onSeek(thought.positionMs) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        thought.positionMs?.let { formatTime(it) } ?: "Без времени",
                        color = if (thought.positionMs != null) Color(0xFFFF4B3E) else Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        thought.text,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    TextButton(
                        onClick = { onDelete(thought.id) },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Удалить", color = Color(0xFFFF8A80))
                    }
                }
            }
        }
    }
}
