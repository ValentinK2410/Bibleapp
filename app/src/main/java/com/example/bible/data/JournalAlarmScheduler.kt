package com.example.bible.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.bible.MainActivity
import com.example.bible.receiver.JournalAlarmReceiver
import java.time.LocalDate
import java.time.ZoneId

/** Будильник ежедневника: срабатывает без открытого приложения. */
object JournalAlarmScheduler {

    private const val PREFS = "journal_alarm_ids"
    private const val KEY_IDS = "ids"

    fun reschedule(context: Context, entries: List<DailyJournalEntry>) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val store = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        store.getStringSet(KEY_IDS, emptySet()).orEmpty().forEach { code ->
            am.cancel(alarmPending(app, code.toIntOrNull() ?: return@forEach))
        }
        val nextIds = mutableSetOf<String>()
        val now = System.currentTimeMillis()
        entries.forEach { entry ->
            triggers(entry, now).forEach { trigger ->
                val code = (entry.id + ":" + trigger.tag).hashCode()
                val fire = Intent(app, JournalAlarmReceiver::class.java).apply {
                    putExtra(JournalAlarmReceiver.EXTRA_ID, entry.id)
                    putExtra(JournalAlarmReceiver.EXTRA_TITLE, entry.title.ifBlank { entry.location })
                    putExtra(JournalAlarmReceiver.EXTRA_TAG, trigger.tag)
                }
                val pending = PendingIntent.getBroadcast(
                    app,
                    code,
                    fire,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val show = PendingIntent.getActivity(
                    app,
                    code,
                    Intent(app, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                runCatching {
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger.atMillis, show), pending)
                    nextIds += code.toString()
                }
            }
        }
        store.edit().putStringSet(KEY_IDS, nextIds).apply()
    }

    private data class Trigger(val atMillis: Long, val tag: String)

    private fun triggers(entry: DailyJournalEntry, now: Long): List<Trigger> {
        if (!entry.reminderOn || entry.allDay || entry.hour == null) return emptyList()
        val start = nextStartMillis(entry, now) ?: return emptyList()
        val out = mutableListOf(Trigger(start, "start"))
        val lead = entry.reminderMinutes.coerceAtLeast(0) * 60_000L
        if (lead > 0) {
            val remindAt = start - lead
            if (remindAt > now) out += Trigger(remindAt, "remind")
        }
        return out
    }

    private fun nextStartMillis(entry: DailyJournalEntry, now: Long): Long? {
        val hour = entry.hour ?: return null
        val minute = entry.minute ?: 0
        var day = runCatching { LocalDate.parse(entry.dayKey) }.getOrNull() ?: return null
        val zone = ZoneId.systemDefault()
        fun at(date: LocalDate): Long =
            date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        var whenMs = at(day)
        var guard = 0
        while (whenMs <= now && guard < 500) {
            day = advance(day, entry.repeat) ?: return null
            whenMs = at(day)
            guard++
        }
        return whenMs.takeIf { it > now }
    }

    private fun advance(day: LocalDate, repeat: String): LocalDate? = when (repeat) {
        "daily" -> day.plusDays(1)
        "weekly" -> day.plusWeeks(1)
        "monthly" -> day.plusMonths(1)
        "yearly" -> day.plusYears(1)
        else -> null
    }

    private fun alarmPending(context: Context, code: Int): PendingIntent {
        val intent = Intent(context, JournalAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
