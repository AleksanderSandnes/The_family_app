@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.settings

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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandnes.familyapp.BuildConfig
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.ThemeMode
import com.sandnes.familyapp.ui.components.FeatureTopBar
import com.sandnes.familyapp.ui.components.appSwitchColors
import com.sandnes.familyapp.ui.theme.Radius
import com.sandnes.familyapp.ui.theme.glassCard
import com.sandnes.familyapp.util.LocaleManager
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

private val LEAD_TIME_OPTIONS =
    listOf(
        R.string.same_day to 0,
        R.string.one_day to 1,
        R.string.two_days to 2,
        R.string.seven_days to 7,
    )

// Language picker options: stored tag + endonym label. Endonyms stay constant across languages;
// "System" localizes so it reads in the currently active language.
private val LANGUAGE_OPTIONS =
    listOf(
        LocaleManager.SYSTEM to R.string.system,
        "en" to R.string.english,
        "nb" to R.string.norwegian,
    )

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val notificationsEnabled by vm.notificationsEnabled.collectAsStateWithLifecycle()
    val notifyDaysBefore by vm.notifyDaysBefore.collectAsStateWithLifecycle()
    val locationVisible by vm.locationVisible.collectAsStateWithLifecycle()
    val appLanguage by vm.appLanguage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val settingsSavedMessage = stringResource(R.string.settings_saved)

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> vm.setNotificationsEnabled(granted) }

    // Show "Settings saved" whenever any preference changes after the initial composition.
    // drop(1) on each snapshotFlow skips the current value at subscription time (the
    // already-settled DataStore value), so only real user-driven changes trigger the snackbar.
    LaunchedEffect(Unit) {
        merge(
            snapshotFlow { themeMode }.drop(1).map { },
            snapshotFlow { notificationsEnabled }.drop(1).map { },
            snapshotFlow { notifyDaysBefore }.drop(1).map { },
            snapshotFlow { locationVisible }.drop(1).map { },
            snapshotFlow { appLanguage }.drop(1).map { },
        ).collect {
            snackbarHostState.showSnackbar(settingsSavedMessage)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { FeatureTopBar(stringResource(R.string.settings), onBack) },
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
            SettingsSectionHeader(stringResource(R.string.appearance))
            SettingsCard {
                ThemeSelector(selected = themeMode, onSelect = vm::setThemeMode)
            }

            // ── NOTIFICATIONS ────────────────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.notifications))
            SettingsCard {
                ToggleRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.notifications),
                    subtitle = stringResource(R.string.family_activity_and_reminders),
                    checked = notificationsEnabled,
                    onChange = { enabled ->
                        if (!enabled) {
                            vm.setNotificationsEnabled(false)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.setNotificationsEnabled(true)
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
            SettingsSectionHeader(stringResource(R.string.privacy))
            SettingsCard {
                ToggleRow(
                    icon = Icons.Filled.LocationOn,
                    title = stringResource(R.string.visible_on_family_map),
                    subtitle = stringResource(R.string.share_your_location_with_family),
                    checked = locationVisible,
                    onChange = { vm.setLocationVisible(it) },
                )
            }

            // ── LANGUAGE ─────────────────────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.language))
            SettingsCard {
                LanguageSelector(selected = appLanguage, onSelect = vm::setAppLanguage)
            }

            // ── ABOUT ────────────────────────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.about))
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
    Column(
        Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = Radius.overviewCard),
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
            stringResource(R.string.remind_me),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LEAD_TIME_OPTIONS.forEach { (labelRes, days) ->
                LeadTimeChip(
                    label = stringResource(labelRes),
                    selected = selected == days,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(days) },
                )
            }
        }
    }
}

@Composable
private fun LanguageSelector(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.language),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LANGUAGE_OPTIONS.forEach { (tag, labelRes) ->
                LeadTimeChip(
                    label = stringResource(labelRes),
                    selected = selected == tag,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(tag) },
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
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp))
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
                Text(
                    stringResource(R.string.theme),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeOption(
                icon = Icons.Filled.BrightnessAuto,
                label = stringResource(R.string.system),
                selected = selected == ThemeMode.SYSTEM,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(ThemeMode.SYSTEM) },
            )
            ThemeOption(
                icon = Icons.Filled.LightMode,
                label = stringResource(R.string.light),
                selected = selected == ThemeMode.LIGHT,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(ThemeMode.LIGHT) },
            )
            ThemeOption(
                icon = Icons.Filled.DarkMode,
                label = stringResource(R.string.dark),
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
                }.clickable(onClick = onClick)
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
            colors = appSwitchColors(),
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
                modifier =
                    Modifier
                        .padding(12.dp)
                        .size(24.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.the_family_app),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.app_version_format, versionName, versionCode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.your_family_together),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
