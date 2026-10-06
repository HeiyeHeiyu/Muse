package io.zer0.muse.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundServiceStopGateTest {

    @Test
    fun `only the first concurrent stop request wins until reset`() {
        val gate = ForegroundServiceStopGate()

        assertTrue(gate.tryRequest())
        assertFalse(gate.tryRequest())

        gate.reset()

        assertTrue(gate.tryRequest())
    }
}
