package com.lazyapps.steparena.feature.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.database.entity.GamePlayerProfileEntity
import com.lazyapps.steparena.core.designsystem.component.RankBadge
import com.lazyapps.steparena.core.designsystem.component.RankProgressBar
import com.lazyapps.steparena.game.RankSystem
import com.lazyapps.steparena.game.publicDisplayName

data class RankUiState(val profile: GamePlayerProfileEntity? = null, val displayName: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankScreen(state: RankUiState) {
    var details by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.game_rank_title), style = MaterialTheme.typography.headlineMedium) }
        state.profile?.let { profile ->
            item {
                val rank = RankSystem.definition(profile.rating)
                GameCard(publicDisplayName(state.displayName, stringResource(R.string.game_you))) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RankBadge(rank.displayName)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { details = true }) {
                            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.game_rank_details_action))
                        }
                    }
                    Text(rank.displayName, style = MaterialTheme.typography.headlineSmall)
                    val next = nextRankProgress(profile.rating)
                    if (next == null) Text(stringResource(R.string.game_highest_rank)) else {
                        RankProgressBar(
                            progress = next.progress,
                            description = stringResource(R.string.game_rank_progress_description, (next.progress * 100).toInt()),
                        )
                        Text(stringResource(R.string.game_next_rank_remaining, formatNumber(next.remaining)))
                    }
                }
            }
        } ?: item { CircularProgressIndicator() }
    }
    val profile = state.profile
    if (details && profile != null) ModalBottomSheet(onDismissRequest = { details = false }) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.game_rank_details_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.game_rating_value, formatNumber(profile.rating)))
            Text(stringResource(R.string.game_result_counts, profile.wins, profile.losses, profile.draws))
            Text(stringResource(R.string.game_streak_counts, profile.currentWinStreak, profile.bestWinStreak))
            if (profile.beginnerMatchesRemaining > 0) Text(stringResource(R.string.game_beginner_support, profile.beginnerMatchesRemaining))
        }
    }
}
