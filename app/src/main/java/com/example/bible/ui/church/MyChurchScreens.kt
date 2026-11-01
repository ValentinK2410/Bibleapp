package com.example.bible.ui.church

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.example.bible.ui.theme.OutlinedTextField
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.calendarBirthdayInYear
import com.example.bible.data.church.ChurchParticipant
import com.example.bible.data.church.birthLocalDate
import com.example.bible.data.formatBirthDateRuFull
import com.example.bible.data.formatMonthDayRu
import com.example.bible.data.formatYearsRussian
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyChurchHubScreen(
    onBack: () -> Unit,
    onOpenParticipants: () -> Unit,
    onOpenAccounting: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenProtocols: () -> Unit,
    onOpenCertificates: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.church_my_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ChurchHubCard(
                    title = stringResource(R.string.church_section_participants),
                    subtitle = stringResource(R.string.church_section_participants_sub),
                    icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                    onClick = onOpenParticipants,
                )
            }
            item {
                ChurchHubCard(
                    title = stringResource(R.string.church_section_accounting),
                    subtitle = stringResource(R.string.church_section_placeholder_sub),
                    icon = { Icon(Icons.Filled.RequestQuote, contentDescription = null) },
                    onClick = onOpenAccounting,
                )
            }
            item {
                ChurchHubCard(
                    title = stringResource(R.string.church_section_orders),
                    subtitle = stringResource(R.string.church_section_placeholder_sub),
                    icon = { Icon(Icons.Filled.Description, contentDescription = null) },
                    onClick = onOpenOrders,
                )
            }
            item {
                ChurchHubCard(
                    title = stringResource(R.string.church_section_protocols),
                    subtitle = stringResource(R.string.church_section_placeholder_sub),
                    icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                    onClick = onOpenProtocols,
                )
            }
            item {
                ChurchHubCard(
                    title = stringResource(R.string.church_section_certificates),
                    subtitle = stringResource(R.string.church_section_placeholder_sub),
                    icon = { Icon(Icons.Filled.Church, contentDescription = null) },
                    onClick = onOpenCertificates,
                )
            }
        }
    }
}

@Composable
private fun ChurchHubCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                icon()
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChurchParticipantsScreen(
    viewModel: ChurchViewModel,
    onBack: () -> Unit,
    onOpenParticipant: (String) -> Unit,
) {
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.church_participants_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenParticipant("new") }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.church_add_participant))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ChurchBirthdayBlock(participants, onOpenParticipant) }
            items(participants, key = { it.id }) { p ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenParticipant(p.id) },
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            p.displayName().ifBlank { stringResource(R.string.church_participant_unnamed) },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (p.position.isNotBlank()) {
                            Text(
                                p.position,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        p.birthLocalDate?.let { birth ->
                            Text(
                                birth.formatBirthDateRuFull(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Дни рождения текущего месяца: кто справляет, сколько лет исполняется и сколько дней осталось. */
@Composable
private fun ChurchBirthdayBlock(
    participants: List<ChurchParticipant>,
    onOpenParticipant: (String) -> Unit,
) {
    val today = LocalDate.now()
    val thisMonth = participants.mapNotNull { p -> p.birthLocalDate?.let { p to it } }
        .filter { (_, birth) -> birth.monthValue == today.monthValue }
        .sortedBy { (_, birth) -> birth.dayOfMonth }
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Cake, contentDescription = null, tint = onContainer)
                Text(
                    stringResource(R.string.church_birthdays_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = onContainer,
                )
            }
            if (thisMonth.isEmpty()) {
                Text(
                    stringResource(R.string.church_birthdays_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                )
                Text(
                    stringResource(R.string.church_birthdays_empty_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer.copy(alpha = 0.8f),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    thisMonth.forEach { (person, birth) ->
                        val anniversary = calendarBirthdayInYear(birth.monthValue, birth.dayOfMonth, today.year)
                        val daysLeft = ChronoUnit.DAYS.between(today, anniversary).toInt()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenParticipant(person.id) },
                        ) {
                            Text(
                                person.displayName().ifBlank {
                                    stringResource(R.string.church_participant_unnamed)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = onContainer,
                            )
                            Text(
                                stringResource(
                                    R.string.church_birthday_age_fmt,
                                    birth.formatMonthDayRu(),
                                    formatYearsRussian(anniversary.year - birth.year),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = onContainer.copy(alpha = 0.85f),
                            )
                            Text(
                                when {
                                    daysLeft == 0 -> stringResource(R.string.church_birthday_today)
                                    daysLeft < 0 -> stringResource(R.string.church_birthday_passed)
                                    else -> pluralStringResource(
                                        R.plurals.church_birthday_countdown,
                                        daysLeft,
                                        daysLeft,
                                    )
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChurchMemberEditScreen(
    participantId: String,
    viewModel: ChurchViewModel,
    onBack: () -> Unit,
) {
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    val isNew = participantId == "new"
    val existing = remember(participantId, participants) {
        participants.find { it.id == participantId }
    }

    var lastName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var patronymic by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }

    LaunchedEffect(participantId, existing) {
        if (!isNew && existing != null) {
            lastName = existing.lastName
            firstName = existing.firstName
            patronymic = existing.patronymic
            position = existing.position
            birthDate = existing.birthDate
        }
        if (isNew) {
            lastName = ""
            firstName = ""
            patronymic = ""
            position = ""
            birthDate = ""
        }
    }

    val context = LocalContext.current
    val birth = remember(birthDate) { runCatching { LocalDate.parse(birthDate) }.getOrNull() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (isNew) {
                            stringResource(R.string.church_add_participant)
                        } else {
                            stringResource(R.string.church_edit_participant)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text(stringResource(R.string.church_field_last_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text(stringResource(R.string.church_field_first_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = patronymic,
                onValueChange = { patronymic = it },
                label = { Text(stringResource(R.string.church_field_patronymic)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = position,
                onValueChange = { position = it },
                label = { Text(stringResource(R.string.church_field_position)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val initial = birth ?: LocalDate.now()
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                birthDate = LocalDate.of(year, month + 1, dayOfMonth).toString()
                            },
                            initial.year,
                            initial.monthValue - 1,
                            initial.dayOfMonth,
                        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Filled.Cake,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        birth?.formatBirthDateRuFull()
                            ?: stringResource(R.string.church_birth_date_not_set),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (birth != null) {
                    IconButton(onClick = { birthDate = "" }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.church_birth_date_clear),
                        )
                    }
                }
            }
            TextButton(
                onClick = {
                    val id = if (isNew) UUID.randomUUID().toString() else participantId
                    viewModel.upsertParticipant(
                        ChurchParticipant(
                            id = id,
                            lastName = lastName.trim(),
                            firstName = firstName.trim(),
                            patronymic = patronymic.trim(),
                            position = position.trim(),
                            birthDate = birthDate,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        ),
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.church_save))
            }
            if (!isNew) {
                TextButton(
                    onClick = {
                        viewModel.deleteParticipant(participantId)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.church_delete_participant),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChurchPlaceholderScreen(
    title: String,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Text(
            stringResource(R.string.church_placeholder_hint),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
        )
    }
}
