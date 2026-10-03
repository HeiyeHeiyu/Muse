package io.zer0.muse.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolDataPrivacyTest {

    @Test
    fun persistenceArgumentsRedactInputAndCredentialFields() {
        val safe = ToolDataPrivacy.safeArgumentsForPersistence(
            toolName = "browser_type",
            arguments = """{"selector":"#password","text":"super-secret","api_key":"sk-live-secret"}""",
        )

        assertFalse(safe.contains("super-secret"))
        assertFalse(safe.contains("sk-live-secret"))
        assertTrue(safe.contains("[REDACTED]"))
        assertTrue(safe.contains("#password"))
    }

    @Test
    fun persistenceResultsDoNotKeepClipboardOrGeneratedPassword() {
        val clipboard = ToolDataPrivacy.safeResultForPersistence(
            toolName = "clipboard_read",
            result = "clipboard content: bank-password-123",
        )
        val password = ToolDataPrivacy.safeResultForPersistence(
            toolName = "generate_password",
            result = "generated password: P@ssw0rd",
        )

        assertFalse(clipboard.contains("bank-password-123"))
        assertFalse(password.contains("P@ssw0rd"))
        assertTrue(clipboard.contains("omitted"))
        assertTrue(password.contains("omitted"))
    }

    @Test
    fun malformedArgumentPreviewIsNotCopiedVerbatim() {
        val preview = ToolDataPrivacy.safeArgumentsForPreview(
            toolName = "browser_type",
            arguments = "malformed super-secret-password",
        )

        assertFalse(preview.contains("super-secret-password"))
        assertTrue(preview.contains("malformed arguments"))
    }
}
