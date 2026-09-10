package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contacts
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bible.R
import com.example.bible.data.ContactsRepository
import com.example.bible.data.UserContact
import com.example.bible.data.birthLocalDate
import com.example.bible.data.completedAgeYears
import com.example.bible.data.daysUntilNextBirthday
import com.example.bible.data.formatBirthDateRuFull
import com.example.bible.data.formatMonthDayRu
import com.example.bible.data.formatYearsRussian
import com.example.bible.data.nextBirthdayCalendarDate
import com.example.bible.data.loadImportedPhoneContact
import com.example.bible.data.validateBirthEpochDayInput
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { ContactsRepository(context) }
    var items by remember { mutableStateOf<List<UserContact>>(emptyList()) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<UserContact?>(null) }
    var deleteTarget by remember { mutableStateOf<UserContact?>(null) }

    fun reload() {
        val today = LocalDate.now()
        items =
            repo.load().sortedWith(
                compareBy<UserContact> { it.daysUntilNextBirthday(today) ?: Int.MAX_VALUE }
                    .thenBy { it.fullName.lowercase() },
            )
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
                    editor =
                        UserContact(
                            id = UUID.randomUUID().toString(),
                            fullName = "",
                            phone = "",
                            email = "",
                            notes = "",
                            birthEpochDay = null,
                            latitude = null,
                            longitude = null,
                        )
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.contacts_add))
            }
        },
    ) { padding ->
        val today = LocalDate.now()

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
                        val expanded = expandedId == c.id
                        val birth = c.birthLocalDate()
                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clickable {
                                            expandedId =
                                                if (expandedId == c.id) {
                                                    null
                                                } else {
                                                    c.id
                                                }
                                        }
                                        .padding(end = 8.dp),
                                ) {
                                    Text(
                                        c.fullName.ifBlank { "—" },
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (birth != null) {
                                        val next = c.nextBirthdayCalendarDate(today)
                                        val daysUntil = c.daysUntilNextBirthday(today) ?: 0
                                        val age = c.completedAgeYears(today)
                                        Text(
                                            stringResource(
                                                R.string.contacts_birthday_born_label,
                                                birth.formatBirthDateRuFull(),
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                        Text(
                                            stringResource(
                                                R.string.contacts_birthday_age_label,
                                                formatYearsRussian(age ?: 0),
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                        next?.let { n ->
                                            val countdown =
                                                when (daysUntil) {
                                                    0 -> stringResource(R.string.contacts_birthday_countdown_today)
                                                    else ->
                                                        pluralStringResource(
                                                            R.plurals.contacts_birth_day_countdown,
                                                            daysUntil,
                                                            daysUntil,
                                                        )
                                                }
                                            Text(
                                                stringResource(
                                                    R.string.contacts_birthday_next_label,
                                                    n.formatMonthDayRu(),
                                                    countdown,
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(top = 2.dp),
                                            )
                                        }
                                    } else {
                                        Text(
                                            stringResource(R.string.contacts_no_birthday_hint),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                            modifier = Modifier.padding(top = 6.dp),
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
                                    AnimatedVisibility(visible = expanded) {
                                        Column(Modifier.padding(top = 10.dp)) {
                                            if (c.phone.isNotBlank()) {
                                                Text(
                                                    stringResource(R.string.contacts_card_expand_phone, c.phone),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                                Spacer(Modifier.height(6.dp))
                                            }
                                            if (c.email.isNotBlank()) {
                                                Text(
                                                    stringResource(R.string.contacts_card_expand_email, c.email),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Spacer(Modifier.height(6.dp))
                                            }
                                            if (c.notes.isNotBlank()) {
                                                Text(
                                                    stringResource(R.string.contacts_card_expand_notes_title),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Text(
                                                    c.notes,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            if (c.phone.isBlank() && c.email.isBlank() && c.notes.isBlank()) {
                                                Text(
                                                    stringResource(R.string.contacts_expand_all_empty),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
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
                        if (expandedId == del.id) expandedId = null
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
    val ctx = LocalContext.current
    val todayYear = LocalDate.now().year
    val birthInitial =
        remember(initial.id) {
            initial.birthEpochDay?.let { ed ->
                val raw = LocalDate.ofEpochDay(ed)
                val maxD = YearMonth.of(raw.year, raw.monthValue).lengthOfMonth()
                LocalDate.of(raw.year, raw.monthValue, raw.dayOfMonth.coerceIn(1, maxD))
            }
        }
    var fullName by remember(initial.id) { mutableStateOf(initial.fullName) }
    var phone by remember(initial.id) { mutableStateOf(initial.phone) }
    var email by remember(initial.id) { mutableStateOf(initial.email) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes) }
    var pickYear by remember(initial.id) {
        mutableIntStateOf(birthInitial?.year ?: 1990)
    }
    var pickMonth by remember(initial.id) {
        mutableIntStateOf(birthInitial?.monthValue ?: 1)
    }
    var pickDay by remember(initial.id) {
        mutableIntStateOf(birthInitial?.dayOfMonth ?: 1)
    }
    var manualBirthText by remember(initial.id) {
        mutableStateOf(birthInitial?.toString().orEmpty())
    }
    var manualBirthFocused by remember(initial.id) { mutableStateOf(false) }
    LaunchedEffect(pickYear, pickMonth, pickDay, manualBirthFocused) {
        if (manualBirthFocused) return@LaunchedEffect
        val ym = YearMonth.of(pickYear, pickMonth.coerceIn(1, 12))
        val clamped = pickDay.coerceIn(1, ym.lengthOfMonth())
        if (clamped != pickDay) {
            pickDay = clamped
            return@LaunchedEffect
        }
        val line = LocalDate.of(pickYear, ym.monthValue, clamped).toString()
        if (manualBirthText != line) manualBirthText = line
    }
    var latText by remember(initial.id) {
        mutableStateOf(initial.latitude?.let { String.format(java.util.Locale.US, "%.6f", it) }.orEmpty())
    }
    var lonText by remember(initial.id) {
        mutableStateOf(initial.longitude?.let { String.format(java.util.Locale.US, "%.6f", it) }.orEmpty())
    }

    val pickContactLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickContact(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val imported =
                runCatching { ctx.contentResolver.loadImportedPhoneContact(uri) }.getOrElse {
                    Toast.makeText(ctx, ctx.getString(R.string.contacts_import_read_failed), Toast.LENGTH_SHORT).show()
                    return@rememberLauncherForActivityResult
                }
            if (imported == null) {
                Toast.makeText(ctx, ctx.getString(R.string.contacts_import_pick_failed), Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            fullName = imported.fullName
            phone = imported.phone
            email = imported.email
            imported.birthEpochDay?.let { epoch ->
                val ld = LocalDate.ofEpochDay(epoch)
                manualBirthFocused = false
                pickYear = ld.year
                pickMonth = ld.monthValue
                pickDay = ld.dayOfMonth
                manualBirthText = ld.toString()
            }
        }

    val readContactsLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { ok ->
            if (ok) {
                pickContactLauncher.launch(null)
            } else {
                Toast.makeText(ctx, ctx.getString(R.string.contacts_permission_contacts_denied), Toast.LENGTH_LONG)
                    .show()
            }
        }

    fun openDeviceContactPicker() {
        when {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED -> pickContactLauncher.launch(null)
            else -> readContactsLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
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
                OutlinedButton(
                    onClick = { openDeviceContactPicker() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.Contacts,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.contacts_import_from_phone))
                }
                Spacer(Modifier.height(12.dp))
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
                Text(
                    stringResource(R.string.contacts_field_birth),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.contacts_birth_picker_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                ContactBirthWheelRow(
                    year = pickYear,
                    month = pickMonth,
                    day = pickDay,
                    maxYear = todayYear,
                    onYmdChange = { ny, nm, nd ->
                        pickYear = ny
                        pickMonth = nm
                        pickDay = nd
                    },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualBirthText,
                    onValueChange = { manualBirthText = it },
                    label = { Text(stringResource(R.string.contacts_field_birth_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { st ->
                            val nowFocused = st.isFocused
                            if (manualBirthFocused && !nowFocused) {
                                val trimmed = manualBirthText.trim()
                                when {
                                    trimmed.isEmpty() -> { }
                                    else -> {
                                        val parsed = runCatching { LocalDate.parse(trimmed) }.getOrNull()
                                        val today = LocalDate.now()
                                        if (parsed != null && parsed.year >= 1900 && !parsed.isAfter(today)) {
                                            pickYear = parsed.year
                                            pickMonth = parsed.monthValue
                                            pickDay = parsed.dayOfMonth
                                            if (manualBirthText != trimmed) {
                                                manualBirthText = trimmed
                                            }
                                        } else {
                                            Toast.makeText(
                                                ctx,
                                                ctx.getString(R.string.contacts_toast_bad_birth_date),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            val ym = YearMonth.of(pickYear, pickMonth.coerceIn(1, 12))
                                            val dd = pickDay.coerceIn(1, ym.lengthOfMonth())
                                            manualBirthText = LocalDate.of(pickYear, ym.monthValue, dd).toString()
                                        }
                                    }
                                }
                            }
                            manualBirthFocused = nowFocused
                        },
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
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                    val trimmedBirth = manualBirthText.trim()
                    val birthEpoch =
                        if (trimmedBirth.isBlank()) {
                            null
                        } else {
                            val parsed = validateBirthEpochDayInput(trimmedBirth)
                            if (parsed == null) {
                                Toast.makeText(ctx, ctx.getString(R.string.contacts_toast_bad_birth_date), Toast.LENGTH_SHORT)
                                    .show()
                                return@TextButton
                            }
                            parsed
                        }
                    val lat = latText.trim().toDoubleOrNull()
                    val lon = lonText.trim().toDoubleOrNull()
                    val both =
                        lat != null && lon != null &&
                            lat.isFinite() && lon.isFinite() &&
                            lat in -90.0..90.0 && lon in -180.0..180.0
                    onSave(
                        initial.copy(
                            fullName = fullName.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            notes = notes.trim(),
                            birthEpochDay = birthEpoch,
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
