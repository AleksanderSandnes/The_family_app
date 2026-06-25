@file:Suppress("ktlint:standard:function-naming")

package com.example.mainactivity.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mainactivity.BuildConfig
import com.example.mainactivity.data.ThemeMode
import com.example.mainactivity.ui.components.FeatureTopBar
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

private val LEAD_TIME_OPTIONS =
    listOf(
        "Same day" to 0,
        "1 day" to 1,
        "2 days" to 2,
        "7 days" to 7,
    )

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val notificationsEnabled by vm.notificationsEnabled.collectAsStateWithLifecycle()
    val notifyDaysBefore by vm.notifyDaysBefore.collectAsStateWithLifecycle()
    val locationVisible by vm.locationVisible.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> vm.setNotificationsEnabled(granted, context) }

    // Show "Settings saved" whenever any preference changes after the initial composition.
    // drop(1) on each snapshotFlow skips the current value at subscription time (the
    // already-settled DataStore value), so only real user-driven changes trigger the snackbar.
    LaunchedEffect(Unit) {
        merge(
            snapshotFlow { themeMode }.drop(1).map { },
            snapshotFlow { notificationsEnabled }.drop(1).map { },
            snapshotFlow { notifyDaysBefore }.drop(1).map { },
            snapshotFlow { locationVisible }.drop(1).map { },
        ).collect {
            snackbarHostState.showSnackbar("Settings saved")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FeatureTopBar("Settings", onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // ── APPEARANCE ──────────────────────────────────────────────────
            SettingsSectionHeader("Appearance")
            SettingsCard {
                ThemeSelector(selected = themeMode, onSelect = vm::setThemeMode)
            }

            // ── NOTIFICATIONS ────────────────────────────────────────────────
            SettingsSectionHeader("Notifications")
            SettingsCard {
                ToggleRow(
                    icon = Icons.Filled.Notifications,
                    title = "Notifications",
                    subtitle = "Family activity and reminders",
                    checked = notificationsEnabled,
                    onChange = { enabled ->
                        if (!enabled) {
                            vm.setNotificationsEnabled(false, context)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.setNotificationsEnabled(true, context)
                        }
                    },
                )
                AnimatedVisibility(visible = notificationsEnabled) {
                    Column {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        LeadTimeSelector(selected = notifyDaysBefore, onSelect = vm::setNotifyDaysBefore)
                    }
                }
            }

            // ── PRIVACY ──────────────────────────────────────────────────────
            SettingsSectionHeader("Privacy")
            SettingsCard {
                ToggleRow(
                    icon = Icons.Filled.LocationOn,
                    title = "Visible on family map",
                    subtitle = "Share your location with family",
                    checked = locationVisible,
                    onChange = { vm.setLocationVisible(it) },
                )
            }

            // ── ABOUT ────────────────────────────────────────────────────────
            SettingsSectionHeader("About")
            SettingsCard {
                AboutSection()
            }

            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun LeadTimeSelector(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Remind me",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LEAD_TIME_OPTIONS.forEach { (label, days) ->
                LeadTimeChip(
                    label = label,
                    selected = selected == days,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(days) },
                )
            }
        }
    }
}

@Composable
private fun LeadTimeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .clickable(onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun ThemeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Appearance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Choose how the app looks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeOption(
                icon = Icons.Filled.BrightnessAuto,
                label = "System",
                selected = selected == ThemeMode.SYSTEM,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(ThemeMode.SYSTEM) },
            )
            ThemeOption(
                icon = Icons.Filled.LightMode,
                label = "Light",
                selected = selected == ThemeMode.LIGHT,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(ThemeMode.LIGHT) },
            )
            ThemeOption(
                icon = Icons.Filled.DarkMode,
                label = "Dark",
                selected = selected == ThemeMode.DARK,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(ThemeMode.DARK) },
            )
        }
    }
}

@Composable
private fun ThemeOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .semantics {
                    contentDescription = "$label theme${if (selected) ", selected" else ""}"
                }
                .clickable(onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg)
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val description = "$title ${if (checked) "on" else "off"}"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
private fun AboutSection() {
    val versionName = BuildConfig.VERSION_NAME
    val versionCode = BuildConfig.VERSION_CODE
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(12.dp)
                    .size(24.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "The Family App",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "v$versionName (build $versionCode)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Your family, together",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
