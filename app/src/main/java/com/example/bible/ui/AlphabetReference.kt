package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.R
import com.example.bible.data.InterlinearTts

private data class AlphabetEntry(
    val letter: String,
    val name: String,
    val translit: String,
    val sound: String,
    /** Пусто — только колонка «Лат.» (иврит/греческий). Заполнено — колонки «Транскр.» и «Кирилл.» (арабский). */
    val cyrillicTranslit: String = "",
    /** Название буквы по-арабски для TTS; пусто — только русская озвучка описания. */
    val arabicTtsName: String = "",
)

private data class NumberEntry(
    val symbol: String,
    val value: Int,
    /** Кратко на экране */
    val hintRu: String,
    /** Полная строка для озвучки по-русски */
    val speakRu: String,
    /** Фраза на арабском для TTS (абджад); пусто — только [speakRu]. */
    val speakAr: String = "",
)

private val HEBREW_ALPHABET = listOf(
    AlphabetEntry("א", "Алеф", "ʾ", "пауза / гортанная смычка"),
    AlphabetEntry("בּ / ב", "Бет / Вет", "b / v", "б / в"),
    AlphabetEntry("גּ / ג", "Гимель", "g / gh", "г"),
    AlphabetEntry("דּ / ד", "Далет", "d / dh", "д"),
    AlphabetEntry("ה", "Хе", "h", "х (легкий)"),
    AlphabetEntry("ו", "Вав", "v / w", "в / у"),
    AlphabetEntry("ז", "Зайин", "z", "з"),
    AlphabetEntry("ח", "Хет", "ḥ", "х гортанный"),
    AlphabetEntry("ט", "Тет", "ṭ", "т эмфатический"),
    AlphabetEntry("י", "Йод", "y", "й / краткий и"),
    AlphabetEntry("כּ / כ / ך", "Каф / Хаф", "k / kh", "к / х"),
    AlphabetEntry("ל", "Ламед", "l", "л"),
    AlphabetEntry("מ / ם", "Мем", "m", "м"),
    AlphabetEntry("נ / ן", "Нун", "n", "н"),
    AlphabetEntry("ס", "Самех", "s", "с"),
    AlphabetEntry("ע", "Аин", "ʿ", "гортанный звук"),
    AlphabetEntry("פּ / פ / ף", "Пе / Фе", "p / f", "п / ф"),
    AlphabetEntry("צ / ץ", "Цади", "ṣ", "ц"),
    AlphabetEntry("ק", "Коф", "q", "к"),
    AlphabetEntry("ר", "Реш", "r", "р"),
    AlphabetEntry("שׁ / שׂ", "Шин / Син", "sh / s", "ш / с"),
    AlphabetEntry("תּ / ת", "Тав", "t / th", "т"),
)

private val GREEK_ALPHABET = listOf(
    AlphabetEntry("Α α", "Альфа", "a", "а"),
    AlphabetEntry("Β β", "Бета", "b", "б"),
    AlphabetEntry("Γ γ", "Гамма", "g", "г / нг перед гласной"),
    AlphabetEntry("Δ δ", "Дельта", "d", "д"),
    AlphabetEntry("Ε ε", "Эпсилон", "e", "э краткое"),
    AlphabetEntry("Ζ ζ", "Дзета", "z", "дз"),
    AlphabetEntry("Η η", "Эта", "ē", "э или и долгое"),
    AlphabetEntry("Θ θ", "Тета", "th", "т (англ. th)"),
    AlphabetEntry("Ι ι", "Йота", "i", "и"),
    AlphabetEntry("Κ κ", "Каппа", "k", "к"),
    AlphabetEntry("Λ λ", "Лямбда", "l", "л"),
    AlphabetEntry("Μ μ", "Мю", "m", "м"),
    AlphabetEntry("Ν ν", "Ню", "n", "н"),
    AlphabetEntry("Ξ ξ", "Кси", "x", "кс"),
    AlphabetEntry("Ο ο", "Омикрон", "o", "о краткое"),
    AlphabetEntry("Π π", "Пи", "p", "п"),
    AlphabetEntry("Ρ ρ", "Ро", "r", "р"),
    AlphabetEntry("Σ σ/ς", "Сигма", "s", "с"),
    AlphabetEntry("Τ τ", "Тау", "t", "т"),
    AlphabetEntry("Υ υ", "Ипсилон", "y / u", "и / у"),
    AlphabetEntry("Φ φ", "Фи", "ph", "ф"),
    AlphabetEntry("Χ χ", "Хи", "ch", "х"),
    AlphabetEntry("Ψ ψ", "Пси", "ps", "пс"),
    AlphabetEntry("Ω ω", "Омега", "ō", "о долгое"),
)

/** Арабский алфавит (изолированные буквы): латинская транскрипция (близко к Tanzil) и кириллическая подсказка для чтения. */
private val ARABIC_ALPHABET = listOf(
    AlphabetEntry("ا", "Алиф", "ā / ʼ", "долгое а; опора для гласных", "а / ’", "ألف"),
    AlphabetEntry("ب", "Ба", "b", "как рус. б", "б", "باء"),
    AlphabetEntry("ت", "Та", "t", "как рус. т", "т", "تاء"),
    AlphabetEntry("ث", "Са", "th", "межзубное, как англ. th", "с", "ثاء"),
    AlphabetEntry("ج", "Джим", "j", "как дж", "дж", "جيم"),
    AlphabetEntry("ح", "Ха", "ḥ", "глубокое «х», ниже горла", "х", "حاء"),
    AlphabetEntry("خ", "Ха", "kh", "х с придыханием", "х", "خاء"),
    AlphabetEntry("د", "Даль", "d", "как рус. д", "д", "دال"),
    AlphabetEntry("ذ", "Заль", "dh", "мягкое, ближе к з", "з", "ذال"),
    AlphabetEntry("ر", "Ра", "r", "лёгкое р", "р", "راء"),
    AlphabetEntry("ز", "Зай", "z", "как рус. з", "з", "زاي"),
    AlphabetEntry("س", "Син", "s", "как рус. с", "с", "سين"),
    AlphabetEntry("ش", "Шин", "sh", "как ш", "ш", "شين"),
    AlphabetEntry("ص", "Сад", "ṣ", "с эмфатическое", "с", "صاد"),
    AlphabetEntry("ض", "Дад", "ḍ", "д эмфатическое", "д", "ضاد"),
    AlphabetEntry("ط", "Та", "ṭ", "т эмфатическое", "т", "طاء"),
    AlphabetEntry("ظ", "За", "ẓ", "з эмфатическое", "з", "ظاء"),
    AlphabetEntry("ع", "Айн", "ʿ", "гортанная смычка", "’", "عين"),
    AlphabetEntry("غ", "Гайн", "gh", "как франц. r / г", "г", "غين"),
    AlphabetEntry("ف", "Фа", "f", "как ф", "ф", "فاء"),
    AlphabetEntry("ق", "Каф", "q", "к глубже, чем к", "к", "قاف"),
    AlphabetEntry("ك", "Каф", "k", "как рус. к", "к", "كاف"),
    AlphabetEntry("ل", "Лам", "l", "как л", "л", "لام"),
    AlphabetEntry("م", "Мим", "m", "как м", "м", "ميم"),
    AlphabetEntry("ن", "Нун", "n", "как н", "н", "نون"),
    AlphabetEntry("ه", "Ха", "h", "лёгкое х / придыхание", "х", "هاء"),
    AlphabetEntry("و", "Вав", "w / ū", "в или долгое у", "в / у", "واو"),
    AlphabetEntry("ي", "Йа", "y / ī", "й или долгое и", "й / и", "ياء"),
    AlphabetEntry("ء", "Хамза", "ʼ", "глоттальная пауза", "’", "همزة"),
)

/** Иврит: буквы א–י как цифры 1–10 (стандартная запись). */
private val HEBREW_NUMBERS_1_10 = listOf(
    NumberEntry("א", 1, "1 — алеф", "Иврит, число один, буква алеф."),
    NumberEntry("ב", 2, "2 — бет", "Иврит, число два, буква бет."),
    NumberEntry("ג", 3, "3 — гимель", "Иврит, число три, буква гимель."),
    NumberEntry("ד", 4, "4 — далет", "Иврит, число четыре, буква далет."),
    NumberEntry("ה", 5, "5 — хе", "Иврит, число пять, буква хе."),
    NumberEntry("ו", 6, "6 — вав", "Иврит, число шесть, буква вав."),
    NumberEntry("ז", 7, "7 — заин", "Иврит, число семь, буква заин."),
    NumberEntry("ח", 8, "8 — хет", "Иврит, число восемь, буква хет."),
    NumberEntry("ט", 9, "9 — тет", "Иврит, число девять, буква тет."),
    NumberEntry("י", 10, "10 — йод", "Иврит, число десять, буква йод."),
)

/** Греческая майлесийская нумерация: 1–10. */
private val GREEK_NUMBERS_1_10 = listOf(
    NumberEntry("α", 1, "1 — альфа", "Греческий, число один, буква альфа."),
    NumberEntry("β", 2, "2 — бета", "Греческий, число два, буква бета."),
    NumberEntry("γ", 3, "3 — гамма", "Греческий, число три, буква гамма."),
    NumberEntry("δ", 4, "4 — дельта", "Греческий, число четыре, буква дельта."),
    NumberEntry("ε", 5, "5 — эпсилон", "Греческий, число пять, буква эпсилон."),
    NumberEntry("ϛ", 6, "6 — стигма", "Греческий, число шесть, буква стигма."),
    NumberEntry("ζ", 7, "7 — дзета", "Греческий, число семь, буква дзета."),
    NumberEntry("η", 8, "8 — эта", "Греческий, число восемь, буква эта."),
    NumberEntry("θ", 9, "9 — тета", "Греческий, число девять, буква тета."),
    NumberEntry("ι", 10, "10 — йота", "Греческий, число десять, буква йота."),
)

/** Арабский абджад: буквы ا–ي как числа 1–10 (классическая буквенная запись, как у иврита). */
private val ARABIC_NUMBERS_1_10 = listOf(
    NumberEntry("ا", 1, "1 — алиф", "Арабский абджад, число один, буква алиф.", "ألف"),
    NumberEntry("ب", 2, "2 — ба", "Арабский абджад, число два, буква ба.", "باء"),
    NumberEntry("ج", 3, "3 — джим", "Арабский абджад, число три, буква джим.", "جيم"),
    NumberEntry("د", 4, "4 — даль", "Арабский абджад, число четыре, буква даль.", "دال"),
    NumberEntry("ه", 5, "5 — ха", "Арабский абджад, число пять, буква ха.", "هاء"),
    NumberEntry("و", 6, "6 — вав", "Арабский абджад, число шесть, буква вав.", "واو"),
    NumberEntry("ز", 7, "7 — зай", "Арабский абджад, число семь, буква зай.", "زاي"),
    NumberEntry("ح", 8, "8 — ха", "Арабский абджад, число восемь, буква ха.", "حاء"),
    NumberEntry("ط", 9, "9 — та", "Арабский абджад, число девять, буква та.", "طاء"),
    NumberEntry("ي", 10, "10 — йа", "Арабский абджад, число десять, буква йа.", "ياء"),
)

private fun AlphabetEntry.speakRussian(): String =
    if (cyrillicTranslit.isEmpty()) {
        "$name. Латинская запись: $translit. Произношение: $sound."
    } else {
        "$name. Транскрипция: $translit. Кириллица: $cyrillicTranslit. Произношение: $sound."
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlphabetReferenceSheet(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val tts = remember { InterlinearTts(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        var tab by remember { mutableIntStateOf(0) }
        val tabs = listOf("עברית Иврит", "Ελληνικά Греческий", "العربية Арабский", "Числа 1–10")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Алфавит и числа",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 8.dp,
                divider = {},
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(title, fontSize = 14.sp) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when (tab) {
                0, 1 -> {
                    val alphabet = if (tab == 0) HEBREW_ALPHABET else GREEK_ALPHABET
                    AlphabetTableHeader(showCyrillicTranslit = false)
                    HorizontalDivider()
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(alphabet) { entry ->
                            AlphabetRow(
                                entry = entry,
                                showCyrillicTranslit = false,
                                onSpeak = { tts.speakRussian(entry.speakRussian()) },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
                2 -> {
                    AlphabetTableHeader(showCyrillicTranslit = true)
                    HorizontalDivider()
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(ARABIC_ALPHABET) { entry ->
                            AlphabetRow(
                                entry = entry,
                                showCyrillicTranslit = true,
                                onSpeak = {
                                    if (entry.arabicTtsName.isNotBlank()) {
                                        tts.speakArabic(entry.arabicTtsName)
                                    } else {
                                        tts.speakRussian(entry.speakRussian())
                                    }
                                },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        item {
                            Text(
                                "Иврит (1–10)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        item {
                            NumberTableHeader()
                            HorizontalDivider()
                        }
                        items(HEBREW_NUMBERS_1_10) { entry ->
                            NumberRow(
                                entry = entry,
                                onSpeak = { tts.speakRussian(entry.speakRu) },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                        }
                        item {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Греческий (1–10)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        item {
                            NumberTableHeader()
                            HorizontalDivider()
                        }
                        items(GREEK_NUMBERS_1_10) { entry ->
                            NumberRow(
                                entry = entry,
                                onSpeak = { tts.speakRussian(entry.speakRu) },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                        }
                        item {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Арабский абджад (1–10)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        item {
                            NumberTableHeader()
                            HorizontalDivider()
                        }
                        items(ARABIC_NUMBERS_1_10) { entry ->
                            NumberRow(
                                entry = entry,
                                onSpeak = {
                                    if (entry.speakAr.isNotBlank()) {
                                        tts.speakArabic(entry.speakAr)
                                    } else {
                                        tts.speakRussian(entry.speakRu)
                                    }
                                },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlphabetTableHeader(showCyrillicTranslit: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Буква",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            "Название",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (showCyrillicTranslit) {
            Text(
                "Транскр.",
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                "Кирилл.",
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                "Лат.",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            "Звук",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.15f),
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.width(40.dp))
    }
}

@Composable
private fun NumberTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Знак",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            "Значение",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            "Кратко",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(40.dp))
    }
}

@Composable
private fun AlphabetRow(
    entry: AlphabetEntry,
    showCyrillicTranslit: Boolean,
    onSpeak: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = entry.letter,
            fontSize = if (showCyrillicTranslit) 22.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = entry.name,
            fontSize = if (showCyrillicTranslit) 13.sp else 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (showCyrillicTranslit) {
            Text(
                text = entry.translit,
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                text = entry.cyrillicTranslit,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.width(52.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = entry.translit,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = entry.sound,
            fontSize = if (showCyrillicTranslit) 11.sp else 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.15f),
            textAlign = TextAlign.Start,
        )
        IconButton(onClick = onSpeak, modifier = Modifier.width(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(R.string.alphabet_speak_row_cd),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun NumberRow(
    entry: NumberEntry,
    onSpeak: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.symbol,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${entry.value}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = entry.hintRu,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSpeak, modifier = Modifier.width(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(R.string.alphabet_speak_row_cd),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
