package com.example.bible.data.travel

import android.content.Context
import android.net.Uri
import com.example.bible.R
import com.example.bible.service.TravelMediaService

/**
 * Срабатывание звука при приближении к отметке (GPS) или тапу рядом с ней на карте.
 * Отдельные кулдауны для «подошёл» и «тапнул».
 */
object TravelMarkerSoundTrigger {

    private const val PREFS = "travel_marker_sound_state"
    private const val PREF_NEAR = "near_"
    private const val PREF_LAST_APPROACH = "appr_"
    private const val PREF_LAST_TAP = "tap_"

    /** Радиус «внутри» отметки для GPS, м. */
    const val APPROACH_RADIUS_METERS = 42.0

    /** Макс. расстояние тапа до отметки, м (зависит от масштаба карты). */
    const val TAP_MAX_DISTANCE_METERS = 55.0

    fun wasNearMarker(context: Context, incidentId: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREF_NEAR + incidentId, false)

    fun setNearMarker(context: Context, incidentId: String, near: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(PREF_NEAR + incidentId, near)
            .apply()
    }

    private fun canApproach(context: Context, incidentId: String, cooldownMs: Long): Boolean {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = sp.getLong(PREF_LAST_APPROACH + incidentId, 0L)
        return System.currentTimeMillis() - last >= cooldownMs
    }

    private fun markApproach(context: Context, incidentId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(PREF_LAST_APPROACH + incidentId, System.currentTimeMillis())
            .apply()
    }

    private fun canTap(context: Context, incidentId: String, cooldownMs: Long): Boolean {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = sp.getLong(PREF_LAST_TAP + incidentId, 0L)
        return System.currentTimeMillis() - last >= cooldownMs
    }

    private fun markTap(context: Context, incidentId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(PREF_LAST_TAP + incidentId, System.currentTimeMillis())
            .apply()
    }

    fun onGpsApproach(
        context: Context,
        incidentId: String,
        soundUriString: String,
        title: String,
        approachCooldownMs: Long = 110_000L,
    ) {
        if (soundUriString.isBlank()) return
        if (!canApproach(context, incidentId, approachCooldownMs)) return
        markApproach(context, incidentId)
        play(context, soundUriString, title)
    }

    fun onMapTapNear(
        context: Context,
        incidentId: String,
        soundUriString: String,
        title: String,
        tapCooldownMs: Long = 18_000L,
    ) {
        if (soundUriString.isBlank()) return
        if (!canTap(context, incidentId, tapCooldownMs)) return
        markTap(context, incidentId)
        play(context, soundUriString, title)
    }

    private fun play(context: Context, soundUriString: String, title: String) {
        val app = context.applicationContext
        val uri = runCatching { Uri.parse(soundUriString) }.getOrNull() ?: return
        TravelMediaService.startSound(
            app,
            uri,
            title.ifBlank { app.getString(R.string.travel_incidents_header) },
        )
    }
}

/**
 * Тап по зоне (круг/полигон) с действием «Воспроизвести звук» и своим [TravelZone.mediaUri].
 */
object TravelZoneTapSound {

    private const val PREFS = "travel_zone_tap_sound"
    private const val PREF_LAST_TAP = "tap_"

    private fun canTap(context: Context, zoneId: String, cooldownMs: Long): Boolean {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = sp.getLong(PREF_LAST_TAP + zoneId, 0L)
        return System.currentTimeMillis() - last >= cooldownMs
    }

    private fun markTap(context: Context, zoneId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(PREF_LAST_TAP + zoneId, System.currentTimeMillis())
            .apply()
    }

    fun onMapTapInZone(
        context: Context,
        zoneId: String,
        soundUriString: String,
        title: String,
        tapCooldownMs: Long = 18_000L,
    ) {
        if (soundUriString.isBlank()) return
        if (!canTap(context, zoneId, tapCooldownMs)) return
        markTap(context, zoneId)
        val app = context.applicationContext
        val uri = runCatching { Uri.parse(soundUriString) }.getOrNull() ?: return
        TravelMediaService.startSound(
            app,
            uri,
            title.ifBlank { app.getString(R.string.travel_notif_title) },
        )
    }
}
