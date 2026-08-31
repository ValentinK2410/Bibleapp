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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.bible.data.MediaCatalogPaths
import com.example.bible.data.MicroblogImage
import com.example.bible.data.MicroblogPost
import com.example.bible.data.MicroblogSpan
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    maxHeight: androidx.compose.ui.unit.Dp,
) {
    val context = LocalContext.current
    val scale = displayScale.coerceIn(0.35f, 1f)
    AsyncImage(
        model = File(MediaCatalogPaths.microblogDir(context), fileName),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth(scale)
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(16.dp)),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun MicroblogEditorImageRow(
    image: MicroblogImage,
    onCrop: () -> Unit,
    onScale: (Float) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.45f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = File(MediaCatalogPaths.microblogDir(context), image.fileName),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Фото в записи", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Обрежьте кадр или выберите ширину на экране",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCrop) {
                Icon(Icons.Filled.Crop, contentDescription = "Обрезать и размер файла")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Убрать", tint = cs.error)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.55f to "Узко", 0.78f to "Средне", 1f to "Широко").forEach { (value, label) ->
                val selected = kotlin.math.abs(image.displayScale - value) < 0.04f
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) cs.onPrimary else cs.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) cs.primary else cs.surface)
                        .clickable { onScale(value) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
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
                    images.add(MicroblogImage(name))
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
                    if (textFieldValue.text.isNotBlank()) {
                        MicroblogRichText(
                            text = textFieldValue.text,
                            spans = spans,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                        )
                    }
                    images.forEach { image ->
                        MicroblogFittedImage(
                            fileName = image.fileName,
                            displayScale = image.displayScale,
                            maxHeight = 420.dp,
                        )
                    }
                }
            }
        } else {
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize(),
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
                        if (diff > 0) applyTyping(changePos, changePos + diff)
                    }
                    textFieldValue = new
                    syncFromCursor(new)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
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
            if (images.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    images.forEachIndexed { index, image ->
                        MicroblogEditorImageRow(
                            image = image,
                            onCrop = { cropTarget = image.fileName },
                            onScale = { images[index] = image.copy(displayScale = it) },
                            onRemove = {
                                val removed = images.removeAt(index)
                                viewModel.deleteUnusedMicroblogImage(removed.fileName, images.map { it.fileName })
                            },
                        )
                    }
                }
            }
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
private fun MicroblogRichText(
    text: String,
    spans: List<MicroblogSpan>,
    style: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, spans, linkColor) {
        buildMicroblogAnnotated(text, spans, linkColor)
    }
    ClickableText(
        text = annotated,
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
