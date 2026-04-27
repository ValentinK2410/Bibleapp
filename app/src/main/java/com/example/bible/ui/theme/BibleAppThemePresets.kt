package com.example.bible.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.bible.R

/** Пользовательские темы оформления (плюс стандартная). */
enum class BibleAppThemePreset(val storageKey: String) {
    STANDARD("standard"),
    /** Тёмные тона, «металл», высокий контраст. */
    BRUTAL("brutal"),
    /** Розовая, тёплая, с цветочным настроением. */
    PINK("pink"),
    /** Голубая нежность, небо. */
    SKY("sky"),
    /** Салатовый луг, природа. */
    MEADOW("meadow"),
    /** Тёплый пергамент / папирус. */
    PAPYRUS("papyrus"),
    /** Коричневые тона кожи и свитков. */
    LEATHER("leather"),
    ;

    companion object {
        fun fromStorageKey(key: String?): BibleAppThemePreset =
            entries.find { it.storageKey == key } ?: STANDARD
    }
}

private val StandardLight = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF4A6572),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE3EE),
    onSecondaryContainer = Color(0xFF0B1D26),
    tertiary = Color(0xFF3A6B4C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBDE8CA),
    onTertiaryContainer = Color(0xFF00210D),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF2F4F7),
    onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFE0E3EA),
    onSurfaceVariant = Color(0xFF434750),
    outline = Color(0xFFC4C7CE),
    outlineVariant = Color(0xFFDEE1E8),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F6),
    inversePrimary = Color(0xFF9ECAFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F5FA),
    surfaceContainer = Color(0xFFEEEFF4),
    surfaceContainerHigh = Color(0xFFE8E9EF),
    surfaceContainerHighest = Color(0xFFE2E3E9),
)

private val StandardDark = darkColorScheme(
    primary = Color(0xFF8ABAFF),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF1D3A5C),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFA8C7D8),
    onSecondary = Color(0xFF19323E),
    secondaryContainer = Color(0xFF334955),
    onSecondaryContainer = Color(0xFFCFE3EE),
    tertiary = Color(0xFF8BCBA0),
    onTertiary = Color(0xFF003920),
    tertiaryContainer = Color(0xFF1E4430),
    onTertiaryContainer = Color(0xFFBDE8CA),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E3E9),
    surface = Color(0xFF1A1C22),
    onSurface = Color(0xFFE2E3E9),
    surfaceVariant = Color(0xFF3A3D46),
    onSurfaceVariant = Color(0xFFBFC3CE),
    outline = Color(0xFF6E727C),
    outlineVariant = Color(0xFF434750),
    inverseSurface = Color(0xFFE2E3E9),
    inverseOnSurface = Color(0xFF2F3036),
    inversePrimary = Color(0xFF1565C0),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF1E2026),
    surfaceContainer = Color(0xFF22242A),
    surfaceContainerHigh = Color(0xFF282A30),
    surfaceContainerHighest = Color(0xFF33353B),
)

private val BrutalLight = lightColorScheme(
    primary = Color(0xFF37474F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFD8DC),
    onPrimaryContainer = Color(0xFF1C2529),
    secondary = Color(0xFF455A64),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB0BEC5),
    onSecondaryContainer = Color(0xFF1A2327),
    tertiary = Color(0xFF6D4C41),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7CCC8),
    onTertiaryContainer = Color(0xFF261814),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFBDBDBD),
    onBackground = Color(0xFF121212),
    surface = Color(0xFFECEFF1),
    onSurface = Color(0xFF121212),
    surfaceVariant = Color(0xFFCFD8DC),
    onSurfaceVariant = Color(0xFF263238),
    outline = Color(0xFF607D8B),
    outlineVariant = Color(0xFF90A4AE),
    inverseSurface = Color(0xFF212121),
    inverseOnSurface = Color(0xFFEEEEEE),
    inversePrimary = Color(0xFF90A4AE),
    surfaceContainerLowest = Color(0xFFE0E0E0),
    surfaceContainerLow = Color(0xFFE8EAF0),
    surfaceContainer = Color(0xFFECEFF1),
    surfaceContainerHigh = Color(0xFFD7DDE3),
    surfaceContainerHighest = Color(0xFFC9D0D6),
)

private val BrutalDark = darkColorScheme(
    primary = Color(0xFFB0BEC5),
    onPrimary = Color(0xFF1C2529),
    primaryContainer = Color(0xFF37474F),
    onPrimaryContainer = Color(0xFFECEFF1),
    secondary = Color(0xFF78909C),
    onSecondary = Color(0xFF0D1215),
    secondaryContainer = Color(0xFF263238),
    onSecondaryContainer = Color(0xFFCFD8DC),
    tertiary = Color(0xFF8D6E63),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF4E342E),
    onTertiaryContainer = Color(0xFFD7CCC8),
    error = Color(0xFFEF5350),
    onError = Color(0xFF370000),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121416),
    onBackground = Color(0xFFECEFF1),
    surface = Color(0xFF1C1F23),
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = Color(0xFF2E3438),
    onSurfaceVariant = Color(0xFFB0BEC5),
    outline = Color(0xFF546E7A),
    outlineVariant = Color(0xFF37474F),
    inverseSurface = Color(0xFFE0E0E0),
    inverseOnSurface = Color(0xFF1A1A1A),
    inversePrimary = Color(0xFF455A64),
    surfaceContainerLowest = Color(0xFF0D0F11),
    surfaceContainerLow = Color(0xFF181B1F),
    surfaceContainer = Color(0xFF1C1F23),
    surfaceContainerHigh = Color(0xFF25292E),
    surfaceContainerHighest = Color(0xFF2F3439),
)

private val PinkLight = lightColorScheme(
    primary = Color(0xFFC2185B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF8BBD0),
    onPrimaryContainer = Color(0xFF4A0012),
    secondary = Color(0xFFAD1457),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE4EC),
    onSecondaryContainer = Color(0xFF3E142F),
    tertiary = Color(0xFF8E24AA),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1BEE7),
    onTertiaryContainer = Color(0xFF2E004E),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF5F9),
    onBackground = Color(0xFF321018),
    surface = Color(0xFFFFFBFC),
    onSurface = Color(0xFF321018),
    surfaceVariant = Color(0xFFF2D9E4),
    onSurfaceVariant = Color(0xFF524347),
    outline = Color(0xFF85707A),
    outlineVariant = Color(0xFFD5C2CC),
    inverseSurface = Color(0xFF392A2D),
    inverseOnSurface = Color(0xFFFFECF1),
    inversePrimary = Color(0xFFFFB1C8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF3F7),
    surfaceContainer = Color(0xFFFDEBF1),
    surfaceContainerHigh = Color(0xFFF5E0E8),
    surfaceContainerHighest = Color(0xFFEDD5DF),
)

private val PinkDark = darkColorScheme(
    primary = Color(0xFFFFB2C6),
    onPrimary = Color(0xFF5E0026),
    primaryContainer = Color(0xFF880E4F),
    onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFFFB0D0),
    onSecondary = Color(0xFF4A0D2E),
    secondaryContainer = Color(0xFF6D1E45),
    onSecondaryContainer = Color(0xFFFFD8EA),
    tertiary = Color(0xFFDDA5F6),
    onTertiary = Color(0xFF3E0060),
    tertiaryContainer = Color(0xFF5E2A7A),
    onTertiaryContainer = Color(0xFFF3DAFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1F1418),
    onBackground = Color(0xFFF8DDE6),
    surface = Color(0xFF27191E),
    onSurface = Color(0xFFF8DDE6),
    surfaceVariant = Color(0xFF4A3E42),
    onSurfaceVariant = Color(0xFFDCC2CB),
    outline = Color(0xFFA68F98),
    outlineVariant = Color(0xFF524347),
    inverseSurface = Color(0xFFF8DDE6),
    inverseOnSurface = Color(0xFF392A2D),
    inversePrimary = Color(0xFFC2185B),
    surfaceContainerLowest = Color(0xFF190F13),
    surfaceContainerLow = Color(0xFF22151A),
    surfaceContainer = Color(0xFF27191E),
    surfaceContainerHigh = Color(0xFF322328),
    surfaceContainerHighest = Color(0xFF3D2D33),
)

private val SkyLight = lightColorScheme(
    primary = Color(0xFF0277BD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB3E5FC),
    onPrimaryContainer = Color(0xFF002F4A),
    secondary = Color(0xFF0288D1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1F5FE),
    onSecondaryContainer = Color(0xFF002E46),
    tertiary = Color(0xFF00838F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB2EBF2),
    onTertiaryContainer = Color(0xFF002022),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFE3F4FD),
    onBackground = Color(0xFF0A1E2E),
    surface = Color(0xFFF5FCFF),
    onSurface = Color(0xFF0A1E2E),
    surfaceVariant = Color(0xFFCFE8F5),
    onSurfaceVariant = Color(0xFF3E4A52),
    outline = Color(0xFF6E8A9A),
    outlineVariant = Color(0xFFBFD8E8),
    inverseSurface = Color(0xFF1E2A33),
    inverseOnSurface = Color(0xFFE4F3FF),
    inversePrimary = Color(0xFF81D4FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF9FF),
    surfaceContainer = Color(0xFFE8F6FD),
    surfaceContainerHigh = Color(0xFFDCEEF8),
    surfaceContainerHighest = Color(0xFFD0E6F2),
)

private val SkyDark = darkColorScheme(
    primary = Color(0xFF81D4FA),
    onPrimary = Color(0xFF003547),
    primaryContainer = Color(0xFF01579B),
    onPrimaryContainer = Color(0xFFB3E5FC),
    secondary = Color(0xFF4FC3F7),
    onSecondary = Color(0xFF00344D),
    secondaryContainer = Color(0xFF0277BD),
    onSecondaryContainer = Color(0xFFE1F5FE),
    tertiary = Color(0xFF4DD0E1),
    onTertiary = Color(0xFF00363A),
    tertiaryContainer = Color(0xFF006064),
    onTertiaryContainer = Color(0xFFB2EBF2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D1B24),
    onBackground = Color(0xFFB8E5FF),
    surface = Color(0xFF13232E),
    onSurface = Color(0xFFB8E5FF),
    surfaceVariant = Color(0xFF2A3F4D),
    onSurfaceVariant = Color(0xFFB0CAD8),
    outline = Color(0xFF7A9DB0),
    outlineVariant = Color(0xFF3A4F5C),
    inverseSurface = Color(0xFFB8E5FF),
    inverseOnSurface = Color(0xFF0D1B24),
    inversePrimary = Color(0xFF0277BD),
    surfaceContainerLowest = Color(0xFF08141C),
    surfaceContainerLow = Color(0xFF101E28),
    surfaceContainer = Color(0xFF13232E),
    surfaceContainerHigh = Color(0xFF1C2E3A),
    surfaceContainerHighest = Color(0xFF263947),
)

private val MeadowLight = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF002106),
    secondary = Color(0xFF558B2F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F8E9),
    onSecondaryContainer = Color(0xFF1F2E0A),
    tertiary = Color(0xFF33691E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCEDC8),
    onTertiaryContainer = Color(0xFF0F2004),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFE8F5E9),
    onBackground = Color(0xFF102014),
    surface = Color(0xFFF9FBE7),
    onSurface = Color(0xFF102014),
    surfaceVariant = Color(0xFFD8ECD9),
    onSurfaceVariant = Color(0xFF3E4A3E),
    outline = Color(0xFF6B8A6C),
    outlineVariant = Color(0xFFBFD9BF),
    inverseSurface = Color(0xFF1E281E),
    inverseOnSurface = Color(0xFFE4F3E4),
    inversePrimary = Color(0xFFA5D6A7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2FAF0),
    surfaceContainer = Color(0xFFECF6EA),
    surfaceContainerHigh = Color(0xFFE0EEE0),
    surfaceContainerHighest = Color(0xFFD4E6D4),
)

private val MeadowDark = darkColorScheme(
    primary = Color(0xFFA5D6A7),
    onPrimary = Color(0xFF003910),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFCDDC39),
    onSecondary = Color(0xFF1F2E00),
    secondaryContainer = Color(0xFF3E4E14),
    onSecondaryContainer = Color(0xFFE6EE9C),
    tertiary = Color(0xFF9CCC65),
    onTertiary = Color(0xFF1B3008),
    tertiaryContainer = Color(0xFF33691E),
    onTertiaryContainer = Color(0xFFE8F5D0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101910),
    onBackground = Color(0xFFD0E8D0),
    surface = Color(0xFF162016),
    onSurface = Color(0xFFD0E8D0),
    surfaceVariant = Color(0xFF2D3F2D),
    onSurfaceVariant = Color(0xFFB8D0B8),
    outline = Color(0xFF6E8A6E),
    outlineVariant = Color(0xFF3D4F3D),
    inverseSurface = Color(0xFFD0E8D0),
    inverseOnSurface = Color(0xFF101910),
    inversePrimary = Color(0xFF2E7D32),
    surfaceContainerLowest = Color(0xFF0B130B),
    surfaceContainerLow = Color(0xFF131C13),
    surfaceContainer = Color(0xFF162016),
    surfaceContainerHigh = Color(0xFF202B20),
    surfaceContainerHighest = Color(0xFF2A362A),
)

private val PapyrusLight = lightColorScheme(
    primary = Color(0xFF6D4C41),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7CCC8),
    onPrimaryContainer = Color(0xFF261812),
    secondary = Color(0xFF5D4037),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0D2C8),
    onSecondaryContainer = Color(0xFF261812),
    tertiary = Color(0xFF8D6E63),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7CCC8),
    onTertiaryContainer = Color(0xFF261812),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFEDE0CD),
    onBackground = Color(0xFF2C1810),
    surface = Color(0xFFF5EBD8),
    onSurface = Color(0xFF2C1810),
    surfaceVariant = Color(0xFFE2D6C4),
    onSurfaceVariant = Color(0xFF4A3F35),
    outline = Color(0xFF7D6E62),
    outlineVariant = Color(0xFFD1C4B4),
    inverseSurface = Color(0xFF382E26),
    inverseOnSurface = Color(0xFFF5E6D6),
    inversePrimary = Color(0xFFD7CCC8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3E8D4),
    surfaceContainer = Color(0xFFEFE4CF),
    surfaceContainerHigh = Color(0xFFE6D9C4),
    surfaceContainerHighest = Color(0xFFDCCFB8),
)

private val PapyrusDark = darkColorScheme(
    primary = Color(0xFFD4A574),
    onPrimary = Color(0xFF2C1810),
    primaryContainer = Color(0xFF5D4037),
    onPrimaryContainer = Color(0xFFE8DCC8),
    secondary = Color(0xFFBCAAA4),
    onSecondary = Color(0xFF261812),
    secondaryContainer = Color(0xFF4E342E),
    onSecondaryContainer = Color(0xFFE0D2C8),
    tertiary = Color(0xFFA1887F),
    onTertiary = Color(0xFF261812),
    tertiaryContainer = Color(0xFF5D4037),
    onTertiaryContainer = Color(0xFFE8DCC8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF2A2418),
    onBackground = Color(0xFFE8DCC8),
    surface = Color(0xFF342C20),
    onSurface = Color(0xFFE8DCC8),
    surfaceVariant = Color(0xFF4A4034),
    onSurfaceVariant = Color(0xFFD1C4B4),
    outline = Color(0xFF9A8B7C),
    outlineVariant = Color(0xFF524838),
    inverseSurface = Color(0xFFE8DCC8),
    inverseOnSurface = Color(0xFF2A2418),
    inversePrimary = Color(0xFF6D4C41),
    surfaceContainerLowest = Color(0xFF221C14),
    surfaceContainerLow = Color(0xFF2E271C),
    surfaceContainer = Color(0xFF342C20),
    surfaceContainerHigh = Color(0xFF3F362A),
    surfaceContainerHighest = Color(0xFF4A4034),
)

private val LeatherLight = lightColorScheme(
    primary = Color(0xFF5D4037),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7CCC8),
    onPrimaryContainer = Color(0xFF261812),
    secondary = Color(0xFF6D4C41),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0D2C8),
    onSecondaryContainer = Color(0xFF261812),
    tertiary = Color(0xFF8D6E63),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7CCC8),
    onTertiaryContainer = Color(0xFF261812),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFD7CCC8),
    onBackground = Color(0xFF2C1810),
    surface = Color(0xFFEFEBE9),
    onSurface = Color(0xFF2C1810),
    surfaceVariant = Color(0xFFE0D4CE),
    onSurfaceVariant = Color(0xFF4A3F35),
    outline = Color(0xFF7D6E62),
    outlineVariant = Color(0xFFCDBFBA),
    inverseSurface = Color(0xFF382E26),
    inverseOnSurface = Color(0xFFF5E6D6),
    inversePrimary = Color(0xFFD7CCC8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2EDE9),
    surfaceContainer = Color(0xFFEDE7E3),
    surfaceContainerHigh = Color(0xFFE5DED9),
    surfaceContainerHighest = Color(0xFFDCD4CF),
)

private val LeatherDark = darkColorScheme(
    primary = Color(0xFFFFCA28),
    onPrimary = Color(0xFF3E2723),
    primaryContainer = Color(0xFF6D4C41),
    onPrimaryContainer = Color(0xFFFFE082),
    secondary = Color(0xFFD7CCC8),
    onSecondary = Color(0xFF3E2723),
    secondaryContainer = Color(0xFF5D4037),
    onSecondaryContainer = Color(0xFFE0D2C8),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF3E2723),
    tertiaryContainer = Color(0xFF6D4C41),
    onTertiaryContainer = Color(0xFFFFE0B2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF2D2419),
    onBackground = Color(0xFFFFF8E1),
    surface = Color(0xFF3E3228),
    onSurface = Color(0xFFFFF8E1),
    surfaceVariant = Color(0xFF52483C),
    onSurfaceVariant = Color(0xFFD7CCC8),
    outline = Color(0xFF9A8B7C),
    outlineVariant = Color(0xFF524838),
    inverseSurface = Color(0xFFFFF8E1),
    inverseOnSurface = Color(0xFF2D2419),
    inversePrimary = Color(0xFF5D4037),
    surfaceContainerLowest = Color(0xFF251C14),
    surfaceContainerLow = Color(0xFF342A20),
    surfaceContainer = Color(0xFF3E3228),
    surfaceContainerHigh = Color(0xFF4A3D32),
    surfaceContainerHighest = Color(0xFF56483C),
)

fun colorSchemeFor(preset: BibleAppThemePreset, dark: Boolean): ColorScheme = when (preset) {
    BibleAppThemePreset.STANDARD -> if (dark) StandardDark else StandardLight
    BibleAppThemePreset.BRUTAL -> if (dark) BrutalDark else BrutalLight
    BibleAppThemePreset.PINK -> if (dark) PinkDark else PinkLight
    BibleAppThemePreset.SKY -> if (dark) SkyDark else SkyLight
    BibleAppThemePreset.MEADOW -> if (dark) MeadowDark else MeadowLight
    BibleAppThemePreset.PAPYRUS -> if (dark) PapyrusDark else PapyrusLight
    BibleAppThemePreset.LEATHER -> if (dark) LeatherDark else LeatherLight
}

/** Стандартные палитры для экранов с собственной оболочкой (например песнопение). */
val PesnopenieLightColorScheme: ColorScheme = StandardLight
val PesnopenieDarkColorScheme: ColorScheme = StandardDark

/** JPEG в `res/drawable-nodpi` (стабильные кадры с picsum.photos по seed). */
private fun backdropDrawableRes(preset: BibleAppThemePreset): Int? = when (preset) {
    BibleAppThemePreset.STANDARD -> null
    BibleAppThemePreset.BRUTAL -> R.drawable.theme_backdrop_brutal
    BibleAppThemePreset.PINK -> R.drawable.theme_backdrop_pink
    BibleAppThemePreset.SKY -> R.drawable.theme_backdrop_sky
    BibleAppThemePreset.MEADOW -> R.drawable.theme_backdrop_meadow
    BibleAppThemePreset.PAPYRUS -> R.drawable.theme_backdrop_papyrus
    BibleAppThemePreset.LEATHER -> R.drawable.theme_backdrop_leather
}

/**
 * Фон окна: полноэкранное фото + градиентный скрим цветом темы, чтобы текст и панели оставались читаемыми.
 */
@Composable
fun ThemedWindowBackdrop(
    preset: BibleAppThemePreset,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    if (preset == BibleAppThemePreset.STANDARD) return@ThemedWindowBackdrop
    val resId = backdropDrawableRes(preset) ?: return@ThemedWindowBackdrop
    val base = MaterialTheme.colorScheme.background
    val a0 = if (dark) 0.48f else 0.36f
    val a1 = if (dark) 0.62f else 0.46f
    val a2 = if (dark) 0.74f else 0.56f
    Box(modifier = modifier) {
        Image(
            painter = painterResource(resId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to base.copy(alpha = a0),
                        0.45f to base.copy(alpha = a1),
                        1f to base.copy(alpha = a2),
                    ),
                ),
        )
    }
}
