package com.example.bible.data

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Родительский PIN «Детской Библии»: 4–6 цифр, без пропуска.
 * Подсказка восстанавливает вход и стирает прогресс раздела, настройки ребёнку не открывает.
 */
internal object KidsBiblePin {
    const val MIN_LEN = 4
    const val MAX_LEN = 6
    const val MAX_FAILS = 5
    const val LOCK_MS = 60_000L

    data class Record(
        val salt: String,
        val pinHash: String,
        val hint: String,
        val hintHash: String,
    )

    data class Gate(
        val fails: Int = 0,
        val lockedUntilMs: Long = 0L,
    )

    enum class SetupProblem { PIN, MISMATCH, HINT, ANSWER }

    fun setupProblem(pin: String, repeat: String, hint: String, answer: String): SetupProblem? {
        if (!isPin(pin)) return SetupProblem.PIN
        if (pin != repeat) return SetupProblem.MISMATCH
        if (hint.trim().length < 2) return SetupProblem.HINT
        if (normalizeAnswer(answer).length < 2) return SetupProblem.ANSWER
        return null
    }

    fun isPin(pin: String): Boolean = pin.length in MIN_LEN..MAX_LEN && pin.all { it.isDigit() }

    fun normalizeAnswer(raw: String): String = raw.trim().lowercase()

    fun create(pin: String, hint: String, answer: String, salt: String = newSalt()): Record {
        check(setupProblem(pin, pin, hint, answer) == null)
        return Record(
            salt = salt,
            pinHash = hash(salt, pin),
            hint = hint.trim(),
            hintHash = hash(salt, normalizeAnswer(answer)),
        )
    }

    fun matchesPin(record: Record, pin: String): Boolean =
        isPin(pin) && hash(record.salt, pin) == record.pinHash

    fun matchesHint(record: Record, answer: String): Boolean =
        normalizeAnswer(answer).length >= 2 && hash(record.salt, normalizeAnswer(answer)) == record.hintHash

    fun locked(gate: Gate, now: Long): Boolean = gate.lockedUntilMs > now

    /** Пять ошибок подряд закрывают ввод на минуту. Во время паузы счётчик не растёт. */
    fun fail(gate: Gate, now: Long): Gate {
        if (locked(gate, now)) return gate
        val fails = gate.fails + 1
        return if (fails >= MAX_FAILS) {
            Gate(fails = 0, lockedUntilMs = now + LOCK_MS)
        } else {
            Gate(fails = fails, lockedUntilMs = 0L)
        }
    }

    fun success(): Gate = Gate()

    private fun hash(salt: String, value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$salt:$value".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
