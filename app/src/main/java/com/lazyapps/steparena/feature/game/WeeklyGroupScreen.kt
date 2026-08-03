package com.lazyapps.steparena.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

object WeeklyGroupTestTags { const val INFO_SHEET = "weekly_group_info_sheet" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyGroupScreen(state: WeeklyGroupUiState) {
    var showInfo by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.game_league_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Default.Info, contentDescription = stringResource(R.string.game_league_info_action))
                }
            }
        }
        state.league?.let { league ->
            item {
                GameCard(stringResource(R.string.game_date_range, formatDate(league.weekStartLocalDate), formatDate(league.weekEndLocalDate))) {
                    Text(
                        stringResource(R.string.game_league_rank, league.userRank ?: "-", state.participants.size),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.game_weekly_points, formatNumber(league.userPoints)))
                    if (league.status == LeagueStatus.FINALIZED) Text(stringResource(R.string.game_league_finalized))
                }
            }
            items(visibleLeagueParticipants(state.participants), key = { it.participantId }) { participant ->
                val localColor = Color(0xFF58E6FF).copy(alpha = .16f)
                ListItem(
                    modifier = Modifier.clip(MaterialTheme.shapes.large)
                        .then(if (participant.isLocalPlayer) Modifier.background(localColor) else Modifier),
                    leadingContent = {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Text(participant.displayName.take(1)) }
                    },
                    headlineContent = { Text(participant.displayName, fontWeight = if (participant.isLocalPlayer) FontWeight.Bold else null) },
                    overlineContent = { Text(stringResource(R.string.game_rank_position, participant.rank)) },
                    trailingContent = { Text(stringResource(R.string.game_points_value, formatNumber(participant.points))) },
                )
            }
        } ?: item { Text(stringResource(R.string.game_league_loading), modifier = Modifier.testTag(GameTestTags.EMPTY)) }
    }
    if (showInfo) ModalBottomSheet(
        onDismissRequest = { showInfo = false }, modifier = Modifier.testTag(WeeklyGroupTestTags.INFO_SHEET),
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.game_league_info_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.game_league_explanation))
            Text(stringResource(R.string.game_league_rules_detail))
        }
    }
}

internal fun visibleLeagueParticipants(
    participants: List<WeeklyLeagueParticipantEntity>,
): List<WeeklyLeagueParticipantEntity> =
    participants.sortedWith(compareBy<WeeklyLeagueParticipantEntity> { it.rank }.thenBy { it.participantId })
