package com.example.bible.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeStudioScreen(
    preset: BibleAppThemePreset,
    studio: ThemeStudioState,
    isDark: Boolean,
    onBack: () -> Unit,
    onPreset: (BibleAppThemePreset) -> Unit,
    onTextScale: (Float) -> Unit,
    onColor: (String, Int?) -> Unit,
    onToggleDark: () -> Unit,
    onReset: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }
    var editingRole by remember { mutableStateOf<String?>(null) }
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тема") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmReset = true }) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Заводские настройки")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "Удерживайте любое поле ввода 3 секунды — на месте появятся размер, цвет, рамка, прозрачность и кнопка «Сохранить».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onToggleDark) {
                        Text(if (isDark) "Светлый режим" else "Тёмный режим")
                    }
                    TextButton(onClick = { confirmReset = true }) { Text("Заводские") }
                }
            }
            ThemeGroups.forEach { (title, presets) ->
                item { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(presets.chunked(2)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { item ->
                            PresetCard(
                                preset = item,
                                selected = item == preset,
                                modifier = Modifier.weight(1f),
                                onClick = { onPreset(item) },
                            )
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
            item {
                Text("Размер текста ${(studio.textScale * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
                Slider(
                    value = studio.textScale,
                    onValueChange = onTextScale,
                    valueRange = 0.85f..1.6f,
                )
            }
            item {
                Text("Полутона", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Каждый цвет темы можно заменить. Пустое значение возвращает цвет выбранного стиля.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            items(ColorRoles.keys, key = { it.first }) { (key, label) ->
                val color = scheme.roleColor(key)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editingRole = key }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(color, RoundedCornerShape(10.dp))
                            .border(1.dp, scheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    )
                    Text(label, modifier = Modifier.weight(1f))
                    if (studio.colorOverrides.containsKey(key)) {
                        TextButton(onClick = { onColor(key, null) }) { Text("Сброс") }
                    }
                }
            }
        }
    }
    editingRole?.let { key ->
        val label = ColorRoles.keys.first { it.first == key }.second
        val current = scheme.roleColor(key).toStoredArgb()
        AlertDialog(
            onDismissRequest = { editingRole = null },
            title = { Text(label) },
            text = {
                HueSliderBlock(current) { onColor(key, it) }
            },
            confirmButton = {
                TextButton(onClick = { editingRole = null }) { Text("Готово") }
            },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Заводские настройки") },
            text = { Text("Вернуть стандартную тему, цвета, размер текста и оформление всех полей.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onReset()
                }) { Text("Вернуть") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Отмена") }
            },
        )
    }
}

private val ThemeGroups = listOf(
    "Нежные" to listOf(
        BibleAppThemePreset.BLOSSOM,
        BibleAppThemePreset.PEARL,
        BibleAppThemePreset.LILAC,
        BibleAppThemePreset.PINK,
        BibleAppThemePreset.SKY,
        BibleAppThemePreset.MEADOW,
    ),
    "Ночной сад" to listOf(
        BibleAppThemePreset.NIGHT_GARDEN,
        BibleAppThemePreset.VELVET,
    ),
    "Строгие" to listOf(
        BibleAppThemePreset.STEEL,
        BibleAppThemePreset.NAVY,
        BibleAppThemePreset.TAIGA,
        BibleAppThemePreset.BRUTAL,
    ),
    "Космос" to listOf(
        BibleAppThemePreset.NEBULA,
        BibleAppThemePreset.STARFIELD,
    ),
    "Папирус и свиток" to listOf(
        BibleAppThemePreset.PAPYRUS,
        BibleAppThemePreset.SCROLL,
        BibleAppThemePreset.LEATHER,
    ),
    "Основные" to listOf(BibleAppThemePreset.STANDARD),
)

@Composable
private fun PresetCard(
    preset: BibleAppThemePreset,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = colorSchemeFor(preset, dark = preset.motif == ThemeMotif.STARS || preset.motif == ThemeMotif.NIGHT)
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary, scheme.background))),
            ) {
                ThemeMotifCanvas(preset.motif, Modifier.fillMaxSize())
            }
            Column(Modifier.padding(10.dp)) {
                Text(preset.titleRu, fontWeight = FontWeight.Bold, color = scheme.onSurface, maxLines = 1)
                Text(preset.blurb, style = MaterialTheme.typography.labelSmall, color = scheme.onSurface.copy(alpha = 0.75f), maxLines = 2)
            }
        }
    }
}

@Composable
private fun HueSliderBlock(argb: Int, onChange: (Int) -> Unit) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    var hue by remember(argb) { mutableStateOf(hsv[0]) }
    Slider(
        value = hue,
        onValueChange = {
            hue = it
            val next = floatArrayOf(it, hsv[1].coerceAtLeast(0.45f), hsv[2].coerceAtLeast(0.55f))
            onChange(android.graphics.Color.HSVToColor(next))
        },
        valueRange = 0f..360f,
    )
}

@Composable
fun ThemeMotifCanvas(motif: ThemeMotif, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val ink = Color.White.copy(alpha = 0.55f)
        when (motif) {
            ThemeMotif.FLOWERS, ThemeMotif.NIGHT -> {
                repeat(6) { i ->
                    val c = Offset(size.width * (0.15f + (i % 3) * 0.28f), size.height * (0.35f + (i / 3) * 0.35f))
                    drawCircle(ink, radius = 10.dp.toPx(), center = c)
                    repeat(5) { p ->
                        val a = (p / 5f) * 6.28f
                        drawCircle(
                            ink.copy(alpha = 0.35f),
                            radius = 7.dp.toPx(),
                            center = Offset(c.x + kotlin.math.cos(a) * 16.dp.toPx(), c.y + kotlin.math.sin(a) * 16.dp.toPx()),
                        )
                    }
                }
            }
            ThemeMotif.STARS -> {
                repeat(18) { i ->
                    val x = (i * 47 % 100) / 100f * size.width
                    val y = (i * 29 % 100) / 100f * size.height
                    drawCircle(Color.White.copy(alpha = 0.8f), radius = if (i % 4 == 0) 2.5.dp.toPx() else 1.2.dp.toPx(), center = Offset(x, y))
                }
            }
            ThemeMotif.PAPYRUS, ThemeMotif.SCROLL -> {
                repeat(8) { i ->
                    val y = size.height * (i + 1) / 9f
                    drawLine(Color(0xFF6D4C41).copy(alpha = 0.35f), Offset(8.dp.toPx(), y), Offset(size.width - 8.dp.toPx(), y), strokeWidth = 1.dp.toPx())
                }
                drawLine(Color(0xFF8D6E63), Offset(14.dp.toPx(), 0f), Offset(14.dp.toPx(), size.height), strokeWidth = 3.dp.toPx())
            }
            ThemeMotif.STEEL -> {
                drawRect(Color.White.copy(alpha = 0.25f), style = Stroke(width = 2.dp.toPx()))
                drawLine(ink, Offset(0f, size.height * 0.7f), Offset(size.width, size.height * 0.2f), strokeWidth = 2.dp.toPx())
            }
            ThemeMotif.NONE -> Unit
        }
    }
}
