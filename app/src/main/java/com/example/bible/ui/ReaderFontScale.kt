package com.example.bible.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bible.R
object ReaderFontScaleDefaults {
    const val MIN = 0.16f
    const val MAX = 8.4f
    const val STEP = 0.1f
    const val DEFAULT = 1f
    const val BASE_SP = 18f
}

/** Масштаб названий в списке «Медиа → Видео». */
object VideoLibraryFontDefaults {
    const val MIN = 0.75f
    const val MAX = 1.4f
    const val STEP = 0.05f
    const val DEFAULT = 1f
    /** Базовый размер заголовка (sp) до умножения на масштаб. */
    const val BASE_TITLE_SP = 12f
    const val BASE_META_SP = 11f
}

/**
 * Множитель к системному [Density.fontScale] для текста чтения (sp).
 */
@Composable
fun ProvideReaderFontScale(
    multiplier: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scaled = remember(multiplier, density) {
        Density(
            density = density.density,
            fontScale = (density.fontScale * multiplier).coerceIn(0.1f, 10f),
        )
    }
    CompositionLocalProvider(LocalDensity provides scaled, content = content)
}

/** Плавающие «+» / «−» для масштаба текста в читалке. */
@Composable
fun ReaderFontScaleFabControls(
    readerFontScale: Float,
    onAdjustFontScale: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** Отступ снизу под панель озвучки главы. */
    bottomInset: Dp = 0.dp,
) {
    val canDecrease = readerFontScale > ReaderFontScaleDefaults.MIN + 0.001f
    val canIncrease = readerFontScale < ReaderFontScaleDefaults.MAX - 0.001f

    Column(
        modifier = modifier.padding(end = 12.dp, bottom = 12.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SmallFloatingActionButton(
            onClick = {
                if (canIncrease) onAdjustFontScale(ReaderFontScaleDefaults.STEP)
            },
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(
                alpha = if (canIncrease) 1f else 0.45f,
            ),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                alpha = if (canIncrease) 1f else 0.45f,
            ),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.font_increase),
            )
        }
        SmallFloatingActionButton(
            onClick = {
                if (canDecrease) onAdjustFontScale(-ReaderFontScaleDefaults.STEP)
            },
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(
                alpha = if (canDecrease) 1f else 0.45f,
            ),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                alpha = if (canDecrease) 1f else 0.45f,
            ),
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = stringResource(R.string.font_decrease),
            )
        }
    }
}
