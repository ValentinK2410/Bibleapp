package com.example.bible.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField as M3OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.hypot

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: androidx.compose.material3.TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    ThemedOutlinedField(
        modifier = modifier,
        shape = shape,
        fallbackColors = colors,
        field = { lookModifier, lookColors ->
            M3OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = lookModifier,
                enabled = enabled,
                readOnly = readOnly,
                textStyle = textStyle,
                label = label,
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                prefix = prefix,
                suffix = suffix,
                supportingText = supportingText,
                isError = isError,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = singleLine,
                maxLines = maxLines,
                minLines = minLines,
                shape = shape,
                colors = lookColors,
            )
        },
    )
}

@Composable
fun OutlinedTextField(
    value: androidx.compose.ui.text.input.TextFieldValue,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: androidx.compose.material3.TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    ThemedOutlinedField(
        modifier = modifier,
        shape = shape,
        fallbackColors = colors,
        field = { lookModifier, lookColors ->
            M3OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = lookModifier,
                enabled = enabled,
                readOnly = readOnly,
                textStyle = textStyle,
                label = label,
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                prefix = prefix,
                suffix = suffix,
                supportingText = supportingText,
                isError = isError,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = singleLine,
                maxLines = maxLines,
                minLines = minLines,
                shape = shape,
                colors = lookColors,
            )
        },
    )
}

@Composable
private fun ThemedOutlinedField(
    modifier: Modifier,
    shape: Shape,
    fallbackColors: androidx.compose.material3.TextFieldColors,
    field: @Composable (Modifier, androidx.compose.material3.TextFieldColors) -> Unit,
) {
    val fieldId = "f$currentCompositeKeyHash"
    val controller = LocalThemeFieldController.current
    val saved = controller.looks[fieldId]
    var open by remember(fieldId) { mutableStateOf(false) }
    var draft by remember(fieldId, saved) { mutableStateOf(saved ?: defaultLook()) }
    val scheme = MaterialTheme.colorScheme
    val applied = saved
    val bg = applied?.backgroundArgb?.let { Color(it) }
    val fg = applied?.textArgb?.let { Color(it) }
    val border = applied?.borderArgb?.let { Color(it) }
    val colors = if (applied != null && (bg != null || fg != null || border != null)) {
        OutlinedTextFieldDefaults.colors(
            focusedContainerColor = bg ?: Color.Transparent,
            unfocusedContainerColor = bg ?: Color.Transparent,
            focusedTextColor = fg ?: scheme.onSurface,
            unfocusedTextColor = fg ?: scheme.onSurface,
            cursorColor = fg ?: scheme.primary,
            focusedBorderColor = border ?: scheme.primary,
            unfocusedBorderColor = border ?: scheme.outline,
        )
    } else {
        fallbackColors
    }
    val lookModifier = Modifier
        .fillMaxWidth()
        .then(
            if (applied != null) {
                Modifier
                    .heightIn(min = applied.heightDp.dp)
                    .graphicsLayer { alpha = applied.alpha }
                    .then(
                        if ((applied.borderArgb != null || applied.borderWidthDp > 0f) && applied.borderWidthDp > 0f) {
                            Modifier.border(
                                applied.borderWidthDp.dp,
                                border ?: scheme.outline,
                                shape,
                            )
                        } else {
                            Modifier
                        },
                    )
            } else {
                Modifier
            },
        )
    val frame = if (applied != null && applied.widthFraction < 0.995f) {
        modifier.fillMaxWidth(applied.widthFraction)
    } else {
        modifier
    }
    androidx.compose.foundation.layout.Box(
        modifier = frame.holdForFieldEditor { open = true },
    ) {
        field(lookModifier, colors)
        if (open) {
            Popup(
                alignment = androidx.compose.ui.Alignment.TopCenter,
                offset = IntOffset(0, 72),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true, clippingEnabled = false),
            ) {
                FieldLookEditor(
                    draft = draft,
                    onChange = { draft = it },
                    onSave = {
                        controller.save(fieldId, draft)
                        open = false
                    },
                    onClose = { open = false },
                )
            }
        }
    }
}

private fun defaultLook(): FieldLook = FieldLook()

private fun Modifier.holdForFieldEditor(onHold: () -> Unit): Modifier = pointerInput(Unit) {
    val slop = 24.dp.toPx()
    awaitPointerEventScope {
        while (true) {
            val first = awaitPointerEvent(PointerEventPass.Initial)
            val down = first.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
            val origin = down.position
            val started = System.currentTimeMillis()
            var holding = true
            while (holding && System.currentTimeMillis() - started < 3_000L) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                if (change == null || !change.pressed) {
                    holding = false
                    break
                }
                if (distance(origin, change.position) > slop) {
                    holding = false
                    break
                }
            }
            if (holding) {
                onHold()
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    }
}

private fun distance(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

@Composable
private fun FieldLookEditor(
    draft: FieldLook,
    onChange: (FieldLook) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    var channel by remember { mutableStateOf("bg") }
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.width(320.dp),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Поле", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("Высота ${draft.heightDp.toInt()} · ширина ${(draft.widthFraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = draft.heightDp,
                onValueChange = { onChange(draft.copy(heightDp = it)) },
                valueRange = 40f..180f,
            )
            Slider(
                value = draft.widthFraction,
                onValueChange = { onChange(draft.copy(widthFraction = it)) },
                valueRange = 0.4f..1f,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorDot("Фон", channel == "bg", draft.backgroundArgb?.let { Color(it) } ?: scheme.surface) { channel = "bg" }
                ColorDot("Текст", channel == "fg", draft.textArgb?.let { Color(it) } ?: scheme.onSurface) { channel = "fg" }
                ColorDot("Рамка", channel == "bd", draft.borderArgb?.let { Color(it) } ?: scheme.outline) { channel = "bd" }
            }
            val current = when (channel) {
                "fg" -> draft.textArgb
                "bd" -> draft.borderArgb
                else -> draft.backgroundArgb
            } ?: scheme.primary.toStoredArgb()
            HueSlider(current) { argb ->
                onChange(
                    when (channel) {
                        "fg" -> draft.copy(textArgb = argb)
                        "bd" -> draft.copy(borderArgb = argb)
                        else -> draft.copy(backgroundArgb = argb)
                    },
                )
            }
            Text("Рамка ${draft.borderWidthDp.toInt()} · прозрачность ${(draft.alpha * 100).toInt()}%")
            Slider(
                value = draft.borderWidthDp,
                onValueChange = { onChange(draft.copy(borderWidthDp = it)) },
                valueRange = 0f..8f,
            )
            Slider(
                value = draft.alpha,
                onValueChange = { onChange(draft.copy(alpha = it)) },
                valueRange = 0.2f..1f,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClose) { Text("Закрыть") }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Сохранить") }
            }
        }
    }
}

@Composable
private fun ColorDot(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Column {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            color = color,
            modifier = Modifier
                .size(36.dp)
                .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier),
        ) {}
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HueSlider(argb: Int, onChange: (Int) -> Unit) {
    val hsv = remember(argb) { FloatArray(3) }
    android.graphics.Color.colorToHSV(argb, hsv)
    var hue by remember(argb) { mutableStateOf(hsv[0]) }
    Slider(
        value = hue,
        onValueChange = {
            hue = it
            val next = hsv.clone()
            next[0] = it
            if (next[1] < 0.25f) next[1] = 0.55f
            if (next[2] < 0.25f) next[2] = 0.85f
            onChange(android.graphics.Color.HSVToColor(next))
        },
        valueRange = 0f..360f,
    )
}
