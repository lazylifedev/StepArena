package com.lazyapps.steparena.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.database.entity.GamePlayerProfileEntity
import com.lazyapps.steparena.core.designsystem.component.RankBadge
import com.lazyapps.steparena.core.designsystem.component.RankProgressBar
import com.lazyapps.steparena.game.RankSystem

data class RankUiState(val profile: GamePlayerProfileEntity? = null)

@Composable
fun RankScreen(state: RankUiState) = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    item { Text(stringResource(R.string.game_rank_title), style = MaterialTheme.typography.headlineMedium) }
    state.profile?.let { profile ->
        item {
            val rank = RankSystem.definition(profile.rating)
            GameCard(rank.displayName) {
                RankBadge(rank.displayName)
                Text(
                    stringResource(R.string.game_rating_value, formatNumber(profile.rating)),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(stringResource(R.string.game_result_counts, profile.wins, profile.losses, profile.draws))
                Text(stringResource(R.string.game_streak_counts, profile.currentWinStreak, profile.bestWinStreak))
                val next = nextRankProgress(profile.rating)
                if (next == null) {
                    Text(stringResource(R.string.game_highest_rank))
                } else {
                    Text(stringResource(R.string.game_next_rank, next.rank.displayName))
                    Text(stringResource(R.string.game_next_rank_remaining, formatNumber(next.remaining)))
                    RankProgressBar(
                        progress = next.progress,
                        description = stringResource(
                            R.string.game_rank_progress_description,
                            (next.progress * 100).toInt(),
                        ),
                    )
                }
                if (profile.beginnerMatchesRemaining > 0) {
                    Text(stringResource(R.string.game_beginner_support, profile.beginnerMatchesRemaining))
                }
            }
        }
    } ?: item { CircularProgressIndicator() }
}
