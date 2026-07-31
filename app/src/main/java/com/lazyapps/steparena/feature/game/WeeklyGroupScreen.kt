package com.lazyapps.steparena.feature.game

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.database.entity.WeeklyLeagueEntity
import com.lazyapps.steparena.core.database.entity.WeeklyLeagueParticipantEntity
import com.lazyapps.steparena.game.LeagueStatus

data class WeeklyGroupUiState(
    val league: WeeklyLeagueEntity? = null,
    val participants: List<WeeklyLeagueParticipantEntity> = emptyList(),
)

@Composable
fun WeeklyGroupScreen(state: WeeklyGroupUiState) = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
) {
    item {
        Text(stringResource(R.string.game_league_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.game_league_explanation))
    }
    state.league?.let { league ->
        item {
            Text(stringResource(R.string.game_date_range, formatDate(league.weekStartLocalDate), formatDate(league.weekEndLocalDate)))
            Text(stringResource(R.string.game_league_rank, league.userRank ?: "-", 10))
            Text(stringResource(R.string.game_weekly_points, formatNumber(league.userPoints)))
            if (league.status == LeagueStatus.FINALIZED) Text(stringResource(R.string.game_league_finalized))
        }
        items(state.participants, key = { it.participantId }) { participant ->
            ListItem(
                headlineContent = {
                    Text(
                        participant.displayName,
                        fontWeight = if (participant.isLocalPlayer) FontWeight.Bold else null,
                    )
                },
                overlineContent = { Text(participant.rank.toString()) },
                trailingContent = {
                    Text(stringResource(R.string.game_points_value, formatNumber(participant.points)))
                },
            )
        }
    } ?: item {
        Text(stringResource(R.string.game_league_loading), modifier = Modifier.testTag(GameTestTags.EMPTY))
    }
}
