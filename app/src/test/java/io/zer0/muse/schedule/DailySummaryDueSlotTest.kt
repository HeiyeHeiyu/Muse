package io.zer0.muse.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailySummaryDueSlotTest {

    @Test
    fun `front end catch up chooses the latest configured slot instead of fixed defaults`() {
        assertEquals(15, latestDueDailySummaryHour(currentMinutes = 16 * 60, configuredSlots = listOf(6, 15, 20)))
    }

    @Test
    fun `front end catch up skips when no configured slot has passed`() {
        assertNull(latestDueDailySummaryHour(currentMinutes = 5 * 60 + 59, configuredSlots = listOf(6, 15, 20)))
    }
}
