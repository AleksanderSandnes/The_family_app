package com.example.mainactivity.ui.map

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.mainactivity.MainActivity
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.remote.SupabaseManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class LocationForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (locationCallback != null) return
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            5 * 60_000L
        ).setMinUpdateIntervalMillis(3 * 60_000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                scope.launch { publish(loc.latitude, loc.longitude) }
            }
        }
        fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    private suspend fun publish(lat: Double, lng: Double) {
        val repo = FamilyRepository.get(this)
        val userId = repo.currentUserId.first() ?: return
        val locationVisible = repo.locationVisible.first()
        runCatching {
            val user = repo.getUser(userId) ?: return
            SupabaseManager.client.postgrest.from("user_locations").upsert(buildJsonObject {
                put("user_id", userId)
                put("family_id", user.familyId)
                put("lat", lat)
                put("lng", lng)
                put("display_name", user.name)
                put("visible", locationVisible)
                put("updated_at", Instant.now().toString())
            })
        }
    }

    override fun onDestroy() {
        isRunning = false
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        scope.launch {
            runCatching {
                val repo = FamilyRepository.get(this@LocationForegroundService)
                val userId = repo.currentUserId.first() ?: return@launch
                SupabaseManager.client.postgrest.from("user_locations").update({
                    set("visible", false)
                }) { filter { eq("user_id", userId) } }
            }
        }
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location sharing",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown while your location is shared with family" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Sharing location with family")
        .setContentText("Your position is visible on the family map")
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    companion object {
        var isRunning = false
            private set

        private const val CHANNEL_ID = "location_sharing"
        private const val NOTIFICATION_ID = 9001
    }
}
