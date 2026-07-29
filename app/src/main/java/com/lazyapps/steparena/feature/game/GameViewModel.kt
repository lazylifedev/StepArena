package com.lazyapps.steparena.feature.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.core.database.entity.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GameUiState(
    val profile: GamePlayerProfileEntity? = null,
    val todayMatch: DailyMatchEntity? = null,
    val recentMatches: List<DailyMatchEntity> = emptyList(),
    val league: WeeklyLeagueEntity? = null,
    val season: GameSeasonEntity? = null,
    val achievements: List<AchievementUnlockEntity> = emptyList(),
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as StepArenaApplication).gameRepository
    val state: StateFlow<GameUiState> = combine(
        repository.observePlayerProfile(),
        repository.observeTodayMatch(),
        repository.observeRecentMatches(30),
        repository.observeCurrentLeague(),
        repository.observeCurrentSeason(),
        repository.observeAchievements(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        GameUiState(
            values[0] as GamePlayerProfileEntity,
            values[1] as DailyMatchEntity?,
            values[2] as List<DailyMatchEntity>,
            values[3] as WeeklyLeagueEntity?,
            values[4] as GameSeasonEntity?,
            values[5] as List<AchievementUnlockEntity>,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameUiState())

    init {
        viewModelScope.launch {
            repository.finalizePendingMatches()
            repository.ensureTodayMatch()
            repository.evaluateAchievements()
        }
    }
}
