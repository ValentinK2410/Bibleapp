package com.example.bible.data.travel

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.bible.receiver.TravelGeofenceReceiver
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import kotlin.math.max

object TravelGeofenceManager {

    private const val MIN_RADIUS_M = 100f

    fun geofencePendingIntent(context: Context): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val intent = Intent(context, TravelGeofenceReceiver::class.java)
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    fun sync(context: Context, zones: List<TravelZone>) {
        val client = LocationServices.getGeofencingClient(context.applicationContext)
        val pi = geofencePendingIntent(context.applicationContext)
        try {
            Tasks.await(client.removeGeofences(pi))
        } catch (_: Exception) {
            // ignore
        }
        val circles = zones.filter { it.enabled && it.kind == TravelZoneKind.CIRCLE }
        if (circles.isEmpty()) return
        val geofences = circles.map { z ->
            val r = max(z.radiusMeters, MIN_RADIUS_M)
            Geofence.Builder()
                .setRequestId(z.id)
                .setCircularRegion(z.centerLat, z.centerLng, r)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
        try {
            Tasks.await(client.addGeofences(request, pi))
        } catch (_: Exception) {
            // Разрешения или сервисы Google Play — экран карты покажет подсказку.
        }
    }
}
