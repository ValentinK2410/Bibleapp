package com.example.bible.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.bible.R
import com.example.bible.data.travel.TravelTriggerExecutor
import com.example.bible.data.travel.TravelZoneKind
import com.example.bible.data.travel.TravelMarkerSoundTrigger
import com.example.bible.data.travel.TravelZoneRepository
import com.example.bible.data.travel.pointInPolygon
import com.example.bible.data.travel.travelDistanceMeters
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.example.bible.data.travel.TravelGeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Фоновое отслеживание для полигональных зон (круги обрабатываются через Geofencing API).
 */
class TravelMonitorService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private var callback: LocationCallback? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopUpdates()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            NOTIF_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(getString(R.string.travel_monitor_notif_title))
                .setContentText(getString(R.string.travel_monitor_notif_text))
                .setOngoing(true)
                .build(),
        )
        startUpdates()
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.travel_monitor_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun startUpdates() {
        if (callback != null) return
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                evaluate(TravelGeoPoint(loc.latitude, loc.longitude))
            }
        }
        callback = cb
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            25_000L,
        ).setMinUpdateIntervalMillis(15_000L).build()
        try {
            fused.requestLocationUpdates(request, cb, Looper.getMainLooper())
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun evaluate(position: TravelGeoPoint) {
        serviceScope.launch {
            val repo = TravelZoneRepository(this@TravelMonitorService)
            val zones = withContext(Dispatchers.IO) {
                repo.snapshot()
            }.filter {
                it.enabled && it.kind == TravelZoneKind.POLYGON && it.polygonPoints.size >= 3
            }
            for (z in zones) {
                val inside = pointInPolygon(position, z.polygonPoints)
                val wasInside = TravelTriggerExecutor.isPolygonInside(this@TravelMonitorService, z.id)
                if (inside && !wasInside) {
                    TravelTriggerExecutor.onEnterZone(this@TravelMonitorService, z.id)
                }
                TravelTriggerExecutor.setPolygonInside(this@TravelMonitorService, z.id, inside)
            }
            val markerProx = repo.markerProximityEnabled.first()
            val defaultSound = repo.markerDefaultSoundUri.first()
            val incidents = withContext(Dispatchers.IO) { repo.snapshotIncidents() }
            if (markerProx && (defaultSound != null || incidents.any { !it.soundUri.isNullOrBlank() })) {
                for (inc in incidents) {
                    val uri = inc.soundUri ?: defaultSound
                    if (uri.isNullOrBlank()) continue
                    val dist = travelDistanceMeters(
                        position,
                        TravelGeoPoint(inc.latitude, inc.longitude),
                    )
                    val inside = dist <= TravelMarkerSoundTrigger.APPROACH_RADIUS_METERS
                    val wasNear = TravelMarkerSoundTrigger.wasNearMarker(this@TravelMonitorService, inc.id)
                    if (inside && !wasNear) {
                        val title = inc.note.trim().ifEmpty {
                            getString(R.string.travel_incidents_header)
                        }
                        TravelMarkerSoundTrigger.onGpsApproach(
                            this@TravelMonitorService,
                            inc.id,
                            uri,
                            title,
                        )
                    }
                    TravelMarkerSoundTrigger.setNearMarker(this@TravelMonitorService, inc.id, inside)
                }
            }
        }
    }

    private fun stopUpdates() {
        callback?.let { runCatching { fused.removeLocationUpdates(it) } }
        callback = null
    }

    override fun onDestroy() {
        serviceJob.cancel()
        stopUpdates()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "TravelMonitorService.stop"
        private const val CHANNEL_ID = "travel_polygon_monitor"
        private const val NOTIF_ID = 7722

        fun start(ctx: android.content.Context) {
            ContextCompat.startForegroundService(
                ctx.applicationContext,
                Intent(ctx.applicationContext, TravelMonitorService::class.java),
            )
        }

        fun stop(ctx: android.content.Context) {
            ctx.applicationContext.startService(
                Intent(ctx.applicationContext, TravelMonitorService::class.java).apply {
                    action = ACTION_STOP
                },
            )
        }
    }
}
