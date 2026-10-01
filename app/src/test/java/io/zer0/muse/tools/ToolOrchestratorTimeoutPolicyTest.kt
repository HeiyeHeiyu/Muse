package io.zer0.muse.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolOrchestratorTimeoutPolicyTest {
    @Test
    fun `ui agent deadline scales with bounded max steps and per-step vision timeout`() {
        assertEquals(
            205_000L,
            ToolExecutionTimeoutPolicy.forTool("ui_agent", """{"max_steps":3}""", TOOL_TIMEOUT_MS),
        )
        assertEquals(
            1_960_000L,
            ToolExecutionTimeoutPolicy.forTool("ui_agent", """{"max_steps":30}""", TOOL_TIMEOUT_MS),
        )
    }

    @Test
    fun `automation workflow deadline sums per-step waits and node-script limits`() {
        val arguments = """{"steps":[{"action":"tap_text"},{"action":"node_script","timeoutMs":180000},{"action":"virtual_wait","durationMs":8000}]}"""

        assertEquals(238_000L, ToolExecutionTimeoutPolicy.forTool("automation_workflow", arguments, TOOL_TIMEOUT_MS))
    }

    @Test
    fun `unknown or malformed tools keep injected base timeout`() {
        assertEquals(15_000L, ToolExecutionTimeoutPolicy.forTool("echo", "{}", 15_000L))
        assertEquals(15_000L, ToolExecutionTimeoutPolicy.forTool("ui_agent", "{bad", 15_000L))
    }
}
