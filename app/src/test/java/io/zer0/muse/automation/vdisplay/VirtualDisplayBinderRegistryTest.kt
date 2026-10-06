package io.zer0.muse.automation.vdisplay

import android.os.Binder
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualDisplayBinderRegistryTest {

    @After
    fun tearDown() {
        VirtualDisplayBinderRegistry.clearHandoffToken()
        VirtualDisplayBinderRegistry.update(null)
    }

    @Test
    fun `binder handoff rejects missing or stale token`() {
        val binder = Binder()
        VirtualDisplayBinderRegistry.setExpectedHandoffToken("current")

        assertFalse(VirtualDisplayBinderRegistry.accept(binder, null))
        assertFalse(VirtualDisplayBinderRegistry.accept(binder, "stale"))
        assertTrue(VirtualDisplayBinderRegistry.accept(binder, "current"))
    }
}
