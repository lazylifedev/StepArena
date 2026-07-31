package com.lazyapps.steparena.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.database.entity.AchievementUnlockEntity

data class AchievementUiState(val achievements: List<AchievementUnlockEntity> = emptyList())

@Composable
fun AchievementScreen(vm: GameViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    AchievementScreen(state.achievementUiState())
}

@Composable
fun AchievementScreen(state: AchievementUiState) = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    item { Text(stringResource(R.string.game_achievement_title), style = MaterialTheme.typography.headlineMedium) }
    items(achievementDefinitions) { definition ->
        val unlocked = state.achievements.firstOrNull { it.achievementId == definition.id }
        GameCard(stringResource(definition.titleRes)) {
            Text(stringResource(definition.descriptionRes))
            Text(
                if (unlocked != null) stringResource(R.string.game_achievement_unlocked)
                else stringResource(definition.progressRes, formatNumber(unlocked?.progressValue ?: 0)),
                fontWeight = FontWeight.Bold,
            )
            unlocked?.let {
                Text(stringResource(R.string.game_achievement_date, formatEpochDate(it.unlockedAtEpochMillis)))
            }
        }
    }
}
