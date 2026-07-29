package com.lazyapps.steparena.recovery

import java.time.Instant

class FakeExternalActivityDataSource(
    var currentAvailability: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE,
    var permissions: Set<String> = emptySet(),
    var records: List<ExternalStepSegment> = emptyList(),
) : ExternalActivityDataSource {
    override suspend fun availability() = currentAvailability
    override suspend fun grantedPermissions() = permissions
    override suspend fun readSteps(start: Instant, end: Instant) =
        ExternalStepResult(records.mapNotNull { it.clippedTo(start, end) })
}
