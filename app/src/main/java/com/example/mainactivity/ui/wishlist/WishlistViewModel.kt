package com.example.mainactivity.ui.wishlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.WishModel
import com.example.mainactivity.data.WishlistModel
import com.example.mainactivity.data.remote.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WishlistViewModel(
    app: Application,
) : AndroidViewModel(app) {
    companion object {
        private var cache: List<WishlistModel> = emptyList()
    }

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

    private var realtimeWishlistsChannel: RealtimeChannel? = null
    private var realtimeWishesChannel: RealtimeChannel? = null
    private var subscribedFamilyId: String? = null
    private var subscribedWishesListId: String? = null

    init {
        viewModelScope.launch {
            repo.currentUserId.collect { userId ->
                if (userId != null) loadWishlists(userId) else _wishlists.value = emptyList()
            }
        }
    }

    /** Re-fetch from the server. Called on screen resume so data stays fresh
     *  even though the ViewModel is Activity-scoped and init{} only runs once. */
    fun refresh() =
        viewModelScope.launch {
            val userId = repo.currentUserId.first() ?: return@launch
            loadWishlists(userId)
        }

    private suspend fun loadWishlists(userId: String) {
        if (_wishlists.value.isEmpty()) _isLoading.value = true
        runCatching {
            val user = repo.getUser(userId)
            val result =
                if (user?.familyId != null) {
                    db
                        .from("wishlists")
                        .select {
                            filter {
                                or {
                                    eq("owner_user_id", userId)
                                    eq("family_id", user.familyId)
                                }
                            }
                        }.decodeList<WishlistModel>()
                        .filter { it.familyId == null || it.familyId == user.familyId }
                } else {
                    db
                        .from("wishlists")
                        .select { filter { eq("owner_user_id", userId) } }
                        .decodeList<WishlistModel>()
                        .filter { it.familyId == null }
                }
            cache = result
            _wishlists.value = result
            if (user?.familyId != null) subscribeToWishlists(user.familyId, userId)
        }
        _isLoading.value = false
    }

    /** Data-only reload — does NOT re-subscribe; used by the realtime collector to avoid churn. */
    private suspend fun loadWishlistsOnly(userId: String) {
        runCatching {
            val user = repo.getUser(userId)
            val result =
                if (user?.familyId != null) {
                    db
                        .from("wishlists")
                        .select {
                            filter {
                                or {
                                    eq("owner_user_id", userId)
                                    eq("family_id", user.familyId)
                                }
                            }
                        }.decodeList<WishlistModel>()
                        .filter { it.familyId == null || it.familyId == user.familyId }
                } else {
                    db
                        .from("wishlists")
                        .select { filter { eq("owner_user_id", userId) } }
                        .decodeList<WishlistModel>()
                        .filter { it.familyId == null }
                }
            cache = result
            _wishlists.value = result
        }
    }

    private suspend fun subscribeToWishlists(
        familyId: String,
        userId: String,
    ) {
        // Guard: if already subscribed to the same family, don't re-subscribe
        if (subscribedFamilyId == familyId && realtimeWishlistsChannel != null) return
        realtimeWishlistsChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
        val channel = SupabaseManager.client.channel("wishlists-$familyId")
        realtimeWishlistsChannel = channel
        subscribedFamilyId = familyId
        val flow =
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "wishlists"
                filter("family_id", FilterOperator.EQ, familyId)
            }
        channel.subscribe()
        viewModelScope.launch { flow.collect { loadWishlistsOnly(userId) } }
    }

    fun loadWishlistDetail(wishlistId: String) =
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val wishlistDeferred =
                        async {
                            db
                                .from("wishlists")
                                .select { filter { eq("id", wishlistId) } }
                                .decodeList<WishlistModel>()
                                .firstOrNull()
                        }
                    val wishesDeferred =
                        async {
                            db
                                .from("wishes")
                                .select { filter { eq("wishlist_id", wishlistId) } }
                                .decodeList<WishModel>()
                        }
                    _selectedWishlist.value = wishlistDeferred.await()
                    _wishes.value = wishesDeferred.await()
                }
            }
            subscribeToWishes(wishlistId)
        }

    /** Data-only wishes reload — does NOT re-subscribe; used by the realtime collector. */
    private suspend fun loadWishesOnly(wishlistId: String) {
        runCatching {
            _wishes.value =
                db
                    .from("wishes")
                    .select { filter { eq("wishlist_id", wishlistId) } }
                    .decodeList<WishModel>()
        }
    }

    private suspend fun subscribeToWishes(wishlistId: String) {
        // Guard: if already subscribed to this wishlist, don't re-subscribe
        if (subscribedWishesListId == wishlistId && realtimeWishesChannel != null) return
        realtimeWishesChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
        val channel = SupabaseManager.client.channel("wishes-$wishlistId")
        realtimeWishesChannel = channel
        subscribedWishesListId = wishlistId
        val flow =
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "wishes"
                filter("wishlist_id", FilterOperator.EQ, wishlistId)
            }
        channel.subscribe()
        viewModelScope.launch { flow.collect { loadWishesOnly(wishlistId) } }
    }

    fun addWishlist(
        name: String,
        icon: String = "card_giftcard",
    ) =
        viewModelScope.launch {
            val userId = repo.currentUserId.first() ?: return@launch
            val user = repo.getUser(userId)
            val tempId = "temp-${System.currentTimeMillis()}"
            _wishlists.value =
                _wishlists.value +
                WishlistModel(id = tempId, ownerUserId = userId, familyId = user?.familyId, name = name, icon = icon)
            runCatching {
                db.from("wishlists").insert(
                    buildJsonObject {
                        put("owner_user_id", userId)
                        if (user?.familyId != null) put("family_id", user.familyId)
                        put("name", name)
                        put("icon", icon)
                    },
                )
            }
            loadWishlists(userId)
        }

    fun deleteWishlist(wishlist: WishlistModel) =
        viewModelScope.launch {
            _wishlists.value = _wishlists.value.filter { it.id != wishlist.id }
            runCatching { db.from("wishlists").delete { filter { eq("id", wishlist.id) } } }
            val userId = repo.currentUserId.first() ?: return@launch
            loadWishlists(userId)
        }

    fun renameWishlist(
        wishlistId: String,
        newName: String,
    ) = viewModelScope.launch {
        _wishlists.value = _wishlists.value.map { if (it.id == wishlistId) it.copy(name = newName) else it }
        _selectedWishlist.value = _selectedWishlist.value?.copy(name = newName)
        runCatching {
            db.from("wishlists").update({ set("name", newName) }) { filter { eq("id", wishlistId) } }
        }
        loadWishlistDetail(wishlistId).join()
    }

    fun changeWishlistIcon(
        wishlistId: String,
        newIcon: String,
    ) = viewModelScope.launch {
        _wishlists.value = _wishlists.value.map { if (it.id == wishlistId) it.copy(icon = newIcon) else it }
        _selectedWishlist.value = _selectedWishlist.value?.copy(icon = newIcon)
        runCatching {
            db.from("wishlists").update({ set("icon", newIcon) }) { filter { eq("id", wishlistId) } }
        }
        loadWishlistDetail(wishlistId).join()
    }

    fun addWish(
        wishlistId: String,
        text: String,
    ) =
        viewModelScope.launch {
            val userId = repo.currentUserId.first() ?: return@launch
            val tempId = "temp-${System.currentTimeMillis()}"
            _wishes.value = _wishes.value + WishModel(id = tempId, wishlistId = wishlistId, userId = userId, text = text, checked = false)
            runCatching {
                db.from("wishes").insert(
                    buildJsonObject {
                        put("wishlist_id", wishlistId)
                        put("user_id", userId)
                        put("text", text)
                    },
                )
            }
            loadWishlistDetail(wishlistId).join()
        }

    fun toggle(wish: WishModel) =
        viewModelScope.launch {
            _wishes.value = _wishes.value.map { if (it.id == wish.id) it.copy(checked = !wish.checked) else it }
            runCatching {
                db.from("wishes").update({
                    set("checked", !wish.checked)
                }) { filter { eq("id", wish.id) } }
            }
            loadWishlistDetail(wish.wishlistId).join()
        }

    fun deleteWish(wish: WishModel) =
        viewModelScope.launch {
            _wishes.value = _wishes.value.filter { it.id != wish.id }
            runCatching { db.from("wishes").delete { filter { eq("id", wish.id) } } }
            loadWishlistDetail(wish.wishlistId).join()
        }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            realtimeWishlistsChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
            realtimeWishesChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
        }
    }
}
