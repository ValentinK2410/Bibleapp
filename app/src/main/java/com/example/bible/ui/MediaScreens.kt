package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.animateDpAsState
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.imageLoader
import com.example.bible.data.BibleUserAudio
import com.example.bible.data.BibleUserImage
import com.example.bible.data.BibleUserVideo
import com.example.bible.data.MediaCatalogPaths
import com.example.bible.data.MediaHomeSectionOrder
import com.example.bible.data.CommonsSearchResult
import com.example.bible.data.SafeImagePolicy
import java.io.File
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val BIBLE_IMG_ZOOM_MIN = 0.5f
private const val BIBLE_IMG_ZOOM_MAX = 6f
private const val BIBLE_IMG_PAN_LIMIT = 5000f

/** Поиск по названию и по меткам (несколько слов через пробел — все должны встретиться). */
private fun BibleUserImage.matchesMediaSearch(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val tokens = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return true
    val titleLc = title.lowercase()
    return tokens.all { token ->
        titleLc.contains(token) || tags.any { it.lowercase().contains(token) }
    }
}

private fun BibleUserVideo.matchesUnionSearch(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val tokens = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return true
    val titleLc = title.lowercase()
    return tokens.all { token ->
        titleLc.contains(token) || tags.any { it.lowercase().contains(token) }
    }
}

private fun BibleUserAudio.matchesUnionSearch(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val tokens = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return true
    val titleLc = title.lowercase()
    return tokens.all { token ->
        titleLc.contains(token) || tags.any { it.lowercase().contains(token) }
    }
}

private enum class MediaUnionTab {
    ALL,
    IMAGE,
    VIDEO,
    AUDIO,
}

/** Визуальный стиль карточки на экране «Каталог медиа» (градиенты и акценты). */
private enum class MediaHomeCardVisual {
    Pictures,
    Musician,
    Pesnopenie,
    Videos,
    Audios,
}

private data class MediaHomeCardBrushes(
    val cardBrush: Brush,
    val iconBrush: Brush,
    val iconTint: Color,
    val accentBarBrush: Brush,
    val bubbleColor: Color,
)

private fun buildMediaHomeBrushes(style: MediaHomeCardVisual, cs: ColorScheme): MediaHomeCardBrushes {
    return when (style) {
        MediaHomeCardVisual.Pictures -> MediaHomeCardBrushes(
            cardBrush = Brush.linearGradient(
                listOf(
                    cs.primary.copy(alpha = 0.12f),
                    cs.primaryContainer.copy(alpha = 0.48f),
                    cs.surface,
                ),
            ),
            iconBrush = Brush.linearGradient(
                listOf(cs.primary, cs.primary.copy(alpha = 0.88f)),
            ),
            iconTint = cs.onPrimary,
            accentBarBrush = Brush.horizontalGradient(listOf(cs.primary, cs.tertiary)),
            bubbleColor = cs.primary.copy(alpha = 0.14f),
        )
        MediaHomeCardVisual.Musician -> MediaHomeCardBrushes(
            cardBrush = Brush.linearGradient(
                listOf(
                    cs.tertiary.copy(alpha = 0.10f),
                    cs.tertiaryContainer.copy(alpha = 0.45f),
                    cs.surface,
                ),
            ),
            iconBrush = Brush.linearGradient(
                listOf(cs.tertiary, cs.secondary.copy(alpha = 0.92f)),
            ),
            iconTint = cs.onTertiary,
            accentBarBrush = Brush.horizontalGradient(listOf(cs.tertiary, cs.secondary)),
            bubbleColor = cs.tertiary.copy(alpha = 0.13f),
        )
        MediaHomeCardVisual.Pesnopenie -> MediaHomeCardBrushes(
            cardBrush = Brush.linearGradient(
                listOf(
                    cs.secondary.copy(alpha = 0.11f),
                    cs.secondaryContainer.copy(alpha = 0.42f),
                    cs.surface,
                ),
            ),
            iconBrush = Brush.linearGradient(
                listOf(cs.secondary, cs.primary.copy(alpha = 0.85f)),
            ),
            iconTint = cs.onSecondary,
            accentBarBrush = Brush.horizontalGradient(listOf(cs.secondary, cs.primary)),
            bubbleColor = cs.secondary.copy(alpha = 0.12f),
        )
        MediaHomeCardVisual.Videos -> MediaHomeCardBrushes(
            cardBrush = Brush.linearGradient(
                listOf(
                    cs.primary.copy(alpha = 0.07f),
                    cs.surfaceContainerHighest.copy(alpha = 0.88f),
                    cs.surface,
                ),
            ),
            iconBrush = Brush.linearGradient(
                listOf(cs.primary, cs.secondary.copy(alpha = 0.95f)),
            ),
            iconTint = cs.onPrimary,
            accentBarBrush = Brush.horizontalGradient(
                listOf(cs.primary, cs.secondary),
            ),
            bubbleColor = cs.primary.copy(alpha = 0.11f),
        )
        MediaHomeCardVisual.Audios -> MediaHomeCardBrushes(
            cardBrush = Brush.linearGradient(
                listOf(
                    cs.tertiary.copy(alpha = 0.07f),
                    cs.primaryContainer.copy(alpha = 0.38f),
                    cs.surface,
                ),
            ),
            iconBrush = Brush.linearGradient(
                listOf(cs.primary, cs.tertiary.copy(alpha = 0.9f)),
            ),
            iconTint = cs.onPrimary,
            accentBarBrush = Brush.horizontalGradient(listOf(cs.primary, cs.tertiary)),
            bubbleColor = cs.tertiary.copy(alpha = 0.11f),
        )
    }
}

@Composable
private fun rememberMediaHomeCardBrushes(style: MediaHomeCardVisual): MediaHomeCardBrushes {
    val cs = MaterialTheme.colorScheme
    return remember(style, cs) {
        buildMediaHomeBrushes(style, cs)
    }
}

@Composable
private fun MediaHomeSectionElevatedCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    style: MediaHomeCardVisual,
) {
    val brushes = rememberMediaHomeCardBrushes(style)
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp,
        ),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brushes.cardBrush),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(brushes.iconBrush),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = brushes.iconTint,
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                    }
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shadowElevation = 1.dp,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaHomeOrderDialog(
    draftOrder: MutableList<String>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        draftOrder.apply { add(to.index, removeAt(from.index)) }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Порядок разделов") },
        text = {
            Column {
                Text(
                    "Перетащите за ручку с «⋮⋮», чтобы изменить порядок карточек.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(draftOrder, key = { it }) { sectionId ->
                        ReorderableItem(reorderableLazyListState, key = sectionId) { isDragging ->
                            val elevation by animateDpAsState(
                                targetValue = if (isDragging) 6.dp else 0.dp,
                                label = "media_home_drag",
                            )
                            Surface(
                                tonalElevation = elevation,
                                shadowElevation = elevation,
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        MediaHomeSectionOrder.titleRu(sectionId),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 8.dp),
                                    )
                                    IconButton(
                                        modifier = Modifier.draggableHandle(),
                                        onClick = {},
                                    ) {
                                        Icon(
                                            Icons.Filled.DragHandle,
                                            contentDescription = "Переместить",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Готово")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaHomeScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onOpenPictures: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenAudios: () -> Unit,
    onOpenMusician: () -> Unit = {},
    onOpenPesnopenie: () -> Unit = {},
) {
    val sectionOrder by viewModel.mediaHomeSectionOrder.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    var showOrderDialog by remember { mutableStateOf(false) }
    val draftOrder = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Каталог медиа") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Меню")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Настройка") },
                                onClick = {
                                    menuExpanded = false
                                    draftOrder.clear()
                                    draftOrder.addAll(sectionOrder)
                                    showOrderDialog = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sectionOrder.forEach { id ->
                when (id) {
                    MediaHomeSectionOrder.PICTURES -> MediaHomeSectionElevatedCard(
                        title = "Картинки",
                        subtitle = "Каталог: картинки — галерея, камера или поиск в Google и Яндекс (офлайн)",
                        icon = Icons.Filled.PhotoLibrary,
                        onClick = onOpenPictures,
                        style = MediaHomeCardVisual.Pictures,
                    )
                    MediaHomeSectionOrder.MUSICIAN -> MediaHomeSectionElevatedCard(
                        title = "Для музыканта",
                        subtitle = "Тюнеры: гитара и скрипка",
                        icon = Icons.Filled.MusicNote,
                        onClick = onOpenMusician,
                        style = MediaHomeCardVisual.Musician,
                    )
                    MediaHomeSectionOrder.PESNOPENIE -> MediaHomeSectionElevatedCard(
                        title = "Песнопение",
                        subtitle = "Сборник песен: поиск, теги, добавление своих текстов",
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        onClick = onOpenPesnopenie,
                        style = MediaHomeCardVisual.Pesnopenie,
                    )
                    MediaHomeSectionOrder.VIDEOS -> MediaHomeSectionElevatedCard(
                        title = "Видео",
                        subtitle = "Каталог: видео — только ролики; галерея, камера или загрузка по ссылке",
                        icon = Icons.Filled.VideoLibrary,
                        onClick = onOpenVideos,
                        style = MediaHomeCardVisual.Videos,
                    )
                    MediaHomeSectionOrder.AUDIOS -> MediaHomeSectionElevatedCard(
                        title = "Аудио",
                        subtitle = "Каталог: аудио — только треки; файлы, запись или загрузка по ссылке",
                        icon = Icons.Filled.MusicNote,
                        onClick = onOpenAudios,
                        style = MediaHomeCardVisual.Audios,
                    )
                }
            }
        }
    }

    if (showOrderDialog) {
        MediaHomeOrderDialog(
            draftOrder = draftOrder,
            onDismiss = { showOrderDialog = false },
            onConfirm = {
                viewModel.setMediaHomeSectionOrder(draftOrder.toList())
                showOrderDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PictureLibraryScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val images by viewModel.bibleUserImages.collectAsStateWithLifecycle()
    val commonsLoading by viewModel.commonsSearchLoading.collectAsStateWithLifecycle()
    val commonsResults by viewModel.commonsSearchResults.collectAsStateWithLifecycle()
    val commonsSearchLastQuery by viewModel.commonsSearchLastQuery.collectAsStateWithLifecycle()

    val pictureItems = remember(images) {
        images.filter { MediaCatalogPaths.isLikelyImageFileName(it.fileName) }
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var showWebSheet by remember { mutableStateOf(false) }
    var showMetaDialog by remember { mutableStateOf(false) }
    var metaEditing by remember { mutableStateOf<BibleUserImage?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingSource by remember { mutableStateOf("gallery") }

    var draftTitle by remember { mutableStateOf("") }
    var draftTags by remember { mutableStateOf("") }

    var webQuery by remember { mutableStateOf("") }
    var previewCommons by remember { mutableStateOf<CommonsSearchResult?>(null) }
    var librarySearchQuery by remember { mutableStateOf("") }
    /** Полноэкранный просмотр с зумом. */
    var viewingImage by remember { mutableStateOf<BibleUserImage?>(null) }
    /** null — все; google / yandex / commons */
    var webFilter by remember { mutableStateOf<String?>(null) }
    val filteredWebResults = remember(commonsResults, webFilter) {
        if (webFilter == null) commonsResults
        else commonsResults.filter { it.origin == webFilter }
    }
    val webFilterShowsNothing = !commonsLoading &&
        commonsResults.isNotEmpty() &&
        filteredWebResults.isEmpty() &&
        webFilter != null

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            pendingUri = uri
            pendingSource = "gallery"
            draftTitle = ""
            draftTags = ""
            showMetaDialog = true
        }
    }

    val captureUriHolder = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { ok ->
        if (ok && captureUriHolder.value != null) {
            pendingUri = captureUriHolder.value
            pendingSource = "camera"
            draftTitle = ""
            draftTags = ""
            showMetaDialog = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val dir = MediaCatalogPaths.picturesDir(context)
            val f = File(dir, "capture_temp.jpg")
            try {
                if (f.exists()) f.delete()
                f.createNewFile()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    f,
                )
                captureUriHolder.value = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Камера", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Нужно разрешение камеры", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasCam) {
            val dir = MediaCatalogPaths.picturesDir(context)
            val f = File(dir, "capture_temp.jpg")
            try {
                if (f.exists()) f.delete()
                f.createNewFile()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    f,
                )
                captureUriHolder.value = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Камера", Toast.LENGTH_SHORT).show()
            }
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Медиа — Картинки")
                        Text(
                            "${pictureItems.size} в базе",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить")
            }
        },
    ) { padding ->
        if (pictureItems.isEmpty()) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Пока нет картинок.\nНажмите + чтобы добавить из галереи, камеры или интернета.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val filteredLibrary = remember(pictureItems, librarySearchQuery) {
                pictureItems.filter { it.matchesMediaSearch(librarySearchQuery) }
            }
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                OutlinedTextField(
                    value = librarySearchQuery,
                    onValueChange = { librarySearchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text("Поиск по названию или меткам") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    singleLine = true,
                )
                if (filteredLibrary.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (librarySearchQuery.isNotBlank()) {
                                "Ничего не найдено"
                            } else {
                                ""
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredLibrary, key = { it.id }) { img ->
                            val f = MediaCatalogPaths.pictureFile(context, img.fileName)
                            val openViewer = {
                                viewingImage = img
                            }
                            val openEditor = {
                                pendingUri = null
                                metaEditing = img
                                draftTitle = img.title
                                draftTags = img.tags.joinToString(", ")
                                showMetaDialog = true
                            }
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clickable(onClick = openViewer),
                                    ) {
                                        AsyncImage(
                                            model = f.takeIf { it.exists() } ?: img.sourceUrl,
                                            contentDescription = img.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp),
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.45f),
                                        ) {
                                            IconButton(
                                                onClick = openViewer,
                                                modifier = Modifier.size(40.dp),
                                            ) {
                                                Icon(
                                                    Icons.Filled.Visibility,
                                                    contentDescription = "Просмотр",
                                                    tint = Color.White,
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = openEditor),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            img.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            maxLines = 2,
                                        )
                                        IconButton(
                                            onClick = openEditor,
                                            modifier = Modifier.size(40.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.Edit,
                                                contentDescription = "Изменить",
                                            )
                                        }
                                    }
                                }
                            }
                        }
            }
            }
        }
    }
    }

    viewingImage?.let { img ->
        val imgFile = MediaCatalogPaths.pictureFile(context, img.fileName)
        val model = imgFile.takeIf { it.exists() } ?: img.sourceUrl
        if (model != null) {
            BibleImageViewerDialog(
                image = img,
                imageModel = model,
                onDismiss = { viewingImage = null },
                onEdit = {
                    viewingImage = null
                    pendingUri = null
                    metaEditing = img
                    draftTitle = img.title
                    draftTags = img.tags.joinToString(", ")
                    showMetaDialog = true
                },
                onShare = {
                    if (imgFile.exists()) {
                        shareMediaFile(context, imgFile, "image/*")
                    } else {
                        Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        } else {
            LaunchedEffect(img.id) {
                Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                viewingImage = null
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(16.dp).padding(bottom = 24.dp)) {
                Text("Добавить картинку", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAddSheet = false
                            pickLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Text("Из галереи", modifier = Modifier.padding(start = 16.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAddSheet = false
                            launchCamera()
                        },
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Text("Снять камерой", modifier = Modifier.padding(start = 16.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAddSheet = false
                            webQuery = ""
                            webFilter = null
                            viewModel.clearCommonsSearch()
                            showWebSheet = true
                        },
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.TravelExplore, contentDescription = null)
                        Text("Поиск в интернете", modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }

    if (showWebSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showWebSheet = false
                webFilter = null
                viewModel.clearCommonsSearch()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            modifier = Modifier.heightIn(max = 640.dp),
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                Text("Поиск картинок в интернете", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Сводная выдача: Wikimedia Commons, Openverse, при возможности — Google, Яндекс, Bing (безопасный режим). Откровенный контент не ищется и не показывается. Выберите картинку — файл сохранится офлайн.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = webQuery,
                        onValueChange = { webQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Например: Вифлеем, Noah ark") },
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            webFilter = null
                            viewModel.searchCommonsImages(webQuery)
                        },
                        enabled = webQuery.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Искать")
                    }
                }
                Spacer(Modifier.height(8.dp))
                val selectWebFilter: (String) -> Unit = { key ->
                    val next = if (webFilter == key) null else key
                    webFilter = next
                    if (next != null && webQuery.isNotBlank() && commonsResults.isEmpty() && !commonsLoading) {
                        viewModel.searchCommonsImages(webQuery)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = webFilter == null,
                        onClick = { webFilter = null },
                        label = { Text("Все", fontSize = 12.sp) },
                    )
                    FilterChip(
                        selected = webFilter == "openverse",
                        onClick = { selectWebFilter("openverse") },
                        label = { Text("Openverse", fontSize = 12.sp) },
                    )
                    FilterChip(
                        selected = webFilter == "commons",
                        onClick = { selectWebFilter("commons") },
                        label = { Text("Commons", fontSize = 12.sp) },
                    )
                    FilterChip(
                        selected = webFilter == "google",
                        onClick = { selectWebFilter("google") },
                        label = { Text("Google", fontSize = 12.sp) },
                    )
                    FilterChip(
                        selected = webFilter == "yandex",
                        onClick = { selectWebFilter("yandex") },
                        label = { Text("Яндекс", fontSize = 12.sp) },
                    )
                    FilterChip(
                        selected = webFilter == "bing",
                        onClick = { selectWebFilter("bing") },
                        label = { Text("Bing", fontSize = 12.sp) },
                    )
                }
                if (webFilterShowsNothing) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "С этим фильтром ничего не найдено. Попробуйте «Все», Openverse или Commons.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (commonsLoading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filteredWebResults.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.height(300.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(filteredWebResults, key = { "${it.origin}_${it.fullUrl}" }) { hit ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clickable {
                                        previewCommons = hit
                                        draftTitle = webQuery.trim().ifBlank { hit.pageTitle }
                                        draftTags = ""
                                    },
                            ) {
                                AsyncImage(
                                    model = hit.thumbUrl,
                                    contentDescription = hit.pageTitle,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    tonalElevation = 1.dp,
                                ) {
                                    Text(
                                        when (hit.origin) {
                                            "openverse" -> "O"
                                            "google" -> "G"
                                            "yandex" -> "Я"
                                            "commons" -> "W"
                                            "bing" -> "B"
                                            else -> "?"
                                        },
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                } else if (
                    !commonsLoading &&
                    commonsResults.isEmpty() &&
                    webQuery.isNotBlank() &&
                    webQuery.trim() == commonsSearchLastQuery.trim()
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (SafeImagePolicy.isBlockedQuery(webQuery.trim())) {
                            "Такой запрос не выполняется: в приложении отключён поиск изображений сексуально откровенного и порнографического характера."
                        } else {
                            "Ничего не найдено. Проверьте интернет или попробуйте другие слова."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    previewCommons?.let { hit ->
        AlertDialog(
            onDismissRequest = { previewCommons = null },
            title = { Text("Добавить в базу") },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    AsyncImage(
                        model = hit.thumbUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (hit.origin) {
                            "openverse" -> "Источник: Openverse (CC и др.; проверьте лицензию на странице оригинала)"
                            "google" -> "Источник: Google (файл будет сохранён офлайн)"
                            "yandex" -> "Источник: Яндекс (файл будет сохранён офлайн)"
                            "bing" -> "Источник: Bing (файл будет сохранён офлайн)"
                            "commons" -> "Источник: Wikimedia Commons"
                            else -> hit.pageTitle
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftTitle,
                        onValueChange = { draftTitle = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draftTags,
                        onValueChange = { draftTags = it },
                        label = { Text("Метки через запятую") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val h = previewCommons ?: return@TextButton
                        viewModel.importBibleImageFromRemoteUrl(
                            fullUrl = h.fullUrl,
                            title = draftTitle.ifBlank { h.pageTitle.substringAfter(':').trim() },
                            tags = draftTags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                            sourceUrl = h.fullUrl,
                            imageSource = when (h.origin) {
                                "openverse" -> "web_openverse"
                                "google" -> "web_google"
                                "yandex" -> "web_yandex"
                                "bing" -> "web_bing"
                                else -> "commons"
                            },
                        ) { err ->
                            if (err != null) {
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                                previewCommons = null
                                showWebSheet = false
                                viewModel.clearCommonsSearch()
                            }
                        }
                    },
                ) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewCommons = null }) {
                    Text("Отмена")
                }
            },
        )
    }

    if (showMetaDialog) {
        val isNew = metaEditing == null
        AlertDialog(
            onDismissRequest = {
                showMetaDialog = false
                pendingUri = null
                metaEditing = null
            },
            title = { Text(if (isNew) "Новая картинка" else "Редактирование") },
            text = {
                Column {
                    if (isNew && pendingUri != null) {
                        AsyncImage(
                            model = pendingUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = draftTitle,
                        onValueChange = { draftTitle = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draftTags,
                        onValueChange = { draftTags = it },
                        label = { Text("Метки через запятую") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!isNew) {
                        Text(
                            "ID: ${metaEditing?.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        TextButton(
                            onClick = {
                                val ed = metaEditing ?: return@TextButton
                                viewModel.deleteBibleImage(ed)
                                Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                                showMetaDialog = false
                                metaEditing = null
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Удалить из базы", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isNew && pendingUri != null) {
                            viewModel.importBibleImageFromUri(
                                uri = pendingUri!!,
                                title = draftTitle,
                                tags = draftTags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                source = pendingSource,
                            ) { err ->
                                if (err != null) Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                else Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                                showMetaDialog = false
                                pendingUri = null
                            }
                        } else {
                            val ed = metaEditing
                            if (ed != null) {
                                viewModel.updateBibleImage(
                                    ed.copy(
                                        title = draftTitle.trim().ifBlank { ed.title },
                                        tags = draftTags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                    ),
                                )
                                Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                            }
                            showMetaDialog = false
                            metaEditing = null
                        }
                    },
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMetaDialog = false
                        pendingUri = null
                        metaEditing = null
                    },
                ) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun ZoomableBibleImage(
    imageModel: Any,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    resetKey: Int,
) {
    val imageLoader = LocalContext.current.imageLoader
    var scale by remember(resetKey) { mutableFloatStateOf(1f) }
    var offset by remember(resetKey) { mutableStateOf(Offset.Zero) }
    fun clamp(o: Offset) = Offset(
        o.x.coerceIn(-BIBLE_IMG_PAN_LIMIT, BIBLE_IMG_PAN_LIMIT),
        o.y.coerceIn(-BIBLE_IMG_PAN_LIMIT, BIBLE_IMG_PAN_LIMIT),
    )
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(BIBLE_IMG_ZOOM_MIN, BIBLE_IMG_ZOOM_MAX)
        offset = clamp(offset + panChange)
    }
    Box(modifier = modifier) {
        AsyncImage(
            model = imageModel,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(state = transformState, lockRotationOnZoomPan = true),
        )
    }
}

@Composable
private fun BibleImageViewerDialog(
    image: BibleUserImage,
    imageModel: Any,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
) {
    var resetZoom by remember(image.id) { mutableIntStateOf(0) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            BackHandler(onBack = onDismiss)
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                    }
                    Text(
                        image.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = "Поделиться", tint = Color.White)
                    }
                    TextButton(onClick = { resetZoom++ }) {
                        Text("Масштаб", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = {
                        onDismiss()
                        onEdit()
                    }) {
                        Text("Изменить", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    ZoomableBibleImage(
                        imageModel = imageModel,
                        contentDescription = image.title,
                        resetKey = resetZoom,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (image.tags.isNotEmpty()) {
                    Text(
                        image.tags.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Выбор файла из раздела «Медиа»: картинки, видео или аудио.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLibraryUnionPickerSheet(
    images: List<BibleUserImage>,
    videos: List<BibleUserVideo>,
    audios: List<BibleUserAudio>,
    onDismiss: () -> Unit,
    onSelect: (MediaLibraryPick) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(MediaUnionTab.ALL) }
    val imgF = remember(images, searchQuery) {
        images.filter { MediaCatalogPaths.isLikelyImageFileName(it.fileName) }
            .filter { it.matchesMediaSearch(searchQuery) }
    }
    val vidF = remember(videos, searchQuery) {
        videos.filter { MediaCatalogPaths.isLikelyVideoFileName(it.fileName) }
            .filter { it.matchesUnionSearch(searchQuery) }
    }
    val audF = remember(audios, searchQuery) {
        audios.filter { MediaCatalogPaths.isLikelyAudioFileName(it.fileName) }
            .filter { it.matchesUnionSearch(searchQuery) }
    }
    val tabScroll = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        modifier = Modifier.heightIn(max = 580.dp),
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text("Файл из «Медиа»", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск по названию или меткам") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(tabScroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = tab == MediaUnionTab.ALL,
                    onClick = { tab = MediaUnionTab.ALL },
                    label = { Text("Все") },
                )
                FilterChip(
                    selected = tab == MediaUnionTab.IMAGE,
                    onClick = { tab = MediaUnionTab.IMAGE },
                    label = { Text("Картинки") },
                )
                FilterChip(
                    selected = tab == MediaUnionTab.VIDEO,
                    onClick = { tab = MediaUnionTab.VIDEO },
                    label = { Text("Видео") },
                )
                FilterChip(
                    selected = tab == MediaUnionTab.AUDIO,
                    onClick = { tab = MediaUnionTab.AUDIO },
                    label = { Text("Аудио") },
                )
            }
            Spacer(Modifier.height(8.dp))
            val emptyAll = imgF.isEmpty() && vidF.isEmpty() && audF.isEmpty()
            val hasTypedMedia =
                images.any { MediaCatalogPaths.isLikelyImageFileName(it.fileName) } ||
                    videos.any { MediaCatalogPaths.isLikelyVideoFileName(it.fileName) } ||
                    audios.any { MediaCatalogPaths.isLikelyAudioFileName(it.fileName) }
            if (!hasTypedMedia) {
                Text(
                    "В каталоге медиа пока нет файлов. Добавьте: Картинки, Видео или Аудио.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (emptyAll) {
                Text(
                    "Ничего не найдено",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    fun showImages() = tab == MediaUnionTab.ALL || tab == MediaUnionTab.IMAGE
                    fun showVideos() = tab == MediaUnionTab.ALL || tab == MediaUnionTab.VIDEO
                    fun showAudios() = tab == MediaUnionTab.ALL || tab == MediaUnionTab.AUDIO
                    if (showImages() && imgF.isNotEmpty()) {
                        if (tab == MediaUnionTab.ALL) {
                            item {
                                Text(
                                    "Картинки",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                        items(imgF, key = { it.id }) { img ->
                            MediaUnionRow(
                                title = img.title.ifBlank { img.fileName },
                                subtitle = img.tags.take(3).joinToString(", ").ifBlank { null },
                                leading = {
                                    Icon(Icons.Filled.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    onSelect(MediaLibraryPick.Image(img))
                                    onDismiss()
                                },
                            )
                        }
                    }
                    if (showVideos() && vidF.isNotEmpty()) {
                        if (tab == MediaUnionTab.ALL) {
                            item {
                                Text(
                                    "Видео",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                        items(vidF, key = { it.id }) { vid ->
                            MediaUnionRow(
                                title = vid.title.ifBlank { vid.fileName },
                                subtitle = vid.tags.take(3).joinToString(", ").ifBlank { null },
                                leading = {
                                    Icon(Icons.Filled.Videocam, null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    onSelect(MediaLibraryPick.Video(vid))
                                    onDismiss()
                                },
                            )
                        }
                    }
                    if (showAudios() && audF.isNotEmpty()) {
                        if (tab == MediaUnionTab.ALL) {
                            item {
                                Text(
                                    "Аудио",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                        items(audF, key = { it.id }) { a ->
                            MediaUnionRow(
                                title = a.title.ifBlank { a.fileName },
                                subtitle = a.tags.take(3).joinToString(", ").ifBlank { null },
                                leading = {
                                    Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    onSelect(MediaLibraryPick.Audio(a))
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaUnionRow(
    title: String,
    subtitle: String?,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Выбор источника вложения: системный файл или ссылка на файл из раздела «Медиа».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachFileSourceSheet(
    onDismiss: () -> Unit,
    onPickDeviceFile: () -> Unit,
    onPickFromMediaLibrary: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text("Прикрепить", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onPickDeviceFile()
                        onDismiss()
                    },
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null)
                    Text("Файл с устройства", modifier = Modifier.padding(start = 16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onPickFromMediaLibrary()
                        onDismiss()
                    },
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text("Из раздела «Медиа»", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Картинка, видео или аудио из вашей базы",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Сетка картинок из [BibleUserImage] для выбора при вложении.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLibraryImagePickerSheet(
    images: List<BibleUserImage>,
    onDismiss: () -> Unit,
    onSelect: (BibleUserImage) -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(images, searchQuery) {
        images
            .filter { MediaCatalogPaths.isLikelyImageFileName(it.fileName) }
            .filter { it.matchesMediaSearch(searchQuery) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        modifier = Modifier.heightIn(max = 580.dp),
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text("Картинки из «Медиа»", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (images.none { MediaCatalogPaths.isLikelyImageFileName(it.fileName) }) {
                Text(
                    "В базе нет картинок. Добавьте их в меню: Каталог медиа → Картинки.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск по названию или меткам") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                when {
                    filtered.isEmpty() -> {
                        Text(
                            "Ничего не найдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                        ) {
                            items(filtered, key = { it.id }) { img ->
                                val f = MediaCatalogPaths.pictureFile(context, img.fileName)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelect(img)
                                            onDismiss()
                                        },
                                ) {
                                    Column {
                                        AsyncImage(
                                            model = f.takeIf { it.exists() },
                                            contentDescription = img.title,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Text(
                                            img.title.ifBlank { img.fileName },
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(6.dp),
                                            maxLines = 2,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun shareMediaFile(context: android.content.Context, file: java.io.File, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться"))
    } catch (e: Exception) {
        Toast.makeText(context, e.message ?: "Не удалось поделиться", Toast.LENGTH_SHORT).show()
    }
}
