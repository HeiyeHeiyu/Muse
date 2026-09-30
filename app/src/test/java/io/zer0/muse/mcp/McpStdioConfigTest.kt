package io.zer0.muse.mcp

import io.zer0.common.AppJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-A: stdio 传输配置序列化与 SSRF 边界测试（纯逻辑）。
 */
class McpStdioConfigTest {

    @Test
    fun stdioConfigRoundTrips() {
        val config = McpServerConfig(
            id = "s1",
            name = "local",
            transportType = McpTransportType.STDIO,
            command = "npx -y @modelcontextprotocol/server-memory",
        )
        val json = AppJson.encodeToString(McpServerConfig.serializer(), config)
        val decoded = AppJson.decodeFromString(McpServerConfig.serializer(), json)
        assertEquals(McpTransportType.STDIO, decoded.transportType)
        assertEquals("npx -y @modelcontextprotocol/server-memory", decoded.command)
    }

    @Test
    fun legacyConfigWithoutCommandStillParses() {
        // 旧配置 JSON（无 command 字段）向后兼容
        val legacy = """{"id":"old","name":"old","transportType":"STREAMABLE_HTTP","url":"https://example.com/mcp"}"""
        val decoded = AppJson.decodeFromString(McpServerConfig.serializer(), legacy)
        assertEquals("", decoded.command)
        assertEquals(McpTransportType.STREAMABLE_HTTP, decoded.transportType)
    }

    @Test
    fun stdioIsNeverSsrfBlocked() {
        val config = McpServerConfig(
            id = "s1",
            name = "local",
            transportType = McpTransportType.STDIO,
            command = "npx -y foo",
        )
        assertFalse(config.isSsrfBlocked())
    }

    @Test
    fun remoteTransportStillSsrfChecked() {
        val config = McpServerConfig(id = "s1", name = "r", url = "http://192.168.1.1:8080/mcp")
        assertTrue(config.isSsrfBlocked())
    }
}
