package com.example.bible.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.example.bible.data.MediaLibrarySort
import com.example.bible.data.UserMediaKind

/**
 * Компактный поиск и сортировка в одной строке: поле не раздувает экран,
 * а порядок найденных файлов меняется без отдельной панели.
 */
@Composable
fun MediaLibrarySearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: MediaLibrarySort,
    onSortChange: (MediaLibrarySort) -> Unit,
    kind: UserMediaKind,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactMediaSearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        MediaLibrarySortButton(
            sort = sort,
            onSortChange = onSortChange,
            kind = kind,
        )
    }
}

@Composable
private fun CompactMediaSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Поиск по названию или меткам",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Очистить",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaLibrarySortButton(
    sort: MediaLibrarySort,
    onSortChange: (MediaLibrarySort) -> Unit,
    kind: UserMediaKind,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Filled.Sort,
                    contentDescription = "Сортировка: ${sort.labelRu(kind)}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MediaLibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.labelRu(kind)) },
                    onClick = {
                        onSortChange(option)
                        menuOpen = false
                    },
                    trailingIcon = if (option == sort) {
                        {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
