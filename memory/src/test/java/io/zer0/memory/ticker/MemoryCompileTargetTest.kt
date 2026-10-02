package io.zer0.memory.ticker

import io.zer0.memory.compile.MemoryCompileTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryCompileTargetTest {

    @Test
    fun `notification target keeps the generating assistant and explicit space`() {
        val currentRuntimeTarget = MemoryCompileTarget(
            assistantId = "assistant-b",
            scope = "assistant-b",
            spaceId = "current-space",
        )

        val target = notificationCompileTarget(
            current = currentRuntimeTarget,
            assistantId = "assistant-a",
            spaceId = "generating-space",
        )

        assertEquals("assistant-a", target.assistantId)
        assertEquals("assistant-a", target.normalizedScope)
        assertEquals("generating-space", target.normalizedSpaceId)
    }
}
