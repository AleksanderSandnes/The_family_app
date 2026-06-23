package com.example.mainactivity.ui.map

import android.annotation.SuppressLint
import android.app.Application
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.UserLocationModel
import com.example.mainactivity.data.remote.SupabaseManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class FamilyMapViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)
    private val db get() = SupabaseManager.client.postgrest

    private val _myLocation = MutableStateFlow<LatLng?>(null)
    val myLocation: StateFlow<LatLng?> = _myLocation.asStateFlow()

    private val _locations = MutableStateFlow<List<UserLocationModel>>(emptyList())
    val locations: StateFlow<List<UserLocationModel>> = _locations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(app)
    private var locationCallback: LocationCallback? = null
    private var realtimeChannel: RealtimeChannel? = null
    private var currentUserId: String? = null
    private var currentFamilyId: String? = null

    init {
        viewModelScope.launch {
            repo.currentUserId.collect { userId ->
                currentUserId = userId
                if (userId != null) {
                    val user = repo.getUser(userId)
                    currentFamilyId = user?.familyId
                    loadLocations()
                    if (user?.familyId != null) subscribeToLocations(user.familyId)
                }
            }
        }
    }

    private suspend fun loadLocations() {
        val familyId = currentFamilyId ?: return
        val myId = currentUserId ?: return
        _isLoading.value = true
        runCatching {
            _locations.value = db.from("user_locations").select {
                filter {
                    eq("family_id", familyId)
                    eq("visible", true)
                }
            }.decodeList<UserLocationModel>().filter { it.userId != myId }
        }
        _isLoading.value = false
    }

    private suspend fun subscribeToLocations(familyId: String) {
        realtimeChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
        val channel = SupabaseManager.client.channel("locations-$familyId")
        realtimeChannel = channel
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "user_locations"
            filter("family_id", FilterOperator.EQ, familyId)
        }
        channel.subscribe()
        viewModelScope.launch {
            changeFlow.collect { loadLocations() }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (locationCallback != null) return
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            30_000L
        ).setMinUpdateIntervalMillis(15_000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                _myLocation.value = LatLng(loc.latitude, loc.longitude)
                viewModelScope.launch { publishLocation(loc.latitude, loc.longitude) }
            }
        }
        fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }

    fun clearOwnLocation() {
        viewModelScope.launch { clearOwnLocationSuspend() }
    }

    private suspend fun publishLocation(lat: Double, lng: Double) {
        val userId = currentUserId ?: return
        val locationVisible = repo.locationVisible.first()
        runCatching {
            val user = repo.getUser(userId) ?: return
            db.from("user_locations").upsert(buildJsonObject {
                put("user_id", userId)
                put("family_id", user.familyId)
                put("lat", lat)
                put("lng", lng)
                put("display_name", user.name)
                put("visible", locationVisible)
                put("updated_at", java.time.Instant.now().toString())
            })
        }
    }

    private suspend fun clearOwnLocationSuspend() {
        val userId = currentUserId ?: return
        runCatching {
            db.from("user_locations").update({
                set("visible", false)
            }) { filter { eq("user_id", userId) } }
        }
    }

    override fun onCleared() {
        stopLocationUpdates()
        realtimeChannel?.let {
            viewModelScope.launch { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
        }
        // visibility cleared by screen's DisposableEffect unless service takes over
    }
}
