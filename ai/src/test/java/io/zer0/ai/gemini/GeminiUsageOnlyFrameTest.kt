package io.zer0.ai.gemini

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiUsageOnlyFrameTest {

    @Test
    fun `usage-only frame completes after visible content`() {
        assertTrue(
            shouldCompleteUsageOnlyFrame(
                hasEmittedContent = true,
                candidateCount = 0,
                finished = false,
            ),
        )
    }

    @Test
    fun `usage-only frame does not complete an empty or finished stream`() {
        assertFalse(shouldCompleteUsageOnlyFrame(false, 0, false))
        assertFalse(shouldCompleteUsageOnlyFrame(true, 0, true))
        assertFalse(shouldCompleteUsageOnlyFrame(true, 1, false))
    }
}
