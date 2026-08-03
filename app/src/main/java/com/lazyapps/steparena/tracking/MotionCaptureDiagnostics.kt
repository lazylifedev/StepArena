package com.lazyapps.steparena.tracking

import com.lazyapps.steparena.game.MotionEvidenceAssessment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

data class MotionCaptureDiagnosticSnapshot(
    val gyroscopeAvailable: Boolean = false,
    val accelerationMode: String = "UNAVAILABLE",
    val capturing: Boolean = false,
    val lastAssessment: MotionEvidenceAssessment = MotionEvidenceAssessment.UNKNOWN,
    val lastEvaluatedAt: Instant? = null,
)

object MotionCaptureDiagnostics {
    private val mutable = MutableStateFlow(MotionCaptureDiagnosticSnapshot())
    val snapshot = mutable.asStateFlow()
    fun update(value: MotionCaptureDiagnosticSnapshot) { mutable.value = value }
    fun clearCapture() { mutable.value = mutable.value.copy(capturing = false) }
}
