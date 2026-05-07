package com.example.bible.ui.travel

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.bible.R

fun Modifier.hudOffsetDp(offsetXDp: Float, offsetYDp: Float): Modifier =
    offset(x = offsetXDp.dp, y = offsetYDp.dp)

/**
 * Плавающая панель над картой: сдвиг одним/двумя пальцами, масштаб щипком.
 * Координаты в dp от точки привязки (верхний левый край с базовым padding родителя).
 */
@Composable
fun TravelDraggableMapHudPanel(
    offsetXDp: Float,
    offsetYDp: Float,
    onOffsetDpChange: (Float, Float) -> Unit,
    panelScale: Float,
    onPanelScaleChange: (Float) -> Unit,
    scaleRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scaleRef = rememberUpdatedState(panelScale)
    val offX = rememberUpdatedState(offsetXDp)
    val offY = rememberUpdatedState(offsetYDp)
    val dragHint = stringResource(R.string.travel_map_hud_drag_pinch_hint)
    Surface(
        modifier = modifier
            .semantics { contentDescription = dragHint }
            .graphicsLayer {
                scaleX = panelScale
                scaleY = panelScale
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    val dpx = with(density) { panChange.x.toDp().value }
                    val dpy = with(density) { panChange.y.toDp().value }
                    onOffsetDpChange(offX.value + dpx, offY.value + dpy)
                    val next = (scaleRef.value * zoomChange).coerceIn(scaleRange.start, scaleRange.endInclusive)
                    onPanelScaleChange(next)
                }
            },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            content()
        }
    }
}
