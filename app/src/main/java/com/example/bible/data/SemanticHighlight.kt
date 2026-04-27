package com.example.bible.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

/**
 * Тематическая подсветка слов по словарям (для анализа текста).
 * Не заменяет морфологический анализ: совпадения по формам слов в тексте.
 */
enum class SemanticHighlightCategory(
    val id: String,
    val labelRu: String,
    /** Чем раньше в списке, тем выше приоритет при перекрытии. */
    val priority: Int,
    val defaultColorArgb: Long,
    val wordsRu: Set<String>,
    val wordsEn: Set<String>,
) {
    GOD(
        id = "god",
        labelRu = "Бог, божественное",
        priority = 0,
        defaultColorArgb = 0xFF1565C0,
        wordsRu = setOf(
            "бог", "бога", "богу", "богом", "боге", "боги", "богов", "богам", "богами", "богах",
            "божий", "божия", "божию", "божие", "божии", "божественн", "всевышн", "вседержител",
        ),
        wordsEn = setOf("god", "gods", "lord", "almighty", "divine", "deity"),
    ),
    CHRIST(
        id = "christ",
        labelRu = "Христос, Иисус",
        priority = 1,
        defaultColorArgb = 0xFF6A1B9A,
        wordsRu = setOf(
            "иисус", "христос", "христа", "христу", "христом", "христе", "мессия", "еммануил",
            "наследник", "спасител",
        ),
        wordsEn = setOf("jesus", "christ", "messiah", "saviour", "savior", "emmanuel"),
    ),
    HOLY_SPIRIT(
        id = "spirit",
        labelRu = "Дух, Святой Дух",
        priority = 2,
        defaultColorArgb = 0xFF00838F,
        wordsRu = setOf(
            "дух", "духа", "духу", "духом", "духе", "духи", "духов", "духам", "святой", "святая",
            "святое", "святые", "святом", "святую", "святым",
        ),
        wordsEn = setOf("spirit", "ghost", "holy", "paraclete"),
    ),
    LORD_NAME(
        id = "lord",
        labelRu = "Господь, Яхве",
        priority = 3,
        defaultColorArgb = 0xFF283593,
        wordsRu = setOf(
            "господь", "господа", "господу", "господом", "господе", "господи", "иегова", "яхве",
            "адонай",
        ),
        wordsEn = setOf("lord", "jehovah", "yahweh", "adonai"),
    ),
    PEOPLE(
        id = "people",
        labelRu = "Народ, ученики, апостолы",
        priority = 4,
        defaultColorArgb = 0xFF2E7D32,
        wordsRu = setOf(
            "ученик", "ученика", "ученику", "учеником", "ученике", "ученики", "учеников", "ученикам",
            "апостол", "апостола", "апостолу", "апостолом", "апостоле", "апостолы", "апостолов",
            "народ", "народа", "народу", "народом", "народе", "толпа", "толпы", "человек", "люди",
            "мужи", "жены", "дети", "сын", "сына", "сыну", "дочь", "дочери",
        ),
        wordsEn = setOf(
            "disciple", "disciples", "apostle", "apostles", "people", "crowd", "multitude", "man", "men",
            "woman", "women", "child", "children", "son", "daughter",
        ),
    ),
    ENEMIES(
        id = "enemies",
        labelRu = "Враги, зло, диавол",
        priority = 5,
        defaultColorArgb = 0xFFC62828,
        wordsRu = setOf(
            "враг", "врага", "врагу", "врагом", "враге", "враги", "врагов", "врагам", "сатана",
            "сатаны", "диавол", "диавола", "диаволу", "лукавый", "лукавого", "зло", "зла", "злу",
            "нечестив", "бес", "беса", "бесу", "бесы",
        ),
        wordsEn = setOf(
            "enemy", "enemies", "satan", "devil", "evil", "wicked", "demon", "demons", "adversary",
        ),
    ),
    SIN(
        id = "sin",
        labelRu = "Грех, беззаконие",
        priority = 6,
        defaultColorArgb = 0xFFAD1457,
        wordsRu = setOf(
            "грех", "греха", "греху", "грехом", "грехе", "грехи", "грехов", "грешник", "грешит",
            "беззаконие", "беззакония", "беззаконию", "согреш", "преступлен", "скверн", "нечистот",
        ),
        wordsEn = setOf("sin", "sins", "sinner", "iniquity", "transgression", "trespass", "unclean"),
    ),
    FAITH_LOVE(
        id = "faith_love",
        labelRu = "Вера, любовь, надежда",
        priority = 7,
        defaultColorArgb = 0xFFEF6C00,
        wordsRu = setOf(
            "вера", "веры", "вере", "веру", "верою", "верите", "верил", "верят", "любовь", "любви",
            "любовью", "люблю", "любит", "любите", "надежда", "надежды", "надежде", "милость",
            "милости", "милосерд",
        ),
        wordsEn = setOf(
            "faith", "believe", "love", "hope", "mercy", "grace", "charity", "lovingkindness",
        ),
    ),
    PRAYER(
        id = "prayer",
        labelRu = "Молитва, хвала",
        priority = 8,
        defaultColorArgb = 0xFF5D4037,
        wordsRu = setOf(
            "молитва", "молитвы", "молитву", "молитвой", "молиться", "молитесь", "молился", "молились",
            "хвала", "хвалы", "хвалу", "хвалите", "славослов", "благослов", "благодар",
        ),
        wordsEn = setOf("pray", "prayer", "prayed", "praise", "thank", "thanks", "worship", "bless"),
    ),
    LIFE_DEATH(
        id = "life_death",
        labelRu = "Жизнь, смерть, воскресение",
        priority = 9,
        defaultColorArgb = 0xFF00695C,
        wordsRu = setOf(
            "жизнь", "жизни", "жизнью", "жив", "живёт", "живут", "смерть", "смерти", "умер", "умерл",
            "воскрес", "воскресен", "воскресени", "гроб", "гроба", "погреб", "крест", "креста",
        ),
        wordsEn = setOf("life", "death", "die", "died", "resurrection", "rise", "grave", "cross"),
    ),
    ;

    companion object {
        fun byId(id: String): SemanticHighlightCategory? =
            entries.find { it.id == id }
    }
}

enum class SemanticScope {
    /** Один стих текущей главы */
    VERSE,
    /** Вся открытая глава */
    CHAPTER,
    /** Вся книга (все главы) */
    BOOK,
}

enum class SemanticDisplayStyle {
    /** Полупрозрачный фон */
    BACKGROUND,
    /** Цвет букв */
    FOREGROUND,
    /** Подчёркивание */
    UNDERLINE,
}

data class SemanticHighlightSession(
    val categories: Set<SemanticHighlightCategory>,
    val scope: SemanticScope,
    val bookId: String,
    val chapter: Int,
    /** Для [SemanticScope.VERSE] */
    val verseNumber: Int? = null,
    val displayStyle: SemanticDisplayStyle = SemanticDisplayStyle.BACKGROUND,
    /** Библейская палитра «свет / тьма» ([BiblicalThematicCategory]). */
    val biblicalThematicCategories: Set<BiblicalThematicCategory> = emptySet(),
)

fun SemanticHighlightSession.appliesToVerse(ref: VerseRef): Boolean {
    if (ref.bookId != bookId) return false
    return when (scope) {
        SemanticScope.VERSE ->
            ref.chapter == chapter && ref.verse == (verseNumber ?: return false)
        SemanticScope.CHAPTER -> ref.chapter == chapter
        SemanticScope.BOOK -> true
    }
}

data class SemanticStyleSpan(
    val start: Int,
    val end: Int,
    val colorArgb: Long,
    val isBackground: Boolean,
    val underline: Boolean,
    /** Метка смысла (лексикон пользователя / пресет); для тапа по слову. */
    val senseLabel: String? = null,
    /** Id правила [SemanticLexiconRule] для медиа и подписи в окне словаря. */
    val lexiconRuleId: String? = null,
)

private fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\u0301' // ударение

/** Поиск вхождений целого слова (без учёта регистра). */
internal fun findWholeWordOccurrences(text: String, token: String): List<IntRange> {
    if (token.isBlank()) return emptyList()
    val lowerText = text.lowercase()
    val lowerToken = token.lowercase()
    val result = mutableListOf<IntRange>()
    var from = 0
    while (from <= lowerText.length - lowerToken.length) {
        val i = lowerText.indexOf(lowerToken, from)
        if (i < 0) break
        val j = i + lowerToken.length
        val leftOk = i == 0 || !isWordChar(text[i - 1])
        val rightOk = j >= text.length || !isWordChar(text[j])
        if (leftOk && rightOk) {
            result.add(i until j)
        }
        from = i + 1
    }
    return result
}

private fun wordsForCategory(cat: SemanticHighlightCategory, translation: TranslationId): Set<String> {
    val ru = cat.wordsRu
    val en = cat.wordsEn
    return when (translation) {
        TranslationId.WEB -> en + ru.filter { it.length <= 3 }
        else -> ru + en.filter { it.length <= 4 }
    }
}

/** Подбор стиля отображения для категории. */
fun spanStyleForCategory(
    cat: SemanticHighlightCategory,
    displayStyle: SemanticDisplayStyle,
): Triple<Long, Boolean, Boolean> = spanStyleForArgb(cat.defaultColorArgb, displayStyle)

internal fun spanStyleForArgb(
    colorArgb: Long,
    displayStyle: SemanticDisplayStyle,
): Triple<Long, Boolean, Boolean> {
    return when (displayStyle) {
        SemanticDisplayStyle.BACKGROUND -> Triple(colorArgb, true, false)
        SemanticDisplayStyle.FOREGROUND -> Triple(colorArgb, false, false)
        SemanticDisplayStyle.UNDERLINE -> Triple(colorArgb, false, true)
    }
}

/**
 * Одна порция слов с собственным стилем (для лексикона) или общим стилем сессии.
 */
internal data class OrderedSpanJob(
    val words: Set<String>,
    val colorArgb: Long,
    val displayStyle: SemanticDisplayStyle,
    val senseLabel: String?,
    val lexiconRuleId: String? = null,
)

/**
 * Порядок: сначала библейская ось «свет», затем классические темы, затем ось «тьма» —
 * чтобы на перекрытии «свет» не затирался «тьмой».
 */
internal fun collectSemanticSpanJobs(
    classic: Set<SemanticHighlightCategory>,
    biblical: Set<BiblicalThematicCategory>,
    translation: TranslationId,
    sessionDisplayStyle: SemanticDisplayStyle,
): List<OrderedSpanJob> {
    val jobs = mutableListOf<OrderedSpanJob>()
    biblical
        .filter { it.axis == BiblicalAxis.LIGHT }
        .sortedBy { it.subPriority }
        .forEach { cat ->
            jobs.add(OrderedSpanJob(cat.wordsForTranslation(translation), cat.defaultColorArgb, sessionDisplayStyle, null, null))
        }
    classic.sortedBy { it.priority }.forEach { cat ->
        jobs.add(OrderedSpanJob(wordsForCategory(cat, translation), cat.defaultColorArgb, sessionDisplayStyle, null, null))
    }
    biblical
        .filter { it.axis == BiblicalAxis.DARK }
        .sortedBy { it.subPriority }
        .forEach { cat ->
            jobs.add(OrderedSpanJob(cat.wordsForTranslation(translation), cat.defaultColorArgb, sessionDisplayStyle, null, null))
        }
    return jobs
}

internal fun findSpansFromOrderedJobs(text: String, jobs: List<OrderedSpanJob>): List<SemanticStyleSpan> {
    if (text.isEmpty() || jobs.isEmpty()) return emptyList()
    val covered = BooleanArray(text.length)
    val spans = mutableListOf<SemanticStyleSpan>()
    for (job in jobs) {
        val (color, isBg, underline) = spanStyleForArgb(job.colorArgb, job.displayStyle)
        for (w in job.words) {
            if (w.length < 2) continue
            for (range in findWholeWordOccurrences(text, w)) {
                val overlaps = range.any { idx -> idx < covered.size && covered[idx] }
                if (overlaps) continue
                for (idx in range) {
                    if (idx < covered.size) covered[idx] = true
                }
                spans.add(
                    SemanticStyleSpan(
                        start = range.first,
                        end = range.last + 1,
                        colorArgb = color,
                        isBackground = isBg,
                        underline = underline,
                        senseLabel = job.senseLabel,
                        lexiconRuleId = job.lexiconRuleId,
                    ),
                )
            }
        }
    }
    return spans.sortedBy { it.start }
}

private fun SemanticLexiconRule.toOrderedSpanJob(translation: TranslationId): OrderedSpanJob =
    OrderedSpanJob(wordsForTranslation(translation), colorArgb, displayStyle, senseLabel, id)

/**
 * Пользовательский лексикон (всегда), затем временная сессия тематики, затем пресет (если включён).
 * Первое совпадение по порядку «занимает» символы.
 */
fun findFullReaderSemanticSpans(
    text: String,
    session: SemanticHighlightSession?,
    translation: TranslationId,
    presetLexiconEnabled: Boolean,
    userRules: List<SemanticLexiconRule>,
    presetRules: List<SemanticLexiconRule>,
): List<SemanticStyleSpan> {
    if (text.isEmpty()) return emptyList()
    val jobs = mutableListOf<OrderedSpanJob>()
    for (r in userRules.filter { it.enabled }) {
        jobs.add(r.toOrderedSpanJob(translation))
    }
    if (session != null &&
        (session.categories.isNotEmpty() || session.biblicalThematicCategories.isNotEmpty())
    ) {
        jobs.addAll(
            collectSemanticSpanJobs(
                session.categories,
                session.biblicalThematicCategories,
                translation,
                session.displayStyle,
            ),
        )
    }
    if (presetLexiconEnabled) {
        for (r in presetRules) {
            jobs.add(r.toOrderedSpanJob(translation))
        }
    }
    return findSpansFromOrderedJobs(text, jobs)
}

/**
 * Классические темы + библейская палитра; одна маска покрытия на весь проход.
 */
fun findUnifiedSemanticSpans(
    text: String,
    categories: Set<SemanticHighlightCategory>,
    biblicalThematicCategories: Set<BiblicalThematicCategory>,
    translation: TranslationId,
    displayStyle: SemanticDisplayStyle,
): List<SemanticStyleSpan> {
    if (text.isEmpty()) return emptyList()
    if (categories.isEmpty() && biblicalThematicCategories.isEmpty()) return emptyList()
    val jobs = collectSemanticSpanJobs(categories, biblicalThematicCategories, translation, displayStyle)
    return findSpansFromOrderedJobs(text, jobs)
}

/**
 * Находит непересекающиеся фрагменты (приоритет категории при конфликте).
 */
fun findSemanticStyleSpans(
    text: String,
    categories: Set<SemanticHighlightCategory>,
    translation: TranslationId,
    displayStyle: SemanticDisplayStyle,
): List<SemanticStyleSpan> =
    findUnifiedSemanticSpans(text, categories, emptySet(), translation, displayStyle)

fun buildHighlightedVerseAnnotated(
    verseText: String,
    highlights: List<TextHighlight>,
    semanticSpans: List<SemanticStyleSpan> = emptyList(),
): AnnotatedString {
    val len = verseText.length
    val sortedManual = highlights
        .filter { it.startOffset < it.endOffset }
        .sortedBy { it.startOffset }
    val sortedSemantic = semanticSpans
        .filter { it.start < it.end }
        .sortedBy { it.start }
    return buildAnnotatedString {
        append(verseText)
        for (s in sortedSemantic) {
            val st = s.start.coerceIn(0, len)
            val en = s.end.coerceIn(st, len)
            if (st >= en) continue
            val c = Color(s.colorArgb)
            when {
                s.isBackground -> addStyle(SpanStyle(background = c.copy(alpha = 0.42f)), st, en)
                s.underline -> addStyle(SpanStyle(color = c, textDecoration = TextDecoration.Underline), st, en)
                else -> addStyle(SpanStyle(color = c), st, en)
            }
            val label = s.senseLabel
            if (!label.isNullOrBlank()) {
                addStringAnnotation(tag = "sense", annotation = label, start = st, end = en)
            }
        }
        for (h in sortedManual) {
            val s = h.startOffset.coerceIn(0, len)
            val e = h.endOffset.coerceIn(s, len)
            if (s >= e) continue
            val c = Color(h.colorArgb)
            when {
                h.underline -> addStyle(SpanStyle(color = c, textDecoration = TextDecoration.Underline), s, e)
                h.isBackground -> addStyle(SpanStyle(background = c.copy(alpha = 0.42f)), s, e)
                else -> addStyle(SpanStyle(color = c), s, e)
            }
        }
    }
}
