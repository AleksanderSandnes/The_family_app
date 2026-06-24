package com.example.mainactivity.ui.shopping

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.ShoppingItemModel
import com.example.mainactivity.data.ShoppingListModel
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

class ShoppingViewModel(app: Application) : AndroidViewModel(app) {
    companion object { private var cache: List<ShoppingListModel> = emptyList() }

    private val repo = FamilyRepository.get(app)
    private val db get() = SupabaseManager.client.postgrest

    private val _lists = MutableStateFlow(cache)
    val lists: StateFlow<List<ShoppingListModel>> = _lists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedList = MutableStateFlow<ShoppingListModel?>(null)
    val selectedList: StateFlow<ShoppingListModel?> = _selectedList.asStateFlow()

    private val _items = MutableStateFlow<List<ShoppingItemModel>>(emptyList())
    val items: StateFlow<List<ShoppingItemModel>> = _items.asStateFlow()

    private var realtimeListsChannel: RealtimeChannel? = null
    private var realtimeItemsChannel: RealtimeChannel? = null
    private var currentUserId: String? = null

    init {
        viewModelScope.launch {
            repo.currentUserId.collect { userId ->
                currentUserId = userId
                if (userId != null) loadLists(userId) else _lists.value = emptyList()
            }
        }
        viewModelScope.launch {
            repo.familyChanged.collect {
                val userId = repo.currentUserId.first() ?: return@collect
                loadLists(userId)
            }
        }
    }

    /** Re-fetch from the server. Called on screen resume so data stays fresh
     *  even though the ViewModel is Activity-scoped and init{} only runs once. */
    fun refresh() = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        loadLists(userId)
    }

    private suspend fun loadLists(userId: String) {
        if (_lists.value.isEmpty()) _isLoading.value = true
        runCatching {
            val user = repo.getUser(userId)
            val result = if (user?.familyId != null) {
                db.from("shopping_lists")
                    .select { filter { or { eq("owner_user_id", userId); eq("family_id", user.familyId) } } }
                    .decodeList<ShoppingListModel>()
                    .filter { it.familyId == null || it.familyId == user.familyId }
            } else {
                db.from("shopping_lists")
                    .select { filter { eq("owner_user_id", userId) } }
                    .decodeList<ShoppingListModel>()
                    .filter { it.familyId == null }
            }
            cache = result
            _lists.value = result
            if (user?.familyId != null) subscribeToLists(user.familyId, userId)
        }
        _isLoading.value = false
    }

    private suspend fun subscribeToLists(familyId: String, userId: String) {
        realtimeListsChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
        val channel = SupabaseManager.client.channel("shopping-lists-$familyId")
        realtimeListsChannel = channel
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "shopping_lists"
            filter("family_id", FilterOperator.EQ, familyId)
        }
        channel.subscribe()
        viewModelScope.launch { flow.collect { android.util.Log.d("RealtimeProbe", "shopping_lists change event received for family=$familyId"); loadLists(userId) } }
    }

    fun loadListDetail(listId: String) = viewModelScope.launch {
        runCatching {
            coroutineScope {
                val listDeferred = async {
                    db.from("shopping_lists")
                        .select { filter { eq("id", listId) } }
                        .decodeList<ShoppingListModel>()
                        .firstOrNull()
                }
                val itemsDeferred = async {
                    db.from("shopping_items")
                        .select { filter { eq("list_id", listId) } }
                        .decodeList<ShoppingItemModel>()
                }
                _selectedList.value = listDeferred.await()
                _items.value = itemsDeferred.await()
            }
        }
        subscribeToItems(listId)
    }

    private suspend fun subscribeToItems(listId: String) {
        realtimeItemsChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
        val channel = SupabaseManager.client.channel("shopping-items-$listId")
        realtimeItemsChannel = channel
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "shopping_items"
            filter("list_id", FilterOperator.EQ, listId)
        }
        channel.subscribe()
        viewModelScope.launch { flow.collect { loadListDetail(listId) } }
    }

    fun addList(title: String) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        val user = repo.getUser(userId)
        val tempId = "temp-${System.currentTimeMillis()}"
        _lists.value = _lists.value + ShoppingListModel(id = tempId, title = title, ownerUserId = userId, familyId = user?.familyId)
        runCatching {
            db.from("shopping_lists").insert(buildJsonObject {
                put("title", title)
                put("owner_user_id", userId)
                if (user?.familyId != null) put("family_id", user.familyId)
            })
        }
        loadLists(userId)
    }

    fun deleteList(list: ShoppingListModel) = viewModelScope.launch {
        _lists.value = _lists.value.filter { it.id != list.id }
        runCatching { db.from("shopping_lists").delete { filter { eq("id", list.id) } } }
        val userId = repo.currentUserId.first() ?: return@launch
        loadLists(userId)
    }

    fun addItem(listId: String, item: String) = viewModelScope.launch {
        val tempId = "temp-${System.currentTimeMillis()}"
        _items.value = _items.value + ShoppingItemModel(id = tempId, listId = listId, item = item, checked = false)
        runCatching {
            db.from("shopping_items").insert(buildJsonObject {
                put("list_id", listId)
                put("item", item)
            })
        }
        loadListDetail(listId).join()
    }

    fun toggle(item: ShoppingItemModel) = viewModelScope.launch {
        _items.value = _items.value.map { if (it.id == item.id) it.copy(checked = !item.checked) else it }
        runCatching {
            db.from("shopping_items").update({
                set("checked", !item.checked)
            }) { filter { eq("id", item.id) } }
        }
        loadListDetail(item.listId).join()
    }

    fun deleteItem(item: ShoppingItemModel) = viewModelScope.launch {
        _items.value = _items.value.filter { it.id != item.id }
        runCatching { db.from("shopping_items").delete { filter { eq("id", item.id) } } }
        loadListDetail(item.listId).join()
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            realtimeListsChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
            realtimeItemsChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
        }
    }
}
