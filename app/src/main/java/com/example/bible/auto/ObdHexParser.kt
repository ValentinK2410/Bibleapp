package com.example.bible.auto

/** Разбор hex-ответов ELM327 (OBD-II / UDS). */
internal object ObdHexParser {

    fun hexTokens(raw: String): List<String> =
        raw.uppercase()
            .replace("SEARCHING...", " ")
            .split(Regex("[^0-9A-F]+"))
            .filter { it.length == 2 }

    fun isError(raw: String): Boolean {
        val u = raw.uppercase()
        return u.contains("UNABLE TO CONNECT") ||
            u.contains("BUS INIT") && u.contains("ERROR") ||
            (u.contains("ERROR") && !u.contains("41") && !u.contains("43") && !u.contains("49"))
    }

    fun isNoData(raw: String): Boolean = raw.uppercase().contains("NO DATA")

    /** Байты данных для mode/pid, например mode=01 pid=0C → ищем 41 0C … */
    fun dataBytesForModePid(raw: String, mode: Int, pid: String): List<Int>? {
        val tokens = hexTokens(raw)
        if (tokens.isEmpty()) return null
        val service = (mode + 0x40).toString(16).uppercase().padStart(2, '0')
        val pidNorm = pid.uppercase().padStart(2, '0')
        for (i in 0 until tokens.size - 1) {
            if (tokens[i] == service && tokens[i + 1] == pidNorm) {
                return tokens.drop(i + 2).map { it.toInt(16) }
            }
        }
        // Сжатый ответ 410C1AF0
        val compact = tokens.joinToString("")
        val needle = service + pidNorm
        val idx = compact.indexOf(needle)
        if (idx >= 0) {
            val rest = compact.substring(idx + needle.length)
            if (rest.length < 2) return emptyList()
            return rest.chunked(2).map { it.toInt(16) }
        }
        return null
    }

    /** Поддерживаемые PID из ответа 41 00 / 41 20 / 41 40 … */
    fun supportedPidsFromBitmapResponse(raw: String, basePid: Int): Set<String> {
        val tokens = hexTokens(raw)
        val service = "41"
        val pidHex = basePid.toString(16).uppercase().padStart(2, '0')
        val start = tokens.indexOfFirst { it == service }
        if (start < 0) return emptySet()
        var dataStart = start + 1
        if (tokens.getOrNull(dataStart) == pidHex) dataStart++
        val bitmap = tokens.drop(dataStart).take(4).map { it.toInt(16) }
        if (bitmap.size < 4) return emptySet()
        val out = LinkedHashSet<String>()
        for (bit in 0 until 32) {
            val byteIndex = bit / 8
            val bitIndex = 7 - (bit % 8)
            if ((bitmap[byteIndex] shr bitIndex) and 1 == 1) {
                val pid = (basePid + bit + 1).toString(16).uppercase().padStart(2, '0')
                out.add(pid)
            }
        }
        return out
    }

    fun decodeDtcPair(b0: Int, b1: Int): String {
        if (b0 == 0 && b1 == 0) return ""
        val type = when ((b0 shr 6) and 0x3) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            else -> 'U'
        }
        val d1 = (b0 shr 4) and 0x3
        val d2 = b0 and 0x0F
        val d3 = (b1 shr 4) and 0x0F
        val d4 = b1 and 0x0F
        return "$type$d1${d2.toString(16).uppercase()}${d3.toString(16).uppercase()}${d4.toString(16).uppercase()}"
    }

    fun parseDtcsMode03(raw: String): List<String> {
        if (isNoData(raw)) return emptyList()
        val tokens = hexTokens(raw)
        val idx = tokens.indexOf("43")
        if (idx < 0) return emptyList()
        val count = tokens.getOrNull(idx + 1)?.toIntOrNull(16) ?: return emptyList()
        val pairs = tokens.drop(idx + 2).chunked(2).mapNotNull {
            if (it.size < 2) return@mapNotNull null
            decodeDtcPair(it[0].toInt(16), it[1].toInt(16))
        }.filter { it.isNotBlank() }
        return if (count > 0 && pairs.isNotEmpty()) pairs.take(count) else pairs
    }

    fun parseVinMode09(raw: String): String? {
        val tokens = hexTokens(raw)
        val idx = tokens.indexOf("49")
        if (idx < 0) return null
        val chars = tokens.drop(idx + 3).mapNotNull { token ->
            token.toIntOrNull(16)?.takeIf { it in 32..126 }?.toChar()
        }
        val vin = chars.joinToString("").trim()
        return vin.takeIf { it.length >= 11 }
    }

    fun isClearDtcSuccess(raw: String): Boolean =
        hexTokens(raw).contains("44") || raw.uppercase().contains("44")
}
