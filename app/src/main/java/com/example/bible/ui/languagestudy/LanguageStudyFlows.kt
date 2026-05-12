package com.example.bible.ui.languagestudy

import android.app.Application
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bible.data.BiblePreferences
import com.example.bible.data.db.LangVocabWordEntity
import com.example.bible.R
import com.example.bible.ui.LanguageStudyCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

private enum class LangStudyTab { HOME, SESSION, DICT, PACK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageStudyLanguageFlowScreen(code: LanguageStudyCode, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val lsVm: LanguageStudyViewModel = viewModel(factory = LanguageStudyViewModel.Factory(app))
    val langTag = code.routeArg
    var tab by remember { mutableStateOf(LangStudyTab.HOME) }
    LaunchedEffect(langTag) { lsVm.prepareLanguage(langTag) }
    val importNote by lsVm.importMessage.collectAsStateWithLifecycle()
    if (importNote != null) {
        AlertDialog(
            onDismissRequest = { lsVm.clearImportMessage() },
            confirmButton = { TextButton(onClick = { lsVm.clearImportMessage() }) { Text("OK") } },
            title = { Text(stringResource(R.string.language_study_import_result_title)) },
            text = { Text(importNote.orEmpty()) },
        )
    }
    val sb = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(sb.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (tab) {
                            LangStudyTab.HOME -> stringResource(code.titleRes)
                            LangStudyTab.SESSION -> stringResource(R.string.language_study_tab_session)
                            LangStudyTab.DICT -> stringResource(R.string.language_study_tab_dictionary)
                            LangStudyTab.PACK -> stringResource(R.string.language_study_tab_pack)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (tab) {
                                LangStudyTab.HOME -> onBack()
                                else -> tab = LangStudyTab.HOME
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = sb,
            )
        },
    ) { padding ->
        when (tab) {
            LangStudyTab.HOME ->
                LS_Home(code, Modifier.padding(padding), lsVm, { tab = LangStudyTab.SESSION }, { tab = LangStudyTab.DICT }, { tab = LangStudyTab.PACK })
            LangStudyTab.SESSION -> LS_Session(Modifier.padding(padding), langTag, lsVm)
            LangStudyTab.DICT -> LS_Dict(Modifier.padding(padding), langTag, lsVm)
            LangStudyTab.PACK -> LS_Pack(Modifier.padding(padding), code, lsVm)
        }
    }
}

@Composable
private fun LS_Home(
    code: LanguageStudyCode,
    modifier: Modifier,
    lsVm: LanguageStudyViewModel,
    openSession: () -> Unit,
    openDict: () -> Unit,
    openPack: () -> Unit,
) {
    val overview by lsVm.overview.collectAsStateWithLifecycle()
    LaunchedEffect(code.routeArg) { lsVm.refreshOverview(code.routeArg) }
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text(stringResource(R.string.language_study_home_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.language_study_stats_fmt, overview.totalWords, overview.dueCount), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        LsHubCard(stringResource(R.string.language_study_tab_session), stringResource(R.string.language_study_hub_session_hint), Icons.Filled.School, openSession)
        Spacer(Modifier.height(8.dp))
        LsHubCard(stringResource(R.string.language_study_tab_dictionary), stringResource(R.string.language_study_hub_dict_hint), Icons.Filled.LibraryBooks, openDict)
        Spacer(Modifier.height(8.dp))
        LsHubCard(stringResource(R.string.language_study_tab_pack), stringResource(R.string.language_study_hub_pack_hint), Icons.Filled.UploadFile, openPack)
        Spacer(Modifier.weight(1f))
        Button(onClick = { lsVm.refreshOverview(code.routeArg) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text(stringResource(R.string.language_study_refresh_stats))
        }
        Text(stringResource(R.string.language_study_srs_hint_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LsHubCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ручное нажатие или цикл после паузы, если включён повтор на сессии. */
private fun lsSpeakWithOptionalRepeat(
    scope: CoroutineScope,
    repeatLoopEnabled: Boolean,
    pauseMs: () -> Long,
    repeatStillEnabled: () -> Boolean,
    play: (onDone: (() -> Unit)?) -> Unit,
) {
    if (!repeatLoopEnabled) {
        play(null)
        return
    }
    fun scheduleNext() {
        play {
            scope.launch {
                delay(pauseMs())
                if (repeatStillEnabled()) scheduleNext()
            }
        }
    }
    scheduleNext()
}

private suspend fun LangStudyTtsFacade.speakStudyAwait(text: String) {
    if (text.isBlank()) return
    suspendCancellableCoroutine<Unit> { cont ->
        val done = AtomicBoolean(false)
        fun finish() {
            if (done.compareAndSet(false, true) && cont.isActive) {
                cont.resume(Unit)
            }
        }
        val ok = speakStudy(text) { finish() }
        if (!ok) finish()
    }
}

private suspend fun LangStudyTtsFacade.speakRussianAwait(text: String) {
    if (text.isBlank()) return
    suspendCancellableCoroutine<Unit> { cont ->
        val done = AtomicBoolean(false)
        fun finish() {
            if (done.compareAndSet(false, true) && cont.isActive) {
                cont.resume(Unit)
            }
        }
        val ok = speakRussian(text) { finish() }
        if (!ok) finish()
    }
}

@Composable
private fun LS_Session(modifier: Modifier, langTag: String, lsVm: LanguageStudyViewModel) {
    val ctx = LocalContext.current
    val prefs = remember { BiblePreferences(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    val session by lsVm.session.collectAsStateWithLifecycle()
    val tts = rememberLangStudyTts(langTag)
    val q = session.queue
    val idx = session.index
    val maxView = session.maxViewIndex
    var repeatLoopEnabled by remember { mutableStateOf(false) }
    var autoAdvanceDeck by remember { mutableStateOf(false) }
    val repeatPauseMs by prefs.langStudySrsRepeatPauseMs.collectAsStateWithLifecycle(
        initialValue = BiblePreferences.LANG_STUDY_SRS_REPEAT_PAUSE_MS_DEFAULT,
    )
    var pauseSliderMs by remember { mutableLongStateOf(repeatPauseMs) }
    LaunchedEffect(repeatPauseMs) {
        pauseSliderMs = repeatPauseMs
    }
    val repeatLoopRef = rememberUpdatedState(repeatLoopEnabled)
    val pauseRef = rememberUpdatedState(repeatPauseMs)
    val autoAdvanceDeckRef = rememberUpdatedState(autoAdvanceDeck)

    if (q.isEmpty()) {
        Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.language_study_session_empty))
            Spacer(Modifier.height(16.dp))
            Button(onClick = { lsVm.startSession(langTag, 28) }) { Text(stringResource(R.string.language_study_start_session)) }
        }
        return
    }
    if (idx !in q.indices) {
        LaunchedEffect(Unit) { lsVm.refreshOverview(langTag) }
        return
    }
    val w = q[idx]
    val lemmaLine = remember(w.wordKey, w.display, w.lemma) { w.display.ifBlank { w.lemma } }

    LaunchedEffect(autoAdvanceDeck, idx, lemmaLine, repeatPauseMs, w.wordKey, q.size) {
        if (!autoAdvanceDeck || q.isEmpty()) return@LaunchedEffect
        val card = q.getOrNull(idx) ?: return@LaunchedEffect
        val pauseStep = (repeatPauseMs / 3).coerceIn(250L..4000L)
        val showExample = !card.exampleL2.isNullOrBlank() &&
            !card.exampleRu.isNullOrBlank() &&
            (card.exampleL2.trim() != lemmaLine.trim() || card.exampleRu.trim() != card.glossRu.trim())
        delay(200)
        if (!autoAdvanceDeckRef.value) return@LaunchedEffect
        tts.speakStudyAwait(lemmaLine)
        delay(pauseStep)
        if (!autoAdvanceDeckRef.value) return@LaunchedEffect
        tts.speakRussianAwait(card.glossRu)
        if (showExample) {
            delay(pauseStep)
            if (!autoAdvanceDeckRef.value) return@LaunchedEffect
            tts.speakStudyAwait(card.exampleL2!!.trim())
            delay(pauseStep)
            if (!autoAdvanceDeckRef.value) return@LaunchedEffect
            tts.speakRussianAwait(card.exampleRu!!.trim())
        }
        delay(pauseStep)
        if (!autoAdvanceDeckRef.value) return@LaunchedEffect
        if (idx >= q.lastIndex) {
            autoAdvanceDeck = false
        } else {
            lsVm.sessionAutoplayAdvanceOne()
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = {
                    autoAdvanceDeck = false
                    lsVm.sessionGoPrevWithinQueue()
                },
                enabled = idx > 0,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.language_study_session_prev_card),
                )
            }
            Text(
                stringResource(R.string.language_study_session_progress, idx + 1, q.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = {
                    autoAdvanceDeck = false
                    lsVm.sessionGoNextWithinQueue()
                },
                enabled = idx < maxView,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.language_study_session_next_card),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.then(
                    if (repeatLoopEnabled) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            ) {
                IconButton(onClick = { repeatLoopEnabled = !repeatLoopEnabled }) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = stringResource(R.string.language_study_repeat_cd),
                        tint = if (repeatLoopEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.language_study_autoplay),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Switch(
                    checked = autoAdvanceDeck,
                    onCheckedChange = { autoAdvanceDeck = it },
                    modifier = Modifier.padding(end = 4.dp),
                    enabled = q.isNotEmpty(),
                )
            }
        }
        AnimatedVisibility(repeatLoopEnabled) {
            val pMin = BiblePreferences.LANG_STUDY_SRS_REPEAT_PAUSE_MS_MIN.toFloat()
            val pMax = BiblePreferences.LANG_STUDY_SRS_REPEAT_PAUSE_MS_MAX.toFloat()
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(
                    stringResource(
                        R.string.language_study_repeat_pause_label,
                        String.format(Locale.getDefault(), "%.1f", pauseSliderMs / 1000f),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = pauseSliderMs.toFloat().coerceIn(pMin, pMax),
                    onValueChange = { v ->
                        pauseSliderMs = v.toLong().coerceIn(
                            BiblePreferences.LANG_STUDY_SRS_REPEAT_PAUSE_MS_MIN,
                            BiblePreferences.LANG_STUDY_SRS_REPEAT_PAUSE_MS_MAX,
                        )
                    },
                    onValueChangeFinished = {
                        val v = pauseSliderMs
                        scope.launch {
                            prefs.setLangStudySrsRepeatPauseMs(v)
                        }
                    },
                    valueRange = pMin..pMax,
                )
            }
        }
        LsFlashcardMinimal(
            word = w,
            lemmaLine = lemmaLine,
            tts = tts,
            repeatLoopEnabled = repeatLoopEnabled,
            pauseMsSnapshot = { pauseRef.value },
            repeatStillEnabled = { repeatLoopRef.value },
            scope = scope,
        )
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.language_study_grade_prompt), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = {
                    autoAdvanceDeck = false
                    lsVm.gradeCurrent(langTag, 2)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.language_study_again))
            }
            OutlinedButton(
                onClick = {
                    autoAdvanceDeck = false
                    lsVm.gradeCurrent(langTag, 3)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.language_study_hard))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = {
                    autoAdvanceDeck = false
                    lsVm.gradeCurrent(langTag, 4)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.language_study_good))
            }
            Button(
                onClick = {
                    autoAdvanceDeck = false
                    lsVm.gradeCurrent(langTag, 5)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.language_study_easy))
            }
        }
    }
}

@Composable
private fun LsFlashcardMinimal(
    word: LangVocabWordEntity,
    lemmaLine: String,
    tts: LangStudyTtsFacade,
    repeatLoopEnabled: Boolean,
    pauseMsSnapshot: () -> Long,
    repeatStillEnabled: () -> Boolean,
    scope: CoroutineScope,
) {
    val display = lemmaLine
    val ipa = word.ipa
    val glossRu = word.glossRu
    val exampleL2 = word.exampleL2
    val exampleRu = word.exampleRu
    val showExample = !exampleL2.isNullOrBlank() &&
        !exampleRu.isNullOrBlank() &&
        (exampleL2.trim() != display.trim() || exampleRu.trim() != glossRu.trim())
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = display,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    lineHeight = MaterialTheme.typography.displaySmall.lineHeight * 1.15f,
                )
                IconButton(
                    enabled = lemmaLine.isNotBlank(),
                    onClick = {
                        lsSpeakWithOptionalRepeat(
                            scope,
                            repeatLoopEnabled,
                            pauseMsSnapshot,
                            repeatStillEnabled,
                        ) { cb -> tts.speakStudy(lemmaLine, cb) }
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.language_study_speak))
                }
            }
            ipa?.takeIf { it.isNotBlank() }?.let { ipaLine ->
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.language_study_ipa_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = ipaLine,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.language_study_gloss_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    enabled = glossRu.isNotBlank(),
                    onClick = {
                        lsSpeakWithOptionalRepeat(
                            scope,
                            repeatLoopEnabled,
                            pauseMsSnapshot,
                            repeatStillEnabled,
                        ) { cb ->
                            tts.speakRussian(glossRu, cb)
                        }
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.language_study_speak_gloss_cd))
                }
            }
            Text(
                text = glossRu,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (showExample) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        stringResource(R.string.language_study_example_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = exampleL2.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = exampleL2.isNotBlank(),
                        onClick = {
                            lsSpeakWithOptionalRepeat(
                                scope,
                                repeatLoopEnabled,
                                pauseMsSnapshot,
                                repeatStillEnabled,
                            ) { cb ->
                                tts.speakStudy(exampleL2!!.trim(), cb)
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.language_study_speak_example_l2_cd),
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = exampleRu.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = exampleRu.isNotBlank(),
                        onClick = {
                            lsSpeakWithOptionalRepeat(
                                scope,
                                repeatLoopEnabled,
                                pauseMsSnapshot,
                                repeatStillEnabled,
                            ) { cb ->
                                tts.speakRussian(exampleRu!!.trim(), cb)
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.language_study_speak_example_ru_cd),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LS_Dict(modifier: Modifier, langTag: String, lsVm: LanguageStudyViewModel) {
    var query by remember { mutableStateOf("") }
    val hits by lsVm.dictHits.collectAsStateWithLifecycle()
    val tts = rememberLangStudyTts(langTag)
    Column(modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; lsVm.searchDictionary(langTag, it) },
            label = { Text(stringResource(R.string.language_study_search_hint)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { lsVm.searchDictionary(langTag, query) }),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(hits, key = { it.wordKey }) { w ->
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.display, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { tts.speak(w.display.ifBlank { w.lemma }) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, null) }
                    }
                    Text(w.glossRu, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun LS_Pack(modifier: Modifier, code: LanguageStudyCode, lsVm: LanguageStudyViewModel) {
    val ctx = LocalContext.current
    var urlDraft by remember { mutableStateOf("") }
    val zipPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) lsVm.importFromUri(uri) }
    val bundled = bundledPath(code.routeArg)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(stringResource(R.string.language_study_pack_intro), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        Button(onClick = { zipPick.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.UploadFile, null)
            Text(stringResource(R.string.language_study_pick_zip))
        }
        OutlinedButton(
            onClick = { lsVm.importBundled("${code.routeArg}_v1.zip") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            enabled = assetExists(ctx, bundled),
        ) {
            Icon(Icons.Filled.CloudDownload, null)
            Text(
                if (assetExists(ctx, bundled)) stringResource(R.string.language_study_import_from_apk)
                else stringResource(R.string.language_study_bundled_missing),
            )
        }
        Text(stringResource(R.string.language_study_url_import_label), Modifier.padding(top = 20.dp))
        OutlinedTextField(urlDraft, { urlDraft = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("https://") }, singleLine = true)
        Button(onClick = { lsVm.downloadPackFromUrl(urlDraft) }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Icon(Icons.Filled.Link, null)
            Text(stringResource(R.string.language_study_download_url))
        }
        HorizontalDivider(Modifier.padding(vertical = 20.dp))
        Text(stringResource(R.string.language_study_pack_format_hint), style = MaterialTheme.typography.bodySmall)
    }
}

private fun bundledPath(lang: String): String = "language_packs/bundled/${lang}_v1.zip"

private fun assetExists(context: Context, path: String): Boolean =
    try {
        context.assets.open(path).close()
        true
    } catch (_: Exception) {
        false
    }
