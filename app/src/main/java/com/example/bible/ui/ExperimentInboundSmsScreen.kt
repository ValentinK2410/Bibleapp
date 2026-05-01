package com.example.bible.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bible.R
import com.example.bible.data.ExperimentSmsSpeakPrefs
import com.example.bible.sms.SmsCryptoPrefs
import com.example.bible.sms.SmsCryptoSecureStore
import com.example.bible.sms.SmsOutboundCrypto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val SMS_QUERY_LIMIT = 800

private val smsExperimentPermissions = arrayOf(
    Manifest.permission.READ_SMS,
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.CALL_PHONE,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.READ_PHONE_STATE,
)

internal data class InboundSmsRow(
    val id: Long,
    val address: String,
    val body: String,
    val dateMs: Long,
    val read: Boolean,
)

internal fun Context.loadInboundSms(limit: Int = SMS_QUERY_LIMIT): List<InboundSmsRow> {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }
    val uri = Telephony.Sms.Inbox.CONTENT_URI
    val projection = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.READ,
    )
    val sort = "${Telephony.Sms.DATE} DESC"
    val out = ArrayList<InboundSmsRow>(minOf(limit, 512))
    contentResolver.query(uri, projection, null, null, sort)?.use { c ->
        val iId = c.getColumnIndexOrThrow(Telephony.Sms._ID)
        val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
        val iRead = c.getColumnIndexOrThrow(Telephony.Sms.READ)
        while (c.moveToNext() && out.size < limit) {
            out.add(
                InboundSmsRow(
                    id = c.getLong(iId),
                    address = c.getString(iAddr).orEmpty(),
                    body = c.getString(iBody).orEmpty(),
                    dateMs = c.getLong(iDate),
                    read = c.getInt(iRead) != 0,
                ),
            )
        }
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentInboundSmsScreen(
    onBack: () -> Unit,
    onOpenSmsReactions: () -> Unit = {},
    onOpenSmsSpeechOverrides: () -> Unit = {},
) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<InboundSmsRow>>(emptyList()) }
    var readGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var receiveGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var sendSmsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var callPhoneGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var contactsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var inboxNonce by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        readGranted = result[Manifest.permission.READ_SMS] == true
        receiveGranted = result[Manifest.permission.RECEIVE_SMS] == true
        sendSmsGranted = result[Manifest.permission.SEND_SMS] == true
        callPhoneGranted = result[Manifest.permission.CALL_PHONE] == true
        contactsGranted = result[Manifest.permission.READ_CONTACTS] == true
        if (!readGranted) {
            Toast.makeText(
                context,
                context.getString(R.string.experiment_sms_permission_denied),
                Toast.LENGTH_LONG,
            ).show()
        } else {
            inboxNonce++
        }
    }

    LaunchedEffect(readGranted, inboxNonce) {
        if (readGranted) {
            rows = context.loadInboundSms()
        } else {
            rows = emptyList()
        }
    }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(receiveGranted, context, mainHandler) {
        if (!receiveGranted) {
            return@DisposableEffect onDispose { }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                    mainHandler.post { inboxNonce++ }
                }
            }
        }
        val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    val dateFmt = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
    }

    var speakIncomingSms by remember {
        mutableStateOf(ExperimentSmsSpeakPrefs.isSpeakIncomingEnabled(context))
    }

    var passphraseDraft by remember { mutableStateOf("") }
    var encryptOutboundSms by remember {
        mutableStateOf(SmsCryptoPrefs.isEncryptOutboundEnabled(context))
    }
    var decryptInboundSms by remember {
        mutableStateOf(SmsCryptoPrefs.isDecryptInboundEnabled(context))
    }
    var hasCryptoKey by remember {
        mutableStateOf(SmsCryptoSecureStore.hasPassphrase(context))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.experiment_sms_inbox_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { inboxNonce++ },
                        enabled = readGranted,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.experiment_sms_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.experiment_sms_inbox_hint, SMS_QUERY_LIMIT),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.experiment_sms_speak_incoming_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.experiment_sms_speak_incoming_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Switch(
                    checked = speakIncomingSms,
                    onCheckedChange = {
                        speakIncomingSms = it
                        ExperimentSmsSpeakPrefs.setSpeakIncomingEnabled(context, it)
                    },
                )
            }
            OutlinedButton(
                onClick = onOpenSmsReactions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Text(stringResource(R.string.experiment_sms_reactions_button))
            }
            OutlinedButton(
                onClick = onOpenSmsSpeechOverrides,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                Text(stringResource(R.string.experiment_sms_speech_override_nav_button))
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.experiment_sms_crypto_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.experiment_sms_crypto_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                    )
                    OutlinedTextField(
                        value = passphraseDraft,
                        onValueChange = { passphraseDraft = it },
                        label = { Text(stringResource(R.string.experiment_sms_crypto_passphrase_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = {
                                SmsCryptoSecureStore.setPassphrase(context, passphraseDraft)
                                passphraseDraft = ""
                                hasCryptoKey = SmsCryptoSecureStore.hasPassphrase(context)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.experiment_sms_crypto_key_saved),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        ) {
                            Text(stringResource(R.string.experiment_sms_crypto_save_key))
                        }
                        TextButton(
                            onClick = {
                                SmsCryptoSecureStore.clearPassphrase(context)
                                encryptOutboundSms = false
                                decryptInboundSms = false
                                SmsCryptoPrefs.setEncryptOutboundEnabled(context, false)
                                SmsCryptoPrefs.setDecryptInboundEnabled(context, false)
                                hasCryptoKey = false
                                inboxNonce++
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.experiment_sms_crypto_key_cleared),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        ) {
                            Text(stringResource(R.string.experiment_sms_crypto_clear_key))
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                stringResource(R.string.experiment_sms_crypto_encrypt_out_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.experiment_sms_crypto_encrypt_out_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = encryptOutboundSms,
                            onCheckedChange = {
                                encryptOutboundSms = it
                                SmsCryptoPrefs.setEncryptOutboundEnabled(context, it)
                            },
                            enabled = hasCryptoKey,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                stringResource(R.string.experiment_sms_crypto_decrypt_in_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.experiment_sms_crypto_decrypt_in_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = decryptInboundSms,
                            onCheckedChange = {
                                decryptInboundSms = it
                                SmsCryptoPrefs.setDecryptInboundEnabled(context, it)
                                inboxNonce++
                            },
                            enabled = hasCryptoKey,
                        )
                    }
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            if (!readGranted || !receiveGranted || !sendSmsGranted || !callPhoneGranted) {
                Text(
                    text = stringResource(R.string.experiment_sms_permission_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (!contactsGranted && speakIncomingSms) {
                Text(
                    text = stringResource(R.string.experiment_sms_contacts_permission_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            FilledTonalButton(
                onClick = {
                    permissionLauncher.launch(smsExperimentPermissions)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Sms, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.experiment_sms_grant_permissions))
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!readGranted) {
                Text(
                    text = stringResource(R.string.experiment_sms_need_read_permission),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else if (rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.experiment_sms_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(rows, key = { it.id }) { sms ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = if (sms.read) 0.85f else 0.98f,
                                ),
                            ),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    text = sms.address.ifBlank { "—" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = dateFmt.format(Instant.ofEpochMilli(sms.dateMs)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                Text(
                                    text = SmsOutboundCrypto.decryptInboundForDisplay(
                                        context,
                                        sms.body.ifBlank { "—" },
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
