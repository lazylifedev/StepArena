package com.lazyapps.steparena.game

import java.time.LocalDate
import java.time.ZoneId

enum class RankTier { BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, MASTER }
enum class MatchType { DAILY }
enum class MatchStatus { ACTIVE, FINALIZED, CANCELLED }
enum class MatchOutcome { IN_PROGRESS, WIN, LOSS, DRAW, NO_CONTEST, CANCELLED }
enum class OpponentPersonality { STEADY, SPRINTER, EARLY_BIRD, NIGHT_WALKER, CHALLENGER, RELAXED }
enum class CompetitiveStepQuality { FULL, RESTRICTED, EXCLUDED }
enum class CompetitiveStepRestrictionReason {
    RECOVERED_LIMITED, EXTERNAL_RECOVERY_LIMITED, ESTIMATED_LIMITED, UNKNOWN_EXCLUDED,
    INTEGRITY_LIMIT, DEBUG_DATA, OVERFLOW, NEGATIVE_VALUE, DAILY_ELIGIBLE_LIMIT,
    ABNORMAL_STEPS_PER_MINUTE, COUNTER_BURST, LOW_DETECTOR_COVERAGE,
    IMPLAUSIBLY_REGULAR_RHYTHM, LONG_GAP_INCREMENT, REBOOT_OR_RESET, IMPOSSIBLE_CADENCE,
}
enum class LeagueStatus { ACTIVE, FINALIZED }
enum class SeasonStatus { ACTIVE, FINALIZED }
enum class GameNotificationType { MATCH_RESULT, PROMOTION, ACHIEVEMENT, WEEKLY_LEAGUE, SEASON }
enum class LeagueResultBand { TOP_THREE, MIDDLE, BOTTOM }

data class LeagueParticipant(
    val id: String,
    val name: String,
    val points: Int,
    val eligibleSteps: Long,
)

object LeagueRanking {
    fun rank(participants: List<LeagueParticipant>): List<LeagueParticipant> =
        participants.sortedWith(
            compareByDescending<LeagueParticipant> { it.points }
                .thenByDescending { it.eligibleSteps }
                .thenBy { it.id },
        )

    fun resultBand(rank: Int): LeagueResultBand = when (rank) {
        in 1..3 -> LeagueResultBand.TOP_THREE
        in 4..7 -> LeagueResultBand.MIDDLE
        else -> LeagueResultBand.BOTTOM
    }
}

object QuietHours {
    fun isQuiet(localTime: java.time.LocalTime): Boolean =
        localTime >= java.time.LocalTime.of(22, 0) || localTime < java.time.LocalTime.of(8, 0)
}

data class RankDefinition(
    val tier: RankTier,
    val division: Int?,
    val minimumRating: Int,
    val maximumRating: Int?,
) {
    val displayName: String = if (division == null) {
        "Master"
    } else {
        "${tier.name.lowercase().replaceFirstChar(Char::uppercase)} ${roman(division)}"
    }

    companion object {
        private fun roman(value: Int) = when (value) { 1 -> "I"; 2 -> "II"; else -> "III" }
    }
}

object RankSystem {
    val definitions = buildList {
        var minimum = 1_000
        RankTier.entries.filter { it != RankTier.MASTER }.forEach { tier ->
            (3 downTo 1).forEach { division ->
                add(RankDefinition(tier, division, minimum, minimum + 199))
                minimum += 200
            }
        }
        add(RankDefinition(RankTier.MASTER, null, minimum, null))
    }

    fun definition(rating: Int): RankDefinition =
        definitions.lastOrNull { rating >= it.minimumRating } ?: definitions.first()

    fun clampTransition(before: Int, requestedAfter: Int): Int {
        val beforeIndex = definitions.indexOf(definition(before))
        val targetIndex = definitions.indexOf(definition(requestedAfter))
        val allowedIndex = targetIndex.coerceIn(beforeIndex - 1, beforeIndex + 1)
            .coerceIn(definitions.indices)
        val allowed = definitions[allowedIndex]
        return requestedAfter.coerceIn(allowed.minimumRating, allowed.maximumRating ?: 9_999)
    }
}

data class CompetitiveStepPolicy(
    val measuredRate: Double = 1.0,
    val recoveredRate: Double = 0.0,
    val externalRecoveredRate: Double = 0.0,
    val estimatedRate: Double = 0.0,
    val unknownRate: Double = 1.0,
    val maxExternalRecoveredStepsPerDay: Long = 10_000,
    val maxEligibleStepsPerDay: Long = 100_000,
)

/** User-facing, integrity-approved steps. Raw values remain in the audit model. */
object OfficialSteps {
    const val DAILY_LIMIT = 100_000L
    const val SEGMENT_SIZE = 10_000L
    const val REWARD_LIMIT = 30_000L

    fun fromEligible(eligibleSteps: Long): Long = eligibleSteps.coerceAtLeast(0).coerceAtMost(DAILY_LIMIT)
}

data class CompetitiveIntegrityPolicy(
    val maxStepsPerMinute: Long = 250,
    val maxStepsPerHour: Long = 12_000,
    val maxStepsPerDay: Long = 100_000,
)

data class CompetitiveStepInput(
    val measured: Long = 0,
    val recovered: Long = 0,
    val externalRecovered: Long = 0,
    val estimated: Long = 0,
    val unknown: Long = 0,
    val integrityRestricted: Long = 0,
    val integrityExcluded: Long = 0,
    val integrityReasons: Set<CompetitiveStepRestrictionReason> = emptySet(),
    val integrityViolation: Boolean = false,
    val debugData: Boolean = false,
)

data class CompetitiveStepSummary(
    val totalSteps: Long,
    val eligibleSteps: Long,
    val restrictedSteps: Long,
    val excludedSteps: Long,
    val quality: CompetitiveStepQuality,
    val reasons: Set<CompetitiveStepRestrictionReason>,
)

class CompetitiveStepCalculator(private val policy: CompetitiveStepPolicy = CompetitiveStepPolicy()) {
    fun calculate(input: CompetitiveStepInput): CompetitiveStepSummary {
        val reasons = linkedSetOf<CompetitiveStepRestrictionReason>()
        val raw = listOf(
            input.measured, input.recovered, input.externalRecovered, input.estimated, input.unknown,
            input.integrityRestricted, input.integrityExcluded,
        )
        if (raw.any { it < 0 }) reasons += CompetitiveStepRestrictionReason.NEGATIVE_VALUE
        val values = raw.map { it.coerceAtLeast(0) }
        val total = values.fold(0L) { acc, value ->
            if (Long.MAX_VALUE - acc < value) {
                reasons += CompetitiveStepRestrictionReason.OVERFLOW
                Long.MAX_VALUE
            } else acc + value
        }
        if (input.integrityViolation || input.debugData) {
            reasons += if (input.debugData) CompetitiveStepRestrictionReason.DEBUG_DATA
                else CompetitiveStepRestrictionReason.INTEGRITY_LIMIT
            return CompetitiveStepSummary(total, 0, 0, total, CompetitiveStepQuality.EXCLUDED, reasons)
        }
        reasons += input.integrityReasons
        fun weighted(value: Long, rate: Double): Long = (value * rate).toLong().coerceAtMost(value)
        val external = values[2].coerceAtMost(policy.maxExternalRecoveredStepsPerDay)
        var eligible = weighted(values[0], policy.measuredRate) +
            weighted(values[1], policy.recoveredRate) +
            weighted(external, policy.externalRecoveredRate) +
            weighted(values[3], policy.estimatedRate) +
            weighted(values[4], policy.unknownRate)
        if (values[1] > 0) reasons += CompetitiveStepRestrictionReason.RECOVERED_LIMITED
        if (values[2] > 0) reasons += CompetitiveStepRestrictionReason.EXTERNAL_RECOVERY_LIMITED
        if (values[3] > 0) reasons += CompetitiveStepRestrictionReason.ESTIMATED_LIMITED
        if (values[4] > 0 && policy.unknownRate == 0.0) {
            reasons += CompetitiveStepRestrictionReason.UNKNOWN_EXCLUDED
        }
        if (eligible > policy.maxEligibleStepsPerDay) reasons += CompetitiveStepRestrictionReason.DAILY_ELIGIBLE_LIMIT
        eligible = eligible.coerceAtMost(policy.maxEligibleStepsPerDay)
        val excluded = values[4] + (values[2] - external) + values[6]
        val restricted = (total - eligible - excluded).coerceAtLeast(0)
        return CompetitiveStepSummary(
            total, eligible, restricted, excluded,
            if (reasons.isEmpty()) CompetitiveStepQuality.FULL else CompetitiveStepQuality.RESTRICTED,
            reasons,
        )
    }
}

data class LocalOpponent(
    val id: String,
    val displayName: String,
    val avatarKey: String,
    val rankTier: RankTier,
    val rankDivision: Int?,
    val targetSteps: Long,
    val personality: OpponentPersonality,
)

data class OpponentGenerationInput(
    val installationId: String,
    val seasonId: String,
    val localDate: LocalDate,
    val rank: RankDefinition,
    val recentMedian: Long? = null,
    val currentLossStreak: Int = 0,
    val beginner: Boolean = false,
)

class LocalOpponentGenerator {
    private val names = listOf("Aoi", "Ren", "Sora", "Hina", "Riku", "Yui", "Kai", "Mio")
    fun generate(input: OpponentGenerationInput): LocalOpponent {
        val seedText = "${input.seasonId}|${input.localDate}|${input.rank.tier}|${input.rank.division}|${input.installationId}"
        val seed = seedText.fold(1125899906842597L) { acc, char -> acc * 31 + char.code }
        val random = java.util.Random(seed)
        val personality = OpponentPersonality.entries[random.nextInt(OpponentPersonality.entries.size)]
        val rankIndex = RankSystem.definitions.indexOf(input.rank)
        val rankFactor = 0.9 + rankIndex * 0.025
        val weekdayFactor = if (input.localDate.dayOfWeek.value >= 6) 1.05 else 1.0
        val rescue = (1.0 - input.currentLossStreak.coerceAtMost(5) * 0.03)
        val beginner = if (input.beginner) 0.85 else 1.0
        val randomFactor = 0.85 + random.nextDouble() * 0.30
        val target = ((input.recentMedian ?: 6_000L) * rankFactor * weekdayFactor * rescue * beginner * randomFactor)
            .toLong().coerceIn(1_000, 50_000)
        val id = "npc-${seed.toULong().toString(16)}"
        return LocalOpponent(
            id, names[random.nextInt(names.size)], "avatar_${random.nextInt(8) + 1}",
            input.rank.tier, input.rank.division, target, personality,
        )
    }

    fun progress(opponent: LocalOpponent, minuteOfDay: Int): Long {
        val fraction = (minuteOfDay.coerceIn(0, 1_440) / 1_440.0)
        val shaped = when (opponent.personality) {
            OpponentPersonality.EARLY_BIRD -> kotlin.math.sqrt(fraction)
            OpponentPersonality.NIGHT_WALKER -> fraction * fraction
            OpponentPersonality.SPRINTER -> fraction * fraction * fraction
            OpponentPersonality.STEADY -> fraction
            OpponentPersonality.CHALLENGER -> fraction * 1.05
            OpponentPersonality.RELAXED -> fraction * 0.95
        }.coerceIn(0.0, 1.0)
        return (opponent.targetSteps * shaped).toLong()
    }
}

data class RatingCalculationInput(
    val outcome: MatchOutcome,
    val playerRankIndex: Int,
    val opponentRankIndex: Int,
    val currentWinStreak: Int,
    val beginner: Boolean,
    val integrityViolation: Boolean,
    val restrictedRatio: Double,
)

data class RatingChange(
    val base: Int,
    val rankDifferenceBonus: Int,
    val streakBonus: Int,
    val integrityAdjustment: Int,
    val finalDelta: Int,
)

interface RatingCalculator { fun calculate(input: RatingCalculationInput): RatingChange }

class DefaultRatingCalculator : RatingCalculator {
    override fun calculate(input: RatingCalculationInput): RatingChange {
        val base = when (input.outcome) {
            MatchOutcome.WIN -> 25
            MatchOutcome.LOSS -> if (input.beginner) 0 else -20
            else -> 0
        }
        val rankBonus = if (input.outcome == MatchOutcome.WIN) {
            ((input.opponentRankIndex - input.playerRankIndex) * 3).coerceIn(-6, 9)
        } else 0
        val streak = if (input.outcome == MatchOutcome.WIN && !input.beginner) {
            (input.currentWinStreak * 2).coerceAtMost(10)
        } else 0
        val preliminary = base + rankBonus + streak
        val integrity = when {
            input.integrityViolation -> -preliminary
            preliminary > 0 && input.restrictedRatio > 0.5 -> -(preliminary - 15).coerceAtLeast(0)
            else -> 0
        }
        return RatingChange(base, rankBonus, streak, integrity, (preliminary + integrity).coerceIn(-30, 45))
    }
}

fun outcome(userSteps: Long, opponentSteps: Long, blocked: Boolean = false): MatchOutcome = when {
    blocked || userSteps == 0L && opponentSteps == 0L -> MatchOutcome.NO_CONTEST
    userSteps > opponentSteps -> MatchOutcome.WIN
    userSteps < opponentSteps -> MatchOutcome.LOSS
    else -> MatchOutcome.DRAW
}

data class GameClock(val date: LocalDate, val zoneId: ZoneId)
