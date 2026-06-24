@file:Suppress("ktlint:standard:function-naming")

package com.example.mainactivity.ui.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mainactivity.data.MessageModel
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.ui.components.EmptyState
import com.example.mainactivity.ui.components.FeatureTopBar
import com.example.mainactivity.ui.components.InitialAvatar
import com.example.mainactivity.ui.components.InputDialog
import com.example.mainactivity.ui.components.LoadingState
import com.example.mainactivity.ui.theme.BrandGradient
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpen: (String) -> Unit,
    viewModel: ChatViewModel = viewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle(emptyList())
    val conversationParticipants by viewModel.conversationParticipants.collectAsStateWithLifecycle()
    val familyMembers by viewModel.familyMembers.collectAsStateWithLifecycle()
    val myId by viewModel.currentUserId.collectAsStateWithLifecycle(null)
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle(false)
    var showMemberPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navigateToConversation.collect { newId -> onOpen(newId) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FeatureTopBar("Family chat") },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showMemberPicker = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, "New conversation") }
        },
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingState()
            }
        } else if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.AutoMirrored.Filled.Chat, "No conversations", "Start a chat to keep your family connected.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(conversations, key = { it.id }) { c ->
                    val participants = conversationParticipants[c.id] ?: emptyList()
                    val isOneOnOne = participants.size == 2 || c.userTo != null
                    val other =
                        participants.firstOrNull { it.id != myId }
                            ?: if (c.userTo != null) {
                                val otherId = if (c.userTo != myId) c.userTo else c.userFrom
                                familyMembers.firstOrNull { it.id == otherId }
                            } else {
                                null
                            }

                    val displayName =
                        remember(c, participants, myId, other) {
                            when {
                                isOneOnOne -> other?.name ?: c.name.takeIf { it.isNotBlank() } ?: "Chat"
                                c.name.isNotBlank() -> c.name
                                else ->
                                    participants
                                        .filter { it.id != myId }
                                        .take(3)
                                        .joinToString(", ") { it.name.split(" ").first() }
                                        .ifBlank { "Group chat" }
                            }
                        }
                    val avatarUri =
                        if (c.imageUri != null) {
                            c.imageUri
                        } else if (isOneOnOne) {
                            other?.avatarUrl
                        } else {
                            null
                        }
                    val avatarColor =
                        Color(
                            (if (isOneOnOne) other?.avatarColor else null)
                                ?.takeIf { it != 0 } ?: 0xFF6366F1.toInt(),
                        )

                    Surface(
                        onClick = { onOpen(c.id) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            InitialAvatar(displayName, avatarColor, size = 44, avatarUri = avatarUri)
                            Spacer(Modifier.size(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!isOneOnOne && participants.isNotEmpty()) {
                                    Text(
                                        "${participants.size} members",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMemberPicker) {
        NewConversationSheet(
            familyMembers = familyMembers,
            myId = myId,
            onDismiss = { showMemberPicker = false },
            onCreate = { name, memberIds ->
                viewModel.createConversation(name, memberIds)
                showMemberPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationScreen(
    conversationId: String,
    onBack: () -> Unit,
    onNavigateTo: (String) -> Unit = {},
    viewModel: ChatViewModel = viewModel(),
) {
    LaunchedEffect(conversationId) { viewModel.loadConversation(conversationId) }

    // Navigate to a new conversation (e.g. after upgrading 1:1 → group, or existing 1:1 found)
    LaunchedEffect(Unit) {
        viewModel.navigateToConversation.collect { newId ->
            onBack()
            onNavigateTo(newId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.conversationDeleted.collect { onBack() }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val myId by viewModel.currentUserId.collectAsStateWithLifecycle(null)
    val replyTo by viewModel.replyTo.collectAsStateWithLifecycle()
    val userProfiles by viewModel.userProfiles.collectAsStateWithLifecycle()
    val currentParticipants by viewModel.currentParticipants.collectAsStateWithLifecycle()
    val familyMembers by viewModel.familyMembers.collectAsStateWithLifecycle()

    // Compute display title: 1:1 always shows person's name; groups use stored name or list
    val title =
        remember(conversation, currentParticipants, myId) {
            val conv = conversation ?: return@remember "Chat"
            val others = currentParticipants.filter { it.id != myId }
            when {
                others.size == 1 -> others.first().name
                conv.name.isNotBlank() -> conv.name
                others.size > 1 -> others.take(3).joinToString(", ") { it.name.split(" ").first() }
                else -> "Chat"
            }
        }

    // Family members who aren't yet in this conversation
    val availableToAdd =
        remember(familyMembers, currentParticipants, myId) {
            val currentIds = currentParticipants.map { it.id }.toSet()
            familyMembers.filter { it.id !in currentIds }
        }
    val isGroup = currentParticipants.size > 2

    var draft by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showImagePicker by remember { mutableStateOf(false) }
    var showAddMember by remember { mutableStateOf(false) }
    var showRemoveMember by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    var prevMsgCount by remember { mutableStateOf(0) }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) {
            prevMsgCount = 0
            return@LaunchedEffect
        }
        val lastVisible =
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: -1
        val wasAtBottom = prevMsgCount == 0 || lastVisible >= (prevMsgCount - 2).coerceAtLeast(0)
        if (wasAtBottom) listState.scrollToItem(messages.lastIndex)
        prevMsgCount = messages.size
    }

    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible && messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            val lastVisible =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            messages.isNotEmpty() && lastVisible < messages.lastIndex - 1
        }
    }

    val galleryLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri -> uri?.let { viewModel.saveImageFromUri(context, it, conversationId) } }

    val cameraLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
        ) { success -> viewModel.onCameraResult(context, success) }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) viewModel.prepareCameraCapture(context, conversationId)?.let { cameraLauncher.launch(it) }
        }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FeatureTopBar(
                title = title,
                onBack = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            // Rename is only meaningful for group chats — a 1:1 always
                            // shows the other person's name, so it can't be renamed.
                            if (isGroup) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        showMenu = false
                                        showRename = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Change image") },
                                onClick = {
                                    showMenu = false
                                    showImagePicker = true
                                },
                            )
                            if (conversation?.imageUri != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove image", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.removeImage(conversationId)
                                    },
                                )
                            }
                            if (availableToAdd.isNotEmpty()) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (isGroup) "Add member" else "Add member (creates group)") },
                                    leadingIcon = { Icon(Icons.Filled.GroupAdd, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showMenu = false
                                        showAddMember = true
                                    },
                                )
                            }
                            // "Remove member" is group-only. A 1:1 has no "Leave" —
                            // the only way out of a 1:1 is to delete the conversation.
                            if (isGroup) {
                                DropdownMenuItem(
                                    text = { Text("Remove member", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Filled.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        showRemoveMember = true
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete conversation", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column {
                    AnimatedVisibility(visible = replyTo != null) {
                        replyTo?.let { quoted ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .height(36.dp)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Replying",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        quoted.text.take(80),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = viewModel::clearReplyTo) {
                                    Icon(Icons.Filled.Close, "Cancel reply", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Message") },
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4,
                        )
                        val canSend = draft.isNotBlank()
                        FloatingActionButton(
                            onClick = {
                                if (canSend) {
                                    viewModel.send(conversationId, draft.trim())
                                    draft = ""
                                    keyboardController?.hide()
                                }
                            },
                            containerColor = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(52.dp),
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
                    }
                }
            }
        },
    ) { padding ->
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.AutoMirrored.Filled.Chat, "Say hello 👋", "Send the first message in this conversation.")
            }
        } else {
            Box(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                        val mine = msg.userFrom == myId
                        val prevFrom = if (index > 0) messages[index - 1].userFrom else null
                        val nextFrom = if (index < messages.lastIndex) messages[index + 1].userFrom else null
                        val isFirstInGroup = prevFrom != msg.userFrom
                        val isLastInGroup = nextFrom != msg.userFrom
                        val timeLabel =
                            remember(msg.sentAt) {
                                runCatching {
                                    java.time.OffsetDateTime
                                        .parse(msg.sentAt)
                                        .format(
                                            java.time.format.DateTimeFormatter
                                                .ofPattern("HH:mm"),
                                        )
                                }.getOrDefault("")
                            }
                        MessageRow(
                            msg = msg,
                            mine = mine,
                            myId = myId,
                            timeLabel = timeLabel,
                            isFirstInGroup = isFirstInGroup,
                            isLastInGroup = isLastInGroup,
                            senderProfile = if (!mine) userProfiles[msg.userFrom] else null,
                            messages = messages,
                            onReply = { viewModel.setReplyTo(msg) },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = showScrollToBottom,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(messages.lastIndex) } },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Scroll to bottom")
                    }
                }
            }
        }
    }

    if (showRename) {
        InputDialog(
            title = "Rename conversation",
            label = "Name",
            initial = conversation?.name ?: "",
            confirmText = "Rename",
            onDismiss = { showRename = false },
        ) { name, _ ->
            viewModel.renameConversation(conversationId, name)
            showRename = false
        }
    }

    if (showImagePicker) {
        GroupImagePickerDialog(
            hasImage = conversation?.imageUri != null,
            onDismiss = { showImagePicker = false },
            onCamera = {
                showImagePicker = false
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onGallery = {
                showImagePicker = false
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemove = {
                showImagePicker = false
                viewModel.removeImage(conversationId)
            },
        )
    }

    if (showAddMember) {
        AddMemberSheet(
            candidates = availableToAdd,
            onDismiss = { showAddMember = false },
            onAdd = { userId ->
                viewModel.addMember(conversationId, userId)
                showAddMember = false
            },
        )
    }

    if (showRemoveMember) {
        RemoveMemberSheet(
            participants = currentParticipants,
            myId = myId,
            onDismiss = { showRemoveMember = false },
            onRemove = { userId ->
                viewModel.removeMember(conversationId, userId)
                showRemoveMember = false
                if (userId == myId) onBack()
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete conversation") },
            text = { Text("This will permanently delete the conversation and all messages for everyone. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteConversation(conversationId)
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

// ─── New conversation member picker ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewConversationSheet(
    familyMembers: List<UserModel>,
    myId: String?,
    onDismiss: () -> Unit,
    onCreate: (name: String, memberIds: List<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val others = remember(familyMembers, myId) { familyMembers.filter { it.id != myId } }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var groupName by remember { mutableStateOf("") }

    val isGroup = selectedIds.size > 1
    val canCreate = selectedIds.isNotEmpty() && (!isGroup || groupName.isNotBlank())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "New conversation",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Select family members to chat with",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            if (others.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No other family members yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(others, key = { it.id }) { member ->
                        MemberSelectRow(
                            member = member,
                            selected = member.id in selectedIds,
                            onToggle = {
                                selectedIds =
                                    if (member.id in selectedIds) {
                                        selectedIds - member.id
                                    } else {
                                        selectedIds + member.id
                                    }
                            },
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isGroup) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group name") },
                        placeholder = { Text("e.g. Weekend plans") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        val name = if (isGroup) groupName else ""
                        onCreate(name, selectedIds.toList())
                    }
                },
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    if (isGroup) "Create group" else "Start chat",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Add member to existing conversation ───────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberSheet(
    candidates: List<UserModel>,
    onDismiss: () -> Unit,
    onAdd: (userId: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Add member",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Tap a member to add them",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(candidates, key = { it.id }) { member ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onAdd(member.id) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        InitialAvatar(
                            name = member.name,
                            color = Color(member.avatarColor.takeIf { it != 0 } ?: 0xFF6366F1.toInt()),
                            size = 42,
                            avatarUri = member.avatarUrl,
                        )
                        Text(member.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Remove member from conversation ───────────────────────────────────────

@Composable
private fun RemoveMemberSheet(
    participants: List<UserModel>,
    myId: String?,
    onDismiss: () -> Unit,
    onRemove: (userId: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Members", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                participants.forEach { member ->
                    val isMe = member.id == myId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        InitialAvatar(
                            name = member.name,
                            color = Color(member.avatarColor.takeIf { it != 0 } ?: 0xFF6366F1.toInt()),
                            size = 36,
                            avatarUri = member.avatarUrl,
                        )
                        Text(
                            if (isMe) "${member.name} (You)" else member.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { onRemove(member.id) },
                        ) {
                            Text(
                                if (isMe) "Leave" else "Remove",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ─── Member select row (used in NewConversationSheet) ──────────────────────

@Composable
private fun MemberSelectRow(
    member: UserModel,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InitialAvatar(
            name = member.name,
            color = Color(member.avatarColor.takeIf { it != 0 } ?: 0xFF6366F1.toInt()),
            size = 42,
            avatarUri = member.avatarUrl,
        )
        Text(
            member.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
    }
}

// ─── Message rendering (unchanged) ─────────────────────────────────────────

@Composable
private fun MessageRow(
    msg: MessageModel,
    mine: Boolean,
    myId: String?,
    timeLabel: String,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    senderProfile: UserModel?,
    messages: List<MessageModel>,
    onReply: () -> Unit,
) {
    var showTime by remember { mutableStateOf(false) }

    SwipeToReplyWrapper(onReply = onReply) {
        if (mine) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = if (isFirstInGroup) 6.dp else 0.dp)
                        .pointerInput(Unit) { detectTapGestures(onTap = { showTime = !showTime }) },
                horizontalAlignment = Alignment.End,
            ) {
                Surface(
                    shape =
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = if (isLastInGroup) 4.dp else 18.dp,
                        ),
                    color = Color.Transparent,
                    modifier = Modifier.widthIn(max = 280.dp),
                ) {
                    Box(Modifier.background(BrandGradient)) {
                        MessageContent(msg, mine = true, myId = myId, messages = messages)
                    }
                }
                if (showTime && timeLabel.isNotEmpty()) {
                    Text(
                        timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                    )
                }
            }
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = if (isFirstInGroup) 6.dp else 0.dp)
                        .pointerInput(Unit) { detectTapGestures(onTap = { showTime = !showTime }) },
                verticalAlignment = Alignment.Bottom,
            ) {
                if (isLastInGroup) {
                    val avatarName = senderProfile?.name?.ifBlank { "?" } ?: "?"
                    val avatarColor =
                        Color(
                            senderProfile?.avatarColor?.takeIf { it != 0 } ?: 0xFF6366F1.toInt(),
                        )
                    InitialAvatar(
                        name = avatarName,
                        color = avatarColor,
                        size = 36,
                        avatarUri = senderProfile?.avatarUrl,
                    )
                } else {
                    Spacer(Modifier.size(36.dp))
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.widthIn(max = 280.dp)) {
                    if (isFirstInGroup && senderProfile?.name != null) {
                        Text(
                            senderProfile.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        )
                    }
                    Surface(
                        shape =
                            RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isLastInGroup) 4.dp else 18.dp,
                                bottomEnd = 18.dp,
                            ),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        MessageContent(msg, mine = false, myId = myId, messages = messages)
                    }
                    if (showTime && timeLabel.isNotEmpty()) {
                        Text(
                            timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageContent(
    msg: MessageModel,
    mine: Boolean,
    myId: String?,
    messages: List<MessageModel>,
) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        if (msg.replyToId != null) {
            val quoted = messages.find { it.id == msg.replyToId }
            if (quoted != null) {
                QuoteBubble(
                    text = quoted.text,
                    isQuotedMine = quoted.userFrom == myId,
                    isMine = mine,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        Text(
            msg.text,
            color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun QuoteBubble(
    text: String,
    isQuotedMine: Boolean,
    isMine: Boolean,
) {
    val accentColor = if (isMine) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
    val bgColor = if (isMine) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(bgColor, RoundedCornerShape(6.dp)),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accentColor, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(
                if (isQuotedMine) "You" else "Reply",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text.take(80),
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SwipeToReplyWrapper(
    onReply: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val triggerPx = remember(density) { with(density) { 64.dp.toPx() } }
    var triggered by remember { mutableStateOf(false) }
    val iconAlpha = (offsetX.value / triggerPx).coerceIn(0f, 1f)

    Box(Modifier.fillMaxWidth()) {
        Icon(
            Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .alpha(iconAlpha),
            tint = MaterialTheme.colorScheme.primary,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value >= triggerPx && !triggered) {
                                    triggered = true
                                    onReply()
                                }
                                offsetX.animateTo(0f)
                                triggered = false
                            }
                        },
                        onHorizontalDrag = { _, delta ->
                            scope.launch {
                                val next = (offsetX.value + delta).coerceIn(0f, triggerPx * 1.5f)
                                offsetX.snapTo(next)
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}

@Composable
private fun GroupImagePickerDialog(
    hasImage: Boolean,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemove: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Group image", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onCamera, modifier = Modifier.fillMaxWidth()) {
                    Text("Take photo", style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose from gallery", style = MaterialTheme.typography.bodyLarge)
                }
                if (hasImage) {
                    TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                        Text("Remove image", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
