package com.example.bible.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
