package com.example.bible.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.TimemarkProject

/**
 * Выбор: обычная озвучка главы или один из сохранённых проектов таймкодов.
 */
@Composable
fun TimemarkSourceSelector(
    projects: List<TimemarkProject>,
    selectedProjectId: String?,
    onSelectPlain: () -> Unit,
    onSelectProject: (TimemarkProject) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (projects.isEmpty()) return

    var menuOpen by remember { mutableStateOf(false) }
    val plainLabel = stringResource(R.string.timemark_source_plain)
    val untitled = stringResource(R.string.timemark_untitled_project)
    val buttonLabel = when {
        selectedProjectId == null -> plainLabel
        else -> {
            val p = projects.find { it.id == selectedProjectId }
            p?.title?.takeIf { it.isNotBlank() } ?: untitled
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.timemark_source_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { menuOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        buttonLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, Modifier.size(20.dp))
                }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.fillMaxWidth(),
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.timemark_source_plain)) },
                    onClick = {
                        onSelectPlain()
                        menuOpen = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Headphones, contentDescription = null)
                    },
                    trailingIcon = {
                        if (selectedProjectId == null) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
                HorizontalDivider()
                projects.forEach { p ->
                    val title = p.title.ifBlank { untitled }
                    DropdownMenuItem(
                        text = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            onSelectProject(p)
                            menuOpen = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            if (p.id == selectedProjectId) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                }
            }
        }
    }
}
