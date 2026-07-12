package com.sandnes.familyapp.ui.family

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.FamilyModel
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.data.getFamilyRelations
import com.sandnes.familyapp.data.remote.SupabaseManager
import com.sandnes.familyapp.data.setFamilyRelation
import com.sandnes.familyapp.util.compressImageWithOrientation
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val JOIN_CODE_LENGTH = 8
private const val MAX_IMAGE_DIM = 1024
private const val JPEG_QUALITY = 85

@HiltViewModel
class FamilyViewModel
    @Inject
    constructor(
        private val app: Application,
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

        /** My relation to each other member (their id → relation label), from my perspective. */
        private val _relations = MutableStateFlow<Map<String, String>>(emptyMap())
        val relations: StateFlow<Map<String, String>> = _relations.asStateFlow()

        /**
         * Set right after joining a family that already has members, so the screen can prompt the
         * newcomer to label the people already there. Mirrors iOS `promptRelationSetup`.
         */
        private val _promptRelationSetup = MutableStateFlow(false)
        val promptRelationSetup: StateFlow<Boolean> = _promptRelationSetup.asStateFlow()

        fun dismissRelationSetup() {
            _promptRelationSetup.value = false
        }

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
                    _relations.value =
                        repo
                            .getFamilyRelations(user.familyId)
                            .filter { it.fromUserId == userId }
                            .associate { it.toUserId to it.relation }
                } else {
                    _family.value = null
                    _members.value = emptyList()
                    _relations.value = emptyMap()
                }
            }
        }

        /**
         * Sets my relation ("Dad", "Wife", …) to another member, or clears it when blank. Optimistic
         * local update, then persists (RLS enforces from_user_id = me). Mirrors iOS `setRelation`.
         */
        fun setRelation(
            toUserId: String,
            relation: String,
        ) = viewModelScope.launch {
            val userId = repo.currentUserId.first() ?: return@launch
            val familyId = _family.value?.id ?: return@launch
            _relations.update { current ->
                if (relation.isBlank()) current - toUserId else current + (toUserId to relation)
            }
            repo.setFamilyRelation(userId, toUserId, familyId, relation)
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
                    .onSuccess {
                        load(userId)
                        // Offer to set relations to the members already in the family.
                        if (_members.value.any { it.id != userId }) {
                            _promptRelationSetup.value = true
                        }
                    }.onFailure { _error.value = it.message }
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
                                _error.value = app.getString(R.string.could_not_read_the_selected_photo)
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
                        _error.value = app.getString(R.string.failed_to_update_photo_please_try_again)
                    }
                } finally {
                    _isUploading.value = false
                }
            }
        }

        private suspend fun compressImage(bytes: ByteArray): ByteArray =
            withContext(Dispatchers.IO) { compressImageWithOrientation(bytes, MAX_IMAGE_DIM, JPEG_QUALITY) }

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
