package com.lazyapps.steparena.game

import androidx.annotation.StringRes
import com.lazyapps.steparena.R

object GameNotificationPresentation {
    fun achievementTitle(id: String, stringResource: (Int) -> String): String =
        stringResource(achievementTitleRes(id))

    @StringRes
    fun achievementTitleRes(id: String): Int = when (id) {
        "first_1000_steps" -> R.string.achievement_first_1000_title
        "three_day_streak" -> R.string.achievement_three_days_title
        "seven_day_streak" -> R.string.achievement_seven_days_title
        "first_win" -> R.string.achievement_first_win_title
        "three_wins" -> R.string.achievement_three_wins_title
        "five_wins" -> R.string.achievement_five_wins_title
        "daily_10000_steps" -> R.string.achievement_daily_10000_title
        "daily_20000_steps" -> R.string.achievement_daily_20000_title
        "silver_promotion" -> R.string.achievement_silver_title
        "season_10_matches" -> R.string.achievement_ten_matches_title
        "seven_days_no_recovery" -> R.string.achievement_no_recovery_title
        "gap_recovery_success" -> R.string.achievement_recovery_title
        else -> R.string.achievement_unknown_title
    }

    @StringRes
    fun matchOutcomeNameRes(outcome: MatchOutcome): Int = when (outcome) {
        MatchOutcome.WIN -> R.string.game_outcome_win
        MatchOutcome.LOSS -> R.string.game_outcome_loss
        MatchOutcome.DRAW -> R.string.game_outcome_draw
        MatchOutcome.NO_CONTEST -> R.string.game_outcome_no_contest
        MatchOutcome.IN_PROGRESS -> R.string.game_outcome_in_progress
        MatchOutcome.CANCELLED -> R.string.game_outcome_cancelled
    }

    fun matchOutcomeName(outcome: MatchOutcome, stringResource: (Int) -> String): String =
        stringResource(matchOutcomeNameRes(outcome))
}
