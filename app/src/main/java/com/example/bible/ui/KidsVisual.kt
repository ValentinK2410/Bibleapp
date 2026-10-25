package com.example.bible.ui

import android.animation.ValueAnimator
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Тёплая гамма раздела «Детям»: мёд, глина, шалфей и припылённый синий,
 * чтобы плитки оставались в одной семье с синей темой приложения и не спорили в тёмной теме.
 */
internal val KidsWarmHoney = listOf(Color(0xFFE0A15A), Color(0xFFC46A3A))
internal val KidsWarmSunset = listOf(Color(0xFFC46A3A), Color(0xFFD4894A), Color(0xFFE7C48A))
internal val KidsDusk = listOf(Color(0xFF5C3D34), Color(0xFF8C5A3C), Color(0xFFC4895A))

@Composable
internal fun kidsMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        scale > 0f && ValueAnimator.areAnimatorsEnabled()
    }
}

@Composable
internal fun kidsTileColors(index: Int): List<Color> {
    val base = kidsGradient(index)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    if (!dark) return base
    return base.map { color ->
        Color(
            red = color.red * 0.78f,
            green = color.green * 0.78f,
            blue = color.blue * 0.78f,
            alpha = color.alpha,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.kidsPressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
): Modifier {
    val motion = kidsMotionEnabled()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && motion && enabled) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "kids-press",
    )
    val clickable = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier.clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
    }
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.then(clickable)
}

@Composable
internal fun KidsEnter(calm: Boolean = false, content: @Composable () -> Unit) {
    val motion = kidsMotionEnabled()
    var tick by remember { mutableIntStateOf(if (motion) 0 else 1) }
    LaunchedEffect(Unit) { tick = 1 }
    AnimatedVisibility(
        visible = tick == 1,
        enter = if (!motion) {
            EnterTransition.None
        } else if (calm) {
            fadeIn(tween(220))
        } else {
            fadeIn(tween(280)) + slideInVertically(tween(320)) { it / 14 }
        },
    ) {
        content()
    }
}

internal fun kidsRouteEnter(route: String?, motion: Boolean): EnterTransition {
    if (!motion || !route.isKidsSection()) return if (motion) fadeIn(tween(700)) else EnterTransition.None
    return fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 10 }
}

internal fun kidsRouteExit(from: String?, to: String?, motion: Boolean, pop: Boolean): ExitTransition {
    val inside = from.isKidsSection() && to.isKidsSection()
    if (!motion) return ExitTransition.None
    if (!inside) return fadeOut(tween(700))
    return if (pop) {
        fadeOut(tween(200)) + slideOutHorizontally(tween(280)) { it / 10 }
    } else {
        fadeOut(tween(180))
    }
}

internal fun String?.isKidsSection(): Boolean {
    val head = this?.substringBefore("?")?.substringBefore("/").orEmpty()
    return head == "kids" || head.startsWith("kids_") || head == "azbuka" || head == "cifry"
}

@Composable
internal fun KidsRewardStars(count: Int = 3) {
    val motion = kidsMotionEnabled()
    var shown by remember { mutableStateOf(!motion) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.72f,
        animationSpec = if (motion) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        } else {
            tween(0)
        },
        label = "reward",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.scale(scale)) {
        repeat(count.coerceIn(1, 5)) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = Color(0xFFE0A15A),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
