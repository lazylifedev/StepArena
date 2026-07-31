package com.lazyapps.steparena.feature.game

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChallengeComparison(
    val totalSteps: Long,
    val eligibleSteps: Long,
    val partnerTargetSteps: Long,
    val remainingSteps: Long,
    val healthConnectAddedSteps: Long,
    val isFinalized: Boolean,
) {
    val goalAchieved: Boolean get() = eligibleSteps >= partnerTargetSteps
    val showsTotalBreakdown: Boolean get() = healthConnectAddedSteps > 0
}

data class ChallengeCelebration(val matchId: String)

fun challengeComparison(
    current: CurrentChallengeSteps,
    healthConnectAddedSteps: Long,
    partnerTargetSteps: Long,
): ChallengeComparison {
    val added = healthConnectAddedSteps.coerceAtLeast(0).takeUnless { current.isFinalized } ?: 0
    val displayed = current.displayedUserSteps.coerceAtLeast(0)
    val total = if (Long.MAX_VALUE - displayed < added) Long.MAX_VALUE else displayed + added
    val target = partnerTargetSteps.coerceAtLeast(1)
    return ChallengeComparison(
        totalSteps = total,
        eligibleSteps = current.eligibleSteps.coerceAtLeast(0),
        partnerTargetSteps = target,
        remainingSteps = (target - current.eligibleSteps.coerceAtLeast(0)).coerceAtLeast(0),
        healthConnectAddedSteps = added,
        isFinalized = current.isFinalized,
    )
}

internal fun shouldCelebrateChallenge(
    lastCelebratedMatchId: String?,
    matchId: String,
    eligibleSteps: Long,
    partnerTargetSteps: Long,
): Boolean =
    matchId.isNotBlank() &&
        matchId != lastCelebratedMatchId &&
        partnerTargetSteps > 0 &&
        eligibleSteps >= partnerTargetSteps

class ChallengeCelebrationRepository(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @SuppressLint("ApplySharedPref", "UseKtx")
    suspend fun claim(
        matchId: String,
        eligibleSteps: Long,
        partnerTargetSteps: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val lastMatchId = preferences.getString(KEY_LAST_CELEBRATED_MATCH_ID, null)
            if (!shouldCelebrateChallenge(
                    lastMatchId,
                    matchId,
                    eligibleSteps,
                    partnerTargetSteps,
                )
            ) {
                false
            } else {
                preferences.edit()
                    .putString(KEY_LAST_CELEBRATED_MATCH_ID, matchId)
                    .commit()
            }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "game_notifications"
        const val KEY_LAST_CELEBRATED_MATCH_ID = "challenge_last_celebrated_match_id"
        val lock = Any()
    }
}
