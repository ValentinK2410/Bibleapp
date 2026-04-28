package com.example.bible.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getMainExecutor
import com.example.bible.R
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.resume

private const val BRUTE_ATTEMPT_TIMEOUT_MS = 18_000L
private const val BRUTE_MAX_PASSWORDS = 40
private const val BRUTE_DELAY_MS = 2_500L

private enum class WifiApSecurity {
    OPEN,
    WPA2,
    WPA3,
    UNSUPPORTED,
}

private enum class WifiConnectUiStatus {
    Unsupported,
    Pending,
    Available,
    Unavailable,
    Lost,
}

private data class WifiRequestHandle(
    val cm: ConnectivityManager,
    val cb: ConnectivityManager.NetworkCallback,
)

private fun ScanResult.apSecurity(): WifiApSecurity {
    val c = capabilities.uppercase(Locale.US)
    if (c.contains("8021X") || c.contains("EAP")) return WifiApSecurity.UNSUPPORTED
    if (c.contains("WEP")) return WifiApSecurity.UNSUPPORTED
    if (c.contains("SAE") || c.contains("WPA3")) return WifiApSecurity.WPA3
    if (c.contains("WPA2") || c.contains("WPA") || c.contains("PSK")) return WifiApSecurity.WPA2
    return WifiApSecurity.OPEN
}

@SuppressLint("MissingPermission")
private fun startWifiConnectionRequest(
    appContext: Context,
    scan: ScanResult,
    manualSsid: String,
    password: String,
    onStatus: (WifiConnectUiStatus) -> Unit,
): WifiRequestHandle? {
    val security = scan.apSecurity()
    if (security == WifiApSecurity.UNSUPPORTED) {
        onStatus(WifiConnectUiStatus.Unsupported)
        return null
    }
    val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val builder = WifiNetworkSpecifier.Builder()
    val ssidFromScan = scan.displaySsid().trim()
    val ssidManual = manualSsid.trim()
    val ssid = ssidFromScan.ifBlank { ssidManual }
    when {
        ssid.isNotBlank() -> builder.setSsid(ssid)
        security == WifiApSecurity.OPEN -> {
            try {
                builder.setBssid(MacAddress.fromString(scan.BSSID))
            } catch (_: IllegalArgumentException) {
                onStatus(WifiConnectUiStatus.Unavailable)
                return null
            }
        }
        else -> {
            onStatus(WifiConnectUiStatus.Unavailable)
            return null
        }
    }
    when (security) {
        WifiApSecurity.OPEN -> Unit
        WifiApSecurity.WPA2 -> builder.setWpa2Passphrase(password)
        WifiApSecurity.WPA3 -> builder.setWpa3Passphrase(password)
        WifiApSecurity.UNSUPPORTED -> return null
    }
    val specifier = try {
        builder.build()
    } catch (_: Exception) {
        onStatus(WifiConnectUiStatus.Unavailable)
        return null
    }
    val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .setNetworkSpecifier(specifier)
        .build()
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onStatus(WifiConnectUiStatus.Available)
        }

        override fun onUnavailable() {
            onStatus(WifiConnectUiStatus.Unavailable)
        }

        override fun onLost(network: Network) {
            onStatus(WifiConnectUiStatus.Lost)
        }
    }
    try {
        cm.requestNetwork(request, callback)
    } catch (_: SecurityException) {
        onStatus(WifiConnectUiStatus.Unavailable)
        return null
    }
    onStatus(WifiConnectUiStatus.Pending)
    return WifiRequestHandle(cm, callback)
}

private suspend fun tryWifiPassword(
    appContext: Context,
    scan: ScanResult,
    manualSsid: String,
    password: String,
): Pair<Boolean, WifiRequestHandle?> = withContext(Dispatchers.Main.immediate) {
    var handle: WifiRequestHandle? = null
    val ok = try {
        withTimeout(BRUTE_ATTEMPT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val h = startWifiConnectionRequest(
                    appContext,
                    scan,
                    manualSsid,
                    password,
                ) { status ->
                    when (status) {
                        WifiConnectUiStatus.Pending -> Unit
                        WifiConnectUiStatus.Available ->
                            if (cont.isActive) cont.resume(true)
                        WifiConnectUiStatus.Unavailable,
                        WifiConnectUiStatus.Lost,
                        WifiConnectUiStatus.Unsupported,
                        -> if (cont.isActive) cont.resume(false)
                    }
                }
                if (h == null) {
                    if (cont.isActive) cont.resume(false)
                    return@suspendCancellableCoroutine
                }
                handle = h
                cont.invokeOnCancellation {
                    runCatching { h.cm.unregisterNetworkCallback(h.cb) }
                }
            }
        }
    } catch (_: TimeoutCancellationException) {
        handle?.let { runCatching { it.cm.unregisterNetworkCallback(it.cb) } }
        handle = null
        false
    }
    if (ok) {
        true to handle
    } else {
        handle?.let { runCatching { it.cm.unregisterNetworkCallback(it.cb) } }
        false to null
    }
}

private fun clearPendingWifiConnect(
    pair: Pair<ConnectivityManager, ConnectivityManager.NetworkCallback>?,
) {
    pair?.let { (cm, cb) ->
        runCatching { cm.unregisterNetworkCallback(cb) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentWifiScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = remember { context.applicationContext }
    val scope = rememberCoroutineScope()
    val wifiManager = remember(appContext) {
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    var hasFineLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasFineLocation = granted
    }

    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }

    var pickedScan by remember { mutableStateOf<ScanResult?>(null) }
    var pendingWifiConnect by remember {
        mutableStateOf<Pair<ConnectivityManager, ConnectivityManager.NetworkCallback>?>(null)
    }
    var bruteJob by remember { mutableStateOf<Job?>(null) }
    var bruteProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val pendingForDispose = rememberUpdatedState(pendingWifiConnect)
    val bruteJobForDispose = rememberUpdatedState(bruteJob)
    DisposableEffect(Unit) {
        onDispose {
            bruteJobForDispose.value?.cancel()
            clearPendingWifiConnect(pendingForDispose.value)
        }
    }

    DisposableEffect(wifiManager, hasFineLocation) {
        if (!hasFineLocation) {
            scanResults = emptyList()
            return@DisposableEffect onDispose { }
        }
        val callback = object : WifiManager.ScanResultsCallback() {
            override fun onScanResultsAvailable() {
                scanning = false
                scanResults = readSortedScanResults(wifiManager)
            }
        }
        wifiManager.registerScanResultsCallback(getMainExecutor(appContext), callback)
        scanResults = readSortedScanResults(wifiManager)
        onDispose {
            wifiManager.unregisterScanResultsCallback(callback)
        }
    }

    @Suppress("DEPRECATION")
    val wifiOn = wifiManager.isWifiEnabled

    fun cancelActiveConnection() {
        bruteJob?.cancel()
        bruteJob = null
        clearPendingWifiConnect(pendingWifiConnect)
        pendingWifiConnect = null
        bruteProgress = null
    }

    pickedScan?.let { scan ->
        WifiConnectDialog(
            scan = scan,
            onDismiss = { pickedScan = null },
            onConnect = { manualSsid, password ->
                clearPendingWifiConnect(pendingWifiConnect)
                pendingWifiConnect = null
                val h = startWifiConnectionRequest(appContext, scan, manualSsid, password) { status ->
                    val msg = when (status) {
                        WifiConnectUiStatus.Unsupported ->
                            appContext.getString(R.string.experiment_wifi_unsupported_security)
                        WifiConnectUiStatus.Pending ->
                            appContext.getString(R.string.experiment_wifi_status_pending)
                        WifiConnectUiStatus.Available ->
                            appContext.getString(R.string.experiment_wifi_status_ok)
                        WifiConnectUiStatus.Unavailable ->
                            appContext.getString(R.string.experiment_wifi_status_fail)
                        WifiConnectUiStatus.Lost ->
                            appContext.getString(R.string.experiment_wifi_status_lost)
                    }
                    val len = if (status == WifiConnectUiStatus.Pending) {
                        Toast.LENGTH_SHORT
                    } else {
                        Toast.LENGTH_LONG
                    }
                    Toast.makeText(appContext, msg, len).show()
                }
                if (h != null) {
                    pendingWifiConnect = h.cm to h.cb
                }
                pickedScan = null
            },
            onBruteForce = { manualSsid, candidates ->
                pickedScan = null
                bruteJob?.cancel()
                bruteJob = scope.launch {
                    bruteProgress = 0 to candidates.size
                    try {
                        for ((index, pwd) in candidates.withIndex()) {
                            if (!isActive) break
                            bruteProgress = (index + 1) to candidates.size
                            clearPendingWifiConnect(pendingWifiConnect)
                            pendingWifiConnect = null
                            val (ok, handle) = tryWifiPassword(appContext, scan, manualSsid, pwd)
                            if (ok && handle != null) {
                                pendingWifiConnect = handle.cm to handle.cb
                                Toast.makeText(
                                    appContext,
                                    appContext.getString(R.string.experiment_wifi_brute_ok, pwd),
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@launch
                            }
                            if (index < candidates.lastIndex) {
                                delay(BRUTE_DELAY_MS)
                            }
                        }
                        if (isActive) {
                            Toast.makeText(
                                appContext,
                                appContext.getString(R.string.experiment_wifi_brute_fail),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    } catch (_: CancellationException) {
                        clearPendingWifiConnect(pendingWifiConnect)
                        pendingWifiConnect = null
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.experiment_wifi_brute_cancelled),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } finally {
                        bruteProgress = null
                        bruteJob = null
                    }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.experiment_wifi_title)) },
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.experiment_wifi_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.experiment_wifi_tap_to_connect),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            if (!wifiOn) {
                Text(
                    text = stringResource(R.string.experiment_wifi_disabled),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (!hasFineLocation) {
                Text(
                    text = stringResource(R.string.experiment_wifi_need_location),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = { permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.experiment_wifi_grant_location))
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        scanning = true
                        @Suppress("DEPRECATION")
                        wifiManager.startScan()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = wifiOn && !scanning,
                ) {
                    Text(
                        if (scanning) {
                            stringResource(R.string.experiment_wifi_scanning)
                        } else {
                            stringResource(R.string.experiment_wifi_scan)
                        },
                    )
                }
            }

            bruteProgress?.let { (cur, total) ->
                Text(
                    text = stringResource(R.string.experiment_wifi_brute_progress, cur, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (pendingWifiConnect != null || bruteJob?.isActive == true) {
                OutlinedButton(
                    onClick = {
                        val wasBrute = bruteJob?.isActive == true
                        cancelActiveConnection()
                        if (!wasBrute) {
                            Toast.makeText(
                                appContext,
                                appContext.getString(R.string.experiment_wifi_request_cancelled),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.experiment_wifi_cancel_connect))
                }
            }

            if (scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            HorizontalDivider()

            if (hasFineLocation && wifiOn && scanResults.isEmpty() && !scanning) {
                Text(
                    text = stringResource(R.string.experiment_wifi_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(
                    items = scanResults,
                    key = { "${it.BSSID}_${it.frequency}_${it.level}_${it.displaySsid()}" },
                ) { scan ->
                    WifiScanResultCard(
                        scan = scan,
                        onClick = { pickedScan = scan },
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun readSortedScanResults(wifiManager: WifiManager): List<ScanResult> {
    return try {
        wifiManager.scanResults
            .filter { it.BSSID.isNotBlank() }
            .distinctBy { "${it.BSSID}_${it.frequency}" }
            .sortedByDescending { it.level }
    } catch (_: SecurityException) {
        emptyList()
    }
}

@Suppress("DEPRECATION")
private fun ScanResult.displaySsid(): String {
    val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        wifiSsid?.toString()?.trim('"') ?: ""
    } else {
        SSID?.trim('"') ?: ""
    }
    return when (raw) {
        "",
        "<unknown ssid>",
        "[SSID]",
        -> ""
        else -> raw
    }
}

private fun wifiChannelFromFrequencyMhz(frequency: Int): String {
    val ch = when {
        frequency in 2412..2484 -> (frequency - 2407) / 5
        frequency in 5160..5885 -> (frequency - 5000) / 5
        else -> null
    }
    return ch?.toString() ?: "—"
}

@Composable
private fun WifiConnectDialog(
    scan: ScanResult,
    onDismiss: () -> Unit,
    onConnect: (manualSsid: String, password: String) -> Unit,
    onBruteForce: (manualSsid: String, passwords: List<String>) -> Unit,
) {
    val security = remember(scan) { scan.apSecurity() }
    var password by remember(scan) { mutableStateOf("") }
    var manualSsid by remember(scan) { mutableStateOf("") }
    var candidateLines by remember(scan) { mutableStateOf("") }
    var fieldError by remember(scan) { mutableStateOf<String?>(null) }

    val titleSsid = scan.displaySsid().ifBlank { stringResource(R.string.experiment_wifi_hidden_ssid) }
    val errPasswordShort = stringResource(R.string.experiment_wifi_error_password_short)
    val errSsidRequired = stringResource(R.string.experiment_wifi_error_ssid_required)
    val bruteNoCandidates = stringResource(R.string.experiment_wifi_brute_no_candidates)
    val bruteLimitFmt = stringResource(R.string.experiment_wifi_brute_limit, BRUTE_MAX_PASSWORDS)
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.experiment_wifi_connect_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(scroll)) {
                Text(
                    text = titleSsid,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                when (security) {
                    WifiApSecurity.UNSUPPORTED -> {
                        Text(stringResource(R.string.experiment_wifi_unsupported_security))
                    }
                    WifiApSecurity.OPEN -> {
                        Text(stringResource(R.string.experiment_wifi_open_network))
                    }
                    WifiApSecurity.WPA2,
                    WifiApSecurity.WPA3,
                    -> {
                        if (scan.displaySsid().isBlank()) {
                            OutlinedTextField(
                                value = manualSsid,
                                onValueChange = { manualSsid = it; fieldError = null },
                                label = { Text(stringResource(R.string.experiment_wifi_ssid_manual)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; fieldError = null },
                            label = { Text(stringResource(R.string.experiment_wifi_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.experiment_wifi_brute_disclaimer),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = candidateLines,
                            onValueChange = { candidateLines = it; fieldError = null },
                            label = { Text(stringResource(R.string.experiment_wifi_brute_list_hint)) },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                fieldError = null
                                if (scan.displaySsid().isBlank() && manualSsid.isBlank()) {
                                    fieldError = errSsidRequired
                                    return@OutlinedButton
                                }
                                val list = candidateLines.lines()
                                    .map { it.trim() }
                                    .filter { it.length >= 8 }
                                    .distinct()
                                if (list.isEmpty()) {
                                    fieldError = bruteNoCandidates
                                    return@OutlinedButton
                                }
                                if (list.size > BRUTE_MAX_PASSWORDS) {
                                    fieldError = bruteLimitFmt
                                    return@OutlinedButton
                                }
                                onBruteForce(manualSsid, list)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.experiment_wifi_brute_run))
                        }
                    }
                }
                fieldError?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            when (security) {
                WifiApSecurity.UNSUPPORTED -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.experiment_wifi_got_it))
                    }
                }
                else -> {
                    TextButton(
                        onClick = {
                            fieldError = null
                            when (security) {
                                WifiApSecurity.OPEN -> onConnect("", "")
                                WifiApSecurity.WPA2,
                                WifiApSecurity.WPA3,
                                -> {
                                    if (password.length < 8) {
                                        fieldError = errPasswordShort
                                        return@TextButton
                                    }
                                    if (scan.displaySsid().isBlank() && manualSsid.isBlank()) {
                                        fieldError = errSsidRequired
                                        return@TextButton
                                    }
                                    onConnect(manualSsid, password)
                                }
                                WifiApSecurity.UNSUPPORTED -> Unit
                            }
                        },
                    ) {
                        Text(stringResource(R.string.experiment_wifi_connect))
                    }
                }
            }
        },
        dismissButton = {
            if (security != WifiApSecurity.UNSUPPORTED) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.experiment_wifi_cancel))
                }
            }
        },
    )
}

@Composable
private fun WifiScanResultCard(
    scan: ScanResult,
    onClick: () -> Unit,
) {
    val ssidRaw = scan.displaySsid()
    val title = if (ssidRaw.isBlank()) {
        stringResource(R.string.experiment_wifi_hidden_ssid)
    } else {
        ssidRaw
    }
    val channel = wifiChannelFromFrequencyMhz(scan.frequency)
    val signalLabel = stringResource(R.string.experiment_wifi_signal_dbm)
    val channelLabel = stringResource(R.string.experiment_wifi_channel)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = scan.BSSID,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$signalLabel: ${scan.level} dBm · ${scan.frequency} MHz · $channelLabel: $channel",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = scan.capabilities.ifBlank { "—" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
