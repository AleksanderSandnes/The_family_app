package com.sandnes.familyapp.ui.profile

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.ProfileUpdate
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.data.remote.SupabaseManager
import com.sandnes.familyapp.util.compressImageWithOrientation
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject

private const val MAX_IMAGE_DIM = 1024
private const val JPEG_QUALITY = 85
private const val STOP_TIMEOUT_MS = 5000L

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val app: Application,
        internal val repo: FamilyRepository,
    ) : ViewModel() {
        private val _user = MutableStateFlow<UserModel?>(null)
        val user: StateFlow<UserModel?> = _user.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private val _isUploading = MutableStateFlow(false)
        val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

        // One-time "complete your profile" prompt after a Google sign-up: Google doesn't provide
        // phone/birthday. Dismissed for the session once the user saves or skips.
        private val dismissedCompletion = MutableStateFlow(false)
        val needsProfileCompletion: StateFlow<Boolean> =
            combine(_user, dismissedCompletion) { user, dismissed ->
                !dismissed &&
                    user != null &&
                    isGoogleSignedIn() &&
                    (user.mobile.isBlank() || user.birthday.isBlank())
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

        fun clearError() {
            _error.value = null
        }

        fun dismissProfileCompletion() {
            dismissedCompletion.value = true
        }

        /** Saves the phone/birthday collected by the one-time Google completion prompt. */
        fun completeGoogleProfile(
            mobile: String,
            birthday: String,
        ) = viewModelScope.launch {
            val userId = repo.currentUserId.first() ?: return@launch
            val current = _user.value ?: return@launch
            val newMobile = mobile.trim().ifBlank { current.mobile }
            val newBirthday = birthday.trim().ifBlank { current.birthday }
            repo.updateProfile(
                userId,
                ProfileUpdate(current.name, current.email, newBirthday, newMobile, current.avatarUrl),
            )
            _user.value = current.copy(mobile = newMobile, birthday = newBirthday)
            dismissedCompletion.value = true
        }

        /** True when the active auth session was created via the Google OAuth provider. */
        private fun isGoogleSignedIn(): Boolean =
            runCatching {
                val meta =
                    SupabaseManager.client.auth
                        .currentSessionOrNull()
                        ?.user
                        ?.appMetadata
                val provider = meta?.get("provider")?.jsonPrimitive?.content
                provider == "google"
            }.getOrDefault(false)

        fun refresh() {
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                _user.value = repo.getUser(userId)
            }
        }

        private var pendingCameraFile: File? = null

        init {
            viewModelScope.launch {
                repo.currentUserId.collect { userId ->
                    _user.value = if (userId != null) repo.getUser(userId) else null
                }
            }
        }

        fun prepareCameraCapture(context: Context): Uri? =
            runCatching {
                val captureDir = File(context.cacheDir, "camera_captures").also { it.mkdirs() }
                val file = File(captureDir, "avatar_pending.jpg")
                pendingCameraFile = file
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull()

        fun onCameraResult(success: Boolean) =
            viewModelScope.launch {
                if (!success) {
                    pendingCameraFile = null
                    return@launch
                }
                val file = pendingCameraFile ?: return@launch
                pendingCameraFile = null
                runCatching {
                    uploadAvatar(compressImage(file.readBytes()))
                }.onFailure { e ->
                    Log.e("ProfileVM", "Failed to read camera capture", e)
                    _error.value = app.getString(R.string.could_not_read_the_captured_photo)
                }
                file.delete()
            }

        fun saveAvatarFromUri(
            context: Context,
            sourceUri: Uri,
        ) =
            viewModelScope.launch {
                _error.value = null
                runCatching {
                    val raw =
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                        }
                    if (raw == null) {
                        _error.value = app.getString(R.string.could_not_read_the_selected_photo)
                        return@launch
                    }
                    uploadAvatar(compressImage(raw))
                }.onFailure { e ->
                    Log.e("ProfileVM", "Failed to read photo from URI", e)
                    _error.value = app.getString(R.string.could_not_read_the_selected_photo)
                }
            }

        private suspend fun compressImage(bytes: ByteArray): ByteArray =
            withContext(Dispatchers.IO) { compressImageWithOrientation(bytes, MAX_IMAGE_DIM, JPEG_QUALITY) }

        fun removeAvatar() =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                val current = _user.value ?: return@launch
                runCatching {
                    val authId =
                        SupabaseManager.client.auth
                            .currentSessionOrNull()
                            ?.user
                            ?.id
                            ?: error("No auth session")
                    SupabaseManager.client.storage
                        .from("avatars")
                        .delete("$authId/avatar.jpg")
                    repo.updateProfile(userId, ProfileUpdate(current.name, current.email, current.birthday, current.mobile, null))
                    _user.value = current.copy(avatarUrl = null)
                }.onFailure { e -> Log.e("ProfileVM", "Avatar remove failed", e) }
            }

        fun save(
            name: String,
            email: String,
            birthday: String,
            mobile: String,
        ) =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                val current = _user.value ?: return@launch
                repo.updateProfile(userId, ProfileUpdate(name.trim(), email.trim(), birthday.trim(), mobile.trim(), current.avatarUrl))
                _user.value =
                    current.copy(
                        name = name.trim(),
                        email = email.trim(),
                        birthday = birthday.trim(),
                        mobile = mobile.trim(),
                    )
            }

        fun signOut(onDone: () -> Unit) =
            viewModelScope.launch {
                repo.signOut()
                onDone()
            }

        private fun uploadAvatar(bytes: ByteArray) =
            viewModelScope.launch {
                _isUploading.value = true
                try {
                    val userId = repo.currentUserId.first() ?: return@launch
                    val current = _user.value ?: return@launch
                    runCatching {
                        val authId =
                            SupabaseManager.client.auth
                                .currentSessionOrNull()
                                ?.user
                                ?.id
                                ?: error("No auth session")
                        val bucket = SupabaseManager.client.storage.from("avatars")
                        bucket.upload("$authId/avatar.jpg", bytes) { upsert = true }
                        val url = bucket.publicUrl("$authId/avatar.jpg") + "?t=${System.currentTimeMillis()}"
                        repo.updateProfile(userId, ProfileUpdate(current.name, current.email, current.birthday, current.mobile, url))
                        _user.value = current.copy(avatarUrl = url)
                    }.onFailure { e ->
                        Log.e("ProfileVM", "Avatar upload failed", e)
                        _error.value = app.getString(R.string.failed_to_update_photo_please_try_again)
                    }
                } finally {
                    _isUploading.value = false
                }
            }
    }
