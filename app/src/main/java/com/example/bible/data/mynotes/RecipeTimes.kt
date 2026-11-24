package com.example.bible.data.mynotes

/**
 * Кусок текста шага или заметки: [time] — это длительность вроде «10 мин» или «1,5 ч».
 * Склейка [text] всех кусков равна исходной строке.
 */
internal data class RecipeTextPiece(
    val text: String,
    val time: Boolean,
)

/** Количество в начале строки ингредиента и остаток названия. [amount] == null, если количества нет. */
internal data class RecipeIngredientParts(
    val amount: String?,
    val name: String,
)

/** Строка карточки ингредиентов: свой id для чекбокса и уже отделённое количество. */
internal data class RecipeIngredientRow(
    val id: String,
    val parts: RecipeIngredientParts,
)

private val timeNumber = """(?:\d+\s*/\s*\d+|\d+(?:[.,]\d+)?|[½¼¾⅓⅔⅛⅜⅝⅞])"""

/**
 * Полные слова идут раньше сокращений.
 * «ч» не захватывает «ч. л.» — это чайная ложка, а не час.
 */
private val timeUnit =
    """(?:минуту|минуты|минута|минут|часов|часа|часу|час|секунду|секунды|секунда|секунд|мин\.?|сек\.?|ч(?!\.?\s*л)\.?)"""

private val timeAmount = """$timeNumber(?:\s*[–—\-−]\s*$timeNumber)?\s*$timeUnit"""

private val TIME = Regex(
    """(?iu)(?<![\p{L}\d/])(?:полтора\s+час(?:а|ов|у)?|полчаса|$timeAmount)(?![\p{L}])""",
)

private val ingredientUnitSources = listOf(
    "килограммов", "килограмма", "килограмм", "килограмму",
    "миллилитров", "миллилитра", "миллилитр", "миллилитру",
    "столовых ложек", "столовых ложки", "столовая ложка", "столовую ложку", "столовые ложки",
    "чайных ложек", "чайных ложки", "чайная ложка", "чайную ложку", "чайные ложки",
    "ст. ложек", "ст. ложки", "ст. ложка", "ст. ложку",
    "ч. ложек", "ч. ложки", "ч. ложка", "ч. ложку",
    "стаканов", "стакана", "стакан", "стакану",
    "зубчиков", "зубчика", "зубчик", "зубчику",
    "щепоток", "щепотки", "щепотка", "щепотку",
    "упаковок", "упаковки", "упаковка", "упаковку",
    "головок", "головки", "головка", "головку",
    "долек", "дольки", "долька", "дольку",
    "веточек", "веточки", "веточка", "веточку",
    "пучков", "пучка", "пучок", "пучку",
    "пачек", "пачки", "пачка", "пачку",
    "банок", "банки", "банка", "банку",
    "литров", "литра", "литр", "литру",
    "граммов", "грамма", "грамм", "грамму",
    "ложек", "ложки", "ложка", "ложку",
    "штук", "штуки", "штука", "штуку",
    "ст. л.", "ст.л.", "ст. л", "ст.л",
    "ч. л.", "ч.л.", "ч. л", "ч.л",
    "шт.", "шт",
    "кг", "гр.", "гр", "мг", "мл",
    "г.", "г",
    "л.", "л",
)

private val ingredientUnit = ingredientUnitSources
    .distinct()
    .sortedByDescending { it.length }
    .joinToString("|") { Regex.escape(it) }
    .let { "(?:$it)" }

private val amountNumber = """(?:\d+\s*/\s*\d+|\d+(?:[.,]\d+)?|[½¼¾⅓⅔⅛⅜⅝⅞])"""

private val amountRange = """$amountNumber(?:\s*[–—\-−]\s*$amountNumber)?"""

private val AMOUNT = Regex(
    """(?iu)^($amountRange(?:\s*$ingredientUnit(?![\p{L}\d]))?)(?![\p{L}\d])(?:\s+(.+))?${'$'}""",
)

private val BARE_AMOUNT = Regex(
    """(?iu)^(по\s+вкусу|щепотка|щепотки|щепотку|пучок|пучка)\s+(.+)$""",
)

/**
 * Делит строку на обычный текст и длительности.
 * Соседние длительности («2 часа 15 минут», «10 мин. 30 сек.») склеиваются в один кусок.
 */
internal fun splitRecipeTimes(text: String): List<RecipeTextPiece> {
    if (text.isEmpty()) return emptyList()
    val found = TIME.findAll(text).map { it.range }.toList()
    if (found.isEmpty()) return listOf(RecipeTextPiece(text, time = false))
    val merged = mutableListOf<IntRange>()
    for (range in found) {
        val last = merged.lastOrNull()
        if (last != null && text.substring(last.last + 1, range.first).isBlank()) {
            merged[merged.lastIndex] = last.first..range.last
        } else {
            merged += range
        }
    }
    val pieces = mutableListOf<RecipeTextPiece>()
    var cursor = 0
    for (range in merged) {
        if (cursor < range.first) {
            pieces += RecipeTextPiece(text.substring(cursor, range.first), time = false)
        }
        pieces += RecipeTextPiece(text.substring(range.first, range.last + 1), time = true)
        cursor = range.last + 1
    }
    if (cursor < text.length) {
        pieces += RecipeTextPiece(text.substring(cursor), time = false)
    }
    return pieces
}

/** Отделяет ведущее количество («200 г», «1,5 ст. л.», «по вкусу») от названия. */
internal fun splitRecipeIngredient(text: String): RecipeIngredientParts {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return RecipeIngredientParts(amount = null, name = "")
    val times = splitRecipeTimes(trimmed)
    if (times.firstOrNull()?.time == true) {
        return RecipeIngredientParts(amount = null, name = trimmed)
    }
    val measured = AMOUNT.find(trimmed)
    if (measured != null) {
        val amount = measured.groupValues[1].trim().replace(Regex("""\s+"""), " ")
        val name = measured.groups[2]?.value?.trim().orEmpty()
        if (amount.isNotEmpty()) return RecipeIngredientParts(amount = amount, name = name)
    }
    val bare = BARE_AMOUNT.find(trimmed)
    if (bare != null) {
        return RecipeIngredientParts(
            amount = bare.groupValues[1].trim().replace(Regex("""\s+"""), " "),
            name = bare.groupValues[2].trim(),
        )
    }
    return RecipeIngredientParts(amount = null, name = trimmed)
}

private val ingredientBullet = Regex("""[•●∙·▪◦‣]""")

/** Тире с пробелами вокруг. Диапазон «2 - 3» (цифры с обеих сторон) не трогаем. */
private val ingredientListDash = Regex("""[ \t]+[-–—−][ \t]+""")

private val leadingIngredientBullet = Regex("""^[-–—−*•●∙·▪◦‣]+\s*""")

/**
 * Делит текст на отдельные ингредиенты.
 * Разделители: перевод строки, запятая, точка с запятой и маркеры «-» / «•».
 * Запятая в числе («1,5») и диапазон («2-3», «2 - 3») остаются внутри пункта.
 * Запятая в скобках («мука (пшеничная, высший сорт)») тоже не делит пункт.
 */
internal fun splitRecipeIngredientList(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val normalized = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(ingredientBullet, "\n")
        .let(::unwrapIngredientListDashes)
    val items = mutableListOf<String>()
    normalized.split('\n').forEach { rawLine ->
        splitIngredientSeparators(rawLine).forEach { piece ->
            val item = piece.trim().replace(leadingIngredientBullet, "").trim()
            if (item.isNotEmpty()) items += item
        }
    }
    return items
}

/** Разворачивает сохранённые строки в пункты карточки. У одиночного пункта id строки сохраняется. */
internal fun expandRecipeIngredientLines(lines: List<RecipeLine>): List<RecipeIngredientRow> {
    return lines.flatMap { line ->
        val pieces = splitRecipeIngredientList(line.text)
        pieces.mapIndexed { index, piece ->
            RecipeIngredientRow(
                id = if (pieces.size == 1) line.id else "${line.id}#$index",
                parts = splitRecipeIngredient(piece),
            )
        }
    }
}

private fun unwrapIngredientListDashes(text: String): String {
    return ingredientListDash.replace(text) { match ->
        val before = text.getOrNull(match.range.first - 1)
        val after = text.getOrNull(match.range.last + 1)
        if (before?.isDigit() == true && after?.isDigit() == true) match.value else "\n"
    }
}

private fun splitIngredientSeparators(text: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0
    var index = 0
    while (index < text.length) {
        val ch = text[index]
        when (ch) {
            '(', '[', '«' -> {
                depth++
                current.append(ch)
            }
            ')', ']', '»' -> {
                if (depth > 0) depth--
                current.append(ch)
            }
            ';', ',' -> {
                val decimalComma = ch == ',' && isDecimalComma(text, index)
                if (depth == 0 && !decimalComma) {
                    result += current.toString()
                    current.clear()
                } else {
                    current.append(ch)
                }
            }
            else -> current.append(ch)
        }
        index++
    }
    result += current.toString()
    return result
}

private fun isDecimalComma(text: String, index: Int): Boolean {
    val previous = text.getOrNull(index - 1)
    val next = text.getOrNull(index + 1)
    return previous != null && previous.isDigit() && next != null && next.isDigit()
}

/** Есть что показать в режиме просмотра рецепта. Пустой черновик открывается сразу в правке. */
internal fun MyNote.hasRecipeText(): Boolean {
    if (kind != MyNoteKind.RECIPE) return false
    if (title.isNotBlank() || body.isNotBlank() || servings.isNotBlank() || cookTime.isNotBlank()) {
        return true
    }
    return ingredients.any { it.text.isNotBlank() } || steps.any { it.text.isNotBlank() }
}
