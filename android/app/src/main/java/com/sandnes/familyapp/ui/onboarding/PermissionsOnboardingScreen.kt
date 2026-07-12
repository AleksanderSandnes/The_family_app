@file:Suppress("ktlint:standard:function-naming", "InlinedApi")

package com.sandnes.familyapp.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sandnes.familyapp.R
import com.sandnes.familyapp.ui.theme.Amber500
import com.sandnes.familyapp.ui.theme.Indigo500
import com.sandnes.familyapp.ui.theme.Indigo700
import com.sandnes.familyapp.ui.theme.Pink500
import com.sandnes.familyapp.ui.theme.Teal500
import com.sandnes.familyapp.ui.theme.Violet600
import kotlinx.coroutines.delay

@Composable
fun PermissionsOnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current

    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    // Combined launcher used by the "Continue" button — requests all ungranted permissions at once.
    val combinedLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            notifGranted = results[Manifest.permission.POST_NOTIFICATIONS] ?: notifGranted
            locationGranted =
                results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                locationGranted
            cameraGranted = results[Manifest.permission.CAMERA] ?: cameraGranted
            micGranted = results[Manifest.permission.RECORD_AUDIO] ?: micGranted
            onComplete()
        }

    // Per-card launchers for re-requesting individual permissions by tapping a card.
    val notifLauncher =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                notifGranted = granted
            }
        } else {
            null
        }

    val locationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            locationGranted =
                results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraGranted = granted
        }

    val micLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            micGranted = granted
        }

    val cards =
        listOf(
            PermissionCardData(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.notifications),
                description = stringResource(R.string.perm_notifications_desc),
                accentColor = Indigo500,
                granted = notifGranted,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            ),
            PermissionCardData(
                icon = Icons.Filled.LocationOn,
                title = stringResource(R.string.location),
                description = stringResource(R.string.perm_location_desc),
                accentColor = Teal500,
                granted = locationGranted,
                onRequest = {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
            ),
            PermissionCardData(
                icon = Icons.Filled.CameraAlt,
                title = stringResource(R.string.camera),
                description = stringResource(R.string.perm_camera_desc),
                accentColor = Pink500,
                granted = cameraGranted,
                onRequest = { cameraLauncher.launch(Manifest.permission.CAMERA) },
            ),
            PermissionCardData(
                icon = Icons.Filled.Mic,
                title = stringResource(R.string.microphone),
                description = stringResource(R.string.perm_microphone_desc),
                accentColor = Amber500,
                granted = micGranted,
                onRequest = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            ),
        )

    var cardsVisible by remember { mutableStateOf(List(cards.size) { false }) }
    LaunchedEffect(Unit) {
        cards.indices.forEach { i ->
            delay(150L * i)
            cardsVisible = cardsVisible.toMutableList().also { it[i] = true }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Indigo700, Violet600),
                    ),
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 64.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.FamilyRestroom,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.before_we_start),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_permissions_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            cards.forEachIndexed { index, card ->
                AnimatedVisibility(
                    visible = cardsVisible[index],
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
                ) {
                    PermissionCardItem(card = card)
                }
                if (index < cards.lastIndex) Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    val toRequest = mutableListOf<String>()
                    if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (!locationGranted) {
                        toRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
                        toRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    if (!cameraGranted) toRequest.add(Manifest.permission.CAMERA)
                    if (!micGranted) toRequest.add(Manifest.permission.RECORD_AUDIO)

                    if (toRequest.isEmpty()) {
                        onComplete()
                    } else {
                        combinedLauncher.launch(toRequest.toTypedArray())
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Indigo700,
                    ),
            ) {
                Text(
                    stringResource(R.string.continue_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onComplete) {
                Text(stringResource(R.string.skip_for_now), color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

private data class PermissionCardData(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val accentColor: Color,
    val granted: Boolean,
    val onRequest: () -> Unit,
)

@Composable
private fun PermissionCardItem(card: PermissionCardData) {
    val semanticsLabel =
        if (card.granted) {
            stringResource(R.string.permission_granted_a11y, card.title)
        } else {
            stringResource(R.string.permission_not_granted_a11y, card.title)
        }
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .semantics { contentDescription = semanticsLabel }
                .clickable(enabled = !card.granted) { card.onRequest() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(card.accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    card.icon,
                    contentDescription = null,
                    tint = card.accentColor,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    card.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    card.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (card.granted) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4ADE80),
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
