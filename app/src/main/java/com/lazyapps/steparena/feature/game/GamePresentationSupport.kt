package com.lazyapps.steparena.feature.game

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.designsystem.component.GlassSurface
import com.lazyapps.steparena.core.designsystem.theme.StepArenaSpacing
import com.lazyapps.steparena.game.MatchOutcome
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

object GameTestTags {
    const val HEALTH_CAP = "competitive_health_cap"
    const val EMPTY = "game_empty"
}

@Composable
internal fun GameCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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

internal data class AchievementPresentation(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:StringRes val progressRes: Int = R.string.game_achievement_not_unlocked,
)

internal val achievementDefinitions = listOf(
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

internal fun formatMonth(value: String): String = runCatching {
    YearMonth.parse(value.take(7)).format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.getDefault()))
}.getOrDefault(value)

internal fun formatEpochDate(value: Long): String =
    Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

internal fun participantRows(json: String): List<Pair<String, Int>> =
    Regex(""""name":"([^"]+)","points":(\d+)""").findAll(json)
        .map { it.groupValues[1] to it.groupValues[2].toInt() }.toList()
