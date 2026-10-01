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
              {"action":"read"},
              {"action":"virtual_tap","displayId":42,"x":120,"y":640},
              {"action":"virtual_read"},
              {"action":"node_script","code":"console.log('safe')","timeoutMs":45000}
            ]
            """.trimIndent(),
        )

        assertTrue(result.isSuccess)
        assertEquals(
            listOf("launch", "tap_text", "read", "virtual_tap", "virtual_read", "node_script"),
            result.getOrThrow().map { it.action },
        )
        assertEquals(2, result.getOrThrow()[1].maxSwipes)
        assertEquals(42, result.getOrThrow()[3].displayId)
        assertEquals(120, result.getOrThrow()[3].x)
        assertEquals("console.log('safe')", result.getOrThrow()[5].code)
        assertEquals(45_000L, result.getOrThrow()[5].timeoutMs)
    }

    @Test
    fun `parser rejects empty and oversized workflows`() {
        assertTrue(AutomationWorkflowParser.parse("[]").isFailure)
        val oversized = (1..21).joinToString(prefix = "[", postfix = "]") { "{\"action\":\"read\"}" }
        assertTrue(AutomationWorkflowParser.parse(oversized).isFailure)
    }
}
