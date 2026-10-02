package io.zer0.muse.automation.vdisplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualDisplayLeaseRegistryTest {

    @Test
    fun `display is destroyed only after the final workflow releases its lease`() {
        val leases = VirtualDisplayLeaseRegistry()

        assertTrue(leases.acquire("run-a", 42))
        assertTrue(leases.acquire("run-b", 42))
        assertEquals(42, leases.displayFor("run-a"))
        assertEquals(42, leases.displayFor("run-b"))

        assertFalse(leases.release("run-a", 42))
        assertEquals(42, leases.displayFor("run-b"))
        assertTrue(leases.release("run-b", 42))
        assertEquals(null, leases.displayFor("run-b"))
    }

    @Test
    fun `refreshed display keeps old id as an alias only for its owning run`() {
        val leases = VirtualDisplayLeaseRegistry()
        leases.acquire("run-a", 42)
        leases.acquire("run-b", 42)

        leases.acquire("run-a", 43)

        assertEquals(43, leases.displayFor("run-a"))
        assertTrue(leases.isKnownDisplayId("run-a", 42))
        assertTrue(leases.isKnownDisplayId("run-a", 43))
        assertFalse(leases.isKnownDisplayId("run-b", 43))
        assertEquals(42, leases.displayFor("run-b"))

        assertTrue(leases.release("run-a", 43))
        assertFalse(leases.isKnownDisplayId("run-a", 42))
        assertTrue(leases.isKnownDisplayId("run-b", 42))
    }
}
