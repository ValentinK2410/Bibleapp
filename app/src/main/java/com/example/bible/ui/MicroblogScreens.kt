package com.example.bible.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.bible.data.MICROBLOG_IMAGE_AT_END
import com.example.bible.data.MediaCatalogPaths
import com.example.bible.data.MicroblogImage
import com.example.bible.data.MicroblogImageOps
import com.example.bible.data.MicroblogImageWrap
import com.example.bible.data.MicroblogPost
import com.example.bible.data.MicroblogSpan
import com.example.bible.data.adjustMicroblogImageAnchors
import com.example.bible.data.microblogCanMoveInsertAt
import com.example.bible.data.microblogDescribeInsertAt
import com.example.bible.data.microblogMoveInsertAt
import com.example.bible.data.microblogParagraphSlots
import com.example.bible.data.microblogResolvedInsertAt
import com.example.bible.data.microblogSlotIndex
import com.example.bible.data.microblogSnapInsertAt
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val UrlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>\[\]()]+)""")
private const val LinkTag = "url"
private val FontSizes = listOf(12, 14, 16, 18, 20, 24, 28, 32)
private val TextColors = listOf(
    Color.White,
    Color(0xFFFF5252), Color(0xFFFF9800), Color(0xFFFFEB3B),
    Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF9C27B0),
    Color(0xFF00BCD4), Color(0xFFE91E63), Color(0xFF607D8B),
    Color(0xFF000000),
)
private val BgColors = listOf(
    Color.Transparent,
    Color(0x40FF5252), Color(0x40FF9800), Color(0x40FFEB3B),
    Color(0x404CAF50), Color(0x402196F3), Color(0x409C27B0),
    Color(0x40E91E63), Color(0x80FFEB3B), Color(0x802196F3),
)

private enum class ColorPickMode { TEXT, BACKGROUND }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicroblogFeedScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onNewPost: () -> Unit,
    onOpenPost: (String) -> Unit,
    onEditPost: (String) -> Unit = onOpenPost,
) {
    val posts by viewModel.microblogPosts.collectAsStateWithLifecycle()
    val blogTitle by viewModel.microblogTitle.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val filtered = remember(posts, query) {
        val q = query.trim()
        if (q.isEmpty()) posts
        else posts.filter {
            it.title.contains(q, ignoreCase = true) || it.body.contains(q, ignoreCase = true)
        }
    }
    LaunchedEffect(Unit) { viewModel.refreshMicroblogPosts() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(blogTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        renameDraft = blogTitle
                        renameOpen = true
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Название блога")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewPost) {
                Icon(Icons.Filled.Add, contentDescription = "Новый пост")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Поиск по постам") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Очистить")
                        }
                    }
                },
            )
            when {
                posts.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Постов пока нет. Нажмите +, чтобы написать запись со стилями, ссылками и картинками.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                filtered.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Ничего не найдено по запросу «$query».",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(filtered, key = { it.id }) { post ->
                            MicroblogPostCard(
                                post = post,
                                dateLabel = dateFormat.format(Date(post.updatedAtMs)),
                                onOpen = { onOpenPost(post.id) },
                                onEdit = { onEditPost(post.id) },
                                onDelete = { deleteId = post.id },
                            )
                        }
                    }
                }
            }
        }
    }
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Удалить пост?") },
            text = { Text("Запись будет удалена с устройства вместе с прикреплёнными картинками.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMicroblogPost(id)
                    deleteId = null
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text("Отмена") }
            },
        )
    }
    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Название блога") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    label = { Text("Как назвать блог") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setMicroblogTitle(renameDraft)
                    renameOpen = false
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("Отмена") }
            },
        )
    }
}

private enum class MicroblogCardKind { AI, PHOTO, TEXT }

private fun microblogCardKind(post: MicroblogPost): MicroblogCardKind = when {
    post.body.startsWith("Беседа с ИИ") -> MicroblogCardKind.AI
    post.imageFileNames.isNotEmpty() -> MicroblogCardKind.PHOTO
    else -> MicroblogCardKind.TEXT
}

/** Заголовок карточки: своё название, иначе первая непустая строка текста. */
private fun microblogHeadline(post: MicroblogPost): String {
    val own = post.title.trim()
    if (own.isNotEmpty()) return own
    return post.body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "Без названия"
}

/** Начало текста для превью: без стилей, без строк, дублирующих заголовок. */
private fun microblogSnippet(post: MicroblogPost, maxChars: Int = 200): String {
    val skip = setOf(post.title.trim(), "Беседа с ИИ")
    val text = post.body.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it !in skip }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (text.length <= maxChars) return text
    return text.take(maxChars).trimEnd().trimEnd(',', '.', ';', ':', '—', '-') + "…"
}

@Composable
private fun MicroblogPostCard(
    post: MicroblogPost,
    dateLabel: String,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val kind = microblogCardKind(post)
    val accent = when (kind) {
        MicroblogCardKind.AI -> listOf(cs.secondary, cs.primary)
        MicroblogCardKind.PHOTO -> listOf(cs.primary, cs.tertiary)
        MicroblogCardKind.TEXT -> listOf(cs.tertiary, cs.secondary)
    }
    val chipLabel: String
    val chipColor: Color
    when (kind) {
        MicroblogCardKind.AI -> {
            chipLabel = "Беседа ИИ"
            chipColor = cs.secondaryContainer
        }
        MicroblogCardKind.PHOTO -> {
            chipLabel = "С фото"
            chipColor = cs.primaryContainer
        }
        MicroblogCardKind.TEXT -> {
            chipLabel = "Пост"
            chipColor = cs.tertiaryContainer
        }
    }
    val headline = remember(post.title, post.body) { microblogHeadline(post) }
    val snippet = remember(post.title, post.body) { microblogSnippet(post) }

    ElevatedCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cs.surface),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(Brush.horizontalGradient(accent)),
            )
            if (post.images.isNotEmpty()) {
                MicroblogPreviewSlider(images = post.images, accent = accent)
            }
            Column(
                Modifier.padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chipLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSecondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(chipColor)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        dateLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
                Text(
                    headline,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (snippet.isNotEmpty()) {
                    Text(
                        snippet,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                        color = cs.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Читать полностью",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Редактировать", tint = cs.primary)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = cs.error)
                    }
                }
            }
        }
    }
}

/** Превью картинок поста: листается само, но пользователь может пролистать вручную. */
@Composable
private fun MicroblogPreviewSlider(
    images: List<MicroblogImage>,
    accent: List<Color>,
) {
    val context = LocalContext.current
    val state = rememberPagerState(pageCount = { images.size })
    if (images.size > 1) {
        LaunchedEffect(state, images.size) {
            while (true) {
                delay(3200)
                if (!state.isScrollInProgress) {
                    state.animateScrollToPage((state.currentPage + 1) % images.size)
                }
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(
                Brush.linearGradient(accent.map { it.copy(alpha = 0.16f) }),
            ),
    ) {
        HorizontalPager(state = state, modifier = Modifier.fillMaxSize()) { page ->
            AsyncImage(
                model = File(MediaCatalogPaths.microblogDir(context), images[page].fileName),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        if (images.size > 1) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.38f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                if (images.size <= 6) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(images.size) { i ->
                            val active = i == state.currentPage
                            Box(
                                Modifier
                                    .size(if (active) 7.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (active) Color.White else Color.White.copy(alpha = 0.5f),
                                    ),
                            )
                        }
                    }
                } else {
                    Text(
                        "${state.currentPage + 1}/${images.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun MicroblogFittedImage(
    fileName: String,
    displayScale: Float,
    maxHeight: Dp,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scale = displayScale.coerceIn(0.35f, 1f)
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(MediaCatalogPaths.microblogDir(context), fileName),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth(scale)
                .heightIn(max = maxHeight)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (selected) {
                        Modifier.border(3.dp, cs.primary, RoundedCornerShape(16.dp))
                    } else {
                        Modifier
                    },
                ),
            contentScale = ContentScale.Fit,
        )
    }
}

private sealed class MicroblogBodyBlock {
    data class TextRun(val start: Int, val end: Int) : MicroblogBodyBlock()
    data class FullImage(val index: Int) : MicroblogBodyBlock()
    data class WrapImage(val index: Int, val textStart: Int, val textEnd: Int) : MicroblogBodyBlock()
}

private fun buildMicroblogBodyBlocks(
    text: String,
    images: List<MicroblogImage>,
): List<MicroblogBodyBlock> {
    if (images.isEmpty()) {
        return if (text.isEmpty()) emptyList() else listOf(MicroblogBodyBlock.TextRun(0, text.length))
    }
    data class Placed(val index: Int, val image: MicroblogImage, val at: Int)
    val placed = images.mapIndexed { i, img ->
        Placed(i, img, microblogResolvedInsertAt(img.insertAt, text.length))
    }.sortedWith(compareBy({ it.at }, { it.index }))

    val blocks = mutableListOf<MicroblogBodyBlock>()
    var cursor = 0
    for ((i, item) in placed.withIndex()) {
        if (cursor < item.at) {
            blocks.add(MicroblogBodyBlock.TextRun(cursor, item.at))
            cursor = item.at
        }
        val nextAt = placed.getOrNull(i + 1)?.at ?: text.length
        if (item.image.wrap != MicroblogImageWrap.FULL && cursor < nextAt) {
            blocks.add(MicroblogBodyBlock.WrapImage(item.index, cursor, nextAt))
            cursor = nextAt
        } else {
            blocks.add(MicroblogBodyBlock.FullImage(item.index))
        }
    }
    if (cursor < text.length) {
        blocks.add(MicroblogBodyBlock.TextRun(cursor, text.length))
    }
    return blocks
}

private fun trimNewlinesRange(text: String, start: Int, end: Int): Pair<Int, Int>? {
    var s = start.coerceIn(0, text.length)
    var e = end.coerceIn(0, text.length)
    while (s < e && text[s] == '\n') s++
    while (e > s && text[e - 1] == '\n') e--
    return if (e > s) s to e else null
}

/** Текст поста вместе с картинками: фото стоят на якорях между абзацами, одно из них может обтекаться. */
@Composable
private fun MicroblogArticleBody(
    text: String,
    spans: List<MicroblogSpan>,
    images: List<MicroblogImage>,
    style: TextStyle,
    selectedImageIndex: Int? = null,
    onImageClick: ((Int) -> Unit)? = null,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, spans, linkColor) {
        buildMicroblogAnnotated(text, spans, linkColor)
    }
    val blocks = remember(text, images) { buildMicroblogBodyBlocks(text, images) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MicroblogBodyBlock.TextRun -> {
                    val range = trimNewlinesRange(text, block.start, block.end) ?: return@forEach
                    MicroblogAnnotatedText(
                        annotated = annotated.subSequence(range.first, range.second),
                        style = style,
                    )
                }
                is MicroblogBodyBlock.FullImage -> {
                    val image = images[block.index]
                    MicroblogFittedImage(
                        fileName = image.fileName,
                        displayScale = if (image.wrap == MicroblogImageWrap.FULL) image.displayScale else 1f,
                        maxHeight = 420.dp,
                        selected = selectedImageIndex == block.index,
                        onClick = onImageClick?.let { { it(block.index) } },
                    )
                }
                is MicroblogBodyBlock.WrapImage -> {
                    val range = trimNewlinesRange(text, block.textStart, block.textEnd)
                    val slice = if (range != null) {
                        annotated.subSequence(range.first, range.second)
                    } else {
                        AnnotatedString("")
                    }
                    MicroblogWrappedText(
                        annotated = slice,
                        style = style,
                        image = images[block.index],
                        selected = selectedImageIndex == block.index,
                        onClick = onImageClick?.let { { it(block.index) } },
                    )
                }
            }
        }
    }
}

/**
 * Текст реально обтекает картинку: первые строки измеряются по узкой колонке рядом с фото,
 * остальной текст идёт под ним на всю ширину.
 */
@Composable
private fun MicroblogWrappedText(
    annotated: AnnotatedString,
    style: TextStyle,
    image: MicroblogImage,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val file = remember(image.fileName) {
        File(MediaCatalogPaths.microblogDir(context), image.fileName)
    }
    val srcSize = remember(image.fileName) { MicroblogImageOps.readSize(file) }
    val onLeft = image.wrap == MicroblogImageWrap.LEFT

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val totalPx = with(density) { maxWidth.toPx() }
        val gap = 12.dp
        val gapPx = with(density) { gap.toPx() }
        val imageWidthPx = totalPx * image.displayScale.coerceIn(0.28f, 0.6f)
        val ratio = srcSize?.let { (w, h) -> h.toFloat() / w.toFloat() } ?: 1f
        val imageHeightPx = (imageWidthPx * ratio).coerceIn(imageWidthPx * 0.4f, totalPx * 1.2f)
        val narrowPx = (totalPx - imageWidthPx - gapPx).coerceAtLeast(totalPx * 0.28f)

        val splitIndex = remember(annotated, style, narrowPx, imageHeightPx) {
            if (annotated.isEmpty()) {
                0
            } else {
                val layout = measurer.measure(
                    text = annotated,
                    style = style,
                    constraints = Constraints(maxWidth = narrowPx.roundToInt().coerceAtLeast(1)),
                )
                var lastFitting = -1
                for (line in 0 until layout.lineCount) {
                    if (layout.getLineBottom(line) <= imageHeightPx) lastFitting = line else break
                }
                if (lastFitting < 0) 0 else layout.getLineEnd(lastFitting, visibleEnd = true)
            }
        }
        val head = if (splitIndex > 0) annotated.subSequence(0, splitIndex) else AnnotatedString("")
        val tail = if (splitIndex < annotated.length) {
            val rest = annotated.subSequence(splitIndex, annotated.length)
            val firstVisible = rest.text.indexOfFirst { !it.isWhitespace() }
            when {
                firstVisible < 0 -> AnnotatedString("")
                firstVisible > 0 -> rest.subSequence(firstVisible, rest.length)
                else -> rest
            }
        } else {
            AnnotatedString("")
        }

        val imageWidth = with(density) { imageWidthPx.toDp() }
        val imageHeight = with(density) { imageHeightPx.toDp() }
        val narrowWidth = with(density) { narrowPx.toDp() }

        Column {
            Row(verticalAlignment = Alignment.Top) {
                val photo: @Composable () -> Unit = {
                    Box(
                        Modifier
                            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
                    ) {
                        AsyncImage(
                            model = file,
                            contentDescription = null,
                            modifier = Modifier
                                .size(width = imageWidth, height = imageHeight)
                                .clip(RoundedCornerShape(14.dp))
                                .then(
                                    if (selected) {
                                        Modifier.border(3.dp, cs.primary, RoundedCornerShape(14.dp))
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                val column: @Composable () -> Unit = {
                    Box(Modifier.width(narrowWidth)) {
                        if (head.isNotEmpty()) {
                            MicroblogAnnotatedText(annotated = head, style = style)
                        }
                    }
                }
                if (onLeft) {
                    photo()
                    Spacer(Modifier.width(gap))
                    column()
                } else {
                    column()
                    Spacer(Modifier.width(gap))
                    photo()
                }
            }
            if (tail.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                MicroblogAnnotatedText(annotated = tail, style = style)
            }
        }
    }
}

@Composable
private fun MicroblogImageEditorPanel(
    image: MicroblogImage,
    index: Int,
    total: Int,
    bodyText: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onCrop: () -> Unit,
    onScale: (Float) -> Unit,
    onWrap: (MicroblogImageWrap) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPlaceAtCursor: () -> Unit,
    onPlaceAt: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val hasText = bodyText.isNotBlank()
    val placeLabel = microblogDescribeInsertAt(bodyText, image.insertAt)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer.copy(alpha = 0.35f)),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Фото ${index + 1} из $total",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Выше по тексту")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Ниже по тексту")
                }
                IconButton(onClick = onCrop) {
                    Icon(Icons.Filled.Crop, contentDescription = "Обрезать")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "Удалить", tint = cs.error)
                }
            }
            Text(
                when {
                    !hasText -> "Добавьте текст ниже — тогда фото можно поставить до или после абзаца и включить обтекание."
                    image.wrap == MicroblogImageWrap.LEFT -> "Текст обтекает фото справа. Сейчас: $placeLabel."
                    image.wrap == MicroblogImageWrap.RIGHT -> "Текст обтекает фото слева. Сейчас: $placeLabel."
                    else -> "Фото блоком в тексте. Сейчас: $placeLabel."
                },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Text("Место в тексте", style = MaterialTheme.typography.labelMedium)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val slots = microblogParagraphSlots(bodyText)
                val currentSlot = microblogSlotIndex(bodyText, image.insertAt)
                MicroblogOptionChip(
                    label = "К курсору",
                    selected = false,
                    enabled = hasText,
                    onClick = onPlaceAtCursor,
                )
                if (hasText) {
                    slots.forEachIndexed { i, slot ->
                        val label = when {
                            i == 0 -> "До 1-го"
                            i == slots.lastIndex -> "После последнего"
                            else -> "После $i-го"
                        }
                        MicroblogOptionChip(
                            label = label,
                            selected = currentSlot == i,
                            onClick = {
                                onPlaceAt(
                                    if (slot >= bodyText.length) MICROBLOG_IMAGE_AT_END else slot,
                                )
                            },
                        )
                    }
                }
            }
            Text("Расположение", style = MaterialTheme.typography.labelMedium)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    MicroblogImageWrap.FULL to "Во всю ширину",
                    MicroblogImageWrap.LEFT to "Слева",
                    MicroblogImageWrap.RIGHT to "Справа",
                ).forEach { (mode, label) ->
                    MicroblogOptionChip(
                        label = label,
                        selected = image.wrap == mode,
                        enabled = hasText || mode == MicroblogImageWrap.FULL,
                        onClick = { onWrap(mode) },
                    )
                }
            }
            Text("Ширина", style = MaterialTheme.typography.labelMedium)
            val widthOptions = if (image.wrap == MicroblogImageWrap.FULL) {
                listOf(0.55f to "Узко", 0.78f to "Средне", 1f to "Широко")
            } else {
                listOf(0.32f to "Узко", 0.42f to "Средне", 0.55f to "Широко")
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                widthOptions.forEach { (value, label) ->
                    MicroblogOptionChip(
                        label = label,
                        selected = kotlin.math.abs(image.displayScale - value) < 0.04f,
                        onClick = { onScale(value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MicroblogOptionChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        color = when {
            !enabled -> cs.onSurface.copy(alpha = 0.38f)
            selected -> cs.onPrimary
            else -> cs.onSurface
        },
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                when {
                    !enabled -> cs.surfaceVariant.copy(alpha = 0.5f)
                    selected -> cs.primary
                    else -> cs.surface
                },
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicroblogEditorScreen(
    viewModel: BibleViewModel,
    postId: String?,
    startInEdit: Boolean = postId == null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var createdAt by remember { mutableStateOf(System.currentTimeMillis()) }
    val id = remember { postId ?: java.util.UUID.randomUUID().toString() }
    var postTitle by remember { mutableStateOf("") }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var spans by remember { mutableStateOf(listOf<MicroblogSpan>()) }
    val images = remember { mutableStateListOf<MicroblogImage>() }
    var loaded by remember { mutableStateOf(postId == null) }
    var isViewMode by remember { mutableStateOf(postId != null && !startInEdit) }
    var cropTarget by remember { mutableStateOf<String?>(null) }
    var selectedImageIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(images.size) {
        if (images.isEmpty()) {
            selectedImageIndex = null
        } else if (selectedImageIndex == null || selectedImageIndex!! >= images.size) {
            selectedImageIndex = 0
        }
    }
    var isBold by remember { mutableStateOf(false) }
    var isItalic by remember { mutableStateOf(false) }
    var isUnderline by remember { mutableStateOf(false) }
    var isStrike by remember { mutableStateOf(false) }
    var fontSize by remember { mutableIntStateOf(16) }
    var colorArgb by remember { mutableIntStateOf(0) }
    var bgArgb by remember { mutableIntStateOf(0) }
    var showSize by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }
    var colorMode by remember { mutableStateOf(ColorPickMode.TEXT) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkDraft by remember { mutableStateOf("") }

    LaunchedEffect(postId) {
        if (postId != null) {
            val existing = viewModel.loadMicroblogPost(postId)
            if (existing != null) {
                createdAt = existing.createdAtMs
                postTitle = existing.title
                textFieldValue = TextFieldValue(existing.body)
                spans = existing.spans
                images.clear()
                images.addAll(existing.images)
            }
            loaded = true
        }
    }

    fun hasFormat(
        bold: Boolean = isBold,
        italic: Boolean = isItalic,
        underline: Boolean = isUnderline,
        strike: Boolean = isStrike,
        size: Int = fontSize,
        color: Int = colorArgb,
        bg: Int = bgArgb,
        link: String? = null,
    ): Boolean = bold || italic || underline || strike || size != 16 || color != 0 || bg != 0 || !link.isNullOrBlank()

    fun formatRange(): Pair<Int, Int>? {
        val text = textFieldValue.text
        val sel = textFieldValue.selection
        if (!sel.collapsed && sel.min < sel.max) return sel.min to sel.max
        val cursor = sel.min.coerceIn(0, text.length)
        var start = cursor
        var end = cursor
        fun Char.isWordPart(): Boolean = isLetterOrDigit() || this == '-'
        while (start > 0 && text[start - 1].isWordPart()) start--
        while (end < text.length && text[end].isWordPart()) end++
        if (end <= start) return null
        return start to end
    }

    fun applyFormat(link: String? = null) {
        val range = formatRange() ?: return
        val start = range.first
        val end = range.second
        val updated = spans.toMutableList()
        updated.removeAll { it.start < end && it.end > start }
        val url = link?.trim()?.takeIf { it.isNotEmpty() }
        if (hasFormat(link = url)) {
            updated.add(
                MicroblogSpan(
                    start = start,
                    end = end,
                    bold = isBold,
                    italic = isItalic,
                    underline = isUnderline || url != null,
                    strikethrough = isStrike,
                    fontSize = fontSize,
                    colorArgb = colorArgb,
                    bgColorArgb = bgArgb,
                    linkUrl = url,
                ),
            )
        }
        spans = updated
        if (textFieldValue.selection.collapsed) {
            textFieldValue = textFieldValue.copy(selection = TextRange(start, end))
        }
    }

    fun applyTyping(start: Int, end: Int) {
        if (end <= start || !hasFormat()) return
        val updated = spans.toMutableList()
        val merge = updated.lastOrNull { span ->
            span.end == start &&
                span.bold == isBold &&
                span.italic == isItalic &&
                span.underline == isUnderline &&
                span.strikethrough == isStrike &&
                span.fontSize == fontSize &&
                span.colorArgb == colorArgb &&
                span.bgColorArgb == bgArgb &&
                span.linkUrl.isNullOrBlank()
        }
        if (merge != null) {
            updated.remove(merge)
            updated.add(merge.copy(end = end))
        } else {
            updated.add(
                MicroblogSpan(
                    start = start,
                    end = end,
                    bold = isBold,
                    italic = isItalic,
                    underline = isUnderline,
                    strikethrough = isStrike,
                    fontSize = fontSize,
                    colorArgb = colorArgb,
                    bgColorArgb = bgArgb,
                ),
            )
        }
        spans = updated
    }

    fun syncFromCursor(value: TextFieldValue) {
        val textLen = value.text.length
        if (textLen == 0) {
            isBold = false
            isItalic = false
            isUnderline = false
            isStrike = false
            fontSize = 16
            colorArgb = 0
            bgArgb = 0
            return
        }
        val lookAt = if (value.selection.collapsed) {
            (value.selection.min - 1).coerceAtLeast(0)
        } else {
            value.selection.min
        }.coerceIn(0, textLen - 1)
        val hit = spans.lastOrNull { lookAt >= it.start && lookAt < it.end && it.end <= textLen }
        if (hit != null) {
            isBold = hit.bold
            isItalic = hit.italic
            isUnderline = hit.underline
            isStrike = hit.strikethrough
            fontSize = hit.fontSize
            colorArgb = hit.colorArgb
            bgArgb = hit.bgColorArgb
        } else {
            isBold = false
            isItalic = false
            isUnderline = false
            isStrike = false
            fontSize = 16
            colorArgb = 0
            bgArgb = 0
        }
    }

    fun persist() {
        val body = textFieldValue.text
        if (postTitle.isBlank() && body.isBlank() && images.isEmpty()) {
            if (postId != null) viewModel.deleteMicroblogPost(id)
            return
        }
        viewModel.saveMicroblogPost(
            MicroblogPost(
                id = id,
                title = postTitle.trim(),
                body = body,
                spans = spans.filter { it.start >= 0 && it.end <= body.length && it.start < it.end },
                images = images.toList(),
                createdAtMs = createdAt,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.importMicroblogImage(uri).fold(
                onSuccess = { name ->
                    val at = microblogSnapInsertAt(textFieldValue.text, textFieldValue.selection.min)
                    images.add(MicroblogImage(fileName = name, insertAt = at))
                    selectedImageIndex = images.lastIndex
                    cropTarget = name
                },
                onFailure = {
                    Toast.makeText(context, it.message ?: "Не удалось добавить картинку", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    val visual = remember(spans, textFieldValue.text.length) {
        val len = textFieldValue.text.length
        MicroblogSpanTransformation(spans.filter { it.start >= 0 && it.end <= len && it.start < it.end })
    }

    fun goBack() {
        if (!isViewMode) persist()
        onBack()
    }
    BackHandler(onBack = { goBack() })

    val cropFile = cropTarget
    if (cropFile != null) {
        MicroblogImageCropScreen(
            file = File(MediaCatalogPaths.microblogDir(context), cropFile),
            onCancel = { cropTarget = null },
            onApply = { left, top, right, bottom, scale ->
                scope.launch {
                    viewModel.cropMicroblogImage(cropFile, left, top, right, bottom, scale).fold(
                        onSuccess = { newName ->
                            val i = images.indexOfFirst { it.fileName == cropFile }
                            if (i >= 0) images[i] = images[i].copy(fileName = newName)
                            if (newName != cropFile) {
                                viewModel.deleteUnusedMicroblogImage(cropFile, images.map { it.fileName })
                            }
                            cropTarget = null
                        },
                        onFailure = {
                            Toast.makeText(context, it.message ?: "Не удалось обрезать фото", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            },
        )
        return
    }

    val dateLabel = remember(createdAt) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(createdAt))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            postId == null -> "Новый пост"
                            isViewMode -> "Пост"
                            else -> "Редактирование"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (isViewMode) {
                        IconButton(onClick = { isViewMode = false }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Редактировать")
                        }
                    } else {
                        IconButton(onClick = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Filled.Image, contentDescription = "Картинка")
                        }
                        IconButton(onClick = {
                            val sel = textFieldValue.selection
                            val selected = if (!sel.collapsed) {
                                textFieldValue.text.substring(sel.min, sel.max)
                            } else {
                                ""
                            }
                            linkDraft = selected.takeIf { it.startsWith("http", true) || it.startsWith("www.", true) }.orEmpty()
                            showLinkDialog = true
                        }) {
                            Icon(Icons.Filled.Link, contentDescription = "Ссылка")
                        }
                        IconButton(onClick = {
                            persist()
                            if (postId != null) isViewMode = true else onBack()
                        }) {
                            Icon(Icons.Filled.Check, contentDescription = "Сохранить")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        if (isViewMode) {
            val kind = microblogCardKind(
                MicroblogPost(title = postTitle, body = textFieldValue.text, images = images.toList()),
            )
            val cs = MaterialTheme.colorScheme
            val accent = when (kind) {
                MicroblogCardKind.AI -> listOf(cs.secondary, cs.primary)
                MicroblogCardKind.PHOTO -> listOf(cs.primary, cs.tertiary)
                MicroblogCardKind.TEXT -> listOf(cs.tertiary, cs.secondary)
            }
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Brush.horizontalGradient(accent)),
                )
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val chip = when (kind) {
                        MicroblogCardKind.AI -> "Беседа ИИ" to cs.secondaryContainer
                        MicroblogCardKind.PHOTO -> "С фото" to cs.primaryContainer
                        MicroblogCardKind.TEXT -> "Пост" to cs.tertiaryContainer
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            chip.first,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSecondaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(chip.second)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            dateLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    if (postTitle.isNotBlank()) {
                        Text(
                            postTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    MicroblogArticleBody(
                        text = textFieldValue.text,
                        spans = spans,
                        images = images.toList(),
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                    )
                }
            }
        } else {
        val bodyStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
        val scroll = rememberScrollState()
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            OutlinedTextField(
                value = postTitle,
                onValueChange = { postTitle = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Название записи") },
            )
            MicroblogFormatBar(
                isBold = isBold,
                onBold = { isBold = it; applyFormat() },
                isItalic = isItalic,
                onItalic = { isItalic = it; applyFormat() },
                isUnderline = isUnderline,
                onUnderline = { isUnderline = it; applyFormat() },
                isStrike = isStrike,
                onStrike = { isStrike = it; applyFormat() },
                fontSize = fontSize,
                showSize = showSize,
                onToggleSize = { showSize = !showSize; if (showSize) showColor = false },
                onSize = { fontSize = it; applyFormat() },
                colorArgb = colorArgb,
                bgArgb = bgArgb,
                showColor = showColor,
                colorMode = colorMode,
                onToggleColor = { showColor = !showColor; if (showColor) showSize = false },
                onMode = { colorMode = it },
                onColor = { colorArgb = it; applyFormat() },
                onBg = { bgArgb = it; applyFormat() },
            )
            Text(
                "Как будет выглядеть",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
            )
            Text(
                "Нажмите на фото в превью, чтобы настроить его. Стрелками двигайте фото до или после абзаца, «К курсору» ставит его к позиции в тексте.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
            ) {
                Box(Modifier.padding(14.dp)) {
                    if (textFieldValue.text.isBlank() && images.isEmpty()) {
                        Text(
                            "Здесь появится предпросмотр: текст и фото вместе.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        MicroblogArticleBody(
                            text = textFieldValue.text,
                            spans = spans,
                            images = images.toList(),
                            style = bodyStyle,
                            selectedImageIndex = selectedImageIndex,
                            onImageClick = { selectedImageIndex = it },
                        )
                    }
                }
            }
            selectedImageIndex?.let { idx ->
                if (idx in images.indices) {
                    val image = images[idx]
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MicroblogImageEditorPanel(
                            image = image,
                            index = idx,
                            total = images.size,
                            bodyText = textFieldValue.text,
                            canMoveUp = if (textFieldValue.text.isNotBlank()) {
                                microblogCanMoveInsertAt(textFieldValue.text, image.insertAt, -1)
                            } else {
                                idx > 0
                            },
                            canMoveDown = if (textFieldValue.text.isNotBlank()) {
                                microblogCanMoveInsertAt(textFieldValue.text, image.insertAt, 1)
                            } else {
                                idx < images.lastIndex
                            },
                            onCrop = { cropTarget = image.fileName },
                            onScale = { images[idx] = image.copy(displayScale = it) },
                            onWrap = { mode ->
                                val scale = if (mode == MicroblogImageWrap.FULL) {
                                    if (image.displayScale < 0.55f) 1f else image.displayScale
                                } else {
                                    image.displayScale.coerceIn(0.28f, 0.55f)
                                }
                                images[idx] = image.copy(wrap = mode, displayScale = scale)
                            },
                            onMoveUp = {
                                val body = textFieldValue.text
                                if (body.isNotBlank()) {
                                    images[idx] = image.copy(
                                        insertAt = microblogMoveInsertAt(body, image.insertAt, -1),
                                    )
                                } else if (idx > 0) {
                                    val item = images.removeAt(idx)
                                    images.add(idx - 1, item)
                                    selectedImageIndex = idx - 1
                                }
                            },
                            onMoveDown = {
                                val body = textFieldValue.text
                                if (body.isNotBlank()) {
                                    images[idx] = image.copy(
                                        insertAt = microblogMoveInsertAt(body, image.insertAt, 1),
                                    )
                                } else if (idx < images.lastIndex) {
                                    val item = images.removeAt(idx)
                                    images.add(idx + 1, item)
                                    selectedImageIndex = idx + 1
                                }
                            },
                            onPlaceAtCursor = {
                                images[idx] = image.copy(
                                    insertAt = microblogSnapInsertAt(
                                        textFieldValue.text,
                                        textFieldValue.selection.min,
                                    ),
                                )
                            },
                            onPlaceAt = { at -> images[idx] = image.copy(insertAt = at) },
                            onRemove = {
                                val removed = images.removeAt(idx)
                                viewModel.deleteUnusedMicroblogImage(
                                    removed.fileName,
                                    images.map { it.fileName },
                                )
                                selectedImageIndex = if (images.isEmpty()) null else idx.coerceAtMost(images.lastIndex)
                            },
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(images, key = { _, img -> img.fileName }) { i, img ->
                                val selected = i == selectedImageIndex
                                AsyncImage(
                                    model = File(MediaCatalogPaths.microblogDir(context), img.fileName),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            if (selected) 2.dp else 1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                            RoundedCornerShape(10.dp),
                                        )
                                        .clickable { selectedImageIndex = i },
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "Текст записи",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
            )
            BasicTextField(
                value = textFieldValue,
                onValueChange = { new ->
                    val old = textFieldValue
                    val diff = new.text.length - old.text.length
                    if (diff != 0) {
                        val changePos = if (diff > 0) {
                            old.selection.min
                        } else {
                            new.selection.min
                        }.coerceIn(0, maxOf(old.text.length, new.text.length))
                        spans = adjustMicroblogSpans(spans, changePos, diff)
                        if (images.isNotEmpty()) {
                            val shifted = adjustMicroblogImageAnchors(
                                images.toList(),
                                changePos,
                                diff,
                                new.text.length,
                            )
                            shifted.forEachIndexed { i, img ->
                                if (i < images.size && images[i] != img) images[i] = img
                            }
                        }
                        if (diff > 0) applyTyping(changePos, changePos + diff)
                    }
                    textFieldValue = new
                    syncFromCursor(new)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = visual,
                decorationBox = { inner ->
                    Box {
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                "Текст поста. Выделите фрагмент и задайте стиль, цвет, размер или ссылку.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
        }
        }
    }

    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Ссылка") },
            text = {
                OutlinedTextField(
                    value = linkDraft,
                    onValueChange = { linkDraft = it },
                    label = { Text("https://…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = normalizeUrl(linkDraft)
                    if (url != null) {
                        val sel = textFieldValue.selection
                        if (sel.collapsed) {
                            val insert = url
                            val pos = sel.min.coerceIn(0, textFieldValue.text.length)
                            val newText = textFieldValue.text.substring(0, pos) + insert +
                                textFieldValue.text.substring(pos)
                            spans = adjustMicroblogSpans(spans, pos, insert.length)
                            if (images.isNotEmpty()) {
                                val shifted = adjustMicroblogImageAnchors(
                                    images.toList(),
                                    pos,
                                    insert.length,
                                    newText.length,
                                )
                                shifted.forEachIndexed { i, img ->
                                    if (i < images.size && images[i] != img) images[i] = img
                                }
                            }
                            textFieldValue = TextFieldValue(newText, TextRange(pos, pos + insert.length))
                            isUnderline = true
                            applyFormat(link = url)
                        } else {
                            isUnderline = true
                            applyFormat(link = url)
                        }
                    }
                    showLinkDialog = false
                }) { Text("Вставить") }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun MicroblogFormatBar(
    isBold: Boolean,
    onBold: (Boolean) -> Unit,
    isItalic: Boolean,
    onItalic: (Boolean) -> Unit,
    isUnderline: Boolean,
    onUnderline: (Boolean) -> Unit,
    isStrike: Boolean,
    onStrike: (Boolean) -> Unit,
    fontSize: Int,
    showSize: Boolean,
    onToggleSize: () -> Unit,
    onSize: (Int) -> Unit,
    colorArgb: Int,
    bgArgb: Int,
    showColor: Boolean,
    colorMode: ColorPickMode,
    onToggleColor: () -> Unit,
    onMode: (ColorPickMode) -> Unit,
    onColor: (Int) -> Unit,
    onBg: (Int) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(vertical = 4.dp),
    ) {
        AnimatedVisibility(visible = showSize) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FontSizes.forEach { size ->
                    val selected = size == fontSize
                    Text(
                        "$size",
                        fontSize = size.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { onSize(size) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        AnimatedVisibility(visible = showColor) {
            Column {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Цвет",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (colorMode == ColorPickMode.TEXT) primary else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { onMode(ColorPickMode.TEXT) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (colorMode == ColorPickMode.TEXT) MaterialTheme.colorScheme.onPrimary
                        else muted,
                    )
                    Text(
                        "Выделение",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (colorMode == ColorPickMode.BACKGROUND) primary else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { onMode(ColorPickMode.BACKGROUND) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (colorMode == ColorPickMode.BACKGROUND) MaterialTheme.colorScheme.onPrimary
                        else muted,
                    )
                }
                val colors = if (colorMode == ColorPickMode.TEXT) TextColors else BgColors
                val selected = if (colorMode == ColorPickMode.TEXT) colorArgb else bgArgb
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    colors.forEach { color ->
                        val argb = color.toArgb()
                        val none = color == Color.White && colorMode == ColorPickMode.TEXT ||
                            color == Color.Transparent && colorMode == ColorPickMode.BACKGROUND
                        val isSel = if (none) selected == 0 else selected == argb
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (color == Color.Transparent) Color.White else color)
                                .border(
                                    if (isSel) 2.dp else 1.dp,
                                    if (isSel) primary else MaterialTheme.colorScheme.outline,
                                    CircleShape,
                                )
                                .clickable {
                                    if (colorMode == ColorPickMode.TEXT) onColor(if (none) 0 else argb)
                                    else onBg(if (none) 0 else argb)
                                },
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
        ) {
            IconToggleButton(checked = isBold, onCheckedChange = onBold) {
                Icon(Icons.Filled.FormatBold, "Жирный", tint = if (isBold) primary else muted)
            }
            IconToggleButton(checked = isItalic, onCheckedChange = onItalic) {
                Icon(Icons.Filled.FormatItalic, "Курсив", tint = if (isItalic) primary else muted)
            }
            IconToggleButton(checked = isUnderline, onCheckedChange = onUnderline) {
                Icon(Icons.Filled.FormatUnderlined, "Подчёркнутый", tint = if (isUnderline) primary else muted)
            }
            IconToggleButton(checked = isStrike, onCheckedChange = onStrike) {
                Icon(Icons.Filled.FormatStrikethrough, "Зачёркнутый", tint = if (isStrike) primary else muted)
            }
            IconToggleButton(checked = showSize, onCheckedChange = { onToggleSize() }) {
                Icon(Icons.Filled.FormatSize, "Размер", tint = if (showSize) primary else muted)
            }
            IconToggleButton(checked = showColor, onCheckedChange = { onToggleColor() }) {
                Icon(
                    if (colorMode == ColorPickMode.BACKGROUND) Icons.Filled.FormatColorFill
                    else Icons.Filled.FormatColorText,
                    "Цвет",
                    tint = if (showColor) primary else muted,
                )
            }
        }
    }
}

@Composable
private fun MicroblogAnnotatedText(
    annotated: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val uriHandler = LocalUriHandler.current
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onClick = { offset ->
            annotated.getStringAnnotations(LinkTag, offset, offset).firstOrNull()?.let { ann ->
                normalizeUrl(ann.item)?.let { runCatching { uriHandler.openUri(it) } }
            }
        },
    )
}

private fun buildMicroblogAnnotated(
    text: String,
    spans: List<MicroblogSpan>,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)
    for (span in spans) {
        if (span.start >= text.length || span.end > text.length || span.start >= span.end) continue
        addStyle(span.toSpanStyle(linkColor), span.start, span.end.coerceAtMost(text.length))
        val url = span.linkUrl
        if (!url.isNullOrBlank()) {
            addStringAnnotation(LinkTag, url, span.start, span.end.coerceAtMost(text.length))
        }
    }
    for (match in UrlRegex.findAll(text)) {
        val start = match.range.first
        val end = match.range.last + 1
        val already = spans.any { !it.linkUrl.isNullOrBlank() && it.start < end && it.end > start }
        if (already) continue
        addStyle(
            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            start,
            end,
        )
        addStringAnnotation(LinkTag, match.value, start, end)
    }
}

private fun MicroblogSpan.toSpanStyle(linkColor: Color): SpanStyle {
    val deco = buildList {
        if (underline || !linkUrl.isNullOrBlank()) add(TextDecoration.Underline)
        if (strikethrough) add(TextDecoration.LineThrough)
    }
    return SpanStyle(
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = when (deco.size) {
            0 -> null
            1 -> deco[0]
            else -> TextDecoration.combine(deco)
        },
        fontSize = if (fontSize != 16) fontSize.sp else TextUnit.Unspecified,
        color = when {
            !linkUrl.isNullOrBlank() -> linkColor
            colorArgb != 0 -> Color(colorArgb)
            else -> Color.Unspecified
        },
        background = if (bgColorArgb != 0) Color(bgColorArgb) else Color.Unspecified,
    )
}

private class MicroblogSpanTransformation(
    private val spans: List<MicroblogSpan>,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        for (span in spans) {
            if (span.start >= text.length || span.end > text.length || span.start >= span.end) continue
            builder.addStyle(span.toSpanStyle(Color.Unspecified), span.start, span.end.coerceAtMost(text.length))
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private fun adjustMicroblogSpans(
    spans: List<MicroblogSpan>,
    changePos: Int,
    diff: Int,
): List<MicroblogSpan> {
    val result = mutableListOf<MicroblogSpan>()
    for (span in spans) {
        when {
            span.end <= changePos -> result.add(span)
            span.start >= changePos + (if (diff < 0) -diff else 0) -> {
                result.add(span.copy(start = span.start + diff, end = span.end + diff))
            }
            diff > 0 && span.start <= changePos && span.end >= changePos -> {
                result.add(span.copy(end = span.end + diff))
            }
            diff < 0 -> {
                val delStart = changePos
                val newStart = span.start.coerceAtMost(delStart)
                val newEnd = (span.end + diff).coerceAtLeast(newStart)
                if (newEnd > newStart) result.add(span.copy(start = newStart, end = newEnd))
            }
            else -> result.add(span)
        }
    }
    return result
}

private fun normalizeUrl(raw: String): String? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    return when {
        t.startsWith("http://", true) || t.startsWith("https://", true) -> t
        t.startsWith("www.", true) -> "https://$t"
        t.contains('.') -> "https://$t"
        else -> null
    }
}
