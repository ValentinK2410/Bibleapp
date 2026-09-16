package com.example.bible.ui

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.AiChatNeuralSpeechPlayer
import com.example.bible.data.AiChatTtsController
import com.example.bible.data.AiChatTtsEngine
import com.example.bible.data.AiChatTtsSettings
import com.example.bible.data.AiChatTtsVoiceOption
import com.example.bible.data.applyAiChatVoice
import com.example.bible.data.applyTtsVoiceForTranslation
import com.example.bible.data.BibleTtsController
import com.example.bible.data.listAiChatRussianVoices
import com.example.bible.data.resolveTtsEnginePackage
import com.example.bible.data.SaluteSpeechClient
import com.example.bible.data.speakAiChatChunk
import com.example.bible.data.BibleUserAudio
import com.example.bible.data.BibleUserImage
import com.example.bible.data.BibleUserVideo
import com.example.bible.data.BibleCanon
import com.example.bible.data.BibleDictionary
import com.example.bible.data.CrossReferences
import com.example.bible.data.DictionaryEntry
import com.example.bible.data.NoteScriptureLinks
import com.example.bible.data.ScriptureAudioPlayMode
import com.example.bible.data.TranslationId
import com.example.bible.data.UserNote
import com.example.bible.data.VerseAttachment
import com.example.bible.data.VerseAttachmentStore
import com.example.bible.data.VerseComparison
import com.example.bible.data.VerseRef
import com.example.bible.data.matchesVerseLocation
import com.example.bible.data.previewText

data class VerseActionTarget(
    val ref: VerseRef,
    val verseText: String,
    val bookName: String,
)

data class VerseRangeCopyRequest(
    val audioLink: Boolean,
    val target: VerseActionTarget,
    val chapterVerseCount: Int,
    val chapterVerseTexts: Map<Int, String>,
    val translation: TranslationId,
)

private fun applyVerseRangeCopy(
    context: Context,
    snap: VerseRangeCopyRequest,
    rawInput: String,
) {
    val raw = rawInput.filter { it.isDigit() || it in ",-*" }.trim()
    val start = snap.target.ref.verse
    val maxV = snap.chapterVerseCount.coerceAtLeast(start)
    val spec = when {
        raw.contains(',') || raw.contains('*') || raw.contains('-') ||
            raw.contains('–') || raw.contains('—') -> raw
        else -> {
            val end = raw.toIntOrNull() ?: start
            "$start-${end.coerceIn(start, maxV)}"
        }
    }
    val verses = VerseShareText.verseNumbersFromRangeSpec(start, raw, maxV)
    val texts = snap.chapterVerseTexts.toMutableMap()
    if (snap.target.verseText.isNotBlank()) {
        texts.putIfAbsent(snap.target.ref.verse, snap.target.verseText)
    }
    if (snap.audioLink) {
        val mode = when {
            spec.contains('*') || spec.contains(',') -> ScriptureAudioPlayMode.SEGMENTS
            spec.contains('-') -> ScriptureAudioPlayMode.RANGE
            else -> ScriptureAudioPlayMode.VERSE
        }
        val link = NoteScriptureLinks.formatAudioLinkWithVerseTexts(
            bookId = snap.target.ref.bookId,
            chapter = snap.target.ref.chapter,
            verses = verses,
            mode = mode,
            translation = snap.translation,
            verseTextsByNumber = texts,
            chapterVerseCount = snap.chapterVerseCount,
            segmentSpec = spec,
        )
        if (link.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("audio_link", link))
        Toast.makeText(context, R.string.verse_audio_link_copied, Toast.LENGTH_SHORT).show()
    } else if (verses.isNotEmpty()) {
        copyVersesToClipboard(
            context = context,
            bookName = snap.target.bookName,
            chapter = snap.target.ref.chapter,
            verseNumbers = verses,
            verseTextsByNumber = texts,
        )
    }
}

@Composable
fun VerseRangeCopyDialogHost(
    request: VerseRangeCopyRequest?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val dialogKey = request?.let { "${it.target.ref.toKey()}:${it.audioLink}" }
    DisposableEffect(dialogKey) {
        val snap = request ?: return@DisposableEffect onDispose { }
        val density = context.resources.displayMetrics.density
        val padPx = (16 * density).toInt()
        val title = context.getString(
            if (snap.audioLink) {
                R.string.verse_copy_audio_link_range_title
            } else {
                R.string.verse_copy_range_title
            },
        )
        val hintText = context.getString(
            R.string.verse_copy_audio_link_range_hint,
            snap.target.ref.verse,
            snap.chapterVerseCount.coerceAtLeast(snap.target.ref.verse),
        )
        val defaultEnd = (snap.target.ref.verse + 1)
            .coerceAtMost(snap.chapterVerseCount)
            .toString()
        val input = EditText(context).apply {
            setText(defaultEnd)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT
            setHint(context.getString(R.string.verse_copy_audio_link_range_example))
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padPx, padPx, padPx, 0)
            addView(
                TextView(context).apply {
                    text = hintText
                },
            )
            addView(
                input,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (8 * density).toInt() },
            )
        }
        var finished = false
        var dialog: AlertDialog? = null
        fun finish() {
            if (finished) return
            finished = true
            onDismiss()
        }
        val handler = Handler(Looper.getMainLooper())
        val showRunnable = Runnable {
            if (finished) return@Runnable
            dialog = AlertDialog.Builder(context)
                .setTitle(title)
                .setView(container)
                .setNegativeButton(context.getString(R.string.timemark_close)) { d, _ ->
                    d.dismiss()
                    finish()
                }
                .setPositiveButton(context.getString(R.string.verse_copy_audio_link_copy)) { d, _ ->
                    applyVerseRangeCopy(context, snap, input.text.toString())
                    d.dismiss()
                    finish()
                }
                .setOnCancelListener { finish() }
                .create()
                .also { built ->
                    built.window?.setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE,
                    )
                    built.setCanceledOnTouchOutside(false)
                    built.show()
                }
        }
        handler.postDelayed(showRunnable, 350)
        onDispose {
            finished = true
            handler.removeCallbacks(showRunnable)
            dialog?.dismiss()
        }
    }
}

/** Озвучка стиха и комментариев: воспроизведение и остановка. */
data class BibleVoiceTts(
    val speak: (String) -> Unit,
    val stop: () -> Unit,
)

/** Озвучка сравнения переводов по списку. */
data class BibleComparisonVoiceTts(
    val speak: (List<VerseComparison>) -> Unit,
    val stop: () -> Unit,
)

/** Озвучка ответов ИИ с выбором голоса и интонации. */
data class AiChatSpeechHandle(
    val speak: (String) -> Unit,
    val stop: () -> Unit,
    val preview: (String) -> Unit,
    val neuralVoices: List<AiChatTtsVoiceOption>,
    val systemVoices: List<AiChatTtsVoiceOption>,
) {
    /** @deprecated используйте [neuralVoices] или [systemVoices] по движку */
    val voices: List<AiChatTtsVoiceOption> get() = systemVoices
}

/**
 * Озвучка ответов ИИ (GigaChat, DeepSeek).
 * По умолчанию — нейросеть SaluteSpeech (естественный голос); запасной вариант — системный TTS.
 */
@Composable
fun rememberAiChatTextToSpeech(
    saluteAuthKey: String = "",
    gigaChatAuthKey: String = "",
    saluteScope: String = SaluteSpeechClient.SCOPE_PERS,
): AiChatSpeechHandle {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var systemVoices by remember { mutableStateOf(emptyList<AiChatTtsVoiceOption>()) }
    val ttsUser by BibleTtsController.settings.collectAsStateWithLifecycle()
    val aiTts by AiChatTtsController.settings.collectAsStateWithLifecycle()
    val app = context.applicationContext
    val engineKey = remember(ttsUser.enginePackage) {
        resolveTtsEnginePackage(app, ttsUser)
    }

    fun speakSystem(text: String) {
        tts?.let { engine ->
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return
            val volume = aiTts.intonation.modifiers().volume
            val chunks = splitTtsChunks(trimmed)
            chunks.forEachIndexed { index, chunk ->
                val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val utteranceId = "ai_chat_${System.nanoTime()}_$index"
                speakAiChatChunk(engine, chunk, queueMode, utteranceId, volume)
            }
        }
    }

    fun applyVoice(engine: TextToSpeech) {
        applyAiChatVoice(engine, ttsUser, aiTts.copy(engine = AiChatTtsEngine.SYSTEM))
        systemVoices = listAiChatRussianVoices(engine)
    }

    val neuralPlayer = remember(saluteAuthKey, gigaChatAuthKey, saluteScope) {
        AiChatNeuralSpeechPlayer(
            appContext = app,
            authKeyProvider = {
                SaluteSpeechClient.resolveAuthKey(saluteAuthKey, gigaChatAuthKey)
            },
            scopeProvider = { saluteScope },
            settingsProvider = { AiChatTtsController.settings.value },
            systemFallback = { text -> speakSystem(text) },
            systemStop = { tts?.stop() },
        )
    }

    DisposableEffect(engineKey) {
        var engine: TextToSpeech? = null
        val init = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.let { e ->
                    applyVoice(e)
                    tts = e
                }
            }
        }
        engine = if (engineKey.isNotEmpty()) {
            TextToSpeech(app, init, engineKey)
        } else {
            TextToSpeech(app, init)
        }
        onDispose {
            runCatching {
                engine?.stop()
                engine?.shutdown()
            }
            tts = null
            systemVoices = emptyList()
        }
    }

    DisposableEffect(neuralPlayer) {
        onDispose { neuralPlayer.release() }
    }

    LaunchedEffect(ttsUser, aiTts, tts) {
        val e = tts ?: return@LaunchedEffect
        applyVoice(e)
    }

    fun dispatchSpeak(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        when (AiChatTtsController.settings.value.engine) {
            AiChatTtsEngine.NEURAL -> neuralPlayer.speak(trimmed)
            AiChatTtsEngine.SYSTEM -> speakSystem(trimmed)
        }
    }

    return AiChatSpeechHandle(
        speak = ::dispatchSpeak,
        stop = {
            neuralPlayer.stop()
            tts?.stop()
        },
        preview = { sample ->
            val text = sample.trim().ifBlank { "Здравствуйте! Это проверка голоса." }
            dispatchSpeak(text)
        },
        neuralVoices = SaluteSpeechClient.neuralVoices,
        systemVoices = systemVoices,
    )
}

@Composable
fun rememberVerseTextToSpeech(translation: TranslationId): BibleVoiceTts {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val ttsUser by BibleTtsController.settings.collectAsStateWithLifecycle()
    val engineKey = ttsUser.enginePackage.trim()

    DisposableEffect(translation, engineKey) {
        var engine: TextToSpeech? = null
        val app = context.applicationContext
        val init = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.let { e ->
                    applyTtsVoiceForTranslation(e, translation, BibleTtsController.settings.value)
                    tts = e
                }
            }
        }
        engine = if (engineKey.isNotEmpty()) {
            TextToSpeech(app, init, engineKey)
        } else {
            TextToSpeech(app, init)
        }
        onDispose {
            runCatching {
                engine?.stop()
                engine?.shutdown()
            }
            tts = null
        }
    }

    LaunchedEffect(translation, ttsUser, tts) {
        val e = tts ?: return@LaunchedEffect
        applyTtsVoiceForTranslation(e, translation, ttsUser)
    }

    return remember(translation) {
        BibleVoiceTts(
            speak = { text: String ->
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            },
            stop = { tts?.stop() },
        )
    }
}

/**
 * Русская озвучка для комментариев и сравнения переводов; длинный текст режется на фрагменты для TTS.
 * Для РБО и Кулаковых — тот же выбор голоса, что и при чтении стихов ([rememberVerseTextToSpeech]).
 */
@Composable
fun rememberStudyTextToSpeech(translation: TranslationId): BibleVoiceTts {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val ttsUser by BibleTtsController.settings.collectAsStateWithLifecycle()
    val engineKey = ttsUser.enginePackage.trim()

    DisposableEffect(translation, engineKey) {
        var engine: TextToSpeech? = null
        val app = context.applicationContext
        val init = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.let { e ->
                    applyTtsVoiceForTranslation(e, translation, BibleTtsController.settings.value)
                    tts = e
                }
            }
        }
        engine = if (engineKey.isNotEmpty()) {
            TextToSpeech(app, init, engineKey)
        } else {
            TextToSpeech(app, init)
        }
        onDispose {
            runCatching {
                engine?.stop()
                engine?.shutdown()
            }
            tts = null
        }
    }

    LaunchedEffect(translation, ttsUser, tts) {
        val e = tts ?: return@LaunchedEffect
        applyTtsVoiceForTranslation(e, translation, ttsUser)
    }

    return remember(translation) {
        BibleVoiceTts(
            speak = { text: String ->
                tts?.let { engine ->
                    val trimmed = text.trim()
                    if (trimmed.isEmpty()) return@let
                    val chunks = splitTtsChunks(trimmed)
                    chunks.forEachIndexed { index, chunk ->
                        val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                        val utteranceId = "study_${System.nanoTime()}_$index"
                        engine.speak(chunk, queueMode, null, utteranceId)
                    }
                }
            },
            stop = { tts?.stop() },
        )
    }
}

/**
 * Озвучка списка сравнения переводов: для строк с названием РБО и Кулаковых подбираются
 * те же голоса, что и при чтении этих переводов в тексте.
 */
@Composable
fun rememberComparisonSpeech(): BibleComparisonVoiceTts {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val ttsUser by BibleTtsController.settings.collectAsStateWithLifecycle()
    val engineKey = ttsUser.enginePackage.trim()

    DisposableEffect(engineKey) {
        var engine: TextToSpeech? = null
        val app = context.applicationContext
        val init = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.let { e ->
                    applyTtsVoiceForTranslation(e, TranslationId.SYNODAL, BibleTtsController.settings.value)
                    tts = e
                }
            }
        }
        engine = if (engineKey.isNotEmpty()) {
            TextToSpeech(app, init, engineKey)
        } else {
            TextToSpeech(app, init)
        }
        onDispose {
            runCatching {
                engine?.stop()
                engine?.shutdown()
            }
            tts = null
        }
    }

    LaunchedEffect(ttsUser, tts) {
        val e = tts ?: return@LaunchedEffect
        applyTtsVoiceForTranslation(e, TranslationId.SYNODAL, ttsUser)
    }

    return remember {
        BibleComparisonVoiceTts(
            speak = { comparisons: List<VerseComparison> ->
                tts?.let { engine ->
                    if (comparisons.isEmpty()) return@let
                    engine.stop()
                    var firstChunk = true
                    for (vc in comparisons) {
                        val tid = translationIdFromComparisonLabel(vc.translationName)
                        applyTtsVoiceForTranslation(engine, tid, BibleTtsController.settings.value)
                        val piece = "${vc.translationName}. ${vc.text.trim()}"
                        val chunks = splitTtsChunks(piece)
                        for (chunk in chunks) {
                            val mode = if (firstChunk) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                            val utteranceId = "cmp_${System.nanoTime()}_${chunk.hashCode()}"
                            engine.speak(chunk, mode, null, utteranceId)
                            firstChunk = false
                        }
                    }
                }
            },
            stop = { tts?.stop() },
        )
    }
}

/** По подписи в списке сравнения (как на studybible) определяем перевод для голоса. */
internal fun translationIdFromComparisonLabel(name: String): TranslationId {
    val n = name.lowercase()
    return when {
        n.contains("рбо") -> TranslationId.RBO
        n.contains("кулак") || n.contains("bti") -> TranslationId.BTI
        else -> TranslationId.SYNODAL
    }
}

private fun splitTtsChunks(text: String, maxSize: Int = 3500): List<String> {
    if (text.length <= maxSize) return listOf(text)
    val result = mutableListOf<String>()
    var remaining = text
    while (remaining.isNotEmpty()) {
        if (remaining.length <= maxSize) {
            result.add(remaining)
            break
        }
        var cut = remaining.lastIndexOf('\n', maxSize).takeIf { it > maxSize / 3 }
            ?: remaining.lastIndexOf('.', maxSize).takeIf { it > maxSize / 3 }
            ?: remaining.lastIndexOf(' ', maxSize).takeIf { it > maxSize / 4 }
        if (cut == null || cut <= 0) cut = maxSize
        result.add(remaining.substring(0, cut).trim())
        remaining = remaining.substring(cut).trimStart()
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VerseActionsBottomSheet(
    target: VerseActionTarget?,
    bookmarkKeys: Set<String>,
    onToggleBookmark: (VerseRef) -> Unit,
    onDismiss: () -> Unit,
    speak: (String) -> Unit,
    onStopSpeech: () -> Unit = {},
    onPlayAudio: ((VerseRef, () -> Unit) -> Unit)? = null,
    /** Озвучка главы с таймкода выбранного стиха (если для главы есть проект таймкодов). */
    onPlayTimemarkVerseAudio: ((VerseRef) -> Unit)? = null,
    onOpenCommentary: (VerseRef) -> Unit,
    onAskDeepSeek: ((VerseActionTarget) -> Unit)? = null,
    onAskGigaChat: ((VerseActionTarget) -> Unit)? = null,
    onNavigateToVerse: ((String, Int, Int) -> Unit)? = null,
    onDictionaryWord: ((String) -> Unit)? = null,
    onPauseMainAudioForAttachment: () -> Unit = {},
    /** Картинки из «Медиа → Картинки» для вложения к стиху. */
    mediaLibraryImages: List<BibleUserImage> = emptyList(),
    mediaLibraryVideos: List<BibleUserVideo> = emptyList(),
    mediaLibraryAudios: List<BibleUserAudio> = emptyList(),
    /** Личные заметки (для пункта «Открыть» к этому стиху). */
    userNotes: List<UserNote> = emptyList(),
    /** Создать новую заметку с привязкой к стиху. */
    onCreateNoteForVerse: ((VerseActionTarget) -> Unit)? = null,
    /** Открыть существующую заметку по id. */
    onOpenExistingVerseNote: ((String) -> Unit)? = null,
    /** Режим выбора нескольких стихов по номерам (начать с текущего). */
    onEnterMultiVerseSelect: ((Int) -> Unit)? = null,
    /** Песочница иврита: весь стих (подстрочник Винокурова, ВЗ). */
    onOpenInterlinearHebrewSandboxWholeVerse: ((VerseRef) -> Unit)? = null,
    translation: TranslationId = TranslationId.SYNODAL,
    chapterVerseCount: Int = 0,
    chapterVerseTexts: Map<Int, String> = emptyMap(),
    onCopyVerseRange: ((VerseRangeCopyRequest) -> Unit)? = null,
) {
    val context = LocalContext.current
    var previewAttachment by remember { mutableStateOf<VerseAttachment?>(null) }
    previewAttachment?.let { att ->
        AttachmentPreviewDialog(
            attachment = att,
            onDismiss = { previewAttachment = null },
            onPauseMainAudio = onPauseMainAudioForAttachment,
        )
    }

    if (target == null) return

    fun copyAudioLink(
        mode: ScriptureAudioPlayMode,
        verses: Set<Int>,
        segmentSpec: String? = null,
    ) {
        val texts = chapterVerseTexts.toMutableMap()
        if (target.verseText.isNotBlank()) {
            texts.putIfAbsent(target.ref.verse, target.verseText)
        }
        val link = NoteScriptureLinks.formatAudioLinkWithVerseTexts(
            bookId = target.ref.bookId,
            chapter = target.ref.chapter,
            verses = verses,
            mode = mode,
            translation = translation,
            verseTextsByNumber = texts,
            chapterVerseCount = chapterVerseCount,
            segmentSpec = segmentSpec,
        )
        if (link.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("audio_link", link))
        Toast.makeText(context, R.string.verse_audio_link_copied, Toast.LENGTH_SHORT).show()
    }

    fun verseTextsIncludingTarget(): Map<Int, String> {
        val texts = chapterVerseTexts.toMutableMap()
        if (target.verseText.isNotBlank()) {
            texts.putIfAbsent(target.ref.verse, target.verseText)
        }
        return texts
    }

    fun copyTextVerses(verseNumbers: Set<Int>) {
        if (verseNumbers.isEmpty()) return
        copyVersesToClipboard(
            context = context,
            bookName = target.bookName,
            chapter = target.ref.chapter,
            verseNumbers = verseNumbers,
            verseTextsByNumber = verseTextsIncludingTarget(),
        )
    }

    fun openRangeCopy(audioLink: Boolean) {
        onCopyVerseRange?.invoke(
            VerseRangeCopyRequest(
                audioLink = audioLink,
                target = target,
                chapterVerseCount = chapterVerseCount,
                chapterVerseTexts = chapterVerseTexts,
                translation = translation,
            ),
        )
        onDismiss()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isBookmarked = target.ref.toKey() in bookmarkKeys
    val attachmentStore = remember { VerseAttachmentStore.get(context) }
    var attachListVersion by remember { mutableIntStateOf(0) }
    val attachments = remember(target.ref, attachListVersion) {
        attachmentStore.listFor(target.ref)
    }
    val pickAttachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                attachmentStore.addFromUri(target.ref, uri)
                attachListVersion++
            }.onFailure { e ->
                Toast.makeText(context, e.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showAttachSourceSheet by remember { mutableStateOf(false) }
    var showMediaLibraryPicker by remember { mutableStateOf(false) }

    if (showAttachSourceSheet) {
        AttachFileSourceSheet(
            onDismiss = { showAttachSourceSheet = false },
            onPickDeviceFile = { pickAttachmentLauncher.launch("*/*") },
            onPickFromMediaLibrary = {
                showMediaLibraryPicker = true
            },
        )
    }
    if (showMediaLibraryPicker) {
        MediaLibraryUnionPickerSheet(
            images = mediaLibraryImages,
            videos = mediaLibraryVideos,
            audios = mediaLibraryAudios,
            onDismiss = { showMediaLibraryPicker = false },
            onSelect = { pick ->
                runCatching {
                    when (pick) {
                        is MediaLibraryPick.Image ->
                            attachmentStore.addFromMediaLibrary(target.ref, pick.image)
                        is MediaLibraryPick.Video ->
                            attachmentStore.addFromVideoLibrary(target.ref, pick.video)
                        is MediaLibraryPick.Audio ->
                            attachmentStore.addFromAudioLibrary(target.ref, pick.audio)
                    }
                    attachListVersion++
                }.onFailure { e ->
                    Toast.makeText(context, e.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    val shareText = remember(target, chapterVerseTexts) {
        VerseShareText.format(
            bookName = target.bookName,
            chapter = target.ref.chapter,
            verseNumbers = setOf(target.ref.verse),
            verseTextsByNumber = verseTextsIncludingTarget(),
        )
    }
    val notesAtVerse = remember(target.ref, userNotes) {
        userNotes
            .filter { it.matchesVerseLocation(target.ref) }
            .sortedByDescending { it.updatedAt }
    }
    val primaryNote = notesAtVerse.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(
                    R.string.verse_actions_title,
                    target.bookName,
                    target.ref.chapter,
                    target.ref.verse,
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(
                            if (isBookmarked) R.string.verse_action_remove_bookmark
                            else R.string.verse_action_add_bookmark,
                        ),
                    )
                },
                leadingContent = {
                    Icon(
                        if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = null,
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onToggleBookmark(target.ref)
                        onDismiss()
                    },
            )
            if (onCreateNoteForVerse != null || onOpenExistingVerseNote != null) {
                if (primaryNote != null && onOpenExistingVerseNote != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.verse_note_open)) },
                        supportingContent = {
                            Column {
                                val sub = primaryNote.title.trim().ifBlank { primaryNote.previewText() }.trim()
                                if (sub.isNotEmpty()) {
                                    Text(sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (notesAtVerse.size > 1) {
                                    Text(stringResource(R.string.verse_note_more, notesAtVerse.size - 1))
                                }
                            }
                        },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.StickyNote2, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenExistingVerseNote(primaryNote.id)
                                onDismiss()
                            },
                    )
                }
                if (onCreateNoteForVerse != null) {
                    ListItem(
                        headlineContent = {
                            Text(
                                if (primaryNote != null) {
                                    stringResource(R.string.verse_note_new)
                                } else {
                                    stringResource(R.string.verse_action_note)
                                },
                            )
                        },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCreateNoteForVerse(target)
                                onDismiss()
                            },
                    )
                }
            }
            if (onPlayTimemarkVerseAudio != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.verse_action_play_narration)) },
                    leadingContent = {
                        Icon(Icons.Default.Headphones, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onStopSpeech()
                            onPlayTimemarkVerseAudio(target.ref)
                            onDismiss()
                        },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.verse_action_speak)) },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (onPlayAudio != null) {
                            onPlayAudio(target.ref) { speak(target.verseText) }
                        } else {
                            speak(target.verseText)
                        }
                        onDismiss()
                    },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.audio_stop)) },
                leadingContent = {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onStopSpeech()
                    },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.verse_action_copy)) },
                leadingContent = {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        copyTextVerses(setOf(target.ref.verse))
                        onDismiss()
                    },
            )
            if (onEnterMultiVerseSelect != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.verse_action_select_multiple)) },
                    leadingContent = {
                        Icon(Icons.Default.Checklist, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onEnterMultiVerseSelect(target.ref.verse)
                            onDismiss()
                        },
                )
            }
            if (chapterVerseCount > target.ref.verse) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.verse_copy_range)) },
                    leadingContent = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            openRangeCopy(audioLink = false)
                        },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.verse_copy_to_end)) },
                    leadingContent = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val end = chapterVerseCount.coerceAtLeast(target.ref.verse)
                            copyTextVerses((target.ref.verse..end).toSet())
                            onDismiss()
                        },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.verse_copy_audio_link_verse)) },
                leadingContent = {
                    Icon(Icons.Default.Headphones, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        copyAudioLink(
                            ScriptureAudioPlayMode.VERSE,
                            setOf(target.ref.verse),
                        )
                        onDismiss()
                    },
            )
            if (chapterVerseCount > target.ref.verse) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.verse_copy_audio_link_range)) },
                    leadingContent = {
                        Icon(Icons.Default.Headphones, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            openRangeCopy(audioLink = true)
                        },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.verse_copy_audio_link_to_end)) },
                    leadingContent = {
                        Icon(Icons.Default.Headphones, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            copyAudioLink(
                                ScriptureAudioPlayMode.TO_CHAPTER_END,
                                setOf(target.ref.verse),
                            )
                            onDismiss()
                        },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.verse_action_share)) },
                leadingContent = {
                    Icon(Icons.Default.Share, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                        onDismiss()
                    },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.verse_action_commentary)) },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onOpenCommentary(target.ref)
                        onDismiss()
                    },
            )
            if (onAskDeepSeek != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.verse_action_deepseek)) },
                    leadingContent = {
                        Icon(Icons.Filled.Psychology, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val t = target
                            onAskDeepSeek(t)
                            onDismiss()
                        },
                )
            }
            if (onAskGigaChat != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.verse_action_gigachat_reflect)) },
                    leadingContent = {
                        Icon(Icons.Filled.AutoStories, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val t = target
                            onAskGigaChat(t)
                            onDismiss()
                        },
                )
            }
            if (onOpenInterlinearHebrewSandboxWholeVerse != null &&
                target.ref.translation == TranslationId.INTERLINEAR &&
                BibleCanon.isOldTestament(target.ref.bookId)
            ) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.verse_action_hebrew_sandbox_whole))
                    },
                    leadingContent = {
                        Icon(Icons.Filled.School, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOpenInterlinearHebrewSandboxWholeVerse(target.ref)
                            onDismiss()
                        },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.verse_attach_file)) },
                leadingContent = {
                    Icon(Icons.Filled.AttachFile, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showAttachSourceSheet = true
                    },
            )
            if (attachments.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = stringResource(R.string.verse_attachments_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                attachments.forEach { att ->
                    ListItem(
                        headlineContent = { Text(att.displayName) },
                        supportingContent = {
                            Text(
                                att.mimeType,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.AttachFile, contentDescription = null)
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    attachmentStore.remove(target.ref, att.id)
                                    attachListVersion++
                                },
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.attachment_delete),
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                previewAttachment = att
                            },
                    )
                }
            }

            if (onDictionaryWord != null) {
                val dictWords = remember(target.verseText) {
                    target.verseText.split(Regex("[\\s,.;:!?\"'«»()\\[\\]—–-]+"))
                        .filter { it.length >= 3 }
                        .mapNotNull { w -> BibleDictionary.lookup(w)?.let { w to it } }
                        .distinctBy { it.second.word }
                }
                if (dictWords.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    Text(
                        text = "Справочник",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        dictWords.forEach { (_, entry) ->
                            AssistChip(
                                onClick = {
                                    onDictionaryWord(entry.word)
                                    onDismiss()
                                },
                                label = { Text(entry.word) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Book,
                                        contentDescription = null,
                                        modifier = Modifier.padding(0.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            val crossRefs = remember(target.ref) {
                CrossReferences.forVerse(target.ref.bookId, target.ref.chapter, target.ref.verse)
            }
            if (crossRefs.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = "Перекрёстные ссылки",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                crossRefs.forEach { ref ->
                    ListItem(
                        headlineContent = { Text(ref.label()) },
                        leadingContent = {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onNavigateToVerse?.invoke(ref.bookId, ref.chapter, ref.verse)
                                onDismiss()
                            },
                    )
                }
            }
        }
    }
}
