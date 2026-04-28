package com.example.bible.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getMainExecutor
import com.example.bible.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentWifiScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = remember { context.applicationContext }
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
                    WifiScanResultCard(scan = scan)
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
private fun WifiScanResultCard(scan: ScanResult) {
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
        modifier = Modifier.fillMaxWidth(),
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
