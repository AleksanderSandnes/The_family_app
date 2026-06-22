package com.example.mainactivity.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mainactivity.ui.components.FamilyTextField
import com.example.mainactivity.ui.components.FeatureTopBar
import com.example.mainactivity.ui.components.InitialAvatar
import com.example.mainactivity.ui.components.PrimaryButton
import com.example.mainactivity.ui.theme.heroGradient

@Composable
fun ProfileScreen(
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val dark = androidx.compose.foundation.isSystemInDarkTheme()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FeatureTopBar("Profile") }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(heroGradient(dark)).padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Text(
                            user?.name?.trim()?.firstOrNull()?.uppercase() ?: "?",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.size(16.dp))
                    Column {
                        Text(user?.name ?: "", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(user?.email ?: "", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(6.dp)) {
                    InfoRow(Icons.Filled.Mail, "Email", user?.email.orEmpty().ifBlank { "—" })
                    InfoRow(Icons.Filled.Phone, "Mobile", user?.mobile.orEmpty().ifBlank { "—" })
                    InfoRow(Icons.Filled.Cake, "Birthday", user?.birthday.orEmpty().ifBlank { "—" })
                }
            }

            ActionRow(Icons.Filled.Edit, "Edit profile", onEdit)
            ActionRow(Icons.Filled.Settings, "Settings", onSettings)
            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                "Sign out",
                onClick = { viewModel.signOut(onSignedOut) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.AutoMirrored.Filled.Logout
            )
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(14.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(14.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    var name by remember(user) { mutableStateOf(user?.name ?: "") }
    var email by remember(user) { mutableStateOf(user?.email ?: "") }
    var mobile by remember(user) { mutableStateOf(user?.mobile ?: "") }
    var birthday by remember(user) { mutableStateOf(user?.birthday ?: "") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FeatureTopBar("Edit profile", onBack) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FamilyTextField(name, { name = it }, "Full name")
            FamilyTextField(email, { email = it }, "Email", keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
            FamilyTextField(mobile, { mobile = it }, "Mobile", keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
            FamilyTextField(birthday, { birthday = it }, "Birthday")
            Spacer(Modifier.height(6.dp))
            PrimaryButton("Save changes", onClick = { viewModel.save(name, email, birthday, mobile); onBack() }, modifier = Modifier.fillMaxWidth())
        }
    }
}
