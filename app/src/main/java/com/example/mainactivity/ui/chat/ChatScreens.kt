@file:Suppress("ktlint:standard:function-naming")

package com.example.mainactivity.ui.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.mainactivity.data.ConversationWithPreview
import com.example.mainactivity.data.MessageModel
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.ui.chat.components.ReactionChipsRow
import com.example.mainactivity.ui.chat.components.ReactionPickerPopup
import com.example.mainactivity.ui.chat.components.VoiceNoteMessage
import com.example.mainactivity.ui.components.EmptyState
import com.example.mainactivity.ui.components.FeatureTopBar
import com.example.mainactivity.ui.components.InitialAvatar
import com.example.mainactivity.ui.components.InputDialog
import com.example.mainactivity.ui.components.LoadingState
import com.example.mainactivity.ui.theme.BrandGradient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─── Relative time helper ──────────────────────────────────────────────────

private fun relativeTime(isoString: String): String {
    return try {
        val instant = java.time.Instant.parse(isoString)
        val nowMs = System.currentTimeMillis()
        val diffMs = nowMs - instant.toEpochMilli()
        val diffMin = diffMs / 60_000
        val diffH = diffMs / 3_600_000
        val diffD = diffMs / 86_400_000
        when {
            diffMin < 1 -> "now"
            diffH < 1 -> "${diffMin}m"
            diffD < 1 -> "${diffH}h"
            diffD < 7 -> "${diffD}d"
            else -> {
                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = instant.toEpochMilli()
                val month = cal.getDisplayName(
                    java.util.Calendar.MONTH,
                    java.util.Calendar.SHORT,
                    java.util.Locale.getDefault(),
                ) ?: ""
                "$month ${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
            }
        }
    } catch (e: Exception) {
        ""
    }
}

// ─── Chat list screen ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpen: (String) -> Unit,
    viewModel: ChatViewModel = viewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle(emptyList())
    val familyMembers by viewModel.familyMembers.collectAsStateWithLifecycle()
    val myId by viewModel.currentUserId.collectAsStateWithLifecycle(null)
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle(false)
    var showMemberPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navigateToConversation.collect { newId -> onOpen(newId) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            FeatureTopBar(
                title = "Chats",
                actions = {
                    IconButton(onClick = { showMemberPicker = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "New conversation")
                    }
                },
            )
        },
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingState()
            }
        } else if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.AutoMirrored.Filled.Chat,
                    "No conversations",
                    "Start a chat to keep your family connected.",
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            ) {
                items(conversations, key = { it.conversation.id }) { preview ->
                    ConversationRow(
                        preview = preview,
                        currentUserId = myId ?: "",
                        onClick = { onOpen(preview.conversation.id) },
                    )
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

// ─── Messenger-style conversation row ─────────────────────────────────────

@Composable
private fun ConversationRow(
    preview: ConversationWithPreview,
    currentUserId: String,
    onClick: () -> Unit,
) {
    val conv = preview.conversation
    val isUnread = preview.unreadCount > 0
    val participants = preview.participants
    val isOneOnOne = participants.size == 2 || conv.userTo != null
    val other = participants.firstOrNull { it.id != currentUserId }

    val displayName = remember(conv, participants, currentUserId) {
        when {
            isOneOnOne -> other?.name ?: conv.name.takeIf { it.isNotBlank() } ?: "Chat"
            conv.name.isNotBlank() -> conv.name
            else -> participants
                .filter { it.id != currentUserId }
                .take(3)
                .joinToString(", ") { it.name.split(" ").first() }
                .ifBlank { "Group chat" }
        }
    }
    val avatarUri = if (conv.imageUri != null) conv.imageUri
    else if (isOneOnOne) other?.avatarUrl
    else null
    val avatarColor = Color(
        (if (isOneOnOne) other?.avatarColor else null)
            ?.takeIf { it != 0 } ?: 0xFF6366F1.toInt(),
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InitialAvatar(name = displayName, color = avatarColor, size = 56, avatarUri = avatarUri)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    preview.lastMessage?.let { msg ->
                        Text(
                            text = relativeTime(msg.sentAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUnread) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val previewText = when {
                        preview.lastMessage == null -> "No messages yet"
                        preview.lastMessage.messageType == "image" ->
                            "${preview.lastSenderName?.let { "$it: " } ?: ""}📷 Photo"

                        preview.lastMessage.messageType == "voice" ->
                            "${preview.lastSenderName?.let { "$it: " } ?: ""}🎤 Voice message"

                        preview.lastSenderName != null ->
                            "${preview.lastSenderName}: ${preview.lastMessage.text}"

                        else -> preview.lastMessage.text
                    }
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUnread) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isUnread) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935)),
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 84.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        )
    }
}

// ─── Conversation / message screen ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationScreen(
    conversationId: String,
    onBack: () -> Unit,
    onNavigateTo: (String) -> Unit = {},
    viewModel: ChatViewModel = viewModel(),
) {
    LaunchedEffect(conversationId) {
        viewModel.loadConversation(conversationId)
        viewModel.setCurrentConversation(conversationId)
        viewModel.markRead(conversationId)
    }

    DisposableEffect(conversationId) {
        onDispose { viewModel.setCurrentConversation(null) }
    }

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
    val reactionsMap by viewModel.reactions.collectAsStateWithLifecycle()

    val title = remember(conversation, currentParticipants, myId) {
        val conv = conversation ?: return@remember "Chat"
        val others = currentParticipants.filter { it.id != myId }
        when {
            others.size == 1 -> others.first().name
            conv.name.isNotBlank() -> conv.name
            others.size > 1 -> others.take(3).joinToString(", ") { it.name.split(" ").first() }
            else -> "Chat"
        }
    }

    val availableToAdd = remember(familyMembers, currentParticipants, myId) {
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

    // Voice recording state
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var mediaRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<java.io.File?>(null) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    val listState = rememberLazyListState()
    var prevMsgCount by remember { mutableStateOf(0) }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) {
            prevMsgCount = 0
            return@LaunchedEffect
        }
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
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
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            messages.isNotEmpty() && lastVisible < messages.lastIndex - 1
        }
    }

    // Group image launchers (existing — unchanged)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.saveImageFromUri(context, it, conversationId) } }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success -> viewModel.onCameraResult(context, success) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.prepareCameraCapture(context, conversationId)?.let { cameraLauncher.launch(it) }
    }

    // Message media launchers (new — for sending images in chat)
    val msgGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@rememberLauncherForActivityResult
        viewModel.sendImage(conversationId, bytes, "img_${System.currentTimeMillis()}.jpg")
    }

    val msgCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
        viewModel.sendImage(conversationId, stream.toByteArray(), "cam_${System.currentTimeMillis()}.jpg")
    }

    fun startRecording() {
        val file = java.io.File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        recordingFile = file
        val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.media.MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            android.media.MediaRecorder()
        }
        recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
        recorder.setOutputFile(file.absolutePath)
        runCatching { recorder.prepare(); recorder.start() }
        mediaRecorder = recorder
        isRecording = true
    }

    fun stopRecording(send: Boolean) {
        isRecording = false
        runCatching { mediaRecorder?.stop(); mediaRecorder?.release() }
        mediaRecorder = null
        if (send) {
            recordingFile?.let { file ->
                val bytes = file.readBytes()
                viewModel.sendVoice(conversationId, bytes, file.name)
                file.delete()
            }
        } else {
            recordingFile?.delete()
        }
        recordingFile = null
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
                            if (isGroup) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = { showMenu = false; showRename = true },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Change image") },
                                onClick = { showMenu = false; showImagePicker = true },
                            )
                            if (conversation?.imageUri != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove image", color = MaterialTheme.colorScheme.error) },
                                    onClick = { showMenu = false; viewModel.removeImage(conversationId) },
                                )
                            }
                            if (availableToAdd.isNotEmpty()) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (isGroup) "Add member" else "Add member (creates group)") },
                                    leadingIcon = { Icon(Icons.Filled.GroupAdd, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { showMenu = false; showAddMember = true },
                                )
                            }
                            if (isGroup) {
                                DropdownMenuItem(
                                    text = { Text("Remove member", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Filled.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { showMenu = false; showRemoveMember = true },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete conversation", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; showDeleteConfirm = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column {
                    // Reply indicator
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
                                        when (quoted.messageType) {
                                            "image" -> "📷 Photo"
                                            "voice" -> "🎤 Voice message"
                                            else -> quoted.text.take(80)
                                        },
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

                    if (isRecording) {
                        // Recording overlay
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val dotAlpha by rememberInfiniteTransition(label = "rec").animateFloat(
                                initialValue = 1f,
                                targetValue = 0.2f,
                                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                                label = "dot",
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE53935).copy(alpha = dotAlpha)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "%d:%02d".format(recordingSeconds / 60, recordingSeconds % 60),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "← Slide to cancel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { stopRecording(false) }) {
                                Icon(Icons.Filled.Close, "Cancel recording", tint = Color(0xFFE53935))
                            }
                        }
                    } else {
                        // Messenger-style input row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            // Gallery + camera icons (hidden when typing)
                            AnimatedVisibility(
                                visible = draft.isEmpty(),
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally(),
                            ) {
                                Row {
                                    IconButton(onClick = {
                                        msgGalleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    }) {
                                        Icon(
                                            Icons.Filled.PhotoLibrary,
                                            contentDescription = "Send image",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(onClick = { msgCameraLauncher.launch(null) }) {
                                        Icon(
                                            Icons.Filled.CameraAlt,
                                            contentDescription = "Camera",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }

                            // Text field
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        "Aa",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    )
                                },
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 5,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                ),
                            )

                            // Send or mic
                            AnimatedContent(
                                targetState = draft.isNotBlank(),
                                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                                label = "send_mic",
                            ) { hasText ->
                                if (hasText) {
                                    IconButton(onClick = {
                                        viewModel.send(conversationId, draft.trim())
                                        draft = ""
                                        keyboardController?.hide()
                                    }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    startRecording()
                                                    tryAwaitRelease()
                                                    stopRecording(true)
                                                },
                                            )
                                        },
                                    ) {
                                        Icon(
                                            Icons.Filled.Mic,
                                            contentDescription = "Record voice note",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.AutoMirrored.Filled.Chat,
                    "Say hello 👋",
                    "Send the first message in this conversation.",
                )
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
                        val timeLabel = remember(msg.sentAt) {
                            runCatching {
                                java.time.OffsetDateTime
                                    .parse(msg.sentAt)
                                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                            }.getOrDefault("")
                        }
                        val msgReactions = reactionsMap[msg.id] ?: emptyMap()
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
                            reactions = msgReactions,
                            onReact = { emoji -> viewModel.toggleReaction(msg.id, conversationId, emoji) },
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
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
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
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
                                selectedIds = if (member.id in selectedIds) selectedIds - member.id
                                else selectedIds + member.id
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
                Text(if (isGroup) "Create group" else "Start chat", style = MaterialTheme.typography.labelLarge)
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Add member", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
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
                        TextButton(onClick = { onRemove(member.id) }) {
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
        Text(member.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
    }
}

// ─── Message rendering ─────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
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
    reactions: Map<String, List<String>>,
    onReact: (String) -> Unit,
) {
    var showTime by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    val myReaction = reactions.entries.find { myId != null && myId in it.value }?.key

    SwipeToReplyWrapper(onReply = onReply) {
        if (mine) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isFirstInGroup) 6.dp else 0.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Box {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = if (isLastInGroup) 4.dp else 18.dp,
                        ),
                        color = Color.Transparent,
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .combinedClickable(
                                onLongClick = { showReactionPicker = true },
                                onClick = { showTime = !showTime },
                            ),
                    ) {
                        Box(Modifier.background(BrandGradient)) {
                            MessageContent(msg, mine = true, myId = myId, messages = messages)
                        }
                    }
                    if (showReactionPicker) {
                        ReactionPickerPopup(
                            onReact = { emoji -> onReact(emoji); showReactionPicker = false },
                            onDismiss = { showReactionPicker = false },
                        )
                    }
                }
                if (reactions.isNotEmpty()) {
                    ReactionChipsRow(
                        reactions = reactions,
                        myReaction = myReaction,
                        isMine = true,
                        onTap = onReact,
                    )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isFirstInGroup) 6.dp else 0.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (isLastInGroup) {
                    val avatarName = senderProfile?.name?.ifBlank { "?" } ?: "?"
                    val avatarColor = Color(senderProfile?.avatarColor?.takeIf { it != 0 } ?: 0xFF6366F1.toInt())
                    InitialAvatar(name = avatarName, color = avatarColor, size = 36, avatarUri = senderProfile?.avatarUrl)
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
                    Box {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isLastInGroup) 4.dp else 18.dp,
                                bottomEnd = 18.dp,
                            ),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.combinedClickable(
                                onLongClick = { showReactionPicker = true },
                                onClick = { showTime = !showTime },
                            ),
                        ) {
                            MessageContent(msg, mine = false, myId = myId, messages = messages)
                        }
                        if (showReactionPicker) {
                            ReactionPickerPopup(
                                onReact = { emoji -> onReact(emoji); showReactionPicker = false },
                                onDismiss = { showReactionPicker = false },
                            )
                        }
                    }
                    if (reactions.isNotEmpty()) {
                        ReactionChipsRow(
                            reactions = reactions,
                            myReaction = myReaction,
                            isMine = false,
                            onTap = onReact,
                        )
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
    when (msg.messageType) {
        "image" -> {
            AsyncImage(
                model = msg.mediaUrl,
                contentDescription = "Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .heightIn(max = 260.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
        }
        "voice" -> {
            Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                VoiceNoteMessage(url = msg.mediaUrl ?: "", isMine = mine)
            }
        }
        else -> {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (msg.replyToId != null) {
                    val quoted = messages.find { it.id == msg.replyToId }
                    if (quoted != null) {
                        QuoteBubble(
                            text = when (quoted.messageType) {
                                "image" -> "📷 Photo"
                                "voice" -> "🎤 Voice message"
                                else -> quoted.text
                            },
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
            modifier = Modifier
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
                        Text(
                            "Remove image",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
