package com.zidee.ress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test
    fun cadenceAndClockChanges() {
        assertTrue(shouldCheck(100, 0))
        assertFalse(shouldCheck(CHECK_INTERVAL, 1))
        assertTrue(shouldCheck(CHECK_INTERVAL + 1, 1))
        assertTrue(shouldCheck(1, 2))
    }

    @Test
    fun newerVersionsOnlyAndDismissalIsPerVersion() {
        assertNull(availableUpdate(1, "1.0.0", 1, 0))
        assertNull(availableUpdate(2, "1.1", 1, 2))
        assertNull(availableUpdate(3, " ", 1, 2))
        assertNull(availableUpdate(3, "x".repeat(101), 1, 2))
        assertEquals(UpdateVersion(3, "1.2"), availableUpdate(3, "1.2", 1, 2))
    }
}
