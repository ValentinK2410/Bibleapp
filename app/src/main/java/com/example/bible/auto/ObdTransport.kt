package com.example.bible.auto

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ObdTransport {
    abstract suspend fun open(): ObdLink
    abstract suspend fun close()

    data class Bluetooth(val deviceAddress: String) : ObdTransport() {
        private var socket: BluetoothSocket? = null

        @SuppressLint("MissingPermission")
        override suspend fun open(): ObdLink = withContext(Dispatchers.IO) {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: error("Bluetooth недоступен")
            adapter.cancelDiscovery()
            val device = adapter.getRemoteDevice(deviceAddress)
            val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            val connected = runCatching {
                device.createRfcommSocketToServiceRecord(spp).apply { connect() }
            }.getOrElse {
                @Suppress("DiscouragedPrivateApi")
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                (method.invoke(device, 1) as BluetoothSocket).apply { connect() }
            }
            socket = connected
            ObdLink(
                reader = BufferedReader(InputStreamReader(connected.inputStream, Charsets.US_ASCII)),
                writer = OutputStreamWriter(connected.outputStream, Charsets.US_ASCII),
                closer = {
                    runCatching { connected.close() }
                    socket = null
                },
            )
        }

        override suspend fun close() = withContext(Dispatchers.IO) {
            runCatching { socket?.close() }
            socket = null
        }
    }

    data class Wifi(
        val host: String,
        val port: Int = 35000,
    ) : ObdTransport() {
        private var socket: Socket? = null

        override suspend fun open(): ObdLink = withContext(Dispatchers.IO) {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 8_000)
            socket = s
            ObdLink(
                reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.US_ASCII)),
                writer = OutputStreamWriter(s.getOutputStream(), Charsets.US_ASCII),
                closer = {
                    runCatching { s.close() }
                    socket = null
                },
            )
        }

        override suspend fun close() = withContext(Dispatchers.IO) {
            runCatching { socket?.close() }
            socket = null
        }
    }
}

class ObdLink(
    private val reader: BufferedReader,
    private val writer: OutputStreamWriter,
    private val closer: () -> Unit,
) {
    @Synchronized
    fun send(command: String, timeoutMs: Long = 3_000L): String {
        writer.write(if (command.endsWith("\r")) command else "$command\r")
        writer.flush()
        return readUntilPrompt(timeoutMs)
    }

    /** Сброс мусора после открытия Bluetooth/Wi‑Fi (приветствие ELM327). */
    @Synchronized
    fun drainStartupBanner(maxMs: Long = 2_000L) {
        val deadline = System.currentTimeMillis() + maxMs
        while (System.currentTimeMillis() < deadline) {
            if (!reader.ready()) {
                Thread.sleep(30)
                continue
            }
            if (reader.read() == -1) break
        }
    }

    private fun readUntilPrompt(timeoutMs: Long): String {
        var deadline = System.currentTimeMillis() + timeoutMs
        val sb = StringBuilder()
        var lastDataAt = System.currentTimeMillis()
        while (System.currentTimeMillis() < deadline) {
            if (!reader.ready()) {
                val text = sb.toString()
                val upper = text.uppercase()
                if (text.contains('>') &&
                    !upper.contains("SEARCHING") &&
                    System.currentTimeMillis() - lastDataAt > 350
                ) {
                    break
                }
                Thread.sleep(25)
                continue
            }
            val ch = reader.read()
            if (ch == -1) break
            sb.append(ch.toChar())
            lastDataAt = System.currentTimeMillis()
            val upper = sb.toString().uppercase()
            if (upper.contains("SEARCHING")) {
                deadline = maxOf(deadline, System.currentTimeMillis() + 25_000L)
            }
            if (upper.contains("UNABLE TO CONNECT")) break
            if (upper.contains("STOPPED") && !upper.contains("SEARCHING")) break
        }
        return sb.toString()
            .replace('>', ' ')
            .replace("\r", " ")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun close() {
        runCatching { closer() }
    }
}

@SuppressLint("MissingPermission")
fun listPairedObdCandidates(context: Context): List<Pair<String, String>> {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return emptyList()
    val adapter = manager.adapter ?: return emptyList()
    return adapter.bondedDevices
        .mapNotNull { device ->
            val name = device.name?.trim().orEmpty()
            val label = when {
                name.isNotBlank() -> name
                else -> device.address
            }
            label to device.address
        }
        .sortedBy { it.first.lowercase() }
}
