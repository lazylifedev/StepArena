package com.lazyapps.steparena.qa.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QaTelemetrySanitizerTest {
    @Test fun productionIsDisabledAndQaIsEnabled() {
        assertFalse(QaTelemetryPolicy.isEnabled("production"))
        assertEquals(true, QaTelemetryPolicy.isEnabled("qa"))
    }

    @Test fun removesSecretsAndSensitiveIdentifiers() {
        val result = QaTelemetrySanitizer.sanitize(
            mapOf("uid" to "raw", "email" to "a@example.test", "challengeId" to "raw", "steps" to 10L),
        )
        assertFalse(result.containsKey("uid"))
        assertFalse(result.containsKey("email"))
        assertFalse(result.containsKey("challengeId"))
        assertEquals(10L, result["steps"])
    }

    @Test fun redactsSensitiveValues() {
        assertEquals("[REDACTED]", QaTelemetrySanitizer.sanitize(mapOf("message" to "Bearer abc"))["message"])
    }
}
