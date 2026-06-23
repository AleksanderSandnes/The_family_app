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
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.ui.components.FeatureTopBar
import com.example.mainactivity.ui.components.LoadingState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FamilyMapScreen(
    onBack: () -> Unit,
    viewModel: FamilyMapViewModel = viewModel()
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val userProfiles by viewModel.userProfiles.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val cameraPositionState = rememberCameraPositionState()

    val markerBitmaps = remember { mutableStateMapOf<String, BitmapDescriptor>() }
    val markerSizePx = remember(density) { (72 * density).toInt() }

    fun fgGranted() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun bgGranted() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    var foregroundGranted by remember { mutableStateOf(fgGranted()) }
    var backgroundGranted by remember { mutableStateOf(bgGranted()) }

    val fgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        foregroundGranted = granted
        if (granted) viewModel.startLocationUpdates()
    }

    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        backgroundGranted = granted
        if (granted) {
            ContextCompat.startForegroundService(
                context, Intent(context, LocationForegroundService::class.java)
            )
        }
    }

    LaunchedEffect(Unit) {
        if (foregroundGranted) {
            viewModel.startLocationUpdates()
            if (backgroundGranted && !LocationForegroundService.isRunning) {
                ContextCompat.startForegroundService(
                    context, Intent(context, LocationForegroundService::class.java)
                )
            }
        } else {
            fgLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(myLocation) {
        myLocation?.let { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 14f)) }
    }

    // Build marker bitmaps whenever locations or profiles change
    LaunchedEffect(userProfiles, locations) {
        locations.forEach { loc ->
            val profile = userProfiles[loc.userId]
            val bitmap = withContext(Dispatchers.Default) {
                buildMarkerBitmap(context, profile, loc.displayName, markerSizePx)
            }
            markerBitmaps[loc.userId] = BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopLocationUpdates()
            if (!LocationForegroundService.isRunning) viewModel.clearOwnLocation()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FeatureTopBar("Family Map", onBack) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (isLoading && locations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState() }
            } else {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = foregroundGranted)
                ) {
                    locations.forEach { loc ->
                        Marker(
                            state = MarkerState(LatLng(loc.lat, loc.lng)),
                            title = loc.displayName,
                            icon = markerBitmaps[loc.userId]
                        )
                    }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (foregroundGranted && !backgroundGranted &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ) {
                        BackgroundPermissionCard(
                            onRequest = {
                                bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        )
                        Spacer(Modifier.size(8.dp))
                    }

                    if (locations.isNotEmpty()) {
                        MemberLegend(names = locations.map { it.displayName })
                    } else if (foregroundGranted) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.LocationOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    "No family members sharing location",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
    sizePx: Int
): Bitmap {
    val name = profile?.name?.ifBlank { displayName } ?: displayName
    val colorInt = profile?.avatarColor?.takeIf { it != 0 } ?: 0xFF6366F1.toInt()
    if (profile?.avatarUrl != null) {
        val photo = loadCircularBitmap(context, profile.avatarUrl, sizePx)
        if (photo != null) return photo
    }
    return createInitialsBitmap(name, colorInt, sizePx)
}

private fun createInitialsBitmap(name: String, colorInt: Int, sizePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = sizePx / 2f

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorInt }
    canvas.drawCircle(radius, radius, radius, bgPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.07f
    }
    canvas.drawCircle(radius, radius, radius - sizePx * 0.035f, borderPaint)

    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = sizePx * 0.42f
        typeface = Typeface.DEFAULT_BOLD
    }
    val bounds = Rect()
    textPaint.getTextBounds(initial, 0, initial.length, bounds)
    canvas.drawText(initial, radius, radius - bounds.exactCenterY(), textPaint)
    return bitmap
}

private suspend fun loadCircularBitmap(context: Context, url: String, sizePx: Int): Bitmap? {
    return try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(sizePx)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val source = (result.drawable as? BitmapDrawable)?.bitmap ?: return null

        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, clipPaint)
        val xferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(Bitmap.createScaledBitmap(source, sizePx, sizePx, true), 0f, 0f, xferPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.07f
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - sizePx * 0.035f, borderPaint)
        output
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun BackgroundPermissionCard(onRequest: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Share in background",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "Let family see you even when the app is closed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.size(10.dp))
            Button(onClick = onRequest) { Text("Allow") }
        }
    }
}

@Composable
private fun MemberLegend(names: List<String>, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "On the map",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.size(6.dp))
            names.forEach { name ->
                Row(
                    Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PersonPin, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
