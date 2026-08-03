package com.lazyapps.steparena.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.core.database.entity.*
import com.lazyapps.steparena.core.database.model.*
import com.lazyapps.steparena.game.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

/** QA-only real-cloud round trip. Every row is namespaced and outside the current day. */
@RunWith(AndroidJUnit4::class)
class CloudBackupV2QaTest {
    private val prefix = "qa-v2-20260803"
    private val date = "2020-01-02"
    private val zone = "Asia/Tokyo"

    @Test fun nonEmptyV2BackupAndRestoreIsIdempotentAndCounterSafe() = runBlocking {
        assumeTrue("QA-only fixture", BuildConfig.FLAVOR == "qa")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StepArenaApplication
        val db = app.database
        val processingBeforeCleanup = db.processingState().get()
        val todayForCleanup = LocalDate.now(app.clock).toString()
        val todayBeforeCleanup = db.daily().all().filter { it.localDate == todayForCleanup }
        deleteFixture(db)
        assertEquals(processingBeforeCleanup, db.processingState().get())
        assertEquals(todayBeforeCleanup, db.daily().all().filter { it.localDate == todayForCleanup })
        println("QA_PROTECTION todaySteps=${todayBeforeCleanup.sumOf { it.steps }} lastCounter=${processingBeforeCleanup?.lastCounterValue}")
        val user = FirebaseAuth.getInstance().currentUser
        assumeTrue("QA Google-linked authentication is required", user != null && !user.isAnonymous &&
            user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID })
        val processingBefore = db.processingState().get()
        val today = LocalDate.now(app.clock).toString()
        val todayBefore = db.daily().all().filter { it.localDate == today }
        seed(db)

        val backup = app.cloudBackupRepository.backupNow()
        assertTrue("QA Google-linked authentication and cloud backup are required: $backup", backup is BackupResult.Success)
        deleteFixture(db)
        val preview = app.cloudRestoreRepository.check()
        assertEquals(RestoreStatus.AVAILABLE, preview.status)
        assertEquals(2, preview.preview?.metadata?.schemaVersion)
        val first = app.cloudRestoreRepository.restoreConfirmed()
        assertTrue(first is RestoreResult.Success)
        assertFixturePresent(db)
        val counts = fixtureCounts(db)
        val second = app.cloudRestoreRepository.restoreConfirmed()
        assertTrue(second is RestoreResult.Success)
        assertEquals(counts, fixtureCounts(db))
        assertEquals(processingBefore, db.processingState().get())
        assertEquals(todayBefore, db.daily().all().filter { it.localDate == today })
    }

    private suspend fun seed(db: com.lazyapps.steparena.core.database.StepArenaDatabase) {
        val q = DataQuality.MEASURED
        db.daily().upsert(DailyActivityRecordEntity("$prefix-daily", date, zone, 120, 0, q, 0, 0, 84.0, 90, 5.0, 3.36, q, q, q, q, q, 1, 1, true, 1577977200000, 1577970000000, 1577977200000))
        db.hourly().upsert(HourlyActivityRecordEntity("$prefix-hour", date, 12, zone, 32400, 1577934000000, 1577937600000, 120, 84.0, 90, 5.0, 3.36, q, q, q, q, q, 1577934100000, 1577934190000, 2, 0, 0, .7, 60.0, 1, 1577934000000, 1577934190000))
        db.sessions().upsert(WalkingSessionEntity("$prefix-session", date, zone, 1577934000000, 1577934190000, 120, 84.0, 90, 190, 0, 5.0, 3.36, 1.59, WalkingSessionType.MANUAL_WALK, WalkingSessionStatus.COMPLETED, q, q, q, q, q, null, 1577934190000, null, true, 2, 0, 0, 1577934000000, 1577934190000))
        db.dailyMatches().insert(DailyMatchEntity("$prefix-match", date, zone, "$prefix-season", MatchType.DAILY, MatchStatus.FINALIZED, MatchOutcome.WIN, "$prefix-opponent", "QA Opponent", "walk", RankTier.BRONZE, 3, OpponentPersonality.STEADY, 100, 120, 100, 10, 10, "QA_FIXTURE", CompetitiveStepQuality.RESTRICTED, 1000, 10, 1010, "QA", 1577977200000, 1577934000000, 1577977200000))
        db.weeklyLeagues().upsert(WeeklyLeagueEntity("$prefix-league", "2019-12-30", "2020-01-05", zone, LeagueStatus.FINALIZED, 12, 1, "[]", 1578236400000, 1577674800000, 1578236400000))
        db.weeklyLeagueParticipants().upsertAll(listOf(WeeklyLeagueParticipantEntity("$prefix-league", "$prefix-player", "QA Player", "walk", 12, 120, 1, true, false, 1577674800000, 1578236400000)))
        db.gameSeasons().upsert(GameSeasonEntity("$prefix-season", 1577674800000, 1578236400000, 1000, 1010, RankTier.BRONZE, 3, 1, 0, 0, 100, 1, SeasonStatus.FINALIZED, false, 1577674800000, 1578236400000))
        db.achievementUnlocks().insert(AchievementUnlockEntity("$prefix-achievement", 1577977200000, 120, "$prefix-season", false))
        db.competitiveIntegritySegments().upsert(CompetitiveIntegritySegmentEntity("$prefix-integrity", date, zone, 1577934000000, 1577934190000, 120, 100, 10, 10, CompetitiveIntegrityAssessment.LIMITED, "QA_FIXTURE", 3, 1577934000000))
    }

    private fun deleteFixture(db: com.lazyapps.steparena.core.database.StepArenaDatabase) {
        val sql = db.openHelper.writableDatabase
        listOf(
            "DELETE FROM competitive_integrity_segments WHERE id='$prefix-integrity'",
            "DELETE FROM achievement_unlocks WHERE achievementId='$prefix-achievement'",
            "DELETE FROM weekly_league_participants WHERE leagueId='$prefix-league'",
            "DELETE FROM game_seasons WHERE id='$prefix-season'",
            "DELETE FROM weekly_leagues WHERE id='$prefix-league'",
            "DELETE FROM daily_matches WHERE id='$prefix-match'",
            "DELETE FROM walking_sessions WHERE id='$prefix-session'",
            "DELETE FROM hourly_activity_records WHERE id='$prefix-hour'",
            "DELETE FROM daily_activity_records WHERE id='$prefix-daily'",
        ).forEach(sql::execSQL)
    }

    private suspend fun assertFixturePresent(db: com.lazyapps.steparena.core.database.StepArenaDatabase) {
        assertNotNull(db.daily().get(date, zone)); assertNotNull(db.hourly().byId("$prefix-hour"))
        assertNotNull(db.sessions().get("$prefix-session")); assertNotNull(db.dailyMatches().get("$prefix-match"))
        assertNotNull(db.weeklyLeagues().get("$prefix-league")); assertEquals(1, db.weeklyLeagueParticipants().getForLeague("$prefix-league").size)
        assertNotNull(db.gameSeasons().get("$prefix-season")); assertNotNull(db.achievementUnlocks().get("$prefix-achievement"))
        assertNotNull(db.competitiveIntegritySegments().byId("$prefix-integrity"))
    }

    private suspend fun fixtureCounts(db: com.lazyapps.steparena.core.database.StepArenaDatabase) = listOf(
        db.daily().all().count { it.id.startsWith(prefix) }, db.hourly().all().count { it.id.startsWith(prefix) },
        db.sessions().all().count { it.id.startsWith(prefix) }, db.weeklyLeagueParticipants().getForLeague("$prefix-league").size,
        if (db.dailyMatches().get("$prefix-match") == null) 0 else 1, if (db.gameSeasons().get("$prefix-season") == null) 0 else 1,
    )
}
