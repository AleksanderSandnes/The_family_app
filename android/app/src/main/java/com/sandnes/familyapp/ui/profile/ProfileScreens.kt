@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.profile

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sandnes.familyapp.R
import com.sandnes.familyapp.ui.components.AppTopBar
import com.sandnes.familyapp.ui.components.BirthdayPickerField
import com.sandnes.familyapp.ui.components.DestructiveButton
import com.sandnes.familyapp.ui.components.ErrorBanner
import com.sandnes.familyapp.ui.components.FamilyTextField
import com.sandnes.familyapp.ui.components.FeatureTopBar
import com.sandnes.familyapp.ui.components.PrimaryButton
import com.sandnes.familyapp.ui.components.RefreshOnResume
import com.sandnes.familyapp.ui.components.SecondaryButton
import com.sandnes.familyapp.ui.theme.Radius
import com.sandnes.familyapp.ui.theme.Spacing
import com.sandnes.familyapp.ui.theme.glassCard
import com.sandnes.familyapp.ui.theme.heroGradient
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ProfileScreen(
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
    val needsCompletion by viewModel.needsProfileCompletion.collectAsStateWithLifecycle()
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val context = LocalContext.current

    var showAvatarPicker by remember { mutableStateOf(false) }

    RefreshOnResume { viewModel.refresh() }

    val galleryLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri -> uri?.let { viewModel.saveAvatarFromUri(context, it) } }

    val cameraLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
        ) { success -> viewModel.onCameraResult(success) }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) viewModel.prepareCameraCapture(context)?.let { cameraLauncher.launch(it) }
        }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { AppTopBar(stringResource(R.string.profile)) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.screenEdge, end = Spacing.screenEdge, top = Spacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ErrorBanner(error)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.bigCard))
                    .background(heroGradient(dark))
                    .padding(Spacing.xxl),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar circle — clickable to change photo (disabled during upload)
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !isUploading) { showAvatarPicker = true },
                    ) {
                        val avatarUri = user?.avatarUrl
                        var imgFailed by remember(avatarUri) { mutableStateOf(false) }
                        if (avatarUri != null && !imgFailed) {
                            AsyncImage(
                                model = avatarUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                onError = { imgFailed = true },
                            )
                        } else {
                            Box(
                                Modifier.fillMaxSize().clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    user
                                        ?.name
                                        ?.trim()
                                        ?.firstOrNull()
                                        ?.uppercase() ?: "?",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        // Upload spinner overlay (full circle) or camera strip (idle)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .height(if (isUploading) 72.dp else 20.dp)
                                .clip(if (isUploading) CircleShape else RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                                .background(Color.Black.copy(alpha = if (isUploading) 0.55f else 0.38f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.CameraAlt, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                    Spacer(Modifier.size(16.dp))
                    Column {
                        Text(user?.name ?: "", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .glassCard(cornerRadius = Radius.overviewCard)
                    .padding(Spacing.xs),
            ) {
                InfoRow(Icons.Filled.Mail, "Email", user?.email.orEmpty().ifBlank { "—" })
                InfoRow(Icons.Filled.Phone, "Mobile", user?.mobile.orEmpty().ifBlank { "—" })
                InfoRow(Icons.Filled.Cake, "Birthday", formatBirthday(user?.birthday))
            }

            ActionRow(Icons.Filled.Edit, "Edit profile", onEdit)
            ActionRow(Icons.Filled.Settings, "Settings", onSettings)
            Spacer(Modifier.height(Spacing.xs))
            DestructiveButton(
                "Sign out",
                onClick = { viewModel.signOut(onSignedOut) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.AutoMirrored.Filled.Logout,
            )
        }
    }

    if (needsCompletion) {
        ProfileCompletionDialog(
            onSave = { mobile, birthday -> viewModel.completeGoogleProfile(mobile, birthday) },
            onSkip = { viewModel.dismissProfileCompletion() },
        )
    }

    if (showAvatarPicker) {
        AvatarPickerDialog(
            hasAvatar = user?.avatarUrl != null,
            onDismiss = { showAvatarPicker = false },
            onCamera = {
                showAvatarPicker = false
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onGallery = {
                showAvatarPicker = false
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemove = {
                showAvatarPicker = false
                viewModel.removeAvatar()
            },
        )
    }
}

@Composable
private fun AvatarPickerDialog(
    hasAvatar: Boolean,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemove: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Profile photo", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onCamera, modifier = Modifier.fillMaxWidth()) {
                    Text("Take photo", style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose from gallery", style = MaterialTheme.typography.bodyLarge)
                }
                if (hasAvatar) {
                    TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                        Text("Remove photo", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatBirthday(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    return runCatching {
        LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
    }.getOrElse { raw }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = Radius.row)
            .clickable(onClick = onClick)
            .padding(Spacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(14.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One-time prompt shown after a Google sign-up (Google doesn't provide phone/birthday). Saving is
 * enabled once at least one field is filled; "Skip for now" dismisses it for the session.
 */
@Composable
private fun ProfileCompletionDialog(
    onSave: (String, String) -> Unit,
    onSkip: () -> Unit,
) {
    var mobile by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onSkip,
        shape = RoundedCornerShape(Radius.sheet),
        title = { Text("Complete your profile", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    "Add your phone and birthday so your family can reach you and celebrate you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FamilyTextField(
                    mobile,
                    { mobile = it },
                    "Mobile",
                    leadingIcon = Icons.Filled.Phone,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                )
                BirthdayPickerField(value = birthday, onChange = { birthday = it }, label = "Birthday")
            }
        },
        confirmButton = {
            PrimaryButton(
                "Save",
                onClick = { onSave(mobile, birthday) },
                enabled = mobile.isNotBlank() || birthday.isNotBlank(),
            )
        },
        dismissButton = { SecondaryButton("Skip for now", onClick = onSkip) },
    )
}

@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    var name by remember(user) { mutableStateOf(user?.name ?: "") }
    var email by remember(user) { mutableStateOf(user?.email ?: "") }
    var mobile by remember(user) { mutableStateOf(user?.mobile ?: "") }
    var birthday by remember(user) { mutableStateOf(user?.birthday ?: "") }

    val saveEnabled = name.isNotBlank() && email.isNotBlank() && mobile.isNotBlank() && birthday.isNotBlank()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { FeatureTopBar(stringResource(R.string.edit_profile), onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Personal information",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FamilyTextField(name, { name = it }, "Full name *")
            FamilyTextField(email, { email = it }, "Email *", keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
            FamilyTextField(mobile, { mobile = it }, "Mobile *", keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
            BirthdayPickerField(value = birthday, onChange = { birthday = it }, label = "Birthday *")
            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                "Save changes",
                onClick = {
                    viewModel.save(name, email, birthday, mobile)
                    onBack()
                },
                enabled = saveEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
