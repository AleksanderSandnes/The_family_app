package com.example.mainactivity.ui.shopping

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.ShoppingItemEntity
import com.example.mainactivity.data.ShoppingListEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)
    private val dao = repo.shoppingDao

    val lists: Flow<List<ShoppingListEntity>> = repo.currentUserId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.listsForUser(id)
    }

    fun addList(title: String) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        dao.insertList(ShoppingListEntity(title = title, ownerUserId = userId))
    }

    fun deleteList(list: ShoppingListEntity) = viewModelScope.launch { dao.deleteList(list) }

    fun list(id: Long) = dao.observeList(id)
    fun items(listId: Long) = dao.itemsForList(listId)

    fun addItem(listId: Long, item: String) = viewModelScope.launch {
        dao.insertItem(ShoppingItemEntity(listId = listId, item = item))
    }

    fun toggle(item: ShoppingItemEntity) = viewModelScope.launch {
        dao.updateItem(item.copy(checked = !item.checked))
    }

    fun deleteItem(item: ShoppingItemEntity) = viewModelScope.launch { dao.deleteItem(item) }
}
