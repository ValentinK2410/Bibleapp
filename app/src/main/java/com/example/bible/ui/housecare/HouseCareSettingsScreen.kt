package com.example.bible.ui.housecare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.housecare.HousePlace
import com.example.bible.ui.theme.OutlinedTextField

private val PlaceKinds = listOf("Дом", "Квартира", "Гараж", "Дача", "Сарае")

internal fun floorsLabel(count: Int): String {
    val word = when {
        count % 100 in 11..14 -> "этажей"
        count % 10 == 1 -> "этаж"
        count % 10 in 2..4 -> "этажа"
        else -> "этажей"
    }
    return "$count $word"
}

/** Настройки «Системы уборки»: адреса объектов и сколько в них этажей. */
@Composable
fun HouseCareSettingsScreen(
    viewModel: HouseCareViewModel,
    onBack: () -> Unit,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    var addingKind by remember { mutableStateOf<String?>(null) }
    var editingPlace by remember { mutableStateOf<HousePlace?>(null) }

    HouseCareScaffold(title = "Настройки уборки", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { HouseCareSectionTitle("Объекты") }
            item {
                Text(
                    "Укажите адрес дома, квартиры, гаража, дачи или сарая и сколько в них этажей. " +
                        "Эти сведения подписывают список этажей в «Системе уборки».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (data.places.isEmpty()) {
                item {
                    EmptyAddCard(
                        hint = "Добавьте объект",
                        subtitle = "Например: дом в селе, квартира, гараж, дача, сарай",
                        onClick = { addingKind = "Дом" },
                    )
                }
            }
            items(data.places, key = { it.id }) { place ->
                CleaningRow(
                    title = place.name,
                    subtitle = buildString {
                        append(place.address.ifBlank { "Адрес не указан" })
                        append(" · ")
                        append(if (place.floorsCount > 0) floorsLabel(place.floorsCount) else "Этажи не указаны")
                    },
                    cleaned = false,
                    showNotCleanedLabel = false,
                    onClick = { editingPlace = place },
                    onRename = { editingPlace = place },
                )
            }
            item { HouseCareSectionTitle("Быстрое добавление") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlaceKinds.forEach { kind ->
                        Card(
                            modifier = Modifier.clickable { addingKind = kind },
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    kind,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    addingKind?.let { kind ->
        PlaceEditDialog(
            title = "Новый объект",
            initial = HousePlace(name = kind),
            canDelete = false,
            onDismiss = { addingKind = null },
            onSave = { name, address, floors ->
                viewModel.addPlace(name, address, floors)
                addingKind = null
            },
            onDelete = {},
        )
    }

    editingPlace?.let { place ->
        PlaceEditDialog(
            title = place.name,
            initial = place,
            canDelete = true,
            onDismiss = { editingPlace = null },
            onSave = { name, address, floors ->
                viewModel.updatePlace(place.id, name, address, floors)
                editingPlace = null
            },
            onDelete = { viewModel.deletePlace(place.id); editingPlace = null },
        )
    }
}

@Composable
internal fun PlaceEditDialog(
    title: String,
    initial: HousePlace,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, address: String, floorsCount: Int) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var address by remember { mutableStateOf(initial.address) }
    var floors by remember { mutableStateOf(if (initial.floorsCount > 0) initial.floorsCount.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    placeholder = { Text("Дом, квартира, гараж, дача, сарае") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Адрес") },
                    placeholder = { Text("Улица, дом, квартира") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = floors,
                        onValueChange = { value -> floors = value.filter { it.isDigit() }.take(2) },
                        label = { Text("Этажей") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { floors = (((floors.toIntOrNull() ?: 0) - 1).coerceAtLeast(0)).toString() }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Меньше этажей")
                    }
                    IconButton(onClick = { floors = ((floors.toIntOrNull() ?: 0) + 1).coerceAtMost(99).toString() }) {
                        Icon(Icons.Filled.Add, contentDescription = "Больше этажей")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, address, floors.toIntOrNull() ?: 0) },
                enabled = name.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = {
            if (canDelete) {
                TextButton(onClick = onDelete) { Text("Удалить") }
            } else {
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}
