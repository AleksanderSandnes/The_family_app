package com.sandnes.familyapp.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.ShoppingItemModel
import com.sandnes.familyapp.data.ShoppingListModel
import com.sandnes.familyapp.data.remote.SupabaseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
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
import javax.inject.Inject

/** Per-list shopping progress: items bought out of total. */
data class ListProgress(
    val bought: Int,
    val total: Int,
)

@HiltViewModel
class ShoppingViewModel
    @Inject
    constructor(
        internal val repo: FamilyRepository,
    ) : ViewModel() {
        companion object {
            private var cache: List<ShoppingListModel> = emptyList()
        }

        private val db get() = SupabaseManager.client.postgrest

        private val _lists = MutableStateFlow(cache)
        val lists: StateFlow<List<ShoppingListModel>> = _lists.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _selectedList = MutableStateFlow<ShoppingListModel?>(null)
        val selectedList: StateFlow<ShoppingListModel?> = _selectedList.asStateFlow()

        private val _items = MutableStateFlow<List<ShoppingItemModel>>(emptyList())
        val items: StateFlow<List<ShoppingItemModel>> = _items.asStateFlow()

        /** Map of listId to bought/total progress, shown on list cards. */
        private val _listProgress = MutableStateFlow<Map<String, ListProgress>>(emptyMap())
        val listProgress: StateFlow<Map<String, ListProgress>> = _listProgress.asStateFlow()

        /** One-shot, user-visible error as a string resource id. Cleared via [clearError]. */
        private val _errorRes = MutableStateFlow<Int?>(null)
        val errorRes: StateFlow<Int?> = _errorRes.asStateFlow()

        fun clearError() {
            _errorRes.value = null
        }

        /** Last successfully deleted item, offered for snackbar undo. Cleared via [clearUndo]. */
        private val _undoItem = MutableStateFlow<ShoppingItemModel?>(null)
        val undoItem: StateFlow<ShoppingItemModel?> = _undoItem.asStateFlow()

        fun clearUndo() {
            _undoItem.value = null
        }

        private var realtimeListsChannel: RealtimeChannel? = null
        private var realtimeItemsChannel: RealtimeChannel? = null
        private var currentUserId: String? = null

        /** The familyId the lists channel is currently subscribed for -- guards against re-subscribe. */
        private var subscribedListsFamilyId: String? = null

        /** The listId the items channel is currently subscribed for -- guards against re-subscribe. */
        private var subscribedItemsListId: String? = null

        init {
            viewModelScope.launch {
                repo.currentUserId.collect { userId ->
                    currentUserId = userId
                    if (userId != null) {
                        loadLists(userId)
                    } else {
                        _lists.value = emptyList()
                        _listProgress.value = emptyMap()
                    }
                }
            }
            viewModelScope.launch {
                repo.familyChanged.collect {
                    val userId = repo.currentUserId.first() ?: return@collect
                    reloadLists(userId)
                }
            }
            viewModelScope.launch {
                var seenDisconnect = false
                runCatching { SupabaseManager.client.realtime.status }.getOrNull()?.collect { status ->
                    when (status) {
                        Realtime.Status.DISCONNECTED -> seenDisconnect = true
                        Realtime.Status.CONNECTED ->
                            if (seenDisconnect) {
                                seenDisconnect = false
                                val userId = repo.currentUserId.first() ?: return@collect
                                reloadLists(userId)
                                subscribedItemsListId?.let { reloadItems(it) }
                            }
                        else -> {}
                    }
                }
            }
        }

        /** Re-fetch from the server (called on screen resume). Does NOT re-subscribe. */
        fun refresh() =
            viewModelScope.launch {
                val userId = repo.currentUserId.first() ?: return@launch
                reloadLists(userId)
            }

        /**
         * Initial load: fetches data then sets up realtime subscription.
         * Only called when userId first resolves from DataStore.
         */
        private suspend fun loadLists(userId: String) {
            if (_lists.value.isEmpty()) _isLoading.value = true
            runCatching {
                val user = repo.getUser(userId)
                reloadListsInternal(userId, user?.familyId)
                if (user?.familyId != null) subscribeToListsOnce(user.familyId, userId)
            }
            _isLoading.value = false
        }

        /**
         * Pure data reload -- no subscribe. Used by the realtime collector, familyChanged,
         * refresh(), and all list mutations so they never trigger re-subscription.
         */
        private suspend fun reloadLists(userId: String) {
            runCatching {
                val user = repo.getUser(userId)
                reloadListsInternal(userId, user?.familyId)
                // Also subscribe if the user just joined/created a family.
                if (user?.familyId != null) subscribeToListsOnce(user.familyId, userId)
            }
        }

        private suspend fun reloadListsInternal(
            userId: String,
            familyId: String?,
        ) {
            val result =
                if (familyId != null) {
                    db
                        .from("shopping_lists")
                        .select {
                            filter {
                                or {
                                    eq("owner_user_id", userId)
                                    eq("family_id", familyId)
                                }
                            }
                        }.decodeList<ShoppingListModel>()
                        .filter { it.familyId == null || it.familyId == familyId }
                } else {
                    db
                        .from("shopping_lists")
                        .select { filter { eq("owner_user_id", userId) } }
                        .decodeList<ShoppingListModel>()
                        .filter { it.familyId == null }
                }
            cache = result
            _lists.value = result
            loadListProgress(result.map { it.id })
        }

        /** Loads bought/total progress per list in one query and updates [_listProgress]. */
        private suspend fun loadListProgress(listIds: List<String>) {
            if (listIds.isEmpty()) {
                _listProgress.value = emptyMap()
                return
            }
            runCatching {
                val allItems =
                    db
                        .from("shopping_items")
                        .select {
                            filter { isIn("list_id", listIds) }
                        }.decodeList<ShoppingItemModel>()
                _listProgress.value =
                    allItems.groupBy { it.listId }.mapValues { (_, items) ->
                        ListProgress(bought = items.count { it.checked }, total = items.size)
                    }
            }
        }

        /** Subscribe to the lists channel at most once per familyId. */
        private suspend fun subscribeToListsOnce(
            familyId: String,
            userId: String,
        ) {
            if (subscribedListsFamilyId == familyId) return
            realtimeListsChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
            subscribedListsFamilyId = familyId
            val channel = runCatching { SupabaseManager.client.channel("shopping-lists-$familyId") }.getOrNull() ?: return
            realtimeListsChannel = channel
            val flow =
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "shopping_lists"
                    filter("family_id", FilterOperator.EQ, familyId)
                }
            channel.subscribe()
            // Collector calls reloadLists (data only) -- never re-subscribes.
            viewModelScope.launch { flow.collect { reloadLists(userId) } }
        }

        fun loadListDetail(listId: String) =
            viewModelScope.launch {
                runCatching {
                    coroutineScope {
                        val listDeferred =
                            async {
                                db
                                    .from("shopping_lists")
                                    .select { filter { eq("id", listId) } }
                                    .decodeList<ShoppingListModel>()
                                    .firstOrNull()
                            }
                        val itemsDeferred =
                            async {
                                db
                                    .from("shopping_items")
                                    .select { filter { eq("list_id", listId) } }
                                    .decodeList<ShoppingItemModel>()
                            }
                        _selectedList.value = listDeferred.await()
                        _items.value = itemsDeferred.await()
                    }
                }
                // Only subscribes when navigating to a new list; guard prevents re-subscribe on reload.
                subscribeToItemsOnce(listId)
            }

        /**
         * Pure data reload for items -- no subscribe. Used by the realtime collector and all item
         * mutations so they never trigger re-subscription.
         */
        private suspend fun reloadItems(listId: String) {
            runCatching {
                coroutineScope {
                    val listDeferred =
                        async {
                            db
                                .from("shopping_lists")
                                .select { filter { eq("id", listId) } }
                                .decodeList<ShoppingListModel>()
                                .firstOrNull()
                        }
                    val itemsDeferred =
                        async {
                            db
                                .from("shopping_items")
                                .select { filter { eq("list_id", listId) } }
                                .decodeList<ShoppingItemModel>()
                        }
                    _selectedList.value = listDeferred.await()
                    _items.value = itemsDeferred.await()
                }
            }
        }

        /** Subscribe to the items channel at most once per listId. */
        private suspend fun subscribeToItemsOnce(listId: String) {
            if (subscribedItemsListId == listId) return
            realtimeItemsChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
            subscribedItemsListId = listId
            val channel = runCatching { SupabaseManager.client.channel("shopping-items-$listId") }.getOrNull() ?: return
            realtimeItemsChannel = channel
            val flow =
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "shopping_items"
                    filter("list_id", FilterOperator.EQ, listId)
                }
            channel.subscribe()
            // Collector calls reloadItems (data only) -- never re-subscribes.
            viewModelScope.launch { flow.collect { reloadItems(listId) } }
        }

        fun addList(
            title: String,
            icon: String = "shopping_cart",
            color: Int? = null,
        ) = viewModelScope.launch {
            val userId = repo.currentUserId.first() ?: return@launch
            val user = repo.getUser(userId)
            val tempId = "temp-${java.util.UUID.randomUUID()}"
            _lists.value = _lists.value +
                ShoppingListModel(id = tempId, title = title, ownerUserId = userId, familyId = user?.familyId, icon = icon, color = color)
            runCatching {
                db.from("shopping_lists").insert(
                    buildJsonObject {
                        put("title", title)
                        put("icon", icon)
                        put("owner_user_id", userId)
                        if (user?.familyId != null) put("family_id", user.familyId)
                        if (color != null) put("color", color)
                    },
                )
            }.onFailure { _errorRes.value = R.string.couldnt_save }
            reloadLists(userId)
        }

        fun changeListColor(
            listId: String,
            color: Int?,
        ) = viewModelScope.launch {
            _lists.value = _lists.value.map { if (it.id == listId) it.copy(color = color) else it }
            _selectedList.value = _selectedList.value?.copy(color = color)
            runCatching {
                db.from("shopping_lists").update({ set("color", color) }) { filter { eq("id", listId) } }
            }
            val userId = currentUserId ?: repo.currentUserId.first() ?: return@launch
            reloadLists(userId)
        }

        fun changeListIcon(
            listId: String,
            icon: String,
        ) = viewModelScope.launch {
            _lists.value = _lists.value.map { if (it.id == listId) it.copy(icon = icon) else it }
            _selectedList.value = _selectedList.value?.copy(icon = icon)
            runCatching {
                db.from("shopping_lists").update({ set("icon", icon) }) { filter { eq("id", listId) } }
            }
            val userId = currentUserId ?: repo.currentUserId.first() ?: return@launch
            reloadLists(userId)
        }

        fun deleteList(list: ShoppingListModel) =
            viewModelScope.launch {
                _lists.value = _lists.value.filter { it.id != list.id }
                runCatching { db.from("shopping_lists").delete { filter { eq("id", list.id) } } }
                    .onFailure { _errorRes.value = R.string.couldnt_delete }
                val userId = repo.currentUserId.first() ?: return@launch
                reloadLists(userId)
            }

        fun addItem(
            listId: String,
            item: String,
        ) =
            viewModelScope.launch {
                val tempId = "temp-${java.util.UUID.randomUUID()}"
                _items.value = _items.value + ShoppingItemModel(id = tempId, listId = listId, item = item, checked = false)
                runCatching {
                    db.from("shopping_items").insert(
                        buildJsonObject {
                            put("list_id", listId)
                            put("item", item)
                        },
                    )
                }.onFailure { _errorRes.value = R.string.couldnt_save }
                reloadItems(listId)
            }

        fun toggle(item: ShoppingItemModel) =
            viewModelScope.launch {
                _items.value = _items.value.map { if (it.id == item.id) it.copy(checked = !item.checked) else it }
                runCatching {
                    db.from("shopping_items").update({
                        set("checked", !item.checked)
                    }) { filter { eq("id", item.id) } }
                }.onFailure { _errorRes.value = R.string.couldnt_save }
                reloadItems(item.listId)
            }

        fun deleteItem(item: ShoppingItemModel) =
            viewModelScope.launch {
                _items.value = _items.value.filter { it.id != item.id }
                runCatching { db.from("shopping_items").delete { filter { eq("id", item.id) } } }
                    .onSuccess { _undoItem.value = item }
                    .onFailure { _errorRes.value = R.string.couldnt_delete }
                reloadItems(item.listId)
            }

        /** Re-inserts a deleted item (snackbar undo). The row gets a fresh id; content is kept. */
        fun restoreItem(item: ShoppingItemModel) =
            viewModelScope.launch {
                runCatching {
                    db.from("shopping_items").insert(
                        buildJsonObject {
                            put("list_id", item.listId)
                            put("item", item.item)
                            put("checked", item.checked)
                        },
                    )
                }.onFailure { _errorRes.value = R.string.couldnt_save }
                reloadItems(item.listId)
            }

        fun renameItem(
            item: ShoppingItemModel,
            newName: String,
        ) = viewModelScope.launch {
            _items.value = _items.value.map { if (it.id == item.id) it.copy(item = newName) else it }
            runCatching {
                db.from("shopping_items").update({ set("item", newName) }) { filter { eq("id", item.id) } }
            }.onFailure { _errorRes.value = R.string.couldnt_save }
            reloadItems(item.listId)
        }

        fun renameList(
            listId: String,
            newTitle: String,
        ) = viewModelScope.launch {
            _selectedList.value = _selectedList.value?.copy(title = newTitle)
            runCatching {
                db.from("shopping_lists").update({ set("title", newTitle) }) { filter { eq("id", listId) } }
            }.onFailure { _errorRes.value = R.string.couldnt_save }
            reloadItems(listId)
        }

        /** Deletes all checked items from the given list. */
        fun clearCompleted(listId: String) =
            viewModelScope.launch {
                val completed = _items.value.filter { it.checked }
                if (completed.isEmpty()) return@launch
                _items.value = _items.value.filter { !it.checked }
                runCatching {
                    db.from("shopping_items").delete {
                        filter {
                            eq("list_id", listId)
                            eq("checked", true)
                        }
                    }
                }.onFailure { _errorRes.value = R.string.couldnt_delete }
                reloadItems(listId)
            }

        override fun onCleared() {
            viewModelScope.launch {
                realtimeListsChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
                realtimeItemsChannel?.let { runCatching { SupabaseManager.client.realtime.removeChannel(it) } }
            }
        }
    }
