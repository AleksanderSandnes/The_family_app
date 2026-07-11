@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.family

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.ui.components.AppTopBar
import com.sandnes.familyapp.ui.components.CopyableCodeField
import com.sandnes.familyapp.ui.components.DestructiveButton
import com.sandnes.familyapp.ui.components.EmptyState
import com.sandnes.familyapp.ui.components.ErrorBanner
import com.sandnes.familyapp.ui.components.FamilyTextField
import com.sandnes.familyapp.ui.components.InitialAvatar
import com.sandnes.familyapp.ui.components.InputDialog
import com.sandnes.familyapp.ui.components.PillTag
import com.sandnes.familyapp.ui.components.PrimaryButton
import com.sandnes.familyapp.ui.components.SecondaryButton
import com.sandnes.familyapp.ui.components.SectionHeader
import com.sandnes.familyapp.ui.components.SwipeToRevealDelete
import com.sandnes.familyapp.ui.navigation.Routes
import com.sandnes.familyapp.ui.theme.Radius
import com.sandnes.familyapp.ui.theme.Spacing
import com.sandnes.familyapp.ui.theme.colorFromArgb
import com.sandnes.familyapp.ui.theme.glassCard

/** Localised display name for a stored (English) relation value — mirrors iOS `L(dynamic:)`. */
@Composable
fun relationDisplayName(relation: String): String =
    when (relation) {
        "Mom" -> stringResource(R.string.relation_mom)
        "Dad" -> stringResource(R.string.relation_dad)
        "Son" -> stringResource(R.string.relation_son)
        "Daughter" -> stringResource(R.string.relation_daughter)
        "Sister" -> stringResource(R.string.relation_sister)
        "Brother" -> stringResource(R.string.relation_brother)
        "Wife" -> stringResource(R.string.relation_wife)
        "Husband" -> stringResource(R.string.relation_husband)
        "Fiancé" -> stringResource(R.string.relation_fiance)
        "Partner" -> stringResource(R.string.relation_partner)
        "Grandmother" -> stringResource(R.string.relation_grandmother)
        "Grandfather" -> stringResource(R.string.relation_grandfather)
        "Grandchild" -> stringResource(R.string.relation_grandchild)
        "Aunt" -> stringResource(R.string.relation_aunt)
        "Uncle" -> stringResource(R.string.relation_uncle)
        "Cousin" -> stringResource(R.string.relation_cousin)
        "Friend" -> stringResource(R.string.relation_friend)
        "Other" -> stringResource(R.string.relation_other)
        else -> relation
    }

/** Family relation options (relative to the viewer). Mirrors iOS `familyRelationOptions`. */
val familyRelationOptions =
    listOf(
        "Mom",
        "Dad",
        "Son",
        "Daughter",
        "Sister",
        "Brother",
        "Wife",
        "Husband",
        "Fiancé",
        "Partner",
        "Grandmother",
        "Grandfather",
        "Grandchild",
        "Aunt",
        "Uncle",
        "Cousin",
        "Friend",
        "Other",
    )

private val defaultAvatarColor = Color(0xFF6366F1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(
    onBack: (() -> Unit)? = null,
    viewModel: FamilyViewModel = hiltViewModel(),
) {
    val family by viewModel.family.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val relations by viewModel.relations.collectAsStateWithLifecycle()
    val promptRelationSetup by viewModel.promptRelationSetup.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<UserModel?>(null) }
    var selectedMember by remember { mutableStateOf<UserModel?>(null) }
    var showPhotoMenu by remember { mutableStateOf(false) }
    var joinInitial by remember { mutableStateOf("") }
    var showQr by remember { mutableStateOf(false) }

    val pendingJoin by viewModel.pendingJoinCode.collectAsStateWithLifecycle()
    LaunchedEffect(pendingJoin) {
        val code = pendingJoin
        if (code != null) {
            joinInitial = code
            showJoin = true
            viewModel.consumePendingJoinCode()
        }
    }

    val isAdmin = family != null && family!!.adminId == currentUser?.id

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) viewModel.uploadFamilyPhoto(context, uri)
        }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.family),
                onBack = onBack,
                actions = {
                    if (family != null && isAdmin) {
                        Box {
                            IconButton(onClick = { showPhotoMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(
                                expanded = showPhotoMenu,
                                onDismissRequest = { showPhotoMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.change_family_photo)) },
                                    onClick = {
                                        showPhotoMenu = false
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
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
            NoFamilyContent(
                error = error,
                modifier = Modifier.fillMaxSize().padding(padding),
                onCreate = { showCreate = true },
                onJoin = {
                    joinInitial = ""
                    showJoin = true
                },
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Spacing.screenEdge),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                item {
                    FamilyHeaderCard(
                        familyName = family!!.name,
                        joinCode = family!!.joinCode,
                        photoUrl = family!!.photoUrl,
                        members = members,
                        isAdmin = isAdmin,
                        onChangePhoto = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onShare = {
                            val message =
                                context.getString(
                                    R.string.join_family_invite_message,
                                    family!!.name,
                                    Routes.inviteLink(family!!.joinCode),
                                    family!!.joinCode,
                                )
                            val send =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, message)
                                }
                            context.startActivity(Intent.createChooser(send, context.getString(R.string.share_invite)))
                        },
                        onShowQr = { showQr = true },
                    )
                    ErrorBanner(error)
                    SectionHeader(stringResource(R.string.members), modifier = Modifier.padding(top = Spacing.sm))
                }

                items(members, key = { it.id }) { member ->
                    val memberIsAdmin = member.id == family!!.adminId
                    val canRemove = isAdmin && !memberIsAdmin && member.id != currentUser?.id
                    val card: @Composable () -> Unit = {
                        MemberCard(
                            member = member,
                            isAdmin = memberIsAdmin,
                            relation = relations[member.id],
                            onClick = { selectedMember = member },
                        )
                    }
                    if (canRemove) {
                        SwipeToRevealDelete(
                            onDelete = { memberToRemove = member },
                            shape = RoundedCornerShape(Radius.row),
                        ) { card() }
                    } else {
                        card()
                    }
                }

                item {
                    DestructiveButton(
                        text = stringResource(R.string.leave_family),
                        onClick = { showLeaveConfirm = true },
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                        leadingIcon = Icons.AutoMirrored.Filled.Logout,
                    )
                }
            }
        }
    }

    // ── Sheets ─────────────────────────────────────────────────────────────

    selectedMember?.let { member ->
        MemberProfileSheet(
            member = member,
            isSelf = member.id == currentUser?.id,
            relation = relations[member.id] ?: "",
            onSetRelation = { viewModel.setRelation(member.id, it) },
            onDismiss = { selectedMember = null },
        )
    }

    if (promptRelationSetup) {
        RelationsSetupSheet(
            members = members.filter { it.id != currentUser?.id },
            relations = relations,
            onSet = { toUserId, relation -> viewModel.setRelation(toUserId, relation) },
            onDismiss = { viewModel.dismissRelationSetup() },
        )
    }

    // ── Dialogs ────────────────────────────────────────────────────────────

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            shape = RoundedCornerShape(Radius.large),
            title = { Text(stringResource(R.string.leave_family_q)) },
            text = { Text(stringResource(R.string.you_will_lose_access_to_shared_data_you_can_rejoin_later_with_the_invite_code)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    viewModel.leaveFamily()
                }) {
                    Text(stringResource(R.string.leave), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            shape = RoundedCornerShape(Radius.large),
            title = { Text(stringResource(R.string.remove_member_q)) },
            text = { Text("${member.name} will be removed from the family. They can rejoin later with the invite code.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeMember(member.id)
                    memberToRemove = null
                }) {
                    Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) { Text(stringResource(R.string.cancel)) }
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
            title = stringResource(R.string.join_a_family),
            label = stringResource(R.string.invite_code),
            initial = joinInitial,
            confirmText = stringResource(R.string.join),
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

    if (showQr && family != null) {
        val link = Routes.inviteLink(family!!.joinCode)
        val qr = remember(link) { generateQrBitmap(link) }
        AlertDialog(
            onDismissRequest = { showQr = false },
            shape = RoundedCornerShape(Radius.large),
            confirmButton = { TextButton(onClick = { showQr = false }) { Text(stringResource(R.string.done)) } },
            title = { Text(stringResource(R.string.scan_to_join)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (qr != null) {
                        Image(
                            bitmap = qr,
                            contentDescription = "Family invite QR code",
                            modifier = Modifier.size(220.dp),
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        family!!.joinCode,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }
}

// ── No-family state ────────────────────────────────────────────────────────

@Composable
private fun NoFamilyContent(
    error: String?,
    modifier: Modifier = Modifier,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    Box(modifier) {
        Box(
            modifier =
                Modifier
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
                title = stringResource(R.string.bring_your_family_together),
                subtitle = stringResource(R.string.create_a_family_space_or_join_one_with_an_invite_code_to_share_calendars_shopping_lists_wishlists_and_more),
            )
            Spacer(Modifier.height(Spacing.sm))
            ErrorBanner(error)
            if (error != null) Spacer(Modifier.height(Spacing.sm))
            PrimaryButton(
                text = stringResource(R.string.create_a_family),
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Filled.FamilyRestroom,
            )
            Spacer(Modifier.height(Spacing.md))
            SecondaryButton(
                text = stringResource(R.string.join_with_invite_code),
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Filled.GroupAdd,
            )
        }
    }
}

// ── Header card ────────────────────────────────────────────────────────────

private const val PHOTO_HERO_SIZE = 112
private const val CAMERA_BADGE_SIZE = 34

@Composable
private fun FamilyHeaderCard(
    familyName: String,
    joinCode: String,
    photoUrl: String?,
    members: List<UserModel>,
    isAdmin: Boolean,
    onChangePhoto: () -> Unit,
    onShare: () -> Unit,
    onShowQr: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassCard(Radius.bigCard)
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FamilyPhotoHero(photoUrl = photoUrl, isAdmin = isAdmin, onChangePhoto = onChangePhoto)
        Text(
            familyName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${members.size} member${if (members.size == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AvatarStack(members = members)
        if (joinCode.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            CopyableCodeField(
                code = joinCode,
                label = stringResource(R.string.invite_code),
                modifier =
                    Modifier.semantics {
                        contentDescription = "Family invite code: $joinCode"
                    },
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SecondaryButton(
                    text = stringResource(R.string.share_invite),
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Filled.Share,
                )
                SecondaryButton(
                    text = stringResource(R.string.qr_code),
                    onClick = onShowQr,
                    modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Filled.QrCode2,
                )
            }
        }
    }
}

@Composable
private fun FamilyPhotoHero(
    photoUrl: String?,
    isAdmin: Boolean,
    onChangePhoto: () -> Unit,
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        val circle =
            Modifier
                .size(PHOTO_HERO_SIZE.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF7C3AED), Color(0xFF5457E8)),
                    ),
                ).let { if (isAdmin) it.clickable(onClick = onChangePhoto) else it }
        Box(circle, contentAlignment = Alignment.Center) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Family photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(PHOTO_HERO_SIZE.dp).clip(CircleShape),
                )
            } else {
                Icon(
                    Icons.Filled.FamilyRestroom,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(46.dp),
                )
            }
        }
        if (isAdmin) {
            Box(
                Modifier
                    .size(CAMERA_BADGE_SIZE.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onChangePhoto),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.CameraAlt,
                    contentDescription = "Change family photo",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Overlapping avatar stack showing up to 4 avatars, then a "+N" overflow badge. */
@Composable
private fun AvatarStack(members: List<UserModel>) {
    if (members.isEmpty()) return

    val avatarSize = 40
    val overlapOffset = 26 // dp each avatar shifts right from previous
    val displayMembers = members.take(4)
    val overflow = members.size - displayMembers.size
    val stackWidth =
        (
            overlapOffset * (displayMembers.size - 1) + avatarSize +
                (if (overflow > 0) overlapOffset + avatarSize else 0)
        ).dp

    val names = members.joinToString(", ") { it.name }

    Box(
        modifier =
            Modifier
                .width(stackWidth)
                .height(avatarSize.dp)
                .semantics { contentDescription = "Family members: $names" },
    ) {
        displayMembers.forEachIndexed { index, member ->
            Box(
                modifier =
                    Modifier
                        .offset(x = (index * overlapOffset).dp)
                        .size(avatarSize.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp),
            ) {
                InitialAvatar(
                    name = member.name,
                    color = member.avatarColor.takeIf { it != 0 }?.let { colorFromArgb(it) } ?: defaultAvatarColor,
                    size = avatarSize - 4,
                    avatarUri = member.avatarUrl,
                )
            }
        }
        if (overflow > 0) {
            Box(
                modifier =
                    Modifier
                        .offset(x = (displayMembers.size * overlapOffset).dp)
                        .size(avatarSize.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$overflow",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Single member row: avatar, name + relation (as seen by me), admin badge, chevron. */
@Composable
private fun MemberCard(
    member: UserModel,
    isAdmin: Boolean,
    relation: String?,
    onClick: () -> Unit,
) {
    val avatarColor = member.avatarColor.takeIf { it != 0 }?.let { colorFromArgb(it) } ?: defaultAvatarColor
    // iOS shows the viewer's relation as the subtitle (never the email).
    val subtitle = relation?.takeIf { it.isNotBlank() }?.let { relationDisplayName(it) }.orEmpty()
    val memberDescription = "${member.name}, ${if (isAdmin) "Admin" else "Member"}"

    Row(
        Modifier
            .fillMaxWidth()
            .glassCard(Radius.row)
            .clickable(onClick = onClick)
            .padding(Spacing.lg)
            .semantics { contentDescription = memberDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialAvatar(name = member.name, color = avatarColor, size = 44, avatarUri = member.avatarUrl)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isAdmin) {
            PillTag(
                text = "Admin",
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Member profile sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberProfileSheet(
    member: UserModel,
    isSelf: Boolean,
    relation: String,
    onSetRelation: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val avatarColor = member.avatarColor.takeIf { it != 0 }?.let { colorFromArgb(it) } ?: defaultAvatarColor

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenEdge)
                .padding(bottom = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            InitialAvatar(name = member.name, color = avatarColor, size = 96, avatarUri = member.avatarUrl)
            Text(
                member.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(Modifier.fillMaxWidth().glassCard(Radius.row)) {
                if (member.email.isNotBlank()) {
                    InfoRow(icon = Icons.Filled.Email, label = stringResource(R.string.email), value = member.email)
                    HorizontalDivider(Modifier.padding(start = 52.dp))
                }
                InfoRow(
                    icon = Icons.Filled.Phone,
                    label = stringResource(R.string.phone),
                    value = member.mobile.ifBlank { stringResource(R.string.not_set) },
                )
                if (!isSelf) {
                    HorizontalDivider(Modifier.padding(start = 52.dp))
                    RelationRow(relation = relation, onSetRelation = onSetRelation)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun RelationRow(
    relation: String,
    onSetRelation: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(stringResource(R.string.your_relation), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RelationPicker(relation = relation, onSetRelation = onSetRelation)
        }
    }
}

/** Dropdown that lets the viewer pick their relation label (or clear it). */
@Composable
private fun RelationPicker(
    relation: String,
    onSetRelation: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                relationDisplayName(relation).ifBlank { stringResource(R.string.set_relation) },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (relation.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                Icons.Filled.UnfoldMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            familyRelationOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(relationDisplayName(option)) },
                    onClick = {
                        expanded = false
                        onSetRelation(option)
                    },
                )
            }
            if (relation.isNotBlank()) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.none), color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        expanded = false
                        onSetRelation("")
                    },
                )
            }
        }
    }
}

// ── Relations-setup sheet (post-join) ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelationsSetupSheet(
    members: List<UserModel>,
    relations: Map<String, String>,
    onSet: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenEdge)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.set_your_relations),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
            }
            Text(
                stringResource(R.string.how_are_you_related_to_each_member_you_can_change_this_anytime),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(members, key = { it.id }) { member ->
                    val avatarColor = member.avatarColor.takeIf { it != 0 }?.let { colorFromArgb(it) } ?: defaultAvatarColor
                    Row(
                        Modifier.fillMaxWidth().glassCard(Radius.row).padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InitialAvatar(name = member.name, color = avatarColor, size = 40, avatarUri = member.avatarUrl)
                        Spacer(Modifier.width(Spacing.md))
                        Text(
                            member.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        RelationPicker(
                            relation = relations[member.id] ?: "",
                            onSetRelation = { onSet(member.id, it) },
                        )
                    }
                }
            }
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
        shape = RoundedCornerShape(Radius.large),
        title = { Text("Create a family") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
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
