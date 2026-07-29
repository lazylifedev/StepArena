package com.lazyapps.steparena.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.steparena.core.designsystem.motion.InMemoryMotionSettingsRepository
import com.lazyapps.steparena.core.designsystem.motion.MotionSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val homeRepository: HomeRepository = DemoHomeRepository(),
    private val motionRepository: MotionSettingsRepository = InMemoryMotionSettingsRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.StartSession -> _uiState.update {
                it.copy(sessionState = SessionState.STARTED)
            }
            HomeAction.Retry -> load()
            is HomeAction.SetMotion -> viewModelScope.launch {
                motionRepository.save(action.level)
                _uiState.update { it.copy(motionLevel = action.level) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(content = HomeContent.Loading) }
            _uiState.update { current ->
                runCatching { homeRepository.loadHome() }
                    .fold(
                        onSuccess = { snapshot ->
                            current.copy(
                                content = snapshot?.let(HomeContent::Ready) ?: HomeContent.Empty,
                                motionLevel = motionRepository.read(),
                            )
                        },
                        onFailure = { current.copy(content = HomeContent.Failure()) },
                    )
            }
        }
    }
}
