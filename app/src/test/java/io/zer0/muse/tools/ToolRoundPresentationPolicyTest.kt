package io.zer0.muse.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRoundPresentationPolicyTest {
    @Test
    fun `only the first provider round exposes reasoning`() {
        assertTrue(ToolRoundPresentationPolicy.exposeReasoning(1))
        assertFalse(ToolRoundPresentationPolicy.exposeReasoning(2))
        assertFalse(ToolRoundPresentationPolicy.exposeReasoning(3))
    }

    @Test
    fun `invalid zero round does not expose reasoning`() {
        assertFalse(ToolRoundPresentationPolicy.exposeReasoning(0))
    }
}
