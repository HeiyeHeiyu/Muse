package io.zer0.muse.tools.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShellCommandLogPolicyTest {

    @Test
    fun `command log summary keeps only the safe verb`() {
        val summary = sanitizeShellCommandForLog(
            "settings put secure auth_token super-secret-value",
        )

        assertEquals("settings", summary)
        assertFalse(summary.contains("auth_token"))
        assertFalse(summary.contains("super-secret-value"))
    }

    @Test
    fun `blank or malformed command gets a neutral summary`() {
        assertEquals("<unknown>", sanitizeShellCommandForLog(""))
        assertEquals("<unknown>", sanitizeShellCommandForLog("   \t"))
        assertEquals("<unknown>", sanitizeShellCommandForLog("$(cat secret)"))
    }
}
