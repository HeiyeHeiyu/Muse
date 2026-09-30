package io.zer0.muse.channel

import io.zer0.muse.tools.ToolRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelToolPolicyTest {

    @Test
    fun `empty allowlist keeps legacy no-filter behavior`() {
        assertEquals(emptySet<String>(), parseChannelToolAllowlist(""))
        assertEquals(emptySet<String>(), parseChannelToolAllowlist("[]"))
    }

    @Test
    fun `malformed allowlist is distinct from an empty allowlist`() {
        assertNull(parseChannelToolAllowlist("{"))
        assertNull(parseChannelToolAllowlist("[{}]"))
    }

    @Test
    fun `channel only executes an exposed non-high-risk tool`() {
        val exposed = setOf("weather", "ui_get_page_info")

        assertTrue(mayExecuteChannelToolCall("weather", exposed, ToolRiskLevel.NORMAL))
        assertFalse(mayExecuteChannelToolCall("ui_get_page_info", exposed, ToolRiskLevel.HIGH))
        assertFalse(mayExecuteChannelToolCall("workspace_delete", exposed, ToolRiskLevel.NORMAL))
    }
}
