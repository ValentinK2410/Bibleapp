package com.example.bible.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

@Composable
fun BibleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appThemePreset: BibleAppThemePreset = BibleAppThemePreset.STANDARD,
    studio: ThemeStudioState = ThemeStudioState(),
    onSaveFieldLook: (String, FieldLook) -> Unit = { _, _ -> },
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val base = when {
        appThemePreset != BibleAppThemePreset.STANDARD -> colorSchemeFor(appThemePreset, darkTheme)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> colorSchemeFor(BibleAppThemePreset.STANDARD, darkTheme)
    }
    val colorScheme = base.withOverrides(studio.colorOverrides)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BibleTypography.scaledBy(studio.textScale),
    ) {
        CompositionLocalProvider(
            LocalThemeFieldController provides ThemeFieldController(studio.fields, onSaveFieldLook),
            content = content,
        )
    }
}

/**
 * Отдельная дневная / ночная палитра только для раздела «Песнопение» (не зависит от темы всего приложения).
 */
@Composable
fun PesnopenieMaterialTheme(
    useDark: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDark) PesnopenieDarkColorScheme else PesnopenieLightColorScheme,
        typography = BibleTypography,
        content = content,
    )
}
