package com.lazyapps.steparena.game

import android.content.Context
import androidx.room.withTransaction
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.database.StepArenaDatabase
import com.lazyapps.steparena.core.database.entity.*
import com.lazyapps.steparena.core.database.model.DataQuality
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf

interface GameRepository {
    fun observePlayerProfile(): Flow<GamePlayerProfileEntity>
    fun observeTodayMatch(): Flow<DailyMatchEntity?>
    fun observeRecentMatches(limit: Int): Flow<List<DailyMatchEntity>>
    fun observeCurrentLeague(): Flow<WeeklyLeagueEntity?>
    fun observeCurrentLeagueParticipants(): Flow<List<WeeklyLeagueParticipantEntity>>
    fun observeCurrentSeason(): Flow<GameSeasonEntity?>
    fun observeAchievements(): Flow<List<AchievementUnlockEntity>>
    fun observeNotificationEvents(): Flow<List<GameNotificationEventEntity>>
    suspend fun ensureTodayMatch()
    suspend fun finalizePendingMatches()
    suspend fun finalizeMatch(id: String)
    suspend fun rebuildCurrentLeague()
    suspend fun finalizeExpiredLeagues()
    suspend fun finalizeExpiredSeasons()
    suspend fun evaluateAchievements()
    suspend fun runMaintenance()
    suspend fun acknowledgeNotificationEvent(id: String)
}

class LocalGameRepository(
    private val context: Context,
    private val database: StepArenaDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val stepCalculator: CompetitiveStepCalculator = CompetitiveStepCalculator(),
    private val opponentGenerator: LocalOpponentGenerator = LocalOpponentGenerator(),
    private val ratingCalculator: RatingCalculator = DefaultRatingCalculator(),
    private val installationIdOverride: String? = null,
    private val notificationConfig: GameNotificationConfig = GameNotificationConfig(),
) : GameRepository {
    private val zone: ZoneId get() = clock.zone
    private val today: LocalDate get() = LocalDate.now(clock)
    private val installationId: String by lazy {
        installationIdOverride ?: run {
            val preferences = context.getSharedPreferences("local_game", Context.MODE_PRIVATE)
            preferences.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
                preferences.edit().putString("installation_id", it).apply()
            }
        }
    }

    override fun observePlayerProfile() = database.gamePlayerProfile().observe().filterNotNull()
    override fun observeTodayMatch() = database.dailyMatches().observe(today.toString(), zone.id)
    fun observeMatch(date: LocalDate, zoneId: ZoneId) =
        database.dailyMatches().observe(date.toString(), zoneId.id)
    override fun observeRecentMatches(limit: Int) = database.dailyMatches().recent(limit)
    override fun observeCurrentLeague() = database.weeklyLeagues().observeCurrent()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeCurrentLeagueParticipants() = observeCurrentLeague().flatMapLatest { league ->
        league?.let { database.weeklyLeagueParticipants().observeForLeague(it.id) }
            ?: flowOf(emptyList())
    }
    override fun observeCurrentSeason() = database.gameSeasons().observeCurrent()
    override fun observeAchievements() = database.achievementUnlocks().observeAll()
    override fun observeNotificationEvents() = database.gameNotificationEvents().observeAll()
    override suspend fun acknowledgeNotificationEvent(id: String) =
        database.gameNotificationEvents().acknowledge(id)

    override suspend fun runMaintenance() {
        finalizePendingMatches()
        finalizeExpiredLeagues()
        finalizeExpiredSeasons()
        ensureTodayMatch()
        rebuildCurrentLeague()
        evaluateAchievements()
        GameNotificationDispatcher(context, database, clock, notificationConfig).dispatchPending()
    }

    override suspend fun ensureTodayMatch() = ensureMatch(today, zone)

    suspend fun ensureMatch(targetDate: LocalDate, targetZone: ZoneId) = database.withTransaction {
        val now = clock.millis()
        val profileDao = database.gamePlayerProfile()
        val profile = profileDao.get() ?: GamePlayerProfileEntity(
            createdAtEpochMillis = now, updatedAtEpochMillis = now,
        ).also { profileDao.upsert(it) }
        ensureSeason(now, profile)
        val date = targetDate.toString()
        val rank = RankSystem.definition(profile.rating)
        val recent = database.daily().recentNow(28)
        val median = recent.map { daily ->
            OfficialSteps.fromEligible(
                competitiveSummary(
                    daily,
                    database.competitiveIntegritySegments().forDate(daily.localDate, daily.zoneId),
                ).eligibleSteps,
            )
        }.sorted().let { values ->
            values.takeIf { it.isNotEmpty() }?.get(values.size / 2)
        }
        val generationInput = OpponentGenerationInput(
                installationId, seasonId(targetDate), targetDate, rank, median,
                profile.currentLossStreak, profile.beginnerMatchesRemaining > 0,
            )
        val userCandidate = MatchCandidate(
            id = "player", type = MatchCandidateType.REAL,
            rankTier = rank.tier, rankDivision = rank.division,
            recentOfficialSteps = median, personalGoalBand = goalBand(median),
            timezone = targetZone.id, lastActiveAtEpochMillis = now,
            recentOpponentIds = database.dailyMatches().recentNow(3).map { it.opponentId }.toSet(),
        )
        val opponent = MatchCandidateScoring.rank(
            opponentGenerator.generateCandidates(generationInput).map { bot ->
                val botSteps = OfficialSteps.fromEligible(
                    opponentGenerator.progress(bot, 1_440),
                )
                MatchCandidate(
                    id = bot.id, type = MatchCandidateType.BOT,
                    rankTier = bot.rankTier, rankDivision = bot.rankDivision,
                    recentOfficialSteps = botSteps, personalGoalBand = goalBand(bot.targetSteps),
                    timezone = targetZone.id, lastActiveAtEpochMillis = 0L,
                )
            }, userCandidate,
        ).first().let { selected ->
            generationInput.let { opponentGenerator.generateCandidates(it).first { it.id == selected.id } }
        }
        database.dailyMatches().insert(
            DailyMatchEntity(
                id = "daily-${seasonId(targetDate)}-$date-${targetZone.id.hashCode()}",
                localDate = date, zoneId = targetZone.id, seasonId = seasonId(targetDate),
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
        database.dailyMatches().getForDate(date, targetZone.id)?.let { active ->
            if (active.status == MatchStatus.ACTIVE) {
                val summary = competitiveSummary(
                    database.daily().get(date, targetZone.id),
                    database.competitiveIntegritySegments().forDate(date, targetZone.id),
                )
                val desired = active.copy(
                        totalUserSteps = summary.totalSteps,
                        eligibleUserSteps = summary.eligibleSteps,
                        restrictedUserSteps = summary.restrictedSteps,
                        excludedUserSteps = summary.excludedSteps,
                        restrictionReasons = summary.reasons.joinToString(",") { it.name },
                        competitiveQuality = summary.quality,
                        updatedAtEpochMillis = now,
                    )
                if (desired.copy(updatedAtEpochMillis = active.updatedAtEpochMillis) != active) {
                    database.dailyMatches().update(desired)
                }
            }
        }
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
        val steps = competitiveSummary(
            daily,
            database.competitiveIntegritySegments().forDate(match.localDate, match.zoneId),
        )
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
        createNotification(
            GameNotificationType.MATCH_RESULT,
            match.id,
            "match:${match.id}",
            context.getString(R.string.notification_challenge_result_title),
            context.getString(R.string.notification_challenge_result_text, result.notificationName()),
            "challenge",
            now,
        )
        val beforeRank = RankSystem.definition(profile.rating)
        val afterRank = RankSystem.definition(after)
        if (RankSystem.definitions.indexOf(afterRank) > RankSystem.definitions.indexOf(beforeRank)) {
            createNotification(
                GameNotificationType.PROMOTION,
                match.id,
                "promotion:${match.id}:${afterRank.displayName}",
                context.getString(R.string.notification_rank_updated_title),
                context.getString(
                    R.string.notification_rank_updated_text,
                    beforeRank.displayName,
                    afterRank.displayName,
                ),
                "challenge/rank",
                now,
            )
        }
    }

    override suspend fun rebuildCurrentLeague() = database.withTransaction { rebuildLeague(clock.millis()) }

    private suspend fun rebuildLeague(now: Long) {
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val recent = database.dailyMatches().recentNow(7)
        val points = recent.sumOf {
            when (it.outcome) { MatchOutcome.WIN -> 3; MatchOutcome.DRAW -> 1; else -> 0 }
        }
        val existingId = "league-$start-${zone.id.hashCode()}"
        if (database.weeklyLeagues().get(existingId)?.status == LeagueStatus.FINALIZED) return
        val profile = database.gamePlayerProfile().get()
        val participantSeeds = listOf(
            Triple(
                "player",
                publicDisplayName(profile?.displayName, context.getString(R.string.game_you)),
                "local_player",
            ),
            Triple("npc-1", context.getString(R.string.partner_asahi), "asahi"),
            Triple("npc-2", context.getString(R.string.partner_komorebi), "komorebi"),
            Triple("npc-3", context.getString(R.string.partner_soyokaze), "soyokaze"),
            Triple("npc-4", context.getString(R.string.partner_hinata), "hinata"),
            Triple("npc-5", context.getString(R.string.partner_michikusa), "michikusa"),
            Triple("npc-6", context.getString(R.string.partner_aozora), "aozora"),
            Triple("npc-7", context.getString(R.string.partner_kawabe), "kawabe"),
            Triple("npc-8", context.getString(R.string.partner_tsukimi), "tsukimi"),
            Triple("npc-9", context.getString(R.string.partner_nagisa), "nagisa"),
        )
        val ranked = LeagueRanking.rank(participantSeeds.mapIndexed { index, seed ->
            LeagueParticipant(
                id = seed.first,
                name = seed.second,
                points = if (index == 0) points else (start.hashCode() + index * 7).mod(22),
                eligibleSteps = if (index == 0) recent.sumOf { it.eligibleUserSteps }
                    else (start.hashCode().toLong() + index * 13L).mod(50_000L),
            )
        })
        val userRank = ranked.indexOfFirst { it.id == "player" } + 1
        val leagueDao = database.weeklyLeagues()
        val existingLeague = leagueDao.get(existingId)
        val desiredLeague = WeeklyLeagueEntity(
                existingId, start.toString(), start.plusDays(6).toString(),
                zone.id, LeagueStatus.ACTIVE, points, userRank,
                "[]", null, existingLeague?.createdAtEpochMillis ?: now, now,
            )
        if (existingLeague == null ||
            desiredLeague.copy(updatedAtEpochMillis = existingLeague.updatedAtEpochMillis) != existingLeague
        ) {
            leagueDao.upsert(desiredLeague)
        }
        val participantDao = database.weeklyLeagueParticipants()
        val existingParticipants = participantDao.getForLeague(existingId).associateBy { it.participantId }
        val desiredParticipants = ranked.mapIndexed { index, participant ->
                val seed = participantSeeds.first { it.first == participant.id }
                WeeklyLeagueParticipantEntity(
                    leagueId = existingId,
                    participantId = participant.id,
                    displayName = participant.name,
                    avatarKey = seed.third,
                    points = participant.points,
                    eligibleSteps = participant.eligibleSteps,
                    rank = index + 1,
                    isLocalPlayer = participant.id == "player",
                    generatedLocally = true,
                    createdAtEpochMillis = existingParticipants[participant.id]?.createdAtEpochMillis ?: now,
                    updatedAtEpochMillis = now,
                )
            }
        val changedParticipants = desiredParticipants.filter { desired ->
            val existing = existingParticipants[desired.participantId]
            existing == null || desired.copy(updatedAtEpochMillis = existing.updatedAtEpochMillis) != existing
        }
        if (changedParticipants.isNotEmpty()) participantDao.upsertAll(changedParticipants)
    }

    override suspend fun finalizeMatch(id: String) = finalize(id)

    override suspend fun finalizeExpiredLeagues() {
        val now = clock.millis()
        database.weeklyLeagues().expired(today.toString()).forEach { league ->
            database.withTransaction {
                val current = database.weeklyLeagues().get(league.id) ?: return@withTransaction
                if (current.status == LeagueStatus.FINALIZED) return@withTransaction
                val rank = current.userRank ?: 10
                database.weeklyLeagues().upsert(
                    current.copy(status = LeagueStatus.FINALIZED, finalizedAtEpochMillis = now, updatedAtEpochMillis = now),
                )
                val band = LeagueRanking.resultBand(rank)
                createNotification(
                    GameNotificationType.WEEKLY_LEAGUE, current.id, "league:${current.id}",
                    context.getString(R.string.notification_weekly_group_title),
                    context.getString(R.string.notification_weekly_group_text, rank),
                    "challenge/weekly-group", now,
                )
            }
        }
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

    override suspend fun finalizeExpiredSeasons() {
        val now = clock.millis()
        database.gameSeasons().expired(now).forEach { season ->
            database.withTransaction {
                val current = database.gameSeasons().get(season.id) ?: return@withTransaction
                if (current.status == SeasonStatus.FINALIZED) return@withTransaction
                val profile = database.gamePlayerProfile().get() ?: return@withTransaction
                val matches = database.dailyMatches().inRange(
                    Instant.ofEpochMilli(current.startedAtEpochMillis).atZone(zone).toLocalDate().toString(),
                    Instant.ofEpochMilli(current.endedAtEpochMillis).atZone(zone).toLocalDate().toString(),
                ).filter { it.status == MatchStatus.FINALIZED }
                database.gameSeasons().upsert(
                    current.copy(
                        endRating = profile.rating,
                        highestRankTier = profile.rankTier,
                        highestRankDivision = profile.rankDivision,
                        wins = matches.count { it.outcome == MatchOutcome.WIN },
                        losses = matches.count { it.outcome == MatchOutcome.LOSS },
                        draws = matches.count { it.outcome == MatchOutcome.DRAW },
                        totalEligibleSteps = matches.sumOf { it.eligibleUserSteps },
                        bestWinStreak = profile.bestWinStreak,
                        status = SeasonStatus.FINALIZED,
                        rewardClaimed = true,
                        updatedAtEpochMillis = now,
                    ),
                )
                createNotification(
                    GameNotificationType.SEASON, current.id, "season:${current.id}",
                    context.getString(R.string.notification_monthly_record_title),
                    context.getString(R.string.notification_monthly_record_text),
                    "challenge/monthly-record", now,
                )
            }
        }
    }

    override suspend fun evaluateAchievements() {
        val profile = database.gamePlayerProfile().get() ?: return
        val now = clock.millis()
        val daily = database.daily().recentNow(40)
        val finalizedMatches = database.dailyMatches().recentNow(50).filter { it.status == MatchStatus.FINALIZED }
        val todayDaily = database.daily().get(today.toString(), zone.id)
        val todayEligible = competitiveSummary(
            todayDaily,
            database.competitiveIntegritySegments().forDate(today.toString(), zone.id),
            stepCalculator,
        ).eligibleSteps
        val measuredDays = daily.filter { it.stepsQuality != DataQuality.UNKNOWN && it.steps > 0 }
        val definitions = buildList {
            val bestEligibleDaily = maxOf(
                finalizedMatches.maxOfOrNull { it.eligibleUserSteps } ?: 0,
                todayEligible,
            )
            if (bestEligibleDaily >= 1_000) add("first_1000_steps" to bestEligibleDaily)
            if (consecutiveDays(measuredDays.map { it.localDate }) >= 3) add("three_day_streak" to 3L)
            if (consecutiveDays(measuredDays.map { it.localDate }) >= 7) add("seven_day_streak" to 7L)
            if (profile.wins >= 1) add("first_win" to profile.wins.toLong())
            if (profile.bestWinStreak >= 3) add("three_wins" to profile.bestWinStreak.toLong())
            if (profile.bestWinStreak >= 5) add("five_wins" to profile.bestWinStreak.toLong())
            if (shouldUnlockDailyTenThousand(bestEligibleDaily)) {
                add("daily_10000_steps" to bestEligibleDaily)
            }
            if (bestEligibleDaily >= 20_000) add("daily_20000_steps" to bestEligibleDaily)
            if (profile.rankTier != RankTier.BRONZE) add("silver_promotion" to profile.rating.toLong())
            if (finalizedMatches.count { it.seasonId == seasonId(today) } >= 10) {
                add("season_10_matches" to finalizedMatches.count { it.seasonId == seasonId(today) }.toLong())
            }
            val noRecovery = daily.filter { it.externalRecoveredSteps == 0L && it.stepsQuality == DataQuality.MEASURED }
            if (consecutiveDays(noRecovery.map { it.localDate }) >= 7) add("seven_days_no_recovery" to 7L)
            if (daily.any { it.externalRecoveredSteps > 0 }) {
                add("gap_recovery_success" to 1L)
            }
        }
        definitions.forEach { (id, progress) ->
            val inserted = database.achievementUnlocks().insert(
                AchievementUnlockEntity(id, now, progress, seasonId(today), false),
            )
            if (inserted != -1L) {
                createNotification(
                    GameNotificationType.ACHIEVEMENT, id, "achievement:$id",
                    context.getString(R.string.notification_achievement_title),
                    achievementTitle(id), "achievements", now,
                )
            }
        }
    }

    private suspend fun createNotification(
        type: GameNotificationType,
        sourceId: String,
        key: String,
        title: String,
        message: String,
        route: String,
        now: Long,
    ) {
        database.gameNotificationEvents().insert(
            GameNotificationEventEntity(
                id = "game-event-${key.hashCode().toUInt().toString(16)}",
                type = type,
                sourceId = sourceId,
                deduplicationKey = key,
                title = title,
                message = message,
                destinationRoute = route,
                createdAtEpochMillis = now,
                notBeforeEpochMillis = GameNotificationDispatcher.nextAllowedEpochMillis(clock),
                deliveredAtEpochMillis = null,
                acknowledged = false,
            ),
        )
    }

    private fun consecutiveDays(dateStrings: List<String>): Int {
        val dates = dateStrings.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.distinct().sorted()
        var best = 0
        var current = 0
        var previous: LocalDate? = null
        dates.forEach { date ->
            current = if (previous?.plusDays(1) == date) current + 1 else 1
            best = maxOf(best, current)
            previous = date
        }
        return best
    }

    private fun achievementTitle(id: String): String {
        return GameNotificationPresentation.achievementTitle(id, context::getString)
    }

    private fun MatchOutcome.notificationName() =
        GameNotificationPresentation.matchOutcomeName(this, context::getString)

    private fun seasonId(date: LocalDate) = "%04d-%02d".format(date.year, date.monthValue)
}

internal fun competitiveSummary(
    daily: DailyActivityRecordEntity?,
    integritySegments: List<CompetitiveIntegritySegmentEntity> = emptyList(),
    calculator: CompetitiveStepCalculator = CompetitiveStepCalculator(),
): CompetitiveStepSummary {
    if (daily == null) return calculator.calculate(CompetitiveStepInput())
    val steps = daily.steps.coerceAtLeast(0)
    val recovered = daily.externalRecoveredSteps.coerceAtLeast(0)
    val classifiedTotal = saturatedNonNegativeSum(integritySegments.map { it.totalSteps }).coerceAtMost(steps)
    val unclassifiedMeasured = (steps - classifiedTotal).coerceAtLeast(0)
    val rawEligible = saturatedNonNegativeSum(integritySegments.map { it.eligibleSteps })
    val rawRestricted = saturatedNonNegativeSum(integritySegments.map { it.restrictedSteps })
    val rawExcluded = saturatedNonNegativeSum(integritySegments.map { it.excludedSteps })
    val scaled = if (rawEligible == 0L && rawRestricted == 0L && rawExcluded == 0L) {
        listOf(classifiedTotal, 0L, 0L)
    } else largestRemainder(classifiedTotal, listOf(rawExcluded, rawRestricted, rawEligible))
    val integrityExcluded = scaled[0]
    val integrityRestricted = scaled[1]
    val integrityEligible = scaled[2]
    val integrityReasons = integritySegments.flatMap { segment ->
        segment.reasons.split(',').mapNotNull { reason ->
            runCatching { CompetitiveStepRestrictionReason.valueOf(reason) }.getOrNull()
        }
    }.toSet()
    val input = when (daily.stepsQuality) {
        DataQuality.MEASURED -> CompetitiveStepInput(
            measured = unclassifiedMeasured + integrityEligible,
            recovered = recovered,
            integrityRestricted = integrityRestricted,
            integrityExcluded = integrityExcluded,
            integrityReasons = integrityReasons,
        )
        DataQuality.RECOVERED -> CompetitiveStepInput(recovered = safeStepSum(steps, recovered))
        DataQuality.ESTIMATED -> CompetitiveStepInput(estimated = steps, recovered = recovered)
        DataQuality.UNKNOWN -> CompetitiveStepInput(unknown = steps, recovered = recovered)
        DataQuality.MIXED -> CompetitiveStepInput(measured = steps, recovered = recovered)
    }
    return calculator.calculate(input)
}

private fun goalBand(steps: Long?): Int = when (steps?.coerceAtLeast(0) ?: 0L) {
    in 0..4_999 -> 0
    in 5_000..6_999 -> 1
    in 7_000..8_999 -> 2
    in 9_000..10_999 -> 3
    in 11_000..12_999 -> 4
    in 13_000..19_999 -> 5
    else -> 6
}

private fun safeStepSum(first: Long, second: Long): Long =
    if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second

private fun saturatedNonNegativeSum(values: Iterable<Long>): Long =
    values.fold(0L) { total, value ->
        val safeValue = value.coerceAtLeast(0)
        if (Long.MAX_VALUE - total < safeValue) Long.MAX_VALUE else total + safeValue
    }

internal fun shouldUnlockDailyTenThousand(eligibleSteps: Long): Boolean = eligibleSteps >= 10_000
