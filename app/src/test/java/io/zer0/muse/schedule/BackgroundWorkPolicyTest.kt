package io.zer0.muse.schedule

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundWorkPolicyTest {

    @Test
    fun `missing schedule setting fails closed`() {
        assertFalse(scheduleWorkEnabledOrFalse(null))
    }

    @Test
    fun `missing settings repository fails closed`() {
        assertFalse(scheduleWorkEnabledFromRepository(available = false, value = true))
    }

    @Test
    fun `explicit schedule setting is preserved`() {
        assertTrue(scheduleWorkEnabledOrFalse(true))
        assertFalse(scheduleWorkEnabledOrFalse(false))
    }

    @Test
    fun `daily summary notification read failure fails closed`() {
        assertFalse(dailySummaryNotificationsEnabledOrFalse(null))
    }

    @Test
    fun `schedule setting reader fails closed when flow throws`() = runTest {
        assertFalse(
            readScheduleWorkEnabledOrFalse {
                throw IllegalStateException("DataStore unavailable")
            },
        )
    }

    @Test
    fun `daily summary slot read failure does not fall back to default slots`() {
        assertTrue(
            DailySummaryWorker.slotsAfterRead(
                configured = null,
                readFailed = true,
            ).isEmpty(),
        )
    }

    @Test
    fun `explicit empty daily summary configuration keeps default slots`() {
        assertEquals(
            listOf(0, 9, 12, 21),
            DailySummaryWorker.slotsAfterRead(
                configured = emptyList(),
                readFailed = false,
            ),
        )
    }
}
