@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.bible.ui.mynotes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bible.data.mynotes.MyNote
import com.example.bible.data.mynotes.MyNoteKind
import com.example.bible.data.mynotes.NotePalette
import com.example.bible.data.mynotes.RecipeIngredientParts
import com.example.bible.data.mynotes.RecipeLine
import com.example.bible.data.mynotes.RecipeTextPiece
import com.example.bible.data.mynotes.splitRecipeIngredient
import com.example.bible.data.mynotes.splitRecipeTimes
import com.example.bible.ui.theme.BibleTheme

@Composable
internal fun RecipeReader(
    note: MyNote,
    ingredients: List<RecipeLine>,
    steps: List<RecipeLine>,
    checkedIngredientIds: Set<String>,
    onToggleIngredient: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = note.palette.accent(dark)
    val container = note.palette.container(dark)
    val visibleIngredients = ingredients.filter { it.text.isNotBlank() }
    val visibleSteps = steps.mapIndexedNotNull { index, line ->
        if (line.text.isBlank()) null else (index + 1) to line
    }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RecipeHeader(note, accent, container)
        if (visibleIngredients.isNotEmpty()) {
            RecipeSection(title = "Ингредиенты", accent = accent) {
                RecipePlaque(container = container, accent = accent) {
                    val parsed = visibleIngredients.map { it to splitRecipeIngredient(it.text) }
                    val showAmount = parsed.any { it.second.amount != null }
                    parsed.forEach { (line, parts) ->
                        IngredientLine(
                            line = line,
                            parts = parts,
                            showAmount = showAmount,
                            checked = line.id in checkedIngredientIds,
                            accent = accent,
                            onToggle = { onToggleIngredient(line.id) },
                        )
                    }
                }
            }
        }
        if (visibleSteps.isNotEmpty()) {
            RecipeSection(title = "Шаги", accent = accent) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    visibleSteps.forEach { (number, line) ->
                        StepPlaque(
                            number = number,
                            text = line.text,
                            accent = accent,
                            container = container,
                        )
                    }
                }
            }
        }
        if (note.body.isNotBlank()) {
            RecipeSection(title = "Заметки", accent = accent) {
                RecipePlaque(container = container, accent = accent) {
                    RecipeTimeText(
                        text = note.body,
                        accent = accent,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun RecipeHeader(note: MyNote, accent: Color, container: Color) {
    val chips = buildList {
        val servings = servingsLabel(note.servings)
        if (servings.isNotEmpty()) add(Icons.Filled.Person to servings)
        val time = note.cookTime.trim()
        if (time.isNotEmpty()) add(Icons.Filled.Timer to time)
    }
    RecipePlaque(container = container, accent = accent) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Restaurant, contentDescription = null, tint = accent)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        "Рецепт",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        note.displayTitle(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (chips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chips.forEach { (icon, label) ->
                        MetaChip(icon = icon, label = label, accent = accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(icon: ImageVector, label: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = Modifier.widthIn(max = 320.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(
                label,
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun RecipeSection(title: String, accent: Color, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.padding(start = 4.dp),
        )
        content()
    }
}

@Composable
private fun RecipePlaque(
    container: Color,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 1.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun IngredientLine(
    line: RecipeLine,
    parts: RecipeIngredientParts,
    showAmount: Boolean,
    checked: Boolean,
    accent: Color,
    onToggle: () -> Unit,
) {
    val faded = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val nameStyle = MaterialTheme.typography.bodyLarge.copy(
        color = if (checked) faded else MaterialTheme.colorScheme.onSurface,
        textDecoration = if (checked) TextDecoration.LineThrough else null,
    )
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                contentDescription = if (checked) "Снять отметку" else "Отметить ингредиент",
                tint = accent,
            )
        }
        if (showAmount) {
            Text(
                parts.amount ?: "•",
                modifier = Modifier
                    .widthIn(min = 72.dp, max = 148.dp)
                    .padding(top = 12.dp, end = 10.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (parts.amount != null) FontWeight.Bold else FontWeight.Normal,
                color = if (checked) faded else accent,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
        }
        if (parts.name.isNotEmpty()) {
            RecipeTimeText(
                text = parts.name,
                accent = accent,
                modifier = Modifier.weight(1f).padding(top = 12.dp, bottom = 10.dp),
                style = nameStyle,
            )
        }
    }
}

@Composable
private fun StepPlaque(number: Int, text: String, accent: Color, container: Color) {
    RecipePlaque(container = container, accent = accent) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = accent,
                modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp),
            ) {
                Box(
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        number.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent.readableOn(),
                    )
                }
            }
            RecipeTimeText(
                text = text,
                accent = accent,
                modifier = Modifier.weight(1f).padding(start = 12.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun RecipeTimeText(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
    ),
) {
    val pieces = remember(text) { splitRecipeTimes(text) }
    if (pieces.none { it.time }) {
        Text(text = text, modifier = modifier, style = style)
        return
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val chipStyle = style.copy(fontWeight = FontWeight.SemiBold)
    val annotated = buildAnnotatedString {
        pieces.forEachIndexed { index, piece ->
            if (piece.time) appendInlineContent("time$index", piece.text) else append(piece.text)
        }
    }
    val inline = pieces.mapIndexedNotNull { index, piece ->
        if (!piece.time) return@mapIndexedNotNull null
        val id = "time$index"
        id to timeChipContent(piece, chipStyle, accent, measurer, density)
    }.toMap()
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        inlineContent = inline,
        overflow = TextOverflow.Visible,
    )
}

@Composable
private fun timeChipContent(
    piece: RecipeTextPiece,
    chipStyle: TextStyle,
    accent: Color,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
): InlineTextContent {
    val icon = 14.dp
    val gap = 4.dp
    val pad = 6.dp
    val slack = 4.dp
    val (width, height) = with(density) {
        val measured = measurer.measure(piece.text, style = chipStyle).size
        val w = measured.width.toDp() + icon + gap + pad * 2 + slack
        val h = maxOf(measured.height.toDp(), icon) + 4.dp
        w to h
    }
    return InlineTextContent(
        Placeholder(
            width = with(density) { width.toSp() },
            height = with(density) { height.toSp() },
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
        ),
    ) { _ ->
        Row(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.18f))
                .border(1.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                .padding(horizontal = pad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Timer,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(icon),
            )
            Spacer(Modifier.width(gap))
            Text(
                piece.text,
                style = chipStyle,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        }
    }
}

private fun Color.readableOn(): Color =
    if (luminance() > 0.42f) Color(0xFF241C16) else Color.White

private fun servingsLabel(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return ""
    val number = value.toIntOrNull() ?: return value
    val mod10 = kotlin.math.abs(number % 10)
    val mod100 = kotlin.math.abs(number % 100)
    val word = when {
        mod10 == 1 && mod100 != 11 -> "порция"
        mod10 in 2..4 && mod100 !in 12..14 -> "порции"
        else -> "порций"
    }
    return "$number $word"
}

@Preview(name = "Рецепт просмотр", widthDp = 360, heightDp = 900, showBackground = true)
@Composable
private fun RecipeReaderPreviewLight() {
    BibleTheme(darkTheme = false) {
        RecipeReader(
            note = previewRecipe(),
            ingredients = previewRecipe().ingredients,
            steps = previewRecipe().steps,
            checkedIngredientIds = setOf("2"),
            onToggleIngredient = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "Рецепт просмотр тёмный", widthDp = 360, heightDp = 900, showBackground = true)
@Composable
private fun RecipeReaderPreviewDark() {
    BibleTheme(darkTheme = true) {
        RecipeReader(
            note = previewRecipe().copy(palette = NotePalette.SKY),
            ingredients = previewRecipe().ingredients,
            steps = previewRecipe().steps,
            checkedIngredientIds = emptySet(),
            onToggleIngredient = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun previewRecipe(): MyNote = MyNote(
    id = "preview-recipe",
    kind = MyNoteKind.RECIPE,
    title = "Томлёная говядина с тимьяном и чесноком",
    body = "Вкуснее на следующий день. Тимьян можно заменить розмарином и дать постоять ещё 10 мин.",
    palette = NotePalette.PEACH,
    servings = "4",
    cookTime = "2 ч 30 мин",
    ingredients = listOf(
        RecipeLine("1", "800 г говяжьей лопатки"),
        RecipeLine("2", "1,5 ст. л. оливкового масла"),
        RecipeLine("3", "2-3 зубчика чеснока"),
        RecipeLine("4", "по вкусу соль и перец"),
        RecipeLine("5", "½ лимона"),
    ),
    steps = listOf(
        RecipeLine(
            "s1",
            "Обсушить мясо и обжарить на сильном огне 2–3 минуты с каждой стороны, пока не появится румяная корочка.",
        ),
        RecipeLine(
            "s2",
            "Добавить чеснок и томить под крышкой 1,5 ч, затем ещё 15–20 минут без крышки.",
        ),
        RecipeLine("s3", "Дать отдохнуть 10 мин. Подавать с лимоном."),
    ),
)
