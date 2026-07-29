package com.lazyapps.steparena.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserBodyProfileValidatorTest {
    private val validator = DefaultUserBodyProfileValidator()

    @Test fun acceptsEmptyAndCommaDecimal() {
        val result = validator.validate("", "60,5", "", true)
        assertTrue(result.isValid)
        assertEquals(60.5, result.profile?.weightKg)
    }

    @Test fun rejectsEachOutOfRangeValue() {
        val result = validator.validate("99", "301", "201", false)
        assertFalse(result.isValid)
        assertTrue(result.heightError)
        assertTrue(result.weightError)
        assertTrue(result.stepLengthError)
    }
}
