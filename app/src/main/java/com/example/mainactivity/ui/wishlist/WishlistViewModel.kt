package com.example.mainactivity.ui.wishlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.WishModel
import com.example.mainactivity.data.WishlistModel
import com.example.mainactivity.data.remote.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WishlistViewModel(app: Application) : AndroidViewModel(app) {
    companion object { private var cache: List<WishlistModel> = emptyList() }

    private val repo = FamilyRepository.get(app)
    private val db get() = SupabaseManager.client.postgrest

    private val _wishlists = MutableStateFlow(cache)
    val wishlists: StateFlow<List<WishlistModel>> = _wishlists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedWishlist = MutableStateFlow<WishlistModel?>(null)
    val selectedWishlist: StateFlow<WishlistModel?> = _selectedWishlist.asStateFlow()

    private val _wishes = MutableStateFlow<List<WishModel>>(emptyList())
    val wishes: StateFlow<List<WishModel>> = _wishes.asStateFlow()

    init {
        viewModelScope.launch {
            repo.currentUserId.collect { userId ->
                if (userId != null) loadWishlists(userId) else _wishlists.value = emptyList()
            }
        }
    }

    private suspend fun loadWishlists(userId: String) {
        if (_wishlists.value.isEmpty()) _isLoading.value = true
        runCatching {
            val result = db.from("wishlists")
                .select { filter { eq("owner_user_id", userId) } }
                .decodeList<WishlistModel>()
            cache = result
            _wishlists.value = result
        }
        _isLoading.value = false
    }

    fun loadWishlistDetail(wishlistId: String) = viewModelScope.launch {
        runCatching {
            _selectedWishlist.value = db.from("wishlists")
                .select { filter { eq("id", wishlistId) } }
                .decodeList<WishlistModel>()
                .firstOrNull()
            _wishes.value = db.from("wishes")
                .select { filter { eq("wishlist_id", wishlistId) } }
                .decodeList<WishModel>()
        }
    }

    fun addWishlist(name: String) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        runCatching {
            db.from("wishlists").insert(buildJsonObject {
                put("owner_user_id", userId)
                put("name", name)
            })
        }
        loadWishlists(userId)
    }

    fun deleteWishlist(wishlist: WishlistModel) = viewModelScope.launch {
        runCatching { db.from("wishlists").delete { filter { eq("id", wishlist.id) } } }
        val userId = repo.currentUserId.first() ?: return@launch
        loadWishlists(userId)
    }

    fun addWish(wishlistId: String, text: String) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        runCatching {
            db.from("wishes").insert(buildJsonObject {
                put("wishlist_id", wishlistId)
                put("user_id", userId)
                put("text", text)
            })
        }
        loadWishlistDetail(wishlistId).join()
    }

    fun toggle(wish: WishModel) = viewModelScope.launch {
        runCatching {
            db.from("wishes").update({
                set("checked", !wish.checked)
            }) { filter { eq("id", wish.id) } }
        }
        loadWishlistDetail(wish.wishlistId).join()
    }

    fun deleteWish(wish: WishModel) = viewModelScope.launch {
        runCatching { db.from("wishes").delete { filter { eq("id", wish.id) } } }
        loadWishlistDetail(wish.wishlistId).join()
    }
}
