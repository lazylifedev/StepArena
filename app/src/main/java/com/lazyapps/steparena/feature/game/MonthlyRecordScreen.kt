package com.lazyapps.steparena.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.database.entity.GameSeasonEntity

data class MonthlyRecordUiState(
    val season: GameSeasonEntity? = null,
    val currentRating: Int = 0,
)

@Composable
fun MonthlyRecordScreen(state: MonthlyRecordUiState) = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    item { Text(stringResource(R.string.game_season_title), style = MaterialTheme.typography.headlineMedium) }
    state.season?.let { season ->
        item {
            GameCard(formatMonth(season.id)) {
                Text(stringResource(season.status.displayNameRes()))
                Text(stringResource(R.string.game_rating_change, formatNumber(season.startRating), formatNumber(season.endRating ?: state.currentRating)))
                Text(stringResource(R.string.game_result_counts, season.wins, season.losses, season.draws))
                Text(stringResource(R.string.game_total_eligible_steps, formatNumber(season.totalEligibleSteps)))
                Text(stringResource(R.string.game_best_streak, season.bestWinStreak))
            }
        }
    } ?: item {
        Text(stringResource(R.string.game_season_loading), modifier = Modifier.testTag(GameTestTags.EMPTY))
    }
}
