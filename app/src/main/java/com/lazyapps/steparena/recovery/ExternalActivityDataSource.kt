package com.lazyapps.steparena.recovery

import java.time.Instant

interface ExternalActivityDataSource {
    suspend fun availability(): HealthConnectAvailability
    suspend fun grantedPermissions(): Set<String>
    suspend fun readSteps(start: Instant, end: Instant): ExternalStepResult
}

class NoOpExternalActivityDataSource : ExternalActivityDataSource {
    override suspend fun availability() = HealthConnectAvailability.NOT_SUPPORTED
    override suspend fun grantedPermissions() = emptySet<String>()
    override suspend fun readSteps(start: Instant, end: Instant) =
        ExternalStepResult(error = ExternalReadError.NOT_AVAILABLE)
}
