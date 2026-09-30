package io.zer0.muse.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class QqMsgIdCacheTest {
    @Test
    fun `automatic reply uses its source event instead of a newer cached message`() {
        val target = "test-${UUID.randomUUID()}"
        QqMsgIdCache.put(target, "newer-message")

        val firstAttempt = QqMsgIdCache.replyContext(target, "queued-message")
        val retry = QqMsgIdCache.replyContext(target, "queued-message")
        val regularSend = QqMsgIdCache.replyContext(target, null)
        val missingSource = QqMsgIdCache.replyContext(target, "")

        assertEquals("queued-message", firstAttempt?.messageId)
        assertEquals(1, firstAttempt?.sequence)
        assertEquals(firstAttempt, retry)
        assertEquals("newer-message", regularSend?.messageId)
        assertNull(missingSource)
    }
}
