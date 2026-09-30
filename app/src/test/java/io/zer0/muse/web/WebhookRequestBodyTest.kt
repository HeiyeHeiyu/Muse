package io.zer0.muse.web

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Test

class WebhookRequestBodyTest {
    @Test
    fun slowChunkedBodyHasATotalReadDeadline() = runBlocking {
        val channel = ByteChannel(autoFlush = true)
        channel.writeFully(byteArrayOf(1))

        val result = readWebhookBodyWithinLimit(channel, maxBytes = 100, timeoutMillis = 25)

        assertSame(WebhookBodyRead.TimedOut, result)
    }
}
