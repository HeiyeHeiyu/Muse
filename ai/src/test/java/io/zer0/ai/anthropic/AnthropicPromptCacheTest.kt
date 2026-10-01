package io.zer0.ai.anthropic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnthropicPromptCacheTest {

    @Test
    fun `cache breakpoint is placed on the last stable system block`() {
        assertNull(anthropicCacheBreakpointIndex(0))
        assertEquals(0, anthropicCacheBreakpointIndex(1))
        assertEquals(0, anthropicCacheBreakpointIndex(2))
        assertEquals(1, anthropicCacheBreakpointIndex(3))
    }
}
