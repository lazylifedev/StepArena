package com.lazyapps.steparena.feature.game

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.designsystem.component.GlassSurface
import com.lazyapps.steparena.core.designsystem.component.RankBadge
import com.lazyapps.steparena.core.designsystem.component.RankProgressBar
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaSpacing
import com.lazyapps.steparena.game.GameNotificationType
import com.lazyapps.steparena.game.LeagueStatus
import com.lazyapps.steparena.game.MatchOutcome
import com.lazyapps.steparena.game.MatchStatus
import com.lazyapps.steparena.game.RankDefinition
import com.lazyapps.steparena.game.RankSystem
import com.lazyapps.steparena.game.SeasonStatus
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class GamePage { MATCH, RANK, LEAGUE, SEASON, ACHIEVEMENTS }

object GameTestTags {
    const val SCREEN = "game_screen"
    const val PROMOTION = "promotion_dialog"
    const val HEALTH_CAP = "competitive_health_cap"
    const val EMPTY = "game_empty"
}

@Composable
fun GameScreen(
    initialPage: GamePage = GamePage.MATCH,
    motionLevel: MotionLevel = MotionLevel.FULL,
    vm: GameViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(initialPage) }
    state.notificationEvents.firstOrNull {
        it.type == GameNotificationType.PROMOTION && !it.acknowledged
    }?.let { event ->
        AlertDialog(
            modifier = Modifier.testTag(GameTestTags.PROMOTION),
            onDismissRequest = { vm.acknowledgeEvent(event.id) },
            title = { Text(stringResource(R.string.game_promotion_title)) },
            text = {
                Text(stringResource(R.string.game_rating_value, formatNumber(state.profile?.rating ?: 0)))
            },
            confirmButton = {
                TextButton(onClick = { vm.acknowledgeEvent(event.id) }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }
    Column(Modifier.fillMaxSize().testTag(GameTestTags.SCREEN)) {
        PrimaryScrollableTabRow(selectedTabIndex = page.ordinal) {
            GamePage.entries.forEach { item ->
                Tab(
                    selected = page == item,
                    onClick = { page = item },
                    text = { Text(stringResource(item.labelRes())) },
                )
            }
        }
        when (page) {
            GamePage.MATCH -> MatchPage(
                state = state,
                motionLevel = motionLevel,
                onChallengeObserved = vm::observeChallengeMilestone,
                onCelebrationConsumed = vm::acknowledgeChallengeCelebration,
            )
            GamePage.RANK -> RankPage(state)
            GamePage.LEAGUE -> LeaguePage(state)
            GamePage.SEASON -> SeasonPage(state)
            GamePage.ACHIEVEMENTS -> AchievementPage(state)
        }
    }
}

@Composable
private fun RankPage(state: GameUiState) = LazyColumn(
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
                Text(
                    stringResource(
                        R.string.game_streak_counts,
                        profile.currentWinStreak,
                        profile.bestWinStreak,
                    ),
                )
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

@Composable
private fun LeaguePage(state: GameUiState) = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
) {
    item {
        Text(stringResource(R.string.game_league_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.game_league_explanation))
    }
    state.league?.let { league ->
        item {
            Text(
                stringResource(
                    R.string.game_date_range,
                    formatDate(league.weekStartLocalDate),
                    formatDate(league.weekEndLocalDate),
                ),
            )
            Text(stringResource(R.string.game_league_rank, league.userRank ?: "-", 10))
            Text(stringResource(R.string.game_weekly_points, formatNumber(league.userPoints)))
            if (league.status == LeagueStatus.FINALIZED) Text(stringResource(R.string.game_league_finalized))
        }
        items(participantRows(league.participantsJson)) { (name, points) ->
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(participantDisplayNameRes(name)),
                        fontWeight = if (name == "You") FontWeight.Bold else null,
                    )
                },
                trailingContent = {
                    Text(stringResource(R.string.game_points_value, formatNumber(points)))
                },
            )
        }
    } ?: item {
        Text(stringResource(R.string.game_league_loading), modifier = Modifier.testTag(GameTestTags.EMPTY))
    }
}

@Composable
private fun SeasonPage(state: GameUiState) = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    item { Text(stringResource(R.string.game_season_title), style = MaterialTheme.typography.headlineMedium) }
    state.season?.let { season ->
        item {
            GameCard(formatMonth(season.id)) {
                Text(stringResource(season.status.displayNameRes()))
                Text(
                    stringResource(
                        R.string.game_rating_change,
                        formatNumber(season.startRating),
                        formatNumber(season.endRating ?: state.profile?.rating ?: 0),
                    ),
                )
                Text(stringResource(R.string.game_result_counts, season.wins, season.losses, season.draws))
                Text(stringResource(R.string.game_total_eligible_steps, formatNumber(season.totalEligibleSteps)))
                Text(stringResource(R.string.game_best_streak, season.bestWinStreak))
            }
        }
    } ?: item {
        Text(stringResource(R.string.game_season_loading), modifier = Modifier.testTag(GameTestTags.EMPTY))
    }
}

@Composable
private fun AchievementPage(state: GameUiState) = LazyColumn(
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
                else stringResource(
                    definition.progressRes,
                    formatNumber(unlocked?.progressValue ?: 0),
                ),
                fontWeight = FontWeight.Bold,
            )
            unlocked?.let {
                Text(stringResource(R.string.game_achievement_date, formatEpochDate(it.unlockedAtEpochMillis)))
            }
        }
    }
}

@Composable
private fun GameCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(StepArenaSpacing.sm)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

private data class AchievementPresentation(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:StringRes val progressRes: Int = R.string.game_achievement_not_unlocked,
)

private val achievementDefinitions = listOf(
    AchievementPresentation("first_1000_steps", R.string.achievement_first_1000_title, R.string.achievement_first_1000_body, R.string.achievement_progress_1000_steps),
    AchievementPresentation("three_day_streak", R.string.achievement_three_days_title, R.string.achievement_three_days_body, R.string.achievement_progress_three_days),
    AchievementPresentation("seven_day_streak", R.string.achievement_seven_days_title, R.string.achievement_seven_days_body, R.string.achievement_progress_seven_days),
    AchievementPresentation("first_win", R.string.achievement_first_win_title, R.string.achievement_first_win_body),
    AchievementPresentation("three_wins", R.string.achievement_three_wins_title, R.string.achievement_three_wins_body, R.string.achievement_progress_three_wins),
    AchievementPresentation("five_wins", R.string.achievement_five_wins_title, R.string.achievement_five_wins_body, R.string.achievement_progress_five_wins),
    AchievementPresentation("daily_10000_steps", R.string.achievement_daily_10000_title, R.string.achievement_daily_10000_body, R.string.achievement_progress_10000_steps),
    AchievementPresentation("daily_20000_steps", R.string.achievement_daily_20000_title, R.string.achievement_daily_20000_body, R.string.achievement_progress_20000_steps),
    AchievementPresentation("silver_promotion", R.string.achievement_silver_title, R.string.achievement_silver_body),
    AchievementPresentation("season_10_matches", R.string.achievement_ten_matches_title, R.string.achievement_ten_matches_body, R.string.achievement_progress_ten_matches),
    AchievementPresentation("seven_days_no_recovery", R.string.achievement_no_recovery_title, R.string.achievement_no_recovery_body, R.string.achievement_progress_seven_days),
    AchievementPresentation("gap_recovery_success", R.string.achievement_recovery_title, R.string.achievement_recovery_body),
)

@StringRes
fun GamePage.labelRes(): Int = when (this) {
    GamePage.MATCH -> R.string.game_tab_match
    GamePage.RANK -> R.string.game_tab_rank
    GamePage.LEAGUE -> R.string.game_tab_league
    GamePage.SEASON -> R.string.game_tab_season
    GamePage.ACHIEVEMENTS -> R.string.game_tab_achievements
}

@StringRes
fun MatchOutcome.displayNameRes(): Int = when (this) {
    MatchOutcome.WIN -> R.string.game_outcome_win
    MatchOutcome.LOSS -> R.string.game_outcome_loss
    MatchOutcome.DRAW -> R.string.game_outcome_draw
    MatchOutcome.NO_CONTEST -> R.string.game_outcome_no_contest
    MatchOutcome.IN_PROGRESS -> R.string.game_outcome_in_progress
    MatchOutcome.CANCELLED -> R.string.game_outcome_cancelled
}

@StringRes
fun SeasonStatus.displayNameRes(): Int = when (this) {
    SeasonStatus.ACTIVE -> R.string.game_season_active
    SeasonStatus.FINALIZED -> R.string.game_season_finalized
}

@StringRes
fun participantDisplayNameRes(name: String): Int {
    val fixed = mapOf(
        "You" to R.string.game_you,
        "Aoi" to R.string.partner_asahi,
        "Ren" to R.string.partner_komorebi,
        "Sora" to R.string.partner_soyokaze,
        "Hina" to R.string.partner_hinata,
        "Riku" to R.string.partner_michikusa,
        "Yui" to R.string.partner_aozora,
        "Kai" to R.string.partner_kawabe,
        "Mio" to R.string.partner_tsukimi,
    )
    return fixed[name] ?: partnerNameResources[Math.floorMod(name.hashCode(), partnerNameResources.size)]
}

private val partnerNameResources = intArrayOf(
    R.string.partner_asahi,
    R.string.partner_komorebi,
    R.string.partner_soyokaze,
    R.string.partner_hinata,
    R.string.partner_michikusa,
    R.string.partner_aozora,
    R.string.partner_kawabe,
    R.string.partner_tsukimi,
    R.string.partner_nagisa,
    R.string.partner_wakaba,
)

data class NextRankProgress(val rank: RankDefinition, val remaining: Int, val progress: Float)

fun nextRankProgress(rating: Int): NextRankProgress? {
    val current = RankSystem.definition(rating)
    val next = RankSystem.definitions.getOrNull(RankSystem.definitions.indexOf(current) + 1) ?: return null
    val span = next.minimumRating - current.minimumRating
    return NextRankProgress(
        rank = next,
        remaining = (next.minimumRating - rating).coerceAtLeast(0),
        progress = ((rating - current.minimumRating).toFloat() / span.coerceAtLeast(1))
            .coerceIn(0f, 1f),
    )
}

internal fun formatNumber(value: Number): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).format(value)

internal fun formatDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}.getOrDefault(value)

private fun formatMonth(value: String): String = runCatching {
    YearMonth.parse(value.take(7)).format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.getDefault()))
}.getOrDefault(value)

private fun formatEpochDate(value: Long): String =
    Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

private fun participantRows(json: String): List<Pair<String, Int>> =
    Regex(""""name":"([^"]+)","points":(\d+)""").findAll(json)
        .map { it.groupValues[1] to it.groupValues[2].toInt() }.toList()
