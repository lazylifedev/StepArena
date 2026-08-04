package com.lazyapps.steparena.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.Timestamp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.GoogleAuthProvider
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.tracking.TrackingStateRepository
import java.security.MessageDigest
import java.time.LocalDate
import java.util.TreeMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** QA-only real-cloud check. Uses current local data and never writes the legacy v1 paths. */
@RunWith(AndroidJUnit4::class)
class VersionedCloudBackupQaTest {
    @Test fun explicitV2BackupPreservesV1AndRestoresIdempotently() = runBlocking {
        assertEquals("qa", BuildConfig.FLAVOR)
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StepArenaApplication
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val user = requireNotNull(auth.currentUser)
        assertTrue(user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID })
        val uid = requireNotNull(FirebaseBackupIdentityProvider(auth).googleLinkedUid())
        val appCheckToken = FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
        assertTrue("App Check force-refresh token is required", appCheckToken.token.isNotBlank())
        println("QA_BACKUP_PREFLIGHT authNonNull=true googleProvider=true appCheckForceRefresh=true uidSuffix=${uid.takeLast(4)}")
        val firestore = FirebaseFirestore.getInstance()
        val legacy = firestore.collection("userBackups").document(uid)
        val before = legacyFingerprint(legacy)
        assertTrue("Existing schema v1 backup is required", before.entries.isNotEmpty())

        val today = LocalDate.now(app.clock).toString()
        val dailyBefore = app.database.daily().all().filter { it.localDate == today }
        val processingBefore = app.database.processingState().get()
        val trackingBefore = TrackingStateRepository(app).current()
        val finalizedChallengeCountBefore = app.database.dailyMatches().finalizedForBackup().size

        val versionedReference = legacy.collection("versions").document("v2")
        val v2Before = versionedReference.get(Source.SERVER).await()
        val physicalChallengeBefore = versionedReference.collection("challengeResults").get(Source.SERVER).await().documents
        val legacyUntaggedChallengeBefore = physicalChallengeBefore.count { !it.data.orEmpty().containsKey("backupGeneration") }
        val cloudGenerationBefore = if (v2Before.exists()) requireNotNull(v2Before.getLong("backupGeneration")) else 0L
        if (v2Before.exists()) assertEquals("complete", v2Before.getString("backupStatus"))
        val startupCheck = app.cloudRestoreRepository.check()
        if (startupCheck.status == RestoreStatus.CHECKING) {
            withTimeout(30_000) {
                app.cloudRestoreRepository.state.first { it.status != RestoreStatus.CHECKING }
            }
        }
        val backup = app.cloudBackupRepository.backupNow()
        assertTrue("Explicit versioned backup must succeed: $backup", backup is BackupResult.Success)
        assertEquals(cloudGenerationBefore + 1L, (backup as BackupResult.Success).generation)
        val after = legacyFingerprint(legacy)
        assertEquals(before, after)

        val v2Root = versionedReference.get(Source.SERVER).await()
        assertTrue(v2Root.exists())
        assertEquals(2L, v2Root.getLong("schemaVersion"))
        assertEquals(1L, v2Root.getLong("childGenerationVersion"))
        assertEquals("complete", v2Root.getString("backupStatus"))
        val currentGeneration = requireNotNull(v2Root.getLong("backupGeneration"))
        val v2Counts = V2_COLLECTIONS.associateWith {
            if (it == "settings") {
                val settings = versionedReference.collection(it).document("current").get(Source.SERVER).await()
                if (settings.exists() && settings.getLong("backupGeneration") == currentGeneration) 1 else 0
            } else versionedReference.collection(it).whereEqualTo("backupGeneration", currentGeneration).get(Source.SERVER).await().size()
        }
        val physicalChallengeAfter = versionedReference.collection("challengeResults").get(Source.SERVER).await().documents
        assertEquals(legacyUntaggedChallengeBefore, physicalChallengeAfter.count { !it.data.orEmpty().containsKey("backupGeneration") })
        assertEquals(v2Root.getLong("challengeResultCount")?.toInt(), v2Counts.getValue("challengeResults"))
        assertEquals(finalizedChallengeCountBefore, v2Counts.getValue("challengeResults"))
        assertEquals(1, v2Counts.getValue("settings"))
        val v2Count = v2Counts.values.sum()
        assertTrue("v2 backup must contain data", v2Count > 0)

        val preview = app.cloudRestoreRepository.check()
        assertEquals("preview=$preview", RestoreStatus.AVAILABLE, preview.status)
        assertEquals(2, preview.preview?.metadata?.schemaVersion)
        val first = app.cloudRestoreRepository.restoreConfirmed()
        val second = app.cloudRestoreRepository.restoreConfirmed()
        assertTrue(first is RestoreResult.Success)
        assertTrue(second is RestoreResult.Success)
        val secondSuccess = second as RestoreResult.Success
        assertEquals(0, secondSuccess.added)

        assertEquals(dailyBefore, app.database.daily().all().filter { it.localDate == today })
        assertEquals(processingBefore, app.database.processingState().get())
        assertEquals(trackingBefore, TrackingStateRepository(app).current())
        val finalUser = requireNotNull(auth.currentUser)
        assertTrue(finalUser.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID })
        assertTrue(FirebaseAppCheck.getInstance().getAppCheckToken(true).await().token.isNotBlank())
        println("QA_VERSIONED_BACKUP uidSuffix=${uid.takeLast(4)} v1Documents=${before.entries.size} " +
            "v1Hash=${before.digest} v2Documents=$v2Count cloudGenerationBefore=$cloudGenerationBefore " +
            "cloudGenerationAfter=${v2Root.getLong("backupGeneration")} authMaintained=true appCheckPost=true " +
            "challengePhysical=${physicalChallengeAfter.size} challengeCurrent=${v2Counts.getValue("challengeResults")} " +
            "legacyUntaggedChallenge=${physicalChallengeAfter.count { !it.data.orEmpty().containsKey("backupGeneration") }} " +
            "todaySteps=${dailyBefore.sumOf { it.steps }} today_steps=${trackingBefore.accumulatedTodaySteps} " +
            "lastCounter=${processingBefore?.lastCounterValue} secondRestoreAdded=${secondSuccess.added}")
    }

    private suspend fun legacyFingerprint(root: DocumentReference): Fingerprint {
        val entries = mutableListOf<String>()
        val rootSnapshot = root.get(Source.SERVER).await()
        if (rootSnapshot.exists()) entries += "root=" + canonical(rootSnapshot.data.orEmpty())
        V1_COLLECTIONS.forEach { collection ->
            root.collection(collection).get(Source.SERVER).await().documents.sortedBy { it.id }.forEach { document ->
                entries += "$collection/${document.id}=" + canonical(document.data.orEmpty())
            }
        }
        return Fingerprint(entries, sha256(entries.joinToString("\n")))
    }

    private fun canonical(value: Any?): String = when (value) {
        null -> "null"
        is Map<*, *> -> TreeMap(value.entries.associate { it.key.toString() to canonical(it.value) }).entries.joinToString(",", "{", "}") { "${it.key}:${it.value}" }
        is List<*> -> value.joinToString(",", "[", "]") { canonical(it) }
        is Timestamp -> "timestamp:${value.seconds}:${value.nanoseconds}"
        else -> "${value::class.java.name}:$value"
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private data class Fingerprint(val entries: List<String>, val digest: String)

    private companion object {
        val V1_COLLECTIONS = listOf("daily", "hourly", "sessions", "challengeResults", "leagueHistory", "achievements", "settings")
        val V2_COLLECTIONS = listOf("daily", "hourly", "sessions", "challengeResults", "leagueHistory", "leagueParticipants", "seasonHistory", "integritySegments", "achievements", "settings")
    }
}
