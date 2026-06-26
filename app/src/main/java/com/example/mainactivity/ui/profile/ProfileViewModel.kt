package com.example.mainactivity.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.ProfileUpdate
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.data.remote.SupabaseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

private const val MAX_IMAGE_DIM = 1024
private const val JPEG_QUALITY = 85

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        internal val repo: FamilyRepository,
    ) : ViewModel() {
        private val _user = MutableStateFlow<UserModel?>(null)
        val user: StateFlow<UserModel?> = _user.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private val _isUploading = MutableStateFlow(false)
        val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

        fun clearError() {
            _error.value = null
        }

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
                    _error.value = "Could not read the captured photo."
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
                        _error.value = "Could not read the selected photo."
                        return@launch
                    }
                    uploadAvatar(compressImage(raw))
                }.onFailure { e ->
                    Log.e("ProfileVM", "Failed to read photo from URI", e)
                    _error.value = "Could not read the selected photo."
                }
            }

        private suspend fun compressImage(bytes: ByteArray): ByteArray =
            withContext(Dispatchers.IO) {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext bytes
                val maxDim = MAX_IMAGE_DIM
                val scale = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height, 1f)
                val scaled =
                    if (scale < 1f) {
                        Bitmap.createScaledBitmap(
                            bmp,
                            (bmp.width * scale).toInt(),
                            (bmp.height * scale).toInt(),
                            true,
                        )
                    } else {
                        bmp
                    }
                ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
            }

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
                        _error.value = "Failed to update photo. Please try again."
                    }
                } finally {
                    _isUploading.value = false
                }
            }
    }
