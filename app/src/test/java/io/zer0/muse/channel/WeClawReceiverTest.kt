package io.zer0.muse.channel

import io.zer0.muse.data.SecureKeyCipher
import io.zer0.muse.data.SecureKeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class WeClawReceiverTest {
    @Test
    fun `derived event id is stable for a retried poll batch without persisting cursor contents`() {
        val message = WeClawClient.InboundMsg(
            fromUserId = "user-1",
            text = "hello",
            contextToken = "private-context-token",
        )

        val first = weClawBatchEventId("cursor-before", "cursor-after", 0, message)
        val retry = weClawBatchEventId("cursor-before", "cursor-after", 0, message)

        assertEquals(first, retry)
        assertFalse(first.contains("cursor-before"))
        assertFalse(first.contains("private-context-token"))
    }

    @Test
    fun `derived event id distinguishes messages and successive cursor batches`() {
        val message = WeClawClient.InboundMsg(
            fromUserId = "user-1",
            text = "hello",
            contextToken = "context",
        )

        val first = weClawBatchEventId("cursor-1", "cursor-2", 0, message)
        val nextInBatch = weClawBatchEventId("cursor-1", "cursor-2", 1, message)
        val nextBatch = weClawBatchEventId("cursor-2", "cursor-3", 0, message)

        assertNotEquals(first, nextInBatch)
        assertNotEquals(first, nextBatch)
    }

    @Test
    fun `queued WeClaw reply token is encrypted and can be restored for replay`() = runBlocking {
        val original = SecureKeyStore.delegate
        try {
            SecureKeyStore.delegate = TestCipher()
            val encrypted = protectWeClawReplyContextToken("private-context-token")

            assertFalse(encrypted.contains("private-context-token"))
            assertEquals("private-context-token", restoreWeClawReplyContextToken(encrypted))
            assertNull(restoreWeClawReplyContextToken("not-encrypted"))
        } finally {
            SecureKeyStore.delegate = original
        }
    }

    @Test
    fun `WeClaw cursor is encrypted with legacy plaintext migration support`() = runBlocking {
        val original = SecureKeyStore.delegate
        try {
            SecureKeyStore.delegate = TestCipher()
            val encrypted = protectWeClawCursor("private-cursor")

            assertFalse(encrypted.contains("private-cursor"))
            assertEquals("private-cursor", restoreWeClawCursor(encrypted))
            assertEquals("legacy-cursor", restoreWeClawCursor("legacy-cursor"))
        } finally {
            SecureKeyStore.delegate = original
        }
    }

    private class TestCipher : SecureKeyCipher {
        override suspend fun encrypt(plain: String): String {
            if (plain.isEmpty()) return ""
            val encoded = Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8))
            return "test-encrypted:$encoded"
        }

        override suspend fun decrypt(stored: String): String = decryptOrNull(stored).orEmpty()

        override suspend fun decryptOrNull(stored: String): String? = when {
            stored.isEmpty() -> ""
            stored.startsWith("test-encrypted:") -> {
                val bytes = Base64.getDecoder().decode(stored.removePrefix("test-encrypted:"))
                String(bytes, Charsets.UTF_8)
            }
            else -> stored.takeUnless { it.startsWith("enc_v1:") }
        }
    }
}
