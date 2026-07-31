package com.lazyapps.steparena.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDisplayNamePolicyTest {
    @Test fun `blank input is stored as unset`() {
        val result = PlayerDisplayNamePolicy.validate("   ")
        assertTrue(result.isValid)
        assertNull(result.normalized)
    }

    @Test fun `surrounding whitespace is removed`() {
        assertEquals("歩子", PlayerDisplayNamePolicy.validate("  歩子  ").normalized)
    }

    @Test fun `one through twenty unicode characters are accepted`() {
        assertTrue(PlayerDisplayNamePolicy.validate("歩").isValid)
        assertTrue(PlayerDisplayNamePolicy.validate("🚶".repeat(20)).isValid)
        assertEquals(DisplayNameError.TOO_LONG, PlayerDisplayNamePolicy.validate("🚶".repeat(21)).error)
    }

    @Test fun `control characters are rejected`() {
        val result = PlayerDisplayNamePolicy.validate("歩\n子")
        assertFalse(result.isValid)
        assertEquals(DisplayNameError.CONTROL_CHARACTER, result.error)
        assertEquals(
            DisplayNameError.CONTROL_CHARACTER,
            PlayerDisplayNamePolicy.validate(" \t ").error,
        )
    }

    @Test fun `public fallback never uses an internal identifier`() {
        assertEquals("あなた", publicDisplayName(null, "あなた"))
        assertEquals("歩子", publicDisplayName("歩子", "あなた"))
    }
}
