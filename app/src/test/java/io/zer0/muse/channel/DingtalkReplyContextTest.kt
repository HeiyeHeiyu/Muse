package io.zer0.muse.channel

import io.zer0.muse.data.SecureKeyCipher
import io.zer0.muse.data.SecureKeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Base64

class DingtalkReplyContextTest {

    @Test
    fun `session webhook is encrypted before it enters the inbox`() = runBlocking {
        val original = SecureKeyStore.delegate
        try {
            SecureKeyStore.delegate = TestCipher()
            val webhook = "https://api.dingtalk.example/session?access_token=private"

            val encrypted = protectDingtalkReplyContextToken(webhook)

            assertFalse(encrypted.contains(webhook))
            assertEquals(webhook, restoreDingtalkReplyContextToken(encrypted))
        } finally {
            SecureKeyStore.delegate = original
        }
    }

    private class TestCipher : SecureKeyCipher {
        override suspend fun encrypt(plain: String): String = "test-encrypted:" + Base64.getEncoder().encodeToString(plain.toByteArray())

        override suspend fun decrypt(stored: String): String = decryptOrNull(stored).orEmpty()

        override suspend fun decryptOrNull(stored: String): String? = stored.removePrefix("test-encrypted:")
            .takeIf { stored.startsWith("test-encrypted:") }
            ?.let { String(Base64.getDecoder().decode(it)) }
    }
}
