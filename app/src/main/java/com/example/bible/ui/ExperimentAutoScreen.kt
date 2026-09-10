package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bible.R
import com.example.bible.auto.ObdComfortAction
import com.example.bible.auto.ObdComfortStrategyId
import com.example.bible.auto.ObdComfortSupport
import com.example.bible.auto.ObdDtcReport
import com.example.bible.auto.ObdReading
import com.example.bible.auto.ObdSession
import com.example.bible.auto.ObdTransport
import com.example.bible.auto.ObdVehicleProfile
import com.example.bible.auto.listPairedObdCandidates
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class AutoLinkKind { BLUETOOTH, WIFI }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExperimentAutoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { ObdSession() }

    var tab by remember { mutableIntStateOf(0) }
    var linkKind by remember { mutableStateOf(AutoLinkKind.BLUETOOTH) }
    var btExpanded by remember { mutableStateOf(false) }
    var selectedBt by remember { mutableStateOf<Pair<String, String>?>(null) }
    var wifiHost by remember { mutableStateOf("192.168.0.10") }
    var wifiPort by remember { mutableStateOf("35000") }
    var vehicleProfile by remember { mutableStateOf(ObdVehicleProfile.LARGUS_LOGAN_2015) }
    var profileExpanded by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var statusLog by remember { mutableStateOf("") }
    var pollSeconds by remember { mutableStateOf(2f) }
    var polling by remember { mutableStateOf(false) }
    val readings = remember { mutableStateListOf<ObdReading>() }
    var expertMode by remember { mutableStateOf(false) }
    var comfortLog by remember { mutableStateOf("") }
    var comfortSupport by remember { mutableStateOf<ObdComfortSupport?>(null) }
    var comfortStrategy by remember { mutableStateOf(ObdComfortStrategyId.RENAULT_UCH_CAN) }
    var strategyExpanded by remember { mutableStateOf(false) }
    var rawCommand by remember { mutableStateOf("010C") }
    var rawLog by remember { mutableStateOf("") }
    var pendingComfort by remember { mutableStateOf<ObdComfortAction?>(null) }
    var dtcReport by remember { mutableStateOf<ObdDtcReport?>(null) }
    var dtcLoading by remember { mutableStateOf(false) }
    var showClearDtcDialog by remember { mutableStateOf(false) }

    val paired = remember { listPairedObdCandidates(context) }

    val btPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        emptyArray()
    }
    var hasBtPermission by remember {
        mutableStateOf(
            btPermissions.isEmpty() || btPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasBtPermission = result.values.all { it }
    }

    DisposableEffect(session) {
        onDispose { scope.launch { session.disconnect() } }
    }

    LaunchedEffect(connected, polling, pollSeconds) {
        if (!connected || !polling) return@LaunchedEffect
        while (isActive && connected && polling) {
            val snapshot = session.readLiveSnapshot()
            readings.clear()
            readings.addAll(snapshot)
            delay((pollSeconds * 1000).toLong().coerceAtLeast(1_500L))
        }
    }

    fun connect() {
        scope.launch {
            connecting = true
            statusLog = ""
            dtcReport = null
            val transport = when (linkKind) {
                AutoLinkKind.BLUETOOTH -> {
                    val addr = selectedBt?.second
                        ?: run {
                            connecting = false
                            statusLog = "Выберите сопряжённый Bluetooth-адаптер ELM327."
                            return@launch
                        }
                    ObdTransport.Bluetooth(addr)
                }
                AutoLinkKind.WIFI -> ObdTransport.Wifi(
                    host = wifiHost.trim(),
                    port = wifiPort.toIntOrNull() ?: 35000,
                )
            }
            session.connect(transport, vehicleProfile)
                .onSuccess { log ->
                    connected = true
                    statusLog = log
                    comfortSupport = session.comfortSupport
                    comfortStrategy = session.comfortSupport.strategies.firstOrNull()
                        ?: ObdComfortStrategyId.GENERIC_ECU_7E0
                    polling = true
                    dtcLoading = true
                    dtcReport = session.readDtcs()
                    dtcLoading = false
                }
                .onFailure { e ->
                    connected = false
                    polling = false
                    comfortSupport = null
                    statusLog = e.message ?: e.toString()
                }
            connecting = false
        }
    }

    fun disconnect() {
        scope.launch {
            polling = false
            session.disconnect()
            connected = false
            comfortSupport = null
            dtcReport = null
            readings.clear()
            statusLog = "Отключено."
        }
    }

    pendingComfort?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingComfort = null },
            title = { Text("Команда исполнителя") },
            text = {
                Text(
                    "«${action.titleRu}» через ${comfortStrategy.titleRu}. " +
                        "Только на стоящем авто, на свой риск.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val a = pendingComfort
                    pendingComfort = null
                    if (a != null) {
                        scope.launch {
                            val result = session.tryComfortAction(a, comfortStrategy, expertMode)
                            comfortLog = result.message
                        }
                    }
                }) { Text("Отправить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingComfort = null }) { Text("Отмена") }
            },
        )
    }

    if (showClearDtcDialog) {
        AlertDialog(
            onDismissRequest = { showClearDtcDialog = false },
            title = { Text("Сброс ошибок") },
            text = { Text("Сбросить сохранённые DTC (команда 04)? Check Engine может погаснуть после поездки.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDtcDialog = false
                    scope.launch {
                        val r = session.clearDtcs()
                        dtcReport = session.readDtcs()
                        statusLog = r.message
                    }
                }) { Text("Сбросить") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDtcDialog = false }) { Text("Отмена") }
            },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.experiment_auto_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Связь") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Данные") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Ошибки") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Комфорт") })
                Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text("Эксперт") })
            }

            when (tab) {
                0 -> ConnectionTab(
                    vehicleProfile = vehicleProfile,
                    profileExpanded = profileExpanded,
                    onProfileExpanded = { profileExpanded = it },
                    onProfileSelect = { vehicleProfile = it },
                    linkKind = linkKind,
                    onLinkKind = { linkKind = it },
                    hasBtPermission = hasBtPermission,
                    btPermissions = btPermissions,
                    onRequestBt = { permissionLauncher.launch(btPermissions) },
                    btExpanded = btExpanded,
                    onBtExpanded = { btExpanded = it },
                    selectedBt = selectedBt,
                    onBtSelect = { selectedBt = it },
                    paired = paired,
                    wifiHost = wifiHost,
                    onWifiHost = { wifiHost = it },
                    wifiPort = wifiPort,
                    onWifiPort = { wifiPort = it },
                    connecting = connecting,
                    connected = connected,
                    onConnect = { connect() },
                    onDisconnect = { disconnect() },
                    statusLog = statusLog,
                )
                1 -> LiveDataTab(
                    connected = connected,
                    polling = polling,
                    onPolling = { polling = it },
                    pollSeconds = pollSeconds,
                    onPollSeconds = { pollSeconds = it },
                    readings = readings,
                    vin = session.vin,
                    supportedCount = session.supportedPids.size,
                )
                2 -> DtcTab(
                    connected = connected,
                    loading = dtcLoading,
                    report = dtcReport,
                    onRefresh = {
                        scope.launch {
                            dtcLoading = true
                            dtcReport = session.readDtcs()
                            dtcLoading = false
                        }
                    },
                    onClear = { showClearDtcDialog = true },
                )
                3 -> ComfortTab(
                    connected = connected,
                    support = comfortSupport ?: session.comfortSupport.takeIf { connected },
                    strategy = comfortStrategy,
                    strategyExpanded = strategyExpanded,
                    onStrategyExpanded = { strategyExpanded = it },
                    onStrategySelect = { comfortStrategy = it },
                    expertMode = expertMode,
                    onExpertMode = { expertMode = it },
                    onAction = { action ->
                        if (expertMode) pendingComfort = action
                        else scope.launch {
                            comfortLog = session.tryComfortAction(action, comfortStrategy, false).message
                        }
                    },
                    comfortLog = comfortLog,
                )
                4 -> ExpertTab(
                    connected = connected,
                    expertMode = expertMode,
                    onExpertMode = { expertMode = it },
                    rawCommand = rawCommand,
                    onRawCommand = { rawCommand = it },
                    rawLog = rawLog,
                    onSendRaw = {
                        scope.launch { rawLog = session.sendRaw(rawCommand.trim()) }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionTab(
    vehicleProfile: ObdVehicleProfile,
    profileExpanded: Boolean,
    onProfileExpanded: (Boolean) -> Unit,
    onProfileSelect: (ObdVehicleProfile) -> Unit,
    linkKind: AutoLinkKind,
    onLinkKind: (AutoLinkKind) -> Unit,
    hasBtPermission: Boolean,
    btPermissions: Array<String>,
    onRequestBt: () -> Unit,
    btExpanded: Boolean,
    onBtExpanded: (Boolean) -> Unit,
    selectedBt: Pair<String, String>?,
    onBtSelect: (Pair<String, String>) -> Unit,
    paired: List<Pair<String, String>>,
    wifiHost: String,
    onWifiHost: (String) -> Unit,
    wifiPort: String,
    onWifiPort: (String) -> Unit,
    connecting: Boolean,
    connected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    statusLog: String,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.experiment_auto_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExposedDropdownMenuBox(expanded = profileExpanded, onExpandedChange = onProfileExpanded) {
            OutlinedTextField(
                value = vehicleProfile.titleRu,
                onValueChange = {},
                readOnly = true,
                label = { Text("Профиль автомобиля") },
                supportingText = { Text(vehicleProfile.profileHintRu) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(profileExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            DropdownMenu(expanded = profileExpanded, onDismissRequest = { onProfileExpanded(false) }) {
                ObdVehicleProfile.entries.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile.titleRu) },
                        onClick = {
                            onProfileSelect(profile)
                            onProfileExpanded(false)
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = linkKind == AutoLinkKind.BLUETOOTH, onClick = { onLinkKind(AutoLinkKind.BLUETOOTH) }, label = { Text("Bluetooth") })
            FilterChip(selected = linkKind == AutoLinkKind.WIFI, onClick = { onLinkKind(AutoLinkKind.WIFI) }, label = { Text("Wi‑Fi") })
        }
        when (linkKind) {
            AutoLinkKind.BLUETOOTH -> {
                if (!hasBtPermission) {
                    FilledTonalButton(onClick = onRequestBt) {
                        Text(stringResource(R.string.experiment_auto_grant_bt))
                    }
                } else {
                    ExposedDropdownMenuBox(expanded = btExpanded, onExpandedChange = onBtExpanded) {
                        OutlinedTextField(
                            value = selectedBt?.first ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("ELM327") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(btExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        DropdownMenu(expanded = btExpanded, onDismissRequest = { onBtExpanded(false) }) {
                            paired.forEach { (name, addr) ->
                                DropdownMenuItem(
                                    text = { Text("$name\n$addr") },
                                    onClick = { onBtSelect(name to addr); onBtExpanded(false) },
                                )
                            }
                        }
                    }
                }
            }
            AutoLinkKind.WIFI -> {
                OutlinedTextField(value = wifiHost, onValueChange = onWifiHost, label = { Text("IP") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = wifiPort, onValueChange = onWifiPort, label = { Text("Порт") }, modifier = Modifier.fillMaxWidth())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onConnect, enabled = !connecting && !connected) {
                if (connecting) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                Text(if (connected) "Подключено" else "Подключиться")
            }
            if (connected) FilledTonalButton(onClick = onDisconnect) { Text("Отключить") }
        }
        if (statusLog.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                SelectionContainer {
                    Text(statusLog, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun LiveDataTab(
    connected: Boolean,
    polling: Boolean,
    onPolling: (Boolean) -> Unit,
    pollSeconds: Float,
    onPollSeconds: (Float) -> Unit,
    readings: List<ObdReading>,
    vin: String?,
    supportedCount: Int,
) {
    if (!connected) {
        Text(stringResource(R.string.experiment_auto_need_connection), Modifier.padding(16.dp))
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (vin != null) Text("VIN: $vin", style = MaterialTheme.typography.labelMedium)
        Text("Поддерживаемых PID: $supportedCount", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Опрос ${"%.1f".format(pollSeconds)} с", fontWeight = FontWeight.SemiBold)
            Switch(checked = polling, onCheckedChange = onPolling)
        }
        Slider(value = pollSeconds, onValueChange = onPollSeconds, valueRange = 1.5f..6f)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(readings, key = { it.definition.pid }) { ObdReadingCard(it) }
            if (readings.isEmpty() && polling) {
                item { CircularProgressIndicator(Modifier.padding(24.dp)) }
            }
        }
    }
}

@Composable
private fun DtcTab(
    connected: Boolean,
    loading: Boolean,
    report: ObdDtcReport?,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    if (!connected) {
        Text(stringResource(R.string.experiment_auto_need_connection), Modifier.padding(16.dp))
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onRefresh, enabled = !loading) { Text("Обновить") }
            FilledTonalButton(onClick = onClear) { Text("Сбросить ошибки") }
        }
        if (loading) CircularProgressIndicator()
        report?.let { r ->
            Text("Сохранённые (03):", fontWeight = FontWeight.SemiBold)
            if (r.stored.isEmpty()) Text("Нет ошибок") else r.stored.forEach { Text("• $it", fontFamily = FontFamily.Monospace) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Pending (07):", fontWeight = FontWeight.SemiBold)
            if (r.pending.isEmpty()) Text("Нет") else r.pending.forEach { Text("• $it", fontFamily = FontFamily.Monospace) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Сырой ответ:", style = MaterialTheme.typography.labelSmall)
            SelectionContainer {
                Text("${r.rawStored}\n${r.rawPending}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ComfortTab(
    connected: Boolean,
    support: ObdComfortSupport?,
    strategy: ObdComfortStrategyId,
    strategyExpanded: Boolean,
    onStrategyExpanded: (Boolean) -> Unit,
    onStrategySelect: (ObdComfortStrategyId) -> Unit,
    expertMode: Boolean,
    onExpertMode: (Boolean) -> Unit,
    onAction: (ObdComfortAction) -> Unit,
    comfortLog: String,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.experiment_auto_comfort_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        support?.let { s ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))) {
                Text(s.summaryRu + "\n\n" + s.detailRu, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (support != null && support.strategies.isNotEmpty()) {
            ExposedDropdownMenuBox(expanded = strategyExpanded, onExpandedChange = onStrategyExpanded) {
                OutlinedTextField(
                    value = strategy.titleRu,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Стратегия") },
                    supportingText = { Text(strategy.hintRu) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(strategyExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                DropdownMenu(expanded = strategyExpanded, onDismissRequest = { onStrategyExpanded(false) }) {
                    support.strategies.forEach { id ->
                        DropdownMenuItem(
                            text = { Text(id.titleRu) },
                            onClick = { onStrategySelect(id); onStrategyExpanded(false) },
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Экспертный режим", fontWeight = FontWeight.SemiBold)
            Switch(checked = expertMode, onCheckedChange = onExpertMode)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ObdComfortAction.entries.forEach { action ->
                FilledTonalButton(onClick = { onAction(action) }, enabled = connected) {
                    Text(action.titleRu)
                }
            }
        }
        if (comfortLog.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                SelectionContainer {
                    Text(comfortLog, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ExpertTab(
    connected: Boolean,
    expertMode: Boolean,
    onExpertMode: (Boolean) -> Unit,
    rawCommand: String,
    onRawCommand: (String) -> Unit,
    rawLog: String,
    onSendRaw: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Прямые команды ELM327 (AT…, 01…, 03, 04, UDS hex).", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Разрешить отправку", fontWeight = FontWeight.SemiBold)
            Switch(checked = expertMode, onCheckedChange = onExpertMode)
        }
        OutlinedTextField(value = rawCommand, onValueChange = onRawCommand, label = { Text("Команда") }, modifier = Modifier.fillMaxWidth())
        FilledTonalButton(onClick = onSendRaw, enabled = connected && expertMode) { Text("Отправить") }
        if (rawLog.isNotBlank()) {
            SelectionContainer {
                Text(rawLog, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ObdReadingCard(reading: ObdReading) {
    val valueColor = when (reading.value) {
        "нет данных" -> MaterialTheme.colorScheme.onSurfaceVariant
        "ошибка", "—" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(reading.definition.titleRu, fontWeight = FontWeight.SemiBold)
                Text(
                    "${reading.value} ${reading.definition.unit}",
                    style = MaterialTheme.typography.titleMedium,
                    color = valueColor,
                )
                if (reading.value == "—" || reading.value == "нет данных") {
                    Text(
                        if (!reading.supported) "ECU не объявил PID ${reading.definition.pid}" else "Заведите мотор или PID не поддерживается",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
