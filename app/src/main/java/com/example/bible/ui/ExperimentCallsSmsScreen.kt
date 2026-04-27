package com.example.bible.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bible.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentCallsSmsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var phone by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val phoneLatest by rememberUpdatedState(phone)
    val messageLatest by rememberUpdatedState(message)

    var simSlots by remember { mutableStateOf<List<SimSlot>>(emptyList()) }
    var selectedSimIndex by remember { mutableIntStateOf(0) }
    var simMenuOpen by remember { mutableStateOf(false) }

    val systemDefaultLabel = stringResource(R.string.experiment_calls_sim_system_default)
    val options = remember(simSlots, systemDefaultLabel) {
        if (simSlots.isEmpty()) {
            listOf(
                SimSlot(
                    subscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                    label = systemDefaultLabel,
                    phoneAccountHandle = null,
                ),
            )
        } else {
            simSlots
        }
    }

    val optionsLatest by rememberUpdatedState(options)
    val selectedSimLatest by rememberUpdatedState(selectedSimIndex)

    LaunchedEffect(options.size) {
        if (selectedSimIndex >= options.size) {
            selectedSimIndex = 0
        }
    }

    val readPhoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            simSlots = context.loadSimSlots()
        }
    }

    LaunchedEffect(Unit) {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED -> {
                simSlots = context.loadSimSlots()
            }
            else -> readPhoneLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    fun slotForAction(): SimSlot {
        val list = optionsLatest
        val last = (list.size - 1).coerceAtLeast(0)
        val i = selectedSimLatest.coerceIn(0, last)
        return list[i]
    }

    fun normalizedPhone(): String = phone.trim()

    fun performCall() {
        val p = phoneLatest.trim()
        if (p.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.experiment_calls_phone_required),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val uri = try {
            Uri.fromParts("tel", p, null)
        } catch (_: Exception) {
            Uri.parse("tel:${Uri.encode(p)}")
        }
        val slot = slotForAction()
        val handle = slot.phoneAccountHandle
        val telecom = context.getSystemService(TelecomManager::class.java)
        if (handle != null && telecom != null) {
            try {
                val extras = Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                }
                telecom.placeCall(uri, extras)
                return
            } catch (_: Exception) {
                // часть прошивок надёжнее открывает вызов через intent
            }
        }
        if (handle != null) {
            try {
                val intent = Intent(Intent.ACTION_CALL, uri).apply {
                    putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // общий fallback
            }
        }
        try {
            context.startActivity(Intent(Intent.ACTION_CALL, uri))
        } catch (_: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.experiment_calls_no_app),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun performSendSms() {
        val p = phoneLatest.trim()
        if (p.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.experiment_calls_phone_required),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val subId = slotForAction().subscriptionId
        val smsManager = if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            try {
                SmsManager.getSmsManagerForSubscriptionId(subId)
            } catch (_: Exception) {
                context.getSystemService(SmsManager::class.java)
            }
        } else {
            context.getSystemService(SmsManager::class.java)
        }
        try {
            smsManager.sendTextMessage(p, null, messageLatest, null, null)
            Toast.makeText(
                context,
                context.getString(R.string.experiment_calls_sms_sent),
                Toast.LENGTH_SHORT,
            ).show()
        } catch (_: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.experiment_calls_no_app),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            performCall()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.experiment_calls_permission_call_denied),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            performSendSms()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.experiment_calls_permission_sms_denied),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun onCallClick() {
        if (normalizedPhone().isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.experiment_calls_phone_required),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED -> performCall()
            else -> callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    fun onSmsClick() {
        if (normalizedPhone().isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.experiment_calls_phone_required),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED -> performSendSms()
            else -> smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.experiment_calls_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                text = stringResource(R.string.experiment_calls_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            ExposedDropdownMenuBox(
                expanded = simMenuOpen,
                onExpandedChange = { simMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = options[selectedSimIndex.coerceIn(0, options.lastIndex)].label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.experiment_calls_sim_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = simMenuOpen) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = simMenuOpen,
                    onDismissRequest = { simMenuOpen = false },
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    options.forEachIndexed { index, slot ->
                        DropdownMenuItem(
                            text = { Text(slot.label) },
                            onClick = {
                                selectedSimIndex = index
                                simMenuOpen = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(R.string.experiment_calls_phone_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text(stringResource(R.string.experiment_calls_message_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onCallClick() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.experiment_calls_dial))
                }
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { onSmsClick() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Sms,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.experiment_calls_compose_sms))
                }
            }
        }
    }
}
