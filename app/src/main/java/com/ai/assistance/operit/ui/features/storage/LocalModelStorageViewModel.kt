package com.ai.assistance.operit.ui.features.storage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.mnn.MnnModelDownloadManager
import com.ai.assistance.operit.data.storage.LocalModelDeleteOutcome
import com.ai.assistance.operit.data.storage.LocalModelEntry
import com.ai.assistance.operit.data.storage.LocalModelInventory
import com.ai.assistance.operit.data.storage.LocalModelKind
import com.ai.assistance.operit.data.storage.LocalModelRuntimeRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LocalModelFilter {
    ALL,
    MNN,
    LLAMA,
    SPEECH,
}

data class LocalModelStorageUiState(
    val models: List<LocalModelEntry> = emptyList(),
    val filter: LocalModelFilter = LocalModelFilter.ALL,
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val deleteOutcome: LocalModelDeleteOutcome? = null,
) {
    val displayedModels: List<LocalModelEntry>
        get() = when (filter) {
            LocalModelFilter.ALL -> models
            LocalModelFilter.MNN -> models.filter { it.kind == LocalModelKind.MNN }
            LocalModelFilter.LLAMA -> models.filter { it.kind == LocalModelKind.LLAMA }
            LocalModelFilter.SPEECH -> models.filter { it.kind == LocalModelKind.SPEECH }
        }

    val totalBytes: Long
        get() = displayedModels.sumOf { it.bytes }
}

class LocalModelStorageViewModel(
    private val inventory: LocalModelInventory,
    private val downloadManager: MnnModelDownloadManager,
) : ViewModel() {
    private val _state = MutableStateFlow(LocalModelStorageUiState(isLoading = true))
    val state: StateFlow<LocalModelStorageUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        observeRuntimeUsage()
        refresh()
    }

    fun setFilter(filter: LocalModelFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    val models = inventory.listModels()
                    _state.update {
                        it.copy(models = models, isLoading = false, errorMessage = null)
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage?.takeIf { message ->
                                message.isNotBlank()
                            } ?: error.javaClass.simpleName,
                        )
                    }
                }
            }
    }

    fun delete(entry: LocalModelEntry) {
        if (_state.value.isDeleting || !entry.canDelete) return
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, errorMessage = null, deleteOutcome = null) }
            try {
                val outcome = inventory.delete(entry)
                val models = inventory.listModels()
                _state.update {
                    it.copy(
                        models = models,
                        isDeleting = false,
                        deleteOutcome = outcome,
                        errorMessage = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = error.localizedMessage?.takeIf { message ->
                            message.isNotBlank()
                        } ?: error.javaClass.simpleName,
                    )
                }
            }
        }
    }

    fun consumeDeleteOutcome() {
        _state.update { it.copy(deleteOutcome = null) }
    }

    private fun observeRuntimeUsage() {
        viewModelScope.launch {
            LocalModelRuntimeRegistry.activePaths.collect {
                if (!_state.value.isDeleting) {
                    refresh()
                }
            }
        }
        viewModelScope.launch {
            downloadManager.downloadSnapshots
                .debounce(400)
                .collect {
                    if (!_state.value.isDeleting) {
                        refresh()
                    }
                }
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LocalModelStorageViewModel::class.java)) {
                val downloadManager = MnnModelDownloadManager.getInstance(appContext)
                return LocalModelStorageViewModel(
                    LocalModelInventory(appContext, downloadManager),
                    downloadManager,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
