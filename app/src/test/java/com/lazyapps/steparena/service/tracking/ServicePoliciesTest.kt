package com.lazyapps.steparena.service.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicePoliciesTest {
    @Test fun staleSessionAction_isRejected() {
        assertFalse(isCurrentSessionRequest("old", "new"))
        assertTrue(isCurrentSessionRequest("new", "new"))
        assertTrue(isCurrentSessionRequest(null, "new"))
    }
}
