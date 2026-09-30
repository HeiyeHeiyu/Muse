package io.zer0.muse.automation.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationWorkflowParserTest {
    @Test
    fun `parser keeps ordered semantic steps`() {
        val result = AutomationWorkflowParser.parse(
            """
            [
              {"action":"launch","packageName":"com.android.settings"},
              {"action":"tap_text","text":"Network","maxSwipes":2,"verifyText":"Internet"},
              {"action":"read"}
            ]
            """.trimIndent(),
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("launch", "tap_text", "read"), result.getOrThrow().map { it.action })
        assertEquals(2, result.getOrThrow()[1].maxSwipes)
    }

    @Test
    fun `parser rejects empty and oversized workflows`() {
        assertTrue(AutomationWorkflowParser.parse("[]").isFailure)
        val oversized = (1..21).joinToString(prefix = "[", postfix = "]") { "{\"action\":\"read\"}" }
        assertTrue(AutomationWorkflowParser.parse(oversized).isFailure)
    }
}
