package io.zer0.muse.channel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeishuFrameAckPolicyTest {

    @Test
    fun `event fragments are acknowledged before the full payload is assembled`() {
        assertTrue(shouldAcknowledgeFeishuEventFrame(messageId = "message-1", frameType = "event"))
    }

    @Test
    fun `frames without an event identity are not acknowledged by the event path`() {
        assertFalse(shouldAcknowledgeFeishuEventFrame(messageId = "", frameType = "event"))
        assertFalse(shouldAcknowledgeFeishuEventFrame(messageId = "message-1", frameType = "control"))
    }
}
