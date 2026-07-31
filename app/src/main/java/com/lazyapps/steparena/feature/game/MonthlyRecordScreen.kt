package com.lazyapps.steparena.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.GameSeasonEntity

data class MonthlyRecordUiState(
    val season: GameSeasonEntity? = null,
    val currentRating: Int = 0,
    val daily: List<DailyActivityRecordEntity> = emptyList(),
)

@Composable
fun MonthlyRecordScreen(state: MonthlyRecordUiState) = LazyColumn(
    Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    item { Text(stringResource(R.string.game_season_title), style = MaterialTheme.typography.headlineMedium) }
    state.season?.let { season ->
        val monthDays = state.daily.filter { it.localDate.startsWith(season.id.take(7)) }
        val total = monthDays.sumOf { it.steps }
        val average = if (monthDays.isEmpty()) 0 else total / monthDays.size
        val best = monthDays.maxOfOrNull { it.steps } ?: 0
        item {
            GameCard(formatMonth(season.id)) {
                Text(stringResource(R.string.game_monthly_steps, formatNumber(total)), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.game_monthly_average, formatNumber(average)))
                Text(stringResource(R.string.game_monthly_best, formatNumber(best)))
                MonthlyBars(monthDays)
            }
        }
    } ?: item { Text(stringResource(R.string.game_season_loading), modifier = Modifier.testTag(GameTestTags.EMPTY)) }
}

@Composable
private fun MonthlyBars(days: List<DailyActivityRecordEntity>) {
    val recent = days.sortedBy { it.localDate }.takeLast(14)
    val max = recent.maxOfOrNull { it.steps }?.coerceAtLeast(1) ?: 1
    Row(
        Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom,
    ) {
        recent.forEach { day ->
            Box(
                Modifier.weight(1f).fillMaxHeight((day.steps.toFloat() / max).coerceIn(.04f, 1f))
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
            )
        }
    }
}
