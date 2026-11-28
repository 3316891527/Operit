package com.ai.assistance.operit.ui.features.management.update.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.GitHubRelease
import com.ai.assistance.operit.data.updates.distribution.DistributionReleaseResolver
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 加载旧版 Operit 与 Operit Kong 的正式更新历史。 */
class UpdateViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Loading)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val apiService = GitHubApiService(context)

    init {
        loadUpdates()
    }

    fun loadUpdates() {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Loading
            try {
                val updates = coroutineScope {
                    // 两个来源各自提供发布记录，任一读取失败都不生成不完整的历史。
                    val upstream = async {
                        apiService.getRepositoryReleases("AAswordman", "Operit", page = 1, perPage = 20)
                            .getOrThrow()
                    }
                    val kong = async { loadKongReleases() }
                    OfficialUpdateHistory.merge(upstream.await(), kong.await()) { tag ->
                        context.getString(R.string.update_version_title, tag)
                    }
                }
                _uiState.value = UpdateUiState.Success(updates)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("UpdateViewModel", "读取正式更新历史失败", error)
                _uiState.value = UpdateUiState.Error(context.getString(R.string.update_unknown_error))
            }
        }
    }

    private suspend fun loadKongReleases(): List<GitHubRelease> = buildList {
        var page = 1
        val pageSize = 100
        // 正式发行与补丁共用一个仓库，不能让较新的补丁把正式发行挤出历史。
        do {
            val releases = apiService.getRepositoryReleases(
                DistributionReleaseResolver.OWNER,
                DistributionReleaseResolver.REPOSITORY,
                page = page++,
                perPage = pageSize
            ).getOrThrow()
            addAll(releases)
        } while (releases.size == pageSize)
    }
}

/** 更新历史界面的状态。 */
sealed class UpdateUiState {
    object Loading : UpdateUiState()
    data class Success(val updates: List<UpdateInfo>) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}
