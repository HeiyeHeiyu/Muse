package io.zer0.muse.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * MCP 工具暴露边界契约：
 * 空的助手 MCP 绑定列表不能把所有已连接的外部 server 工具带进来。
 */
class McpToolIsolationContractTest {

    @Test
    fun `empty assistant MCP binding must not mean every server`() {
        val source = File("src/main/java/io/zer0/muse/ui/chat/ChatStreamCoordinator.kt").readText()
        assertFalse(
            "MCP 工具必须要求显式 server 绑定，不能用空列表放行全部 server",
            source.contains("!isMcpTool || configuredMcpServerIds.isEmpty()"),
        )
    }
}
