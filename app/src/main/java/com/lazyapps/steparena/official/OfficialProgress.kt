package com.lazyapps.steparena.official

import com.google.firebase.functions.FirebaseFunctions
import com.lazyapps.steparena.activity.ActivityRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OfficialProgressSnapshot(
    val localDate: LocalDate,
    val timezone: String,
    val totalSteps: Long,
    val eligibleSteps: Long,
    val restrictedSteps: Long,
    val excludedSteps: Long,
    val integrityVersion: Int,
    val sourceRevision: String,
)

data class SubmitOfficialProgressResult(val status: String, val officialSteps: Long?)

class OfficialProgressRemoteDataSource(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("us-central1"),
) {
    suspend fun submit(snapshot: OfficialProgressSnapshot, requestId: String): SubmitOfficialProgressResult {
        val data = mapOf(
            "localDate" to snapshot.localDate.toString(), "timezone" to snapshot.timezone,
            "totalSteps" to snapshot.totalSteps, "eligibleSteps" to snapshot.eligibleSteps,
            "restrictedSteps" to snapshot.restrictedSteps, "excludedSteps" to snapshot.excludedSteps,
            "integrityVersion" to snapshot.integrityVersion, "sourceRevision" to snapshot.sourceRevision,
            "requestId" to requestId,
        )
        val result = suspendCancellableCoroutine { continuation ->
            val task = functions.getHttpsCallable("submitOfficialProgress").call(data)
            task.addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
        val body = result.data as? Map<*, *> ?: error("invalid_function_response")
        return SubmitOfficialProgressResult(
            status = body["status"] as? String ?: error("missing_status"),
            officialSteps = (body["officialSteps"] as? Number)?.toLong(),
        )
    }
}

class OfficialProgressRepository(
    private val activityRepository: ActivityRepository,
    private val remote: OfficialProgressRemoteDataSource = OfficialProgressRemoteDataSource(),
) {
    suspend fun submitToday(): SubmitOfficialProgressResult {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        return remote.submit(activityRepository.officialProgressSnapshot(date, zone), UUID.randomUUID().toString())
    }
}
