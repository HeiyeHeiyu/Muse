package io.zer0.muse.tools

import org.junit.Assert.assertTrue
import org.junit.Test

class JsSandboxHostIsolationTest {

    @Test
    fun `execution without plugin config clears the previous host config`() {
        val script = JsSandbox.buildHostInjectionScript(
            scopeKey = null,
            pluginConfigJson = null,
        )

        assertTrue(script.contains("window.__musePluginConfig = null;"))
        assertTrue(script.contains("window.__musePluginId = null;"))
    }

    @Test
    fun `plugin config and id are injected for the current execution only`() {
        val script = JsSandbox.buildHostInjectionScript(
            scopeKey = "plugin-a",
            pluginConfigJson = """{"token":"value"}""",
        )

        assertTrue(script.contains("""window.__musePluginConfig = {"token":"value"};"""))
        assertTrue(script.contains("""window.__musePluginId = "plugin-a";"""))
    }
}
