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
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import java.util.concurrent.atomic.AtomicBoolean
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
import kotlin.math.roundToInt

/** Один запрос подключения: на части устройств ассоциация занимает дольше 15–20 с. */
private const val BRUTE_ATTEMPT_TIMEOUT_MS = 42_000L
private const val BRUTE_DELAY_MS = 2_500L
private const val BRUTE_MAX_PASSWORDS = 500
private const val WPA_PSK_MIN_LEN = 8
private const val WPA_PSK_MAX_LEN = 63
private const val GEN_LENGTH_MIN = 3
private const val GEN_LENGTH_MAX = 80
private const val GEN_RANDOM_MAX_BATCH = 1_000
private const val GEN_SEQUENTIAL_MAX_STEPS = 100_000
private const val GEN_FULL_ENUM_MAX = 200_000

private const val WIFI_SPECIAL_CHARSET = "!@#\$%&*+-_=.,?^~`|:;/()[]{}"

private enum class WifiPasswordGenMode {
    RANDOM,
    SEQUENTIAL_DIGITS,
    FULL_ENUMERATION,
}

private fun buildWifiCharset(
    digits: Boolean,
    lower: Boolean,
    upper: Boolean,
    special: Boolean,
): CharArray {
    val sb = StringBuilder()
    if (digits) sb.append("0123456789")
    if (lower) for (c in 'a'..'z') sb.append(c)
    if (upper) for (c in 'A'..'Z') sb.append(c)
    if (special) sb.append(WIFI_SPECIAL_CHARSET)
    return sb.toString().toCharArray()
}

private fun effectiveWpaGenLength(requested: Int): Int =
    requested.coerceIn(GEN_LENGTH_MIN, GEN_LENGTH_MAX).coerceAtMost(WPA_PSK_MAX_LEN)

private fun generateRandomWifiPasswords(
    charset: CharArray,
    length: Int,
    count: Int,
): List<String> {
    val rnd = java.security.SecureRandom()
    val target = count.coerceIn(1, GEN_RANDOM_MAX_BATCH)
    val out = LinkedHashSet<String>()
    var guard = 0
    while (out.size < target && guard < target * 100) {
        guard++
        val s = buildString(length) {
            repeat(length) { append(charset[rnd.nextInt(charset.size)]) }
        }
        out.add(s)
    }
    return out.toList()
}

private fun generateSequentialDigitPasswords(length: Int, steps: Int): List<String> {
    val n = steps.coerceIn(1, GEN_SEQUENTIAL_MAX_STEPS)
    return (0 until n).map { i ->
        val s = i.toString().padStart(length, '0')
        if (s.length <= length) s else s.substring(s.length - length)
    }
}

private fun fullEnumerationCount(charsetSize: Int, length: Int): Long {
    if (charsetSize <= 0 || length <= 0) return 0L
    var p = 1L
    repeat(length) {
        p *= charsetSize
        if (p > GEN_FULL_ENUM_MAX * 10L) return Long.MAX_VALUE
    }
    return p
}

private fun generateFullEnumeration(charset: CharArray, length: Int): List<String>? {
    val base = charset.size
    val total = fullEnumerationCount(base, length)
    if (total > GEN_FULL_ENUM_MAX) return null
    val out = ArrayList<String>()
    var n = 0L
    while (n < total) {
        var x = n
        out.add(
            buildString(length) {
                for (pos in length - 1 downTo 0) {
                    append(charset[(x % base).toInt()])
                    x /= base.toLong()
                }
            },
        )
        n++
    }
    return out
}

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

/** Тип шифрования для [WifiNetworkSpecifier]: для WPA2/WPA3 transition сначала PSK, при неудаче — SAE. */
private fun ScanResult.preferredLinkSecurity(): WifiApSecurity {
    val c = capabilities.uppercase(Locale.US)
    if (c.contains("8021X") || c.contains("EAP")) return WifiApSecurity.UNSUPPORTED
    if (c.contains("WEP")) return WifiApSecurity.UNSUPPORTED
    val hasSae = c.contains("SAE") || c.contains("WPA3")
    val hasPsk = c.contains("PSK") || c.contains("WPA2") || c.contains("WPA-") || c.contains("[WPA")
    if (hasSae && hasPsk) return WifiApSecurity.WPA2
    if (hasSae) return WifiApSecurity.WPA3
    if (hasPsk || c.contains("WPA")) return WifiApSecurity.WPA2
    return WifiApSecurity.OPEN
}

private fun ScanResult.alternateLinkSecurity(primary: WifiApSecurity): WifiApSecurity? {
    val c = capabilities.uppercase(Locale.US)
    val hasSae = c.contains("SAE") || c.contains("WPA3")
    val hasPsk = c.contains("PSK") || c.contains("WPA2") || c.contains("WPA-") || c.contains("[WPA")
    return when (primary) {
        WifiApSecurity.WPA2 -> if (hasSae) WifiApSecurity.WPA3 else null
        WifiApSecurity.WPA3 -> if (hasPsk) WifiApSecurity.WPA2 else null
        else -> null
    }
}

/** Для UI диалога — то же, что [preferredLinkSecurity]. */
private fun ScanResult.apSecurity(): WifiApSecurity = preferredLinkSecurity()

@SuppressLint("MissingPermission")
private fun startWifiConnectionRequest(
    appContext: Context,
    scan: ScanResult,
    manualSsid: String,
    password: String,
    linkSecurity: WifiApSecurity,
    onStatus: (WifiConnectUiStatus) -> Unit,
): WifiRequestHandle? {
    if (scan.preferredLinkSecurity() == WifiApSecurity.UNSUPPORTED) {
        onStatus(WifiConnectUiStatus.Unsupported)
        return null
    }
    val pwd = password.trim()
    if (linkSecurity != WifiApSecurity.OPEN &&
        (pwd.length < WPA_PSK_MIN_LEN || pwd.length > WPA_PSK_MAX_LEN)
    ) {
        onStatus(WifiConnectUiStatus.Unavailable)
        return null
    }
    val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val builder = WifiNetworkSpecifier.Builder()
    val ssidFromScan = scan.displaySsid().trim()
    val ssidManual = manualSsid.trim()
    val ssid = ssidFromScan.ifBlank { ssidManual }
    when {
        ssid.isNotBlank() -> builder.setSsid(ssid)
        linkSecurity == WifiApSecurity.OPEN -> {
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
    when (linkSecurity) {
        WifiApSecurity.OPEN -> Unit
        WifiApSecurity.WPA2 -> builder.setWpa2Passphrase(pwd)
        WifiApSecurity.WPA3 -> builder.setWpa3Passphrase(pwd)
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
        cm.requestNetwork(request, callback, Handler(Looper.getMainLooper()))
    } catch (_: SecurityException) {
        onStatus(WifiConnectUiStatus.Unavailable)
        return null
    }
    onStatus(WifiConnectUiStatus.Pending)
    return WifiRequestHandle(cm, callback)
}

private suspend fun trySingleWifiConnect(
    appContext: Context,
    scan: ScanResult,
    manualSsid: String,
    password: String,
    linkSecurity: WifiApSecurity,
): Pair<Boolean, WifiRequestHandle?> = withContext(Dispatchers.Main.immediate) {
    var handle: WifiRequestHandle? = null
    val ok = try {
        withTimeout(BRUTE_ATTEMPT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val finished = AtomicBoolean(false)
                val h = startWifiConnectionRequest(
                    appContext,
                    scan,
                    manualSsid,
                    password,
                    linkSecurity,
                ) { status ->
                    when (status) {
                        WifiConnectUiStatus.Pending -> Unit
                        WifiConnectUiStatus.Available ->
                            if (cont.isActive && finished.compareAndSet(false, true)) {
                                cont.resume(true)
                            }
                        WifiConnectUiStatus.Unavailable,
                        WifiConnectUiStatus.Lost,
                        WifiConnectUiStatus.Unsupported,
                        -> if (cont.isActive && finished.compareAndSet(false, true)) {
                            cont.resume(false)
                        }
                    }
                }
                if (h == null) {
                    if (cont.isActive && finished.compareAndSet(false, true)) cont.resume(false)
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

private suspend fun connectWithWpaFallback(
    appContext: Context,
    scan: ScanResult,
    manualSsid: String,
    password: String,
): Pair<Boolean, WifiRequestHandle?> {
    val pwd = password.trim()
    val primary = scan.preferredLinkSecurity()
    if (primary == WifiApSecurity.UNSUPPORTED) {
        return false to null
    }
    if (primary == WifiApSecurity.OPEN) {
        return trySingleWifiConnect(appContext, scan, manualSsid, pwd, WifiApSecurity.OPEN)
    }
    var result = trySingleWifiConnect(appContext, scan, manualSsid, pwd, primary)
    if (result.first) return result
    val alt = scan.alternateLinkSecurity(primary)
    if (alt != null) {
        delay(1_800)
        result = trySingleWifiConnect(appContext, scan, manualSsid, pwd, alt)
    }
    return result
}

private suspend fun tryWifiPassword(
    appContext: Context,
    scan: ScanResult,
    manualSsid: String,
    password: String,
): Pair<Boolean, WifiRequestHandle?> = connectWithWpaFallback(appContext, scan, manualSsid, password)

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

    val needNearbyWifi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var hasFineLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasNearbyWifi by remember {
        mutableStateOf(
            if (needNearbyWifi) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }
    val wifiPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasFineLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (needNearbyWifi) {
            hasNearbyWifi = result[Manifest.permission.NEARBY_WIFI_DEVICES] == true
        }
    }
    val canUseWifiApis = hasFineLocation && (!needNearbyWifi || hasNearbyWifi)

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

    DisposableEffect(wifiManager, canUseWifiApis) {
        if (!canUseWifiApis) {
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
                pickedScan = null
                scope.launch {
                    clearPendingWifiConnect(pendingWifiConnect)
                    pendingWifiConnect = null
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.experiment_wifi_status_pending),
                        Toast.LENGTH_SHORT,
                    ).show()
                    val (ok, handle) = connectWithWpaFallback(appContext, scan, manualSsid, password)
                    if (ok && handle != null) {
                        pendingWifiConnect = handle.cm to handle.cb
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.experiment_wifi_status_ok),
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.experiment_wifi_status_fail),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
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

            if (!canUseWifiApis) {
                Text(
                    text = stringResource(R.string.experiment_wifi_need_location),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (needNearbyWifi && hasFineLocation && !hasNearbyWifi) {
                    Text(
                        text = stringResource(R.string.experiment_wifi_need_nearby),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = {
                        val perms = buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            if (needNearbyWifi) add(Manifest.permission.NEARBY_WIFI_DEVICES)
                        }.toTypedArray()
                        wifiPermLauncher.launch(perms)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.experiment_wifi_grant_permissions))
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

            if (canUseWifiApis && wifiOn && scanResults.isEmpty() && !scanning) {
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

    var genDigit by remember(scan) { mutableStateOf(true) }
    var genLower by remember(scan) { mutableStateOf(true) }
    var genUpper by remember(scan) { mutableStateOf(true) }
    var genSpecial by remember(scan) { mutableStateOf(true) }
    var genLen by remember(scan) { mutableIntStateOf(8) }
    var genMode by remember(scan) { mutableStateOf(WifiPasswordGenMode.RANDOM) }
    var genRandomCount by remember(scan) { mutableStateOf("200") }
    var genSeqSteps by remember(scan) { mutableStateOf("10000") }

    val titleSsid = scan.displaySsid().ifBlank { stringResource(R.string.experiment_wifi_hidden_ssid) }
    val errPasswordShort = stringResource(R.string.experiment_wifi_error_password_short)
    val errSsidRequired = stringResource(R.string.experiment_wifi_error_ssid_required)
    val bruteNoCandidates = stringResource(R.string.experiment_wifi_brute_no_candidates)
    val bruteLimitFmt = stringResource(R.string.experiment_wifi_brute_limit, BRUTE_MAX_PASSWORDS)
    val genCharsetEmpty = stringResource(R.string.experiment_wifi_gen_charset_empty)
    val genSeqDigitsOnly = stringResource(R.string.experiment_wifi_gen_seq_digits_only)
    val genFullTooLarge = stringResource(R.string.experiment_wifi_gen_full_too_large, GEN_FULL_ENUM_MAX)
    val genInvalidCount = stringResource(R.string.experiment_wifi_gen_invalid_count)
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
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.experiment_wifi_gen_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = genDigit,
                                onClick = { genDigit = !genDigit; fieldError = null },
                                label = { Text(stringResource(R.string.experiment_wifi_gen_digits)) },
                            )
                            FilterChip(
                                selected = genLower,
                                onClick = { genLower = !genLower; fieldError = null },
                                label = { Text(stringResource(R.string.experiment_wifi_gen_lower)) },
                            )
                            FilterChip(
                                selected = genUpper,
                                onClick = { genUpper = !genUpper; fieldError = null },
                                label = { Text(stringResource(R.string.experiment_wifi_gen_upper)) },
                            )
                            FilterChip(
                                selected = genSpecial,
                                onClick = { genSpecial = !genSpecial; fieldError = null },
                                label = { Text(stringResource(R.string.experiment_wifi_gen_special)) },
                            )
                        }
                        Text(
                            text = stringResource(R.string.experiment_wifi_gen_length, genLen),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = genLen.toFloat(),
                            onValueChange = {
                                genLen = it.roundToInt().coerceIn(GEN_LENGTH_MIN, GEN_LENGTH_MAX)
                                fieldError = null
                            },
                            valueRange = GEN_LENGTH_MIN.toFloat()..GEN_LENGTH_MAX.toFloat(),
                        )
                        Text(
                            text = stringResource(R.string.experiment_wifi_gen_mode_label),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = genMode == WifiPasswordGenMode.RANDOM,
                                onClick = { genMode = WifiPasswordGenMode.RANDOM; fieldError = null },
                                label = { Text(stringResource(R.string.experiment_wifi_gen_mode_random)) },
                            )
                            FilterChip(
                                selected = genMode == WifiPasswordGenMode.SEQUENTIAL_DIGITS,
                                onClick = {
                                    genMode = WifiPasswordGenMode.SEQUENTIAL_DIGITS
                                    fieldError = null
                                },
                                label = { Text(stringResource(R.string.experiment_wifi_gen_mode_sequential)) },
                            )
                            FilterChip(
                                selected = genMode == WifiPasswordGenMode.FULL_ENUMERATION,
                                onClick = {
                                    genMode = WifiPasswordGenMode.FULL_ENUMERATION
                                    fieldError = null
                                },
                                label = { Text(stringResource(R.string.experiment_wifi_gen_mode_full)) },
                            )
                        }
                        when (genMode) {
                            WifiPasswordGenMode.RANDOM -> {
                                OutlinedTextField(
                                    value = genRandomCount,
                                    onValueChange = { genRandomCount = it; fieldError = null },
                                    label = { Text(stringResource(R.string.experiment_wifi_gen_count_random)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            WifiPasswordGenMode.SEQUENTIAL_DIGITS -> {
                                OutlinedTextField(
                                    value = genSeqSteps,
                                    onValueChange = { genSeqSteps = it; fieldError = null },
                                    label = { Text(stringResource(R.string.experiment_wifi_gen_count_sequential)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            WifiPasswordGenMode.FULL_ENUMERATION -> Unit
                        }
                        OutlinedButton(
                            onClick = {
                                fieldError = null
                                val effLen = effectiveWpaGenLength(genLen)
                                val charset = buildWifiCharset(genDigit, genLower, genUpper, genSpecial)
                                if (charset.isEmpty()) {
                                    fieldError = genCharsetEmpty
                                    return@OutlinedButton
                                }
                                val generated: List<String> = when (genMode) {
                                    WifiPasswordGenMode.RANDOM -> {
                                        val c = genRandomCount.toIntOrNull()?.coerceIn(1, GEN_RANDOM_MAX_BATCH)
                                        if (c == null) {
                                            fieldError = genInvalidCount
                                            return@OutlinedButton
                                        }
                                        generateRandomWifiPasswords(charset, effLen, c)
                                    }
                                    WifiPasswordGenMode.SEQUENTIAL_DIGITS -> {
                                        if (!genDigit || genLower || genUpper || genSpecial) {
                                            fieldError = genSeqDigitsOnly
                                            return@OutlinedButton
                                        }
                                        val steps = genSeqSteps.toIntOrNull()?.coerceIn(1, GEN_SEQUENTIAL_MAX_STEPS)
                                        if (steps == null) {
                                            fieldError = genInvalidCount
                                            return@OutlinedButton
                                        }
                                        generateSequentialDigitPasswords(effLen, steps)
                                    }
                                    WifiPasswordGenMode.FULL_ENUMERATION -> {
                                        val cnt = fullEnumerationCount(charset.size, effLen)
                                        if (cnt > GEN_FULL_ENUM_MAX) {
                                            fieldError = genFullTooLarge
                                            return@OutlinedButton
                                        }
                                        generateFullEnumeration(charset, effLen) ?: emptyList()
                                    }
                                }
                                if (generated.isEmpty()) {
                                    fieldError = bruteNoCandidates
                                    return@OutlinedButton
                                }
                                val existing = candidateLines.lines()
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .toMutableSet()
                                for (p in generated) existing.add(p)
                                candidateLines = existing.joinToString("\n")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.experiment_wifi_gen_add))
                        }
                        Text(
                            text = stringResource(R.string.experiment_wifi_brute_wpa_len_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
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
                                    .filter { it.length in WPA_PSK_MIN_LEN..WPA_PSK_MAX_LEN }
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
                                    val pw = password.trim()
                                    if (pw.length !in WPA_PSK_MIN_LEN..WPA_PSK_MAX_LEN) {
                                        fieldError = errPasswordShort
                                        return@TextButton
                                    }
                                    if (scan.displaySsid().isBlank() && manualSsid.isBlank()) {
                                        fieldError = errSsidRequired
                                        return@TextButton
                                    }
                                    onConnect(manualSsid, pw)
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
