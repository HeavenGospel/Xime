package com.kingzcheung.xime.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kingzcheung.xime.settings.DictEntry
import com.kingzcheung.xime.settings.PersonalDictManager
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SchemaMeta
import com.kingzcheung.xime.settings.UserDictImporter
import com.kingzcheung.xime.settings.UserDictManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class PersonalDictUiState(
    val selectedSchema: String = "pinyin_simp",
    val availableSchemas: List<SchemaMeta> = emptyList(),
    val entries: List<DictEntry> = emptyList(),
    val filteredEntries: List<DictEntry> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    /** true = 当前列表主要来自 *.userdb.txt（自造词），而非 packs 表。 */
    val fromUserDb: Boolean = false,
    /** 当前方案是否有可导出的 *.userdb.txt。 */
    val hasExportableUserDb: Boolean = false,
)

class PersonalDictViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(PersonalDictUiState())
    val uiState: StateFlow<PersonalDictUiState> = _uiState.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    init {
        loadSchemas()
    }

    private fun loadSchemas() {
        viewModelScope.launch {
            val (schemas, preferred) = withContext(Dispatchers.IO) {
                val discovered = SchemaManager.discoverSchemas(context)
                val syncedIds = UserDictImporter.listSyncedUserDbSchemaIds(context)
                val merged = discovered.toMutableList()
                for (id in syncedIds) {
                    if (merged.none { it.schemaId == id }) {
                        merged += SchemaMeta(
                            schemaId = id,
                            name = when (id) {
                                "rime_mint" -> "薄荷拼音（自造词）"
                                "melt_eng" -> "英文（自造词）"
                                else -> "$id（自造词）"
                            },
                        )
                    }
                }
                val pref = pickPreferredSchema(merged, preferredIds = syncedIds)
                merged to pref
            }
            _uiState.update {
                it.copy(
                    availableSchemas = schemas,
                    selectedSchema = preferred ?: it.selectedSchema,
                )
            }
            loadEntries()
        }
    }

    fun selectSchema(schemaId: String) {
        _uiState.update { it.copy(selectedSchema = schemaId, searchQuery = "") }
        loadEntries()
    }

    private fun loadEntries() {
        val schemaId = _uiState.value.selectedSchema
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val (entries, fromUserDb) = withContext(Dispatchers.IO) {
                loadEntriesForSchema(schemaId)
            }
            if (entries.isEmpty()) {
                val fallbackId = withContext(Dispatchers.IO) {
                    pickPreferredSchema(
                        _uiState.value.availableSchemas,
                        preferredIds = UserDictImporter.listSyncedUserDbSchemaIds(context),
                    )
                }
                if (fallbackId != null && fallbackId != schemaId) {
                    val (fbEntries, fbFromUserDb) = withContext(Dispatchers.IO) {
                        loadEntriesForSchema(fallbackId)
                    }
                    if (fbEntries.isNotEmpty()) {
                        val canExport = withContext(Dispatchers.IO) {
                            UserDictManager.hasExportableUserDb(context, fallbackId)
                        }
                        _uiState.update {
                            it.copy(
                                selectedSchema = fallbackId,
                                entries = fbEntries,
                                filteredEntries = filterEntries(fbEntries, ""),
                                searchQuery = "",
                                isLoading = false,
                                fromUserDb = fbFromUserDb,
                                hasExportableUserDb = canExport,
                            )
                        }
                        return@launch
                    }
                }
            }
            val canExport = withContext(Dispatchers.IO) {
                UserDictManager.hasExportableUserDb(context, schemaId)
            }
            _uiState.update {
                it.copy(
                    entries = entries,
                    filteredEntries = filterEntries(entries, ""),
                    searchQuery = "",
                    isLoading = false,
                    fromUserDb = fromUserDb && entries.isNotEmpty(),
                    hasExportableUserDb = canExport,
                )
            }
        }
    }

    private suspend fun loadEntriesForSchema(schemaId: String): Pair<List<DictEntry>, Boolean> {
        PersonalDictManager.ensureSchemaPack(context, schemaId)
        val userDb = UserDictImporter.loadSyncedUserDbEntries(context, schemaId)
        if (userDb.isNotEmpty()) return userDb to true
        val pack = PersonalDictManager.loadEntries(context, schemaId)
        return pack to false
    }

    private fun pickPreferredSchema(
        schemas: List<SchemaMeta>,
        preferredIds: List<String>,
    ): String? {
        if (schemas.isEmpty()) return preferredIds.firstOrNull()
        val ids = schemas.map { it.schemaId }.toSet()
        preferredIds.firstOrNull { it in ids }?.let { return it }
        val synced = UserDictImporter.listSyncedUserDbSchemaIds(context)
        synced.firstOrNull { it in ids }?.let { return it }
        schemas.firstOrNull { schema ->
            UserDictImporter.loadSyncedUserDbEntries(context, schema.schemaId, limit = 1).isNotEmpty() ||
                PersonalDictManager.loadEntries(context, schema.schemaId).isNotEmpty()
        }?.schemaId?.let { return it }
        schemas.firstOrNull { it.schemaId == "rime_mint" }?.schemaId?.let { return it }
        return schemas.firstOrNull()?.schemaId
    }

    fun importUserDict(uri: Uri) {
        if (_uiState.value.isImporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val result = withContext(Dispatchers.IO) {
                UserDictImporter.importUri(context, uri)
            }
            _uiState.update { it.copy(isImporting = false) }
            _toast.tryEmit(
                if (result.success) result.message
                else "导入失败：${result.message}"
            )
            if (result.success) {
                val imported = result.schemaIds
                val packSchemas = result.packNames.mapNotNull { pack ->
                    when (pack) {
                        "user_simp" -> "pinyin_simp"
                        else -> null
                    }
                }
                if (imported.isNotEmpty() || packSchemas.isNotEmpty()) {
                    _uiState.update { st ->
                        val extras = imported.filter { id -> st.availableSchemas.none { it.schemaId == id } }
                            .map { id ->
                                SchemaMeta(
                                    schemaId = id,
                                    name = when (id) {
                                        "rime_mint" -> "薄荷拼音（自造词）"
                                        "pinyin_simp" -> "简体拼音（自造词）"
                                        else -> "$id（自造词）"
                                    },
                                )
                            }
                        val target = imported.firstOrNull()
                            ?: packSchemas.firstOrNull()
                            ?: st.selectedSchema
                        st.copy(
                            availableSchemas = st.availableSchemas + extras,
                            selectedSchema = target,
                            searchQuery = "",
                        )
                    }
                }
                loadEntries()
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                filteredEntries = filterEntries(it.entries, query)
            )
        }
    }

    fun clearSearch() {
        setSearchQuery("")
    }

    fun exportUserDict() {
        if (_uiState.value.isExporting) return
        val schemaId = _uiState.value.selectedSchema
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val result = withContext(Dispatchers.IO) {
                UserDictManager.exportSchemaUserDb(context, schemaId)
            }
            _uiState.update { it.copy(isExporting = false) }
            result.fold(
                onSuccess = { export ->
                    _toast.tryEmit("已导出到 Downloads/${export.fileName}")
                },
                onFailure = { e ->
                    _toast.tryEmit("导出失败：${e.message ?: "未知错误"}")
                },
            )
        }
    }

    private fun filterEntries(entries: List<DictEntry>, query: String): List<DictEntry> {
        if (query.isEmpty()) return entries
        val lowerQuery = query.lowercase(Locale.ROOT)
        return entries.filter {
            it.word.contains(query) ||
                it.code.contains(query, ignoreCase = true) ||
                it.code.lowercase(Locale.ROOT).contains(lowerQuery)
        }
    }
}
