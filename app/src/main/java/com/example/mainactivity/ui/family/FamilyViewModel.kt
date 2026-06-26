package com.example.mainactivity.ui.family

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyModel
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.data.remote.SupabaseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

private const val JOIN_CODE_LENGTH = 8
private const val MAX_IMAGE_DIM = 1024
private const val JPEG_QUALITY = 85

@HiltViewModel
class FamilyViewModel
    @Inject
    constructor(
        internal val repo: FamilyRepository,
    ) : ViewModel() {
        private val _family = MutableStateFlow<FamilyModel?>(null)
        val family: StateFlow<FamilyModel?> = _family.asStateFlow()

        private val _members = MutableStateFlow<List<UserModel>>(emptyList())
        val members: StateFlow<List<UserModel>> = _members.asStateFlow()

        private val _currentUser = MutableStateFlow<UserModel?>(null)
        val currentUser: StateFlow<UserModel?> = _currentUser.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private val _isUploading = MutableStateFlow(false)
        val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

        /** Invite code captured from a deep link; FamilyScreen opens the join flow with it. */
        val pendingJoinCode = repo.pendingJoinCode

        fun consumePendingJoinCode() = repo.consumePendingJoinCode()

        init {
            viewModelScope.launch {
                repo.currentUserId.collect { userId ->
                    if (userId != null) {
                        load(userId)
                    } else {
                        _family.value = null
                        _members.value = emptyList()
                        _currentUser.value = null
                    }
                }
            }
        }

        fun refresh() =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                load(userId)
            }

        private suspend fun load(userId: String) {
            runCatching {
                val user = repo.getUser(userId) ?: return
                _currentUser.value = user
                if (user.familyId != null) {
                    _family.value = repo.getFamily(user.familyId)
                    _members.value =
                        SupabaseManager.client.postgrest
                            .from("users")
                            .select { filter { eq("family_id", user.familyId) } }
                            .decodeList<UserModel>()
                } else {
                    _family.value = null
                    _members.value = emptyList()
                }
            }
        }

        fun clearError() {
            _error.value = null
        }

        fun createFamily(
            name: String,
            code: String,
        ) =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                repo
                    .createFamily(name, code, userId)
                    .onSuccess { load(userId) }
                    .onFailure { _error.value = it.message }
            }

        fun joinFamily(code: String) =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                repo
                    .joinFamily(code, userId)
                    .onSuccess { load(userId) }
                    .onFailure { _error.value = it.message }
            }

        fun leaveFamily() =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                repo.leaveFamily(userId)
                load(userId)
            }

        fun generateJoinCode(): String =
            java.util.UUID
                .randomUUID()
                .toString()
                .take(JOIN_CODE_LENGTH)
                .uppercase()

        fun renameFamily(newName: String) {
            val fid = _family.value?.id ?: return
            viewModelScope.launch {
                repo
                    .renameFamily(fid, newName)
                    .onSuccess {
                        val userId = repo.currentUserId.first() ?: return@launch
                        load(userId)
                    }.onFailure { _error.value = it.message }
            }
        }

        fun uploadFamilyPhoto(
            context: Context,
            uri: Uri,
        ) {
            val fid = _family.value?.id ?: return
            viewModelScope.launch {
                _isUploading.value = true
                try {
                    runCatching {
                        val raw =
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            } ?: run {
                                _error.value = "Could not read the selected photo."
                                return@launch
                            }
                        val compressed = compressImage(raw)
                        val bucket = SupabaseManager.client.storage.from("group-images")
                        val path = "family-photos/$fid/photo.jpg"
                        bucket.upload(path, compressed) { upsert = true }
                        val url = bucket.publicUrl(path) + "?t=${System.currentTimeMillis()}"
                        repo.updateFamilyPhoto(fid, url).getOrThrow()
                        val userId = repo.currentUserId.first() ?: return@launch
                        load(userId)
                    }.onFailure { e ->
                        Log.e("FamilyVM", "Photo upload failed", e)
                        _error.value = "Failed to update photo. Please try again."
                    }
                } finally {
                    _isUploading.value = false
                }
            }
        }

        private suspend fun compressImage(bytes: ByteArray): ByteArray =
            withContext(Dispatchers.IO) {
                val bmp =
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return@withContext bytes
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

        fun removeMember(memberId: String) {
            viewModelScope.launch {
                repo
                    .removeFamilyMember(memberId)
                    .onSuccess {
                        val userId = repo.currentUserId.first() ?: return@launch
                        load(userId)
                    }.onFailure { _error.value = it.message }
            }
        }
    }
