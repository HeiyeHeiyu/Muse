package io.zer0.muse.channel

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ChannelInboxTest {
    @Before
    fun setUp() {
        ChannelInbox.onInbound = null
        ChannelInbox.clear()
    }

    @After
    fun tearDown() {
        ChannelInbox.clear()
        ChannelInbox.onInbound = null
    }

    @Test
    fun concurrentRecordsRetainTheLatestHundredWithoutDuplicates() {
        val writers = 200
        val start = CountDownLatch(1)
        val done = CountDownLatch(writers)

        repeat(writers) { index ->
            Thread {
                try {
                    start.await()
                    ChannelInbox.record(
                        source = ChannelInbox.Source("platform-$index", "user-$index"),
                        text = "message-$index",
                        rawPayload = """{"id":$index}""",
                    )
                } finally {
                    done.countDown()
                }
            }.start()
        }

        start.countDown()
        assertTrue("all writers should finish", done.await(10, TimeUnit.SECONDS))

        val memory = ChannelInbox.messages.value
        assertEquals(100, memory.size)
        assertEquals(100, memory.map { it.platform }.toSet().size)
    }

    @Test
    fun webhookEventIdsAreDeduplicatedPerAuthenticatedChannel() {
        var deliveries = 0
        ChannelInbox.onInbound = { deliveries++ }

        assertTrue(
            ChannelInbox.record(
                source = ChannelInbox.Source("QQ", "user", "qq-a", "event-1"),
                text = "hello",
                rawPayload = "{}",
            ),
        )
        assertTrue(
            ChannelInbox.record(
                source = ChannelInbox.Source("QQ", "user", "qq-b", "event-1"),
                text = "hello",
                rawPayload = "{}",
            ),
        )
        assertFalse(
            ChannelInbox.record(
                source = ChannelInbox.Source("QQ", "user", "qq-a", "event-1"),
                text = "hello",
                rawPayload = "{}",
            ),
        )

        assertEquals(2, ChannelInbox.messages.value.size)
        assertEquals(2, deliveries)
    }
}
