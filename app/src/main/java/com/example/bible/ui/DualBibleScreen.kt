@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.example.bible.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavHostController
import com.example.bible.data.BibleAudioNarrators
import com.example.bible.data.BooksMainMenuOrder
import com.example.bible.data.narratorForReading
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.bible.R
import com.example.bible.data.BibleBook
import com.example.bible.data.BibleCanon
import com.example.bible.data.BibleChapter
import com.example.bible.data.BibleLibrary
import com.example.bible.data.BibleUserAudio
import com.example.bible.data.BibleUserImage
import com.example.bible.data.BibleUserVideo
import com.example.bible.data.LexiconMediaRefs
import com.example.bible.data.VerseRef
import com.example.bible.data.WordSpanMediaAttachment
import com.example.bible.data.findForTap
import com.example.bible.data.newWordSpanMediaId
import com.example.bible.data.BibleVerse
import com.example.bible.data.BibleDictionary
import com.example.bible.data.DictResult
import com.example.bible.data.DictionaryManager
import com.example.bible.data.InterlinearTts
import com.example.bible.data.TextHighlight
import com.example.bible.data.NoteScriptureLinks
import com.example.bible.data.ParsedScriptureNavigation
import com.example.bible.data.ScriptureAudioNavigation
import com.example.bible.data.ScriptureAudioPlayMode
import com.example.bible.data.TranslationId
import com.example.bible.data.SemanticHighlightSession
import com.example.bible.data.SemanticLexiconRule
import com.example.bible.data.UserNote
import com.example.bible.data.matchesVerseLocation
import com.example.bible.data.verseNumbersWithNotesInChapter
import com.example.bible.data.AttachmentKind
import com.example.bible.data.VerseAttachment
import com.example.bible.data.VerseAttachmentStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsTopHeight
import com.example.bible.data.TimemarkStore
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import kotlin.math.roundToInt

data class PaneState(
    val translation: TranslationId,
    val bookId: String? = null,
    val chapter: Int? = null,
)

/** Запрос воспроизведения сегмента озвучки по ссылке из заметки. */
data class ScriptureAudioPlayRequest(
    val startVerse: Int,
    val stopAfterVerse: Int?,
    val playToChapterEnd: Boolean,
    val chapterVerseCount: Int = 0,
    val allSegments: List<com.example.bible.data.ScriptureAudioSegment> = emptyList(),
    val startSegmentIndex: Int = 0,
)

/** Запрос открыть место Писания во встроенной Библии редактора заметок. */
data class NoteBibleNavigation(
    val bookId: String,
    val chapter: Int,
    val verses: Set<Int>,
    /** false — только указанные стихи; true — вся глава с подсветкой. */
    val showFullChapter: Boolean = false,
    val playAudio: Boolean = false,
    val translationCode: String? = null,
    val audioPlayMode: ScriptureAudioPlayMode = ScriptureAudioPlayMode.VERSE,
    val audioSegmentSpec: String? = null,
    val audioSegmentIndex: Int = 0,
    val nonce: Long = System.currentTimeMillis(),
)

/** Прокрутка читалки к стиху (nonce позволяет повторно открыть тот же стих). */
data class VerseScrollRequest(
    val bookId: String,
    val chapter: Int,
    val verses: Set<Int>,
    val showFullChapter: Boolean,
    val playAudio: Boolean = false,
    val translationCode: String? = null,
    val audioPlayMode: ScriptureAudioPlayMode = ScriptureAudioPlayMode.VERSE,
    val audioSegmentSpec: String? = null,
    val audioSegmentIndex: Int = 0,
    val nonce: Long,
) {
    fun matchesChapter(currentBookId: String, currentChapter: Int): Boolean =
        bookId == currentBookId && chapter == currentChapter

    fun scrollToVerse(): Int = verses.minOrNull() ?: 1
}

/** Строка-заголовок перед списком стихов в [ReaderPane]. */
private const val READER_LAZY_HEADER_ITEMS = 1

data class ScrollSyncState(
    val sourcePane: Int = -1,
    val itemIndex: Int = 0,
    val scrollOffset: Int = 0,
    val version: Long = 0L,
)

internal data class DualReaderChrome(
    val navController: NavHostController?,
    val booksMainMenuOrder: List<String>,
    val narratorId: String,
    val isDarkTheme: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DualPaneChapterHeader(
    paneIndex: Int,
    state: PaneState,
    bookId: String,
    chapterNum: Int,
    totalChapters: Int,
    visibleVerse: Int,
    onStateChange: (PaneState) -> Unit,
    syncMode: Boolean,
    onSyncToggle: () -> Unit,
    onExit: (() -> Unit)?,
    onInternalBack: (() -> Unit)? = null,
    onClosePane: (() -> Unit)?,
    onLongPress: () -> Unit,
    onOpenQuickNav: () -> Unit,
    translationTabColors: Map<String, Int>,
    readerChrome: DualReaderChrome,
    readingAudioNarrator: com.example.bible.data.AudioNarrator,
    hasChapterTimemarks: Boolean,
    onAdjustFontScale: (Float) -> Unit,
    onShowTextSizeDialog: () -> Unit,
    onShowNarratorPicker: () -> Unit,
    onShowStudyTools: (Int) -> Unit,
    includeStatusBar: Boolean = paneIndex == 0,
    showSyncInMenu: Boolean = true,
) {
    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }
    val bibleAudioState by com.example.bible.data.BibleAudioPlayer.state.collectAsState()
    val chapterIndex = chapterNum - 1
    val hasPrev = chapterIndex > 0
    val hasNext = chapterIndex < totalChapters - 1
    val chapterShortTitle = readerChapterTitle(bookId, chapterNum, visibleVerse)
    val translations = TranslationId.entries
    val selectedTabIndex = translations.indexOf(state.translation).coerceAtLeast(0)
    val selectedTabArgb = translationTabColors[translations[selectedTabIndex].code]
    val tabIndicatorColor = selectedTabArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            },
    ) {
        ReaderChapterTopBar(
            includeStatusBar = includeStatusBar,
            restoreLightStatusBarIcons = !readerChrome.isDarkTheme,
            navigationIcon = {
                when {
                    onExit != null -> ReaderTopIconButton(onClick = onExit) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            modifier = Modifier.size(ReaderTopBarIconSize),
                        )
                    }
                    onInternalBack != null -> ReaderTopIconButton(onClick = onInternalBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            modifier = Modifier.size(ReaderTopBarIconSize),
                        )
                    }
                }
            },
            centerContent = {
                ReaderChapterNavTitle(
                    title = chapterShortTitle,
                    onTitleClick = onOpenQuickNav,
                    onPrevChapter = if (hasPrev) {
                        { onStateChange(state.copy(chapter = chapterNum - 1)) }
                    } else {
                        null
                    },
                    onNextChapter = if (hasNext) {
                        { onStateChange(state.copy(chapter = chapterNum + 1)) }
                    } else {
                        null
                    },
                    prevContentDescription = stringResource(R.string.prev_chapter),
                    nextContentDescription = stringResource(R.string.next_chapter),
                )
            },
            actions = {
                ReaderTopIconButton(onClick = {
                    readerChrome.navController?.navigate("search")
                }) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.search_title),
                        modifier = Modifier.size(ReaderTopBarIconSize),
                    )
                }
                if (onClosePane == null) {
                    ReaderTopIconButton(onClick = {
                        com.example.bible.data.BibleAudioPlayer.stopForNavigation()
                        readerChrome.navController?.navigate("dual?bookId=$bookId&chapter=$chapterNum")
                    }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.menu_dual_bible),
                            modifier = Modifier.size(ReaderTopBarIconSize),
                            tint = ReaderTopBarColors.Accent,
                        )
                    }
                }
                ReaderTopIconButton(
                    onClick = {
                        com.example.bible.data.BibleAudioPlayer.stopForNavigation()
                        playReaderChapterAudio(
                            context,
                            readingAudioNarrator,
                            bookId,
                            chapterNum,
                            visibleVerse.coerceAtLeast(1),
                            state.translation,
                        )
                    },
                    showTimemarkBadge = hasChapterTimemarks,
                ) {
                    val isThisChapterAudio = bibleAudioState.isPlaying &&
                        bibleAudioState.bookId == bookId &&
                        bibleAudioState.chapter == chapterNum &&
                        bibleAudioState.narratorId == readingAudioNarrator.id
                    Icon(
                        imageVector = if (isThisChapterAudio) Icons.Default.Pause else Icons.Default.Headphones,
                        contentDescription = if (hasChapterTimemarks) {
                            stringResource(R.string.timemark_listen_with_cues_cd)
                        } else {
                            "Слушать"
                        },
                        tint = when {
                            isThisChapterAudio -> ReaderTopBarColors.Accent
                            hasChapterTimemarks -> ReaderTopBarColors.Accent
                            else -> ReaderTopBarColors.ContentMuted
                        },
                        modifier = Modifier.size(ReaderTopBarIconSize),
                    )
                }
                ReaderTopIconButton(onClick = { onShowStudyTools(visibleVerse.coerceAtLeast(1)) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "Изучение",
                        tint = ReaderTopBarColors.Accent,
                        modifier = Modifier.size(ReaderTopBarIconSize),
                    )
                }
                if (onClosePane != null) {
                    IconButton(onClick = onClosePane, modifier = Modifier.size(ReaderTopBarIconButtonSize)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.dual_close_pane),
                            modifier = Modifier.size(ReaderTopBarIconSize),
                            tint = ReaderTopBarColors.ContentMuted,
                        )
                    }
                }
                Box {
                    ReaderTopIconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Ещё",
                            modifier = Modifier.size(ReaderTopBarIconSize),
                        )
                    }
                    val readerMenuMaxH = (LocalConfiguration.current.screenHeightDp * 0.58f).dp
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        modifier = Modifier.heightIn(max = readerMenuMaxH),
                    ) {
                        if (showSyncInMenu) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (syncMode) {
                                            "${stringResource(R.string.dual_sync)} ✓"
                                        } else {
                                            stringResource(R.string.dual_sync)
                                        },
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onSyncToggle()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_settings_title)) },
                            onClick = {
                                showMoreMenu = false
                                readerChrome.navController?.navigate("main_settings")
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Settings, contentDescription = null)
                            },
                        )
                        HorizontalDivider()
                        readerChrome.navController?.let { nav ->
                            BooksMainMenuOrderedItems(
                                menuOrder = readerChrome.booksMainMenuOrder,
                                translation = state.translation,
                                narratorId = readerChrome.narratorId,
                                closeMenu = { showMoreMenu = false },
                                navController = nav,
                                onShowTextSizeDialog = {
                                    showMoreMenu = false
                                    onShowTextSizeDialog()
                                },
                                onShowBookNarratorPicker = {
                                    showMoreMenu = false
                                    onShowNarratorPicker()
                                },
                                timemarkBookId = bookId,
                                timemarkChapter = chapterNum,
                                timemarkNarratorId = readingAudioNarrator.id,
                            )
                        }
                        HorizontalDivider()
                        translations.forEach { tid ->
                            DropdownMenuItem(
                                text = { Text(tid.labelRu) },
                                onClick = {
                                    showMoreMenu = false
                                    onStateChange(state.copy(translation = tid))
                                },
                                trailingIcon = {
                                    if (tid == state.translation) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                },
                            )
                        }
                    }
                }
            },
        )
        ScrollableTabRow(
            modifier = Modifier.height(36.dp),
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = tabIndicatorColor,
            edgePadding = 8.dp,
            indicator = { tabPositions ->
                if (selectedTabIndex in tabPositions.indices) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = tabIndicatorColor,
                    )
                }
            },
        ) {
            translations.forEachIndexed { index, tid ->
                val selected = selectedTabIndex == index
                Tab(
                    modifier = Modifier.height(36.dp),
                    selected = selected,
                    onClick = { onStateChange(state.copy(translation = tid)) },
                    text = {
                        TranslationTabLabel(
                            translation = tid,
                            selected = selected,
                            highlightArgb = translationTabColors[tid.code],
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualBibleScreen(
    library: BibleLibrary,
    initialPanes: List<PaneState>? = null,
    bookmarkKeys: Set<String>,
    textHighlights: List<TextHighlight>,
    onAddTextHighlight: (TextHighlight) -> Unit,
    onRemoveTextHighlights: (VerseRef, Int, Int) -> Unit,
    onToggleBookmark: (VerseRef) -> Unit,
    readerFontScale: Float,
    onAdjustReaderFontScale: (Float) -> Unit,
    onVerseCommentary: (VerseRef) -> Unit,
    onPlayAudio: ((VerseRef, () -> Unit) -> Unit)? = null,
    onPauseMainAudioForAttachment: () -> Unit = {},
    onExit: () -> Unit,
    /** Закрыть одну панель и открыть оставшийся перевод в обычной читалке. */
    onClosePane: (keepPane: PaneState) -> Unit,
    mediaLibraryImages: List<BibleUserImage> = emptyList(),
    userNotes: List<UserNote> = emptyList(),
    semanticHighlightSession: SemanticHighlightSession? = null,
    userLexiconRules: List<SemanticLexiconRule> = emptyList(),
    presetLexiconRules: List<SemanticLexiconRule> = emptyList(),
    presetLexiconEnabled: Boolean = true,
    mediaLibraryVideos: List<BibleUserVideo> = emptyList(),
    mediaLibraryAudios: List<BibleUserAudio> = emptyList(),
    wordSpanMediaAttachments: List<WordSpanMediaAttachment> = emptyList(),
    onUpsertWordSpanMedia: (WordSpanMediaAttachment) -> Unit = {},
    onDeleteWordSpanMedia: (String) -> Unit = {},
    onRemoveWordSpanMediaIntersecting: (VerseRef, Int, Int) -> Unit = { _, _, _ -> },
    /** Заметка к стиху: открыть редактор (создать запись с привязкой). */
    onVerseNote: ((VerseRef, bookName: String, verseText: String) -> Unit)? = null,
    /** Открыть существующую заметку по id. */
    onOpenVerseNote: ((String) -> Unit)? = null,
    viewModel: BibleViewModel? = null,
    onOpenDeepSeekSettings: () -> Unit = {},
    onOpenGigaChatSettings: () -> Unit = onOpenDeepSeekSettings,
    translationTabColors: Map<String, Int> = emptyMap(),
    navController: NavHostController? = null,
    booksMainMenuOrder: List<String> = BooksMainMenuOrder.allIds,
    narratorId: String = "bondarenko",
    isDarkTheme: Boolean = false,
) {
    var panes by remember(initialPanes) {
        mutableStateOf(
            initialPanes ?: listOf(
                PaneState(TranslationId.SYNODAL),
                PaneState(TranslationId.WEB),
            ),
        )
    }
    var syncMode by remember { mutableStateOf(true) }
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    var resizeMode by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0.5f) }
    var totalHeightPx by remember { mutableIntStateOf(1) }
    var scrollSync by remember { mutableStateOf(ScrollSyncState()) }

    fun applyPaneUpdate(index: Int, newState: PaneState) {
        panes = if (syncMode) {
            panes.mapIndexed { i, p ->
                if (i == index) newState
                else p.copy(
                    bookId = newState.bookId,
                    chapter = newState.chapter,
                )
            }
        } else {
            panes.mapIndexed { i, p -> if (i == index) newState else p }
        }
    }

    fun applyScrollSync(sourcePane: Int, itemIndex: Int, scrollOffset: Int) {
        if (!syncMode) return
        scrollSync = ScrollSyncState(
            sourcePane = sourcePane,
            itemIndex = itemIndex,
            scrollOffset = scrollOffset,
            version = scrollSync.version + 1,
        )
    }

    val dualChrome = remember(navController, booksMainMenuOrder, narratorId, isDarkTheme) {
        DualReaderChrome(
            navController = navController,
            booksMainMenuOrder = booksMainMenuOrder,
            narratorId = narratorId,
            isDarkTheme = isDarkTheme,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { totalHeightPx = it.height },
    ) {
        Column(Modifier.fillMaxSize()) {
            // Pane 1
            Box(
                modifier = Modifier
                    .weight(splitFraction)
                    .fillMaxWidth(),
            ) {
                BiblePaneColumn(
                    paneIndex = 0,
                    state = panes[0],
                    onStateChange = { applyPaneUpdate(0, it) },
                    library = library,
                    bookmarkKeys = bookmarkKeys,
                    onToggleBookmark = onToggleBookmark,
                    syncMode = syncMode,
                    onSyncToggle = { syncMode = !syncMode },
                    scrollSync = scrollSync,
                    onScrollSync = { idx, offset -> applyScrollSync(0, idx, offset) },
                    textHighlights = textHighlights,
                    onAddTextHighlight = onAddTextHighlight,
                    onRemoveTextHighlights = onRemoveTextHighlights,
                    onVerseCommentary = onVerseCommentary,
                    onPlayAudio = onPlayAudio,
                    onPauseMainAudioForAttachment = onPauseMainAudioForAttachment,
                    readerFontScale = readerFontScale,
                    onAdjustReaderFontScale = onAdjustReaderFontScale,
                    onExit = onExit,
                    onClosePane = { onClosePane(panes[1]) },
                    onLongPressTopBar = {
                        dragFraction = splitFraction
                        resizeMode = true
                    },
                    mediaLibraryImages = mediaLibraryImages,
                    userNotes = userNotes,
                    semanticHighlightSession = semanticHighlightSession,
                    userLexiconRules = userLexiconRules,
                    presetLexiconRules = presetLexiconRules,
                    presetLexiconEnabled = presetLexiconEnabled,
                    mediaLibraryVideos = mediaLibraryVideos,
                    mediaLibraryAudios = mediaLibraryAudios,
                    wordSpanMediaAttachments = wordSpanMediaAttachments,
                    onUpsertWordSpanMedia = onUpsertWordSpanMedia,
                    onDeleteWordSpanMedia = onDeleteWordSpanMedia,
                    onRemoveWordSpanMediaIntersecting = onRemoveWordSpanMediaIntersecting,
                    onVerseNote = onVerseNote,
                    onOpenVerseNote = onOpenVerseNote,
                    viewModel = viewModel,
                    onOpenDeepSeekSettings = onOpenDeepSeekSettings,
                    onOpenGigaChatSettings = onOpenGigaChatSettings,
                    translationTabColors = translationTabColors,
                    readerChrome = dualChrome,
                )
            }

            // Divider bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )

            // Pane 2
            Box(
                modifier = Modifier
                    .weight(1f - splitFraction)
                    .fillMaxWidth(),
            ) {
                BiblePaneColumn(
                    paneIndex = 1,
                    state = panes[1],
                    onStateChange = { applyPaneUpdate(1, it) },
                    library = library,
                    bookmarkKeys = bookmarkKeys,
                    onToggleBookmark = onToggleBookmark,
                    syncMode = syncMode,
                    onSyncToggle = { syncMode = !syncMode },
                    scrollSync = scrollSync,
                    onScrollSync = { idx, offset -> applyScrollSync(1, idx, offset) },
                    textHighlights = textHighlights,
                    onAddTextHighlight = onAddTextHighlight,
                    onRemoveTextHighlights = onRemoveTextHighlights,
                    onVerseCommentary = onVerseCommentary,
                    onPlayAudio = onPlayAudio,
                    onPauseMainAudioForAttachment = onPauseMainAudioForAttachment,
                    readerFontScale = readerFontScale,
                    onAdjustReaderFontScale = onAdjustReaderFontScale,
                    onExit = null,
                    onClosePane = { onClosePane(panes[0]) },
                    onLongPressTopBar = {
                        dragFraction = splitFraction
                        resizeMode = true
                    },
                    mediaLibraryImages = mediaLibraryImages,
                    userNotes = userNotes,
                    semanticHighlightSession = semanticHighlightSession,
                    userLexiconRules = userLexiconRules,
                    presetLexiconRules = presetLexiconRules,
                    presetLexiconEnabled = presetLexiconEnabled,
                    mediaLibraryVideos = mediaLibraryVideos,
                    mediaLibraryAudios = mediaLibraryAudios,
                    wordSpanMediaAttachments = wordSpanMediaAttachments,
                    onUpsertWordSpanMedia = onUpsertWordSpanMedia,
                    onDeleteWordSpanMedia = onDeleteWordSpanMedia,
                    onRemoveWordSpanMediaIntersecting = onRemoveWordSpanMediaIntersecting,
                    onVerseNote = onVerseNote,
                    onOpenVerseNote = onOpenVerseNote,
                    viewModel = viewModel,
                    onOpenDeepSeekSettings = onOpenDeepSeekSettings,
                    onOpenGigaChatSettings = onOpenGigaChatSettings,
                    translationTabColors = translationTabColors,
                    readerChrome = dualChrome,
                )
            }
        }

        if (resizeMode) {
            ResizeOverlay(
                fraction = dragFraction,
                totalHeightPx = totalHeightPx,
                onFractionChange = { dragFraction = it.coerceIn(0.2f, 0.8f) },
                onApply = {
                    splitFraction = dragFraction
                    resizeMode = false
                },
                onReset = {
                    dragFraction = 0.5f
                    splitFraction = 0.5f
                    resizeMode = false
                },
                onDismiss = { resizeMode = false },
            )
        }
    }
}

@Composable
internal fun VerticalSplitHandle(
    modifier: Modifier = Modifier,
    onDragDeltaPx: (Float) -> Unit,
    onLongPress: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(20.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDeltaPx(dragAmount)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .width(48.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
            Text(
                stringResource(R.string.note_editor_split_handle_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
internal fun HorizontalSplitHandle(
    modifier: Modifier = Modifier,
    onDragDeltaPx: (Float) -> Unit,
    onDragEnd: () -> Unit = {},
    onLongPress: () -> Unit,
) {
    Box(
        modifier
            .fillMaxHeight()
            .width(20.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { onDragEnd() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        onDragDeltaPx(dragAmount)
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
        Box(
            Modifier
                .width(4.dp)
                .height(48.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
internal fun HorizontalResizeOverlay(
    fraction: Float,
    totalWidthPx: Int,
    onFractionChange: (Float) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    var heightPx by remember { mutableIntStateOf(1) }
    val currentFraction by rememberUpdatedState(fraction)
    val currentWidth by rememberUpdatedState(totalWidthPx)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height }
            .background(Color.Black.copy(alpha = 0.5f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                },
        )

        val lineXPx = (fraction * totalWidthPx).roundToInt()
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .offset { IntOffset(lineXPx - with(density) { 1.dp.roundToPx() }, 0) }
                .background(MaterialTheme.colorScheme.tertiary),
        )

        val handleSizeDp = 56.dp
        val handleSizePx = with(density) { handleSizeDp.roundToPx() }
        Box(
            modifier = Modifier
                .size(handleSizeDp)
                .offset {
                    IntOffset(
                        lineXPx - handleSizePx / 2,
                        (heightPx - handleSizePx) / 2,
                    )
                }
                .shadow(12.dp, CircleShape)
                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val w = currentWidth
                        if (w > 0) {
                            onFractionChange(currentFraction + dragAmount.x / w)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(3.dp, 24.dp).background(Color.White))
                Box(Modifier.size(3.dp, 24.dp).background(Color.White))
                Box(Modifier.size(3.dp, 24.dp).background(Color.White))
            }
        }

        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Text(
            text = "${(fraction * 100).roundToInt()}%  /  ${((1f - fraction) * 100).roundToInt()}%",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarTop + 8.dp)
                .background(Color(0xAA000000), shape = CircleShape)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusBarTop + 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onReset,
                modifier = Modifier
                    .size(44.dp)
                    .shadow(4.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = onApply,
                modifier = Modifier
                    .size(44.dp)
                    .shadow(4.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape),
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
internal fun ResizeOverlay(
    fraction: Float,
    totalHeightPx: Int,
    onFractionChange: (Float) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(1) }
    val currentFraction by rememberUpdatedState(fraction)
    val currentHeight by rememberUpdatedState(totalHeightPx)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width }
            .background(Color.Black.copy(alpha = 0.5f)),
    ) {
        // Tap catcher behind everything
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                },
        )

        // Horizontal line at split position
        val lineYPx = (fraction * totalHeightPx).roundToInt()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .offset { IntOffset(0, lineYPx - with(density) { 1.dp.roundToPx() }) }
                .background(MaterialTheme.colorScheme.tertiary),
        )

        // Draggable circle handle — centered horizontally
        val handleSizeDp = 56.dp
        val handleSizePx = with(density) { handleSizeDp.roundToPx() }
        Box(
            modifier = Modifier
                .size(handleSizeDp)
                .offset {
                    IntOffset(
                        (widthPx - handleSizePx) / 2,
                        lineYPx - handleSizePx / 2,
                    )
                }
                .shadow(12.dp, CircleShape)
                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val h = currentHeight
                        if (h > 0) {
                            val newFraction = currentFraction + dragAmount.y / h
                            onFractionChange(newFraction)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(24.dp, 3.dp).background(Color.White))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(24.dp, 3.dp).background(Color.White))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(24.dp, 3.dp).background(Color.White))
            }
        }

        // Percentage label
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Text(
            text = "${(fraction * 100).roundToInt()}%  /  ${((1f - fraction) * 100).roundToInt()}%",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarTop + 8.dp)
                .background(Color(0xAA000000), shape = CircleShape)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )

        // Top-right controls: Reset and Apply
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusBarTop + 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onReset,
                modifier = Modifier
                    .size(44.dp)
                    .shadow(4.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = onApply,
                modifier = Modifier
                    .size(44.dp)
                    .shadow(4.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape),
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun PaneTopBar(
    state: PaneState,
    paneIndex: Int,
    library: BibleLibrary,
    syncMode: Boolean,
    onSyncToggle: () -> Unit,
    onTranslationChange: (TranslationId) -> Unit,
    onBack: () -> Unit,
    onExit: (() -> Unit)?,
    onClosePane: (() -> Unit)? = null,
    onAdjustFontScale: (Float) -> Unit,
    readerFontScale: Float,
    onLongPress: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
    showSyncControl: Boolean = true,
    showInternalBack: Boolean = false,
    includeStatusBar: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showAlphabet by remember { mutableStateOf(false) }
    val bookName = state.bookId?.let { rememberBookName(library, state.translation, it) }
    val titleText = buildString {
        append(state.translation.shortLabel)
        if (state.bookId != null) {
            append("  ")
            append(BibleCanon.byId(state.bookId)?.abbrRu ?: bookName.orEmpty())
        } else if (bookName != null) {
            append("  ")
            append(bookName)
        }
        if (state.chapter != null) {
            append(':')
            append(state.chapter)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (includeStatusBar) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongPress() },
                    )
                }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (onExit != null) {
            IconButton(onClick = onExit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else if (showInternalBack && (state.chapter != null || state.bookId != null)) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (onTitleClick != null) {
            TextButton(
                onClick = onTitleClick,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text(
                    text = titleText,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = titleText,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
        }

        // Alphabet reference for interlinear
        if (state.translation == TranslationId.INTERLINEAR) {
            TextButton(
                onClick = { showAlphabet = true },
                modifier = Modifier
                    .height(40.dp)
                    .widthIn(min = 44.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(
                    "Αβ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Font size controls
        val canDecreaseFont = readerFontScale > ReaderFontScaleDefaults.MIN + 0.001f
        val canIncreaseFont = readerFontScale < ReaderFontScaleDefaults.MAX - 0.001f
        TextButton(
            onClick = { onAdjustFontScale(-ReaderFontScaleDefaults.STEP) },
            enabled = canDecreaseFont,
            modifier = Modifier
                .height(40.dp)
                .widthIn(min = 44.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            Text(
                "A−",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (canDecreaseFont) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }
        TextButton(
            onClick = { onAdjustFontScale(ReaderFontScaleDefaults.STEP) },
            enabled = canIncreaseFont,
            modifier = Modifier
                .height(40.dp)
                .widthIn(min = 44.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            Text(
                "A+",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (canIncreaseFont) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }

        // Sync indicator
        if (showSyncControl) {
            TextButton(
                onClick = onSyncToggle,
                modifier = Modifier
                    .height(40.dp)
                    .widthIn(min = 44.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(
                    if (syncMode) "⇅" else "⇉",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (syncMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
        }

        // Menu
        if (onClosePane != null) {
            IconButton(onClick = onClosePane, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.dual_close_pane),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                TranslationId.entries.forEach { tid ->
                    DropdownMenuItem(
                        text = { Text(tid.labelRu) },
                        onClick = {
                            menuOpen = false
                            onTranslationChange(tid)
                        },
                        trailingIcon = {
                            if (tid == state.translation) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                    )
                }
            }
        }
    }
    }

    if (showAlphabet) {
        AlphabetReferenceSheet(onDismiss = { showAlphabet = false })
    }
}

@Composable
fun NoteEditorBiblePane(
    modifier: Modifier = Modifier,
    library: BibleLibrary,
    initialTranslation: TranslationId,
    initialBookId: String?,
    initialChapter: Int?,
    narratorId: String,
    bookmarkKeys: Set<String>,
    onToggleBookmark: (VerseRef) -> Unit,
    readerFontScale: Float,
    onAdjustReaderFontScale: (Float) -> Unit,
    onPlayAudio: ((VerseRef, () -> Unit) -> Unit)? = null,
    navigationRequest: NoteBibleNavigation? = null,
    onNavigationConsumed: () -> Unit = {},
    navController: NavHostController? = null,
    booksMainMenuOrder: List<String> = BooksMainMenuOrder.allIds,
    translationTabColors: Map<String, Int> = emptyMap(),
    isDarkTheme: Boolean = false,
    viewModel: BibleViewModel? = null,
) {
    var paneState by remember(initialTranslation, initialBookId, initialChapter) {
        mutableStateOf(
            PaneState(
                translation = initialTranslation,
                bookId = initialBookId,
                chapter = initialChapter,
            ),
        )
    }
    var scrollToVerseRequest by remember { mutableStateOf<VerseScrollRequest?>(null) }
    var showQuickNav by remember { mutableStateOf(false) }
    val dualContext = androidx.compose.ui.platform.LocalContext.current
    val noteChrome = remember(navController, booksMainMenuOrder, narratorId, isDarkTheme) {
        DualReaderChrome(
            navController = navController,
            booksMainMenuOrder = booksMainMenuOrder,
            narratorId = narratorId,
            isDarkTheme = isDarkTheme,
        )
    }
    LaunchedEffect(navigationRequest?.nonce) {
        val req = navigationRequest ?: return@LaunchedEffect
        val targetTranslation = req.translationCode?.let { TranslationId.fromCode(it) } ?: paneState.translation
        paneState = paneState.copy(
            translation = targetTranslation,
            bookId = req.bookId,
            chapter = req.chapter,
        )
        scrollToVerseRequest = VerseScrollRequest(
            bookId = req.bookId,
            chapter = req.chapter,
            verses = req.verses,
            showFullChapter = req.showFullChapter,
            playAudio = req.playAudio,
            translationCode = req.translationCode,
            audioPlayMode = req.audioPlayMode,
            audioSegmentSpec = req.audioSegmentSpec,
            audioSegmentIndex = req.audioSegmentIndex,
            nonce = req.nonce,
        )
        onNavigationConsumed()
    }
    Column(modifier.fillMaxSize()) {
        BibleAudioMiniBar()
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        BiblePaneColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            paneIndex = 0,
            state = paneState,
            onStateChange = { paneState = it },
            library = library,
            bookmarkKeys = bookmarkKeys,
            onToggleBookmark = onToggleBookmark,
            syncMode = false,
            onSyncToggle = {},
            scrollSync = ScrollSyncState(),
            onScrollSync = { _, _ -> },
            textHighlights = emptyList(),
            onAddTextHighlight = {},
            onRemoveTextHighlights = { _, _, _ -> },
            onVerseCommentary = { ref ->
                navController?.navigate("commentary/${ref.bookId}/${ref.chapter}/${ref.verse}")
            },
            onPlayAudio = onPlayAudio,
            readerFontScale = readerFontScale,
            onAdjustReaderFontScale = onAdjustReaderFontScale,
            onExit = null,
            onLongPressTopBar = {},
            onOpenQuickNav = { showQuickNav = true },
            showSyncControl = false,
            showInternalBack = true,
            includeReaderStatusBar = false,
            scrollToVerseRequest = scrollToVerseRequest,
            translationTabColors = translationTabColors,
            readerChrome = noteChrome,
            viewModel = viewModel,
            onOpenDeepSeekSettings = {
                navController?.navigate("ai_settings")
            },
            onOpenGigaChatSettings = {
                navController?.navigate("gigachat_settings")
            },
            onPlayChapterFromVerse = { playReq ->
                val bid = paneState.bookId ?: return@BiblePaneColumn
                val ch = paneState.chapter ?: return@BiblePaneColumn
                val narrator = com.example.bible.data.narratorForReading(
                    paneState.translation,
                    bid,
                    narratorId,
                )
                if (playReq.allSegments.size > 1) {
                    playReaderChapterAudioSegments(
                        dualContext,
                        narrator,
                        bid,
                        ch,
                        paneState.translation,
                        playReq.allSegments,
                        playReq.chapterVerseCount,
                        startIndex = playReq.startSegmentIndex,
                    )
                } else {
                    playReaderChapterAudio(
                        dualContext,
                        narrator,
                        bid,
                        ch,
                        playReq.startVerse,
                        paneState.translation,
                        forceVerseStart = true,
                        stopAfterVerse = playReq.stopAfterVerse,
                        playToChapterEnd = playReq.playToChapterEnd,
                        chapterVerseCount = playReq.chapterVerseCount,
                    )
                }
            },
        )
    }
    if (showQuickNav) {
        QuickNavigatorSheet(
            library = library,
            translation = paneState.translation,
            currentBookId = paneState.bookId.orEmpty(),
            onNavigate = { bookId, chapter ->
                paneState = paneState.copy(bookId = bookId, chapter = chapter)
                showQuickNav = false
            },
            onDismiss = { showQuickNav = false },
        )
    }
}

@Composable
internal fun BiblePaneColumn(
    modifier: Modifier = Modifier,
    paneIndex: Int,
    state: PaneState,
    onStateChange: (PaneState) -> Unit,
    library: BibleLibrary,
    bookmarkKeys: Set<String>,
    onToggleBookmark: (VerseRef) -> Unit,
    syncMode: Boolean,
    onSyncToggle: () -> Unit,
    scrollSync: ScrollSyncState,
    onScrollSync: (Int, Int) -> Unit,
    textHighlights: List<TextHighlight>,
    onAddTextHighlight: (TextHighlight) -> Unit,
    onRemoveTextHighlights: (VerseRef, Int, Int) -> Unit,
    onVerseCommentary: (VerseRef) -> Unit,
    onPlayAudio: ((VerseRef, () -> Unit) -> Unit)? = null,
    onPauseMainAudioForAttachment: () -> Unit = {},
    readerFontScale: Float,
    onAdjustReaderFontScale: (Float) -> Unit,
    onExit: (() -> Unit)?,
    onClosePane: (() -> Unit)? = null,
    onLongPressTopBar: () -> Unit,
    mediaLibraryImages: List<BibleUserImage> = emptyList(),
    userNotes: List<UserNote> = emptyList(),
    semanticHighlightSession: SemanticHighlightSession? = null,
    userLexiconRules: List<SemanticLexiconRule> = emptyList(),
    presetLexiconRules: List<SemanticLexiconRule> = emptyList(),
    presetLexiconEnabled: Boolean = true,
    mediaLibraryVideos: List<BibleUserVideo> = emptyList(),
    mediaLibraryAudios: List<BibleUserAudio> = emptyList(),
    wordSpanMediaAttachments: List<WordSpanMediaAttachment> = emptyList(),
    onUpsertWordSpanMedia: (WordSpanMediaAttachment) -> Unit = {},
    onDeleteWordSpanMedia: (String) -> Unit = {},
    onRemoveWordSpanMediaIntersecting: (VerseRef, Int, Int) -> Unit = { _, _, _ -> },
    onVerseNote: ((VerseRef, bookName: String, verseText: String) -> Unit)? = null,
    onOpenVerseNote: ((String) -> Unit)? = null,
    onOpenQuickNav: (() -> Unit)? = null,
    showSyncControl: Boolean = true,
    showInternalBack: Boolean = false,
    includeReaderStatusBar: Boolean? = null,
    scrollToVerseRequest: VerseScrollRequest? = null,
    onPlayChapterFromVerse: ((ScriptureAudioPlayRequest) -> Unit)? = null,
    viewModel: BibleViewModel? = null,
    onOpenDeepSeekSettings: () -> Unit = {},
    onOpenGigaChatSettings: () -> Unit = onOpenDeepSeekSettings,
    translationTabColors: Map<String, Int> = emptyMap(),
    readerChrome: DualReaderChrome? = null,
) {
    val paneContext = LocalContext.current
    var showQuickNav by remember { mutableStateOf(false) }
    var showStudyTools by remember { mutableStateOf(false) }
    var studyVerse by remember { mutableIntStateOf(1) }
    var showTextSizeDialog by remember { mutableStateOf(false) }
    var showNarratorPicker by remember { mutableStateOf(false) }
    var readerVisibleVerse by remember(state.bookId, state.chapter) { mutableIntStateOf(1) }

    Column(modifier.fillMaxSize()) {
        if (state.chapter == null) {
            PaneTopBar(
                state = state,
                paneIndex = paneIndex,
                library = library,
                syncMode = syncMode,
                onSyncToggle = onSyncToggle,
                onTranslationChange = { tid ->
                    onStateChange(state.copy(translation = tid, bookId = null, chapter = null))
                },
                onBack = {
                    when {
                        state.chapter != null -> onStateChange(state.copy(chapter = null))
                        state.bookId != null -> onStateChange(state.copy(bookId = null, chapter = null))
                        else -> {}
                    }
                },
                onExit = onExit,
                onClosePane = onClosePane,
                onAdjustFontScale = onAdjustReaderFontScale,
                readerFontScale = readerFontScale,
                onLongPress = onLongPressTopBar,
                onTitleClick = onOpenQuickNav ?: { showQuickNav = true },
                showSyncControl = showSyncControl,
                showInternalBack = showInternalBack,
                includeStatusBar = onExit != null && paneIndex == 0,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        }

        when {
            state.bookId == null -> {
                BookSelectionGrid(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onBookClick = { id ->
                        onStateChange(state.copy(bookId = id, chapter = null))
                    },
                )
            }
            state.chapter == null -> {
                val bid = state.bookId!!
                when (val shellState = rememberBookShell(library, state.translation, bid)) {
                    BibleBookShellState.Loading -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is BibleBookShellState.Ready -> {
                        ChapterPickerPane(
                            modifier = Modifier.weight(1f),
                            book = shellState.book,
                            bookId = bid,
                            isOnlineTranslation = false,
                            onChapter = { ch ->
                                onStateChange(state.copy(chapter = ch))
                            },
                            onBack = { onStateChange(state.copy(bookId = null, chapter = null)) },
                        )
                    }
                    is BibleBookShellState.Fallback -> {
                        ChapterPickerPane(
                            modifier = Modifier.weight(1f),
                            book = shellState.book,
                            bookId = bid,
                            isOnlineTranslation = shellState.isOnlineOnly,
                            onChapter = { ch ->
                                onStateChange(state.copy(chapter = ch))
                            },
                            onBack = { onStateChange(state.copy(bookId = null, chapter = null)) },
                        )
                    }
                }
            }
            else -> {
                val bid = state.bookId!!
                val chapterNum = state.chapter!!
                val chapterLoad = rememberLoadedChapter(library, state.translation, bid, chapterNum)
                val bookShell = rememberBookShell(library, state.translation, bid)
                val canonReader = BibleCanon.byId(bid)!!
                val bookName = when (chapterLoad) {
                    is BibleChapterLoadState.Ready -> chapterLoad.bookName
                    else -> when (bookShell) {
                        is BibleBookShellState.Ready -> bookShell.book.name
                        is BibleBookShellState.Fallback -> bookShell.book.name
                        BibleBookShellState.Loading -> BibleCanon.displayName(canonReader, state.translation)
                    }
                }
                val localChapter = when (chapterLoad) {
                    is BibleChapterLoadState.Ready -> chapterLoad.chapter
                    else -> null
                }
                val isOnlinePane = when (bookShell) {
                    is BibleBookShellState.Fallback -> bookShell.isOnlineOnly
                    else -> false
                }

                var onlineVerses by remember(state.translation, bid, state.chapter) {
                    mutableStateOf<List<BibleVerse>?>(null)
                }
                var onlineLoading by remember(state.translation, bid, state.chapter) { mutableStateOf(false) }
                var onlineError by remember(state.translation, bid, state.chapter) { mutableStateOf<String?>(null) }

                LaunchedEffect(state.translation, bid, state.chapter, isOnlinePane) {
                    if (!isOnlinePane) return@LaunchedEffect
                    onlineLoading = true
                    onlineError = null
                    onlineVerses = null
                    try {
                        val code = state.translation.onlineCode!!
                        val raw = com.example.bible.data.StudyBibleRepository
                            .fetchChapterText(code, bid, state.chapter!!)
                        val verses = raw.map { (n, t) -> BibleVerse(n, t) }
                        if (verses.isNotEmpty()) {
                            onlineVerses = verses
                        } else {
                            onlineError = "Не удалось загрузить текст"
                        }
                    } catch (e: Exception) {
                        onlineError = e.message ?: "Ошибка загрузки"
                    } finally {
                        onlineLoading = false
                    }
                }

                val effectiveVerses = localChapter?.verses ?: onlineVerses
                val totalChapters = when (bookShell) {
                    is BibleBookShellState.Ready -> bookShell.book.chapters.size
                    is BibleBookShellState.Fallback -> bookShell.book.chapters.size
                    else -> canonReader.chapters
                }
                val chrome = readerChrome
                val readingAudioNarrator = remember(state.translation, chrome?.narratorId, bid) {
                    narratorForReading(state.translation, bid, chrome?.narratorId ?: "bondarenko")
                }
                val hasChapterTimemarks = remember(state.translation, bid, chapterNum, paneContext) {
                    TimemarkStore.hasTimemarksForChapter(
                        paneContext,
                        state.translation.code,
                        bid,
                        chapterNum,
                    )
                }

                Column(Modifier.weight(1f).fillMaxSize()) {
                    if (chrome != null) {
                        val internalBack = if (showInternalBack && onExit == null) {
                            {
                                when {
                                    state.chapter != null -> onStateChange(state.copy(chapter = null))
                                    state.bookId != null -> onStateChange(state.copy(bookId = null, chapter = null))
                                }
                            }
                        } else {
                            null
                        }
                        DualPaneChapterHeader(
                            paneIndex = paneIndex,
                            state = state,
                            bookId = bid,
                            chapterNum = chapterNum,
                            totalChapters = totalChapters,
                            visibleVerse = readerVisibleVerse,
                            onStateChange = onStateChange,
                            syncMode = syncMode,
                            onSyncToggle = onSyncToggle,
                            onExit = onExit,
                            onInternalBack = internalBack,
                            onClosePane = onClosePane,
                            onLongPress = onLongPressTopBar,
                            onOpenQuickNav = { showQuickNav = true },
                            translationTabColors = translationTabColors,
                            readerChrome = chrome,
                            readingAudioNarrator = readingAudioNarrator,
                            hasChapterTimemarks = hasChapterTimemarks,
                            onAdjustFontScale = onAdjustReaderFontScale,
                            onShowTextSizeDialog = { showTextSizeDialog = true },
                            onShowNarratorPicker = { showNarratorPicker = true },
                            onShowStudyTools = { verse ->
                                studyVerse = verse
                                showStudyTools = true
                            },
                            includeStatusBar = includeReaderStatusBar ?: (onExit != null && paneIndex == 0),
                            showSyncInMenu = showSyncControl,
                        )
                    } else {
                        PaneTopBar(
                            state = state,
                            paneIndex = paneIndex,
                            library = library,
                            syncMode = syncMode,
                            onSyncToggle = onSyncToggle,
                            onTranslationChange = { tid ->
                                onStateChange(state.copy(translation = tid))
                            },
                            onBack = { onStateChange(state.copy(chapter = null)) },
                            onExit = onExit,
                            onClosePane = onClosePane,
                            onAdjustFontScale = onAdjustReaderFontScale,
                            readerFontScale = readerFontScale,
                            onLongPress = onLongPressTopBar,
                            onTitleClick = onOpenQuickNav ?: { showQuickNav = true },
                            showSyncControl = showSyncControl,
                            showInternalBack = showInternalBack,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    }

                when {
                    effectiveVerses != null && effectiveVerses.isNotEmpty() -> {
                        ReaderPane(
                            modifier = Modifier.weight(1f),
                            paneIndex = paneIndex,
                            verses = effectiveVerses,
                            scrollSync = scrollSync,
                            onScrollSync = onScrollSync,
                            translation = state.translation,
                            bookId = bid,
                            bookName = bookName,
                            chapterNum = state.chapter!!,
                            bookmarkKeys = bookmarkKeys,
                            onToggleBookmark = onToggleBookmark,
                            syncMode = syncMode,
                            textHighlights = textHighlights,
                            onAddTextHighlight = onAddTextHighlight,
                            onRemoveTextHighlights = onRemoveTextHighlights,
                            onVerseCommentary = onVerseCommentary,
                            onPlayAudio = onPlayAudio,
                            onPauseMainAudioForAttachment = onPauseMainAudioForAttachment,
                            readerFontScale = readerFontScale,
                            mediaLibraryImages = mediaLibraryImages,
                            userNotes = userNotes,
                            semanticHighlightSession = semanticHighlightSession,
                            userLexiconRules = userLexiconRules,
                            presetLexiconRules = presetLexiconRules,
                            presetLexiconEnabled = presetLexiconEnabled,
                            mediaLibraryVideos = mediaLibraryVideos,
                            mediaLibraryAudios = mediaLibraryAudios,
                            wordSpanMediaAttachments = wordSpanMediaAttachments,
                            onUpsertWordSpanMedia = onUpsertWordSpanMedia,
                            onDeleteWordSpanMedia = onDeleteWordSpanMedia,
                            onRemoveWordSpanMediaIntersecting = onRemoveWordSpanMediaIntersecting,
                            onVerseNote = onVerseNote,
                            onOpenVerseNote = onOpenVerseNote,
                            scrollToVerseRequest = scrollToVerseRequest,
                            onPlayChapterFromVerse = onPlayChapterFromVerse,
                            viewModel = viewModel,
                            onOpenDeepSeekSettings = onOpenDeepSeekSettings,
                    onOpenGigaChatSettings = onOpenGigaChatSettings,
                        )
                    }
                    !isOnlinePane && chapterLoad is BibleChapterLoadState.Loading -> {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                    isOnlinePane && onlineLoading -> {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Загрузка ${state.translation.labelRu}…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                    isOnlinePane && onlineError != null -> {
                        Column(Modifier.weight(1f).padding(16.dp)) {
                            Text(onlineError ?: "Ошибка", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { onStateChange(state.copy(chapter = null)) }) {
                                Text(stringResource(R.string.back))
                            }
                        }
                    }
                    else -> {
                        Column(Modifier.weight(1f).padding(16.dp)) {
                            Text(
                                stringResource(R.string.no_chapters_loaded),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            TextButton(onClick = { onStateChange(state.copy(chapter = null)) }) {
                                Text(stringResource(R.string.back))
                            }
                        }
                    }
                }
                }
            }
        }
    }
    if (showQuickNav) {
        QuickNavigatorSheet(
            library = library,
            translation = state.translation,
            currentBookId = state.bookId.orEmpty(),
            onNavigate = { bookId, chapter ->
                onStateChange(state.copy(bookId = bookId, chapter = chapter))
                showQuickNav = false
            },
            onDismiss = { showQuickNav = false },
        )
    }
    val vm = viewModel
    if (showStudyTools && vm != null && state.bookId != null && state.chapter != null) {
        val bid = state.bookId!!
        val chapterNum = state.chapter!!
        val bookShell = rememberBookShell(library, state.translation, bid)
        val bookName = when (bookShell) {
            is BibleBookShellState.Ready -> bookShell.book.name
            is BibleBookShellState.Fallback -> bookShell.book.name
            else -> BibleCanon.displayName(BibleCanon.byId(bid)!!, state.translation)
        }
        val chapterLoad = rememberLoadedChapter(library, state.translation, bid, chapterNum)
        val totalVerses = when (chapterLoad) {
            is BibleChapterLoadState.Ready -> chapterLoad.chapter.verses.size
            else -> 0
        }
        StudyToolsSheet(
            translation = state.translation,
            bookId = bid,
            bookName = bookName,
            chapter = chapterNum,
            verse = studyVerse,
            viewModel = vm,
            onDismiss = { showStudyTools = false },
            totalVerses = totalVerses,
        )
    }
    if (showTextSizeDialog && vm != null) {
        TextSizeSettingsDialog(
            viewModel = vm,
            onDismiss = { showTextSizeDialog = false },
        )
    }
    if (showNarratorPicker && vm != null) {
        NarratorPickerDialog(
            currentId = readerChrome?.narratorId ?: "bondarenko",
            onSelect = { vm.setAudioNarrator(it) },
            onDismiss = { showNarratorPicker = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterPickerPane(
    modifier: Modifier = Modifier,
    book: BibleBook,
    bookId: String = book.id,
    isOnlineTranslation: Boolean = false,
    onChapter: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val presence = rememberTimemarkPresenceIndex()
    val tabColors = rememberTranslationTabColorsMap()
    var infoChapter by remember { mutableStateOf<Int?>(null) }
    Column(modifier.fillMaxSize()) {
        if (book.chapters.isEmpty()) {
            Text(
                stringResource(R.string.no_chapters_loaded),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                gridItems(book.chapters, key = { it.number }) { ch: BibleChapter ->
                    val verseCount = ch.verses.size
                    val chapterCodes = presence.forChapter(bookId, ch.number)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .border(
                                0.5.dp,
                                if (isOnlineTranslation) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(8.dp),
                            )
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = { onChapter(ch.number) },
                                onLongClick = { infoChapter = ch.number },
                            )
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "${ch.number}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        if (!isOnlineTranslation || verseCount > 0) {
                            Text(
                                "$verseCount",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                            )
                        } else {
                            Text(
                                "⟳",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                            )
                        }
                        TimemarkPresenceDots(
                            translationCodes = chapterCodes,
                            tabColors = tabColors,
                            size = 7.dp,
                        )
                    }
                }
            }
        }
    }
    infoChapter?.let { chapterNum ->
        TimemarkTranslationsDialog(
            title = "${book.name}, гл. $chapterNum",
            translationCodes = presence.forChapter(bookId, chapterNum),
            tabColors = tabColors,
            onDismiss = { infoChapter = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderPane(
    modifier: Modifier = Modifier,
    paneIndex: Int,
    verses: List<BibleVerse>,
    scrollSync: ScrollSyncState,
    onScrollSync: (Int, Int) -> Unit,
    translation: TranslationId,
    bookId: String,
    bookName: String,
    chapterNum: Int,
    bookmarkKeys: Set<String>,
    onToggleBookmark: (VerseRef) -> Unit,
    syncMode: Boolean,
    textHighlights: List<TextHighlight>,
    onAddTextHighlight: (TextHighlight) -> Unit,
    onRemoveTextHighlights: (VerseRef, Int, Int) -> Unit,
    onVerseCommentary: (VerseRef) -> Unit,
    onPlayAudio: ((VerseRef, () -> Unit) -> Unit)? = null,
    onPauseMainAudioForAttachment: () -> Unit = {},
    readerFontScale: Float,
    mediaLibraryImages: List<BibleUserImage> = emptyList(),
    userNotes: List<UserNote> = emptyList(),
    semanticHighlightSession: SemanticHighlightSession? = null,
    userLexiconRules: List<SemanticLexiconRule> = emptyList(),
    presetLexiconRules: List<SemanticLexiconRule> = emptyList(),
    presetLexiconEnabled: Boolean = true,
    mediaLibraryVideos: List<BibleUserVideo> = emptyList(),
    mediaLibraryAudios: List<BibleUserAudio> = emptyList(),
    wordSpanMediaAttachments: List<WordSpanMediaAttachment> = emptyList(),
    onUpsertWordSpanMedia: (WordSpanMediaAttachment) -> Unit = {},
    onDeleteWordSpanMedia: (String) -> Unit = {},
    onRemoveWordSpanMediaIntersecting: (VerseRef, Int, Int) -> Unit = { _, _, _ -> },
    onVerseNote: ((VerseRef, bookName: String, verseText: String) -> Unit)? = null,
    onOpenVerseNote: ((String) -> Unit)? = null,
    scrollToVerseRequest: VerseScrollRequest? = null,
    onPlayChapterFromVerse: ((ScriptureAudioPlayRequest) -> Unit)? = null,
    viewModel: BibleViewModel? = null,
    onOpenDeepSeekSettings: () -> Unit = {},
    onOpenGigaChatSettings: () -> Unit = onOpenDeepSeekSettings,
) {
    val verseNumbersWithNotes = remember(userNotes, bookId, chapterNum) {
        userNotes.verseNumbersWithNotesInChapter(bookId, chapterNum)
    }
    val highlightsForPane = remember(textHighlights, translation, bookId, chapterNum) {
        textHighlights.filter {
            it.translation == translation && it.bookId == bookId && it.chapter == chapterNum
        }
    }
    var selectionInfo by remember { mutableStateOf<VerseHighlightSelection?>(null) }
    var clearSelectionSignal by remember { mutableIntStateOf(0) }
    var verseActionsTarget by remember { mutableStateOf<VerseActionTarget?>(null) }
    var verseRangeCopy by remember { mutableStateOf<VerseRangeCopyRequest?>(null) }
    var deepSeekTarget by remember { mutableStateOf<VerseActionTarget?>(null) }
    var gigaChatTarget by remember { mutableStateOf<VerseActionTarget?>(null) }
    var gigaChatInitialScope by remember { mutableStateOf<com.example.bible.data.DeepSeekPassageScope?>(null) }
    var gigaChatInitialRange by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val multiSelect = rememberVerseMultiSelectState()
    var attachmentPreview by remember { mutableStateOf<VerseAttachment?>(null) }
    val paneContext = LocalContext.current
    val attachmentStore = remember { VerseAttachmentStore.get(paneContext) }
    val attachmentIndexTick by attachmentStore.attachmentIndexVersion.collectAsState()
    val dictManager = remember { DictionaryManager.getInstance(paneContext) }
    var dictionaryLookup by remember { mutableStateOf<LexiconDictionarySheetState?>(null) }
    var dictionarySeeAlso by remember { mutableStateOf<List<String>>(emptyList()) }
    val speak = rememberVerseTextToSpeech(translation)

    LaunchedEffect(bookId, chapterNum, translation) {
        multiSelect.clear()
    }
    val chapterVerseTexts = remember(verses) { verses.associate { it.number to it.text } }

    val lexiconById = remember(userLexiconRules, presetLexiconRules) {
        (userLexiconRules + presetLexiconRules).associateBy { it.id }
    }

    val spanMediaForChapter = remember(wordSpanMediaAttachments, translation, bookId, chapterNum) {
        wordSpanMediaAttachments.filter {
            it.translation == translation && it.bookId == bookId && it.chapter == chapterNum
        }
    }
    var wordMediaDialog by remember { mutableStateOf<Pair<VerseHighlightSelection, WordSpanMediaAttachment?>?>(null) }

    val interlinearTts = remember(translation) {
        if (translation == TranslationId.INTERLINEAR) InterlinearTts(paneContext.applicationContext) else null
    }
    DisposableEffect(interlinearTts) {
        onDispose { interlinearTts?.shutdown() }
    }
    val bibleChapterAudioState by com.example.bible.data.BibleAudioPlayer.state.collectAsState()

    ProvideReaderFontScale(multiplier = readerFontScale) {
        val darkBodyStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
            fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
        )

        val listState = rememberLazyListState()
        val isSyncScrolling = remember { mutableStateOf(false) }
        val activeLinkRequest = scrollToVerseRequest?.takeIf { it.matchesChapter(bookId, chapterNum) }
        val highlightVerses = activeLinkRequest?.verses.orEmpty()
        val displayVerses = remember(verses, activeLinkRequest, multiSelect.isActive) {
            val request = activeLinkRequest
            if (multiSelect.isActive) {
                verses
            } else if (request != null && !request.showFullChapter && request.verses.isNotEmpty()) {
                verses.filter { it.number in request.verses }
            } else {
                verses
            }
        }

        LaunchedEffect(scrollToVerseRequest?.nonce, displayVerses, verses.size) {
            val request = scrollToVerseRequest ?: return@LaunchedEffect
            if (!request.matchesChapter(bookId, chapterNum)) return@LaunchedEffect
            val parsedNav = ParsedScriptureNavigation(
                bookId = request.bookId,
                chapter = request.chapter,
                verses = request.verses,
                audio = request.translationCode?.let { code ->
                    ScriptureAudioNavigation(
                        mode = request.audioPlayMode,
                        translationCode = code,
                        segmentSpec = request.audioSegmentSpec,
                    )
                },
            )
            val audioSegment = if (request.playAudio) {
                NoteScriptureLinks.resolveAudioSegment(
                    parsedNav,
                    request.audioSegmentIndex,
                    verses.size,
                )
            } else {
                null
            }
            val allSegments = if (request.playAudio) {
                NoteScriptureLinks.allAudioSegmentsForNavigation(parsedNav, verses.size)
            } else {
                emptyList()
            }
            val target = audioSegment?.startVerse ?: request.scrollToVerse()
            if (target <= 0) return@LaunchedEffect
            val verseIdx = displayVerses.indexOfFirst { it.number == target }
            if (verseIdx < 0) return@LaunchedEffect
            val listIndex = verseIdx + READER_LAZY_HEADER_ITEMS
            listState.scrollToItem(listIndex, scrollOffset = 0)
            if (request.playAudio && audioSegment != null) {
                onPlayChapterFromVerse?.invoke(
                    ScriptureAudioPlayRequest(
                        startVerse = audioSegment.startVerse,
                        stopAfterVerse = audioSegment.endVerseInclusive,
                        playToChapterEnd = audioSegment.endVerseInclusive == null,
                        chapterVerseCount = verses.size,
                        allSegments = allSegments,
                        startSegmentIndex = request.audioSegmentIndex,
                    ),
                )
            }
        }

        // Receive sync from other pane
        LaunchedEffect(scrollSync) {
            if (!syncMode) return@LaunchedEffect
            if (scrollSync.sourcePane == paneIndex) return@LaunchedEffect
            if (scrollSync.version == 0L) return@LaunchedEffect
            val idx = scrollSync.itemIndex.coerceIn(0, (verses.size - 1).coerceAtLeast(0))
            isSyncScrolling.value = true
            listState.scrollToItem(idx, scrollSync.scrollOffset)
            isSyncScrolling.value = false
        }

        // Emit scroll position to sync other pane
        LaunchedEffect(listState, syncMode) {
            if (!syncMode) return@LaunchedEffect
            snapshotFlow {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }
                .collect { (idx, offset) ->
                    if (isSyncScrolling.value) return@collect
                    if (!listState.isScrollInProgress) return@collect
                    onScrollSync(idx, offset)
                }
        }

        val multiSelectPad = if (multiSelect.isActive && multiSelect.count > 0) 56.dp else 0.dp
        val bottomPad = (if (selectionInfo != null) 88.dp else 0.dp) + multiSelectPad
        val interlinearChapterWordStarts = remember(displayVerses) {
            var acc = 0
            buildList {
                for (v in displayVerses) {
                    add(acc)
                    acc += v.interlinearWords?.size ?: 0
                }
            }
        }
        Box(modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(bottom = bottomPad),
            ) {
                items(
                    listOf("interlinear_chapter_tts"),
                    key = { it },
                ) {
                    if (translation == TranslationId.INTERLINEAR) {
                        val origNarrator = remember(bookId) {
                            com.example.bible.data.originalLanguageNarratorForBook(bookId)
                        }
                        if (origNarrator != null) {
                            val chapterAudioLabel = if (BibleCanon.isOldTestament(bookId)) {
                                stringResource(R.string.menu_audio_hebrew_chapter)
                            } else {
                                stringResource(R.string.menu_audio_greek_chapter)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val st = bibleChapterAudioState
                                        val isSame = st.isPlaying &&
                                            st.bookId == bookId &&
                                            st.chapter == chapterNum &&
                                            st.narratorId == origNarrator.id
                                        if (isSame) {
                                            com.example.bible.data.BibleAudioPlayer.togglePlay()
                                        } else {
                                            interlinearTts?.stop()
                                            com.example.bible.data.BibleAudioPlayer.playChapter(
                                                paneContext,
                                                origNarrator,
                                                bookId,
                                                chapterNum,
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(chapterAudioLabel, maxLines = 2)
                                }
                                TextButton(onClick = {
                                    interlinearTts?.stop()
                                    com.example.bible.data.BibleAudioPlayer.release()
                                }) {
                                    Text(stringResource(R.string.interlinear_stop_speech))
                                }
                            }
                        } else if (interlinearTts != null) {
                            val allWords = verses.flatMap { v -> v.interlinearWords ?: emptyList() }
                            if (allWords.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FilledTonalButton(
                                        onClick = { interlinearTts.speakSequence(allWords, bookId) },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            stringResource(R.string.interlinear_speak_chapter_sequence),
                                            maxLines = 2,
                                        )
                                    }
                                    TextButton(onClick = { interlinearTts.stop() }) {
                                        Text(stringResource(R.string.interlinear_stop_speech))
                                    }
                                }
                            }
                        }
                    }
                }
                itemsIndexed(
                    displayVerses,
                    key = { _, v -> v.number },
                ) { verseIdx, verse ->
                    val verseRef = VerseRef(translation, bookId, chapterNum, verse.number)
                    val isLinkHighlight = verse.number in highlightVerses
                    val notesHere = remember(userNotes, verseRef) {
                        userNotes.filter { it.matchesVerseLocation(verseRef) }.sortedByDescending { it.updatedAt }
                    }
                    val firstNoteId = notesHere.firstOrNull()?.id
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isLinkHighlight) {
                                    Modifier
                                        .padding(horizontal = 2.dp, vertical = 1.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                            RoundedCornerShape(8.dp),
                                        )
                                        .padding(horizontal = 4.dp, vertical = 3.dp)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        verse.imageUrl?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                        if (verse.interlinearWords != null && interlinearTts != null) {
                            val interlinearAttachments = remember(verseRef, attachmentIndexTick) {
                                attachmentStore.listFor(verseRef)
                            }
                            val interlinearFirstImage = remember(interlinearAttachments) {
                                interlinearAttachments.firstOrNull { it.kind() == AttachmentKind.Image }
                            }
                            InterlinearVerseContent(
                                words = verse.interlinearWords,
                                verseNumber = verse.number,
                                interlinearTts = interlinearTts,
                                bookId = bookId,
                                interlinearChapterWordOffset = interlinearChapterWordStarts.getOrElse(verseIdx) { 0 },
                                verseRef = verseRef,
                                onVerseNumberClick = {
                                    verseNumberClick(verse.number, multiSelect) {
                                        verseActionsTarget = VerseActionTarget(
                                            ref = verseRef,
                                            verseText = verse.text,
                                            bookName = bookName,
                                        )
                                    }
                                },
                                onVerseNumberLongPress = {
                                    verseNumberLongClick(verse.number, multiSelect) {
                                        interlinearFirstImage?.let { attachmentPreview = it }
                                    }
                                },
                                onAttachmentImageClick = { attachmentPreview = it },
                                verseNumberColor = MaterialTheme.colorScheme.primary,
                                hasVerseNote = verse.number in verseNumbersWithNotes,
                                onVerseNoteIconClick = if (firstNoteId != null && onOpenVerseNote != null) {
                                    { onOpenVerseNote(firstNoteId) }
                                } else {
                                    null
                                },
                            )
                        } else {
                            val verseAttachments = remember(verseRef, attachmentIndexTick) {
                                attachmentStore.listFor(verseRef)
                            }
                            val firstImageAtt = remember(verseAttachments) {
                                verseAttachments.firstOrNull { it.kind() == AttachmentKind.Image }
                            }
                            val spanMediaThisVerse = remember(wordSpanMediaAttachments, verseRef) {
                                wordSpanMediaAttachments.filter { it.matchesVerse(verseRef) }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        "${verse.number}",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .padding(end = 3.dp, top = 2.dp)
                                            .verseNumberSelectionHighlight(multiSelect.isSelected(verse.number))
                                            .combinedClickable(
                                                onClick = {
                                                    verseNumberClick(verse.number, multiSelect) {
                                                        verseActionsTarget = VerseActionTarget(
                                                            ref = verseRef,
                                                            verseText = verse.text,
                                                            bookName = bookName,
                                                        )
                                                    }
                                                },
                                                onLongClick = {
                                                    verseNumberLongClick(verse.number, multiSelect) {
                                                        firstImageAtt?.let { attachmentPreview = it }
                                                    }
                                                },
                                            ),
                                    )
                                    VerseAttachmentIndicator(
                                        verseRef = verseRef,
                                        iconTint = MaterialTheme.colorScheme.primary,
                                        thumbBorderColor = MaterialTheme.colorScheme.outline,
                                        onImageClick = { attachmentPreview = it },
                                    )
                                    if (verse.number in verseNumbersWithNotes) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                                            contentDescription = stringResource(R.string.verse_has_personal_note),
                                            modifier = Modifier
                                                .size(12.dp)
                                                .then(
                                                    if (firstNoteId != null && onOpenVerseNote != null) {
                                                        Modifier.clickable {
                                                            onOpenVerseNote(firstNoteId)
                                                        }
                                                    } else {
                                                        Modifier
                                                    },
                                                ),
                                            tint = MaterialTheme.colorScheme.tertiary,
                                        )
                                    }
                                }
                                SelectableVerseText(
                                    verse = verse,
                                    verseRef = verseRef,
                                    highlights = highlightsForPane,
                                    activeVerseRef = selectionInfo?.ref,
                                    clearSelectionSignal = clearSelectionSignal,
                                    onSelectionRange = { start, end ->
                                        selectionInfo = VerseHighlightSelection(verseRef, start, end)
                                    },
                                    onSelectionCollapsed = {
                                        if (selectionInfo?.ref == verseRef) {
                                            selectionInfo = null
                                        }
                                    },
                                    onWordTap = { word, _, lexiconRuleId, charOffset ->
                                        val rule = lexiconRuleId?.let { lexiconById[it] }
                                        val attached = spanMediaForChapter.findForTap(verseRef, charOffset)
                                            ?.media
                                            ?.takeIf { it.hasAny() }
                                        val results = dictManager.searchAll(word)
                                        val builtIn = BibleDictionary.lookup(word)
                                        dictionarySeeAlso = builtIn?.seeAlso ?: emptyList()
                                        dictionaryLookup = LexiconDictionarySheetState(
                                            word,
                                            results,
                                            dictionarySeeAlso,
                                            rule,
                                            attached,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    textStyle = darkBodyStyle,
                                    semanticHighlightSession = semanticHighlightSession,
                                    userLexiconRules = userLexiconRules,
                                    presetLexiconRules = presetLexiconRules,
                                    presetLexiconEnabled = presetLexiconEnabled,
                                    wordSpanMediaForVerse = spanMediaThisVerse,
                                )
                            }
                        }
                    }
                }
            }
            selectionInfo?.let { sel ->
                ReaderHighlightToolbar(
                    selection = sel,
                    onApply = { mode, argb ->
                        onAddTextHighlight(
                            TextHighlight(
                                translation = translation,
                                bookId = bookId,
                                chapter = chapterNum,
                                verse = sel.ref.verse,
                                startOffset = sel.start,
                                endOffset = sel.end,
                                isBackground = mode == ReaderHighlightMode.BACKGROUND,
                                underline = mode == ReaderHighlightMode.UNDERLINE,
                                colorArgb = argb,
                            ),
                        )
                        clearSelectionSignal++
                        selectionInfo = null
                    },
                    onRemoveOverlapping = {
                        onRemoveTextHighlights(sel.ref, sel.start, sel.end)
                        onRemoveWordSpanMediaIntersecting(sel.ref, sel.start, sel.end)
                        clearSelectionSignal++
                        selectionInfo = null
                    },
                    onDismiss = {
                        clearSelectionSignal++
                        selectionInfo = null
                    },
                    onAttachMedia = {
                        val ex = spanMediaForChapter.find {
                            it.matchesVerse(sel.ref) && it.startOffset == sel.start && it.endOffset == sel.end
                        }
                        wordMediaDialog = sel to ex
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            if (multiSelect.isActive && multiSelect.count > 0) {
                VerseMultiSelectBottomBar(
                    selectedCount = multiSelect.count,
                    onCopy = {
                        copyVersesToClipboard(
                            context = paneContext,
                            bookName = bookName,
                            chapter = chapterNum,
                            verseNumbers = multiSelect.selectedVerses ?: emptySet(),
                            verseTextsByNumber = chapterVerseTexts,
                        )
                        multiSelect.clear()
                    },
                    onReflectGigaChat = viewModel?.let {
                        {
                            val selected = multiSelect.selectedVerses ?: return@let
                            if (selected.isEmpty()) return@let
                            val first = selected.min()
                            val verse = verses.firstOrNull { it.number == first }
                            gigaChatTarget = VerseActionTarget(
                                ref = VerseRef(translation, bookId, chapterNum, first),
                                verseText = verse?.text.orEmpty(),
                                bookName = bookName,
                            )
                            gigaChatInitialScope = com.example.bible.data.DeepSeekPassageScope.RANGE
                            gigaChatInitialRange = selected.min() to selected.max()
                            multiSelect.clear()
                        }
                    },
                    onCancel = { multiSelect.clear() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        wordMediaDialog?.let { (sel, existing) ->
            WordMediaAttachmentDialog(
                title = stringResource(R.string.word_media_dialog_title),
                initialMedia = existing?.media ?: LexiconMediaRefs(),
                bibleUserImages = mediaLibraryImages,
                bibleUserVideos = mediaLibraryVideos,
                bibleUserAudios = mediaLibraryAudios,
                showDelete = existing != null,
                onDismiss = { wordMediaDialog = null },
                onSave = { med ->
                    onUpsertWordSpanMedia(
                        WordSpanMediaAttachment(
                            id = existing?.id ?: newWordSpanMediaId(),
                            translation = translation,
                            bookId = bookId,
                            chapter = chapterNum,
                            verse = sel.ref.verse,
                            startOffset = sel.start,
                            endOffset = sel.end,
                            media = med,
                        ),
                    )
                },
                onDelete = { existing?.let { onDeleteWordSpanMedia(it.id) } },
            )
        }
        attachmentPreview?.let { att ->
            AttachmentPreviewDialog(
                attachment = att,
                onDismiss = { attachmentPreview = null },
                onPauseMainAudio = onPauseMainAudioForAttachment,
            )
        }
        VerseActionsBottomSheet(
            target = verseActionsTarget,
            bookmarkKeys = bookmarkKeys,
            onToggleBookmark = onToggleBookmark,
            onDismiss = { verseActionsTarget = null },
            speak = speak.speak,
            onStopSpeech = speak.stop,
            onPlayAudio = onPlayAudio,
            onOpenCommentary = { ref ->
                onVerseCommentary(ref)
            },
            onAskDeepSeek = viewModel?.let { { t: VerseActionTarget -> deepSeekTarget = t } },
            onAskGigaChat = viewModel?.let {
                { t: VerseActionTarget ->
                    gigaChatInitialScope = null
                    gigaChatInitialRange = null
                    gigaChatTarget = t
                }
            },
            onPauseMainAudioForAttachment = onPauseMainAudioForAttachment,
            mediaLibraryImages = mediaLibraryImages,
            mediaLibraryVideos = mediaLibraryVideos,
            mediaLibraryAudios = mediaLibraryAudios,
            onDictionaryWord = { word ->
                val results = dictManager.searchAll(word)
                val builtIn = BibleDictionary.lookup(word)
                dictionarySeeAlso = builtIn?.seeAlso ?: emptyList()
                dictionaryLookup = LexiconDictionarySheetState(word, results, dictionarySeeAlso, null, null)
            },
            userNotes = userNotes,
            onCreateNoteForVerse = onVerseNote?.let { fn ->
                { t: VerseActionTarget -> fn(t.ref, t.bookName, t.verseText) }
            },
            onOpenExistingVerseNote = onOpenVerseNote,
            onEnterMultiVerseSelect = { multiSelect.start(it) },
            translation = translation,
            chapterVerseCount = verses.size,
            chapterVerseTexts = chapterVerseTexts,
            onCopyVerseRange = { verseRangeCopy = it },
        )
        VerseRangeCopyDialogHost(
            request = verseRangeCopy,
            onDismiss = { verseRangeCopy = null },
        )
        val dsVm = viewModel
        if (dsVm != null) {
            deepSeekTarget?.let { t ->
                DeepSeekVerseDialog(
                    viewModel = dsVm,
                    target = t,
                    onDismiss = { deepSeekTarget = null },
                    onOpenSettings = {
                        deepSeekTarget = null
                        onOpenDeepSeekSettings()
                    },
                    chapterVerseCount = verses.size,
                    chapterVerseTexts = verses.associate { it.number to it.text },
                )
            }
            gigaChatTarget?.let { t ->
                GigaChatVerseDialog(
                    viewModel = dsVm,
                    target = t,
                    onDismiss = {
                        gigaChatTarget = null
                        gigaChatInitialScope = null
                        gigaChatInitialRange = null
                    },
                    onOpenSettings = {
                        gigaChatTarget = null
                        gigaChatInitialScope = null
                        gigaChatInitialRange = null
                        onOpenGigaChatSettings()
                    },
                    chapterVerseCount = verses.size,
                    chapterVerseTexts = verses.associate { it.number to it.text },
                    initialScope = gigaChatInitialScope ?: com.example.bible.data.DeepSeekPassageScope.VERSE,
                    initialRangeStart = gigaChatInitialRange?.first,
                    initialRangeEnd = gigaChatInitialRange?.second,
                )
            }
        }
        dictionaryLookup?.let { st ->
            MultiDictionarySheet(
                word = st.word,
                results = st.results,
                seeAlso = st.seeAlso,
                lexiconRule = st.lexiconRule,
                attachedMedia = st.attachedMedia,
                mediaLibraryImages = mediaLibraryImages,
                mediaLibraryVideos = mediaLibraryVideos,
                mediaLibraryAudios = mediaLibraryAudios,
                onWordClick = { w ->
                    val r = dictManager.searchAll(w)
                    val builtIn = BibleDictionary.lookup(w)
                    dictionarySeeAlso = builtIn?.seeAlso ?: emptyList()
                    dictionaryLookup = LexiconDictionarySheetState(w, r, dictionarySeeAlso, null, null)
                },
                onDismiss = { dictionaryLookup = null },
            )
        }
    }
}
