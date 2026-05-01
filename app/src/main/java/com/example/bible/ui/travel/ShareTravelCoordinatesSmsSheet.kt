package com.example.bible.ui.travel

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bible.R
import com.example.bible.data.ContactsRepository
import com.example.bible.data.TravelSmsSharePrefs
import com.example.bible.data.UserContact
import com.example.bible.data.normalizeRussianOutboundPhoneDigits
import com.example.bible.sms.SmsCryptoPrefs
import com.example.bible.sms.SmsCryptoSecureStore
import com.example.bible.sms.SmsOutboundCrypto
import com.example.bible.sms.sendSmsMultipart
import com.example.bible.ui.SimSlot
import com.example.bible.ui.loadSimSlots
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTravelCoordinatesSmsSheetContent(
    scope: CoroutineScope,
    lastLatitude: Double?,
    lastLongitude: Double?,
    hasFineLocation: Boolean,
    sendSmsGranted: Boolean,
    onRequestSendSmsPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { ContactsRepository(context) }
    var contacts by remember { mutableStateOf<List<UserContact>>(emptyList()) }
    var simSlots by remember { mutableStateOf<List<SimSlot>>(emptyList()) }
    var selectedSimIndex by remember { mutableIntStateOf(0) }
    var simMenuExpanded by remember { mutableStateOf(false) }
    var selectedContactId by remember { mutableStateOf<String?>(null) }
    var manualPhone by remember { mutableStateOf("") }
    var draftLat by remember { mutableStateOf(lastLatitude) }
    var draftLon by remember { mutableStateOf(lastLongitude) }

    LaunchedEffect(lastLatitude, lastLongitude) {
        draftLat = lastLatitude
        draftLon = lastLongitude
    }

    LaunchedEffect(Unit) {
        contacts = repo.load().sortedWith(compareBy({ it.fullName.lowercase() }, { it.phone }))
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            simSlots = context.loadSimSlots()
        }
        val savedSub = TravelSmsSharePrefs.getShareSubscriptionId(context)
        val idx = simSlots.indexOfFirst { it.subscriptionId == savedSub }.takeIf { it >= 0 } ?: 0
        selectedSimIndex = idx.coerceIn(0, (simSlots.size - 1).coerceAtLeast(0))
    }

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

    LaunchedEffect(options.size, selectedSimIndex) {
        if (selectedSimIndex >= options.size) selectedSimIndex = 0
    }

    fun slot(): SimSlot {
        val list = options
        val i = selectedSimIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        return list[i]
    }

    val encryptOn = SmsCryptoPrefs.isEncryptOutboundEnabled(context)
    val hasPass = SmsCryptoSecureStore.hasPassphrase(context)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            stringResource(R.string.travel_share_sms_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.travel_share_sms_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )

        ExposedDropdownMenuBox(
            expanded = simMenuExpanded,
            onExpandedChange = { simMenuExpanded = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                readOnly = true,
                value = slot().label,
                onValueChange = {},
                label = { Text(stringResource(R.string.travel_share_sms_sim_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = simMenuExpanded) },
            )
            DropdownMenu(
                expanded = simMenuExpanded,
                onDismissRequest = { simMenuExpanded = false },
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                options.forEachIndexed { index, s ->
                    DropdownMenuItem(
                        text = { Text(s.label) },
                        onClick = {
                            selectedSimIndex = index
                            TravelSmsSharePrefs.setShareSubscriptionId(context, s.subscriptionId)
                            simMenuExpanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.travel_share_sms_contact_label), style = MaterialTheme.typography.labelMedium)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(contacts.filter { it.phone.isNotBlank() }, key = { it.id }) { c ->
                val sel = selectedContactId == c.id
                TextButton(
                    onClick = {
                        selectedContactId = c.id
                        manualPhone = c.phone.trim()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${c.fullName} · ${c.phone}",
                            style = if (sel) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (sel) Text("✓", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        OutlinedTextField(
            value = manualPhone,
            onValueChange = {
                manualPhone = it
                selectedContactId = null
            },
            label = { Text(stringResource(R.string.travel_share_sms_manual_phone)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            singleLine = true,
        )

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.travel_share_sms_coords_label), style = MaterialTheme.typography.labelMedium)
                val lat = draftLat
                val lon = draftLon
                Text(
                    if (lat != null && lon != null) {
                        String.format(Locale.US, "%.6f, %.6f", lat, lon)
                    } else {
                        stringResource(R.string.travel_share_sms_coords_unknown)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            TextButton(
                onClick = {
                    if (!hasFineLocation) {
                        Toast.makeText(context, R.string.travel_need_location, Toast.LENGTH_LONG).show()
                        return@TextButton
                    }
                    scope.launch {
                        @SuppressLint("MissingPermission")
                        runCatching {
                            val client = LocationServices.getFusedLocationProviderClient(context)
                            val loc = client.getCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                CancellationTokenSource().token,
                            ).await()
                            if (loc != null) {
                                draftLat = loc.latitude
                                draftLon = loc.longitude
                            } else {
                                Toast.makeText(context, R.string.travel_share_coords_failed, Toast.LENGTH_SHORT).show()
                            }
                        }.onFailure {
                            Toast.makeText(context, R.string.travel_share_coords_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.travel_share_sms_refresh_gps))
            }
        }

        if (encryptOn && hasPass) {
            Text(
                stringResource(R.string.travel_share_sms_encrypt_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else if (encryptOn && !hasPass) {
            Text(
                stringResource(R.string.travel_share_sms_encrypt_need_key),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.back))
            }
            Button(
                onClick = {
                    if (!sendSmsGranted) {
                        onRequestSendSmsPermission()
                        return@Button
                    }
                    val lat = draftLat
                    val lon = draftLon
                    if (lat == null || lon == null) {
                        Toast.makeText(context, R.string.travel_share_sms_need_coords, Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    val phone = manualPhone.trim().normalizeRussianOutboundPhoneDigits()
                    if (phone.isEmpty()) {
                        Toast.makeText(context, R.string.travel_share_sms_need_phone, Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    val payload = JSONObject().apply {
                        put("t", "geo")
                        put("lat", lat)
                        put("lon", lon)
                        put("ts", System.currentTimeMillis())
                    }.toString()
                    val body = SmsOutboundCrypto.wrapOutboundBody(context, payload)
                    val sub = slot().subscriptionId
                    TravelSmsSharePrefs.setShareSubscriptionId(context, sub)
                    sendSmsMultipart(context, sub, phone, body)
                    Toast.makeText(context, R.string.travel_share_sms_sent, Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.travel_share_sms_send))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
