@file:Suppress("ktlint:standard:function-naming")

package com.example.mainactivity.ui.family

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.ui.components.CopyableCodeField
import com.example.mainactivity.ui.components.EmptyState
import com.example.mainactivity.ui.components.ErrorBanner
import com.example.mainactivity.ui.components.FamilyTextField
import com.example.mainactivity.ui.components.FeatureTopBar
import com.example.mainactivity.ui.components.InitialAvatar
import com.example.mainactivity.ui.components.InputDialog
import com.example.mainactivity.ui.components.PillTag
import com.example.mainactivity.ui.components.PrimaryButton
import com.example.mainactivity.ui.components.SecondaryButton
import com.example.mainactivity.ui.components.SwipeToRevealDelete

@Composable
fun FamilyScreen(
    onBack: (() -> Unit)? = null,
    viewModel: FamilyViewModel = hiltViewModel(),
) {
    val family by viewModel.family.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<UserModel?>(null) }
    var showPhotoMenu by remember { mutableStateOf(false) }

    val isAdmin = family != null && family!!.adminId == currentUser?.id

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.uploadFamilyPhoto(context, uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            FeatureTopBar(
                title = "Family",
                onBack = onBack,
                actions = {
                    if (family != null) {
                        Box {
                            IconButton(onClick = { showPhotoMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showPhotoMenu,
                                onDismissRequest = { showPhotoMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Change family photo") },
                                    onClick = {
                                        showPhotoMenu = false
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (family == null) {
            // ── No-family empty state ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // Subtle gradient decoration behind the empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    MaterialTheme.colorScheme.background,
                                ),
                            ),
                        ),
                )

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState(
                        icon = Icons.Filled.FamilyRestroom,
                        title = "Bring your family together",
                        subtitle = "Create a family space or join one with an invite code to share calendars, shopping lists, wishlists, and more.",
                    )
                    Spacer(Modifier.height(8.dp))
                    ErrorBanner(error)
                    if (error != null) Spacer(Modifier.height(8.dp))
                    PrimaryButton(
                        text = "Create a Family",
                        onClick = { showCreate = true },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = Icons.Filled.FamilyRestroom,
                    )
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(
                        text = "Join with Invite Code",
                        onClick = { showJoin = true },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = Icons.Filled.GroupAdd,
                    )
                }
            }
        } else {
            // ── Has-family state ──────────────────────────────────────────
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Family header card
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                family!!.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            // Avatar stack
                            AvatarStack(members = members)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${members.size} member${if (members.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (family!!.joinCode.isNotBlank()) {
                                Spacer(Modifier.height(16.dp))
                                CopyableCodeField(
                                    code = family!!.joinCode,
                                    label = "Invite Code",
                                    modifier = Modifier.semantics {
                                        contentDescription = "Family invite code: ${family!!.joinCode}"
                                    },
                                )
                            }
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

                // Member cards
                items(members, key = { it.id }) { member ->
                    val memberIsAdmin = member.id == family!!.adminId
                    val canRemove = isAdmin && !memberIsAdmin && member.id != currentUser?.id
                    val memberDescription = "${member.name}, ${if (memberIsAdmin) "Admin" else "Member"}"

                    if (canRemove) {
                        SwipeToRevealDelete(
                            onDelete = { memberToRemove = member },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            MemberCard(member = member, isAdmin = memberIsAdmin, memberDescription = memberDescription)
                        }
                    } else {
                        MemberCard(member = member, isAdmin = memberIsAdmin, memberDescription = memberDescription)
                    }
                }

                // Leave button
                item {
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(
                        text = "Leave family",
                        onClick = { showLeaveConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = Icons.AutoMirrored.Filled.Logout,
                    )
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────

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

    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Remove member?") },
            text = { Text("${member.name} will be removed from the family. They can rejoin later with the invite code.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeMember(member.id)
                    memberToRemove = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) { Text("Cancel") }
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

    if (showJoin) {
        InputDialog(
            title = "Join a family",
            label = "Invite code",
            confirmText = "Join",
            onDismiss = {
                showJoin = false
                viewModel.clearError()
            },
            onConfirm = { code, _ ->
                viewModel.joinFamily(code)
                showJoin = false
            },
        )
    }
}

/** Overlapping avatar stack showing up to 4 avatars, then a "+N" overflow badge. */
@Composable
private fun AvatarStack(members: List<UserModel>) {
    if (members.isEmpty()) return

    val avatarSize = 36
    val overlapOffset = 24 // dp each avatar shifts right from previous
    val displayMembers = members.take(4)
    val overflow = members.size - displayMembers.size
    val stackWidth = (overlapOffset * (displayMembers.size - 1) + avatarSize +
        (if (overflow > 0) overlapOffset + avatarSize else 0)).dp

    val names = members.joinToString(", ") { it.name }
    val avatarColors = listOf(
        Color(0xFF6366F1), // indigo
        Color(0xFF8B5CF6), // violet
        Color(0xFF14B8A6), // teal
        Color(0xFFF59E0B), // amber
    )

    Box(
        modifier = Modifier
            .width(stackWidth)
            .height(avatarSize.dp)
            .semantics { contentDescription = "Family members: $names" },
    ) {
        displayMembers.forEachIndexed { index, member ->
            val color = if (member.avatarColor != 0) Color(member.avatarColor) else avatarColors[index % avatarColors.size]
            Box(
                modifier = Modifier
                    .offset(x = (index * overlapOffset).dp)
                    .size(avatarSize.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.dp),
            ) {
                InitialAvatar(
                    name = member.name,
                    color = color,
                    size = avatarSize,
                    avatarUri = member.avatarUrl,
                )
            }
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .offset(x = (displayMembers.size * overlapOffset).dp)
                    .size(avatarSize.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$overflow",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Single member card with avatar, name+email column, and role badge. */
@Composable
private fun MemberCard(
    member: UserModel,
    isAdmin: Boolean,
    memberDescription: String,
) {
    val avatarColor = if (member.avatarColor != 0) Color(member.avatarColor) else Color(0xFF6366F1)

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = memberDescription },
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InitialAvatar(
                name = member.name,
                color = avatarColor,
                size = 48,
                avatarUri = member.avatarUrl,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isAdmin) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Admin",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    text = member.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PillTag(
                text = if (isAdmin) "Admin" else "Member",
                container = if (isAdmin) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                content = if (isAdmin) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
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

