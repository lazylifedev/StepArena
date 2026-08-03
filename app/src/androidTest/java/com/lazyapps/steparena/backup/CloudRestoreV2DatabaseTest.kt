package com.lazyapps.steparena.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestore
import com.lazyapps.steparena.activity.DailyStepGoalRepository
import com.lazyapps.steparena.activity.UserProfileRepository
import com.lazyapps.steparena.core.database.StepArenaDatabase
import com.lazyapps.steparena.core.database.entity.*
import com.lazyapps.steparena.core.database.model.DataQuality
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudRestoreV2DatabaseTest {
    private lateinit var db: StepArenaDatabase
    private lateinit var repository: CloudRestoreRepository
    private val clock = Clock.fixed(Instant.parse("2026-08-03T03:00:00Z"), ZoneId.of("Asia/Tokyo"))

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StepArenaDatabase::class.java).allowMainThreadQueries().build()
        repository = CloudRestoreRepository(object : BackupIdentityProvider { override fun googleLinkedUid() = "test" },
            FirebaseFirestore.getInstance(), db, UserProfileRepository(context), DailyStepGoalRepository(context), clock, BackupOperationGate())
    }
    @After fun close() = db.close()

    @Test fun nonEmptyRestoreProtectsCurrentAndFutureAndSecondRunDoesNotDuplicate() = runBlocking {
        val snapshot = snapshot(listOf(daily("past", "2026-08-01"), daily("today", "2026-08-03"), daily("future", "2026-08-04")))
        val first = repository.applySnapshot(snapshot) as RestoreResult.Success
        assertEquals(1, first.added)
        assertNotNull(db.daily().get("2026-08-01", "Asia/Tokyo"))
        assertNull(db.daily().get("2026-08-03", "Asia/Tokyo")); assertNull(db.daily().get("2026-08-04", "Asia/Tokyo"))
        val second = repository.applySnapshot(snapshot) as RestoreResult.Success
        assertEquals(0, second.added); assertEquals(1, db.daily().count())
    }

    @Test fun transactionExceptionRollsBackEarlierRows() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER fail_hour BEFORE INSERT ON hourly_activity_records BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        val value = snapshot(listOf(daily("rollback", "2026-08-01")), listOf(hour()))
        assertThrows(Throwable::class.java) { runBlocking { repository.applySnapshot(value) } }
        assertEquals(0, db.daily().count()); assertEquals(0, db.hourly().count())
    }

    private fun snapshot(daily: List<DailyActivityRecordEntity>, hourly: List<HourlyActivityRecordEntity> = emptyList()) = RestoreSnapshot(
        RestoreMetadata(1, clock.instant(), 2, emptyMap()), emptyList(), null, daily, hourly)

    private fun daily(id: String, date: String): DailyActivityRecordEntity { val q=DataQuality.MEASURED; return DailyActivityRecordEntity(id,date,"Asia/Tokyo",10,0,q,0,0,7.0,10,1.0,2.5,q,q,q,q,q,1,0,true,1,1,1) }
    private fun hour(): HourlyActivityRecordEntity { val q=DataQuality.MEASURED; return HourlyActivityRecordEntity("hour","2026-08-01",1,"Asia/Tokyo",32400,1,2,10,7.0,10,1.0,2.5,q,q,q,q,q,1,2,1,0,0,.7,60.0,1,1,2) }
}
