package com.example.bible.ui.housecare

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.PowerManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Цветовая палитра для раздела «Попечительная о доме» (включая «Стандартный» исходный вариант). */
internal data class HouseCarePalette(
    val key: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val primaryLight: Color,
    val primaryContainerLight: Color,
    val secondaryLight: Color,
    val backgroundLight: Color,
    val surfaceLight: Color,
    val borderLight: Color,
    val primaryDark: Color,
    val primaryContainerDark: Color,
    val backgroundDark: Color,
    val surfaceDark: Color,
    val borderDark: Color,
) {
    fun primary(dark: Boolean): Color = if (dark) primaryDark else primaryLight
    fun primaryContainer(dark: Boolean): Color = if (dark) primaryContainerDark else primaryContainerLight
    fun background(dark: Boolean): Color = if (dark) backgroundDark else backgroundLight
    fun surface(dark: Boolean): Color = if (dark) surfaceDark else surfaceLight
    fun border(dark: Boolean): Color = if (dark) borderDark else borderLight
}

internal val houseCarePalettes: List<HouseCarePalette> = listOf(
    HouseCarePalette(
        key = "standard",
        title = "Стандартный",
        subtitle = "Изначальное оформление приложения (как было изначально)",
        emoji = "🏛️",
        primaryLight = Color(0xFF3F51B5),
        primaryContainerLight = Color(0xFFE8EAF6),
        secondaryLight = Color(0xFF5C6BC0),
        backgroundLight = Color(0xFFFAFAFA),
        surfaceLight = Color(0xFFFFFFFF),
        borderLight = Color(0xFFE0E0E0),
        primaryDark = Color(0xFF9FA8DA),
        primaryContainerDark = Color(0xFF283593),
        backgroundDark = Color(0xFF121212),
        surfaceDark = Color(0xFF1E1E1E),
        borderDark = Color(0xFF424242),
    ),
    HouseCarePalette(
        key = "rose_blush",
        title = "Нежная роза",
        subtitle = "Лепестки пиона и пудровый шёлк",
        emoji = "🌸",
        primaryLight = Color(0xFFC25278),
        primaryContainerLight = Color(0xFFFCE8EF),
        secondaryLight = Color(0xFF9C5B73),
        backgroundLight = Color(0xFFFFF8FA),
        surfaceLight = Color(0xFFFFFBFD),
        borderLight = Color(0xFFF2C6D6),
        primaryDark = Color(0xFFF49AB8),
        primaryContainerDark = Color(0xFF562336),
        backgroundDark = Color(0xFF1F161A),
        surfaceDark = Color(0xFF2A1E23),
        borderDark = Color(0xFF6B374B),
    ),
    HouseCarePalette(
        key = "lavender_dream",
        title = "Лавандовый уют",
        subtitle = "Нежная сирень и прованское утро",
        emoji = "💜",
        primaryLight = Color(0xFF7E57C2),
        primaryContainerLight = Color(0xFFF1E8FC),
        secondaryLight = Color(0xFF6E5B8C),
        backgroundLight = Color(0xFFFAF7FE),
        surfaceLight = Color(0xFFFDFAFF),
        borderLight = Color(0xFFD9C5F2),
        primaryDark = Color(0xFFC8ADF7),
        primaryContainerDark = Color(0xFF3E2963),
        backgroundDark = Color(0xFF1A1621),
        surfaceDark = Color(0xFF241F2E),
        borderDark = Color(0xFF563F7D),
    ),
    HouseCarePalette(
        key = "peachy_peony",
        title = "Персиковый пион",
        subtitle = "Тёплый коралл и сливочный крем",
        emoji = "🌷",
        primaryLight = Color(0xFFC65F4B),
        primaryContainerLight = Color(0xFFFCECE8),
        secondaryLight = Color(0xFF9E6458),
        backgroundLight = Color(0xFFFFF9F7),
        surfaceLight = Color(0xFFFFFCFB),
        borderLight = Color(0xFFF2C7BE),
        primaryDark = Color(0xFFF5A393),
        primaryContainerDark = Color(0xFF59291F),
        backgroundDark = Color(0xFF211816),
        surfaceDark = Color(0xFF2C201E),
        borderDark = Color(0xFF703B30),
    ),
    HouseCarePalette(
        key = "mint_garden",
        title = "Мятный сад",
        subtitle = "Свежесть утренней росы и шалфея",
        emoji = "🌿",
        primaryLight = Color(0xFF3A8B73),
        primaryContainerLight = Color(0xFFE5F6F0),
        secondaryLight = Color(0xFF527D70),
        backgroundLight = Color(0xFFF6FCFA),
        surfaceLight = Color(0xFFFAFEFD),
        borderLight = Color(0xFFB9E2D5),
        primaryDark = Color(0xFF8CD9C1),
        primaryContainerDark = Color(0xFF1E4B3E),
        backgroundDark = Color(0xFF141E1B),
        surfaceDark = Color(0xFF1C2925),
        borderDark = Color(0xFF316455),
    ),
    HouseCarePalette(
        key = "berry_velvet",
        title = "Ягодный бархат",
        subtitle = "Спелая малина и пудровая ваниль",
        emoji = "🌺",
        primaryLight = Color(0xFFA63664),
        primaryContainerLight = Color(0xFFFBE6EF),
        secondaryLight = Color(0xFF8C4C66),
        backgroundLight = Color(0xFFFFF6FA),
        surfaceLight = Color(0xFFFFFAFC),
        borderLight = Color(0xFFEBBAD0),
        primaryDark = Color(0xFFF291B8),
        primaryContainerDark = Color(0xFF521B33),
        backgroundDark = Color(0xFF20151A),
        surfaceDark = Color(0xFF2B1C23),
        borderDark = Color(0xFF692B46),
    ),
    HouseCarePalette(
        key = "warm_vanilla",
        title = "Тёплая ваниль",
        subtitle = "Мягкое золото, карамель и уют",
        emoji = "✨",
        primaryLight = Color(0xFFA67332),
        primaryContainerLight = Color(0xFFFBF0E1),
        secondaryLight = Color(0xFF8A6E4B),
        backgroundLight = Color(0xFFFFFBF5),
        surfaceLight = Color(0xFFFFFDF9),
        borderLight = Color(0xFFEAD1B0),
        primaryDark = Color(0xFFECC186),
        primaryContainerDark = Color(0xFF4F3616),
        backgroundDark = Color(0xFF1F1B15),
        surfaceDark = Color(0xFF2A241C),
        borderDark = Color(0xFF664924),
    ),
)

internal fun paletteByKey(key: String?): HouseCarePalette =
    houseCarePalettes.firstOrNull { it.key == key } ?: houseCarePalettes.first()

/** Проверяет, является ли комната «15-минуткой». */
internal fun isFifteenMinuteZoneName(name: String?): Boolean {
    val lower = name?.trim()?.lowercase().orEmpty()
    return "15" in lower && "минут" in lower
}

/** Эмодзи-значки для оформления отдельных комнат и разделов уборки. */
internal val houseCareDecorativeEmojis = listOf(
    "🌸", "🌷", "✨", "🫧", "🪞", "🕯️", "🌿", "🧺", "🛋️", "🛏️", "🛁", "🍽️", "🧸", "⏱", "🏡", "💎",
)

/** Подразделы (экраны) внутри «Попечительной о доме», каждый из которых можно настроить отдельно. */
internal enum class HouseCareSectionId(val key: String, val title: String, val emoji: String) {
    Hub("hub", "Главная «Попечительной о доме»", "🌸"),
    System("system", "Система уборки и объекты", "🏡"),
    Week("week", "Неделя и этажи", "📅"),
    Day("day", "День недели и привычки", "☀️"),
    Floor("floor", "Комнаты этажа", "🚪"),
    Zone("zone", "Комната, разделы и 15-минутка", "🧹"),
    Task("task", "Раздел и фото «До / После»", "📸"),
    Stats("stats", "Статистика уборки", "📊"),
}

/** Стиль карточек внутри раздела. */
internal enum class HouseCareCardStyle(val key: String, val title: String, val desc: String) {
    Standard("standard", "Стандартный", "Изначальный вид карточек"),
    SoftBlush("soft_blush", "Нежный румянец", "Мягкий оттенок палитры и тонкий кант"),
    Pearl("pearl", "Жемчужная гладь", "Чистая светлая поверхность с цветной рамкой"),
    Watercolor("watercolor", "Акварель", "Плавный воздушный градиент на карточках"),
    LaceBorder("lace_border", "Ажурная рамка", "Выразительный цветной контур и нежный фон"),
    ;

    companion object {
        fun fromKey(k: String?): HouseCareCardStyle = entries.firstOrNull { it.key == k } ?: Standard
    }
}

/** Индивидуальные настройки конкретного подраздела (если пусто — наследует общую тему раздела). */
internal data class SectionCustomStyle(
    val paletteKey: String = "",
    val cardStyleKey: String = "",
    val cornerRadiusDp: Int = 0,
    val showFloralHeader: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("paletteKey", paletteKey)
        put("cardStyleKey", cardStyleKey)
        put("cornerRadiusDp", cornerRadiusDp)
        put("showFloralHeader", showFloralHeader)
    }

    companion object {
        fun fromJson(j: JSONObject?): SectionCustomStyle {
            if (j == null) return SectionCustomStyle()
            return SectionCustomStyle(
                paletteKey = j.optString("paletteKey", ""),
                cardStyleKey = j.optString("cardStyleKey", ""),
                cornerRadiusDp = j.optInt("cornerRadiusDp", 0),
                showFloralHeader = j.optBoolean("showFloralHeader", true),
            )
        }
    }
}

/** Полные настройки темы и звуков таймера «15-минутка». */
internal data class HouseCareThemeSettings(
    val globalPaletteKey: String = "standard",
    val globalCardStyleKey: String = HouseCareCardStyle.Standard.key,
    val globalCornerDp: Int = 22,
    val headerOrnament: Boolean = true,
    val sectionOverrides: Map<String, SectionCustomStyle> = emptyMap(),
    val timerTickSound: Boolean = true,
    val timerMinuteSound: Boolean = true,
    val timerVoiceCountdown: Boolean = true,
) {
    fun resolvePalette(section: HouseCareSectionId): HouseCarePalette {
        val overrideKey = sectionOverrides[section.key]?.paletteKey?.takeIf { it.isNotBlank() }
        return paletteByKey(overrideKey ?: globalPaletteKey)
    }

    fun resolveCardStyle(section: HouseCareSectionId): HouseCareCardStyle {
        val overrideKey = sectionOverrides[section.key]?.cardStyleKey?.takeIf { it.isNotBlank() }
        return HouseCareCardStyle.fromKey(overrideKey ?: globalCardStyleKey)
    }

    fun resolveCornerDp(section: HouseCareSectionId): Int {
        val overrideCorner = sectionOverrides[section.key]?.cornerRadiusDp ?: 0
        return if (overrideCorner in 14..34) overrideCorner else globalCornerDp.coerceIn(14, 34)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("globalPaletteKey", globalPaletteKey)
        put("globalCardStyleKey", globalCardStyleKey)
        put("globalCornerDp", globalCornerDp)
        put("headerOrnament", headerOrnament)
        val overridesObj = JSONObject()
        sectionOverrides.forEach { (k, v) -> overridesObj.put(k, v.toJson()) }
        put("sectionOverrides", overridesObj)
        put("timerTickSound", timerTickSound)
        put("timerMinuteSound", timerMinuteSound)
        put("timerVoiceCountdown", timerVoiceCountdown)
    }

    companion object {
        fun fromJson(raw: String?): HouseCareThemeSettings {
            if (raw.isNullOrBlank()) return HouseCareThemeSettings()
            return try {
                val j = JSONObject(raw)
                val overridesObj = j.optJSONObject("sectionOverrides")
                val map = mutableMapOf<String, SectionCustomStyle>()
                if (overridesObj != null) {
                    val keys = overridesObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        map[k] = SectionCustomStyle.fromJson(overridesObj.optJSONObject(k))
                    }
                }
                HouseCareThemeSettings(
                    globalPaletteKey = j.optString("globalPaletteKey", "standard").ifBlank { "standard" },
                    globalCardStyleKey = j.optString("globalCardStyleKey", HouseCareCardStyle.Standard.key),
                    globalCornerDp = j.optInt("globalCornerDp", 22).coerceIn(14, 34),
                    headerOrnament = j.optBoolean("headerOrnament", true),
                    sectionOverrides = map,
                    timerTickSound = j.optBoolean("timerTickSound", true),
                    timerMinuteSound = j.optBoolean("timerMinuteSound", true),
                    timerVoiceCountdown = j.optBoolean("timerVoiceCountdown", true),
                )
            } catch (_: Exception) {
                HouseCareThemeSettings()
            }
        }
    }
}

/** Хранилище темы и настроек таймера для «Попечительной о доме». */
internal object HouseCareThemeStore {
    private const val PREFS_NAME = "house_care_feminine_theme"
    private const val KEY_JSON = "theme_settings_json"

    private val _settings = MutableStateFlow(HouseCareThemeSettings())
    private var initialized = false

    fun observe(context: Context): StateFlow<HouseCareThemeSettings> {
        if (!initialized) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _settings.value = HouseCareThemeSettings.fromJson(prefs.getString(KEY_JSON, null))
            initialized = true
        }
        return _settings
    }

    fun update(context: Context, transform: (HouseCareThemeSettings) -> HouseCareThemeSettings) {
        observe(context)
        val next = transform(_settings.value)
        _settings.value = next
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, next.toJson().toString())
            .apply()
    }
}

internal data class ActiveHouseCareStyle(
    val section: HouseCareSectionId = HouseCareSectionId.Hub,
    val palette: HouseCarePalette = houseCarePalettes.first(),
    val cardStyle: HouseCareCardStyle = HouseCareCardStyle.Standard,
    val cornerDp: Int = 22,
    val headerOrnament: Boolean = true,
)

internal val LocalHouseCareStyle = compositionLocalOf { ActiveHouseCareStyle() }

/**
 * Обёртка темы ТОЛЬКО для экранов «Попечительной о доме»:
 * если выбран «Стандартный» стиль, полностью сохраняет исходную тему приложения как было изначально;
 * иначе применяет выбранную палитру раздела.
 */
@Composable
internal fun HouseCareThemeWrapper(
    section: HouseCareSectionId,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val settings by HouseCareThemeStore.observe(context).collectAsState()
    val dark = isSystemInDarkTheme()
    val palette = settings.resolvePalette(section)
    val cardStyle = settings.resolveCardStyle(section)
    val cornerDp = settings.resolveCornerDp(section)

    val baseScheme = MaterialTheme.colorScheme
    val isStandardPalette = palette.key == "standard"
    val targetScheme = remember(palette, dark, baseScheme, isStandardPalette) {
        if (isStandardPalette) {
            baseScheme
        } else {
            val primary = palette.primary(dark)
            val primaryContainer = palette.primaryContainer(dark)
            val background = palette.background(dark)
            val surface = palette.surface(dark)
            val border = palette.border(dark)
            baseScheme.copy(
                primary = primary,
                onPrimary = if (dark) Color(0xFF221018) else Color.White,
                primaryContainer = primaryContainer,
                onPrimaryContainer = if (dark) Color(0xFFFFEBF2) else primary,
                secondary = if (dark) palette.primaryDark else palette.secondaryLight,
                secondaryContainer = primaryContainer.copy(alpha = 0.7f),
                background = background,
                surface = surface,
                surfaceVariant = primaryContainer.copy(alpha = if (dark) 0.35f else 0.52f),
                outline = border,
                outlineVariant = border.copy(alpha = if (dark) 0.65f else 0.75f),
            )
        }
    }

    val activeStyle = remember(section, palette, cardStyle, cornerDp, settings.headerOrnament) {
        ActiveHouseCareStyle(
            section = section,
            palette = palette,
            cardStyle = cardStyle,
            cornerDp = cornerDp,
            headerOrnament = settings.headerOrnament,
        )
    }

    CompositionLocalProvider(LocalHouseCareStyle provides activeStyle) {
        MaterialTheme(
            colorScheme = targetScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

/**
 * Диалог настройки оформления:
 * позволяет вернуть стандартный вид (как было изначально) или выбрать нежную палитру.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HouseCareThemeDialog(
    initialSection: HouseCareSectionId,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val settings by HouseCareThemeStore.observe(context).collectAsState()
    val dark = isSystemInDarkTheme()
    // null = настраиваем общую тему всего раздела, иначе — конкретный подраздел
    var selectedSection by remember { mutableStateOf<HouseCareSectionId?>(null) }

    val currentOverride = selectedSection?.let { settings.sectionOverrides[it.key] }
    val activePaletteKey = if (selectedSection == null) {
        settings.globalPaletteKey
    } else {
        currentOverride?.paletteKey.orEmpty()
    }
    val activeCardStyleKey = if (selectedSection == null) {
        settings.globalCardStyleKey
    } else {
        currentOverride?.cardStyleKey.orEmpty()
    }
    val activeCornerDp = if (selectedSection == null) {
        settings.globalCornerDp
    } else {
        currentOverride?.cornerRadiusDp ?: 0
    }

    val isAlreadyStandard = settings.globalPaletteKey == "standard" &&
        settings.globalCardStyleKey == HouseCareCardStyle.Standard.key &&
        settings.globalCornerDp == 22 &&
        settings.sectionOverrides.isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🎨", fontSize = 20.sp)
                Column {
                    Text(
                        "Оформление раздела",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Стандартный стиль или нежные палитры",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!isAlreadyStandard) {
                    OutlinedButton(
                        onClick = {
                            selectedSection = null
                            HouseCareThemeStore.update(context) { cur ->
                                cur.copy(
                                    globalPaletteKey = "standard",
                                    globalCardStyleKey = HouseCareCardStyle.Standard.key,
                                    globalCornerDp = 22,
                                    sectionOverrides = emptyMap(),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Вернуть стандартный стиль (как было изначально)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Text(
                    "Что настраиваем",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChoiceChip(
                        label = "✨ Весь раздел",
                        selected = selectedSection == null,
                        onClick = { selectedSection = null },
                    )
                    ChoiceChip(
                        label = "${initialSection.emoji} Текущий экран",
                        selected = selectedSection == initialSection,
                        onClick = { selectedSection = initialSection },
                    )
                    HouseCareSectionId.entries
                        .filter { it != initialSection }
                        .forEach { sec ->
                            val hasCustom = settings.sectionOverrides[sec.key]?.let {
                                it.paletteKey.isNotBlank() || it.cardStyleKey.isNotBlank() || it.cornerRadiusDp > 0
                            } == true
                            ChoiceChip(
                                label = "${sec.emoji} ${sec.title}" + (if (hasCustom) " •" else ""),
                                selected = selectedSection == sec,
                                onClick = { selectedSection = sec },
                            )
                        }
                }

                if (selectedSection != null) {
                    Text(
                        "Настройка для экрана «${selectedSection?.title}». Можно выбрать свою палитру и стиль карточек именно для него.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (activePaletteKey.isNotBlank() || activeCardStyleKey.isNotBlank() || activeCornerDp > 0) {
                        TextButton(
                            onClick = {
                                val secKey = selectedSection?.key ?: return@TextButton
                                HouseCareThemeStore.update(context) { cur ->
                                    cur.copy(sectionOverrides = cur.sectionOverrides - secKey)
                                }
                            },
                        ) {
                            Text("Сбросить к общей теме раздела")
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    "Цветовая палитра",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )

                if (selectedSection != null) {
                    ChoiceChip(
                        label = "Наследовать общую палитру раздела",
                        selected = activePaletteKey.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val secKey = selectedSection?.key ?: return@ChoiceChip
                            HouseCareThemeStore.update(context) { cur ->
                                val prev = cur.sectionOverrides[secKey] ?: SectionCustomStyle()
                                cur.copy(sectionOverrides = cur.sectionOverrides + (secKey to prev.copy(paletteKey = "")))
                            }
                        },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    houseCarePalettes.forEach { pal ->
                        val isSelected = activePaletteKey == pal.key
                        val primaryColor = pal.primary(dark)
                        val containerColor = pal.primaryContainer(dark)
                        val shape = RoundedCornerShape(16.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(
                                    if (isSelected) containerColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    shape,
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) primaryColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = shape,
                                )
                                .clickable {
                                    HouseCareThemeStore.update(context) { cur ->
                                        if (selectedSection == null) {
                                            if (pal.key == "standard") {
                                                cur.copy(
                                                    globalPaletteKey = "standard",
                                                    globalCardStyleKey = HouseCareCardStyle.Standard.key,
                                                    globalCornerDp = 22,
                                                )
                                            } else {
                                                cur.copy(globalPaletteKey = pal.key)
                                            }
                                        } else {
                                            val secKey = selectedSection!!.key
                                            val prev = cur.sectionOverrides[secKey] ?: SectionCustomStyle()
                                            cur.copy(
                                                sectionOverrides = cur.sectionOverrides +
                                                    (secKey to prev.copy(paletteKey = pal.key)),
                                            )
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(primaryColor, CircleShape)
                                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(pal.emoji, fontSize = 15.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    pal.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    pal.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    "Стиль карточек",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (selectedSection != null) {
                        ChoiceChip(
                            label = "Как во всём разделе",
                            selected = activeCardStyleKey.isBlank(),
                            onClick = {
                                val secKey = selectedSection?.key ?: return@ChoiceChip
                                HouseCareThemeStore.update(context) { cur ->
                                    val prev = cur.sectionOverrides[secKey] ?: SectionCustomStyle()
                                    cur.copy(
                                        sectionOverrides = cur.sectionOverrides +
                                            (secKey to prev.copy(cardStyleKey = "")),
                                    )
                                }
                            },
                        )
                    }
                    HouseCareCardStyle.entries.forEach { st ->
                        ChoiceChip(
                            label = st.title,
                            selected = activeCardStyleKey == st.key,
                            onClick = {
                                HouseCareThemeStore.update(context) { cur ->
                                    if (selectedSection == null) {
                                        cur.copy(globalCardStyleKey = st.key)
                                    } else {
                                        val secKey = selectedSection!!.key
                                        val prev = cur.sectionOverrides[secKey] ?: SectionCustomStyle()
                                        cur.copy(
                                            sectionOverrides = cur.sectionOverrides +
                                                (secKey to prev.copy(cardStyleKey = st.key)),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }

                Text(
                    "Мягкость углов карточек",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(22 to "Стандартные", 18 to "Аккуратные", 24 to "Нежные", 30 to "Воздушные").forEach { (dpVal, label) ->
                        ChoiceChip(
                            label = label,
                            selected = activeCornerDp == dpVal,
                            onClick = {
                                HouseCareThemeStore.update(context) { cur ->
                                    if (selectedSection == null) {
                                        cur.copy(globalCornerDp = dpVal)
                                    } else {
                                        val secKey = selectedSection!!.key
                                        val prev = cur.sectionOverrides[secKey] ?: SectionCustomStyle()
                                        cur.copy(
                                            sectionOverrides = cur.sectionOverrides +
                                                (secKey to prev.copy(cornerRadiusDp = dpVal)),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово")
            }
        },
    )
}

/**
 * Блок выбора индивидуального цвета и нежного значка для конкретной комнаты или раздела уборки.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ItemFeminineStyleSelector(
    accentKey: String,
    iconEmoji: String,
    onAccentChange: (String) -> Unit,
    onEmojiChange: (String) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Иконка и цвет раздела",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChoiceChip(
                label = "Авто",
                selected = iconEmoji.isBlank(),
                onClick = { onEmojiChange("") },
            )
            houseCareDecorativeEmojis.forEach { emoji ->
                val selected = iconEmoji == emoji
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            CircleShape,
                        )
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            },
                            shape = CircleShape,
                        )
                        .clickable { onEmojiChange(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 16.sp)
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChoiceChip(
                label = "Цвет темы",
                selected = accentKey.isBlank(),
                onClick = { onAccentChange("") },
            )
            houseCarePalettes.forEach { pal ->
                val selected = accentKey == pal.key
                val color = pal.primary(dark)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp),
                        )
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { onAccentChange(pal.key) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(color, CircleShape),
                    )
                    Text(
                        pal.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ============================================================================
// Звуковой движок, фоновый менеджер и большой таймер «15-минутка»
// ============================================================================

/**
 * Синтезирует мягкое тиканье часов, нежный колокольчик каждой минуты
 * и проговаривает голосом отсчёт последних секунд: «5, 4, 3, 2, 1, Время вышло, пора завершать уборку».
 */
internal class FifteenMinuteSoundEngine(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var toneGenerator: ToneGenerator? = null

    init {
        runCatching {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        }
        runCatching {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    val result = tts?.setLanguage(Locale.forLanguageTag("ru-RU"))
                    ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                    tts?.setPitch(1.06f)
                    tts?.setSpeechRate(1.02f)
                }
            }
        }
    }

    /** Отчётливый часовой «тик-так» каждую секунду. */
    fun playTick(isEvenSecond: Boolean = true) {
        scope.launch {
            val baseFreq = if (isEvenSecond) 920.0 else 780.0
            val played = playClockClick(baseFreq = baseFreq, volume = 0.88f)
            if (!played) {
                runCatching {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 28)
                }
            }
        }
    }

    /** Нежный двухнотный перезвон прошедшей минуты + мягкое голосовое уведомление. */
    fun playMinutePassed(minutesElapsed: Int, speakVoice: Boolean) {
        scope.launch {
            playSoftTone(freqHz = 659.25, durationMs = 160, decayRate = 13.0, volume = 0.65f) // E5
            delay(130)
            playSoftTone(freqHz = 880.0, durationMs = 240, decayRate = 9.0, volume = 0.70f) // A5
        }
        if (speakVoice && ttsReady && minutesElapsed > 0) {
            val word = when {
                minutesElapsed % 10 == 1 && minutesElapsed % 100 != 11 -> "минута"
                minutesElapsed % 10 in 2..4 && minutesElapsed % 100 !in 12..14 -> "минуты"
                else -> "минут"
            }
            val verb = if (minutesElapsed == 1) "Прошла" else "Прошло"
            speak("$verb $minutesElapsed $word")
        }
    }

    /** Отсчёт последних секунд: 5, 4, 3, 2, 1. */
    fun playFinalSecond(secondLeft: Int, speakVoice: Boolean) {
        scope.launch {
            val freq = 740.0 + (5 - secondLeft.coerceIn(1, 5)) * 60.0
            playSoftTone(freqHz = freq, durationMs = 140, decayRate = 16.0, volume = 0.68f)
        }
        if (speakVoice) {
            val word = when (secondLeft) {
                5 -> "Пять"
                4 -> "Четыре"
                3 -> "Три"
                2 -> "Два"
                1 -> "Один"
                else -> secondLeft.toString()
            }
            speak(word)
        }
    }

    /** Финал: «Время вышло, пора завершать уборку!» + мелодичный аккорд. */
    fun playFinished(speakVoice: Boolean) {
        scope.launch {
            playSoftTone(freqHz = 523.25, durationMs = 160, decayRate = 12.0, volume = 0.68f) // C5
            delay(140)
            playSoftTone(freqHz = 659.25, durationMs = 160, decayRate = 12.0, volume = 0.68f) // E5
            delay(140)
            playSoftTone(freqHz = 783.99, durationMs = 180, decayRate = 10.0, volume = 0.70f) // G5
            delay(150)
            playSoftTone(freqHz = 1046.50, durationMs = 420, decayRate = 6.0, volume = 0.78f) // C6
        }
        if (speakVoice) {
            speak("Время вышло, пора завершать уборку!")
        }
    }

    fun speak(phrase: String) {
        if (!ttsReady) return
        runCatching {
            tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "hc_timer_${System.currentTimeMillis()}")
        }
    }

    fun stopSpeaking() {
        runCatching {
            tts?.stop()
        }
    }

    /**
     * Генерирует чёткий механический часовой щелчок («тик» / «так») с буфером не меньше minBufferSize,
     * чтобы AudioTrack гарантированно воспроизводил звук каждую секунду на всех устройствах Android.
     */
    private fun playClockClick(baseFreq: Double, volume: Float): Boolean {
        return runCatching {
            val sampleRate = 24000
            val minBufBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(4800)
            val activeSamples = (sampleRate * 48 / 1000) // 48 мс активного щелчка
            val totalSamples = maxOf(activeSamples, (minBufBytes / 2) + 256)
            val samples = ShortArray(totalSamples)
            for (i in 0 until activeSamples) {
                val t = i.toDouble() / sampleRate
                // Короткий ударный фронт (первые 4 мс) + деревянный/часовой резонанс
                val clickTransient = if (t < 0.004) (1.0 - t / 0.004) * sin(2.0 * PI * 2600.0 * t) else 0.0
                val attack = (i.toDouble() / (sampleRate * 0.0015)).coerceAtMost(1.0)
                val bodyEnv = attack * exp(-95.0 * t)
                val bodyWave = sin(2.0 * PI * baseFreq * t) +
                    0.55 * sin(2.0 * PI * (baseFreq * 1.95) * t) +
                    0.25 * sin(2.0 * PI * (baseFreq * 3.1) * t)
                val combined = (clickTransient * 0.65 + bodyWave * bodyEnv * 0.85) * volume
                val pcm = (combined * Short.MAX_VALUE).toInt()
                samples[i] = pcm.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcmBuffer(sampleRate, samples, sleepMs = 65L)
        }.getOrDefault(false)
    }

    private fun playSoftTone(freqHz: Double, durationMs: Int, decayRate: Double, volume: Float) {
        runCatching {
            val sampleRate = 24000
            val minBufBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(4800)
            val activeSamples = (sampleRate * durationMs / 1000).coerceAtLeast(1)
            val totalSamples = maxOf(activeSamples, (minBufBytes / 2) + 256)
            val samples = ShortArray(totalSamples)
            for (i in 0 until activeSamples) {
                val t = i.toDouble() / sampleRate
                val attack = (i.toDouble() / (sampleRate * 0.004)).coerceAtMost(1.0)
                val envelope = attack * exp(-decayRate * t)
                val wave = sin(2.0 * PI * freqHz * t) + 0.28 * sin(4.0 * PI * freqHz * t)
                val value = (wave * envelope * volume * Short.MAX_VALUE * 0.78).toInt()
                samples[i] = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcmBuffer(sampleRate, samples, sleepMs = durationMs.toLong() + 45L)
        }
    }

    private fun playPcmBuffer(sampleRate: Int, samples: ShortArray, sleepMs: Long): Boolean {
        val bufferBytes = samples.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return false
        }
        val written = track.write(samples, 0, samples.size)
        if (written <= 0) {
            track.release()
            return false
        }
        track.play()
        Thread.sleep(sleepMs)
        runCatching { track.stop() }
        track.release()
        return true
    }
}

/** Состояние фонового таймера «15-минутка», живущее на уровне всего приложения. */
internal data class FifteenMinuteTimerState(
    val zoneTitle: String = "15-минутка",
    val presetMinutes: Int = 15,
    val totalSeconds: Int = 15 * 60,
    val remainingSeconds: Int = 15 * 60,
    val overtimeSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isFinishedBanner: Boolean = false,
)

/**
 * Фоновый менеджер таймера «15-минутка»:
 * не привязан к жизненному циклу конкретного экрана, поэтому продолжает тикать,
 * подавать минутные сигналы и голосовой отсчёт при переходах по экранам,
 * съёмке фото «До/После» и в фоновом режиме, пока пользователь сам не нажмёт «Пауза» или «Сброс».
 */
internal object FifteenMinuteTimerManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(FifteenMinuteTimerState())
    val state: StateFlow<FifteenMinuteTimerState> = _state

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var soundEngine: FifteenMinuteSoundEngine? = null

    private var timerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun ensureInitialized(context: Context): FifteenMinuteSoundEngine {
        val app = context.applicationContext
        appContext = app
        HouseCareThemeStore.observe(app)
        var engine = soundEngine
        if (engine == null) {
            synchronized(this) {
                engine = soundEngine
                if (engine == null) {
                    engine = FifteenMinuteSoundEngine(app)
                    soundEngine = engine
                }
            }
        }
        return engine!!
    }

    fun setZoneTitle(title: String) {
        if (title.isNotBlank() && _state.value.zoneTitle != title) {
            _state.value = _state.value.copy(zoneTitle = title)
        }
    }

    fun toggleStartPause(context: Context) {
        ensureInitialized(context)
        if (_state.value.isRunning) {
            pause()
        } else {
            start(context)
        }
    }

    fun start(context: Context) {
        val engine = ensureInitialized(context)
        val cur = _state.value
        if (cur.isRunning) return

        val startRemaining = if (cur.remainingSeconds <= 0 && cur.isFinishedBanner) {
            cur.totalSeconds
        } else {
            cur.remainingSeconds
        }
        _state.value = cur.copy(
            remainingSeconds = startRemaining,
            overtimeSeconds = if (startRemaining == cur.totalSeconds) 0 else cur.overtimeSeconds,
            isRunning = true,
            isFinishedBanner = false,
        )

        acquireWakeLock(context)
        val settings = HouseCareThemeStore.observe(context).value
        if (settings.timerTickSound) {
            engine.playTick(isEvenSecond = startRemaining % 2 == 0)
        }

        timerJob?.cancel()
        timerJob = scope.launch {
            var nextTickAt = SystemClock.elapsedRealtime() + 1000L
            while (_state.value.isRunning) {
                val now = SystemClock.elapsedRealtime()
                val waitMs = (nextTickAt - now).coerceAtLeast(10L)
                delay(waitMs)
                if (!_state.value.isRunning) break
                nextTickAt += 1000L
                val drift = SystemClock.elapsedRealtime() - nextTickAt
                if (drift > 1500L) {
                    nextTickAt = SystemClock.elapsedRealtime() + 1000L
                }

                val currentSettings = appContext?.let { HouseCareThemeStore.observe(it).value }
                    ?: HouseCareThemeSettings()
                val snapshot = _state.value

                if (snapshot.remainingSeconds > 0) {
                    val next = (snapshot.remainingSeconds - 1).coerceAtLeast(0)
                    _state.value = snapshot.copy(
                        remainingSeconds = next,
                        isFinishedBanner = if (next == 0) true else snapshot.isFinishedBanner,
                    )

                    if (next == 0) {
                        // Таймер дошёл до 00:00, но НЕ останавливается сам, пока пользователь не остановит!
                        engine.playFinished(speakVoice = currentSettings.timerVoiceCountdown)
                    } else {
                        if (currentSettings.timerTickSound) {
                            engine.playTick(isEvenSecond = next % 2 == 0)
                        }
                        if (next in 1..5 && currentSettings.timerVoiceCountdown) {
                            engine.playFinalSecond(next, speakVoice = true)
                        } else if (next % 60 == 0 && next < snapshot.totalSeconds && currentSettings.timerMinuteSound) {
                            val elapsedMinutes = (snapshot.totalSeconds - next) / 60
                            engine.playMinutePassed(
                                minutesElapsed = elapsedMinutes,
                                speakVoice = currentSettings.timerVoiceCountdown,
                            )
                        }
                    }
                } else {
                    // Время вышло, но пользователь ещё не остановил таймер — продолжаем работать в фоне
                    val over = snapshot.overtimeSeconds + 1
                    _state.value = snapshot.copy(
                        overtimeSeconds = over,
                        isFinishedBanner = true,
                    )
                    if (currentSettings.timerTickSound) {
                        engine.playTick(isEvenSecond = over % 2 == 0)
                    }
                    if (over % 15 == 0) {
                        engine.playFinished(speakVoice = currentSettings.timerVoiceCountdown)
                    }
                }
            }
        }
    }

    fun pause() {
        timerJob?.cancel()
        timerJob = null
        releaseWakeLock()
        soundEngine?.stopSpeaking()
        _state.value = _state.value.copy(isRunning = false)
    }

    fun reset(context: Context) {
        ensureInitialized(context)
        timerJob?.cancel()
        timerJob = null
        releaseWakeLock()
        soundEngine?.stopSpeaking()
        val cur = _state.value
        _state.value = cur.copy(
            remainingSeconds = cur.totalSeconds,
            overtimeSeconds = 0,
            isRunning = false,
            isFinishedBanner = false,
        )
    }

    fun selectPreset(context: Context, minutes: Int) {
        ensureInitialized(context)
        timerJob?.cancel()
        timerJob = null
        releaseWakeLock()
        soundEngine?.stopSpeaking()
        _state.value = _state.value.copy(
            presetMinutes = minutes,
            totalSeconds = minutes * 60,
            remainingSeconds = minutes * 60,
            overtimeSeconds = 0,
            isRunning = false,
            isFinishedBanner = false,
        )
    }

    private fun acquireWakeLock(context: Context) {
        runCatching {
            if (wakeLock?.isHeld == true) return
            val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BibleApp:FifteenMinuteTimerWakeLock").apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L) // до 1 часа непрерывной работы
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }
}

/**
 * Компактная плашка активного таймера «15-минутка»:
 * отображается в верхней части других экранов «Попечительной о доме», когда таймер запущен,
 * чтобы пользователь видел обратный отсчёт и мог поставить на паузу или остановить его с любого экрана.
 */
@Composable
internal fun ActiveFifteenMinuteTimerBanner(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val timerState by FifteenMinuteTimerManager.state.collectAsState()
    if (!timerState.isRunning && !timerState.isFinishedBanner) return

    val mins = timerState.remainingSeconds / 60
    val secs = timerState.remainingSeconds % 60
    val timeText = if (timerState.remainingSeconds > 0) {
        String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    } else {
        val overM = timerState.overtimeSeconds / 60
        val overS = timerState.overtimeSeconds % 60
        String.format(Locale.getDefault(), "00:00 (+%02d:%02d)", overM, overS)
    }

    val isDone = timerState.isFinishedBanner
    val accent = if (isDone) cleaningGreen else MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.14f),
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (isDone) "✨" else "⏱", fontSize = 18.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isDone) "Время 15-минутки вышло!" else "Идёт ${timerState.zoneTitle} · $timeText",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (isDone) {
                        "Нажмите «Стоп», чтобы завершить звуковой сигнал"
                    } else {
                        "Таймер работает в фоне · можно открывать разделы и делать фото"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (timerState.isRunning) {
                TextButton(
                    onClick = { FifteenMinuteTimerManager.pause() },
                ) {
                    Text(
                        if (isDone) "Стоп" else "Пауза",
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                }
            }
            IconButton(
                onClick = { FifteenMinuteTimerManager.reset(context) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Сбросить таймер",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Большой и красивый таймер «15-минутка» с удобным включением, паузой, сбросом,
 * непрерывной работой в фоновом режиме, звуками тиканья каждую секунду, минутными сигналами
 * и голосовым отсчётом 5, 4, 3, 2, 1, «Время вышло, пора завершать уборку!».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FifteenMinuteTimerCard(
    zoneTitle: String = "15-минутка",
    tasksDone: Int = 0,
    tasksTotal: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings by HouseCareThemeStore.observe(context).collectAsState()
    val soundEngine = remember(context.applicationContext) {
        FifteenMinuteTimerManager.ensureInitialized(context)
    }
    val timerState by FifteenMinuteTimerManager.state.collectAsState()

    LaunchedEffect(zoneTitle) {
        FifteenMinuteTimerManager.setZoneTitle(zoneTitle)
    }

    val presetMinutes = timerState.presetMinutes
    val totalSeconds = timerState.totalSeconds
    val remainingSeconds = timerState.remainingSeconds
    val isRunning = timerState.isRunning
    val isFinishedBanner = timerState.isFinishedBanner
    var showFullScreen by rememberSaveable { mutableStateOf(false) }

    val style = LocalHouseCareStyle.current
    val dark = isSystemInDarkTheme()
    val isStandard = style.palette.key == "standard"
    val primary = if (isStandard) MaterialTheme.colorScheme.primary else style.palette.primary(dark)
    val container = if (isStandard) MaterialTheme.colorScheme.primaryContainer else style.palette.primaryContainer(dark)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(style.cornerDp.dp),
        colors = CardDefaults.cardColors(
            containerColor = container.copy(alpha = if (dark) 0.42f else 0.55f),
        ),
        border = BorderStroke(1.5.dp, primary.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BadgeCircle(
                        size = 36.dp,
                        container = primary.copy(alpha = 0.16f),
                    ) {
                        Text("⏱", fontSize = 17.sp)
                    }
                    Column {
                        Text(
                            "Таймер «15-минутка»",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                        )
                        Text(
                            if (tasksTotal > 0) {
                                "Частей комнат: $tasksDone из $tasksTotal выполнено"
                            } else {
                                "Работает даже в фоне, пока вы не остановите ✨"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = { showFullScreen = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = "На весь экран",
                        tint = primary,
                    )
                }
            }

            // Большой круговой циферблат
            CircularFeminineDial(
                remainingSeconds = remainingSeconds,
                totalSeconds = totalSeconds,
                isRunning = isRunning,
                isFinished = isFinishedBanner,
                primary = primary,
                dialSize = 210.dp,
            )

            if (isFinishedBanner) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cleaningGreen.copy(alpha = 0.15f))
                        .border(1.dp, cleaningGreen.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isRunning) {
                            "✨ Время вышло! Нажмите «Остановить» или «Сброс» ✨"
                        } else {
                            "✨ Время вышло! Пора завершать уборку ✨"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = cleaningGreen,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Кнопки управления: Старт/Пауза и Сброс
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { FifteenMinuteTimerManager.toggleStartPause(context) },
                    modifier = Modifier
                        .weight(1.35f)
                        .height(48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.secondary else primary,
                    ),
                ) {
                    Icon(
                        if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            isRunning && isFinishedBanner -> "Остановить"
                            isRunning -> "Пауза"
                            remainingSeconds < totalSeconds && remainingSeconds > 0 -> "Продолжить"
                            else -> "Старт 15-минутки"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                OutlinedButton(
                    onClick = { FifteenMinuteTimerManager.reset(context) },
                    modifier = Modifier
                        .weight(0.85f)
                        .height(48.dp),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.2.dp, primary.copy(alpha = 0.5f)),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Сброс",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                    )
                }
            }

            // Быстрый выбор времени (по умолчанию 15 мин)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(5, 10, 15, 20).forEach { mins ->
                    ChoiceChip(
                        label = "$mins мин",
                        selected = presetMinutes == mins,
                        modifier = Modifier.weight(1f),
                        onClick = { FifteenMinuteTimerManager.selectPreset(context, mins) },
                    )
                }
            }

            HorizontalDivider(color = primary.copy(alpha = 0.2f))

            // Настройки звука: тиканье, минутный сигнал, голосовой отсчёт 5..1
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Звуковое сопровождение",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                    )
                    Text(
                        "🔊 Проверить отсчёт",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                soundEngine.playFinished(speakVoice = true)
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SoundToggleChip(
                        label = "🔔 Тиканье",
                        enabled = settings.timerTickSound,
                        primary = primary,
                        onClick = {
                            val next = !settings.timerTickSound
                            HouseCareThemeStore.update(context) { it.copy(timerTickSound = next) }
                            if (next) soundEngine.playTick()
                        },
                    )
                    SoundToggleChip(
                        label = "🎶 Каждая минута",
                        enabled = settings.timerMinuteSound,
                        primary = primary,
                        onClick = {
                            val next = !settings.timerMinuteSound
                            HouseCareThemeStore.update(context) { it.copy(timerMinuteSound = next) }
                            if (next) soundEngine.playMinutePassed(1, speakVoice = false)
                        },
                    )
                    SoundToggleChip(
                        label = "🗣 Отсчёт 5..1 и финал",
                        enabled = settings.timerVoiceCountdown,
                        primary = primary,
                        onClick = {
                            val next = !settings.timerVoiceCountdown
                            HouseCareThemeStore.update(context) { it.copy(timerVoiceCountdown = next) }
                            if (next) soundEngine.speak("Пять, четыре, три, два, один. Время вышло, пора завершать уборку!")
                        },
                    )
                }
            }
        }
    }

    if (showFullScreen) {
        Dialog(
            onDismissRequest = { showFullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                zoneTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = primary,
                            )
                            Text(
                                "Режим «15-минутка» · работает даже в фоновом режиме 🌸",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { showFullScreen = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть")
                        }
                    }

                    CircularFeminineDial(
                        remainingSeconds = remainingSeconds,
                        totalSeconds = totalSeconds,
                        isRunning = isRunning,
                        isFinished = isFinishedBanner,
                        primary = primary,
                        dialSize = 280.dp,
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (isFinishedBanner) {
                            Text(
                                "✨ Время вышло! Пора завершать уборку ✨",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = cleaningGreen,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = { FifteenMinuteTimerManager.toggleStartPause(context) },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(54.dp),
                                shape = RoundedCornerShape(50),
                            ) {
                                Icon(
                                    if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when {
                                        isRunning && isFinishedBanner -> "Остановить"
                                        isRunning -> "Пауза"
                                        else -> "Старт"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            OutlinedButton(
                                onClick = { FifteenMinuteTimerManager.reset(context) },
                                modifier = Modifier
                                    .weight(0.8f)
                                    .height(54.dp),
                                shape = RoundedCornerShape(50),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Сброс", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularFeminineDial(
    remainingSeconds: Int,
    totalSeconds: Int,
    isRunning: Boolean,
    isFinished: Boolean,
    primary: Color,
    dialSize: androidx.compose.ui.unit.Dp,
) {
    val progress by animateFloatAsState(
        targetValue = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds.toFloat() else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "timer_dial_progress",
    )
    val isLastFive = remainingSeconds in 1..5 && isRunning
    val dialColor by animateColorAsState(
        targetValue = when {
            isFinished -> cleaningGreen
            isLastFive -> Color(0xFFD8436B)
            else -> primary
        },
        label = "timer_dial_color",
    )

    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    val formatted = String.format(Locale.getDefault(), "%02d:%02d", mins, secs)

    Box(
        modifier = Modifier.size(dialSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val outerPadding = 10.dp.toPx()
            val radius = (size.minDimension / 2f) - strokeWidth - outerPadding
            val center = Offset(size.width / 2f, size.height / 2f)

            // Нежное внешнее кольцо из 60 часовых засечек
            for (tick in 0 until 60) {
                val angleRad = ((tick * 6f - 90f) * PI / 180.0)
                val isMajor = tick % 5 == 0
                val innerR = radius + strokeWidth * 0.85f
                val outerR = innerR + (if (isMajor) 6.dp.toPx() else 3.dp.toPx())
                val start = Offset(
                    x = center.x + (innerR * cos(angleRad)).toFloat(),
                    y = center.y + (innerR * sin(angleRad)).toFloat(),
                )
                val end = Offset(
                    x = center.x + (outerR * cos(angleRad)).toFloat(),
                    y = center.y + (outerR * sin(angleRad)).toFloat(),
                )
                drawLine(
                    color = dialColor.copy(alpha = if (isMajor) 0.45f else 0.2f),
                    start = start,
                    end = end,
                    strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Фоновая дорожка
            drawCircle(
                color = dialColor.copy(alpha = 0.14f),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth),
            )

            // Активная дуга
            val arcSize = Size(radius * 2f, radius * 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            drawArc(
                color = dialColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                when {
                    isFinished -> "Готово! 🌸"
                    isLastFive -> "Финиш: $remainingSeconds..."
                    isRunning -> "Идёт уборка ✨"
                    remainingSeconds == totalSeconds -> "Нажмите «Старт»"
                    else -> "На паузе"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = dialColor,
            )
            Text(
                formatted,
                fontSize = if (dialSize > 240.dp) 52.sp else 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val elapsedMin = ((totalSeconds - remainingSeconds).coerceAtLeast(0)) / 60
            Text(
                if (elapsedMin > 0) "Прошло $elapsedMin мин из ${totalSeconds / 60}" else "Цель: ${totalSeconds / 60} минут",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SoundToggleChip(
    label: String,
    enabled: Boolean,
    primary: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (enabled) primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape,
            )
            .border(
                width = if (enabled) 1.5.dp else 1.dp,
                color = if (enabled) primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
            color = if (enabled) primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
