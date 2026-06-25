@file:Suppress("ktlint:standard:function-naming")

package com.example.mainactivity.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mainactivity.ui.components.CopyableCodeField
import com.example.mainactivity.ui.components.ErrorBanner
import com.example.mainactivity.ui.components.FamilyTextField
import com.example.mainactivity.ui.components.FeatureTopBar
import com.example.mainactivity.ui.components.InitialAvatar
import com.example.mainactivity.ui.components.InputDialog
import com.example.mainactivity.ui.components.PillTag
import com.example.mainactivity.ui.components.PrimaryButton
import com.example.mainactivity.ui.components.SecondaryButton

@Composable
fun FamilyScreen(
    onBack: (() -> Unit)? = null,
    viewModel: FamilyViewModel = hiltViewModel(),
) {
    val family by viewModel.family.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showEditFamily by remember { mutableStateOf(false) }

    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FeatureTopBar("Family", onBack) },
    ) { padding ->
        if (family == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(96.dp)) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Groups, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Bring your family together", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Create a new family group or join an existing one with a code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                ErrorBanner(error)
                PrimaryButton("Create a family", onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Filled.Groups)
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Join with a code", onClick = { showJoin = true }, modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Filled.GroupAdd)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Surface(
                        onClick = { showEditFamily = true },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(family!!.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${members.size} member${if (members.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Members",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                items(members, key = { it.id }) { member ->
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            InitialAvatar(member.name, Color(if (member.avatarColor != 0) member.avatarColor else 0xFF6366F1.toInt()), avatarUri = member.avatarUrl)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(member.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(member.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (member.id == family!!.adminId) {
                                PillTag("Admin", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Leave family", onClick = { showLeaveConfirm = true }, modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.AutoMirrored.Filled.Logout)
                }
            }
        }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Leave family?") },
            text = { Text("You will lose access to shared data. You can rejoin later with the invite code.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    viewModel.leaveFamily()
                }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showCreate) {
        CreateFamilyDialog(
            onDismiss = { showCreate = false },
            onConfirm = { name, code ->
                viewModel.createFamily(name, code)
                showCreate = false
            },
            generateCode = { viewModel.generateJoinCode() },
        )
    }

    if (showEditFamily && family != null) {
        val isAdmin = family!!.adminId == currentUser?.id
        EditFamilyDialog(
            initialName = family!!.name,
            joinCode = family!!.joinCode,
            isAdmin = isAdmin,
            onDismiss = { showEditFamily = false },
            onSave = { newName ->
                viewModel.renameFamily(newName)
                showEditFamily = false
            },
        )
    }

    if (showJoin) {
        InputDialog(
            title = "Join a family",
            label = "Family name",
            secondLabel = "Invite code",
            confirmText = "Join",
            onDismiss = {
                showJoin = false
                viewModel.clearError()
            },
            onConfirm = { name, code ->
                viewModel.joinFamily(name, code)
                showJoin = false
            },
        )
    }
}

@Composable
private fun CreateFamilyDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, code: String) -> Unit,
    generateCode: () -> String,
) {
    val code = remember { generateCode() }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Create a family") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FamilyTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Family name",
                    modifier = Modifier.fillMaxWidth(),
                )
                CopyableCodeField(code = code)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, code) },
                enabled = name.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditFamilyDialog(
    initialName: String,
    joinCode: String,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Family settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FamilyTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Family name",
                    enabled = isAdmin,
                    modifier = Modifier.fillMaxWidth(),
                )
                CopyableCodeField(code = joinCode)
            }
        },
        confirmButton = {
            if (isAdmin) {
                TextButton(
                    onClick = { onSave(name) },
                    enabled = name.isNotBlank(),
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
