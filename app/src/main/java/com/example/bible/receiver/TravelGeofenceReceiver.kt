package com.example.bible.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.bible.data.travel.TravelTriggerExecutor
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class TravelGeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition and Geofence.GEOFENCE_TRANSITION_ENTER == 0) return
        val list = event.triggeringGeofences ?: return
        for (g in list) {
            TravelTriggerExecutor.onEnterZone(context.applicationContext, g.requestId)
        }
    }
}
