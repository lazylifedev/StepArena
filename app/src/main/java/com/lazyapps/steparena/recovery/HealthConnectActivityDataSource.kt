package com.lazyapps.steparena.recovery

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

class HealthConnectActivityDataSource(private val context: Context) : ExternalActivityDataSource {
    private val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)

    override suspend fun availability(): HealthConnectAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return HealthConnectAvailability.NOT_SUPPORTED
        }
        return runCatching {
            when (HealthConnectClient.getSdkStatus(context)) {
                HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    HealthConnectAvailability.UPDATE_REQUIRED
                HealthConnectClient.SDK_UNAVAILABLE ->
                    if (Build.VERSION.SDK_INT >= 34) HealthConnectAvailability.NOT_SUPPORTED
                    else HealthConnectAvailability.PROVIDER_NOT_INSTALLED
                else -> HealthConnectAvailability.UNKNOWN
            }
        }.getOrDefault(HealthConnectAvailability.UNKNOWN)
    }

    override suspend fun grantedPermissions(): Set<String> {
        if (availability() != HealthConnectAvailability.AVAILABLE) return emptySet()
        return runCatching {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())
    }

    override suspend fun readSteps(start: Instant, end: Instant): ExternalStepResult {
        if (!start.isBefore(end)) return ExternalStepResult(error = ExternalReadError.API_FAILURE)
        if (availability() != HealthConnectAvailability.AVAILABLE) {
            return ExternalStepResult(error = ExternalReadError.NOT_AVAILABLE)
        }
        if (stepsPermission !in grantedPermissions()) {
            return ExternalStepResult(error = ExternalReadError.PERMISSION_REQUIRED)
        }
        return runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            val records = mutableListOf<StepsRecord>()
            var token: String? = null
            do {
                val page = client.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        pageToken = token,
                    ),
                )
                records += page.records
                token = page.pageToken
            } while (token != null)
            ExternalStepResult(
                records.mapNotNull { record ->
                    if (record.metadata.dataOrigin.packageName == context.packageName) null else {
                        ExternalStepSegment(
                            start = record.startTime,
                            end = record.endTime,
                            steps = record.count,
                            dataOriginPackage = record.metadata.dataOrigin.packageName,
                            recordId = record.metadata.id.takeIf(String::isNotBlank),
                            lastModifiedAt = record.metadata.lastModifiedTime,
                            recordingMethod = ExternalRecordingMethod.UNKNOWN,
                        ).clippedTo(start, end)
                    }
                },
            )
        }.getOrElse { ExternalStepResult(error = ExternalReadError.API_FAILURE) }
    }

    fun requiredPermissions(): Set<String> = setOf(stepsPermission)
}
