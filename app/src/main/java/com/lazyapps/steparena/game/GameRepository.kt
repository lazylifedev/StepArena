package com.lazyapps.steparena.game

import android.content.Context
import androidx.room.withTransaction
import com.lazyapps.steparena.core.database.StepArenaDatabase
import com.lazyapps.steparena.core.database.entity.*
import com.lazyapps.steparena.core.database.model.DataQuality
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

interface GameRepository {
    fun observePlayerProfile(): Flow<GamePlayerProfileEntity>
    fun observeTodayMatch(): Flow<DailyMatchEntity?>
    fun observeRecentMatches(limit: Int): Flow<List<DailyMatchEntity>>
    fun observeCurrentLeague(): Flow<WeeklyLeagueEntity?>
    fun observeCurrentSeason(): Flow<GameSeasonEntity?>
    fun observeAchievements(): Flow<List<AchievementUnlockEntity>>
    suspend fun ensureTodayMatch()
    suspend fun finalizePendingMatches()
    suspend fun rebuildCurrentLeague()
    suspend fun evaluateAchievements()
}

class LocalGameRepository(
    private val context: Context,
    private val database: StepArenaDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val stepCalculator: CompetitiveStepCalculator = CompetitiveStepCalculator(),
    private val opponentGenerator: LocalOpponentGenerator = LocalOpponentGenerator(),
    private val ratingCalculator: RatingCalculator = DefaultRatingCalculator(),
) : GameRepository {
    private val zone: ZoneId get() = clock.zone
    private val today: LocalDate get() = LocalDate.now(clock)
    private val installationId: String by lazy {
        val preferences = context.getSharedPreferences("local_game", Context.MODE_PRIVATE)
        preferences.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("installation_id", it).apply()
        }
    }

    override fun observePlayerProfile() = database.gamePlayerProfile().observe().filterNotNull()
    override fun observeTodayMatch() = database.dailyMatches().observe(today.toString(), zone.id)
    override fun observeRecentMatches(limit: Int) = database.dailyMatches().recent(limit)
    override fun observeCurrentLeague() = database.weeklyLeagues().observeCurrent()
    override fun observeCurrentSeason() = database.gameSeasons().observeCurrent()
    override fun observeAchievements() = database.achievementUnlocks().observeAll()

    override suspend fun ensureTodayMatch() = database.withTransaction {
        val now = clock.millis()
        val profileDao = database.gamePlayerProfile()
        val profile = profileDao.get() ?: GamePlayerProfileEntity(
            createdAtEpochMillis = now, updatedAtEpochMillis = now,
        ).also { profileDao.upsert(it) }
        ensureSeason(now, profile)
        val date = today.toString()
        val rank = RankSystem.definition(profile.rating)
        val recent = database.daily().recentNow(28)
        val median = recent.map { it.steps }.sorted().let { values ->
            values.takeIf { it.isNotEmpty() }?.get(values.size / 2)
        }
        val opponent = opponentGenerator.generate(
            OpponentGenerationInput(
                installationId, seasonId(today), today, rank, median,
                profile.currentLossStreak, profile.beginnerMatchesRemaining > 0,
            ),
        )
        database.dailyMatches().insert(
            DailyMatchEntity(
                id = "daily-${seasonId(today)}-$date-${zone.id.hashCode()}",
                localDate = date, zoneId = zone.id, seasonId = seasonId(today),
                matchType = MatchType.DAILY, status = MatchStatus.ACTIVE, outcome = null,
                opponentId = opponent.id, opponentName = opponent.displayName,
                opponentAvatarKey = opponent.avatarKey, opponentRankTier = opponent.rankTier,
                opponentRankDivision = opponent.rankDivision,
                opponentPersonality = opponent.personality,
                opponentTargetSteps = opponent.targetSteps, totalUserSteps = 0,
                eligibleUserSteps = 0, restrictedUserSteps = 0, excludedUserSteps = 0,
                restrictionReasons = "", competitiveQuality = CompetitiveStepQuality.FULL,
                ratingBefore = profile.rating, ratingDelta = null, ratingAfter = null,
                ratingBreakdown = null, finalizedAtEpochMillis = null,
                createdAtEpochMillis = now, updatedAtEpochMillis = now,
            ),
        )
        rebuildLeague(now)
    }

    override suspend fun finalizePendingMatches() {
        val pending = database.dailyMatches().pending(before = today.toString())
        pending.forEach { match -> finalize(match.id) }
    }

    private suspend fun finalize(id: String) = database.withTransaction {
        val dao = database.dailyMatches()
        val match = dao.get(id) ?: return@withTransaction
        if (match.status != MatchStatus.ACTIVE || match.ratingAfter != null) return@withTransaction
        val daily = database.daily().get(match.localDate, match.zoneId)
        val steps = competitiveSummary(daily)
        val result = outcome(
            steps.eligibleSteps, match.opponentTargetSteps,
            steps.quality == CompetitiveStepQuality.EXCLUDED,
        )
        val profile = database.gamePlayerProfile().get() ?: return@withTransaction
        val playerIndex = RankSystem.definitions.indexOf(RankSystem.definition(profile.rating))
        val opponentIndex = RankSystem.definitions.indexOfFirst {
            it.tier == match.opponentRankTier && it.division == match.opponentRankDivision
        }.coerceAtLeast(0)
        val change = ratingCalculator.calculate(
            RatingCalculationInput(
                result, playerIndex, opponentIndex, profile.currentWinStreak,
                profile.beginnerMatchesRemaining > 0,
                steps.quality == CompetitiveStepQuality.EXCLUDED,
                if (steps.totalSteps == 0L) 0.0 else steps.restrictedSteps.toDouble() / steps.totalSteps,
            ),
        )
        val after = RankSystem.clampTransition(profile.rating, (profile.rating + change.finalDelta).coerceAtLeast(1_000))
        val winStreak = if (result == MatchOutcome.WIN) profile.currentWinStreak + 1 else 0
        val lossStreak = if (result == MatchOutcome.LOSS) profile.currentLossStreak + 1 else 0
        val now = clock.millis()
        database.gamePlayerProfile().upsert(
            profile.copy(
                rating = after,
                rankTier = RankSystem.definition(after).tier,
                rankDivision = RankSystem.definition(after).division,
                totalMatches = profile.totalMatches + 1,
                wins = profile.wins + if (result == MatchOutcome.WIN) 1 else 0,
                losses = profile.losses + if (result == MatchOutcome.LOSS) 1 else 0,
                draws = profile.draws + if (result == MatchOutcome.DRAW) 1 else 0,
                noContests = profile.noContests + if (result == MatchOutcome.NO_CONTEST) 1 else 0,
                currentWinStreak = winStreak,
                bestWinStreak = maxOf(profile.bestWinStreak, winStreak),
                currentLossStreak = lossStreak,
                beginnerMatchesRemaining = (profile.beginnerMatchesRemaining - 1).coerceAtLeast(0),
                lastOutcome = result,
                updatedAtEpochMillis = now,
            ),
        )
        dao.update(
            match.copy(
                status = MatchStatus.FINALIZED, outcome = result,
                totalUserSteps = steps.totalSteps, eligibleUserSteps = steps.eligibleSteps,
                restrictedUserSteps = steps.restrictedSteps, excludedUserSteps = steps.excludedSteps,
                restrictionReasons = steps.reasons.joinToString(",") { it.name },
                competitiveQuality = steps.quality, ratingDelta = after - profile.rating,
                ratingAfter = after,
                ratingBreakdown = "${change.base},${change.rankDifferenceBonus},${change.streakBonus},${change.integrityAdjustment}",
                finalizedAtEpochMillis = now, updatedAtEpochMillis = now,
            ),
        )
    }

    private fun competitiveSummary(daily: DailyActivityRecordEntity?): CompetitiveStepSummary {
        if (daily == null) return stepCalculator.calculate(CompetitiveStepInput())
        val recovered = daily.unclassifiedSteps.coerceAtMost(daily.steps)
        val measured = (daily.steps - recovered).coerceAtLeast(0)
        return when (daily.stepsQuality) {
            DataQuality.MEASURED -> stepCalculator.calculate(CompetitiveStepInput(measured = daily.steps))
            DataQuality.RECOVERED -> stepCalculator.calculate(CompetitiveStepInput(recovered = daily.steps))
            DataQuality.ESTIMATED -> stepCalculator.calculate(CompetitiveStepInput(estimated = daily.steps))
            DataQuality.UNKNOWN -> stepCalculator.calculate(CompetitiveStepInput(unknown = daily.steps))
            DataQuality.MIXED -> stepCalculator.calculate(CompetitiveStepInput(measured, recovered))
        }
    }

    override suspend fun rebuildCurrentLeague() = database.withTransaction { rebuildLeague(clock.millis()) }

    private suspend fun rebuildLeague(now: Long) {
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val recent = database.dailyMatches().recentNow(7)
        val points = recent.sumOf {
            when (it.outcome) { MatchOutcome.WIN -> 3; MatchOutcome.DRAW -> 1; else -> 0 }
        }
        val names = listOf("You", "Aoi", "Ren", "Sora", "Hina", "Riku", "Yui", "Kai", "Mio", "Nao")
        val participants = names.mapIndexed { index, name ->
            """{"name":"$name","points":${if (index == 0) points else (start.hashCode() + index * 7).mod(22)}}"""
        }.joinToString(prefix = "[", postfix = "]")
        val npcPoints = (1..9).map { (start.hashCode() + it * 7).mod(22) }
        database.weeklyLeagues().upsert(
            WeeklyLeagueEntity(
                "league-$start-${zone.id.hashCode()}", start.toString(), start.plusDays(6).toString(),
                zone.id, LeagueStatus.ACTIVE, points, 1 + npcPoints.count { it > points },
                participants, null, now, now,
            ),
        )
    }

    private suspend fun ensureSeason(now: Long, profile: GamePlayerProfileEntity) {
        val id = seasonId(today)
        if (database.gameSeasons().get(id) != null) return
        val start = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        database.gameSeasons().upsert(
            GameSeasonEntity(
                id, start, end, profile.rating, null, profile.rankTier, profile.rankDivision,
                0, 0, 0, 0, profile.bestWinStreak, SeasonStatus.ACTIVE, false, now, now,
            ),
        )
    }

    override suspend fun evaluateAchievements() {
        val profile = database.gamePlayerProfile().get() ?: return
        val now = clock.millis()
        val definitions = buildList {
            if (profile.wins >= 1) add("first_win" to profile.wins.toLong())
            if (profile.bestWinStreak >= 3) add("three_wins" to profile.bestWinStreak.toLong())
            if (profile.bestWinStreak >= 5) add("five_wins" to profile.bestWinStreak.toLong())
            if (profile.rankTier != RankTier.BRONZE) add("silver_promotion" to profile.rating.toLong())
        }
        definitions.forEach { (id, progress) ->
            database.achievementUnlocks().insert(
                AchievementUnlockEntity(id, now, progress, seasonId(today), false),
            )
        }
    }

    private fun seasonId(date: LocalDate) = "%04d-%02d".format(date.year, date.monthValue)
}
