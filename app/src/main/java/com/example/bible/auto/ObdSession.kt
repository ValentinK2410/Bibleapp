package com.example.bible.auto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ObdSession {
    private var link: ObdLink? = null
    private var transport: ObdTransport? = null
    var activeProfile: ObdVehicleProfile? = null
        private set

    var detectedProtocol: String? = null
        private set

    var supportedPids: Set<String> = emptySet()
        private set

    var vin: String? = null
        private set

    val isConnected: Boolean get() = link != null

    val comfortSupport: ObdComfortSupport
        get() = ObdComfortSupportEvaluator.evaluate(activeProfile, detectedProtocol)

    suspend fun connect(
        transport: ObdTransport,
        profile: ObdVehicleProfile = ObdVehicleProfile.LARGUS_LOGAN_2015,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            disconnectInternal()
            this@ObdSession.transport = transport
            val opened = transport.open()
            link = opened
            opened.drainStartupBanner()

            var connectedAttempt: ObdProtocolAttempt? = null
            var protocolDesc: String? = null
            var lastProbe = ""
            val log = buildString {
                appendLine("Профиль: ${profile.titleRu}")
                if (profile.profileHintRu.isNotBlank()) {
                    appendLine(profile.profileHintRu)
                    appendLine()
                }
                appendLine("ATZ → ${opened.send("ATZ", 6_000L)}")
                Thread.sleep(profile.initDelayMs)
                appendLine("ATE0 → ${opened.send("ATE0", 2_000L)}")
                appendLine("ATL0 → ${opened.send("ATL0", 2_000L)}")
                appendLine("ATS0 → ${opened.send("ATS0", 2_000L)}")
                appendLine("ATST FF → ${opened.send("ATST FF", 2_000L)}")
                appendLine("ATAT1 → ${opened.send("ATAT1", 2_000L)}")
                appendLine("ATBD → ${opened.send("ATBD", 2_000L)}")

                for (attempt in profile.attempts) {
                    appendLine()
                    appendLine("— ${attempt.labelRu} —")
                    for (prep in attempt.prepCommands) {
                        appendLine("$prep → ${opened.send(prep, 2_500L)}")
                    }
                    val spResp = opened.send(attempt.setProtocol, 3_000L)
                    appendLine("${attempt.setProtocol} → $spResp")
                    if (spResp.isBlank() || spResp.uppercase().contains("ERROR")) {
                        appendLine("(протокол не принят, следующий…)")
                        continue
                    }
                    if (attempt.delayAfterProtocolMs > 0) Thread.sleep(attempt.delayAfterProtocolMs)

                    var ok = false
                    for (probe in attempt.probeCommands) {
                        lastProbe = opened.send(probe, attempt.probeTimeoutMs)
                        appendLine("$probe → $lastProbe")
                        if (ObdInitHelper.isProbeSuccessful(lastProbe)) {
                            ok = true
                            break
                        }
                    }
                    if (!ok) {
                        appendLine("ATWS → ${opened.send("ATWS", 2_000L)}")
                        Thread.sleep(400)
                        lastProbe = opened.send("0100", attempt.probeTimeoutMs)
                        appendLine("0100 (повтор) → $lastProbe")
                        ok = ObdInitHelper.isProbeSuccessful(lastProbe)
                    }

                    if (ok) {
                        connectedAttempt = attempt
                        protocolDesc = opened.send("ATDP", 2_000L)
                        appendLine("ATDP → $protocolDesc")
                        configureBusFormat(opened, protocolDesc)
                        appendLine()
                        appendLine("✓ Связь установлена (${attempt.labelRu})")
                        break
                    }
                }

                if (connectedAttempt == null) {
                    appendLine()
                    append(ObdInitHelper.failureHint(profile, toString()))
                }
            }.trim()

            if (connectedAttempt == null) {
                throw IllegalStateException(
                    if (log.isNotBlank()) log
                    else "ECU не ответил. Последний ответ: ${lastProbe.ifBlank { "пусто" }}",
                )
            }

            activeProfile = profile
            detectedProtocol = protocolDesc

            val fullLog = buildString {
                append(log)
                appendLine()
                appendLine()
                append(discoverCapabilities(opened, profile))
            }.trim()

            fullLog
        }.onFailure {
            disconnectInternal()
        }
    }

    private fun configureBusFormat(link: ObdLink, protocolDesc: String?) {
        val isCan = protocolDesc?.uppercase()?.contains("CAN") == true
        if (isCan) {
            link.send("AT CAF1", 2_000L)
            link.send("AT CFC1", 2_000L)
            link.send("AT AL", 2_000L)
        } else {
            link.send("AT CAF0", 2_000L)
        }
    }

    private fun discoverCapabilities(link: ObdLink, profile: ObdVehicleProfile): String = buildString {
        appendLine("— Диагностика ECU —")
        supportedPids = discoverSupportedPids(link, profile)
        appendLine("Поддерживаемых PID: ${supportedPids.size}")
        if (supportedPids.isNotEmpty()) {
            appendLine("Примеры: ${supportedPids.take(8).joinToString(", ")}")
        }
        val vinRaw = link.send("0902", 8_000L)
        vin = ObdHexParser.parseVinMode09(vinRaw)
        appendLine("VIN (0902): ${vin ?: vinRaw.ifBlank { "—" }}")
        val testPid = profile.priorityPids.firstOrNull() ?: "0C"
        val testRaw = link.send("01$testPid", 5_000L)
        appendLine("Тест 01$testPid → $testRaw")
    }

    private fun discoverSupportedPids(link: ObdLink, profile: ObdVehicleProfile): Set<String> {
        val discovered = LinkedHashSet<String>()
        for (base in listOf(0x00, 0x20, 0x40)) {
            val cmd = "01${base.toString(16).uppercase().padStart(2, '0')}"
            val raw = runCatching { link.send(cmd, 6_000L) }.getOrDefault("")
            discovered.addAll(ObdHexParser.supportedPidsFromBitmapResponse(raw, base))
            Thread.sleep(120)
        }
        if (discovered.isEmpty()) {
            discovered.addAll(profile.priorityPids.map { it.uppercase().padStart(2, '0') })
        }
        return discovered
    }

    /** PID для опроса: приоритет профиля ∩ поддерживаемые, иначе базовые. */
    fun pidsToPoll(): List<ObdPidDefinition> {
        val profile = activeProfile ?: return ObdPidDefinitions.standard.take(6)
        val normSupported = supportedPids.map { it.uppercase().padStart(2, '0') }.toSet()
        val fromProfile = profile.priorityPids.mapNotNull { pid ->
            val norm = pid.uppercase().padStart(2, '0')
            if (normSupported.isEmpty() || norm in normSupported) {
                ObdPidDefinitions.byPid(norm)
            } else {
                null
            }
        }
        if (fromProfile.isNotEmpty()) return fromProfile
        return listOf("0C", "0D", "05", "04", "0F", "11").mapNotNull { ObdPidDefinitions.byPid(it) }
    }

    suspend fun readLiveSnapshot(): List<ObdReading> = withContext(Dispatchers.IO) {
        val l = link ?: return@withContext emptyList()
        val defs = pidsToPoll()
        val out = ArrayList<ObdReading>(defs.size)
        for (def in defs) {
            out.add(readPidInternal(l, def))
            Thread.sleep(180)
        }
        out
    }

    suspend fun readPid(def: ObdPidDefinition): ObdReading = withContext(Dispatchers.IO) {
        val l = link ?: return@withContext ObdReading(def, "—", "Не подключено", supported = false)
        readPidInternal(l, def)
    }

    private fun readPidInternal(l: ObdLink, def: ObdPidDefinition): ObdReading {
        val cmd = "${def.mode}${def.pid}"
        val raw = runCatching { l.send(cmd, 6_000L) }.getOrElse { it.message ?: "ошибка" }
        val value = ObdPidDefinitions.parseResponse(raw, def)
        val supported = supportedPids.isEmpty() ||
            def.pid.uppercase().padStart(2, '0') in supportedPids.map { it.uppercase().padStart(2, '0') }
        return ObdReading(def, value, raw, supported)
    }

    suspend fun readDtcs(): ObdDtcReport = withContext(Dispatchers.IO) {
        val l = link ?: return@withContext ObdDtcReport(emptyList(), emptyList(), "Не подключено", "")
        val storedRaw = runCatching { l.send("03", 8_000L) }.getOrElse { it.message ?: "" }
        Thread.sleep(150)
        val pendingRaw = runCatching { l.send("07", 8_000L) }.getOrElse { it.message ?: "" }
        ObdDtcReport(
            stored = ObdHexParser.parseDtcsMode03(storedRaw),
            pending = ObdHexParser.parseDtcsMode03(pendingRaw),
            rawStored = storedRaw,
            rawPending = pendingRaw,
        )
    }

    suspend fun clearDtcs(): ObdDtcClearResult = withContext(Dispatchers.IO) {
        val l = link ?: return@withContext ObdDtcClearResult(false, "Не подключено")
        val raw = runCatching { l.send("04", 8_000L) }.getOrElse { it.message ?: "ошибка" }
        val ok = ObdHexParser.isClearDtcSuccess(raw)
        ObdDtcClearResult(
            success = ok,
            message = if (ok) "Ошибки сброшены (04 → $raw)" else "Сброс не подтверждён: $raw",
        )
    }

    suspend fun sendRaw(command: String): String = withContext(Dispatchers.IO) {
        val l = link ?: return@withContext "Не подключено"
        runCatching { l.send(command, 10_000L) }.getOrElse { it.message ?: "ошибка" }
    }

    suspend fun tryComfortAction(
        action: ObdComfortAction,
        strategy: ObdComfortStrategyId,
        expertMode: Boolean,
    ): ObdComfortResult = withContext(Dispatchers.IO) {
        val l = link ?: return@withContext ObdComfortResult(
            action = action,
            success = false,
            message = "Сначала подключитесь к адаптеру OBD-II.",
        )
        if (!expertMode) {
            return@withContext ObdComfortResult(
                action = action,
                success = false,
                message = "Включите «Экспертный режим» для отправки команд исполнителям.",
            )
        }

        val steps = ObdComfortCatalog.commands(strategy, action)
        val log = buildString {
            appendLine("⚠ ${strategy.titleRu}: ${action.titleRu}")
            appendLine("Протокол: ${detectedProtocol ?: "?"}")
            appendLine()
            var positive = false
            for (step in steps) {
                val resp = l.send(step, 12_000L)
                val display = resp.ifBlank { "(нет ответа)" }
                appendLine("$step → $display")
                if (resp.contains(Regex("7F|6E|50|71", RegexOption.IGNORE_CASE)) ||
                    resp.uppercase().contains("OK")
                ) {
                    positive = positive || resp.contains(Regex("6E|50 03|71 01", RegexOption.IGNORE_CASE))
                }
                Thread.sleep(80)
            }
            appendLine()
            if (!positive) {
                appendLine("ECU не подтвердил действие. Попробуйте другую стратегию или профиль авто.")
            }
        }.trim()
        ObdComfortResult(
            action = action,
            success = log.contains(Regex("6E|50 03|71 01", RegexOption.IGNORE_CASE)),
            message = log,
        )
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
    }

    private suspend fun disconnectInternal() {
        withContext(Dispatchers.IO) {
            runCatching { link?.close() }
            link = null
            runCatching { transport?.close() }
            transport = null
            activeProfile = null
            detectedProtocol = null
            supportedPids = emptySet()
            vin = null
        }
    }
}

data class ObdReading(
    val definition: ObdPidDefinition,
    val value: String,
    val raw: String,
    val supported: Boolean = true,
)

enum class ObdComfortAction(val titleRu: String) {
    WINDOW_DRIVER_UP("Стекло водителя вверх"),
    WINDOW_DRIVER_DOWN("Стекло водителя вниз"),
    WINDOW_ALL_UP("Все стёкла вверх"),
    CENTRAL_LOCK("Центральный замок"),
    CENTRAL_UNLOCK("Снять с замка"),
}

data class ObdComfortResult(
    val action: ObdComfortAction,
    val success: Boolean,
    val message: String,
)
