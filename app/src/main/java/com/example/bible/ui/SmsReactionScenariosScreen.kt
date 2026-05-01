package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.SMS_REACTION_DELAY_MAX_MS
import com.example.bible.data.SmsReactionAction
import com.example.bible.data.SmsReactionActionKind
import com.example.bible.data.SmsReactionRepository
import com.example.bible.data.SmsReactionScenario
import com.example.bible.data.formatSmsReactionDelayHHMM
import com.example.bible.data.isCompleteSmsReactionDelayHHMM
import com.example.bible.data.normalizeSmsDigits
import com.example.bible.data.parseSmsReactionDelayHHMM
import com.example.bible.data.sanitizeSmsReactionDelayHHMMInput
import java.util.UUID

private data class SimChoice(val subscriptionId: Int, val label: String)

private fun buildOutboundSimChoices(context: Context): List<SimChoice> {
    val incoming = context.getString(R.string.experiment_sms_sim_use_incoming)
    val list = mutableListOf(SimChoice(SubscriptionManager.INVALID_SUBSCRIPTION_ID, incoming))
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return list
    }
    val sm = context.getSystemService(SubscriptionManager::class.java) ?: return list
    val infos =
        runCatching { sm.activeSubscriptionInfoList }.getOrNull().orEmpty().sortedBy { it.simSlotIndex }
    for (info in infos) {
        val slot = info.simSlotIndex + 1
        val carrier = info.carrierName?.toString()?.trim().orEmpty()
        val carrierLabel =
            carrier.ifBlank { context.getString(R.string.experiment_sms_sim_carrier_unknown) }
        val label = context.getString(R.string.experiment_sms_sim_slot_fmt, slot, carrierLabel)
        list.add(SimChoice(info.subscriptionId, label))
    }
    return list
}

private data class ReactionEditorState(
    val id: String,
    val title: String,
    val enabled: Boolean,
    val outboundSmsSubscriptionId: Int,
    val outboundCallSubscriptionId: Int,
    val senderFieldText: String,
    val phrasesFieldText: String,
    val matchAllPhrases: Boolean,
    val actions: List<SmsReactionAction>,
    val pendingAudioActionIndex: Int? = null,
    val pendingImageActionIndex: Int? = null,
) {
    constructor(s: SmsReactionScenario) : this(
        id = s.id,
        title = s.title,
        enabled = s.enabled,
        outboundSmsSubscriptionId = s.outboundSmsSubscriptionId,
        outboundCallSubscriptionId = s.outboundCallSubscriptionId,
        senderFieldText = s.senderDigitPatterns.joinToString("\n"),
        phrasesFieldText = s.bodyPhrases.joinToString("\n"),
        matchAllPhrases = s.matchAllPhrases,
        actions = s.actions.toList(),
    )

    fun toScenario(): SmsReactionScenario {
        val senders = senderFieldText
            .split('\n', ',', ';')
            .map { it.normalizeSmsDigits() }
            .filter { it.isNotEmpty() }
            .distinct()
        val phrases = phrasesFieldText.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return SmsReactionScenario(
            id,
            title,
            enabled,
            senders,
            phrases,
            matchAllPhrases,
            outboundSmsSubscriptionId,
            outboundCallSubscriptionId,
            actions,
        )
    }

    fun removeActionAt(index: Int): ReactionEditorState =
        copy(actions = actions.filterIndexed { i, _ -> i != index })

    fun copyActionParam(index: Int, param: String): ReactionEditorState {
        if (index !in actions.indices) return this
        val next = actions.toMutableList()
        next[index] = next[index].copy(param = param)
        return copy(actions = next)
    }

    fun copyActionDelayBeforeNext(index: Int, delayMs: Long): ReactionEditorState {
        if (index !in actions.indices) return this
        val next = actions.toMutableList()
        next[index] = next[index].copy(delayBeforeNextMs = delayMs.coerceIn(0L, SMS_REACTION_DELAY_MAX_MS))
        return copy(actions = next)
    }

    fun withPendingAudioSlot(index: Int): ReactionEditorState =
        copy(pendingAudioActionIndex = index, pendingImageActionIndex = null)

    fun withPendingImageSlot(index: Int): ReactionEditorState =
        copy(pendingImageActionIndex = index, pendingAudioActionIndex = null)

    fun applyPickedAudio(uri: String): ReactionEditorState {
        val idx = pendingAudioActionIndex ?: return this
        val next = actions.toMutableList()
        if (idx in next.indices) next[idx] = next[idx].copy(param = uri)
        return copy(actions = next, pendingAudioActionIndex = null)
    }

    fun applyPickedImage(uri: String): ReactionEditorState {
        val idx = pendingImageActionIndex ?: return this
        val next = actions.toMutableList()
        if (idx in next.indices) next[idx] = next[idx].copy(param = uri)
        return copy(actions = next, pendingImageActionIndex = null)
    }
}

private fun defaultParam(kind: SmsReactionActionKind): String =
    when (kind) {
        SmsReactionActionKind.FLASHLIGHT_SECONDS -> "30"
        SmsReactionActionKind.VIBRATE_CONTINUOUS_MS -> "8000"
        SmsReactionActionKind.VIBRATE_PULSE_LOOP_MS -> "15000"
        else -> ""
    }

private fun reactionSummary(ctx: Context, s: SmsReactionScenario): String =
    ctx.getString(R.string.experiment_sms_reaction_card_summary_fmt, s.senderDigitPatterns.size, s.bodyPhrases.size, s.actions.size)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsReactionScenariosScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { SmsReactionRepository(context) }
    var scenarios by remember { mutableStateOf(repo.load()) }
    fun persist(next: List<SmsReactionScenario>) {
        scenarios = next
        repo.save(next)
    }

    var editorState by remember { mutableStateOf<ReactionEditorState?>(null) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        editorState = editorState?.applyPickedAudio(uri.toString())
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        editorState = editorState?.applyPickedImage(uri.toString())
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.experiment_sms_reactions_title)) },
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
                    editorState = ReactionEditorState(
                        SmsReactionScenario(
                            id = UUID.randomUUID().toString(),
                            title = context.getString(R.string.experiment_sms_reaction_new_title),
                            enabled = true,
                        ),
                    )
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.experiment_sms_reaction_add_cd))
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
                text = stringResource(R.string.experiment_sms_reactions_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(scenarios, key = { it.id }) { s ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                        ),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        reactionSummary(context, s),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                Switch(
                                    checked = s.enabled,
                                    onCheckedChange = { checked ->
                                        persist(scenarios.map { if (it.id == s.id) it.copy(enabled = checked) else it })
                                    },
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { editorState = ReactionEditorState(s) }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.experiment_sms_reaction_edit_cd))
                                }
                                IconButton(onClick = { deleteConfirmId = s.id }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.travel_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteConfirmId?.let { did ->
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text(stringResource(R.string.experiment_sms_reaction_delete_title)) },
            text = { Text(stringResource(R.string.experiment_sms_reaction_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        persist(scenarios.filter { it.id != did })
                        deleteConfirmId = null
                    },
                ) {
                    Text(stringResource(R.string.travel_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) {
                    Text(stringResource(R.string.travel_cancel))
                }
            },
        )
    }

    editorState?.let {
        AlertDialog(
            onDismissRequest = { editorState = null },
            title = { Text(stringResource(R.string.experiment_sms_reaction_editor_title)) },
            dismissButton = {
                TextButton(onClick = { editorState = null }) {
                    Text(stringResource(R.string.travel_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleaned = editorState?.toScenario() ?: return@TextButton
                        if (cleaned.title.isBlank()) {
                            editorState = null
                            return@TextButton
                        }
                        val next = scenarios.filter { it.id != cleaned.id } + cleaned
                        persist(next.sortedBy { it.title.lowercase() })
                        editorState = null
                    },
                ) {
                    Text(stringResource(R.string.travel_save))
                }
            },
            text = {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = editorState!!.title,
                        onValueChange = { nv -> editorState = editorState!!.copy(title = nv) },
                        label = { Text(stringResource(R.string.experiment_sms_reaction_field_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.experiment_sms_reaction_enabled))
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = editorState!!.enabled,
                            onCheckedChange = { v -> editorState = editorState!!.copy(enabled = v) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    val phoneOk =
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                            PackageManager.PERMISSION_GRANTED
                    val simChoices = remember(context, phoneOk) { buildOutboundSimChoices(context) }
                    Text(
                        stringResource(R.string.experiment_sms_reaction_outbound_sim_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    OutboundSimDropdown(
                        sectionTitle = stringResource(R.string.experiment_sms_reaction_outbound_sim_sms),
                        choices = simChoices,
                        selectedSubscriptionId = editorState!!.outboundSmsSubscriptionId,
                        onSelect = { sid -> editorState = editorState!!.copy(outboundSmsSubscriptionId = sid) },
                    )
                    Spacer(Modifier.height(10.dp))
                    OutboundSimDropdown(
                        sectionTitle = stringResource(R.string.experiment_sms_reaction_outbound_sim_call),
                        choices = simChoices,
                        selectedSubscriptionId = editorState!!.outboundCallSubscriptionId,
                        onSelect = { sid -> editorState = editorState!!.copy(outboundCallSubscriptionId = sid) },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editorState!!.senderFieldText,
                        onValueChange = { nv -> editorState = editorState!!.copy(senderFieldText = nv) },
                        label = { Text(stringResource(R.string.experiment_sms_reaction_field_senders)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        supportingText = { Text(stringResource(R.string.experiment_sms_reaction_field_senders_hint)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editorState!!.phrasesFieldText,
                        onValueChange = { nv -> editorState = editorState!!.copy(phrasesFieldText = nv) },
                        label = { Text(stringResource(R.string.experiment_sms_reaction_field_phrases)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 6,
                        supportingText = { Text(stringResource(R.string.experiment_sms_reaction_field_phrases_hint)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.experiment_sms_reaction_phrase_logic), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .weight(1f)
                                .selectable(
                                    selected = !editorState!!.matchAllPhrases,
                                    onClick = { editorState = editorState!!.copy(matchAllPhrases = false) },
                                    role = Role.RadioButton,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = !editorState!!.matchAllPhrases, onClick = null)
                            Text(stringResource(R.string.experiment_sms_reaction_match_any))
                        }
                        Row(
                            Modifier
                                .weight(1f)
                                .selectable(
                                    selected = editorState!!.matchAllPhrases,
                                    onClick = { editorState = editorState!!.copy(matchAllPhrases = true) },
                                    role = Role.RadioButton,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = editorState!!.matchAllPhrases, onClick = null)
                            Text(stringResource(R.string.experiment_sms_reaction_match_all))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(stringResource(R.string.experiment_sms_reaction_actions_header), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    editorState!!.actions.forEachIndexed { index, action ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        actionKindLabel(action.kind),
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { editorState = editorState!!.removeActionAt(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.travel_delete))
                                    }
                                }
                                OutlinedTextField(
                                    value = action.param,
                                    onValueChange = { nv -> editorState = editorState!!.copyActionParam(index, nv) },
                                    label = { Text(actionParamHint(action.kind)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = action.kind != SmsReactionActionKind.SEND_REPLY_SMS,
                                    maxLines = if (action.kind == SmsReactionActionKind.SEND_REPLY_SMS) 4 else 1,
                                )
                                when (action.kind) {
                                    SmsReactionActionKind.PLAY_MEDIA_URI -> {
                                        TextButton(
                                            onClick = {
                                                editorState = editorState!!.withPendingAudioSlot(index)
                                                pickAudioLauncher.launch(arrayOf("audio/*", "application/ogg"))
                                            },
                                        ) {
                                            Text(stringResource(R.string.experiment_sms_reaction_pick_audio))
                                        }
                                    }
                                    SmsReactionActionKind.OPEN_IMAGE_URI -> {
                                        TextButton(
                                            onClick = {
                                                editorState = editorState!!.withPendingImageSlot(index)
                                                pickImageLauncher.launch(arrayOf("image/*"))
                                            },
                                        ) {
                                            Text(stringResource(R.string.experiment_sms_reaction_pick_image))
                                        }
                                    }
                                    else -> Unit
                                }
                                when (action.kind) {
                                    SmsReactionActionKind.SEND_REPLY_SMS -> {
                                        Spacer(Modifier.height(8.dp))
                                        OutboundSimDropdown(
                                            sectionTitle = stringResource(R.string.experiment_sms_reaction_outbound_sim_sms),
                                            choices = simChoices,
                                            selectedSubscriptionId = editorState!!.outboundSmsSubscriptionId,
                                            onSelect = { sid ->
                                                editorState = editorState!!.copy(outboundSmsSubscriptionId = sid)
                                            },
                                        )
                                    }
                                    SmsReactionActionKind.CALLBACK_SENDER,
                                    SmsReactionActionKind.CALLBACK_FIXED_NUMBER -> {
                                        Spacer(Modifier.height(8.dp))
                                        OutboundSimDropdown(
                                            sectionTitle = stringResource(R.string.experiment_sms_reaction_outbound_sim_call),
                                            choices = simChoices,
                                            selectedSubscriptionId = editorState!!.outboundCallSubscriptionId,
                                            onSelect = { sid ->
                                                editorState = editorState!!.copy(outboundCallSubscriptionId = sid)
                                            },
                                        )
                                    }
                                    else -> Unit
                                }
                                if (index < editorState!!.actions.lastIndex) {
                                    Spacer(Modifier.height(8.dp))
                                    val storedDelay = action.delayBeforeNextMs
                                    var delayDraft by remember(index, storedDelay) {
                                        mutableStateOf(formatSmsReactionDelayHHMM(storedDelay))
                                    }
                                    LaunchedEffect(storedDelay, index) {
                                        delayDraft = formatSmsReactionDelayHHMM(storedDelay)
                                    }
                                    OutlinedTextField(
                                        value = delayDraft,
                                        onValueChange = { nv ->
                                            val sanitized = sanitizeSmsReactionDelayHHMMInput(nv)
                                            delayDraft = sanitized
                                            when {
                                                sanitized.isBlank() ->
                                                    editorState = editorState!!.copyActionDelayBeforeNext(index, 0L)
                                                isCompleteSmsReactionDelayHHMM(sanitized) ->
                                                    editorState = editorState!!.copyActionDelayBeforeNext(
                                                        index,
                                                        parseSmsReactionDelayHHMM(sanitized),
                                                    )
                                            }
                                        },
                                        label = {
                                            Text(stringResource(R.string.experiment_sms_action_delay_before_next_label))
                                        },
                                        supportingText = {
                                            Text(stringResource(R.string.experiment_sms_action_delay_before_next_hint))
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions.Default.copy(
                                            keyboardType = KeyboardType.Ascii,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    ActionTypeDropdown(
                        onChosen = { kind ->
                            editorState = editorState!!.copy(
                                actions = editorState!!.actions + SmsReactionAction(kind, defaultParam(kind)),
                            )
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun OutboundSimDropdown(
    sectionTitle: String,
    choices: List<SimChoice>,
    selectedSubscriptionId: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel =
        choices.firstOrNull { it.subscriptionId == selectedSubscriptionId }?.label
            ?: choices.firstOrNull()?.label.orEmpty()
    Column(Modifier.fillMaxWidth()) {
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
            ) {
                choices.forEach { ch ->
                    DropdownMenuItem(
                        text = { Text(ch.label) },
                        onClick = {
                            expanded = false
                            onSelect(ch.subscriptionId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionTypeDropdown(onChosen: (SmsReactionActionKind) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.experiment_sms_reaction_add_action))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SmsReactionActionKind.entries.forEach { k ->
                DropdownMenuItem(
                    text = { Text(actionKindLabel(k)) },
                    onClick = {
                        expanded = false
                        onChosen(k)
                    },
                )
            }
        }
    }
}

@Composable
private fun actionKindLabel(kind: SmsReactionActionKind): String = stringResource(
    when (kind) {
        SmsReactionActionKind.FLASHLIGHT_SECONDS -> R.string.experiment_sms_action_flashlight
        SmsReactionActionKind.PLAY_MEDIA_URI -> R.string.experiment_sms_action_play_media
        SmsReactionActionKind.OPEN_IMAGE_URI -> R.string.experiment_sms_action_open_image
        SmsReactionActionKind.CALLBACK_SENDER -> R.string.experiment_sms_action_callback_sender
        SmsReactionActionKind.CALLBACK_FIXED_NUMBER -> R.string.experiment_sms_action_callback_fixed
        SmsReactionActionKind.VIBRATE_CONTINUOUS_MS -> R.string.experiment_sms_action_vibrate_continuous
        SmsReactionActionKind.VIBRATE_PULSE_LOOP_MS -> R.string.experiment_sms_action_vibrate_pulse
        SmsReactionActionKind.SEND_REPLY_SMS -> R.string.experiment_sms_action_reply_sms
    },
)

@Composable
private fun actionParamHint(kind: SmsReactionActionKind): String = stringResource(
    when (kind) {
        SmsReactionActionKind.FLASHLIGHT_SECONDS -> R.string.experiment_sms_param_flashlight
        SmsReactionActionKind.PLAY_MEDIA_URI -> R.string.experiment_sms_param_uri_audio
        SmsReactionActionKind.OPEN_IMAGE_URI -> R.string.experiment_sms_param_uri_image
        SmsReactionActionKind.CALLBACK_SENDER -> R.string.experiment_sms_param_unused
        SmsReactionActionKind.CALLBACK_FIXED_NUMBER -> R.string.experiment_sms_param_phone_digits
        SmsReactionActionKind.VIBRATE_CONTINUOUS_MS -> R.string.experiment_sms_param_vibrate_ms
        SmsReactionActionKind.VIBRATE_PULSE_LOOP_MS -> R.string.experiment_sms_param_vibrate_pulse_ms
        SmsReactionActionKind.SEND_REPLY_SMS -> R.string.experiment_sms_param_reply_text
    },
)
