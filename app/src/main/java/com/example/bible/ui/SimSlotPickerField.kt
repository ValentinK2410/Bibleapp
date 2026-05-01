package com.example.bible.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Как в редакторе сценариев SMS: заголовок секции и выбор через OutlinedButton + DropdownMenu. */
@Composable
fun SimSlotPickerField(
    sectionTitle: String,
    options: List<SimSlot>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val idx = selectedIndex.coerceIn(0, options.lastIndex)
    val selectedLabel = options[idx].label
    Column(modifier.fillMaxWidth()) {
        Text(
            sectionTitle,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(selectedLabel, maxLines = 3)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                options.forEachIndexed { index, slot ->
                    DropdownMenuItem(
                        text = { Text(slot.label) },
                        onClick = {
                            expanded = false
                            onSelectIndex(index)
                        },
                    )
                }
            }
        }
    }
}
