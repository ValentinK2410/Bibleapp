package com.example.bible.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.ContactsRepository
import com.example.bible.data.UserContact
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { ContactsRepository(context) }
    var items by remember { mutableStateOf<List<UserContact>>(emptyList()) }
    var editor by remember { mutableStateOf<UserContact?>(null) }
    var deleteTarget by remember { mutableStateOf<UserContact?>(null) }

    fun reload() {
        items = repo.load().sortedWith(compareBy({ it.fullName.lowercase() }, { it.phone }))
    }

    LaunchedEffect(Unit) {
        reload()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.contacts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editor = UserContact(
                        id = UUID.randomUUID().toString(),
                        fullName = "",
                        phone = "",
                        email = "",
                        notes = "",
                        latitude = null,
                        longitude = null,
                    )
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.contacts_add))
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                stringResource(R.string.contacts_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (items.isEmpty()) {
                Text(
                    stringResource(R.string.contacts_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { c ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    c.fullName.ifBlank { "—" },
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (c.phone.isNotBlank()) {
                                    Text(
                                        c.phone,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                if (c.email.isNotBlank()) {
                                    Text(
                                        c.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (c.notes.isNotBlank()) {
                                    Text(
                                        c.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                if (c.hasCoordinates()) {
                                    Text(
                                        stringResource(
                                            R.string.contacts_coords_fmt,
                                            c.latitude!!,
                                            c.longitude!!,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(onClick = { deleteTarget = c }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.contacts_delete),
                                        )
                                    }
                                    IconButton(onClick = { editor = c }) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.contacts_edit),
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

    val edit = editor
    if (edit != null) {
        ContactEditorDialog(
            initial = edit,
            onDismiss = { editor = null },
            onSave = { saved ->
                val list = repo.load().filter { it.id != saved.id } + saved
                repo.save(list)
                reload()
                editor = null
            },
        )
    }

    val del = deleteTarget
    if (del != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.contacts_delete_title)) },
            text = { Text(stringResource(R.string.contacts_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        repo.save(repo.load().filter { it.id != del.id })
                        reload()
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.contacts_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.back))
                }
            },
        )
    }
}

@Composable
private fun ContactEditorDialog(
    initial: UserContact,
    onDismiss: () -> Unit,
    onSave: (UserContact) -> Unit,
) {
    var fullName by remember(initial.id) { mutableStateOf(initial.fullName) }
    var phone by remember(initial.id) { mutableStateOf(initial.phone) }
    var email by remember(initial.id) { mutableStateOf(initial.email) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes) }
    var latText by remember(initial.id) {
        mutableStateOf(initial.latitude?.let { String.format(java.util.Locale.US, "%.6f", it) }.orEmpty())
    }
    var lonText by remember(initial.id) {
        mutableStateOf(initial.longitude?.let { String.format(java.util.Locale.US, "%.6f", it) }.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial.fullName.isBlank() && initial.phone.isBlank()) {
                    stringResource(R.string.contacts_editor_new)
                } else {
                    stringResource(R.string.contacts_editor_edit)
                },
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text(stringResource(R.string.contacts_field_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.contacts_field_phone)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.contacts_field_email)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.contacts_field_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.contacts_coords_optional),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text(stringResource(R.string.contacts_field_lat)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = lonText,
                        onValueChange = { lonText = it },
                        label = { Text(stringResource(R.string.contacts_field_lon)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val lat = latText.trim().toDoubleOrNull()
                    val lon = lonText.trim().toDoubleOrNull()
                    val both = lat != null && lon != null &&
                        lat.isFinite() && lon.isFinite() &&
                        lat in -90.0..90.0 && lon in -180.0..180.0
                    onSave(
                        initial.copy(
                            fullName = fullName.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            notes = notes.trim(),
                            latitude = if (both) lat else null,
                            longitude = if (both) lon else null,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.contacts_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.back))
            }
        },
    )
}
