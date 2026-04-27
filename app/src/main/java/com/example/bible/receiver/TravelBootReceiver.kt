package com.example.bible.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.bible.data.travel.TravelGeofenceManager
import com.example.bible.data.travel.TravelZoneKind
import com.example.bible.data.travel.TravelZoneRepository
import com.example.bible.service.TravelMonitorService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class TravelBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        runBlocking {
            val repo = TravelZoneRepository(app)
            val zones = repo.snapshot()
            val polygonMonitor = repo.polygonMonitorEnabled.first()
            TravelGeofenceManager.sync(app, zones)
            val hasPoly = zones.any {
                it.enabled && it.kind == TravelZoneKind.POLYGON && it.polygonPoints.size >= 3
            }
            if (polygonMonitor && hasPoly) {
                TravelMonitorService.start(app)
            }
        }
    }
}
