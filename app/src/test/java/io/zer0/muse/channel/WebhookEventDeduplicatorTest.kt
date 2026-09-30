package io.zer0.muse.channel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class WebhookEventDeduplicatorTest {
    @Test
    fun eventIdsRemainDeduplicatedAfterRestartAndAreScopedToChannel() {
        val file = Files.createTempDirectory("webhook-event-ledger").resolve("events.json").toFile()
        var now = 1_800_000_000_000L
        val firstRun = WebhookEventDeduplicator(file) { now }

        assertTrue(firstRun.claim("QQ", "qq-a", "event-1"))
        assertTrue(firstRun.claim("QQ", "qq-b", "event-1"))

        val afterRestart = WebhookEventDeduplicator(file) { now }
        assertFalse(afterRestart.claim("QQ", "qq-a", "event-1"))
        assertFalse(afterRestart.claim("QQ", "qq-b", "event-1"))
        assertTrue(afterRestart.claim("QQ", "qq-a", "event-2"))

        now += 10 * 60 * 1000L + 1
        val afterRetention = WebhookEventDeduplicator(file) { now }
        assertTrue(afterRetention.claim("QQ", "qq-a", "event-1"))
    }
}
