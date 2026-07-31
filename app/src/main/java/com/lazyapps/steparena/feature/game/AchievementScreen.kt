package com.lazyapps.steparena.feature.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.database.entity.AchievementUnlockEntity
import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.DailyMatchEntity
import com.lazyapps.steparena.core.database.entity.GamePlayerProfileEntity
import com.lazyapps.steparena.game.MatchStatus
import com.lazyapps.steparena.game.RankSystem
import java.time.Instant

data class AchievementProgressUi(
    val id: String,
    val current: Long,
    val target: Long,
    val unlocked: Boolean,
    val unlockedAt: Instant?,
    val isNew: Boolean,
)

data class AchievementUiState(val progress: List<AchievementProgressUi> = emptyList())

object AchievementTestTags {
    const val GRID = "achievement_grid"
    const val SHEET = "achievement_detail_sheet"
    fun item(id: String) = "achievement_$id"
}

@Composable
fun AchievementScreen(vm: GameViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    AchievementScreen(state.achievementUiState(), vm::acknowledgeAchievement)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementScreen(
    state: AchievementUiState,
    onAcknowledge: (String) -> Unit = {},
) {
    var selected by remember { mutableStateOf<AchievementProgressUi?>(null) }
    val columns = achievementColumnCount(LocalConfiguration.current.fontScale)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().testTag(AchievementTestTags.GRID),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.game_achievement_title), style = MaterialTheme.typography.headlineMedium)
        }
        items(state.progress, key = { it.id }) { progress ->
            val definition = achievementDefinitions.first { it.id == progress.id }
            AchievementBadge(definition, progress) {
                selected = progress
                if (progress.isNew) onAcknowledge(progress.id)
            }
        }
    }
    selected?.let { progress ->
        val definition = achievementDefinitions.first { it.id == progress.id }
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            modifier = Modifier.testTag(AchievementTestTags.SHEET),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(definition.titleRes), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(definition.descriptionRes))
                Text(
                    stringResource(
                        R.string.game_achievement_progress_detail,
                        formatNumber(progress.current),
                        formatNumber(progress.target),
                    ),
                    fontWeight = FontWeight.Bold,
                )
                progress.unlockedAt?.let {
                    Text(stringResource(R.string.game_achievement_date, formatEpochDate(it.toEpochMilli())))
                }
            }
        }
    }
}

internal fun achievementColumnCount(fontScale: Float): Int = if (fontScale >= 2f) 1 else 2

@Composable
private fun AchievementBadge(
    definition: AchievementPresentation,
    progress: AchievementProgressUi,
    onClick: () -> Unit,
) {
    val accent = if (progress.unlocked) Color(0xFF58E6FF) else MaterialTheme.colorScheme.outline
    val title = stringResource(definition.titleRes)
    val stateLabel = stringResource(
        if (progress.unlocked) R.string.game_achievement_unlocked_state
        else R.string.game_achievement_locked_state,
    )
    val newLabel = if (progress.isNew) stringResource(R.string.game_achievement_new) else ""
    val accessibilityLabel = stringResource(
        R.string.game_achievement_accessibility,
        title,
        stateLabel,
        formatNumber(progress.current),
        formatNumber(progress.target),
        newLabel,
    )
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp)
            .testTag(AchievementTestTags.item(progress.id))
            .semantics { contentDescription = accessibilityLabel }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (progress.unlocked) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        ),
        border = BorderStroke(if (progress.unlocked) 2.dp else 1.dp, accent),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(
                    if (progress.unlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
                    contentDescription = null,
                    tint = accent,
                )
                if (progress.isNew) Badge { Text(stringResource(R.string.game_achievement_new)) }
            }
            Text(stringResource(definition.titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(
                    R.string.game_achievement_progress_compact,
                    formatNumber(progress.current),
                    formatNumber(progress.target),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            LinearProgressIndicator(
                progress = { (progress.current.toFloat() / progress.target.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal fun achievementProgress(
    profile: GamePlayerProfileEntity?,
    matches: List<DailyMatchEntity>,
    daily: List<DailyActivityRecordEntity>,
    unlocks: List<AchievementUnlockEntity>,
    currentSeasonId: String? = null,
    currentEligibleSteps: Long = 0,
): List<AchievementProgressUi> {
    val finalized = matches.filter { it.status == MatchStatus.FINALIZED }
    val bestEligible = maxOf(
        finalized.maxOfOrNull { it.eligibleUserSteps } ?: 0,
        currentEligibleSteps.coerceAtLeast(0),
    )
    val recordedStreak = consecutiveRecordedDays(daily.filter { it.steps > 0 }.map { it.localDate })
    val noRecoveryStreak = consecutiveRecordedDays(
        daily.filter { it.steps > 0 && it.externalRecoveredSteps == 0L }.map { it.localDate },
    )
    val effectiveSeasonId = currentSeasonId ?: finalized.firstOrNull()?.seasonId
    val currentSeasonMatches = finalized.count { it.seasonId == effectiveSeasonId }
    val silverRating = RankSystem.definitions.first { it.tier.name == "SILVER" }.minimumRating.toLong()
    val values = mapOf(
        "first_1000_steps" to bestEligible,
        "three_day_streak" to recordedStreak.toLong(),
        "seven_day_streak" to recordedStreak.toLong(),
        "first_win" to (profile?.wins ?: 0).toLong(),
        "three_wins" to (profile?.bestWinStreak ?: 0).toLong(),
        "five_wins" to (profile?.bestWinStreak ?: 0).toLong(),
        "daily_10000_steps" to bestEligible,
        "daily_20000_steps" to bestEligible,
        "silver_promotion" to (profile?.rating ?: 1_000).toLong(),
        "season_10_matches" to currentSeasonMatches.toLong(),
        "seven_days_no_recovery" to noRecoveryStreak.toLong(),
        "gap_recovery_success" to daily.count { it.externalRecoveredSteps > 0 }.toLong(),
    )
    val targets = mapOf(
        "first_1000_steps" to 1_000L, "three_day_streak" to 3L, "seven_day_streak" to 7L,
        "first_win" to 1L, "three_wins" to 3L, "five_wins" to 5L,
        "daily_10000_steps" to 10_000L, "daily_20000_steps" to 20_000L,
        "silver_promotion" to silverRating, "season_10_matches" to 10L,
        "seven_days_no_recovery" to 7L, "gap_recovery_success" to 1L,
    )
    return achievementDefinitions.map { definition ->
        val unlock = unlocks.firstOrNull { it.achievementId == definition.id }
        val target = targets.getValue(definition.id)
        val current = if (unlock == null) values.getValue(definition.id) else {
            maxOf(values.getValue(definition.id), unlock.progressValue, target)
        }
        AchievementProgressUi(
            definition.id, current, target,
            unlock != null, unlock?.unlockedAtEpochMillis?.let(Instant::ofEpochMilli),
            unlock != null && !unlock.acknowledged,
        )
    }
}

private fun consecutiveRecordedDays(values: List<String>): Int {
    val dates = values.mapNotNull { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }.distinct().sorted()
    var best = 0
    var current = 0
    var previous: java.time.LocalDate? = null
    dates.forEach { date ->
        current = if (previous?.plusDays(1) == date) current + 1 else 1
        best = maxOf(best, current)
        previous = date
    }
    return best
}
