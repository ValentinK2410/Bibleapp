package com.example.bible.data

data class RussianLetter(
    val upper: Char,
    val lower: Char,
    val name: String,
    val sound: String,
    val type: LetterType,
    val exampleWord: String,
    val exampleTranslation: String,
    val writingHint: String,
)

enum class LetterType(val label: String, val emoji: String) {
    VOWEL("Гласная", "🔴"),
    CONSONANT("Согласная", "🔵"),
    SIGN("Знак", "⚪"),
}

data class SyllableLesson(
    val title: String,
    val description: String,
    val syllables: List<String>,
    val exampleWords: List<Pair<String, String>>,
)

data class ReadingRule(
    val title: String,
    val explanation: String,
    val examples: List<String>,
)

data class AzbukaLesson(
    val id: Int,
    val title: String,
    val description: String,
    val letters: List<RussianLetter>,
    val syllables: List<String>,
    val words: List<Pair<String, String>>,
    val exercises: List<AzbukaExercise>,
)

data class AzbukaExercise(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val hint: String = "",
)

object AzbukaRepository {

    val ALPHABET: List<RussianLetter> = listOf(
        RussianLetter('А', 'а', "А", "а", LetterType.VOWEL, "Ангел", "Посланник Божий", "Две наклонные палочки с перекладиной"),
        RussianLetter('Б', 'б', "Бэ", "б", LetterType.CONSONANT, "Бог", "Творец всего", "Палочка с полукругом внизу и козырьком"),
        RussianLetter('В', 'в', "Вэ", "в", LetterType.CONSONANT, "Вера", "Доверие Богу", "Палочка с двумя полукругами справа"),
        RussianLetter('Г', 'г', "Гэ", "г", LetterType.CONSONANT, "Господь", "Бог, Владыка", "Палочка с перекладиной сверху"),
        RussianLetter('Д', 'д', "Дэ", "д", LetterType.CONSONANT, "Дух", "Дух Святой", "Треугольник на ножках"),
        RussianLetter('Е', 'е', "Е", "йэ / э", LetterType.VOWEL, "Евангелие", "Благая весть", "Палочка с тремя горизонтальными линиями"),
        RussianLetter('Ё', 'ё', "Ё", "йо / о", LetterType.VOWEL, "Ёлка", "Хвойное дерево", "Как Е, но с двумя точками сверху"),
        RussianLetter('Ж', 'ж', "Жэ", "ж", LetterType.CONSONANT, "Жизнь", "Дар Божий", "Три палочки — как снежинка"),
        RussianLetter('З', 'з', "Зэ", "з", LetterType.CONSONANT, "Земля", "Мир, созданный Богом", "Два полукруга один над другим"),
        RussianLetter('И', 'и', "И", "и", LetterType.VOWEL, "Иисус", "Спаситель", "Две палочки с перемычкой наискосок"),
        RussianLetter('Й', 'й', "И краткое", "й", LetterType.CONSONANT, "Рай", "Божий сад", "Как И, но с дужкой сверху"),
        RussianLetter('К', 'к', "Ка", "к", LetterType.CONSONANT, "Крест", "Символ спасения", "Палочка с двумя наклонными"),
        RussianLetter('Л', 'л', "Эль", "л", LetterType.CONSONANT, "Любовь", "Божья любовь", "Две наклонные палочки — шалашик"),
        RussianLetter('М', 'м', "Эм", "м", LetterType.CONSONANT, "Мир", "Покой, вселенная", "Две палочки с галочкой сверху"),
        RussianLetter('Н', 'н', "Эн", "н", LetterType.CONSONANT, "Небо", "Обитель Бога", "Две палочки с перемычкой посередине"),
        RussianLetter('О', 'о', "О", "о", LetterType.VOWEL, "Отец", "Бог Отец", "Круг — как солнце"),
        RussianLetter('П', 'п', "Пэ", "п", LetterType.CONSONANT, "Пророк", "Вестник Бога", "Две палочки с перекладиной сверху — ворота"),
        RussianLetter('Р', 'р', "Эр", "р", LetterType.CONSONANT, "Радость", "Дар Духа Святого", "Палочка с полукругом сверху"),
        RussianLetter('С', 'с', "Эс", "с", LetterType.CONSONANT, "Свет", "Бог есть свет", "Полукруг, открытый вправо"),
        RussianLetter('Т', 'т', "Тэ", "т", LetterType.CONSONANT, "Троица", "Отец, Сын, Дух Святой", "Перекладина сверху на палочке"),
        RussianLetter('У', 'у', "У", "у", LetterType.VOWEL, "Утешитель", "Дух Святой", "Развилка, палочка вниз"),
        RussianLetter('Ф', 'ф', "Эф", "ф", LetterType.CONSONANT, "Фарисей", "Религиозный лидер", "Кружок на палочке — как ключ"),
        RussianLetter('Х', 'х', "Ха", "х", LetterType.CONSONANT, "Хлеб", "Хлеб жизни", "Две перекрещённые палочки"),
        RussianLetter('Ц', 'ц', "Цэ", "ц", LetterType.CONSONANT, "Царь", "Бог — Царь царей", "Как И, но с хвостиком внизу"),
        RussianLetter('Ч', 'ч', "Чэ", "ч", LetterType.CONSONANT, "Чудо", "Божье чудо", "Перевёрнутая буква У"),
        RussianLetter('Ш', 'ш', "Ша", "ш", LetterType.CONSONANT, "Шестоднев", "Дни творения", "Три палочки на подставке"),
        RussianLetter('Щ', 'щ', "Ща", "щ", LetterType.CONSONANT, "Щедрость", "Божья щедрость", "Как Ш, но с хвостиком"),
        RussianLetter('Ъ', 'ъ', "Твёрдый знак", "—", LetterType.SIGN, "Объятие", "Разделяет звуки", "Палочка с полукругом — знак твёрдости"),
        RussianLetter('Ы', 'ы', "Ы", "ы", LetterType.VOWEL, "Рыба", "Символ христиан", "Палочка с полукругом и ещё палочка"),
        RussianLetter('Ь', 'ь', "Мягкий знак", "—", LetterType.SIGN, "Радость", "Смягчает согласную", "Палочка с полукругом внизу"),
        RussianLetter('Э', 'э', "Э", "э", LetterType.VOWEL, "Эдем", "Райский сад", "Полукруг с чёрточкой, открытый влево"),
        RussianLetter('Ю', 'ю', "Ю", "йу / у", LetterType.VOWEL, "Юноша", "Молодой человек", "Палочка с кружком справа"),
        RussianLetter('Я', 'я', "Я", "йа / а", LetterType.VOWEL, "Явление", "Божье явление", "Зеркальная буква R"),
    )

    val LESSONS: List<AzbukaLesson> = listOf(
        AzbukaLesson(
            id = 1,
            title = "Гласные А, О, У",
            description = "Знакомимся с первыми гласными звуками — они поются!",
            letters = ALPHABET.filter { it.upper in listOf('А', 'О', 'У') },
            syllables = listOf("АО", "ОУ", "АУ", "УА", "ОА"),
            words = listOf("Ау" to "Зов в лесу", "Уа" to "Плач младенца"),
            exercises = listOf(
                AzbukaExercise("Какая буква похожа на круг?", listOf("А", "О", "У"), 1),
                AzbukaExercise("Какой звук в слове «Ангел» первый?", listOf("О", "У", "А"), 2),
                AzbukaExercise("Прочитай: У-А. Что получится?", listOf("Ау", "Уа", "Оа"), 1),
            ),
        ),
        AzbukaLesson(
            id = 2,
            title = "Согласные М, П, Б",
            description = "Первые согласные — они не поются, а «стучат».",
            letters = ALPHABET.filter { it.upper in listOf('М', 'П', 'Б') },
            syllables = listOf("МА", "МО", "МУ", "ПА", "ПО", "ПУ", "БА", "БО", "БУ"),
            words = listOf("Мама" to "Самый близкий человек", "Папа" to "Отец", "Бог" to "Творец"),
            exercises = listOf(
                AzbukaExercise("Прочитай: М+А. Какой слог?", listOf("ПА", "МА", "БА"), 1),
                AzbukaExercise("Какое слово: МА+МА?", listOf("Папа", "Мама", "Баба"), 1),
                AzbukaExercise("Какой первый звук в слове «Бог»?", listOf("П", "М", "Б"), 2),
            ),
        ),
        AzbukaLesson(
            id = 3,
            title = "Согласные Н, Т, Д",
            description = "Новые согласные — учимся читать больше слогов!",
            letters = ALPHABET.filter { it.upper in listOf('Н', 'Т', 'Д') },
            syllables = listOf("НА", "НО", "НУ", "ТА", "ТО", "ТУ", "ДА", "ДО", "ДУ"),
            words = listOf("Дом" to "Жилище", "Нота" to "Музыкальный звук", "Тут" to "Здесь"),
            exercises = listOf(
                AzbukaExercise("Прочитай: Д+О+М. Какое слово?", listOf("Том", "Дом", "Ном"), 1),
                AzbukaExercise("Какой слог: Н+А?", listOf("НА", "ТА", "ДА"), 0),
                AzbukaExercise("Составь слово: НО+ТА", listOf("Нота", "Дата", "Тона"), 0),
            ),
        ),
        AzbukaLesson(
            id = 4,
            title = "Гласные И, Е, Ы",
            description = "Новые гласные — делают согласные мягкими!",
            letters = ALPHABET.filter { it.upper in listOf('И', 'Е', 'Ы') },
            syllables = listOf("МИ", "НИ", "ТИ", "ДИ", "МЕ", "НЕ", "ТЕ", "ДЕ", "МЫ", "ТЫ"),
            words = listOf("Мир" to "Покой и вселенная", "Небо" to "Обитель Бога", "Мы" to "Вместе"),
            exercises = listOf(
                AzbukaExercise("Прочитай: М+И+Р. Какое слово?", listOf("Мир", "Мур", "Мор"), 0),
                AzbukaExercise("Какая гласная делает согласную мягкой?", listOf("А", "О", "И"), 2),
                AzbukaExercise("Прочитай: НЕ+БО", listOf("Небо", "Нёбо", "Нибо"), 0),
            ),
        ),
        AzbukaLesson(
            id = 5,
            title = "Согласные К, Г, Х",
            description = "Горловые звуки — К, Г, Х.",
            letters = ALPHABET.filter { it.upper in listOf('К', 'Г', 'Х') },
            syllables = listOf("КА", "КО", "КУ", "ГА", "ГО", "ГУ", "ХА", "ХО", "ХУ"),
            words = listOf("Крест" to "Символ спасения", "Господь" to "Бог", "Хлеб" to "Хлеб жизни"),
            exercises = listOf(
                AzbukaExercise("Какой первый звук в слове «Господь»?", listOf("Х", "К", "Г"), 2),
                AzbukaExercise("Прочитай: Х+А. Какой слог?", listOf("КА", "ХА", "ГА"), 1),
                AzbukaExercise("Слово ГО+РА означает:", listOf("Река", "Гора", "Нора"), 1),
            ),
        ),
        AzbukaLesson(
            id = 6,
            title = "Согласные С, З, Л, Р",
            description = "Свистящие и плавные звуки.",
            letters = ALPHABET.filter { it.upper in listOf('С', 'З', 'Л', 'Р') },
            syllables = listOf("СА", "ЗА", "ЛА", "РА", "СО", "ЗО", "ЛО", "РО", "СУ", "ЗУ", "ЛУ", "РУ"),
            words = listOf("Свет" to "Бог есть свет", "Земля" to "Мир Божий", "Любовь" to "Главная заповедь", "Радость" to "Дар Духа"),
            exercises = listOf(
                AzbukaExercise("Прочитай: С+В+Е+Т", listOf("Свет", "Звук", "Слот"), 0),
                AzbukaExercise("Какой первый звук в «Любовь»?", listOf("Р", "Л", "С"), 1),
                AzbukaExercise("Составь слово: РА+ДО+СТЬ", listOf("Старость", "Радость", "Мудрость"), 1),
            ),
        ),
        AzbukaLesson(
            id = 7,
            title = "Согласные В, Ф, Ж, Ш, Щ",
            description = "Шипящие и глухие-звонкие пары.",
            letters = ALPHABET.filter { it.upper in listOf('В', 'Ф', 'Ж', 'Ш', 'Щ') },
            syllables = listOf("ВА", "ВО", "ФА", "ФО", "ЖА", "ЖО", "ША", "ШО", "ЩА", "ЩО"),
            words = listOf("Вера" to "Доверие Богу", "Жизнь" to "Дар Божий", "Шестоднев" to "Дни творения"),
            exercises = listOf(
                AzbukaExercise("Прочитай: В+Е+РА", listOf("Вера", "Фара", "Жара"), 0),
                AzbukaExercise("Ж и Ш — это какие звуки?", listOf("Свистящие", "Шипящие", "Плавные"), 1),
                AzbukaExercise("Какой первый звук в «Щедрость»?", listOf("Ш", "Ж", "Щ"), 2),
            ),
        ),
        AzbukaLesson(
            id = 8,
            title = "Буквы Ч, Ц, Й, Э, Ю, Я, Ё",
            description = "Оставшиеся буквы и особые звуки.",
            letters = ALPHABET.filter { it.upper in listOf('Ч', 'Ц', 'Й', 'Э', 'Ю', 'Я', 'Ё') },
            syllables = listOf("ЧА", "ЧУ", "ЦА", "ЦО", "ЮА", "ЯМ"),
            words = listOf("Чудо" to "Божье чудо", "Царь" to "Бог — Царь", "Явление" to "Приход Бога"),
            exercises = listOf(
                AzbukaExercise("Прочитай: ЧУ+ДО", listOf("Чудо", "Чадо", "Худо"), 0),
                AzbukaExercise("Буква Я обозначает два звука:", listOf("И+А", "Й+А", "А+Й"), 1),
                AzbukaExercise("Буква Ё всегда:", listOf("Безударная", "Ударная", "Тихая"), 1),
            ),
        ),
        AzbukaLesson(
            id = 9,
            title = "Ъ и Ь — знаки",
            description = "Твёрдый и мягкий знак не имеют звука, но меняют слово.",
            letters = ALPHABET.filter { it.upper in listOf('Ъ', 'Ь') },
            syllables = emptyList(),
            words = listOf("Объятие" to "Твёрдый знак разделяет", "Радость" to "Мягкий знак смягчает", "Соль" to "С мягким знаком на конце"),
            exercises = listOf(
                AzbukaExercise("Какой знак смягчает согласную?", listOf("Ъ", "Ь", "Оба"), 1),
                AzbukaExercise("В слове «объятие» какой знак?", listOf("Ь", "Ъ", "Нет знака"), 1),
                AzbukaExercise("Мягкий знак обозначает звук?", listOf("Да", "Нет", "Иногда"), 1),
            ),
        ),
    )

    val SYLLABLE_LESSONS: List<SyllableLesson> = listOf(
        SyllableLesson(
            title = "Открытые слоги",
            description = "Согласная + гласная = открытый слог. Это самый простой тип слога.",
            syllables = listOf("МА", "МО", "МУ", "МИ", "МЕ", "НА", "НО", "НУ", "НИ", "НЕ", "ТА", "ТО", "ТУ", "ТИ", "ТЕ", "ДА", "ДО", "ДУ"),
            exampleWords = listOf("Ма-ма" to "Слово из двух открытых слогов", "Мо-ло-ко" to "Три открытых слога", "Не-бо" to "Два открытых слога"),
        ),
        SyllableLesson(
            title = "Закрытые слоги",
            description = "Слог, который оканчивается на согласную.",
            syllables = listOf("АМ", "ОН", "УМ", "ОТ", "АН", "АТ", "ОМ", "УТ"),
            exampleWords = listOf("Дом" to "Один закрытый слог", "Мир" to "Один закрытый слог", "Хлеб" to "Один закрытый слог"),
        ),
        SyllableLesson(
            title = "Слоги со стечением",
            description = "Две согласные подряд перед гласной.",
            syllables = listOf("СТА", "СТО", "ПРА", "ПРО", "КРА", "КРО", "ТРА", "ТРО", "ГРА"),
            exampleWords = listOf("Кре-ст" to "Стечение КР", "Прав-да" to "Стечение ПР", "Стра-на" to "Стечение СТР"),
        ),
    )

    val READING_RULES: List<ReadingRule> = listOf(
        ReadingRule(
            "Гласные и согласные",
            "В русском языке 10 гласных букв: А, О, У, Э, Ы, И, Е, Ё, Ю, Я. Гласные можно петь! Согласных 21 — они произносятся с помощью губ, языка, зубов. Ещё есть 2 знака: Ъ и Ь — они не имеют звука.",
            listOf("Гласные: А О У Э Ы И Е Ё Ю Я", "Согласные: Б В Г Д Ж З К Л М Н П Р С Т Ф Х Ц Ч Ш Щ Й", "Знаки: Ъ Ь"),
        ),
        ReadingRule(
            "Как складывать буквы в слоги",
            "Тяни гласную, а перед ней ставь согласную: М-М-М-А → МА. Согласный звук «подбегает» к гласному. Рот сначала готовится произнести согласную, а потом сразу переходит к гласной.",
            listOf("М + А = МА (тяни: М-М-А-А)", "Н + О = НО (тяни: Н-Н-О-О)", "Д + А = ДА (тяни: Д-Д-А-А)"),
        ),
        ReadingRule(
            "Мягкие и твёрдые согласные",
            "Согласные бывают твёрдыми и мягкими. Гласные И, Е, Ё, Ю, Я смягчают согласную перед ними. Гласные А, О, У, Э, Ы оставляют согласную твёрдой.",
            listOf("МА (твёрдая М) — МИ (мягкая М)", "ТУ (твёрдая Т) — ТЮ (мягкая Т)", "НО (твёрдая Н) — НЁ (мягкая Н)"),
        ),
        ReadingRule(
            "Звонкие и глухие согласные",
            "Звонкие согласные произносятся с голосом: Б, В, Г, Д, Ж, З. Глухие — без голоса: П, Ф, К, Т, Ш, С. Они образуют пары.",
            listOf("Б — П (Бог — Пот)", "В — Ф (Вера — Фара)", "Г — К (Гора — Кора)", "Д — Т (Дом — Том)", "Ж — Ш (Жар — Шар)", "З — С (Зуб — Суп)"),
        ),
        ReadingRule(
            "ЖИ-ШИ пиши с И",
            "Это важное правило! После Ж и Ш всегда пишется И, хотя слышится Ы.",
            listOf("ЖИзнь (не «ЖЫзнь»)", "ШИрокий (не «ШЫрокий»)", "Жираф, Шило, Живот"),
        ),
        ReadingRule(
            "ЧА-ЩА пиши с А",
            "После Ч и Щ пишется А, хотя может слышаться Я.",
            listOf("ЧАсы (не «ЧЯсы»)", "ЩАвель (не «ЩЯвель»)", "Чаша, Пища, Чайка"),
        ),
        ReadingRule(
            "ЧУ-ЩУ пиши с У",
            "После Ч и Щ пишется У, хотя может слышаться Ю.",
            listOf("ЧУдо (не «ЧЮдо»)", "ЩУка (не «ЩЮка»)", "Чувство, Щупальце"),
        ),
        ReadingRule(
            "Слог — это один толчок воздуха",
            "Сколько гласных — столько слогов. Каждая гласная образует слог. Хлопай в ладоши на каждую гласную!",
            listOf("Бог — 1 гласная = 1 слог", "Ма-ма — 2 гласные = 2 слога", "Мо-ло-ко — 3 гласные = 3 слога", "Е-ван-ге-ли-е — 5 гласных = 5 слогов"),
        ),
        ReadingRule(
            "Ударение",
            "В каждом слове одна гласная произносится сильнее — это ударный слог. Ударение меняет значение слова!",
            listOf("зА́мок (крепость) — замО́к (на двери)", "мУ́ка (страдание) — мукА́ (для хлеба)", "В слове из одного слога ударение не ставится: Бог, мир, свет"),
        ),
    )

    /** Когда буква пишется не «как по общему правилу» — для вкладки «Правила». */
    val SPELLING_EXCEPTIONS: List<ReadingRule> = listOf(
        ReadingRule(
            title = "После Ц пишется Ы, а не И",
            explanation = "Часто после буквы Ц пишут И: цифра, цирк, цитата, цикл. Но в ряде слов в корне пишется Ы — это исключения, их лучше запомнить отдельно.",
            examples = listOf(
                "Цыплёнок (не «циплёнок»)",
                "Цыган, цыганка",
                "На цыпочках, цыкать",
            ),
        ),
        ReadingRule(
            title = "ЖЮ, ШЮ: чаще в иноязычных словах",
            explanation = "Правила «жи-ши» и «чу-щу» говорят: после Ж и Ш обычно И, после Ч и Щ часто У. Но в словах из других языков слышится «ю» — и пишут букву Ю.",
            examples = listOf(
                "Жюри (суд присяжных)",
                "Брошюра (не «брошура»)",
                "Парашют (не «парашут»)",
            ),
        ),
        ReadingRule(
            title = "Не путать: Ы и И после Ц",
            explanation = "Если слышится похожий звук, не значит, что всегда одна буква. Сравни: цыган и цирк — в обоих после Ц, но буквы разные (ы и и).",
            examples = listOf(
                "Цыган — в корне буква ы",
                "Цирк, цифра — И в корне",
                "Спроси взрослого, если сомневаешься",
            ),
        ),
        ReadingRule(
            title = "Редкие написания — лучше по словарю",
            explanation = "Есть слова, где написание не угадывается только по правилу: их учат или проверяют по орфографическому словарю. Главное — знать основные правила и помнить про исключения.",
            examples = listOf(
                "Сначала правило, потом исключения",
                "Тренируйся в песочнице и уроках",
                "В Библии много имён — там тоже бывают особые буквы",
            ),
        ),
    )
}
