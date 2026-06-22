package com.example.mainactivity.ui.profile

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)

    val user: StateFlow<UserEntity?> =
        repo.currentUser.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var pendingCameraFile: File? = null

    fun prepareCameraCapture(context: Context): Uri? {
        return try {
            val captureDir = File(context.cacheDir, "camera_captures").also { it.mkdirs() }
            val file = File(captureDir, "avatar_pending.jpg")
            pendingCameraFile = file
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }

    fun onCameraResult(context: Context, success: Boolean) = viewModelScope.launch {
        if (!success) { pendingCameraFile = null; return@launch }
        val file = pendingCameraFile ?: return@launch
        pendingCameraFile = null
        persistAvatar(context) { file.inputStream() }
    }

    fun saveAvatarFromUri(context: Context, sourceUri: Uri) = viewModelScope.launch {
        persistAvatar(context) { context.contentResolver.openInputStream(sourceUri) }
    }

    fun removeAvatar() = viewModelScope.launch {
        val u = user.value ?: return@launch
        repo.updateProfile(u.copy(avatarUri = null))
    }

    fun save(name: String, email: String, birthday: String, mobile: String) = viewModelScope.launch {
        val current = user.value ?: return@launch
        repo.updateProfile(current.copy(name = name.trim(), email = email.trim(), birthday = birthday.trim(), mobile = mobile.trim()))
    }

    fun signOut(onDone: () -> Unit) = viewModelScope.launch {
        repo.signOut()
        onDone()
    }

    private fun persistAvatar(context: Context, openStream: () -> java.io.InputStream?) =
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val userId = repo.currentUserId.first() ?: return@withContext
                val currentUser = repo.userDao.findById(userId) ?: return@withContext
                val avatarDir = File(context.filesDir, "avatars").also { it.mkdirs() }
                val dest = File(avatarDir, "avatar_${System.currentTimeMillis()}.jpg")
                openStream()?.use { src -> dest.outputStream().use { src.copyTo(it) } }
                    ?: return@withContext
                repo.updateProfile(currentUser.copy(avatarUri = dest.absolutePath))
                currentUser.avatarUri?.let { oldPath ->
                    val old = File(oldPath)
                    if (old.exists() && old.absolutePath != dest.absolutePath) old.delete()
                }
            }
        }
}
