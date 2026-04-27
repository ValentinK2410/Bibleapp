@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.bible.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlinx.coroutines.flow.collect
import com.example.bible.R
import com.example.bible.data.BibleLibrary
import com.example.bible.data.TranslationId
import com.example.bible.data.genealogy.GenealogyData
import com.example.bible.data.genealogy.GenealogyEngine
import com.example.bible.data.genealogy.GenealogyPathResult
import com.example.bible.data.genealogy.GenealogyPerson
import com.example.bible.data.genealogy.GenealogyScriptureParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenealogyScreen(
    onBack: () -> Unit,
    library: BibleLibrary,
    translation: TranslationId,
    onOpenInReader: (bookId: String, chapter: Int, verse: Int) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<GenealogyPerson?>(null) }
    var pathFrom by remember { mutableStateOf<GenealogyPerson?>(null) }
    var pathTo by remember { mutableStateOf<GenealogyPerson?>(null) }
    var pathResult by remember { mutableStateOf<GenealogyPathResult?>(null) }
    var pickerMode by remember { mutableStateOf<PickerMode>(PickerMode.NONE) }
    var previewSegment by remember { mutableStateOf<String?>(null) }
    var treeScope by remember { mutableStateOf(GenealogyTreeScope.BOTH) }

    val searchResults = remember(searchQuery) {
        GenealogyEngine.search(searchQuery).take(50)
    }

    var expandedDescendantNodes by remember(selected?.id) { mutableStateOf<Set<String>>(emptySet()) }
    var expandedAncestorNodes by remember(selected?.id) { mutableStateOf<Set<String>>(emptySet()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.genealogy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.genealogy_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    stringResource(R.string.genealogy_refs_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    stringResource(R.string.genealogy_themes_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GenealogyData.themes.forEach { theme ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                GenealogyEngine.getPerson(theme.startPersonId)?.let {
                                    selected = it
                                    treeScope = GenealogyTreeScope.BOTH
                                }
                                pathResult = null
                            },
                            label = { Text(theme.titleRu) },
                        )
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.genealogy_path_section_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { pickerMode = PickerMode.FROM },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    pathFrom?.nameRu ?: stringResource(R.string.genealogy_pick_first),
                                    maxLines = 1,
                                )
                            }
                            OutlinedButton(
                                onClick = { pickerMode = PickerMode.TO },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    pathTo?.nameRu ?: stringResource(R.string.genealogy_pick_second),
                                    maxLines = 1,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val a = pathFrom?.id
                                val b = pathTo?.id
                                pathResult = if (a != null && b != null) {
                                    GenealogyEngine.shortestPath(a, b)
                                } else null
                            },
                            enabled = pathFrom != null && pathTo != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.AccountTree, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.genealogy_find_path))
                        }
                    }
                }
            }
            pathResult?.let { result ->
                item {
                    PathResultCard(result)
                }
            }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.genealogy_search_person)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
            }
            items(searchResults.size) { idx ->
                val p = searchResults[idx]
                Card(
                    onClick = {
                        selected = p
                        pathResult = null
                        treeScope = GenealogyTreeScope.BOTH
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.nameRu, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        if (p.noteRu.isNotBlank()) {
                            Text(
                                p.noteRu,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                            )
                        }
                        if (p.scriptureRefs.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.genealogy_search_refs_label) + ": " +
                                    p.scriptureRefs.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                                maxLines = 4,
                            )
                        }
                    }
                }
            }
            selected?.let { p ->
                item {
                    PersonDetailCard(
                        person = p,
                        onSetPathFrom = { pathFrom = p },
                        onSetPathTo = { pathTo = p },
                        onPreviewRef = { seg -> previewSegment = seg },
                    )
                }
                item {
                    Text(
                        stringResource(R.string.genealogy_tree_scope_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = treeScope == GenealogyTreeScope.DESCENDANTS_ONLY,
                            onClick = { treeScope = GenealogyTreeScope.DESCENDANTS_ONLY },
                            label = { Text(stringResource(R.string.genealogy_tree_scope_descendants)) },
                        )
                        FilterChip(
                            selected = treeScope == GenealogyTreeScope.ANCESTORS_ONLY,
                            onClick = { treeScope = GenealogyTreeScope.ANCESTORS_ONLY },
                            label = { Text(stringResource(R.string.genealogy_tree_scope_ancestors)) },
                        )
                        FilterChip(
                            selected = treeScope == GenealogyTreeScope.BOTH,
                            onClick = { treeScope = GenealogyTreeScope.BOTH },
                            label = { Text(stringResource(R.string.genealogy_tree_scope_both)) },
                        )
                    }
                }
                if (treeScope == GenealogyTreeScope.DESCENDANTS_ONLY || treeScope == GenealogyTreeScope.BOTH) {
                    item {
                        GenealogyDescendantTreeCard(
                            rootPerson = p,
                            expandedNodes = expandedDescendantNodes,
                            onExpandedChange = { expandedDescendantNodes = it },
                            onSelectPerson = { sel ->
                                selected = sel
                                treeScope = GenealogyTreeScope.BOTH
                            },
                        )
                    }
                }
                if (treeScope == GenealogyTreeScope.ANCESTORS_ONLY || treeScope == GenealogyTreeScope.BOTH) {
                    item {
                        GenealogyAncestorTreeCard(
                            rootPerson = p,
                            expandedNodes = expandedAncestorNodes,
                            onExpandedChange = { expandedAncestorNodes = it },
                            onSelectPerson = { sel ->
                                selected = sel
                                treeScope = GenealogyTreeScope.BOTH
                            },
                        )
                    }
                }
            }
        }
    }

    previewSegment?.let { seg ->
        ScripturePreviewDialog(
            segment = seg,
            library = library,
            translation = translation,
            onDismiss = { previewSegment = null },
            onOpenInReader = onOpenInReader,
        )
    }

    if (pickerMode != PickerMode.NONE) {
        PersonPickerDialog(
            title = when (pickerMode) {
                PickerMode.FROM -> stringResource(R.string.genealogy_pick_first)
                PickerMode.TO -> stringResource(R.string.genealogy_pick_second)
                else -> ""
            },
            onDismiss = { pickerMode = PickerMode.NONE },
            onPick = { person ->
                when (pickerMode) {
                    PickerMode.FROM -> pathFrom = person
                    PickerMode.TO -> pathTo = person
                    PickerMode.NONE -> {}
                }
                pickerMode = PickerMode.NONE
                pathResult = null
            },
        )
    }
}

private enum class GenealogyTreeScope {
    DESCENDANTS_ONLY,
    ANCESTORS_ONLY,
    BOTH,
}

private enum class PickerMode { NONE, FROM, TO }

private fun allExpandableDescendantIds(rootId: String): Set<String> {
    val acc = mutableSetOf<String>()
    fun dfs(id: String) {
        val ch = GenealogyEngine.children(id)
        if (ch.isNotEmpty()) {
            acc.add(id)
            ch.forEach { dfs(it.id) }
        }
    }
    dfs(rootId)
    return acc
}

/** Глубина дерева от корня (число уровней вниз до самого глубокого листа). */
private fun maxDescendantTreeDepth(rootId: String): Int {
    fun depth(id: String): Int {
        val ch = GenealogyEngine.children(id)
        if (ch.isEmpty()) return 0
        return 1 + ch.maxOf { depth(it.id) }
    }
    return depth(rootId)
}

private fun allExpandableAncestorIds(rootId: String): Set<String> {
    val acc = mutableSetOf<String>()
    fun dfs(id: String) {
        val pa = GenealogyEngine.parents(id)
        if (pa.isNotEmpty()) {
            acc.add(id)
            pa.forEach { dfs(it.id) }
        }
    }
    dfs(rootId)
    return acc
}

/** Глубина дерева предков (уровней вверх). */
private fun maxAncestorTreeDepth(rootId: String): Int {
    fun depth(id: String): Int {
        val pa = GenealogyEngine.parents(id)
        if (pa.isEmpty()) return 0
        return 1 + pa.maxOf { depth(it.id) }
    }
    return depth(rootId)
}

@Composable
private fun GenealogyDescendantTreeCard(
    rootPerson: GenealogyPerson,
    expandedNodes: Set<String>,
    onExpandedChange: (Set<String>) -> Unit,
    onSelectPerson: (GenealogyPerson) -> Unit,
) {
    val hasChildren = remember(rootPerson.id) {
        GenealogyEngine.children(rootPerson.id).isNotEmpty()
    }
    val treeHorizontalScroll = remember(rootPerson.id) { ScrollState(0) }
    var hScrollPx by remember(rootPerson.id) { mutableStateOf(0) }
    LaunchedEffect(treeHorizontalScroll) {
        snapshotFlow { treeHorizontalScroll.value }.collect { hScrollPx = it }
    }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.genealogy_tree_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            if (!hasChildren) {
                Text(
                    stringResource(R.string.genealogy_tree_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        onClick = {
                            onExpandedChange(allExpandableDescendantIds(rootPerson.id))
                        },
                    ) {
                        Text(stringResource(R.string.genealogy_tree_expand_all))
                    }
                    TextButton(
                        onClick = { onExpandedChange(emptySet()) },
                    ) {
                        Text(stringResource(R.string.genealogy_tree_collapse_all))
                    }
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    if (hScrollPx <= 2) {
                        stringResource(R.string.genealogy_tree_scroll_position_start)
                    } else {
                        stringResource(R.string.genealogy_tree_scroll_position_moved)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(6.dp))
                val screenDp = LocalConfiguration.current.screenWidthDp
                val treeDepth = remember(rootPerson.id) { maxDescendantTreeDepth(rootPerson.id) }
                val computedMin = (48 + treeDepth * 28 + 260).coerceAtLeast(300)
                val minTreeWidth = max(screenDp, computedMin).dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val step = 28.dp.toPx()
                            var x = 0f
                            while (x < size.width) {
                                drawLine(
                                    color = gridColor,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1f,
                                )
                                x += step
                            }
                            var y = 0f
                            while (y < size.height) {
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f,
                                )
                                y += step
                            }
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(treeHorizontalScroll),
                    ) {
                        Column(
                            modifier = Modifier.widthIn(min = minTreeWidth),
                        ) {
                            DescendantBranch(
                                personId = rootPerson.id,
                                depth = 0,
                                ancestorContinues = emptyList(),
                                isLastChild = true,
                                expandedNodes = expandedNodes,
                                onToggle = { id ->
                                    onExpandedChange(
                                        if (id in expandedNodes) expandedNodes - id else expandedNodes + id,
                                    )
                                },
                                onSelectPerson = onSelectPerson,
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.genealogy_tree_scroll_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun GenealogyAncestorTreeCard(
    rootPerson: GenealogyPerson,
    expandedNodes: Set<String>,
    onExpandedChange: (Set<String>) -> Unit,
    onSelectPerson: (GenealogyPerson) -> Unit,
) {
    val hasParents = remember(rootPerson.id) {
        GenealogyEngine.parents(rootPerson.id).isNotEmpty()
    }
    val treeHorizontalScroll = remember(rootPerson.id) { ScrollState(0) }
    var hScrollPx by remember(rootPerson.id) { mutableStateOf(0) }
    LaunchedEffect(treeHorizontalScroll) {
        snapshotFlow { treeHorizontalScroll.value }.collect { hScrollPx = it }
    }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.genealogy_tree_ancestors_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            if (!hasParents) {
                Text(
                    stringResource(R.string.genealogy_ancestors_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        onClick = {
                            onExpandedChange(allExpandableAncestorIds(rootPerson.id))
                        },
                    ) {
                        Text(stringResource(R.string.genealogy_tree_expand_all))
                    }
                    TextButton(
                        onClick = { onExpandedChange(emptySet()) },
                    ) {
                        Text(stringResource(R.string.genealogy_tree_collapse_all))
                    }
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    if (hScrollPx <= 2) {
                        stringResource(R.string.genealogy_tree_scroll_position_start)
                    } else {
                        stringResource(R.string.genealogy_tree_scroll_position_moved)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(6.dp))
                val screenDp = LocalConfiguration.current.screenWidthDp
                val treeDepth = remember(rootPerson.id) { maxAncestorTreeDepth(rootPerson.id) }
                val computedMin = (48 + treeDepth * 28 + 260).coerceAtLeast(300)
                val minTreeWidth = max(screenDp, computedMin).dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val step = 28.dp.toPx()
                            var x = 0f
                            while (x < size.width) {
                                drawLine(
                                    color = gridColor,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1f,
                                )
                                x += step
                            }
                            var y = 0f
                            while (y < size.height) {
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f,
                                )
                                y += step
                            }
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(treeHorizontalScroll),
                    ) {
                        Column(
                            modifier = Modifier.widthIn(min = minTreeWidth),
                        ) {
                            AncestorBranch(
                                personId = rootPerson.id,
                                depth = 0,
                                ancestorContinues = emptyList(),
                                isLastChild = true,
                                expandedNodes = expandedNodes,
                                onToggle = { id ->
                                    onExpandedChange(
                                        if (id in expandedNodes) expandedNodes - id else expandedNodes + id,
                                    )
                                },
                                onSelectPerson = onSelectPerson,
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.genealogy_tree_scroll_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DescendantBranch(
    personId: String,
    depth: Int,
    ancestorContinues: List<Boolean>,
    isLastChild: Boolean,
    expandedNodes: Set<String>,
    onToggle: (String) -> Unit,
    onSelectPerson: (GenealogyPerson) -> Unit,
) {
    val person = GenealogyEngine.getPerson(personId) ?: return
    val children = remember(personId) {
        GenealogyEngine.children(personId).sortedBy { it.nameRu }
    }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (depth > 0) {
                Text(
                    text = buildString {
                        for (i in 0 until depth - 1) {
                            append(if (ancestorContinues[i]) "│ " else "  ")
                        }
                        append(if (isLastChild) "└ " else "├ ")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (children.isNotEmpty()) {
                IconButton(onClick = { onToggle(personId) }) {
                    Icon(
                        if (personId in expandedNodes) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Text(
                person.nameRu,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clickable { onSelectPerson(person) },
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (personId in expandedNodes) {
            children.forEachIndexed { i, child ->
                DescendantBranch(
                    personId = child.id,
                    depth = depth + 1,
                    ancestorContinues = ancestorContinues + (i != children.lastIndex),
                    isLastChild = i == children.lastIndex,
                    expandedNodes = expandedNodes,
                    onToggle = onToggle,
                    onSelectPerson = onSelectPerson,
                )
            }
        }
    }
}

@Composable
private fun AncestorBranch(
    personId: String,
    depth: Int,
    ancestorContinues: List<Boolean>,
    isLastChild: Boolean,
    expandedNodes: Set<String>,
    onToggle: (String) -> Unit,
    onSelectPerson: (GenealogyPerson) -> Unit,
) {
    val person = GenealogyEngine.getPerson(personId) ?: return
    val parents = remember(personId) {
        GenealogyEngine.parents(personId).sortedBy { it.nameRu }
    }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (depth > 0) {
                Text(
                    text = buildString {
                        for (i in 0 until depth - 1) {
                            append(if (ancestorContinues[i]) "│ " else "  ")
                        }
                        append(if (isLastChild) "└ " else "├ ")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (parents.isNotEmpty()) {
                IconButton(onClick = { onToggle(personId) }) {
                    Icon(
                        if (personId in expandedNodes) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Text(
                person.nameRu,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clickable { onSelectPerson(person) },
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (personId in expandedNodes) {
            parents.forEachIndexed { i, parent ->
                AncestorBranch(
                    personId = parent.id,
                    depth = depth + 1,
                    ancestorContinues = ancestorContinues + (i != parents.lastIndex),
                    isLastChild = i == parents.lastIndex,
                    expandedNodes = expandedNodes,
                    onToggle = onToggle,
                    onSelectPerson = onSelectPerson,
                )
            }
        }
    }
}

@Composable
private fun ScripturePreviewDialog(
    segment: String,
    library: BibleLibrary,
    translation: TranslationId,
    onDismiss: () -> Unit,
    onOpenInReader: (bookId: String, chapter: Int, verse: Int) -> Unit,
) {
    var body by remember(segment) { mutableStateOf<String?>(null) }
    var loading by remember(segment) { mutableStateOf(true) }

    LaunchedEffect(segment, translation) {
        loading = true
        body = GenealogyScriptureParser.loadPassageText(library, translation, segment)
        loading = false
    }

    val resolved = remember(segment) { GenealogyScriptureParser.parseSegment(segment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.genealogy_verse_preview_title))
                Spacer(Modifier.height(4.dp))
                Text(
                    segment,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            when {
                loading -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(Modifier.padding(16.dp))
                        Text(stringResource(R.string.genealogy_verse_loading))
                    }
                }
                body.isNullOrBlank() -> {
                    Text(
                        stringResource(R.string.genealogy_verse_unavailable),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(body!!, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (resolved != null) {
                    TextButton(
                        onClick = {
                            onOpenInReader(resolved.bookId, resolved.navigateChapter, resolved.navigateVerse)
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(R.string.genealogy_open_in_reader))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.genealogy_close_picker))
                }
            }
        },
    )
}

@Composable
private fun PathResultCard(result: GenealogyPathResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            when (result) {
                is GenealogyPathResult.SamePerson -> {
                    Text(stringResource(R.string.genealogy_same_person), fontWeight = FontWeight.SemiBold)
                }
                is GenealogyPathResult.NotFound -> {
                    Text(result.reasonRu, color = MaterialTheme.colorScheme.error)
                }
                is GenealogyPathResult.Found -> {
                    Text(
                        stringResource(R.string.genealogy_path_found),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    result.steps.forEach { step ->
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text(
                            "${step.from.nameRu} → ${step.to.nameRu}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            step.relationRu,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonDetailCard(
    person: GenealogyPerson,
    onSetPathFrom: () -> Unit,
    onSetPathTo: () -> Unit,
    onPreviewRef: (String) -> Unit,
) {
    val parents = remember(person.id) { GenealogyEngine.parents(person.id) }
    val children = remember(person.id) { GenealogyEngine.children(person.id) }
    val sibs = remember(person.id) { GenealogyEngine.siblings(person.id) }
    val sps = remember(person.id) { GenealogyEngine.spouses(person.id) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.AccountTree, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(person.nameRu, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(person.noteRu, style = MaterialTheme.typography.bodyMedium)
            if (person.scriptureRefs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.genealogy_verse_preview_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                person.scriptureRefs.forEach { refLine ->
                    val segments = GenealogyScriptureParser.splitRefSegments(refLine)
                    if (segments.isEmpty()) {
                        Text(refLine, style = MaterialTheme.typography.labelSmall)
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            segments.forEach { seg ->
                                AssistChip(
                                    onClick = { onPreviewRef(seg) },
                                    label = { Text(seg, style = MaterialTheme.typography.labelMedium) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSetPathFrom) { Text(stringResource(R.string.genealogy_use_as_first)) }
                TextButton(onClick = onSetPathTo) { Text(stringResource(R.string.genealogy_use_as_second)) }
            }
            if (parents.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.genealogy_parents), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    parents.forEach { Text("• ${it.nameRu}", style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (children.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.genealogy_children), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    children.forEach { Text("• ${it.nameRu}", style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (sps.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.genealogy_spouses), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sps.forEach { Text("• ${it.nameRu}", style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (sibs.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.genealogy_siblings), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sibs.forEach { Text("• ${it.nameRu}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun PersonPickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onPick: (GenealogyPerson) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val list = remember(q) { GenealogyEngine.search(q).take(50) }
    val scroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    label = { Text(stringResource(R.string.genealogy_search_person)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .height(280.dp)
                        .verticalScroll(scroll),
                ) {
                    list.forEach { p ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(p) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(p.nameRu, style = MaterialTheme.typography.bodyLarge)
                            if (p.noteRu.isNotBlank()) {
                                Text(
                                    p.noteRu,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                            if (p.scriptureRefs.isNotEmpty()) {
                                Text(
                                    p.scriptureRefs.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                                    maxLines = 3,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.genealogy_close_picker)) } },
    )
}
