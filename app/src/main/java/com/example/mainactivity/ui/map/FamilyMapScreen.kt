@file:Suppress("ktlint:standard:function-naming")

package com.example.mainactivity.ui.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.location.Geocoder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.example.mainactivity.data.UserLocationModel
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.ui.components.EmptyState
import com.example.mainactivity.ui.components.FeatureTopBar
import com.example.mainactivity.ui.components.InitialAvatar
import com.example.mainactivity.ui.components.LoadingState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun FamilyMapScreen(
    onBack: () -> Unit,
    viewModel: FamilyMapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val userProfiles by viewModel.userProfiles.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
    val cameraPositionState = rememberCameraPositionState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val markerBitmaps = remember { mutableStateMapOf<String, BitmapDescriptor>() }
    val markerSizePx = remember(density) { (72 * density).toInt() }

    fun fgGranted() =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    fun bgGranted() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    var foregroundGranted by remember { mutableStateOf(fgGranted()) }
    var backgroundGranted by remember { mutableStateOf(bgGranted()) }
    var rationaleShown by rememberSaveable { mutableStateOf(false) }
    var showRationaleDialog by remember { mutableStateOf(false) }

    val fgLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            val granted =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            foregroundGranted = granted
            if (granted) {
                viewModel.startLocationUpdates()
                if (backgroundGranted && !LocationForegroundService.isRunning) {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, LocationForegroundService::class.java),
                    )
                }
            }
        }

    val bgLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            backgroundGranted = granted
            if (granted) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LocationForegroundService::class.java),
                )
            }
        }

    // Show permission rationale dialog once per session before requesting location
    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = {
                showRationaleDialog = false
                rationaleShown = true
                scope.launch {
                    snackbarHostState.showSnackbar("You can enable location in Settings")
                }
            },
            title = { Text("Location access") },
            text = {
                Text(
                    "This app uses your location to show your family where you are on the map. " +
                        "Your location is only shared when you enable it.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                    rationaleShown = true
                    fgLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                    rationaleShown = true
                    scope.launch {
                        snackbarHostState.showSnackbar("You can enable location in Settings")
                    }
                }) { Text("Not now") }
            },
        )
    }

    LaunchedEffect(Unit) {
        if (foregroundGranted) {
            viewModel.startLocationUpdates()
            if (backgroundGranted && !LocationForegroundService.isRunning) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LocationForegroundService::class.java),
                )
            }
        } else if (!rationaleShown) {
            showRationaleDialog = true
        }
    }

    LaunchedEffect(myLocation) {
        myLocation?.let { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 14f)) }
    }

    // Build marker bitmaps for visible locations only
    LaunchedEffect(userProfiles, locations) {
        val visibleLocations = locations.filter { it.visible }
        visibleLocations.forEach { loc ->
            val profile = userProfiles[loc.userId]
            val bitmap =
                withContext(Dispatchers.Default) {
                    buildMarkerBitmap(context, profile, loc.displayName, markerSizePx)
                }
            markerBitmaps[loc.userId] = BitmapDescriptorFactory.fromBitmap(bitmap)
        }
        // Remove stale bitmaps for users no longer visible
        val visibleIds = visibleLocations.map { it.userId }.toSet()
        markerBitmaps.keys.retainAll(visibleIds)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopLocationUpdates()
            if (!LocationForegroundService.isRunning) viewModel.clearOwnLocation()
        }
    }

    val isSolo = userProfiles.size == 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FeatureTopBar("Family Map", onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (isLoading && locations.isEmpty() && !isSolo) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState() }
            } else {
                GoogleMap(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "Family location map" },
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = foregroundGranted),
                ) {
                    locations.filter { it.visible }.forEach { loc ->
                        Marker(
                            state = rememberUpdatedMarkerState(position = LatLng(loc.lat, loc.lng)),
                            title = loc.displayName,
                            icon = markerBitmaps[loc.userId],
                        )
                    }
                }

                // Empty state overlay when current user is the only family member
                if (isSolo) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            icon = Icons.Filled.PeopleAlt,
                            title = "Just you here",
                            subtitle = "Invite family members to see their locations on the map.",
                        )
                    }
                }

                // Bottom-right column: My Location FAB stacked above optional cards
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .fillMaxWidth()
                            .padding(16.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                myLocation?.let {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(it, 15f),
                                    )
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = "Center on my location")
                    }

                    if (foregroundGranted &&
                        !backgroundGranted &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ) {
                        Spacer(Modifier.size(8.dp))
                        BackgroundPermissionCard(
                            onRequest = {
                                bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (!isSolo) {
                        Spacer(Modifier.size(8.dp))
                        MemberLegend(
                            locations = locations,
                            profiles = userProfiles,
                            currentUserId = currentUserId,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private suspend fun buildMarkerBitmap(
    context: Context,
    profile: UserModel?,
    displayName: String,
    sizePx: Int,
): Bitmap {
    val name = profile?.name?.ifBlank { displayName } ?: displayName
    val colorInt = profile?.avatarColor?.takeIf { it != 0 } ?: 0xFF6366F1.toInt()
    if (profile?.avatarUrl != null) {
        val photo = loadCircularBitmap(context, profile.avatarUrl, sizePx)
        if (photo != null) return photo
    }
    return createInitialsBitmap(name, colorInt, sizePx)
}

private const val AVATAR_BORDER_STROKE_RATIO = 0.07f
private const val AVATAR_BORDER_INSET_RATIO = 0.035f
private const val AVATAR_INITIAL_TEXT_RATIO = 0.42f
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_DAY = 86400

private fun createInitialsBitmap(
    name: String,
    colorInt: Int,
    sizePx: Int,
): Bitmap {
    val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = sizePx / 2f

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorInt }
    canvas.drawCircle(radius, radius, radius, bgPaint)

    val borderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = sizePx * AVATAR_BORDER_STROKE_RATIO
        }
    canvas.drawCircle(radius, radius, radius - sizePx * AVATAR_BORDER_INSET_RATIO, borderPaint)

    val initial =
        name
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString() ?: "?"
    val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = sizePx * AVATAR_INITIAL_TEXT_RATIO
            typeface = Typeface.DEFAULT_BOLD
        }
    val bounds = Rect()
    textPaint.getTextBounds(initial, 0, initial.length, bounds)
    canvas.drawText(initial, radius, radius - bounds.exactCenterY(), textPaint)
    return bitmap
}

private suspend fun loadCircularBitmap(
    context: Context,
    url: String,
    sizePx: Int,
): Bitmap? {
    return try {
        val request =
            ImageRequest
                .Builder(context)
                .data(url)
                .size(sizePx)
                .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val source = result.image.toBitmap()

        val output = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, clipPaint)
        val xferPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            }
        canvas.drawBitmap(source.scale(sizePx, sizePx), 0f, 0f, xferPaint)

        val borderPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = sizePx * AVATAR_BORDER_STROKE_RATIO
            }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - sizePx * AVATAR_BORDER_INSET_RATIO, borderPaint)
        output
    } catch (_: Exception) {
        null
    }
}

/** Formats an ISO-8601 timestamp as a human-readable "last seen" string. */
private fun formatLastSeen(updatedAt: String?): String {
    updatedAt ?: return "Unknown"
    return try {
        val instant = java.time.Instant.parse(updatedAt)
        val seconds =
            java.time.Duration
                .between(instant, java.time.Instant.now())
                .seconds
        when {
            seconds < SECONDS_PER_MINUTE -> "Just now" // also handles clock-skew futures (negative seconds)
            seconds < SECONDS_PER_HOUR -> "${seconds / SECONDS_PER_MINUTE} min ago"
            seconds < SECONDS_PER_DAY -> "${seconds / SECONDS_PER_HOUR} hours ago"
            else ->
                instant
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                    .format(
                        java.time.format.DateTimeFormatter
                            .ofPattern("MMM d, yyyy"),
                    )
        }
    } catch (_: Exception) {
        "Location shared"
    }
}

@Composable
private fun BackgroundPermissionCard(
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Share in background",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "Let family see you even when the app is closed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.size(10.dp))
            Button(onClick = onRequest) { Text("Allow") }
        }
    }
}

/** Reverse-geocodes each shared location to a short place name (locality / area), cached by
 *  user id. Runs off the main thread; returns partial results as they resolve. */
@Composable
private fun rememberGeocodedPlaces(
    locations: List<UserLocationModel>,
    context: Context,
): Map<String, String> {
    val places = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(locations) {
        if (!Geocoder.isPresent()) return@LaunchedEffect
        val geocoder = Geocoder(context, Locale.getDefault())
        locations.forEach { loc ->
            if (places.containsKey(loc.userId)) return@forEach
            val place =
                withContext(Dispatchers.IO) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        geocoder
                            .getFromLocation(loc.lat, loc.lng, 1)
                            ?.firstOrNull()
                            ?.let { it.locality ?: it.subAdminArea ?: it.thoroughfare ?: it.adminArea }
                    }.getOrNull()
                }
            if (!place.isNullOrBlank()) places[loc.userId] = place
        }
    }
    return places
}

@Composable
private fun MemberLegend(
    locations: List<UserLocationModel>,
    profiles: Map<String, UserModel>,
    currentUserId: String?,
    modifier: Modifier = Modifier,
) {
    val locationByUserId = locations.associateBy { it.userId }
    val context = LocalContext.current
    val places = rememberGeocodedPlaces(locations, context)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "On the map",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(6.dp))
            profiles.values
                .filter { currentUserId != null && it.id != currentUserId }
                .forEach { member ->
                    val loc = locationByUserId[member.id]
                    val isSharing = loc != null
                    val rowAlpha = if (isSharing) 1f else 0.4f
                    val lastSeen = if (isSharing) formatLastSeen(loc.updatedAt) else "Not sharing"
                    val place = places[member.id]
                    val statusText =
                        if (isSharing && !place.isNullOrBlank()) "$place · $lastSeen" else lastSeen
                    val avatarColor =
                        member.avatarColor
                            .takeIf { it != 0 }
                            ?.let { Color(it) }
                            ?: MaterialTheme.colorScheme.primary
                    val rowDesc =
                        if (isSharing) {
                            "${member.name}, last seen $statusText"
                        } else {
                            "${member.name}, not sharing location"
                        }

                    Row(
                        modifier =
                            Modifier
                                .padding(vertical = 4.dp)
                                .fillMaxWidth()
                                .alpha(rowAlpha)
                                .clearAndSetSemantics { contentDescription = rowDesc },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InitialAvatar(
                            name = member.name,
                            color = avatarColor,
                            size = 36,
                            avatarUri = member.avatarUrl,
                        )
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(
                                member.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
        }
    }
}
