package io.zer0.muse.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * v2.2.1: 工具失败反馈措辞的单测。
 */
class ToolFailureTextTest {

    @Test
    fun `超时分类含连接或读取与地址`() {
        val text = ToolFailureText.httpFailure("HTTP GET", "https://example.com/a", SocketTimeoutException("timeout"))
        assertTrue(text.contains("超时"))
        assertTrue(text.contains("https://example.com/a"))

        val callTimeout = ToolFailureText.httpFailure("http_post", "https://example.com/b", InterruptedIOException("timeout"))
        assertTrue(callTimeout.contains("超时"))
    }

    @Test
    fun `域名解析失败分类`() {
        val text = ToolFailureText.httpFailure("HTTP GET", "https://nope.invalid", UnknownHostException("Unresolved"))
        assertTrue(text.contains("域名解析失败"))
        assertTrue(text.contains("https://nope.invalid"))
    }

    @Test
    fun `TLS 握手失败分类`() {
        val text = ToolFailureText.httpFailure("HTTP GET", "https://bad.example", SSLHandshakeException("bad cert"))
        assertTrue(text.contains("TLS"))
    }

    @Test
    fun `其余异常带原始信息与地址`() {
        val text = ToolFailureText.httpFailure("HTTP POST", "https://example.com/c", IOException("connection reset"))
        assertTrue(text.contains("https://example.com/c"))
        assertTrue(text.contains("connection reset"))
    }

    @Test
    fun `空异常兜底`() {
        val text = ToolFailureText.httpFailure("HTTP GET", "https://example.com", null)
        assertTrue(text.contains("未知网络异常"))
        assertEquals(true, text.contains("https://example.com"))
    }
}
