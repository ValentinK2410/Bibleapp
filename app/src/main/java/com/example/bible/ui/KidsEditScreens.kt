package com.example.bible.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.KidsCustomHubEntry
import com.example.bible.data.KidsPicturedItemPatch
import com.example.bible.data.KidsPicturedItemJson
import com.example.bible.data.KidsPicturedRoutes
import com.example.bible.data.KidsPicturedSectionEdits
import com.example.bible.data.KidsUserMediaStorage
import com.example.bible.data.KidsUserSectionsMerge
import com.example.bible.data.KidsUserSectionsState

private fun ensureHubOrder(state: KidsUserSectionsState, defaults: List<KidsHubRow>): List<String> {
    return state.order ?: mergeKidsHubRows(defaults, state).map { it.route }
}

private fun rowTitleForRoute(route: String, state: KidsUserSectionsState): String {
    return mergeKidsHubRows(kidsHubDefaultRows(), state).find { it.route == route }?.title ?: route
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsEditHubSectionsScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onOpenPicturedEditor: (String) -> Unit,
) {
    val state by viewModel.kidsUserSections.collectAsStateWithLifecycle()
    val defaults = remember { kidsHubDefaultRows() }
    var addOpen by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newSubtitle by remember { mutableStateOf("") }
    var newEmoji by remember { mutableStateOf("🌿") }
    var newCardStyleToken by remember { mutableStateOf("Pictures") }
    var newTileStyle by remember { mutableStateOf("square") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.kids_edit_sections_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newTitle = ""
                    newSubtitle = ""
                    newEmoji = "🌿"
                    newCardStyleToken = "Pictures"
                    newTileStyle = "square"
                    addOpen = true
                },
            ) {
                Text("+")
            }
        },
    ) { padding ->
        val routes = ensureHubOrder(state, defaults)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(routes, key = { it }) { route ->
                val hidden = route in state.hiddenRoutes
                val canDelete = route.startsWith("kids_custom_")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(rowTitleForRoute(route, state), style = MaterialTheme.typography.titleMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            TextButton(onClick = {
                                val cur = ensureHubOrder(state, defaults).toMutableList()
                                val i = cur.indexOf(route)
                                if (i > 0) {
                                    cur.removeAt(i)
                                    cur.add(i - 1, route)
                                    viewModel.setKidsUserSections(state.copy(order = cur))
                                }
                            }) {
                                Text(stringResource(R.string.kids_edit_hub_up))
                            }
                            TextButton(onClick = {
                                val cur = ensureHubOrder(state, defaults).toMutableList()
                                val i = cur.indexOf(route)
                                if (i in 0 until cur.lastIndex) {
                                    cur.removeAt(i)
                                    cur.add(i + 1, route)
                                    viewModel.setKidsUserSections(state.copy(order = cur))
                                }
                            }) {
                                Text(stringResource(R.string.kids_edit_hub_down))
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.kids_edit_hub_show_in_hub),
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = !hidden,
                                onCheckedChange = { show ->
                                    val h = state.hiddenRoutes.toMutableSet()
                                    if (show) h.remove(route) else h.add(route)
                                    viewModel.setKidsUserSections(state.copy(hiddenRoutes = h))
                                },
                            )
                        }
                        if (KidsPicturedRoutes.isPicturedRoute(route)) {
                            TextButton(onClick = { onOpenPicturedEditor(route) }) {
                                Text(stringResource(R.string.kids_edit_hub_edit_album))
                            }
                        }
                        if (canDelete) {
                            TextButton(
                                onClick = {
                                    val newCustom = state.customHub.filterNot { it.route == route }
                                    val newOrder = ensureHubOrder(state, defaults).filter { it != route }
                                    val newPictured = state.picturedEdits.filterKeys { it != route }
                                    viewModel.setKidsUserSections(
                                        state.copy(
                                            customHub = newCustom,
                                            order = newOrder,
                                            picturedEdits = newPictured,
                                        ),
                                    )
                                },
                            ) {
                                Text(stringResource(R.string.kids_edit_hub_delete_custom))
                            }
                        }
                    }
                }
            }
        }
    }

    if (addOpen) {
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = { Text(stringResource(R.string.kids_edit_add_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(stringResource(R.string.kids_edit_new_album_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newSubtitle,
                        onValueChange = { newSubtitle = it },
                        label = { Text(stringResource(R.string.kids_edit_new_album_subtitle)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newEmoji,
                        onValueChange = { newEmoji = it },
                        label = { Text(stringResource(R.string.kids_edit_new_album_emoji)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newCardStyleToken,
                        onValueChange = { newCardStyleToken = it },
                        label = { Text(stringResource(R.string.kids_edit_card_style_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newTileStyle,
                        onValueChange = { newTileStyle = it },
                        label = { Text(stringResource(R.string.kids_edit_tile_style_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val title = newTitle.trim().ifEmpty { return@TextButton }
                        val route = "kids_custom_${System.currentTimeMillis()}"
                        val entry = KidsCustomHubEntry(
                            route = route,
                            title = title,
                            subtitle = newSubtitle.trim(),
                            cardStyle = newCardStyleToken.trim().ifEmpty { "Pictures" },
                            emojiThumb = newEmoji.trim().ifEmpty { null },
                            tileStyle = newTileStyle.trim().ifEmpty { "square" },
                            items = emptyList(),
                        )
                        val order = ensureHubOrder(state, defaults) + route
                        viewModel.setKidsUserSections(
                            state.copy(
                                customHub = state.customHub + entry,
                                order = order,
                            ),
                        )
                        addOpen = false
                    },
                ) {
                    Text(stringResource(R.string.kids_edit_add_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { addOpen = false }) {
                    Text(stringResource(R.string.travel_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsEditPicturedSectionScreen(
    route: String,
    viewModel: BibleViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.kidsUserSections.collectAsStateWithLifecycle()
    val items = remember(route, state, context) {
        KidsUserSectionsMerge.mergePicturedItems(route, context, state)
    }
    var pendingImgKey by remember { mutableStateOf<String?>(null) }
    var pendingSndKey by remember { mutableStateOf<String?>(null) }
    var addCardOpen by remember { mutableStateOf(false) }
    var addLabel by remember { mutableStateOf("") }
    var addSpeak by remember { mutableStateOf("") }
    var addEmoji by remember { mutableStateOf("🌿") }
    var addFull by remember { mutableStateOf(true) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val key = pendingImgKey ?: return@rememberLauncherForActivityResult
        pendingImgKey = null
        if (uri == null) return@rememberLauncherForActivityResult
        val path = KidsUserMediaStorage.copyUriIntoFilesDir(
            context,
            uri,
            KidsUserMediaStorage.IMAGES_SUBDIR,
            "jpg",
        ) ?: return@rememberLauncherForActivityResult
        val cur = state.picturedEdits[route] ?: KidsPicturedSectionEdits(emptyMap(), emptyList())
        val old = cur.byKey[key]
        val patch = KidsPicturedItemPatch(
            label = old?.label,
            speak = old?.speak,
            emoji = old?.emoji,
            customImagePath = path,
            customSoundPath = old?.customSoundPath,
            clearCustomImage = false,
            clearCustomSound = old?.clearCustomSound ?: false,
            detailFullScreen = old?.detailFullScreen,
        )
        viewModel.setKidsUserSections(
            state.copy(
                picturedEdits = state.picturedEdits + (route to cur.copy(byKey = cur.byKey + (key to patch))),
            ),
        )
    }
    val pickSound = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val key = pendingSndKey ?: return@rememberLauncherForActivityResult
        pendingSndKey = null
        if (uri == null) return@rememberLauncherForActivityResult
        val path = KidsUserMediaStorage.copyUriIntoFilesDir(
            context,
            uri,
            KidsUserMediaStorage.SOUNDS_SUBDIR,
            "mp3",
        ) ?: return@rememberLauncherForActivityResult
        val cur = state.picturedEdits[route] ?: KidsPicturedSectionEdits(emptyMap(), emptyList())
        val old = cur.byKey[key]
        val patch = KidsPicturedItemPatch(
            label = old?.label,
            speak = old?.speak,
            emoji = old?.emoji,
            customImagePath = old?.customImagePath,
            customSoundPath = path,
            clearCustomImage = old?.clearCustomImage ?: false,
            clearCustomSound = false,
            detailFullScreen = old?.detailFullScreen,
        )
        viewModel.setKidsUserSections(
            state.copy(
                picturedEdits = state.picturedEdits + (route to cur.copy(byKey = cur.byKey + (key to patch))),
            ),
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.kids_edit_pictured_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addCardOpen = true }) {
                Text("+")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.itemKey }) { item ->
                val key = item.itemKey
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(item.label, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                pendingImgKey = key
                                pickImage.launch(arrayOf("image/*"))
                            }) {
                                Text(stringResource(R.string.kids_pick_image))
                            }
                            OutlinedButton(onClick = {
                                pendingSndKey = key
                                pickSound.launch(arrayOf("audio/*"))
                            }) {
                                Text(stringResource(R.string.kids_pick_sound))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.kids_detail_fullscreen),
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = item.detailFullScreen,
                                onCheckedChange = { on ->
                                    val cur = state.picturedEdits[route] ?: KidsPicturedSectionEdits(emptyMap(), emptyList())
                                    val old = cur.byKey[key]
                                    val patch = KidsPicturedItemPatch(
                                        label = old?.label,
                                        speak = old?.speak,
                                        emoji = old?.emoji,
                                        customImagePath = old?.customImagePath,
                                        customSoundPath = old?.customSoundPath,
                                        clearCustomImage = old?.clearCustomImage ?: false,
                                        clearCustomSound = old?.clearCustomSound ?: false,
                                        detailFullScreen = on,
                                    )
                                    viewModel.setKidsUserSections(
                                        state.copy(
                                            picturedEdits = state.picturedEdits + (route to cur.copy(byKey = cur.byKey + (key to patch))),
                                        ),
                                    )
                                },
                            )
                        }
                        TextButton(
                            onClick = {
                                val cur = state.picturedEdits[route] ?: KidsPicturedSectionEdits(emptyMap(), emptyList())
                                val old = cur.byKey[key]
                                val patch = KidsPicturedItemPatch(
                                    label = old?.label,
                                    speak = old?.speak,
                                    emoji = old?.emoji,
                                    customImagePath = null,
                                    customSoundPath = null,
                                    clearCustomImage = true,
                                    clearCustomSound = true,
                                    detailFullScreen = old?.detailFullScreen,
                                )
                                viewModel.setKidsUserSections(
                                    state.copy(
                                        picturedEdits = state.picturedEdits + (route to cur.copy(byKey = cur.byKey + (key to patch))),
                                    ),
                                )
                            },
                        ) {
                            Text(stringResource(R.string.kids_reset_custom_media))
                        }
                    }
                }
            }
        }
    }

    if (addCardOpen) {
        AlertDialog(
            onDismissRequest = { addCardOpen = false },
            title = { Text(stringResource(R.string.kids_add_card)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = addLabel,
                        onValueChange = { addLabel = it },
                        label = { Text(stringResource(R.string.kids_add_card_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = addSpeak,
                        onValueChange = { addSpeak = it },
                        label = { Text(stringResource(R.string.kids_add_card_speak)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = addEmoji,
                        onValueChange = { addEmoji = it },
                        label = { Text(stringResource(R.string.kids_edit_new_album_emoji)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.kids_detail_fullscreen), modifier = Modifier.weight(1f))
                        Switch(checked = addFull, onCheckedChange = { addFull = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val label = addLabel.trim().ifEmpty { return@TextButton }
                        val speak = addSpeak.trim().ifEmpty { label }
                        val ik = "user_${System.currentTimeMillis()}"
                        val json = KidsPicturedItemJson(
                            itemKey = ik,
                            label = label,
                            speak = speak,
                            emoji = addEmoji.trim().ifEmpty { "🌿" },
                            drawableName = null,
                            rawSoundName = null,
                            soundPitch = 1f,
                            customImagePath = null,
                            customSoundPath = null,
                            detailFullScreen = addFull,
                        )
                        val cur = state.picturedEdits[route] ?: KidsPicturedSectionEdits(emptyMap(), emptyList())
                        viewModel.setKidsUserSections(
                            state.copy(
                                picturedEdits = state.picturedEdits + (route to cur.copy(added = cur.added + json)),
                            ),
                        )
                        addLabel = ""
                        addSpeak = ""
                        addEmoji = "🌿"
                        addFull = true
                        addCardOpen = false
                    },
                ) {
                    Text(stringResource(R.string.kids_edit_add_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { addCardOpen = false }) {
                    Text(stringResource(R.string.travel_cancel))
                }
            },
        )
    }
}
