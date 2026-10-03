package io.zer0.muse.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RootToolResultPolicyTest {

    @Test
    fun `settings write result confirms target without echoing value`() {
        val result = formatSettingsPutResult("Shizuku", "secure", "auth_token")

        assertEquals("[Shizuku] Setting 'secure:auth_token' updated", result)
        assertFalse(result.contains("secret"))
    }

    @Test
    fun `input result reports length without echoing text`() {
        val result = formatInputInjectResult("Root", textLength = 17, viaClipboard = true)

        assertEquals("[Root] Text injected via clipboard paste (17 chars)", result)
        assertFalse(result.contains("password"))
    }
}
