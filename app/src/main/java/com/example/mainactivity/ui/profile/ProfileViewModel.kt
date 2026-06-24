package com.example.mainactivity.ui.profile

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.data.remote.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProfileViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)

    private val _user = MutableStateFlow<UserModel?>(null)
    val user: StateFlow<UserModel?> = _user.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
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
        try {
            val captureDir = File(context.cacheDir, "camera_captures").also { it.mkdirs() }
            val file = File(captureDir, "avatar_pending.jpg")
            pendingCameraFile = file
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }

    fun onCameraResult(
        context: Context,
        success: Boolean,
    ) =
        viewModelScope.launch {
            if (!success) {
                pendingCameraFile = null
                return@launch
            }
            val file = pendingCameraFile ?: return@launch
            pendingCameraFile = null
            uploadAvatar(file.readBytes())
            file.delete()
        }

    fun saveAvatarFromUri(
        context: Context,
        sourceUri: Uri,
    ) =
        viewModelScope.launch {
            val bytes =
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                } ?: return@launch
            uploadAvatar(bytes)
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
                repo.updateProfile(userId, current.name, current.email, current.birthday, current.mobile, null)
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
            repo.updateProfile(userId, name.trim(), email.trim(), birthday.trim(), mobile.trim(), current.avatarUrl)
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
                repo.updateProfile(userId, current.name, current.email, current.birthday, current.mobile, url)
                _user.value = current.copy(avatarUrl = url)
            }.onFailure { e ->
                Log.e("ProfileVM", "Avatar upload failed", e)
                _error.value = "Failed to update photo. Please try again."
            }
        }
}
