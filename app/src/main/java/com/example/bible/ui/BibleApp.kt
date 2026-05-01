package com.example.bible.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.net.Uri
import com.example.bible.data.KidsUserSectionsMerge
import com.example.bible.data.KidsTopicsRepository
import com.example.bible.R
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.example.bible.data.AudioPlaybackState
import com.example.bible.data.BibleCanon
import com.example.bible.data.BibleSearchHistoryEntry
import com.example.bible.data.BibleBook
import com.example.bible.data.BibleChapter
import com.example.bible.data.BibleDictionary
import com.example.bible.data.DictResult
import com.example.bible.data.DictionaryManager
import com.example.bible.data.DailyVerse
import com.example.bible.data.DailyVerseEntry
import com.example.bible.data.BibleLibrary
import com.example.bible.data.computeSearchHighlightRanges
import com.example.bible.data.BiblePreferences
import com.example.bible.data.BibleRepository
import com.example.bible.data.BibleVerse
import com.example.bible.data.SearchHit
import com.example.bible.data.TextHighlight
import com.example.bible.data.LexiconMediaRefs
import com.example.bible.data.WordSpanMediaAttachment
import com.example.bible.data.findForTap
import com.example.bible.data.newWordSpanMediaId
import com.example.bible.data.TranslationId
import com.example.bible.data.AttachmentKind
import com.example.bible.data.VerseAttachment
import com.example.bible.data.VerseAttachmentStore
import com.example.bible.data.HistoryEntry
import com.example.bible.data.LastSessionResumeKind
import com.example.bible.data.ReadingTraceEntry
import com.example.bible.data.isValidForRestore
import com.example.bible.data.ResumePersistAction
import com.example.bible.data.resumePersistActionForNavDestination
import com.example.bible.data.InterlinearTts
import com.example.bible.data.UserNote
import com.example.bible.data.VerseRef
import com.example.bible.data.matchesVerseLocation
import com.example.bible.data.verseNumbersWithNotesInChapter
import coil.compose.AsyncImage
import com.example.bible.ui.theme.BibleTheme
import com.example.bible.ui.theme.ThemedWindowBackdrop
import com.example.bible.ui.travel.MyTravelsScreen
import com.example.bible.ui.church.ChurchMemberEditScreen
import com.example.bible.ui.church.ChurchParticipantsScreen
import com.example.bible.ui.church.ChurchPlaceholderScreen
import com.example.bible.ui.church.ChurchViewModel
import com.example.bible.ui.church.MyChurchHubScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.bible.data.BibleAudioNarrators
import com.example.bible.data.BibleAudioPlayer
import com.example.bible.data.chapterCountForDownloadEntireBible
import com.example.bible.data.TimemarkProject
import com.example.bible.data.TimemarkStore
import com.example.bible.data.verseNumberAtChapterAudioPosition

/** Контуры мимики: отдельная область композиции, чтобы обновление кадра не инвалидировало весь NavHost. */
@Composable
private fun HoistedMimicFaceOverlay(
    state: MutableState<ExperimentHandMotionFrame?>,
) {
    MimicFacePreviewOverlay(
        frame = state.value,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
fun BibleApp(
    repository: BibleRepository,
    preferences: BiblePreferences,
) {
    val context = LocalContext.current
    val viewModel: BibleViewModel = viewModel(
        factory = BibleViewModelFactory(context, repository, preferences),
    )
    val darkModePref by viewModel.darkMode.collectAsStateWithLifecycle()
    val appThemePreset by viewModel.appThemePreset.collectAsStateWithLifecycle()
    val mimicControlOn by viewModel.mimicControlEnabled.collectAsStateWithLifecycle(false)
    val mimicControlV2On by viewModel.mimicControlV2Enabled.collectAsStateWithLifecycle(false)
    val mimicCameraPreviewOn by viewModel.mimicCameraPreviewEnabled.collectAsStateWithLifecycle(false)
    val mimicFaceOverlayOn by viewModel.mimicFaceOverlayEnabled.collectAsStateWithLifecycle(false)
    val mimicVelocityVectorOn by viewModel.mimicVelocityVectorVisible.collectAsStateWithLifecycle(false)
    val systemDark = isSystemInDarkTheme()
    val isDark = darkModePref ?: systemDark

    BibleTheme(darkTheme = isDark, appThemePreset = appThemePreset) {
        val navController = rememberNavController()
        val state by viewModel.state.collectAsStateWithLifecycle()
        var mimicCursor by remember { mutableStateOf<Offset?>(null) }
        var mimicPointerPressed by remember { mutableStateOf(false) }
        var mimicFallbackVelocityVector by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
        val mimicFaceOverlayFrame = remember { mutableStateOf<ExperimentHandMotionFrame?>(null) }
        var mimicCamGranted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED,
            )
        }
        val mimicCamLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> mimicCamGranted = granted }

        LaunchedEffect(mimicControlOn) {
            mimicCamGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
            if (mimicControlOn && !mimicCamGranted) {
                mimicCamLauncher.launch(Manifest.permission.CAMERA)
            }
            if (!mimicControlOn) {
                mimicCursor = null
                mimicPointerPressed = false
                mimicFallbackVelocityVector = null
                mimicFaceOverlayFrame.value = null
                MimicCursorOverlay.updateVelocityVector(null, null)
                MimicCursorOverlay.updatePointerPressed(false)
            }
        }

        LaunchedEffect(mimicVelocityVectorOn) {
            if (!mimicVelocityVectorOn) {
                mimicFallbackVelocityVector = null
                MimicCursorOverlay.updateVelocityVector(null, null)
            }
        }

        val mimicOnSnap by rememberUpdatedState(mimicControlOn)
        val mimicCamSnap by rememberUpdatedState(mimicCamGranted)
        val mimicDarkSnap by rememberUpdatedState(isDark)
        val mimicPresetSnap by rememberUpdatedState(appThemePreset)
        val mimicBibleReadySnap by rememberUpdatedState(state is BibleLoadState.Ready)
        val lifecycleOwner = LocalLifecycleOwner.current

        val bibleReady = state is BibleLoadState.Ready

        fun tryAttachMimicOverlay() {
            val act = context as? ComponentActivity ?: return
            val canOverlay = MimicCursorOverlay.canDrawOverlays(context)
            if (mimicOnSnap && mimicCamSnap && mimicBibleReadySnap && canOverlay) {
                MimicCursorOverlay.attach(act, mimicDarkSnap, mimicPresetSnap)
            } else {
                // Тема обновляется отдельно (LaunchedEffect); не отцеплять окно при смене isDark/preset.
                MimicCursorOverlay.detach()
            }
        }

        // Только флаги «нужен ли оверлей»; isDark/appThemePreset убраны — иначе detach во время Compose.
        DisposableEffect(mimicControlOn, mimicCamGranted, bibleReady) {
            tryAttachMimicOverlay()
            onDispose {
                MimicCursorOverlay.detach()
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    tryAttachMimicOverlay()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        LaunchedEffect(mimicCursor, mimicControlOn, mimicCamGranted) {
            if (mimicControlOn && mimicCamGranted && MimicCursorOverlay.canDrawOverlays(context)) {
                MimicCursorOverlay.updateCursor(mimicCursor)
            }
        }

        LaunchedEffect(mimicPointerPressed, mimicControlOn, mimicCamGranted) {
            if (mimicControlOn && mimicCamGranted && MimicCursorOverlay.canDrawOverlays(context)) {
                MimicCursorOverlay.updatePointerPressed(mimicPointerPressed)
            }
        }

        LaunchedEffect(isDark, appThemePreset, mimicControlOn, mimicCamGranted) {
            if (mimicControlOn && mimicCamGranted && MimicCursorOverlay.canDrawOverlays(context)) {
                MimicCursorOverlay.updateTheme(isDark, appThemePreset)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isDark) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent()
                            if (down.changes.size >= 3 && down.changes.all { it.pressed }) {
                                val startTime = down.changes.first().uptimeMillis
                                var held = true
                                while (held) {
                                    val event = awaitPointerEvent()
                                    val allPressed = event.changes.count { it.pressed }
                                    if (allPressed < 3) {
                                        val elapsed = event.changes.first().uptimeMillis - startTime
                                        if (elapsed >= 500L) {
                                            viewModel.toggleDarkMode(isDark)
                                        }
                                        held = false
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            ThemedWindowBackdrop(
                preset = appThemePreset,
                dark = isDark,
                modifier = Modifier.fillMaxSize(),
            )
            if (mimicControlOn && mimicCamGranted && state is BibleLoadState.Ready) {
                MimicControlBackdrop(
                    mimicControlV2 = mimicControlV2On,
                    showCameraPreview = mimicCameraPreviewOn,
                    showFaceOverlay = mimicFaceOverlayOn,
                    faceOverlayFrameState = mimicFaceOverlayFrame,
                    showVelocityVector = mimicVelocityVectorOn,
                    onNoseScreenPosition = { mimicCursor = it },
                    onVelocityVector = { c, t ->
                        if (!mimicVelocityVectorOn) {
                            MimicCursorOverlay.updateVelocityVector(null, null)
                            mimicFallbackVelocityVector = null
                        } else {
                            val p = if (c != null && t != null) Pair(c, t) else null
                            if (MimicCursorOverlay.canDrawOverlays(context)) {
                                MimicCursorOverlay.updateVelocityVector(p?.first, p?.second)
                                mimicFallbackVelocityVector = null
                            } else {
                                MimicCursorOverlay.updateVelocityVector(null, null)
                                mimicFallbackVelocityVector = p
                            }
                        }
                    },
                    onVerticalScrollDy = { viewModel.postMimicScrollDy(it) },
                    onMimicCancel = {
                        (context as? ComponentActivity)
                            ?.onBackPressedDispatcher
                            ?.onBackPressed()
                    },
                    onSyntheticPointerPressedChange = { mimicPointerPressed = it },
                )
            }
            when (val s = state) {
                BibleLoadState.Loading -> LoadingScreen()
                is BibleLoadState.Error -> ErrorScreen(
                    message = s.message,
                    onRetry = { viewModel.reload() },
                )
                is BibleLoadState.Ready -> BibleNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    library = s.library,
                    isDark = isDark,
                    mimicControlEnabled = mimicControlOn,
                    mimicVelocityVectorEnabled = mimicVelocityVectorOn,
                    onToggleMimicControl = { viewModel.toggleMimicControl() },
                    onToggleMimicVelocityVector = { viewModel.toggleMimicVelocityVectorVisible() },
                    preferences = preferences,
                )
            }
            if (mimicControlOn && mimicCamGranted && state is BibleLoadState.Ready && mimicFaceOverlayOn) {
                HoistedMimicFaceOverlay(mimicFaceOverlayFrame)
            }
            val fallbackCursor = mimicCursor
            if (mimicControlOn && mimicCamGranted && fallbackCursor != null &&
                !MimicCursorOverlay.canDrawOverlays(context)
            ) {
                val mimicClickPulse = MimicCursorOverlay.clickPulseState.intValue
                val lv = LocalView.current
                val loc = IntArray(2)
                lv.getLocationOnScreen(loc)
                val local = Offset(
                    fallbackCursor.x - loc[0],
                    fallbackCursor.y - loc[1],
                )
                MimicControlCursor(
                    noseCenter = local,
                    showPointerHalo = mimicPointerPressed,
                    clickPulseKey = mimicClickPulse,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            val fallbackVec = mimicFallbackVelocityVector
            if (mimicControlOn && mimicCamGranted && mimicVelocityVectorOn &&
                fallbackVec != null && !MimicCursorOverlay.canDrawOverlays(context)
            ) {
                val lv = LocalView.current
                val loc = IntArray(2)
                lv.getLocationOnScreen(loc)
                val (va, vb) = fallbackVec
                MimicVelocityVectorArrowLayer(
                    start = Offset(va.x - loc[0], va.y - loc[1]),
                    end = Offset(vb.x - loc[0], vb.y - loc[1]),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.bible_logo),
            contentDescription = "Библия",
            modifier = Modifier
                .size(200.dp)
                .padding(bottom = 24.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        Text(
            text = "Библия",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.error_load),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        FilledTonalButton(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BibleNavHost(
    navController: NavHostController,
    viewModel: BibleViewModel,
    library: BibleLibrary,
    isDark: Boolean,
    mimicControlEnabled: Boolean,
    mimicVelocityVectorEnabled: Boolean,
    onToggleMimicControl: () -> Unit,
    onToggleMimicVelocityVector: () -> Unit,
    preferences: BiblePreferences,
) {
    val translation by viewModel.selectedTranslation.collectAsStateWithLifecycle()
    val bookmarkKeys by viewModel.bookmarkKeys.collectAsStateWithLifecycle()
    val bookmarkTagsMap by viewModel.bookmarkTagsMap.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchListState by viewModel.searchListState.collectAsStateWithLifecycle(
        initialValue = BibleSearchListState(),
    )
    val bibleSearchHistory by viewModel.bibleSearchHistory.collectAsStateWithLifecycle()
    val textHighlights by viewModel.textHighlights.collectAsStateWithLifecycle()
    val wordSpanMediaAttachments by viewModel.wordSpanMediaAttachments.collectAsStateWithLifecycle()
    val readerFontScale by viewModel.readerFontScale.collectAsStateWithLifecycle()
    val audioPlaybackState by viewModel.audioPlaybackState.collectAsStateWithLifecycle()
    val audioPlaybackSpeed by viewModel.audioPlaybackSpeed.collectAsStateWithLifecycle()
    val readingHistory by viewModel.readingHistory.collectAsStateWithLifecycle()
    val readingTrace by viewModel.readingTrace.collectAsStateWithLifecycle()
    val narratorId by viewModel.audioNarratorId.collectAsStateWithLifecycle()
    val downloadTick by com.example.bible.data.BibleAudioPlayer.downloadTick.collectAsState()
    val booksMainMenuOrder by viewModel.booksMainMenuOrder.collectAsStateWithLifecycle()
    val appThemePreset by viewModel.appThemePreset.collectAsStateWithLifecycle()
    val mimicCameraPreviewEnabled by viewModel.mimicCameraPreviewEnabled.collectAsStateWithLifecycle(false)
    val mimicFaceOverlayEnabled by viewModel.mimicFaceOverlayEnabled.collectAsStateWithLifecycle(false)
    val mimicMediaPipeFaceGeometryEnabled by viewModel.mimicMediaPipeFaceGeometryEnabled.collectAsStateWithLifecycle(false)
    val mimicControlV2Enabled by viewModel.mimicControlV2Enabled.collectAsStateWithLifecycle(false)
    val kidsUserSections by viewModel.kidsUserSections.collectAsStateWithLifecycle()
    val ttsUserSettings by viewModel.ttsUserSettings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val navLifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(navLifecycleOwner, navController, translation) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                scope.launch {
                    val action = resumePersistActionForNavDestination(navController, translation)
                    preferences.applyResumePersistAction(action)
                }
            }
        }
        navLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { navLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    NavHost(
        navController = navController,
        startDestination = "books",
    ) {
        composable("books") {
            var menuOpen by remember { mutableStateOf(false) }
            var showTextSizeDialog by remember { mutableStateOf(false) }
            var bookLayoutMode by remember { mutableStateOf(BookLayoutMode.GRID) }
            var showBookNarratorPicker by remember { mutableStateOf(false) }
            var fullBibleDlConfirm by remember { mutableStateOf(false) }
            var fullBibleProgress by remember { mutableStateOf<Triple<Int, Int, String>?>(null) }
            val booksScreenContext = LocalContext.current
            val canPop = navController.previousBackStackEntry != null
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Column {
                        TopAppBar(
                            title = {
                                Text(
                                    stringResource(R.string.book_picker_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            },
                            navigationIcon = {
                                if (canPop) {
                                    IconButton(onClick = { navController.navigateUp() }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.back),
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        bookLayoutMode = if (bookLayoutMode == BookLayoutMode.GRID) {
                                            BookLayoutMode.LIST
                                        } else {
                                            BookLayoutMode.GRID
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = if (bookLayoutMode == BookLayoutMode.GRID) {
                                            Icons.AutoMirrored.Filled.List
                                        } else {
                                            Icons.Filled.ViewModule
                                        },
                                        contentDescription = stringResource(R.string.toggle_grid_list),
                                    )
                                }
                                IconButton(onClick = { fullBibleDlConfirm = true }) {
                                    Icon(
                                        Icons.Filled.CloudDownload,
                                        contentDescription = stringResource(R.string.download_full_bible_audio),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (canPop) {
                                    IconButton(onClick = { navController.navigateUp() }) {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                    }
                                }
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.translation_menu))
                                }
                                val booksMenuMaxH = (LocalConfiguration.current.screenHeightDp * 0.58f).dp
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                    modifier = Modifier.heightIn(max = booksMenuMaxH),
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.main_settings_title)) },
                                        onClick = { menuOpen = false; navController.navigate("main_settings") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Settings,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        },
                                    )
                                    HorizontalDivider()
                                    BooksMainMenuOrderedItems(
                                        menuOrder = booksMainMenuOrder,
                                        translation = translation,
                                        narratorId = narratorId,
                                        closeMenu = { menuOpen = false },
                                        navController = navController,
                                        onShowTextSizeDialog = { showTextSizeDialog = true },
                                        onShowBookNarratorPicker = { showBookNarratorPicker = true },
                                    )
                                    HorizontalDivider()
                                    TranslationId.entries.forEach { tid ->
                                        DropdownMenuItem(
                                            text = { Text(tid.labelRu) },
                                            onClick = {
                                                menuOpen = false
                                                scope.launch { viewModel.setTranslation(tid) }
                                            },
                                            trailingIcon = {
                                                if (tid == translation) Icon(Icons.Default.Check, contentDescription = null)
                                            },
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary)
                    }
                },
            ) { padding ->
                val topPad = padding.calculateTopPadding()
                val bottomPad = padding.calculateBottomPadding()
                val booksWithAudio = remember(translation, narratorId, downloadTick) {
                    val fromAssets = viewModel.booksWithAudio(translation)
                    val eff = com.example.bible.data.narratorForTranslation(translation, narratorId).id
                    val downloaded = viewModel.booksWithDownloadedAudio(eff)
                    fromAssets + downloaded
                }
                val dailyVerse = remember { DailyVerse.forToday() }
                Column(
                    modifier = if (bookLayoutMode == BookLayoutMode.GRID) {
                        Modifier
                            .fillMaxSize()
                            .padding(top = topPad, bottom = bottomPad)
                            .background(MaterialTheme.colorScheme.background)
                    } else {
                        Modifier
                            .padding(padding)
                            .background(MaterialTheme.colorScheme.background)
                    },
                ) {
                    DailyVerseCard(
                        entry = dailyVerse,
                        onClick = {
                            navController.navigate("read/${dailyVerse.bookId}/${dailyVerse.chapter}/${dailyVerse.verse}")
                        },
                    )
                    BookSelectionContent(
                        layoutMode = bookLayoutMode,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        booksWithAudio = booksWithAudio,
                        onBookClick = { bookId ->
                            navController.navigate("chapters/$bookId")
                        },
                    )
                }
            }
            if (showBookNarratorPicker) {
                NarratorPickerDialog(
                    currentId = narratorId,
                    onSelect = { id ->
                        viewModel.setAudioNarrator(id)
                        showBookNarratorPicker = false
                    },
                    onDismiss = { showBookNarratorPicker = false },
                )
            }
            if (fullBibleDlConfirm) {
                val fullBibleNarrator = com.example.bible.data.narratorForTranslation(translation, narratorId)
                AlertDialog(
                    onDismissRequest = { fullBibleDlConfirm = false },
                    title = {
                        Text(
                            when (fullBibleNarrator.id) {
                                "hebrew-ot" -> stringResource(R.string.download_ot_hebrew_audio_title)
                                "greek-nt" -> stringResource(R.string.download_nt_greek_audio_title)
                                else -> stringResource(R.string.download_full_bible_audio)
                            },
                        )
                    },
                    text = {
                        Text(
                            when (fullBibleNarrator.id) {
                                "hebrew-ot" -> stringResource(R.string.download_ot_hebrew_audio_confirm)
                                "greek-nt" -> stringResource(R.string.download_nt_greek_audio_confirm)
                                else -> stringResource(
                                    R.string.download_full_bible_audio_confirm,
                                    fullBibleNarrator.name,
                                )
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                fullBibleDlConfirm = false
                                scope.launch {
                                    try {
                                        val totalCh = chapterCountForDownloadEntireBible(fullBibleNarrator)
                                        fullBibleProgress = Triple(0, totalCh, "")
                                        BibleAudioPlayer.downloadEntireBible(
                                            booksScreenContext.applicationContext,
                                            fullBibleNarrator,
                                        ) { d, t, l ->
                                            fullBibleProgress = Triple(d, t, l)
                                        }
                                        Toast.makeText(
                                            booksScreenContext,
                                            "Озвучка сохранена на устройстве",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            booksScreenContext,
                                            e.message ?: "Ошибка загрузки",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    } finally {
                                        fullBibleProgress = null
                                    }
                                }
                            },
                        ) {
                            Text("Начать")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { fullBibleDlConfirm = false }) {
                            Text("Отмена")
                        }
                    },
                )
            }
            if (showTextSizeDialog) {
                TextSizeSettingsDialog(
                    viewModel = viewModel,
                    onDismiss = { showTextSizeDialog = false },
                )
            }
            fullBibleProgress?.let { prog ->
                val dlTitleNarrator = com.example.bible.data.narratorForTranslation(translation, narratorId)
                Dialog(onDismissRequest = {}) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                when (dlTitleNarrator.id) {
                                    "hebrew-ot" -> stringResource(R.string.download_ot_hebrew_audio_title)
                                    "greek-nt" -> stringResource(R.string.download_nt_greek_audio_title)
                                    else -> stringResource(R.string.download_full_bible_audio)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(12.dp))
                            val (done, total, label) = prog
                            LinearProgressIndicator(
                                progress = {
                                    if (total > 0) done.toFloat() / total else 0f
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.download_full_bible_progress, done, total),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (label.isNotBlank()) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        composable("dual") {
            val bibleUserImages by viewModel.bibleUserImages.collectAsStateWithLifecycle()
            val userNotes by viewModel.userNotes.collectAsStateWithLifecycle()
            val semanticHighlightSession by viewModel.semanticHighlightSession.collectAsStateWithLifecycle()
            val readerUserLexiconRules by viewModel.readerUserLexiconRules.collectAsStateWithLifecycle()
            val readerPresetLexiconRules by viewModel.readerPresetLexiconRules.collectAsStateWithLifecycle()
            val lexiconPresetEnabled by viewModel.lexiconPresetEnabled.collectAsStateWithLifecycle()
            val bibleUserVideos by viewModel.bibleUserVideos.collectAsStateWithLifecycle()
            val bibleUserAudios by viewModel.bibleUserAudios.collectAsStateWithLifecycle()
            val wordSpanMediaAttachments by viewModel.wordSpanMediaAttachments.collectAsStateWithLifecycle()
            DualBibleScreen(
                library = library,
                bookmarkKeys = bookmarkKeys,
                textHighlights = textHighlights,
                onAddTextHighlight = { viewModel.addTextHighlight(it) },
                onRemoveTextHighlights = { ref, a, b -> viewModel.removeTextHighlightsIntersecting(ref, a, b) },
                onToggleBookmark = { viewModel.toggleBookmark(it) },
                readerFontScale = readerFontScale,
                onAdjustReaderFontScale = { viewModel.adjustReaderFontScale(it) },
                onVerseCommentary = { ref ->
                    navController.navigate("commentary/${ref.bookId}/${ref.chapter}/${ref.verse}")
                },
                onPlayAudio = { ref, ttsFallback ->
                    viewModel.playVerseAudio(ref.translation, ref.bookId, ref.chapter, ttsFallback)
                },
                onPauseMainAudioForAttachment = { viewModel.pauseAudioIfPlaying() },
                onExit = { navController.navigateUp() },
                mediaLibraryImages = bibleUserImages,
                mediaLibraryAudios = bibleUserAudios,
                onVerseNote = { ref, bookName, verseText ->
                    viewModel.createNoteForVerse(ref, bookName, verseText) { noteId ->
                        navController.navigate("note_edit/$noteId") {
                            launchSingleTop = true
                        }
                    }
                },
                onOpenVerseNote = { noteId ->
                    navController.navigate("note_edit/$noteId") {
                        launchSingleTop = true
                    }
                },
                userNotes = userNotes,
                semanticHighlightSession = semanticHighlightSession,
                userLexiconRules = readerUserLexiconRules,
                presetLexiconRules = readerPresetLexiconRules,
                presetLexiconEnabled = lexiconPresetEnabled,
                mediaLibraryVideos = bibleUserVideos,
                wordSpanMediaAttachments = wordSpanMediaAttachments,
                onUpsertWordSpanMedia = { viewModel.upsertWordSpanMediaAttachment(it) },
                onDeleteWordSpanMedia = { viewModel.deleteWordSpanMediaAttachment(it) },
                onRemoveWordSpanMediaIntersecting = { ref, a, b ->
                    viewModel.removeWordSpanMediaIntersecting(ref, a, b)
                },
            )
        }
        composable("azbuka") {
            AzbukaScreen(
                onBack = { navController.navigateUp() },
                library = library,
            )
        }
        composable("cifry") {
            CifryScreen(
                onBack = { navController.navigateUp() },
            )
        }
        composable("kids") {
            KidsHubScreen(
                navController = navController,
                onBack = { navController.navigateUp() },
                hubState = kidsUserSections,
                onEditSections = {
                    navController.navigate("kids_edit_sections") {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable("kids_edit_sections") {
            KidsEditHubSectionsScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
                onOpenPicturedEditor = { route ->
                    navController.navigate("kids_edit_pictured?r=${Uri.encode(route)}") {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = "kids_edit_pictured?r={r}",
            arguments = listOf(
                navArgument("r") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val r = entry.arguments?.getString("r").orEmpty()
            if (r.isEmpty()) return@composable
            KidsEditPicturedSectionScreen(
                route = r,
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
        composable(
            route = "kids_album?r={r}",
            arguments = listOf(
                navArgument("r") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val r = entry.arguments?.getString("r").orEmpty()
            if (r.isEmpty()) return@composable
            val context = LocalContext.current
            val items = remember(r, kidsUserSections, context) {
                KidsUserSectionsMerge.mergePicturedItems(r, context, kidsUserSections)
            }
            KidsPicturedGridScreen(
                title = KidsPicturedRouteUi.title(r, kidsUserSections),
                items = items,
                onBack = { navController.navigateUp() },
                showDetailDialog = true,
                playRawSoundsOnTap = KidsPicturedRouteUi.playRawOnTap(r),
                tileStyle = KidsPicturedRouteUi.tileStyle(r, kidsUserSections),
            )
        }
        composable("experiment") {
            ExperimentHubScreen(
                onBack = { navController.navigateUp() },
                onOpenCamera = { navController.navigate("experiment_camera") },
                onOpenCameraControl = { navController.navigate("experiment_camera_control") },
                onOpenCamera4 = { navController.navigate("experiment_camera_4") },
                onOpenCamera5MediaPipe = { navController.navigate("experiment_camera_5_mediapipe") },
                onOpenCallsSms = { navController.navigate("experiment_calls_sms") },
                onOpenInboundSms = { navController.navigate("experiment_sms_inbox") },
                onOpenSensorLab = { navController.navigate("experiment_sensor_lab") },
                onOpenSoundLab = { navController.navigate("experiment_sound_lab") },
                onOpenWifi = { navController.navigate("experiment_wifi") },
            )
        }
        composable("experiment_camera") {
            ExperimentCameraScreen(
                onBack = { navController.navigateUp() },
            )
        }
        composable("experiment_camera_control") {
            ExperimentCameraControlScreen(
                onBack = { navController.navigateUp() },
            )
        }
        composable("experiment_camera_4") {
            ExperimentCamera4Screen(
                onBack = { navController.navigateUp() },
            )
        }
        composable("experiment_camera_5_mediapipe") {
            ExperimentCamera5MediaPipeScreen(
                preferences = preferences,
                onBack = { navController.navigateUp() },
            )
        }
        composable("experiment_calls_sms") {
            ExperimentCallsSmsScreen(
                onBack = { navController.navigateUp() },
            )
        }
        composable("experiment_sms_inbox") {
            ExperimentInboundSmsScreen(
                onBack = { navController.navigateUp() },
            )
        }
        composable("experiment_sensor_lab") {
            ExperimentSensorLabScreen(
                onBack = { navController.navigateUp() },
            )
        }
        composable("experiment_sound_lab") {
            ExperimentSoundLabScreen(
                onBack = { navController.navigateUp() },
            )
        }
        composable("experiment_wifi") {
            ExperimentWifiScreen(
                onBack = { navController.navigateUp() },
            )
        }
        composable("kids_colors") {
            KidsTopicScreen(
                title = "Цвета",
                topicItems = KidsTopicsRepository.colors,
                onBack = { navController.navigateUp() },
            )
        }
        composable("kids_seasons") {
            KidsSeasonsScreen(
                onBack = { navController.navigateUp() },
            )
        }
        composable("kids_countries") {
            KidsCountriesScreen(
                onBack = { navController.navigateUp() },
            )
        }
        composable("kids_animals") {
            val context = LocalContext.current
            val items = remember(kidsUserSections, context) {
                KidsUserSectionsMerge.mergePicturedItems("kids_animals", context, kidsUserSections)
            }
            KidsPicturedGridScreen(
                title = KidsPicturedRouteUi.title("kids_animals", kidsUserSections),
                items = items,
                onBack = { navController.navigateUp() },
                showDetailDialog = true,
                playRawSoundsOnTap = KidsPicturedRouteUi.playRawOnTap("kids_animals"),
                tileStyle = KidsPicturedRouteUi.tileStyle("kids_animals", kidsUserSections),
            )
        }
        composable("kids_fish") {
            val context = LocalContext.current
            val items = remember(kidsUserSections, context) {
                KidsUserSectionsMerge.mergePicturedItems("kids_fish", context, kidsUserSections)
            }
            KidsPicturedGridScreen(
                title = KidsPicturedRouteUi.title("kids_fish", kidsUserSections),
                items = items,
                onBack = { navController.navigateUp() },
                showDetailDialog = true,
                playRawSoundsOnTap = KidsPicturedRouteUi.playRawOnTap("kids_fish"),
                tileStyle = KidsPicturedRouteUi.tileStyle("kids_fish", kidsUserSections),
            )
        }
        composable("kids_snakes") {
            val context = LocalContext.current
            val items = remember(kidsUserSections, context) {
                KidsUserSectionsMerge.mergePicturedItems("kids_snakes", context, kidsUserSections)
            }
            KidsPicturedGridScreen(
                title = KidsPicturedRouteUi.title("kids_snakes", kidsUserSections),
                items = items,
                onBack = { navController.navigateUp() },
                showDetailDialog = true,
                playRawSoundsOnTap = KidsPicturedRouteUi.playRawOnTap("kids_snakes"),
                tileStyle = KidsPicturedRouteUi.tileStyle("kids_snakes", kidsUserSections),
            )
        }
        composable("kids_insects") {
            val context = LocalContext.current
            val items = remember(kidsUserSections, context) {
                KidsUserSectionsMerge.mergePicturedItems("kids_insects", context, kidsUserSections)
            }
            KidsPicturedGridScreen(
                title = KidsPicturedRouteUi.title("kids_insects", kidsUserSections),
                items = items,
                onBack = { navController.navigateUp() },
                showDetailDialog = true,
                playRawSoundsOnTap = KidsPicturedRouteUi.playRawOnTap("kids_insects"),
                tileStyle = KidsPicturedRouteUi.tileStyle("kids_insects", kidsUserSections),
            )
        }
        composable("kids_trees") {
            val context = LocalContext.current
            val items = remember(kidsUserSections, context) {
                KidsUserSectionsMerge.mergePicturedItems("kids_trees", context, kidsUserSections)
            }
            KidsPicturedGridScreen(
                title = KidsPicturedRouteUi.title("kids_trees", kidsUserSections),
                items = items,
                onBack = { navController.navigateUp() },
                showDetailDialog = true,
                playRawSoundsOnTap = KidsPicturedRouteUi.playRawOnTap("kids_trees"),
                tileStyle = KidsPicturedRouteUi.tileStyle("kids_trees", kidsUserSections),
            )
        }
        composable("kids_plants") {
            val context = LocalContext.current
            val items = remember(kidsUserSections, context) {
                KidsUserSectionsMerge.mergePicturedItems("kids_plants", context, kidsUserSections)
            }
            KidsPicturedGridScreen(
                title = KidsPicturedRouteUi.title("kids_plants", kidsUserSections),
                items = items,
                onBack = { navController.navigateUp() },
                showDetailDialog = true,
                playRawSoundsOnTap = KidsPicturedRouteUi.playRawOnTap("kids_plants"),
                tileStyle = KidsPicturedRouteUi.tileStyle("kids_plants", kidsUserSections),
            )
        }
        composable("songs") {
            SongCollectionScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
        composable("video_download") {
            VideoDownloadScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
        composable("reading_plan") {
            val planCompleted by viewModel.readingPlanCompletedDates.collectAsStateWithLifecycle()
            val planReminder by viewModel.readingPlanReminderTime.collectAsStateWithLifecycle()
            ReadingPlanScreen(
                onBack = { navController.navigateUp() },
                onOpenPassage = { bookId, chapter ->
                    navController.navigate("read/$bookId/$chapter/0")
                },
                completedDates = planCompleted,
                onMarkDayCompleted = { date, done -> viewModel.setReadingPlanDayCompleted(date, done) },
                reminderTime = planReminder,
                onReminderChange = { h, m -> viewModel.setReadingPlanReminder(h, m) },
            )
        }
        composable("network_region") {
            NetworkRegionScreen(
                onBack = { navController.navigateUp() },
            )
        }
        composable("main_settings") {
            val mainSetCtx = LocalContext.current
            MainSettingsScreen(
                isDark = isDark,
                mimicControlEnabled = mimicControlEnabled,
                mimicControlV2Enabled = mimicControlV2Enabled,
                mimicCameraPreviewEnabled = mimicCameraPreviewEnabled,
                mimicFaceOverlayEnabled = mimicFaceOverlayEnabled,
                mimicMediaPipeFaceGeometryEnabled = mimicMediaPipeFaceGeometryEnabled,
                mimicVelocityVectorEnabled = mimicVelocityVectorEnabled,
                appThemePreset = appThemePreset,
                onAppThemePresetChange = { viewModel.setAppThemePreset(it) },
                onBack = { navController.navigateUp() },
                onToggleDarkMode = { viewModel.toggleDarkMode(isDark) },
                onToggleMimicControl = onToggleMimicControl,
                onToggleMimicControlV2 = { viewModel.toggleMimicControlV2() },
                onToggleMimicCameraPreview = { viewModel.toggleMimicCameraPreview() },
                onToggleMimicFaceOverlay = { viewModel.toggleMimicFaceOverlay() },
                onToggleMimicMediaPipeFaceGeometry = { viewModel.toggleMimicMediaPipeFaceGeometry() },
                onToggleMimicVelocityVector = onToggleMimicVelocityVector,
                onOpenBackup = { navController.navigate("backup") },
                onOpenShareApp = { navController.navigate("share_app") },
                onOpenOfflineDownload = { navController.navigate("offline_download") },
                onOpenNetworkRegion = { navController.navigate("network_region") },
                onOpenMenuOrder = { navController.navigate("books_menu_order") },
                ttsUserSettings = ttsUserSettings,
                onTtsSpeechRateChange = { viewModel.setTtsSpeechRate(it) },
                onTtsPitchChange = { viewModel.setTtsPitch(it) },
                onTtsEnginePackageChange = { viewModel.setTtsEnginePackage(it) },
                onTtsPreferHighQualityChange = { viewModel.setTtsPreferHighQuality(it) },
                onOpenTtsSystemSettings = {
                    runCatching {
                        // Строковая константа: на части срезов android.jar нет Settings.ACTION_TTS_SETTINGS.
                        mainSetCtx.startActivity(
                            Intent("com.android.settings.TTS_SETTINGS")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }
        composable("books_menu_order") {
            BooksMenuOrderScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
        composable("backup") {
            BackupScreen(
                onBack = { navController.navigateUp() },
                exportBundle = { opts -> viewModel.exportDataBundle(opts) },
                exportFullLegacy = { viewModel.exportBackupZip() },
                importZip = { f -> viewModel.importBackupZip(f) },
            )
        }
        composable("share_app") {
            ShareMyAppScreen(
                onBack = { navController.navigateUp() },
                exportShare = { opts, onProgress ->
                    viewModel.exportShareBundle(opts, onProgress)
                },
            )
        }
        composable("offline_download") {
            OfflineStudyDownloadScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
        composable("maps") {
            MapsScreen(onBack = { navController.navigateUp() })
        }
        composable("my_travels") {
            MyTravelsScreen(onBack = { navController.navigateUp() })
        }
        composable("my_church") {
            MyChurchHubScreen(
                onBack = { navController.navigateUp() },
                onOpenParticipants = { navController.navigate("church_participants") },
                onOpenAccounting = { navController.navigate("church_accounting") },
                onOpenOrders = { navController.navigate("church_orders") },
                onOpenProtocols = { navController.navigate("church_protocols") },
                onOpenCertificates = { navController.navigate("church_certificates") },
            )
        }
        composable("church_participants") {
            val ctx = LocalContext.current
            val churchVm = viewModel<ChurchViewModel>(
                factory = ChurchViewModel.factory(ctx.applicationContext as Application),
            )
            ChurchParticipantsScreen(
                viewModel = churchVm,
                onBack = { navController.navigateUp() },
                onOpenParticipant = { id ->
                    navController.navigate("church_member_edit?participantId=$id")
                },
            )
        }
        composable(
            route = "church_member_edit?participantId={participantId}",
            arguments = listOf(
                navArgument("participantId") {
                    type = NavType.StringType
                    defaultValue = "new"
                },
            ),
        ) { entry ->
            val pid = entry.arguments?.getString("participantId") ?: "new"
            val ctx = LocalContext.current
            val churchVm = viewModel<ChurchViewModel>(
                factory = ChurchViewModel.factory(ctx.applicationContext as Application),
            )
            ChurchMemberEditScreen(
                participantId = pid,
                viewModel = churchVm,
                onBack = { navController.navigateUp() },
            )
        }
        composable("church_accounting") {
            ChurchPlaceholderScreen(
                title = stringResource(R.string.church_section_accounting),
                onBack = { navController.navigateUp() },
            )
        }
        composable("church_orders") {
            ChurchPlaceholderScreen(
                title = stringResource(R.string.church_section_orders),
                onBack = { navController.navigateUp() },
            )
        }
        composable("church_protocols") {
            ChurchPlaceholderScreen(
                title = stringResource(R.string.church_section_protocols),
                onBack = { navController.navigateUp() },
            )
        }
        composable("church_certificates") {
            ChurchPlaceholderScreen(
                title = stringResource(R.string.church_section_certificates),
                onBack = { navController.navigateUp() },
            )
        }
        composable("media") {
            MediaHomeScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
                onOpenPictures = { navController.navigate("media_pictures") },
                onOpenVideos = { navController.navigate("media_videos") },
                onOpenAudios = { navController.navigate("media_audios") },
                onOpenMusician = { navController.navigate("media_musician") },
                onOpenPesnopenie = { navController.navigate("songs") },
            )
        }
        composable("media_musician") {
            MusicianSectionScreen(
                onBack = { navController.navigateUp() },
                onOpenGuitarTuner = { navController.navigate("media_musician_guitar") },
                onOpenViolinTuner = { navController.navigate("media_musician_violin") },
                onOpenMetronome = { navController.navigate("media_musician_metronome") },
                onOpenMusicNotes = { navController.navigate("media_musician_notes") },
            )
        }
        composable("media_musician_notes") {
            MusicTheoryNotesScreen(onBack = { navController.navigateUp() })
        }
        composable("media_musician_metronome") {
            MetronomeScreen(onBack = { navController.navigateUp() })
        }
        composable("media_musician_guitar") {
            GuitarTunerScreen(onBack = { navController.navigateUp() })
        }
        composable("media_musician_violin") {
            ViolinTunerScreen(onBack = { navController.navigateUp() })
        }
        composable("media_pictures") {
            PictureLibraryScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
        composable("media_videos") {
            VideoLibraryScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
                onOpenVideoDownload = { navController.navigate("video_download") },
            )
        }
        composable("media_audios") {
            AudioLibraryScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
                onOpenAudioDownload = { navController.navigate("audio_download") },
            )
        }
        composable("audio_download") {
            AudioDownloadScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
        composable("genealogy") {
            GenealogyScreen(
                onBack = { navController.navigateUp() },
                library = library,
                translation = translation,
                onOpenInReader = { bookId, chapter, verse ->
                    navController.navigate("read/$bookId/$chapter/$verse")
                },
            )
        }
        composable("other_books") {
            OtherBooksScreen(
                preferences = preferences,
                onBack = { navController.navigateUp() },
                onOpenQuran = { navController.navigate("quran") },
            )
        }
        composable("quran") { navEntry ->
            val quranRepo = rememberQuranRepository()
            QuranSurahListScreen(
                repository = quranRepo,
                navBackStackEntry = navEntry,
                onBack = { navController.navigateUp() },
                onOpenSurah = { n -> navController.navigate("quran/$n") },
                onOpenSearch = { navController.navigate("quran_search") },
            )
        }
        composable("quran_search") {
            val quranRepo = rememberQuranRepository()
            QuranSearchScreen(
                repository = quranRepo,
                preferences = preferences,
                onBack = { navController.navigateUp() },
                onOpenHit = { surah, verse -> navController.navigate("quran/$surah/v/$verse") },
            )
        }
        composable("quran/{surah}") { entry ->
            val n = entry.arguments?.getString("surah")?.toIntOrNull() ?: return@composable
            val quranRepo = rememberQuranRepository()
            QuranSurahReaderScreen(
                repository = quranRepo,
                preferences = preferences,
                surahNumber = n,
                scrollToVerseNumber = null,
                onBack = { navController.navigateUp() },
                onOpenArabicSandbox = { verse ->
                    navController.navigate("quran_arabic_sandbox/$n/v/$verse")
                },
            )
        }
        composable("quran/{surah}/v/{verse}") { entry ->
            val n = entry.arguments?.getString("surah")?.toIntOrNull() ?: return@composable
            val v = entry.arguments?.getString("verse")?.toIntOrNull() ?: return@composable
            val quranRepo = rememberQuranRepository()
            QuranSurahReaderScreen(
                repository = quranRepo,
                preferences = preferences,
                surahNumber = n,
                scrollToVerseNumber = v,
                onBack = { navController.navigateUp() },
                onOpenArabicSandbox = { verse ->
                    navController.navigate("quran_arabic_sandbox/$n/v/$verse")
                },
            )
        }
        composable("quran_arabic_sandbox/{surah}") { entry ->
            val n = entry.arguments?.getString("surah")?.toIntOrNull() ?: return@composable
            val quranRepo = rememberQuranRepository()
            QuranArabicSandboxScreen(
                repository = quranRepo,
                preferences = preferences,
                surahNumber = n,
                initialVerseNumber = null,
                onBack = { navController.navigateUp() },
            )
        }
        composable("quran_arabic_sandbox/{surah}/v/{verse}") { entry ->
            val n = entry.arguments?.getString("surah")?.toIntOrNull() ?: return@composable
            val v = entry.arguments?.getString("verse")?.toIntOrNull() ?: return@composable
            val quranRepo = rememberQuranRepository()
            QuranArabicSandboxScreen(
                repository = quranRepo,
                preferences = preferences,
                surahNumber = n,
                initialVerseNumber = v,
                onBack = { navController.navigateUp() },
            )
        }
        composable(
            route = "interlinear_hebrew_sandbox/{bookId}/{chapter}/{verse}/{wholeVerse}",
            arguments = listOf(
                navArgument("chapter") { type = NavType.IntType },
                navArgument("verse") { type = NavType.IntType },
                navArgument("wholeVerse") {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) { entry ->
            val bookId = entry.arguments?.getString("bookId") ?: return@composable
            val chapter = entry.arguments?.getInt("chapter") ?: return@composable
            val verse = entry.arguments?.getInt("verse") ?: return@composable
            val wholeArg = entry.arguments?.getInt("wholeVerse") ?: 0
            InterlinearHebrewSandboxScreen(
                library = library,
                preferences = preferences,
                bookId = bookId,
                chapter = chapter,
                initialVerse = verse,
                openAsWholeVerse = wholeArg != 0,
                onBack = { navController.navigateUp() },
            )
        }
        composable("strongs") {
            StrongsScreen(onBack = { navController.navigateUp() })
        }
        composable("timemark_editor") {
            val bibleUserImages by viewModel.bibleUserImages.collectAsStateWithLifecycle()
            TimemarkEditorScreen(
                library = library,
                translation = translation,
                narratorId = narratorId,
                onBack = { navController.navigateUp() },
                mediaLibraryImages = bibleUserImages,
            )
        }
        composable("chapters/{bookId}") { entry ->
            val bookId = entry.arguments?.getString("bookId") ?: return@composable
            val canon = BibleCanon.byId(bookId) ?: return@composable
            val loaded = library.getBook(translation, bookId)
            val isOnlineTranslation = loaded == null && translation.onlineCode != null
            val chapters = loaded?.chapters ?: (1..canon.chapters).map { n ->
                BibleChapter(number = n, verses = emptyList())
            }
            val book = loaded ?: BibleBook(
                id = canon.id,
                name = BibleCanon.displayName(canon, translation),
                chapters = chapters,
            )
            val titleText = "${book.name} - ${translation.labelRu}"
            val chapterScope = rememberCoroutineScope()
            val chapterCtx = LocalContext.current
            var dlProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
            var dlError by remember { mutableStateOf<String?>(null) }
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            if (dlProgress != null) {
                                val (done, total) = dlProgress!!
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Скачивание $done / $total",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { if (total > 0) done.toFloat() / total else 0f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                    )
                                }
                            } else {
                                Text(
                                    titleText,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        },
                        actions = {
                            if (book.chapters.isNotEmpty() && dlProgress == null) {
                                val chaptersWithAudioForBtn = remember(translation, bookId, narratorId, downloadTick) {
                                    val eff = com.example.bible.data.narratorForTranslation(translation, narratorId).id
                                    viewModel.downloadedChaptersFor(eff, bookId)
                                }
                                val allDownloaded = chaptersWithAudioForBtn.size >= book.chapters.size
                                IconButton(
                                    onClick = {
                                        if (allDownloaded) return@IconButton
                                        val narrator = com.example.bible.data.narratorForTranslation(translation, narratorId)
                                        val chaptersToDownload = book.chapters
                                            .map { it.number }
                                            .filter { it !in chaptersWithAudioForBtn }
                                        val total = chaptersToDownload.size
                                        if (total == 0) return@IconButton
                                        dlError = null
                                        dlProgress = 0 to total
                                        chapterScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            var done = 0
                                            var errors = 0
                                            for (ch in chaptersToDownload) {
                                                try {
                                                    com.example.bible.data.BibleAudioPlayer.downloadChapter(
                                                        chapterCtx, narrator, bookId, ch,
                                                    )
                                                } catch (_: Exception) {
                                                    errors++
                                                }
                                                done++
                                                dlProgress = done to total
                                            }
                                            dlProgress = null
                                            if (errors > 0) {
                                                dlError = "Не удалось скачать $errors из $total глав"
                                            }
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = "Скачать все главы",
                                        tint = if (allDownloaded)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (dlProgress != null) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        },
                    )
                },
            ) { padding ->
                if (book.chapters.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.no_chapters_loaded),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                } else {
                    Column(modifier = Modifier.padding(padding)) {
                        if (dlError != null) {
                            Text(
                                dlError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        val chaptersWithAudio = remember(translation, bookId, narratorId, downloadTick) {
                            val fromAssets = book.chapters.mapNotNull { ch ->
                                if (viewModel.hasChapterAudio(translation, bookId, ch.number)) ch.number else null
                            }.toSet()
                            val eff = com.example.bible.data.narratorForTranslation(translation, narratorId).id
                            val downloaded = viewModel.downloadedChaptersFor(eff, bookId)
                            fromAssets + downloaded
                        }
                        ChapterGrid(
                            modifier = Modifier.fillMaxSize(),
                            book = book,
                            chaptersWithAudio = chaptersWithAudio,
                            onChapterClick = { chapter ->
                                navController.navigate("verses/$bookId/$chapter")
                            },
                        )
                    }
                }
            }
        }
        composable("verses/{bookId}/{chapter}") { entry ->
            val bookId = entry.arguments?.getString("bookId") ?: return@composable
            val chapterNum = entry.arguments?.getString("chapter")?.toIntOrNull() ?: return@composable
            val canon = BibleCanon.byId(bookId) ?: return@composable
            val localBook = library.getBook(translation, bookId)
            if (localBook == null && translation.onlineCode != null) {
                LaunchedEffect(Unit) {
                    navController.navigate("read/$bookId/$chapterNum/0") {
                        popUpTo("verses/$bookId/$chapterNum") { inclusive = true }
                    }
                }
                return@composable
            }
            val book = localBook ?: return@composable
            val chapter = book.chapters.find { it.number == chapterNum } ?: return@composable
            val titleText = "${book.name} $chapterNum - ${translation.labelRu}"
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                titleText,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        },
                    )
                },
            ) { padding ->
                VerseGrid(
                    modifier = Modifier.padding(padding),
                    verses = chapter.verses,
                    onVerseClick = { verseNum ->
                        navController.navigate("read/$bookId/$chapterNum/$verseNum")
                    },
                )
            }
        }
        composable(
            route = "read/{bookId}/{chapter}/{scrollVerse}",
            arguments = listOf(
                navArgument("scrollVerse") {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) { entry ->
            val bookId = entry.arguments?.getString("bookId") ?: return@composable
            val chapterNum = entry.arguments?.getString("chapter")?.toIntOrNull() ?: return@composable
            val scrollVerse = entry.arguments?.getInt("scrollVerse") ?: 0
            val canon = BibleCanon.byId(bookId)
            val localBook = library.getBook(translation, bookId)
            val isOnline = localBook == null && translation.onlineCode != null
            val onlineLoading by viewModel.onlineChapterLoading.collectAsStateWithLifecycle()
            val onlineVerses by viewModel.onlineChapterVerses.collectAsStateWithLifecycle()
            val onlineError by viewModel.onlineChapterError.collectAsStateWithLifecycle()
            val bibleUserImages by viewModel.bibleUserImages.collectAsStateWithLifecycle()
            val bibleUserVideos by viewModel.bibleUserVideos.collectAsStateWithLifecycle()
            val bibleUserAudios by viewModel.bibleUserAudios.collectAsStateWithLifecycle()
            val userNotes by viewModel.userNotes.collectAsStateWithLifecycle()

            LaunchedEffect(translation, bookId, chapterNum) {
                if (isOnline) {
                    viewModel.loadOnlineChapter(translation, bookId, chapterNum)
                }
            }

            val bookName = localBook?.name
                ?: BibleCanon.displayName(canon ?: BibleCanon.allBooks.first(), translation)
            val totalChapters = canon?.chapters ?: localBook?.chapters?.size ?: 1
            val chapter: BibleChapter? = if (isOnline) {
                if (onlineVerses.isNotEmpty()) BibleChapter(chapterNum, onlineVerses) else null
            } else {
                localBook?.chapters?.find { it.number == chapterNum }
            }
            val chapterIndex = if (isOnline) chapterNum - 1 else (localBook?.chapters?.indexOf(chapter) ?: -1)
            val hasPrev = chapterIndex > 0
            val hasNext = chapterIndex < totalChapters - 1

            if (isOnline && onlineLoading) {
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("$bookName $chapterNum") },
                            navigationIcon = {
                                IconButton(onClick = { navController.navigateUp() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                                }
                            },
                        )
                    },
                ) { padding ->
                    Box(
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Загрузка ${translation.labelRu}…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                return@composable
            }
            if (isOnline && onlineError != null) {
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("$bookName $chapterNum") },
                            navigationIcon = {
                                IconButton(onClick = { navController.navigateUp() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                                }
                            },
                        )
                    },
                ) { padding ->
                    Box(
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(onlineError ?: "Ошибка", color = MaterialTheme.colorScheme.error)
                    }
                }
                return@composable
            }
            if (chapter == null) return@composable

            val book = localBook ?: com.example.bible.data.BibleBook(
                id = bookId,
                name = bookName,
                chapters = listOf(chapter),
            )

            DisposableEffect(Unit) {
                onDispose { viewModel.flushReadingDwell() }
            }

            LaunchedEffect(translation, bookId, chapterNum, scrollVerse) {
                viewModel.beginReadingDwellSession(
                    translation,
                    bookId,
                    bookName,
                    chapterNum,
                    if (scrollVerse > 0) scrollVerse else 1,
                )
            }

            LaunchedEffect(translation, bookId, chapterNum, scrollVerse) {
                viewModel.addHistoryEntry(
                    translation = translation,
                    bookId = bookId,
                    bookName = bookName,
                    chapter = chapterNum,
                    verse = if (scrollVerse > 0) scrollVerse else 1,
                )
            }
            var showQuickNav by remember { mutableStateOf(false) }
            var showTextSizeDialog by remember { mutableStateOf(false) }
            var showMoreMenu by remember { mutableStateOf(false) }
            var showAlphabet by remember { mutableStateOf(false) }
            var showNarratorPicker by remember { mutableStateOf(false) }
            var showStudyTools by remember { mutableStateOf(false) }
            var studyVerse by remember { mutableIntStateOf(1) }
            val interlinearHebrewSandboxAvailable = remember(bookId) {
                BibleCanon.isOldTestament(bookId) &&
                    library.getBook(TranslationId.INTERLINEAR, bookId) != null
            }

            LaunchedEffect(showStudyTools) {
                if (showStudyTools) viewModel.recordReadingToolUse("Изучение")
            }
            LaunchedEffect(showQuickNav) {
                if (showQuickNav) viewModel.recordReadingToolUse("Быстрый переход")
            }
            LaunchedEffect(showNarratorPicker) {
                if (showNarratorPicker) viewModel.recordReadingToolUse("Выбор озвучки")
            }
            val bibleAudioState by com.example.bible.data.BibleAudioPlayer.state.collectAsState()
            var bibleAudioBarInsetDp by remember { mutableStateOf(0.dp) }
            val readerContext = LocalContext.current
            val semanticHighlightSession by viewModel.semanticHighlightSession.collectAsStateWithLifecycle()
            val readerUserLexiconRules by viewModel.readerUserLexiconRules.collectAsStateWithLifecycle()
            val readerPresetLexiconRules by viewModel.readerPresetLexiconRules.collectAsStateWithLifecycle()
            val lexiconPresetEnabled by viewModel.lexiconPresetEnabled.collectAsStateWithLifecycle()
            val readingAudioNarrator = remember(translation, narratorId) {
                com.example.bible.data.narratorForTranslation(translation, narratorId)
            }
            LaunchedEffect(bookId, chapterNum, scrollVerse) {
                com.example.bible.data.BibleAudioPlayer.chapterContinueNavigation.collect { (bid, nextCh) ->
                    if (bid == bookId && chapterNum == nextCh - 1) {
                        navController.navigate("read/$bid/$nextCh/0") {
                            popUpTo("read/$bookId/$chapterNum/$scrollVerse") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { showQuickNav = true },
                            ) {
                                Text(
                                    "${book.name} $chapterNum",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    translation.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        navigationIcon = {
                            Row {
                                if (hasPrev) {
                                    IconButton(onClick = {
                                        val prev = if (isOnline) chapterNum - 1 else book.chapters[chapterIndex - 1].number
                                        navController.navigate("read/$bookId/$prev/0") {
                                            popUpTo("read/$bookId/$chapterNum/$scrollVerse") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.prev_chapter))
                                    }
                                } else {
                                    IconButton(onClick = { navController.navigateUp() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                                    }
                                }
                            }
                        },
                        actions = {
                            if (hasNext) {
                                IconButton(onClick = {
                                    val next = if (isOnline) chapterNum + 1 else book.chapters[chapterIndex + 1].number
                                    navController.navigate("read/$bookId/$next/0") {
                                        popUpTo("read/$bookId/$chapterNum/$scrollVerse") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.next_chapter))
                                }
                            }
                            IconButton(onClick = {
                                val isCurrentChapter = bibleAudioState.isPlaying &&
                                    bibleAudioState.bookId == bookId &&
                                    bibleAudioState.chapter == chapterNum &&
                                    bibleAudioState.narratorId == readingAudioNarrator.id
                                if (isCurrentChapter) {
                                    com.example.bible.data.BibleAudioPlayer.togglePlay()
                                } else {
                                    com.example.bible.data.BibleAudioPlayer.playChapter(
                                        readerContext, readingAudioNarrator, bookId, chapterNum,
                                    )
                                }
                            }) {
                                val isThisChapterAudio = bibleAudioState.isPlaying &&
                                    bibleAudioState.bookId == bookId &&
                                    bibleAudioState.chapter == chapterNum &&
                                    bibleAudioState.narratorId == readingAudioNarrator.id
                                Icon(
                                    imageVector = if (isThisChapterAudio) Icons.Default.Pause else Icons.Default.Headphones,
                                    contentDescription = "Слушать",
                                    tint = if (isThisChapterAudio) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                studyVerse = scrollVerse.coerceAtLeast(1)
                                showStudyTools = true
                            }) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Изучение", tint = MaterialTheme.colorScheme.primary)
                            }
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Ещё")
                                }
                                val readerMenuMaxH = (LocalConfiguration.current.screenHeightDp * 0.58f).dp
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    modifier = Modifier.heightIn(max = readerMenuMaxH),
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.main_settings_title)) },
                                        onClick = { showMoreMenu = false; navController.navigate("main_settings") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Settings,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        },
                                    )
                                    HorizontalDivider()
                                    BooksMainMenuOrderedItems(
                                        menuOrder = booksMainMenuOrder,
                                        translation = translation,
                                        narratorId = narratorId,
                                        closeMenu = { showMoreMenu = false },
                                        navController = navController,
                                        onShowTextSizeDialog = { showTextSizeDialog = true },
                                        onShowBookNarratorPicker = { showNarratorPicker = true },
                                    )
                                    HorizontalDivider()
                                    if (BibleCanon.isOldTestament(bookId)) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_audio_hebrew_chapter)) },
                                            onClick = {
                                                showMoreMenu = false
                                                BibleAudioPlayer.playChapter(
                                                    readerContext,
                                                    BibleAudioNarrators.hebrewOt,
                                                    bookId,
                                                    chapterNum,
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.VolumeUp,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            },
                                        )
                                    }
                                    if (BibleCanon.isNewTestament(bookId)) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_audio_greek_chapter)) },
                                            onClick = {
                                                showMoreMenu = false
                                                BibleAudioPlayer.playChapter(
                                                    readerContext,
                                                    BibleAudioNarrators.greekNt,
                                                    bookId,
                                                    chapterNum,
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.VolumeUp,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            },
                                        )
                                    }
                                    if (translation == TranslationId.INTERLINEAR) {
                                        DropdownMenuItem(
                                            text = { Text("Алфавит Αβ") },
                                            onClick = { showMoreMenu = false; showAlphabet = true },
                                            leadingIcon = { Text("Αβ", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) },
                                        )
                                    }
                                    if (interlinearHebrewSandboxAvailable) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.interlinear_menu_hebrew_sandbox)) },
                                            onClick = {
                                                showMoreMenu = false
                                                val v = if (scrollVerse > 0) scrollVerse else 1
                                                navController.navigate(
                                                    "interlinear_hebrew_sandbox/$bookId/$chapterNum/$v/0",
                                                )
                                            },
                                            leadingIcon = {
                                                Text(
                                                    "עבר",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            },
                                        )
                                    }
                                    if (interlinearHebrewSandboxAvailable &&
                                        translation == TranslationId.INTERLINEAR
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.interlinear_menu_hebrew_sandbox_whole)) },
                                            onClick = {
                                                showMoreMenu = false
                                                val v = if (scrollVerse > 0) scrollVerse else 1
                                                navController.navigate(
                                                    "interlinear_hebrew_sandbox/$bookId/$chapterNum/$v/1",
                                                )
                                            },
                                            leadingIcon = {
                                                Text(
                                                    "עִב",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            },
                                        )
                                    }
                                    if (BibleCanon.isOldTestament(bookId) || BibleCanon.isNewTestament(bookId) || translation == TranslationId.INTERLINEAR) {
                                        HorizontalDivider()
                                    }
                                    DropdownMenuItem(
                                        text = { Text("К книгам") },
                                        onClick = {
                                            showMoreMenu = false
                                            navController.navigate("books") {
                                                popUpTo("books") { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("К главам") },
                                        onClick = { showMoreMenu = false; navController.navigate("chapters/$bookId") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("К стихам") },
                                        onClick = { showMoreMenu = false; navController.navigate("verses/$bookId/$chapterNum") },
                                        leadingIcon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null) },
                                    )
                                    HorizontalDivider()
                                    TranslationId.entries.forEach { tid ->
                                        DropdownMenuItem(
                                            text = { Text(tid.labelRu) },
                                            onClick = {
                                                showMoreMenu = false
                                                scope.launch { viewModel.setTranslation(tid) }
                                            },
                                            trailingIcon = {
                                                if (tid == translation) Icon(Icons.Default.Check, contentDescription = null)
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                },
            ) { padding ->
                val translations = TranslationId.entries
                val initialPage = translations.indexOf(translation).coerceAtLeast(0)
                val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { translations.size })

                LaunchedEffect(pagerState.currentPage) {
                    val tid = translations[pagerState.currentPage]
                    if (tid != translation) {
                        viewModel.setTranslation(tid)
                    }
                }
                LaunchedEffect(translation) {
                    val idx = translations.indexOf(translation)
                    if (idx >= 0 && pagerState.currentPage != idx) {
                        pagerState.scrollToPage(idx)
                    }
                }

                Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        edgePadding = 8.dp,
                    ) {
                        translations.forEachIndexed { index, tid ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(tid.labelRu, fontSize = 12.sp, maxLines = 1) },
                            )
                        }
                    }
                    ProvideReaderFontScale(multiplier = readerFontScale) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            val pageTrans = translations[page]
                            val pageBook = library.getBook(pageTrans, bookId)
                            val pageChapter = pageBook?.chapters?.find { it.number == chapterNum }

                            val isPageOnline = pageBook == null && pageTrans.onlineCode != null
                            var onlineVersesPage by remember(pageTrans, bookId, chapterNum) {
                                mutableStateOf<List<BibleVerse>?>(null)
                            }
                            var onlineLoadingPage by remember(pageTrans, bookId, chapterNum) { mutableStateOf(false) }
                            var onlineErrorPage by remember(pageTrans, bookId, chapterNum) { mutableStateOf<String?>(null) }
                            LaunchedEffect(pageTrans, bookId, chapterNum, isPageOnline) {
                                if (!isPageOnline) return@LaunchedEffect
                                onlineLoadingPage = true
                                onlineErrorPage = null
                                onlineVersesPage = null
                                try {
                                    val vv = viewModel.fetchOnlineVerses(pageTrans, bookId, chapterNum)
                                    onlineVersesPage = vv.ifEmpty { null }
                                    if (vv.isEmpty()) onlineErrorPage = "Не удалось загрузить текст"
                                } catch (e: Exception) {
                                    onlineErrorPage = e.message ?: "Ошибка загрузки"
                                } finally {
                                    onlineLoadingPage = false
                                }
                            }

                            val effectiveVerses = pageChapter?.verses ?: onlineVersesPage
                            val effectiveBookName = pageBook?.name
                                ?: BibleCanon.displayName(
                                    BibleCanon.byId(bookId) ?: BibleCanon.allBooks.first(),
                                    pageTrans,
                                )
                            val timemarkProjectsForPage = remember(pageTrans, bookId, chapterNum, readerContext) {
                                TimemarkStore.listProjectsForChapter(
                                    readerContext,
                                    pageTrans.code,
                                    bookId,
                                    chapterNum,
                                ).filter { it.cues.isNotEmpty() }
                            }
                            var selectedTimemarkId by remember(pageTrans, bookId, chapterNum) {
                                mutableStateOf<String?>(null)
                            }
                            LaunchedEffect(timemarkProjectsForPage) {
                                if (selectedTimemarkId != null && timemarkProjectsForPage.none { it.id == selectedTimemarkId }) {
                                    selectedTimemarkId = null
                                }
                            }
                            val timemarkForPage = selectedTimemarkId?.let { id ->
                                timemarkProjectsForPage.find { it.id == id }
                            }
                            val verseNumbersWithNotes = remember(userNotes, bookId, chapterNum) {
                                userNotes.verseNumbersWithNotesInChapter(bookId, chapterNum)
                            }
                            val isReaderPagerPageActive = page == pagerState.currentPage
                            val onHebrewSandboxWholeVerse = remember(bookId, pageTrans, library) {
                                if (pageTrans != TranslationId.INTERLINEAR ||
                                    !BibleCanon.isOldTestament(bookId) ||
                                    library.getBook(TranslationId.INTERLINEAR, bookId) == null
                                ) {
                                    null
                                } else {
                                    { ref: VerseRef ->
                                        navController.navigate(
                                            "interlinear_hebrew_sandbox/${ref.bookId}/${ref.chapter}/${ref.verse}/1",
                                        )
                                    }
                                }
                            }

                            when {
                                effectiveVerses != null && effectiveVerses.isNotEmpty() -> {
                                    Column(Modifier.fillMaxSize()) {
                                        if (timemarkProjectsForPage.isNotEmpty()) {
                                            TimemarkSourceSelector(
                                                projects = timemarkProjectsForPage,
                                                selectedProjectId = selectedTimemarkId,
                                                onSelectPlain = { selectedTimemarkId = null },
                                                onSelectProject = { p -> selectedTimemarkId = p.id },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                        ReaderContent(
                                        modifier = Modifier.weight(1f),
                                        verses = effectiveVerses,
                                        scrollToVerse = scrollVerse,
                                        translation = pageTrans,
                                        bookId = bookId,
                                        bookName = effectiveBookName,
                                        chapter = chapterNum,
                                        bookmarkKeys = bookmarkKeys,
                                        textHighlights = textHighlights,
                                        onAddTextHighlight = { viewModel.addTextHighlight(it) },
                                        onRemoveTextHighlights = { ref, a, b -> viewModel.removeTextHighlightsIntersecting(ref, a, b) },
                                        onToggleBookmark = { viewModel.toggleBookmark(it) },
                                        onVerseCommentary = { ref ->
                                            viewModel.recordReadingToolUse("Комментарии")
                                            navController.navigate("commentary/${ref.bookId}/${ref.chapter}/${ref.verse}")
                                        },
                                        onPlayAudio = { ref, ttsFallback ->
                                            viewModel.playVerseAudio(pageTrans, ref.bookId, ref.chapter, ttsFallback)
                                        },
                                        onNavigateToVerse = { targetBookId, targetChapter, targetVerse ->
                                            navController.navigate("read/$targetBookId/$targetChapter/$targetVerse")
                                        },
                                        audioPlaybackState = audioPlaybackState,
                                        audioPlaybackSpeed = audioPlaybackSpeed,
                                        onTogglePause = { viewModel.togglePauseResume() },
                                        onCycleSpeed = { viewModel.cycleAudioSpeed() },
                                        onStopAudio = { viewModel.stopAudio() },
                                        getAudioProgress = { viewModel.getAudioProgress() },
                                        onSeekAudio = { viewModel.seekAudio(it) },
                                        onPauseMainAudioForAttachment = { viewModel.pauseAudioIfPlaying() },
                                        bibleChapterAudioBarBottomInset = bibleAudioBarInsetDp,
                                        timemarkProject = timemarkForPage,
                                        mediaLibraryImages = bibleUserImages,
                                        mediaLibraryVideos = bibleUserVideos,
                                        mediaLibraryAudios = bibleUserAudios,
                                        verseNumbersWithNotes = verseNumbersWithNotes,
                                        userNotes = userNotes,
                                        onVerseNote = { ref, bookName, verseText ->
                                            viewModel.createNoteForVerse(ref, bookName, verseText) { noteId ->
                                                navController.navigate("note_edit/$noteId") {
                                                    launchSingleTop = true
                                                }
                                            }
                                        },
                                        onOpenExistingVerseNote = { noteId ->
                                            navController.navigate("note_edit/$noteId") {
                                                launchSingleTop = true
                                            }
                                        },
                                        semanticHighlightSession = semanticHighlightSession,
                                        userLexiconRules = readerUserLexiconRules,
                                        presetLexiconRules = readerPresetLexiconRules,
                                        presetLexiconEnabled = lexiconPresetEnabled,
                                        wordSpanMediaAttachments = wordSpanMediaAttachments,
                                        onUpsertWordSpanMedia = { viewModel.upsertWordSpanMediaAttachment(it) },
                                        onDeleteWordSpanMedia = { viewModel.deleteWordSpanMediaAttachment(it) },
                                        onRemoveWordSpanMediaIntersecting = { ref, a, b ->
                                            viewModel.removeWordSpanMediaIntersecting(ref, a, b)
                                        },
                                        trackReadingDwell = isReaderPagerPageActive,
                                        onPauseDwellTracking = { viewModel.flushReadingDwell() },
                                        onReadingDwellVerse = { v, t ->
                                            viewModel.onReadingVisibleVerse(t, bookId, bookName, chapterNum, v)
                                        },
                                        onLexiconLookupOpened = { viewModel.recordReadingToolUse("Словари") },
                                        mimicScrollDy = viewModel.mimicScrollDy,
                                        readerFingerScrollEnabled = !mimicControlEnabled,
                                        onOpenInterlinearHebrewSandboxWholeVerse = onHebrewSandboxWholeVerse,
                                    )
                                    }
                                }
                                isPageOnline && onlineLoadingPage -> {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator()
                                            Spacer(Modifier.height(12.dp))
                                            Text("Загрузка ${pageTrans.labelRu}…", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                isPageOnline && onlineErrorPage != null -> {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            onlineErrorPage ?: "Ошибка",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                else -> {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            "Нет данных для ${pageTrans.labelRu}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                BibleAudioBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onBarHeightChanged = { bibleAudioBarInsetDp = it },
                )
                }
            }
            if (showNarratorPicker) {
                NarratorPickerDialog(
                    currentId = narratorId,
                    onSelect = { id ->
                        viewModel.setAudioNarrator(id)
                        showNarratorPicker = false
                    },
                    onDismiss = { showNarratorPicker = false },
                )
            }
            if (showQuickNav) {
                QuickNavigatorSheet(
                    library = library,
                    translation = translation,
                    currentBookId = bookId,
                    onNavigate = { targetBookId, targetChapter ->
                        showQuickNav = false
                        navController.navigate("read/$targetBookId/$targetChapter/0") {
                            popUpTo("read/$bookId/$chapterNum/$scrollVerse") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onDismiss = { showQuickNav = false },
                )
            }
            if (showAlphabet) {
                AlphabetReferenceSheet(onDismiss = { showAlphabet = false })
            }
            if (showStudyTools) {
                StudyToolsSheet(
                    translation = translation,
                    bookId = bookId,
                    bookName = book.name,
                    chapter = chapterNum,
                    verse = studyVerse,
                    viewModel = viewModel,
                    onDismiss = { showStudyTools = false },
                    onNavigateToVerse = { targetBookId, targetChapter, targetVerse ->
                        showStudyTools = false
                        navController.navigate("read/$targetBookId/$targetChapter/$targetVerse")
                    },
                    totalVerses = chapter.verses.size,
                )
            }
            if (showTextSizeDialog) {
                TextSizeSettingsDialog(
                    viewModel = viewModel,
                    onDismiss = { showTextSizeDialog = false },
                )
            }
        }
        composable(
            route = "commentary/{bookId}/{chapter}/{verse}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("chapter") { type = NavType.IntType },
                navArgument("verse") { type = NavType.IntType },
            ),
        ) { entry ->
            val bookId = entry.arguments?.getString("bookId") ?: return@composable
            val ch = entry.arguments?.getInt("chapter") ?: return@composable
            val v = entry.arguments?.getInt("verse") ?: return@composable
            val book = library.getBook(translation, bookId)
            val canon = BibleCanon.byId(bookId)
            val title = book?.name ?: canon?.let { BibleCanon.displayName(it, translation) } ?: bookId
            val verseText = book?.chapters?.find { it.number == ch }?.verses?.find { it.number == v }?.text.orEmpty()

            val commentaryState by viewModel.commentaryState.collectAsStateWithLifecycle()
            val speakCommentaryTts = rememberStudyTextToSpeech(translation)
            LaunchedEffect(translation, bookId, ch, v) {
                viewModel.loadCommentary(translation, bookId, ch, v)
            }
            DisposableEffect(Unit) {
                onDispose { viewModel.resetCommentaryState() }
            }

            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(R.string.commentary_title)) },
                        navigationIcon = {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        },
                    )
                },
            ) { padding ->
                ProvideReaderFontScale(multiplier = readerFontScale) {
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = "$title $ch:$v",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        if (verseText.isNotEmpty()) {
                            Text(
                                text = verseText,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(bottom = 16.dp),
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                        when (commentaryState) {
                            is CommentaryLoadState.Loading -> {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.commentary_loading),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                            is CommentaryLoadState.Ready -> {
                                val commentary = (commentaryState as CommentaryLoadState.Ready).commentary
                                val commentaryScrollState = rememberScrollState()
                                val ttsText = buildString {
                                    append(title)
                                    append(' ')
                                    append(ch)
                                    append(':')
                                    append(v)
                                    append(". ")
                                    if (verseText.isNotEmpty()) {
                                        append("Текст стиха: ")
                                        append(verseText.trim())
                                        append(". ")
                                    }
                                    append(commentary.text.trim())
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(commentaryScrollState),
                                ) {
                                    Text(
                                        text = commentary.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                FilledTonalButton(
                                    onClick = { speakCommentaryTts.speak(ttsText) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                    Text(stringResource(R.string.verse_action_speak))
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { speakCommentaryTts.stop() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        Icons.Filled.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                    Text(stringResource(R.string.audio_stop))
                                }
                                commentary.audioUrl?.let { audioUrl ->
                                    Spacer(Modifier.height(8.dp))
                                    FilledTonalButton(
                                        onClick = { viewModel.playAudio(audioUrl) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(
                                            Icons.Filled.Headphones,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 8.dp),
                                        )
                                        Text(stringResource(R.string.commentary_play_audio))
                                    }
                                }
                            }
                            is CommentaryLoadState.NotFound -> {
                                Text(
                                    text = stringResource(R.string.commentary_not_found),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            is CommentaryLoadState.Error -> {
                                Text(
                                    text = stringResource(R.string.commentary_error),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            is CommentaryLoadState.Idle -> {
                                Text(
                                    text = stringResource(R.string.commentary_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        composable("search") {
            val searchSettings by viewModel.searchSettings.collectAsStateWithLifecycle()
            var showSettings by remember { mutableStateOf(false) }
            var bibleSearchHistoryMenu by remember { mutableStateOf(false) }
            val scopeLabel = when (searchSettings.scope) {
                SearchScope.ALL -> "Быт-Откр"
                SearchScope.OLD_TESTAMENT -> "Быт-Мал"
                SearchScope.NEW_TESTAMENT -> "Мат-Откр"
                SearchScope.SINGLE_BOOK -> {
                    val canon = searchSettings.singleBookId?.let { BibleCanon.byId(it) }
                    canon?.abbrRu ?: "?"
                }
            }

            if (showSettings) {
                SearchSettingsScreen(
                    settings = searchSettings,
                    onSettingsChange = { viewModel.updateSearchSettings(it) },
                    onBack = { showSettings = false },
                )
            } else {
                Scaffold(
                    topBar = {
                        Column {
                            TopAppBar(
                                title = {
                                    Text(
                                        stringResource(R.string.bible_search_title),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { navController.navigateUp() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                                    }
                                },
                                actions = {
                                    if (bibleSearchHistory.isNotEmpty()) {
                                        Box {
                                            IconButton(onClick = { bibleSearchHistoryMenu = true }) {
                                                Icon(
                                                    Icons.Default.MoreVert,
                                                    contentDescription = stringResource(R.string.bible_search_history_menu_cd),
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = bibleSearchHistoryMenu,
                                                onDismissRequest = { bibleSearchHistoryMenu = false },
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.bible_search_history_clear)) },
                                                    onClick = {
                                                        bibleSearchHistoryMenu = false
                                                        viewModel.clearBibleSearchHistory()
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    IconButton(onClick = {}) {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            )
                        }
                    },
                ) { padding ->
                    SearchScreen(
                        modifier = Modifier.padding(padding),
                        query = searchQuery,
                        onQueryChange = viewModel::setSearchQuery,
                        results = searchListState.results,
                        searchInProgress = searchListState.inProgress,
                        settings = searchSettings,
                        readerTranslation = translation,
                        scopeLabel = scopeLabel,
                        searchHistory = bibleSearchHistory,
                        onApplyHistoryQuery = { viewModel.setSearchQuery(it.query) },
                        onRemoveHistoryEntry = { viewModel.removeBibleSearchHistoryEntry(it) },
                        onRecordSearchHistory = { viewModel.recordBibleSearchHistory(it) },
                        onSettingsClick = { showSettings = true },
                        onSettingsChange = { viewModel.updateSearchSettings(it) },
                        onResultClick = { hit ->
                            scope.launch {
                                viewModel.setTranslation(hit.translation)
                                navController.navigate("read/${hit.bookId}/${hit.chapter}/${hit.verse}") {
                                    popUpTo("search") { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                    )
                }
            }
        }
        composable("bookmarks") {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(R.string.bookmarks_title)) },
                        navigationIcon = {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        },
                    )
                },
            ) { padding ->
                BookmarksScreen(
                    modifier = Modifier.padding(padding),
                    bookmarkKeys = bookmarkKeys,
                    bookmarkTagsMap = bookmarkTagsMap,
                    library = library,
                    onOpen = { ref ->
                        scope.launch {
                            viewModel.setTranslation(ref.translation)
                            navController.navigate("read/${ref.bookId}/${ref.chapter}/${ref.verse}") {
                                popUpTo("bookmarks") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onSetBookmarkTags = { ref, tags -> viewModel.setBookmarkTags(ref, tags) },
                )
            }
        }
        composable("history") {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("История чтения") },
                        navigationIcon = {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        },
                        actions = {
                            if (readingHistory.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearHistory() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Очистить историю")
                                }
                            }
                        },
                    )
                },
            ) { padding ->
                HistoryScreen(
                    modifier = Modifier.padding(padding),
                    history = readingHistory,
                    readingTrace = readingTrace,
                    onOpen = { entry ->
                        scope.launch {
                            viewModel.setTranslation(TranslationId.fromCode(entry.translation))
                            navController.navigate("read/${entry.bookId}/${entry.chapter}/${entry.verse}") {
                                popUpTo("history") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onOpenTrace = { entry ->
                        scope.launch {
                            viewModel.setTranslation(TranslationId.fromCode(entry.translation))
                            navController.navigate("read/${entry.bookId}/${entry.chapter}/${entry.verse}") {
                                popUpTo("history") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
        }
        composable("semantic_lexicon") {
            val userRules by viewModel.userSemanticLexiconRules.collectAsStateWithLifecycle()
            val presetOn by viewModel.lexiconPresetEnabled.collectAsStateWithLifecycle()
            val toneIds by viewModel.lexiconPresetToneIds.collectAsStateWithLifecycle()
            val lexiconUserEnabled by viewModel.lexiconUserEnabled.collectAsStateWithLifecycle()
            val lexiconUserToneIds by viewModel.lexiconUserToneIds.collectAsStateWithLifecycle()
            SemanticLexiconScreen(
                userRules = userRules,
                presetRules = com.example.bible.data.PresetSemanticLexicon.rules(),
                lexiconPresetEnabled = presetOn,
                lexiconPresetToneIds = toneIds,
                lexiconUserEnabled = lexiconUserEnabled,
                lexiconUserToneIds = lexiconUserToneIds,
                onSetLexiconUserEnabled = { viewModel.setLexiconUserEnabled(it) },
                onSetLexiconUserToneIds = { viewModel.setLexiconUserToneIds(it) },
                onSaveUserRule = { viewModel.saveUserSemanticLexiconRule(it) },
                onDeleteUserRule = { viewModel.deleteUserSemanticLexiconRule(it) },
                onSetPresetEnabled = { viewModel.setLexiconPresetEnabled(it) },
                onSetPresetToneIds = { viewModel.setLexiconPresetToneIds(it) },
                onBack = { navController.navigateUp() },
            )
        }
        composable("notes") {
            val notes by viewModel.userNotes.collectAsStateWithLifecycle()
            NotesListScreen(
                notes = notes,
                onCreateNote = {
                    val newNote = UserNote()
                    viewModel.saveNote(newNote)
                    navController.navigate("note_edit/${newNote.id}")
                },
                onOpenNote = { noteId ->
                    navController.navigate("note_edit/$noteId")
                },
                onDeleteNote = { noteId ->
                    viewModel.deleteNote(noteId)
                },
                onBack = { navController.navigateUp() },
            )
        }
        composable("note_edit/{noteId}") { entry ->
            val noteId = entry.arguments?.getString("noteId") ?: return@composable
            val notes by viewModel.userNotes.collectAsStateWithLifecycle()
            val noteCustomKinds by viewModel.noteCustomKinds.collectAsStateWithLifecycle()
            val note = notes.find { it.id == noteId } ?: UserNote(id = noteId)
            NoteEditorScreen(
                initialNote = note,
                allNotes = notes,
                customKinds = noteCustomKinds,
                onAddCustomKind = { viewModel.addNoteCustomKind(it) },
                onSave = { viewModel.saveNote(it) },
                onBack = { navController.navigateUp() },
            )
        }
    }

    LaunchedEffect(Unit) {
        val saved = preferences.loadLastSessionResume() ?: return@LaunchedEffect
        if (!saved.isValidForRestore()) {
            preferences.applyResumePersistAction(ResumePersistAction.ClearStored)
            return@LaunchedEffect
        }
        when (saved.kind) {
            LastSessionResumeKind.READ -> {
                viewModel.setTranslation(TranslationId.fromCode(saved.translationCode))
                navController.navigate("read/${saved.bookId}/${saved.chapter}/${saved.scrollVerse}") {
                    popUpTo("books") { inclusive = false }
                    launchSingleTop = true
                }
            }
            LastSessionResumeKind.DUAL -> {
                viewModel.setTranslation(TranslationId.fromCode(saved.translationCode))
                navController.navigate("dual") {
                    popUpTo("books") { inclusive = false }
                    launchSingleTop = true
                }
            }
            LastSessionResumeKind.NOTE -> {
                navController.navigate("note_edit/${saved.noteId}") {
                    popUpTo("books") { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
        preferences.applyResumePersistAction(ResumePersistAction.ClearStored)
    }
}

@Composable
private fun SearchScreen(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<SearchHit>,
    searchInProgress: Boolean,
    settings: SearchSettings,
    readerTranslation: TranslationId,
    scopeLabel: String,
    searchHistory: List<BibleSearchHistoryEntry>,
    onApplyHistoryQuery: (BibleSearchHistoryEntry) -> Unit,
    onRemoveHistoryEntry: (BibleSearchHistoryEntry) -> Unit,
    onRecordSearchHistory: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onSettingsChange: (SearchSettings) -> Unit,
    onResultClick: (SearchHit) -> Unit,
) {
    LaunchedEffect(query, searchInProgress) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || searchInProgress) return@LaunchedEffect
        delay(1100)
        if (query.trim() != trimmed) return@LaunchedEffect
        if (searchInProgress) return@LaunchedEffect
        onRecordSearchHistory(trimmed)
    }
    val accentColor = MaterialTheme.colorScheme.primary
    var showScopePicker by remember { mutableStateOf(false) }
    var showTranslationPicker by remember { mutableStateOf(false) }
    val translationChipScroll = rememberScrollState()
    val historyShown = remember(searchHistory) { searchHistory.reversed().take(12) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                },
            )
            IconButton(onClick = {}) {
                Icon(Icons.Default.Search, contentDescription = null, tint = accentColor)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(translationChipScroll)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val scopeName = when (settings.scope) {
                SearchScope.ALL -> "Вся Библия"
                SearchScope.OLD_TESTAMENT -> "Ветхий Завет"
                SearchScope.NEW_TESTAMENT -> "Новый Завет"
                SearchScope.SINGLE_BOOK -> {
                    settings.singleBookId?.let { BibleCanon.byId(it)?.nameRu } ?: "Книга"
                }
            }
            FilterChip(
                selected = true,
                onClick = { showScopePicker = true },
                label = { Text(scopeName, fontSize = 12.sp) },
            )
            val translationChipText = when (settings.translationMode) {
                BibleSearchTranslationMode.FOLLOW_READER ->
                    "${stringResource(R.string.bible_search_translation_follow_reader)} (${readerTranslation.shortLabel})"
                BibleSearchTranslationMode.SINGLE -> settings.singleTranslationId.shortLabel
                BibleSearchTranslationMode.ALL -> stringResource(R.string.bible_search_translation_all)
            }
            FilterChip(
                selected = true,
                onClick = { showTranslationPicker = true },
                label = {
                    Text(
                        "${stringResource(R.string.bible_search_translation_scope)}: $translationChipText",
                        fontSize = 11.sp,
                        maxLines = 2,
                    )
                },
            )
            Text(
                text = scopeLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
        }

        if (showScopePicker) {
            SearchScopePicker(
                currentScope = settings.scope,
                currentBookId = settings.singleBookId,
                onScopeSelected = { scope, bookId ->
                    onSettingsChange(settings.copy(scope = scope, singleBookId = bookId))
                    showScopePicker = false
                },
                onDismiss = { showScopePicker = false },
            )
        }
        if (showTranslationPicker) {
            SearchTranslationPicker(
                currentMode = settings.translationMode,
                currentSingleId = settings.singleTranslationId,
                readerTranslation = readerTranslation,
                onSelected = { mode, singleId ->
                    onSettingsChange(
                        settings.copy(
                            translationMode = mode,
                            singleTranslationId = singleId ?: settings.singleTranslationId,
                        ),
                    )
                    showTranslationPicker = false
                },
                onDismiss = { showTranslationPicker = false },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = false,
                    onCheckedChange = {},
                    colors = CheckboxDefaults.colors(
                        checkedColor = accentColor,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("копировать", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = true,
                    onCheckedChange = {},
                    colors = CheckboxDefaults.colors(
                        checkedColor = accentColor,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("перейти", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (searchInProgress) "…" else "${results.size}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (query.isNotBlank() && searchInProgress) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            if (historyShown.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.bible_search_history_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                items(
                    items = historyShown,
                    key = { "${it.timestamp}|${it.dedupKey}" },
                ) { entry ->
                    BibleSearchHistoryRow(
                        entry = entry,
                        onApply = { onApplyHistoryQuery(entry) },
                        onRemove = { onRemoveHistoryEntry(entry) },
                    )
                    Spacer(Modifier.height(6.dp))
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            if (query.isBlank()) {
                item {
                    Text(
                        text = stringResource(R.string.search_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
            if (query.isNotBlank() && searchInProgress && results.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.bible_search_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
            itemsIndexed(
                results,
                key = { index, hit ->
                    "${hit.translation.code}|${hit.bookId}|${hit.chapter}|${hit.verse}|$index"
                },
            ) { _, hit ->
                SearchResultItem(
                    hit = hit,
                    query = query,
                    settings = settings,
                    showTranslationBadge = settings.translationMode == BibleSearchTranslationMode.ALL,
                    onClick = { onResultClick(hit) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun BibleSearchHistoryRow(
    entry: BibleSearchHistoryEntry,
    onApply: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        onClick = onApply,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Text(
                    entry.query,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatBibleSearchHistoryTime(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.bible_search_history_remove_cd),
                )
            }
        }
    }
}

private fun formatBibleSearchHistoryTime(epochMs: Long): String {
    val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .withLocale(Locale.getDefault())
    return fmt.format(Instant.ofEpochMilli(epochMs))
}

@Composable
private fun SearchResultItem(
    hit: SearchHit,
    query: String,
    settings: SearchSettings,
    showTranslationBadge: Boolean,
    onClick: () -> Unit,
) {
    val canon = BibleCanon.byId(hit.bookId)
    val abbr = canon?.abbrRu ?: hit.bookId
    val highlightBg = MaterialTheme.colorScheme.tertiaryContainer
    val highlightFg = MaterialTheme.colorScheme.onTertiaryContainer
    val annotated = remember(hit.text, query, settings, highlightBg, highlightFg) {
        buildHighlightedText(hit.text, query, settings, highlightFg, highlightBg)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column {
            if (showTranslationBadge) {
                Text(
                    text = hit.translation.shortLabel,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "$abbr\n${hit.chapter}:${hit.verse}",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = annotated,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun buildHighlightedText(
    text: String,
    query: String,
    settings: SearchSettings,
    highlightForeground: Color,
    highlightBackground: Color,
): AnnotatedString = runCatching {
    buildAnnotatedString {
        if (query.isBlank() || !settings.highlightMatches) {
            append(text)
            return@buildAnnotatedString
        }
        var ranges = computeSearchHighlightRanges(text, query, settings)
        if (ranges.isEmpty()) {
            val q = query.trim()
            if (q.isNotEmpty()) {
                val idx = if (settings.caseSensitive) text.indexOf(q) else text.indexOf(q, ignoreCase = true)
                if (idx >= 0) {
                    ranges = listOf(idx until (idx + q.length).coerceAtMost(text.length))
                }
            }
        }
        if (ranges.isEmpty()) {
            append(text)
            return@buildAnnotatedString
        }
        val spanStyle = SpanStyle(
            color = highlightForeground,
            background = highlightBackground,
            fontWeight = FontWeight.SemiBold,
        )
        var idx = 0
        for (r in ranges) {
            if (r.first < 0 || r.last >= text.length || r.first > r.last) continue
            if (r.first > idx) append(text.substring(idx, r.first))
            withStyle(spanStyle) {
                append(text.substring(r.first, r.last + 1))
            }
            idx = r.last + 1
        }
        if (idx < text.length) append(text.substring(idx))
    }
}.getOrElse { AnnotatedString(text) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSettingsScreen(
    settings: SearchSettings,
    onSettingsChange: (SearchSettings) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки поиска") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SearchSettingRow(
                "Искать по словам без учёта кавычек, пунктуации и спецсимволов",
                "+p",
                settings.ignoreSeparators,
            ) {
                onSettingsChange(settings.copy(ignoreSeparators = it))
            }
            SearchSettingRow("Поиск целых слов", "-w", settings.wholeWords) {
                onSettingsChange(settings.copy(wholeWords = it))
            }
            SearchSettingRow("Поиск слов в указанном порядке", "-r", settings.orderedWords) {
                onSettingsChange(settings.copy(orderedWords = it))
            }
            SearchSettingRow("Поиск с учетом акцентов и подобных символов", "-a", settings.accentSensitive) {
                onSettingsChange(settings.copy(accentSensitive = it))
            }
            SearchSettingRow("Поиск с учетом регистра", "-c", settings.caseSensitive) {
                onSettingsChange(settings.copy(caseSensitive = it))
            }
            SearchSettingRow("Поиск с различением типов кавычек и дефисов/тире", "-q", settings.punctuationSensitive) {
                onSettingsChange(settings.copy(punctuationSensitive = it))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))

            Text(
                text = "Представление результатов",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            SearchSettingRow("Выделять в результатах поиска найденные слова", "+m", settings.highlightMatches) {
                onSettingsChange(settings.copy(highlightMatches = it))
            }

            Spacer(Modifier.height(24.dp))
            FilledTonalButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("ОК")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SearchSettingRow(
    label: String,
    flag: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = flag,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SearchTranslationPicker(
    currentMode: BibleSearchTranslationMode,
    currentSingleId: TranslationId,
    readerTranslation: TranslationId,
    onSelected: (BibleSearchTranslationMode, TranslationId?) -> Unit,
    onDismiss: () -> Unit,
) {
    var showTranslationList by remember { mutableStateOf(false) }

    if (showTranslationList) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
                .heightIn(max = 320.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.bible_search_pick_translation),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = { showTranslationList = false }) {
                    Text("Назад", color = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(TranslationId.entries, key = { it.name }) { tid ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelected(BibleSearchTranslationMode.SINGLE, tid)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tid.labelRu, fontSize = 14.sp)
                        if (currentMode == BibleSearchTranslationMode.SINGLE && tid == currentSingleId) {
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            ScopeOption(
                stringResource(R.string.bible_search_translation_follow_reader),
                readerTranslation.shortLabel,
                currentMode == BibleSearchTranslationMode.FOLLOW_READER,
            ) {
                onSelected(BibleSearchTranslationMode.FOLLOW_READER, null)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTranslationList = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val isSingle = currentMode == BibleSearchTranslationMode.SINGLE
                Icon(
                    if (isSingle) Icons.Default.Check else Icons.Default.Search,
                    contentDescription = null,
                    tint = if (isSingle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (isSingle) {
                        currentSingleId.labelRu
                    } else {
                        stringResource(R.string.bible_search_translation_single)
                    },
                    color = if (isSingle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                )
            }
            ScopeOption(
                stringResource(R.string.bible_search_translation_all),
                "локальные",
                currentMode == BibleSearchTranslationMode.ALL,
            ) {
                onSelected(BibleSearchTranslationMode.ALL, null)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Закрыть", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SearchScopePicker(
    currentScope: SearchScope,
    currentBookId: String?,
    onScopeSelected: (SearchScope, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var showBookList by remember { mutableStateOf(false) }

    if (showBookList) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
                .heightIn(max = 300.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Выберите книгу", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showBookList = false }) {
                    Text("Назад", color = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(BibleCanon.allBooks, key = { it.id }) { entry ->
                    val color = groupTextColor(entry.group)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onScopeSelected(SearchScope.SINGLE_BOOK, entry.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(entry.abbrRu, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(entry.nameRu, color = color, fontSize = 14.sp)
                        if (entry.id == currentBookId && currentScope == SearchScope.SINGLE_BOOK) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            ScopeOption("Вся Библия", "Быт-Откр", currentScope == SearchScope.ALL) {
                onScopeSelected(SearchScope.ALL, null)
            }
            ScopeOption("Ветхий Завет", "Быт-Мал", currentScope == SearchScope.OLD_TESTAMENT) {
                onScopeSelected(SearchScope.OLD_TESTAMENT, null)
            }
            ScopeOption("Новый Завет", "Мат-Откр", currentScope == SearchScope.NEW_TESTAMENT) {
                onScopeSelected(SearchScope.NEW_TESTAMENT, null)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBookList = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val isBookScope = currentScope == SearchScope.SINGLE_BOOK
                val bookName = if (isBookScope) {
                    currentBookId?.let { BibleCanon.byId(it)?.nameRu } ?: "Выбрать..."
                } else "Конкретная книга..."
                Icon(
                    if (isBookScope) Icons.Default.Check else Icons.Default.Search,
                    contentDescription = null,
                    tint = if (isBookScope) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(bookName, color = if (isBookScope) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Закрыть", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ScopeOption(
    label: String,
    range: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Default.Check else Icons.Default.Search,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(range, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun BookmarksScreen(
    modifier: Modifier = Modifier,
    bookmarkKeys: Set<String>,
    bookmarkTagsMap: Map<String, Set<String>>,
    library: BibleLibrary,
    onOpen: (VerseRef) -> Unit,
    onSetBookmarkTags: (VerseRef, Set<String>) -> Unit,
) {
    val refs = remember(bookmarkKeys) {
        bookmarkKeys.mapNotNull { VerseRef.fromKey(it) }
            .sortedWith(compareBy({ it.translation }, { it.bookId }, { it.chapter }, { it.verse }))
    }
    val allTags = remember(bookmarkTagsMap) {
        bookmarkTagsMap.values.flatten().map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    var filterTag by remember { mutableStateOf<String?>(null) }
    var tagEditRef by remember { mutableStateOf<VerseRef?>(null) }
    val filteredRefs = remember(refs, filterTag, bookmarkTagsMap) {
        if (filterTag == null) refs
        else refs.filter { ref ->
            bookmarkTagsMap[ref.toKey()]?.contains(filterTag) == true
        }
    }
    tagEditRef?.let { editRef ->
        val key = editRef.toKey()
        var tagText by remember(key) {
            mutableStateOf(bookmarkTagsMap[key]?.joinToString(", ").orEmpty())
        }
        AlertDialog(
            onDismissRequest = { tagEditRef = null },
            title = { Text(stringResource(R.string.bookmarks_tags)) },
            text = {
                OutlinedTextField(
                    value = tagText,
                    onValueChange = { tagText = it },
                    label = { Text("Через запятую") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val tags = tagText.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
                        onSetBookmarkTags(editRef, tags)
                        tagEditRef = null
                    },
                ) { Text(stringResource(R.string.highlight_done)) }
            },
            dismissButton = {
                TextButton(onClick = { tagEditRef = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    if (refs.isEmpty()) {
        Text(
            text = stringResource(R.string.bookmarks_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(24.dp),
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        if (allTags.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    item {
                        FilterChip(
                            selected = filterTag == null,
                            onClick = { filterTag = null },
                            label = { Text(stringResource(R.string.bookmarks_filter_all)) },
                        )
                    }
                    items(allTags, key = { it }) { tag ->
                        FilterChip(
                            selected = filterTag == tag,
                            onClick = { filterTag = if (filterTag == tag) null else tag },
                            label = { Text(tag) },
                        )
                    }
                }
            }
        }
        items(filteredRefs, key = { it.toKey() }) { ref ->
            val bookName = library.getBook(ref.translation, ref.bookId)?.name
                ?: BibleCanon.byId(ref.bookId)?.let { BibleCanon.displayName(it, ref.translation) }
                ?: ref.bookId
            val tags = bookmarkTagsMap[ref.toKey()].orEmpty()
            ListItem(
                headlineContent = {
                    Text(
                        "${ref.translation.labelRu} · $bookName ${ref.chapter}:${ref.verse}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                supportingContent = {
                    Column {
                        val verseText = library.getBook(ref.translation, ref.bookId)
                            ?.chapters?.find { it.number == ref.chapter }
                            ?.verses?.find { it.number == ref.verse }
                            ?.text
                        Text(
                            verseText.orEmpty(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (tags.isNotEmpty()) {
                            Text(
                                tags.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                },
                trailingContent = {
                    TextButton(onClick = { tagEditRef = ref }) {
                        Text(stringResource(R.string.bookmarks_tags))
                    }
                },
                modifier = Modifier.clickable { onOpen(ref) },
            )
            HorizontalDivider()
        }
    }
}

private fun formatHistoryDwell(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "—"
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h} ч ${m} мин"
        m > 0 -> "${m} мин ${s} с"
        else -> "${s} с"
    }
}

@Composable
private fun HistoryScreen(
    modifier: Modifier = Modifier,
    history: List<HistoryEntry>,
    readingTrace: List<ReadingTraceEntry>,
    onOpen: (HistoryEntry) -> Unit,
    onOpenTrace: (ReadingTraceEntry) -> Unit,
) {
    val activityByDay = remember(history) { readingHistoryToDayCounts(history) }
    val sorted = remember(history) { history.sortedByDescending { it.timestamp } }
    val sortedTrace = remember(readingTrace) { readingTrace.sortedByDescending { it.timestamp } }
    val dateFormat = remember {
        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        item {
            BibleReadingHeatmapCard(activityByDay = activityByDay)
        }
        if (sortedTrace.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(
                        text = "Стих за стихом",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Хронология просмотра на этом устройстве (самые свежие сверху).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            items(
                sortedTrace,
                key = { trace ->
                    "${trace.timestamp}-${trace.translation}-${trace.bookId}-${trace.chapter}-${trace.verse}"
                },
            ) { trace ->
                val translationLabel = TranslationId.fromCode(trace.translation).labelRu
                val canon = BibleCanon.byId(trace.bookId)
                val bookName = trace.bookName.ifEmpty { canon?.nameRu ?: trace.bookId }
                val verseLabel = if (trace.verse > 0) ":${trace.verse}" else ""
                ListItem(
                    headlineContent = {
                        Text(
                            "$bookName ${trace.chapter}$verseLabel",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                "$translationLabel · ${dateFormat.format(java.util.Date(trace.timestamp))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (trace.dwellSeconds > 0) {
                                Text(
                                    "На стихе: ${formatHistoryDwell(trace.dwellSeconds)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            if (trace.tools.isNotBlank()) {
                                Text(
                                    "Открыто: ${trace.tools.replace("|", " · ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { onOpenTrace(trace) },
                )
                HorizontalDivider()
            }
        }
        if (history.isEmpty() && sortedTrace.isEmpty()) {
            item {
                Text(
                    text = "Список посещений пуст. Откройте любую главу в Библии — записи появятся ниже.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        } else if (history.isNotEmpty()) {
            items(sorted, key = { "${it.translation}-${it.bookId}-${it.chapter}-${it.verse}-${it.timestamp}" }) { entry ->
                val translationLabel = TranslationId.fromCode(entry.translation).labelRu
                val canon = BibleCanon.byId(entry.bookId)
                val bookName = entry.bookName.ifEmpty { canon?.nameRu ?: entry.bookId }
                val verseLabel = if (entry.verse > 0) ":${entry.verse}" else ""
                ListItem(
                    headlineContent = {
                        Text(
                            "$bookName ${entry.chapter}$verseLabel",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                "$translationLabel · ${dateFormat.format(java.util.Date(entry.timestamp))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (entry.dwellSeconds > 0) {
                                Text(
                                    "Время на стихе: ${formatHistoryDwell(entry.dwellSeconds)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            if (entry.toolsUsed.isNotBlank()) {
                                Text(
                                    "Открыто: ${entry.toolsUsed.replace("|", " · ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { onOpen(entry) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ChapterGrid(
    modifier: Modifier = Modifier,
    book: BibleBook,
    chaptersWithAudio: Set<Int> = emptySet(),
    onChapterClick: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        gridItems(book.chapters, key = { it.number }) { chapter: BibleChapter ->
            val hasAudio = chapter.number in chaptersWithAudio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .clickable { onChapterClick(chapter.number) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${chapter.number}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (hasAudio) {
                            Icon(
                                Icons.Default.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(14.dp),
                            )
                        }
                    }
                    Text(
                        text = "${chapter.verses.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun VerseGrid(
    modifier: Modifier = Modifier,
    verses: List<BibleVerse>,
    onVerseClick: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        gridItems(verses, key = { it.number }) { verse ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .clickable { onVerseClick(verse.number) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${verse.number}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MultiDictionarySheet(
    word: String,
    results: List<DictResult>,
    seeAlso: List<String>,
    onWordClick: (String) -> Unit,
    onDismiss: () -> Unit,
    lexiconRule: com.example.bible.data.SemanticLexiconRule? = null,
    attachedMedia: com.example.bible.data.LexiconMediaRefs? = null,
    mediaLibraryImages: List<com.example.bible.data.BibleUserImage> = emptyList(),
    mediaLibraryVideos: List<com.example.bible.data.BibleUserVideo> = emptyList(),
    mediaLibraryAudios: List<com.example.bible.data.BibleUserAudio> = emptyList(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                word,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))

            if (lexiconRule != null) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    LexiconRuleHeaderAndMedia(rule = lexiconRule)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }

            if (attachedMedia != null && attachedMedia.hasAny()) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    LexiconMediaSection(
                        contentLabel = word,
                        media = attachedMedia,
                        mediaLibraryImages = mediaLibraryImages,
                        mediaLibraryVideos = mediaLibraryVideos,
                        mediaLibraryAudios = mediaLibraryAudios,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }

            Spacer(Modifier.height(4.dp))

            if (results.isEmpty()) {
                Text(
                    "Определение не найдено",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else if (results.size == 1) {
                val r = results[0]
                Text(
                    r.source,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    r.definition,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                        .weight(1f, fill = false),
                )
            } else {
                val pagerState = rememberPagerState(pageCount = { results.size })
                var selectedTab by remember { mutableIntStateOf(0) }

                LaunchedEffect(pagerState.currentPage) {
                    selectedTab = pagerState.currentPage
                }
                LaunchedEffect(selectedTab) {
                    if (pagerState.currentPage != selectedTab) {
                        pagerState.animateScrollToPage(selectedTab)
                    }
                }

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp,
                    containerColor = Color.Transparent,
                    divider = {},
                ) {
                    results.forEachIndexed { i, r ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = { Text(r.source, fontSize = 13.sp, maxLines = 1) },
                        )
                    }
                }
                HorizontalDivider()

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) { page ->
                    val r = results[page]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        if (r.word != word) {
                            Text(
                                r.word,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            r.definition,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 24.sp,
                        )
                    }
                }
            }

            if (seeAlso.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "См. также:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    seeAlso.forEach { w ->
                        FilterChip(
                            selected = false,
                            onClick = { onWordClick(w) },
                            label = { Text(w, fontSize = 13.sp) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextSizeSettingsDialog(
    viewModel: BibleViewModel,
    onDismiss: () -> Unit,
) {
    val readerScale by viewModel.readerFontScale.collectAsStateWithLifecycle()
    val songSize by viewModel.songFontSize.collectAsStateWithLifecycle()

    val readerSp = (ReaderFontScaleDefaults.BASE_SP * readerScale)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Размер текста") },
        text = {
            Column {
                Text(
                    "Текст Библии и стихов: ${readerSp.toInt()} sp",
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = readerSp,
                    onValueChange = { sp ->
                        viewModel.setReaderFontScale(sp / ReaderFontScaleDefaults.BASE_SP)
                    },
                    valueRange = 3f..150f,
                    steps = 0,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("3", style = MaterialTheme.typography.labelSmall)
                    Text("150", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "Текст песен: ${songSize.toInt()} sp",
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = songSize,
                    onValueChange = { viewModel.setSongFontSize(it) },
                    valueRange = 3f..150f,
                    steps = 0,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("3", style = MaterialTheme.typography.labelSmall)
                    Text("150", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.setReaderFontScale(ReaderFontScaleDefaults.DEFAULT)
                viewModel.setSongFontSize(18f)
            }) { Text("Сброс") }
        },
    )
}

@Composable
private fun DailyVerseCard(
    entry: DailyVerseEntry,
    onClick: () -> Unit,
) {
    val ref = remember(entry) { DailyVerse.referenceLabel(entry) }
    val colors = MaterialTheme.colorScheme
    val gradientBrush = Brush.linearGradient(
        colors = listOf(colors.primaryContainer, colors.secondaryContainer),
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(14.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colors.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Стих дня",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    entry.textRu,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    ref,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickNavigatorSheet(
    library: BibleLibrary,
    translation: TranslationId,
    currentBookId: String,
    onNavigate: (bookId: String, chapter: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedBookId by remember { mutableStateOf(currentBookId) }
    var step by remember { mutableStateOf(if (currentBookId.isNotEmpty()) "chapters" else "books") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 450.dp)
                .padding(bottom = 16.dp),
        ) {
            if (step == "chapters") {
                val canon = BibleCanon.byId(selectedBookId)
                val book = library.getBook(translation, selectedBookId)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { step = "books" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Книги")
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        canon?.nameRu ?: selectedBookId,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.weight(1f))
                }
                HorizontalDivider()
                if (book != null && book.chapters.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        gridItems(book.chapters, key = { it.number }) { ch ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                    .clickable { onNavigate(selectedBookId, ch.number) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${ch.number}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Text(
                        "Нет данных",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "Выберите книгу",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    gridItems(BibleCanon.allBooks, key = { it.id }) { entry ->
                        val color = groupTextColor(entry.group)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 42.dp)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .background(
                                    if (entry.id == selectedBookId) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                )
                                .clickable {
                                    selectedBookId = entry.id
                                    step = "chapters"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                entry.abbrRu,
                                color = color,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Стих, ближе всего к центру экрана (для учёта времени чтения). */
private fun primaryVisibleVerseNumber(
    listState: LazyListState,
    verses: List<BibleVerse>,
): Int? {
    if (verses.isEmpty()) return null
    val info = listState.layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return null
    val viewportCenter = info.viewportStartOffset + info.viewportSize.height / 2
    var bestVerse: Int? = null
    var bestDist = Int.MAX_VALUE
    for (item in info.visibleItemsInfo) {
        val li = item.index
        if (li <= 0) continue
        val vIdx = li - 1
        if (vIdx !in verses.indices) continue
        val center = item.offset + item.size / 2
        val dist = kotlin.math.abs(center - viewportCenter)
        if (dist < bestDist) {
            bestDist = dist
            bestVerse = verses[vIdx].number
        }
    }
    return bestVerse
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderContent(
    modifier: Modifier = Modifier,
    verses: List<BibleVerse>,
    scrollToVerse: Int,
    translation: TranslationId,
    bookId: String,
    bookName: String,
    chapter: Int,
    bookmarkKeys: Set<String>,
    textHighlights: List<TextHighlight>,
    onAddTextHighlight: (TextHighlight) -> Unit,
    onRemoveTextHighlights: (VerseRef, Int, Int) -> Unit,
    onToggleBookmark: (VerseRef) -> Unit,
    onVerseCommentary: (VerseRef) -> Unit,
    onPlayAudio: ((VerseRef, () -> Unit) -> Unit)? = null,
    onNavigateToVerse: ((String, Int, Int) -> Unit)? = null,
    audioPlaybackState: AudioPlaybackState = AudioPlaybackState.IDLE,
    audioPlaybackSpeed: Float = 1.0f,
    onTogglePause: () -> Unit = {},
    onCycleSpeed: () -> Unit = {},
    onStopAudio: () -> Unit = {},
    getAudioProgress: () -> Pair<Int, Int> = { 0 to 0 },
    onSeekAudio: (Int) -> Unit = {},
    onPauseMainAudioForAttachment: () -> Unit = {},
    /** Нижний отступ под панель озвучки главы (измеренная высота [BibleAudioBar]) */
    bibleChapterAudioBarBottomInset: Dp = 0.dp,
    /** Сохранённый проект таймкодов для этой главы (перевод должен совпадать с [translation]). */
    timemarkProject: TimemarkProject? = null,
    /** Картинки из «Медиа → Картинки» для вложений к стиху. */
    mediaLibraryImages: List<com.example.bible.data.BibleUserImage> = emptyList(),
    /** Видео из «Медиа → Видео» (лексикон, вложения). */
    mediaLibraryVideos: List<com.example.bible.data.BibleUserVideo> = emptyList(),
    /** Аудио из «Медиа → Аудио» (лексикон, вложения к словам). */
    mediaLibraryAudios: List<com.example.bible.data.BibleUserAudio> = emptyList(),
    /** Номера стихов текущей главы, к которым уже есть личные заметки. */
    verseNumbersWithNotes: Set<Int> = emptySet(),
    /** Все заметки (для меню и быстрого открытия по иконке). */
    userNotes: List<UserNote> = emptyList(),
    /** Создать заметку к выбранному стиху (перевод, книга, глава, стих, текст). */
    onVerseNote: ((VerseRef, bookName: String, verseText: String) -> Unit)? = null,
    /** Открыть существующую заметку по id (из меню стиха или по иконке). */
    onOpenExistingVerseNote: ((String) -> Unit)? = null,
    /** Временная тематическая подсветка слов по словарям. */
    semanticHighlightSession: com.example.bible.data.SemanticHighlightSession? = null,
    userLexiconRules: List<com.example.bible.data.SemanticLexiconRule> = emptyList(),
    presetLexiconRules: List<com.example.bible.data.SemanticLexiconRule> = emptyList(),
    presetLexiconEnabled: Boolean = true,
    wordSpanMediaAttachments: List<WordSpanMediaAttachment> = emptyList(),
    onUpsertWordSpanMedia: (WordSpanMediaAttachment) -> Unit = {},
    onDeleteWordSpanMedia: (String) -> Unit = {},
    onRemoveWordSpanMediaIntersecting: (VerseRef, Int, Int) -> Unit = { _, _, _ -> },
    trackReadingDwell: Boolean = false,
    onPauseDwellTracking: () -> Unit = {},
    onReadingDwellVerse: ((verse: Int, translation: TranslationId) -> Unit)? = null,
    onLexiconLookupOpened: () -> Unit = {},
    mimicScrollDy: Flow<Float> = emptyFlow(),
    /** false — в читалке отключить прокрутку списка пальцем (при мимике стихи листаются только с открытым ртом). */
    readerFingerScrollEnabled: Boolean = true,
    /** Подстрочник Винокурова (ВЗ): открыть весь стих в песочнице иврита. */
    onOpenInterlinearHebrewSandboxWholeVerse: ((VerseRef) -> Unit)? = null,
) {
    val highlightsForReader = remember(textHighlights, translation, bookId, chapter) {
        textHighlights.filter {
            it.translation == translation && it.bookId == bookId && it.chapter == chapter
        }
    }
    var selectionInfo by remember { mutableStateOf<VerseHighlightSelection?>(null) }
    var clearSelectionSignal by remember { mutableIntStateOf(0) }
    var verseActionsTarget by remember { mutableStateOf<VerseActionTarget?>(null) }
    var attachmentPreview by remember { mutableStateOf<VerseAttachment?>(null) }
    val readerContext = LocalContext.current
    val attachmentStore = remember { VerseAttachmentStore.get(readerContext) }
    val attachmentIndexTick by attachmentStore.attachmentIndexVersion.collectAsState()
    val dictManager = remember { DictionaryManager.getInstance(readerContext) }
    var dictionaryLookup by remember { mutableStateOf<LexiconDictionarySheetState?>(null) }
    var dictionarySeeAlso by remember { mutableStateOf<List<String>>(emptyList()) }
    val speak = rememberVerseTextToSpeech(translation)

    LaunchedEffect(dictionaryLookup?.word) {
        if (dictionaryLookup != null) onLexiconLookupOpened()
    }

    val lexiconById = remember(userLexiconRules, presetLexiconRules) {
        (userLexiconRules + presetLexiconRules).associateBy { it.id }
    }

    val spanMediaForChapter = remember(wordSpanMediaAttachments, translation, bookId, chapter) {
        wordSpanMediaAttachments.filter {
            it.translation == translation && it.bookId == bookId && it.chapter == chapter
        }
    }
    var wordMediaDialog by remember { mutableStateOf<Pair<VerseHighlightSelection, WordSpanMediaAttachment?>?>(null) }

    val interlinearTts = remember(translation) {
        if (translation == TranslationId.INTERLINEAR) InterlinearTts(readerContext.applicationContext) else null
    }
    DisposableEffect(interlinearTts) {
        onDispose { interlinearTts?.shutdown() }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(listState, mimicScrollDy) {
        mimicScrollDy.collect { dy ->
            listState.scroll { scrollBy(dy) }
        }
    }
    var wasDwellTracked by remember { mutableStateOf(false) }
    LaunchedEffect(trackReadingDwell) {
        if (wasDwellTracked && !trackReadingDwell) onPauseDwellTracking()
        wasDwellTracked = trackReadingDwell
    }
    LaunchedEffect(trackReadingDwell, listState, verses, translation) {
        if (!trackReadingDwell || onReadingDwellVerse == null || verses.isEmpty()) return@LaunchedEffect
        snapshotFlow { primaryVisibleVerseNumber(listState, verses) }
            .distinctUntilChanged()
            .debounce(400L)
            .collect { v ->
                if (v != null) onReadingDwellVerse(v, translation)
            }
    }
    val bibleChapterAudioState by BibleAudioPlayer.state.collectAsState()
    var timemarkVerseRange by remember(timemarkProject?.id) { mutableStateOf<IntRange?>(null) }
    var timemarkBarInset by remember(timemarkProject?.id) { mutableStateOf(0.dp) }
    val audioHighlightVerse: Int? = run {
        val s = bibleChapterAudioState
        if (s.bookId != bookId || s.chapter != chapter || s.durationMs <= 0) {
            null
        } else {
            verseNumberAtChapterAudioPosition(verses, s.positionMs, s.durationMs)
        }
    }
    LaunchedEffect(scrollToVerse, verses) {
        if (scrollToVerse <= 0) return@LaunchedEffect
        val idx = verses.indexOfFirst { it.number == scrollToVerse }
        if (idx >= 0) {
            listState.scrollToItem(idx)
        }
    }
    LaunchedEffect(audioHighlightVerse, bibleChapterAudioState.isPlaying, verses) {
        if (!bibleChapterAudioState.isPlaying) return@LaunchedEffect
        val v = audioHighlightVerse ?: return@LaunchedEffect
        val idx = verses.indexOfFirst { it.number == v }
        if (idx >= 0) {
            listState.scrollToItem(idx)
        }
    }
    LaunchedEffect(timemarkVerseRange?.first, timemarkVerseRange?.last, verses) {
        val r = timemarkVerseRange ?: return@LaunchedEffect
        val idx = verses.indexOfFirst { it.number == r.first }
        if (idx >= 0) {
            listState.scrollToItem(idx)
        }
    }

    val bottomPad = if (selectionInfo != null) 88.dp else 16.dp
    val mediaBarVisible = when (audioPlaybackState) {
        AudioPlaybackState.PLAYING,
        AudioPlaybackState.PAUSED,
        AudioPlaybackState.LOADING,
        -> true
        else -> false
    }
    val biblePad =
        if (bibleChapterAudioBarBottomInset > 0.dp) bibleChapterAudioBarBottomInset + 8.dp
        else 0.dp
    val bottomObstruction = maxOf(
        biblePad,
        if (mediaBarVisible) 88.dp else 0.dp,
        timemarkBarInset,
    )
    val interlinearChapterWordStarts = remember(verses) {
        var acc = 0
        buildList {
            for (v in verses) {
                add(acc)
                acc += v.interlinearWords?.size ?: 0
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = readerFingerScrollEnabled,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = bottomPad + bottomObstruction,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(
                listOf("interlinear_chapter_tts"),
                key = { it },
            ) {
                if (translation == TranslationId.INTERLINEAR && interlinearTts != null) {
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
            itemsIndexed(
                verses,
                key = { _, v -> v.number },
            ) { verseIdx, verse ->
                val verseRef = VerseRef(translation, bookId, chapter, verse.number)
                val notesHere = remember(userNotes, verseRef) {
                    userNotes.filter { it.matchesVerseLocation(verseRef) }.sortedByDescending { it.updatedAt }
                }
                val firstNoteId = notesHere.firstOrNull()?.id
                val isTimemarkHighlight = timemarkVerseRange?.contains(verse.number) == true
                val isBibleChapterAudioHighlight =
                    timemarkVerseRange == null && audioHighlightVerse == verse.number
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            when {
                                isTimemarkHighlight -> {
                                    Modifier
                                        .background(
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                            RoundedCornerShape(8.dp),
                                        )
                                        .padding(horizontal = 4.dp, vertical = 3.dp)
                                }
                                isBibleChapterAudioHighlight -> {
                                    Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                                            RoundedCornerShape(8.dp),
                                        )
                                        .padding(horizontal = 4.dp, vertical = 3.dp)
                                }
                                else -> Modifier
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
                        InterlinearVerseContent(
                            words = verse.interlinearWords,
                            verseNumber = verse.number,
                            interlinearTts = interlinearTts,
                            bookId = bookId,
                            interlinearChapterWordOffset = interlinearChapterWordStarts.getOrElse(verseIdx) { 0 },
                            verseRef = verseRef,
                            onVerseNumberClick = {
                                verseActionsTarget = VerseActionTarget(
                                    ref = verseRef,
                                    verseText = verse.text,
                                    bookName = bookName,
                                )
                            },
                            onVerseNumberLongPress = {
                                val first = attachmentStore.listFor(verseRef)
                                    .firstOrNull { it.kind() == AttachmentKind.Image }
                                if (first != null) attachmentPreview = first
                            },
                            onAttachmentImageClick = { attachmentPreview = it },
                            onNavigateToVerse = onNavigateToVerse,
                            hasVerseNote = verse.number in verseNumbersWithNotes,
                            onVerseNoteIconClick = if (firstNoteId != null && onOpenExistingVerseNote != null) {
                                { onOpenExistingVerseNote(firstNoteId) }
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "${verse.number}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(top = 1.dp, end = 1.dp)
                                        .combinedClickable(
                                            onClick = {
                                                verseActionsTarget = VerseActionTarget(
                                                    ref = verseRef,
                                                    verseText = verse.text,
                                                    bookName = bookName,
                                                )
                                            },
                                            onLongClick = {
                                                firstImageAtt?.let { attachmentPreview = it }
                                            },
                                        ),
                                )
                                VerseAttachmentIndicator(
                                    verseRef = verseRef,
                                    onImageClick = { attachmentPreview = it },
                                )
                                if (verse.number in verseNumbersWithNotes) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                                        contentDescription = stringResource(R.string.verse_has_personal_note),
                                        modifier = Modifier
                                            .size(12.dp)
                                            .then(
                                                if (firstNoteId != null && onOpenExistingVerseNote != null) {
                                                    Modifier.clickable {
                                                        onOpenExistingVerseNote(firstNoteId)
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
                                highlights = highlightsForReader,
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
                            chapter = chapter,
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
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (timemarkProject != null) {
                TimemarkReaderBar(
                    project = timemarkProject,
                    bookId = bookId,
                    chapter = chapter,
                    bibleChapterAudioState = bibleChapterAudioState,
                    onActiveVerseRange = { timemarkVerseRange = it },
                    onHeightChanged = { timemarkBarInset = it },
                )
            }
            AudioPlayerBar(
                playbackState = audioPlaybackState,
                playbackSpeed = audioPlaybackSpeed,
                onTogglePause = onTogglePause,
                onCycleSpeed = onCycleSpeed,
                onStop = onStopAudio,
                getProgress = getAudioProgress,
                onSeek = onSeekAudio,
                modifier = Modifier,
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
            onNavigateToVerse = onNavigateToVerse,
            onOpenInterlinearHebrewSandboxWholeVerse = onOpenInterlinearHebrewSandboxWholeVerse,
            onDictionaryWord = { word ->
                val results = dictManager.searchAll(word)
                val builtIn = BibleDictionary.lookup(word)
                dictionarySeeAlso = builtIn?.seeAlso ?: emptyList()
                dictionaryLookup = LexiconDictionarySheetState(word, results, dictionarySeeAlso, null, null)
            },
            onPauseMainAudioForAttachment = onPauseMainAudioForAttachment,
            mediaLibraryImages = mediaLibraryImages,
            mediaLibraryVideos = mediaLibraryVideos,
            mediaLibraryAudios = mediaLibraryAudios,
            userNotes = userNotes,
            onCreateNoteForVerse = onVerseNote?.let { fn ->
                { t: VerseActionTarget -> fn(t.ref, t.bookName, t.verseText) }
            },
            onOpenExistingVerseNote = onOpenExistingVerseNote,
        )
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
                            chapter = chapter,
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
