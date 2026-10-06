package io.zer0.muse.tools

import org.junit.Assert.assertTrue
import org.junit.Test

class RootToolsOutputTest {

    @Test
    fun `logcat tool result keeps the entire requested output`() {
        val output = "logcat line\n".repeat(12_000)

        val result = formatRootLogcatOutput("Root", output)

        assertTrue(result.startsWith("[Root] Logcat output"))
        assertTrue(result.endsWith(output))
    }

    @Test
    fun `package listing keeps the complete result`() {
        val packages = (1..100).map { "com.example.app$it" }

        val result = formatRootPackageListOutput("Shizuku", packages)

        assertTrue(result.contains("com.example.app100"))
    }

    @Test
    fun `failure output is not truncated`() {
        val detail = "failure detail ".repeat(200)

        val result = formatRootFailureOutput("Root", "读取失败", detail, 1)

        assertTrue(result.endsWith(detail))
    }
}
