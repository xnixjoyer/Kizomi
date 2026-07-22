package com.anisync.android.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anisync.android.data.AppSettings
import com.anisync.android.data.CommunityScoreMode
import com.anisync.android.domain.CommunityScoreCacheStats
import com.anisync.android.domain.CommunityScoreRepository
import com.anisync.android.domain.CommunityScoreRuntimeStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommunityScoreSettingsUiState(
    val mode: CommunityScoreMode = CommunityScoreMode.ANILIST,
    val cache: CommunityScoreCacheStats = CommunityScoreCacheStats(),
    val runtime: CommunityScoreRuntimeStats = CommunityScoreRuntimeStats(),
    val clearing: Boolean = false
)

@HiltViewModel
class CommunityScoreSettingsViewModel @Inject constructor(
    private val appSettings: AppSettings,
    private val repository: CommunityScoreRepository
) : ViewModel() {
    private val cacheStats = MutableStateFlow(CommunityScoreCacheStats())
    private val clearing = MutableStateFlow(false)

    val uiState = combine(
        appSettings.communityScoreMode,
        cacheStats,
        repository.runtimeStats,
        clearing
    ) { mode, cache, runtime, isClearing ->
        CommunityScoreSettingsUiState(mode, cache, runtime, isClearing)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CommunityScoreSettingsUiState()
    )

    init {
        refreshCacheStats()
    }

    fun setMode(mode: CommunityScoreMode) = appSettings.setCommunityScoreMode(mode)

    fun clearCache() {
        if (clearing.value) return
        viewModelScope.launch {
            clearing.value = true
            try {
                repository.clearCache()
                cacheStats.value = repository.cacheStats()
            } finally {
                clearing.value = false
            }
        }
    }

    fun refreshCacheStats() {
        viewModelScope.launch { cacheStats.value = repository.cacheStats() }
    }
}
