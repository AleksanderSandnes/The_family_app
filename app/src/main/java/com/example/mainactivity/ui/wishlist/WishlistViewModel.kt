package com.example.mainactivity.ui.wishlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.WishEntity
import com.example.mainactivity.data.WishlistEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WishlistViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)
    private val dao = repo.wishlistDao

    val wishlists: Flow<List<WishlistEntity>> = repo.currentUserId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.wishlistsForUser(id)
    }

    fun addWishlist(name: String) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        dao.insertWishlist(WishlistEntity(ownerUserId = userId, name = name))
    }

    fun deleteWishlist(wishlist: WishlistEntity) = viewModelScope.launch { dao.deleteWishlist(wishlist) }

    fun wishlist(id: Long) = dao.observeWishlist(id)
    fun wishes(wishlistId: Long) = dao.wishesForList(wishlistId)

    fun addWish(wishlistId: Long, text: String) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        dao.insertWish(WishEntity(wishlistId = wishlistId, text = text, userId = userId))
    }

    fun toggle(wish: WishEntity) = viewModelScope.launch { dao.updateWish(wish.copy(checked = !wish.checked)) }
    fun deleteWish(wish: WishEntity) = viewModelScope.launch { dao.deleteWish(wish) }
}
