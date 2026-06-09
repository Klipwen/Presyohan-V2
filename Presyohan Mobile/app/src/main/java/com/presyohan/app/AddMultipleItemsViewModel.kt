package com.presyohan.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AddMultipleItemsViewModel(application: Application) : AndroidViewModel(application) {

    private val draftStore = ImportDraftStore(application)

    private val _draftSession = MutableLiveData<DraftImportSession?>()
    val draftSession: LiveData<DraftImportSession?> get() = _draftSession

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _existingProductNames = MutableLiveData<Set<String>>(emptySet())
    val existingProductNames: LiveData<Set<String>> get() = _existingProductNames

    private val _categoryIdByName = MutableLiveData<Map<String, String>>(emptyMap())
    val categoryIdByName: LiveData<Map<String, String>> get() = _categoryIdByName

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    fun loadOrCreateSession(storeId: String, storeName: String?, sessionId: String? = null) {
        _isLoading.value = true
        viewModelScope.launch {
            val session = if (sessionId != null) {
                withContext(Dispatchers.IO) {
                    draftStore.loadSession(sessionId)
                } ?: withContext(Dispatchers.IO) {
                    draftStore.createSession(storeId, storeName)
                }
            } else {
                withContext(Dispatchers.IO) {
                    draftStore.createSession(storeId, storeName)
                }
            }
            _draftSession.value = session
            fetchExistingData(storeId)
        }
    }

    fun updateSession(updatedSession: DraftImportSession) {
        _draftSession.value = updatedSession
        viewModelScope.launch(Dispatchers.IO) {
            draftStore.saveSession(updatedSession)
        }
    }

    fun deleteSession() {
        val session = _draftSession.value ?: return
        _draftSession.value = null
        viewModelScope.launch(Dispatchers.IO) {
            draftStore.deleteSession(session.sessionId)
        }
    }

    private fun fetchExistingData(storeId: String) {
        viewModelScope.launch {
            try {
                // Fetch Categories
                @Serializable data class CatRow(val category_id: String, val name: String)
                val cats = withContext(Dispatchers.IO) {
                    SupabaseProvider.client.postgrest.rpc(
                        "get_user_categories",
                        buildJsonObject { put("p_store_id", JsonPrimitive(storeId)) }
                    ).decodeList<CatRow>()
                }
                val catMap = cats.associate { it.name to it.category_id }
                _categoryIdByName.value = catMap

                // Fetch Products for Update Detection
                @Serializable data class ProdRow(val name: String)
                val prods = withContext(Dispatchers.IO) {
                    SupabaseProvider.client.postgrest["products"]
                        .select(Columns.list("name")) { filter { eq("store_id", storeId) } }
                        .decodeList<ProdRow>()
                }
                val prodSet = prods.map { it.name.lowercase() }.toSet()
                _existingProductNames.value = prodSet

            } catch (e: Exception) {
                _error.value = "Failed to load store data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
