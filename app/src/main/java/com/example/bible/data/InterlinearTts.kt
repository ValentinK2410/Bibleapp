package com.example.bible.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Какое слово сейчас произносится в режиме [InterlinearTts.speakSequence] (подсветка в читалке). */
data class InterlinearWordSequenceHighlight(
    val generation: Long,
    val wordIndex: Int,
)

/**
 * Озвучивание подстрочных слов (иврит / греческий) через системный TTS.
 * По книге: **Ветхий завет** — иврит (he), **Новый завет** — греческий (el).
 * Если [bookId] не передан — язык по номеру Стронга и символам в [InterlinearWord.original].
 * При отсутствии голоса для языка — латинская транслитерация [InterlinearWord.transliteration] (en-US).
 */
class InterlinearTts(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingSpeak: Pair<InterlinearWord, String?>? = null
    /** Запрос озвучки иврита до завершения инициализации движка (песочница). */
    private var pendingHebrewSandbox: String? = null

    /** Очередь «вся глава» / «весь стих» — индекс следующего к произнесению. */
    private var sequenceWords: List<InterlinearWord>? = null
    private var sequenceBookId: String? = null

    /** Очередь слов арабского аята (озвучка по словам). */
    private var arabicWordSequence: List<String>? = null

    /** Очередь отдельных букв/глифов арабского слова (песочница). */
    private var arabicLetterSequence: List<String>? = null

    /** Очередь букв ивритского слова (песочница). */
    private var hebrewLetterSequence: List<String>? = null

    private var sequenceGeneration = 0L
    private val _sequenceHighlight = MutableStateFlow<InterlinearWordSequenceHighlight?>(null)
    val sequenceHighlight: StateFlow<InterlinearWordSequenceHighlight?> = _sequenceHighlight.asStateFlow()

    /** Колбэк после последнего слова [speakQuranVerseArabicWordByWord] (в т.ч. при обрыве последовательности). */
    private var onArabicWordSequenceFullySpoken: (() -> Unit)? = null

    /** Разовые колбэки по [utteranceId] (русский справочный текст, арабский аят целиком). */
    private val utteranceCompleteActions = mutableMapOf<String, () -> Unit>()

    private fun clearSequenceHighlight() {
        _sequenceHighlight.value = null
    }

    private fun runUtteranceCompletion(utteranceId: String?) {
        if (utteranceId == null) return
        utteranceCompleteActions.remove(utteranceId)?.let { mainHandler.post(it) }
    }

    private fun finalizeArabicWordSequence(fireCallback: Boolean) {
        val cb = onArabicWordSequenceFullySpoken
        onArabicWordSequenceFullySpoken = null
        if (fireCallback && cb != null) {
            mainHandler.post(cb)
        }
    }

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.setSpeechRate(0.88f)
                tts?.setPitch(1f)
                tts?.let { engine ->
                    engine.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}

                            private fun advanceQueuedUtterance(utteranceId: String?) {
                                when {
                                    utteranceId?.startsWith("seq_") == true -> {
                                        val idx = utteranceId.removePrefix("seq_").toIntOrNull() ?: return
                                        mainHandler.post { speakSequenceIndex(idx + 1) }
                                    }
                                    utteranceId?.startsWith("qseq_") == true -> {
                                        val idx = utteranceId.removePrefix("qseq_").toIntOrNull() ?: return
                                        mainHandler.post { speakArabicWordIndex(idx + 1) }
                                    }
                                    utteranceId?.startsWith("lseq_") == true -> {
                                        val idx = utteranceId.removePrefix("lseq_").toIntOrNull() ?: return
                                        mainHandler.post { speakArabicLetterIndex(idx + 1) }
                                    }
                                    utteranceId?.startsWith("hlseq_") == true -> {
                                        val idx = utteranceId.removePrefix("hlseq_").toIntOrNull() ?: return
                                        mainHandler.post { speakHebrewLetterIndex(idx + 1) }
                                    }
                                }
                            }

                            override fun onDone(utteranceId: String?) {
                                runUtteranceCompletion(utteranceId)
                                advanceQueuedUtterance(utteranceId)
                            }

                            override fun onError(utteranceId: String?, errorCode: Int) {
                                runUtteranceCompletion(utteranceId)
                                advanceQueuedUtterance(utteranceId)
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                runUtteranceCompletion(utteranceId)
                                advanceQueuedUtterance(utteranceId)
                            }
                        },
                    )
                }
                pendingSpeak?.let { (w, b) ->
                    pendingSpeak = null
                    speak(w, b)
                }
                pendingHebrewSandbox?.let { h ->
                    pendingHebrewSandbox = null
                    speakHebrew(h)
                }
            }
        }
    }

    fun speak(word: InterlinearWord, bookId: String? = null) {
        arabicWordSequence = null
        arabicLetterSequence = null
        hebrewLetterSequence = null
        stopSequence()
        tts?.stop()
        if (!ready) {
            pendingSpeak = word to bookId
            return
        }
        speakWordWithId(word, "single_${word.strong}_${System.nanoTime()}", bookId)
    }

    /** Все слова подряд (стих или глава). */
    fun speakSequence(words: List<InterlinearWord>, bookId: String? = null) {
        if (words.isEmpty()) return
        if (!ready) return
        tts?.stop()
        arabicWordSequence = null
        arabicLetterSequence = null
        hebrewLetterSequence = null
        sequenceGeneration++
        sequenceWords = words
        sequenceBookId = bookId
        speakSequenceIndex(0)
    }

    fun stopSequence() {
        sequenceWords = null
        sequenceBookId = null
        arabicWordSequence = null
        arabicLetterSequence = null
        hebrewLetterSequence = null
        onArabicWordSequenceFullySpoken = null
        utteranceCompleteActions.clear()
        clearSequenceHighlight()
    }

    private fun speakSequenceIndex(index: Int) {
        val list = sequenceWords ?: return
        if (index >= list.size) {
            sequenceWords = null
            sequenceBookId = null
            clearSequenceHighlight()
            return
        }
        _sequenceHighlight.value = InterlinearWordSequenceHighlight(sequenceGeneration, index)
        speakWordWithId(list[index], "seq_$index", sequenceBookId)
    }

    private fun speakWordWithId(word: InterlinearWord, utteranceId: String, bookId: String?) {
        val engine = tts ?: return

        val locale = localeForWord(word, bookId)
        val langResult = engine.setLanguage(locale)
        val useOriginal =
            langResult != TextToSpeech.LANG_MISSING_DATA &&
                langResult != TextToSpeech.LANG_NOT_SUPPORTED

        val text = if (useOriginal) {
            // Как в песочнице иврита: без никкуд — иначе многие движки TTS «ломают» произношение.
            if (shouldStripHebrewNiqqudForTts(locale, word, bookId)) {
                prepareHebrewForTts(word.original)
            } else {
                prepareOriginalForTts(word.original)
            }
        } else {
            engine.setLanguage(Locale.US)
            word.transliteration.ifBlank { word.original }
        }

        if (text.isBlank()) return

        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId,
        )
    }

    fun stop() {
        stopSequence()
        tts?.stop()
    }

    fun shutdown() {
        pendingSpeak = null
        pendingHebrewSandbox = null
        sequenceWords = null
        sequenceBookId = null
        arabicWordSequence = null
        arabicLetterSequence = null
        hebrewLetterSequence = null
        onArabicWordSequenceFullySpoken = null
        utteranceCompleteActions.clear()
        clearSequenceHighlight()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun localeForWord(word: InterlinearWord, bookId: String?): Locale {
        if (bookId != null) {
            when {
                BibleCanon.isOldTestament(bookId) -> return Locale.forLanguageTag("he-IL")
                BibleCanon.isNewTestament(bookId) -> return Locale.forLanguageTag("el-GR")
            }
        }
        when {
            word.strong?.startsWith("H") == true -> return Locale.forLanguageTag("he-IL")
            word.strong?.startsWith("G") == true -> return Locale.forLanguageTag("el-GR")
        }
        val o = word.original
        if (o.any { it in '\u0590'..'\u05FF' }) return Locale.forLanguageTag("he-IL")
        if (o.any { it in '\u0370'..'\u03FF' || it in '\u1F00'..'\u1FFF' }) {
            return Locale.forLanguageTag("el-GR")
        }
        return Locale.US
    }

    private fun shouldStripHebrewNiqqudForTts(
        locale: Locale,
        word: InterlinearWord,
        bookId: String?,
    ): Boolean {
        when (locale.language) {
            "he", "iw" -> return true
        }
        if (bookId != null && BibleCanon.isOldTestament(bookId)) return true
        if (word.strong?.startsWith("H") == true) return true
        return word.original.any { it in '\u0590'..'\u05FF' }
    }

    private fun prepareOriginalForTts(original: String): String {
        return buildString {
            for (ch in original) {
                when (ch) {
                    '\u05BE' -> append(' ')
                    '\u200c', '\u200d' -> { }
                    else -> append(ch)
                }
            }
        }.trim()
    }

    /** Озвучка справочного текста по-русски (алфавит, числа). */
    fun speakRussian(text: String, onFullySpoken: (() -> Unit)? = null) {
        if (text.isBlank() || !ready) return
        val engine = tts ?: return
        stopSequence()
        engine.stop()
        val ru = Locale.forLanguageTag("ru-RU")
        val r = engine.setLanguage(ru)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
        val uid = "ref_${System.nanoTime()}"
        onFullySpoken?.let { utteranceCompleteActions[uid] = it }
        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            uid,
        )
    }

    /**
     * Озвучка на арабском через системный движок TTS.
     * **Интернет при воспроизведении не нужен**, если в настройках телефона уже загружен
     * офлайн-голос для арабского (часто: «Настройки → Язык и ввод → Синтез речи → загрузить данные»).
     * Без голоса произнесётся короткая подсказка по-русски.
     */
    /**
     * Озвучка иврита (песочница подстрочника ВЗ). Нужен голос he/iw в настройках TTS.
     */
    fun speakHebrew(text: String) {
        val prepared = prepareHebrewForTts(text)
        if (prepared.isBlank()) return
        if (!ready) {
            pendingHebrewSandbox = text
            return
        }
        pendingHebrewSandbox = null
        val engine = tts ?: return
        stopSequence()
        engine.stop()
        if (!setHebrewLocale(engine)) {
            speakRussian(
                "Ивритский голос не установлен. В настройках телефона откройте синтез речи и загрузите данные для иврита.",
            )
            return
        }
        engine.speak(
            prepared,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "he_${System.nanoTime()}",
        )
    }

    /** Буквы ивритского слова после снятия никуд (для песочницы). */
    fun hebrewSandboxLetters(word: String): List<String> =
        extractHebrewLetterGraphemes(prepareHebrewForTts(word))

    /** Текст без огласовок (никуд) — «каркас» для отображения. */
    fun hebrewLetterSkeleton(word: String): String = prepareHebrewForTts(word)

    /** Есть ли в строке никуд / огласовки (комбинируемые знаки после NFD). */
    fun containsHebrewNiqqud(s: String): Boolean {
        if (s.isEmpty()) return false
        val nfd = Normalizer.normalize(s, Normalizer.Form.NFD)
        return nfd.any { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
    }

    fun speakHebrewLettersSequential(word: String) {
        if (!ready) return
        val letters = extractHebrewLetterGraphemes(prepareHebrewForTts(word))
        if (letters.isEmpty()) return
        val engine = tts ?: return
        engine.stop()
        sequenceWords = null
        sequenceBookId = null
        arabicWordSequence = null
        arabicLetterSequence = null
        hebrewLetterSequence = letters
        speakHebrewLetterIndex(0)
    }

    fun speakHebrewLettersSpaced(word: String) {
        val letters = extractHebrewLetterGraphemes(prepareHebrewForTts(word))
        if (letters.isEmpty()) return
        speakHebrew(letters.joinToString(" "))
    }

    private fun speakHebrewLetterIndex(index: Int) {
        val list = hebrewLetterSequence ?: return
        if (index >= list.size) {
            hebrewLetterSequence = null
            return
        }
        val engine = tts ?: return
        if (!setHebrewLocale(engine)) {
            hebrewLetterSequence = null
            speakRussian(
                "Ивритский голос не установлен. В настройках телефона откройте синтез речи и загрузите данные для иврита.",
            )
            return
        }
        val letter = list[index]
        if (letter.isBlank()) {
            mainHandler.post { speakHebrewLetterIndex(index + 1) }
            return
        }
        engine.speak(letter, TextToSpeech.QUEUE_FLUSH, null, "hlseq_$index")
    }

    private fun prepareHebrewForTts(s: String): String {
        val collapsed = buildString {
            for (ch in s) {
                when (ch) {
                    '\u05BE' -> append(' ')
                    '\u200c', '\u200d' -> Unit
                    else -> append(ch)
                }
            }
        }
        val stripped = Normalizer.normalize(collapsed, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
        return stripped.trim().replace(Regex("\\s+"), " ")
    }

    private fun extractHebrewLetterGraphemes(preparedWord: String): List<String> {
        if (preparedWord.isBlank()) return emptyList()
        val out = ArrayList<String>()
        var i = 0
        while (i < preparedWord.length) {
            val cp = preparedWord.codePointAt(i)
            val step = Character.charCount(cp)
            if (Character.isLetter(cp) && Character.UnicodeScript.of(cp) == Character.UnicodeScript.HEBREW) {
                out.add(preparedWord.substring(i, i + step))
            }
            i += step
        }
        return out
    }

    private fun setHebrewLocale(engine: TextToSpeech): Boolean {
        val locales = listOf(
            Locale.forLanguageTag("he-IL"),
            Locale.forLanguageTag("iw-IL"),
            Locale.forLanguageTag("he"),
        )
        for (loc in locales) {
            val r = engine.setLanguage(loc)
            if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                return true
            }
        }
        return false
    }

    fun speakArabic(text: String, onFullySpoken: (() -> Unit)? = null) {
        val prepared = prepareArabicForTts(text)
        if (prepared.isBlank() || !ready) return
        val engine = tts ?: return
        stopSequence()
        engine.stop()
        if (!setArabicLocale(engine)) {
            speakRussian(
                "Арабский голос не установлен. В настройках телефона откройте синтез речи и скачайте данные для арабского языка — тогда озвучка будет работать без интернета.",
            )
            return
        }
        val uid = "ar_${System.nanoTime()}"
        onFullySpoken?.let { utteranceCompleteActions[uid] = it }
        engine.speak(
            prepared,
            TextToSpeech.QUEUE_FLUSH,
            null,
            uid,
        )
    }

    /** Арабский текст аята Корана: убрать служебные символы, затем [speakArabic]. */
    fun speakQuranVerseArabic(arabic: String, onFullySpoken: (() -> Unit)? = null) {
        speakArabic(arabic, onFullySpoken)
    }

    /**
     * Озвучка аята по отдельным словам (разбивка по пробелам после [sanitizeQuranArabicForTts]).
     * Требует установленного арабского голоса TTS (как у [speakArabic]).
     */
    fun speakQuranVerseArabicWordByWord(arabic: String, onFullySpoken: (() -> Unit)? = null) {
        if (!ready) return
        val sanitized = prepareArabicForTts(arabic)
        val words = sanitized.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        val engine = tts ?: return
        engine.stop()
        sequenceWords = null
        sequenceBookId = null
        arabicLetterSequence = null
        hebrewLetterSequence = null
        utteranceCompleteActions.clear()
        onArabicWordSequenceFullySpoken = onFullySpoken
        if (words.isEmpty()) {
            finalizeArabicWordSequence(fireCallback = onFullySpoken != null)
            return
        }
        arabicWordSequence = words
        speakArabicWordIndex(0)
    }

    /**
     * Буквы/глифы арабского слова после [prepareArabicForTts] (для отображения в песочнице).
     */
    fun arabicSandboxLetters(word: String): List<String> =
        extractArabicLetterGraphemes(prepareArabicForTts(word))

    /** Символы без ташкиля и служебных знаков — «буквенный каркас» (как перед TTS). */
    fun arabicLetterSkeleton(word: String): String = prepareArabicForTts(word)

    /** Есть ли в строке арабские огласовки (харакаты / ташкиль). */
    fun containsArabicTashkeel(s: String): Boolean {
        for (ch in s) {
            if (isArabicTashkeel(ch)) return true
        }
        return false
    }

    /** Озвучить слово по буквам последовательно (арабский TTS). */
    fun speakArabicLettersSequential(word: String) {
        if (!ready) return
        val letters = extractArabicLetterGraphemes(prepareArabicForTts(word))
        if (letters.isEmpty()) return
        val engine = tts ?: return
        engine.stop()
        sequenceWords = null
        sequenceBookId = null
        arabicWordSequence = null
        hebrewLetterSequence = null
        arabicLetterSequence = letters
        speakArabicLetterIndex(0)
    }

    /**
     * Одна фраза TTS: буквы через пробел (часто произносятся по отдельности в одном вызове).
     */
    fun speakArabicLettersSpaced(word: String) {
        val letters = extractArabicLetterGraphemes(prepareArabicForTts(word))
        if (letters.isEmpty()) return
        speakArabic(letters.joinToString(" "))
    }

    private fun speakArabicLetterIndex(index: Int) {
        val list = arabicLetterSequence ?: return
        if (index >= list.size) {
            arabicLetterSequence = null
            return
        }
        val engine = tts ?: return
        if (!setArabicLocale(engine)) {
            arabicLetterSequence = null
            speakRussian(
                "Арабский голос не установлен. В настройках телефона откройте синтез речи и скачайте данные для арабского языка — тогда озвучка будет работать без интернета.",
            )
            return
        }
        val letter = list[index]
        if (letter.isBlank()) {
            mainHandler.post { speakArabicLetterIndex(index + 1) }
            return
        }
        engine.speak(letter, TextToSpeech.QUEUE_FLUSH, null, "lseq_$index")
    }

    private fun speakArabicWordIndex(index: Int) {
        val list = arabicWordSequence ?: return
        if (index >= list.size) {
            arabicWordSequence = null
            finalizeArabicWordSequence(fireCallback = true)
            return
        }
        val engine = tts ?: return
        if (!setArabicLocale(engine)) {
            arabicWordSequence = null
            finalizeArabicWordSequence(fireCallback = true)
            speakRussian(
                "Арабский голос не установлен. В настройках телефона откройте синтез речи и скачайте данные для арабского языка — тогда озвучка будет работать без интернета.",
            )
            return
        }
        val word = list[index]
        if (word.isBlank()) {
            mainHandler.post { speakArabicWordIndex(index + 1) }
            return
        }
        engine.speak(word, TextToSpeech.QUEUE_FLUSH, null, "qseq_$index")
    }

    /**
     * Ташкиль и служебные символы: полностью огласованный текст Корана часто ломает или глушит
     * системный TTS после обновлений движка — оставляем буквы без комбинируемых огласовок.
     */
    private fun prepareArabicForTts(s: String): String {
        val collapsed = buildString {
            for (ch in s) {
                when (ch) {
                    '\u0640', '\u200c', '\u200d', '\u2060', '\ufeff' -> Unit
                    else -> if (!isArabicTashkeel(ch)) append(ch)
                }
            }
        }
        return collapsed.trim().replace(Regex("\\s+"), " ")
    }

    private fun isArabicTashkeel(ch: Char): Boolean {
        val c = ch.code
        return c in 0x0610..0x061A ||
            c in 0x064B..0x065F ||
            c == 0x0670 ||
            c in 0x06D6..0x06ED
    }

    /** Буквенные глифы арабской письменности (после удаления ташкиля). */
    private fun extractArabicLetterGraphemes(preparedWord: String): List<String> {
        if (preparedWord.isBlank()) return emptyList()
        val out = ArrayList<String>()
        var i = 0
        while (i < preparedWord.length) {
            val cp = preparedWord.codePointAt(i)
            val step = Character.charCount(cp)
            if (Character.isLetter(cp) && Character.UnicodeScript.of(cp) == Character.UnicodeScript.ARABIC) {
                out.add(preparedWord.substring(i, i + step))
            }
            i += step
        }
        return out
    }

    private fun setArabicLocale(engine: TextToSpeech): Boolean {
        val locales = listOf(
            Locale.forLanguageTag("ar"),
            Locale.forLanguageTag("ar-SA"),
            Locale.forLanguageTag("ar-EG"),
            Locale.forLanguageTag("ar-AE"),
        )
        for (loc in locales) {
            val r = engine.setLanguage(loc)
            if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                return true
            }
        }
        return false
    }
}
