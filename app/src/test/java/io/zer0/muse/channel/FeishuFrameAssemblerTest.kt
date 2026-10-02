package io.zer0.muse.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeishuFrameAssemblerTest {

    @Test
    fun `capacity eviction preserves other incomplete messages`() {
        val assembler = FeishuFrameAssembler(maxPendingMessages = 2)

        assertNull(assembler.add("message-a", sum = 2, seq = 0, payload = "a0".toByteArray()))
        assertNull(assembler.add("message-b", sum = 2, seq = 0, payload = "b0".toByteArray()))
        assertNull(assembler.add("message-c", sum = 2, seq = 0, payload = "c0".toByteArray()))

        assertEquals("b0b1", assembler.add("message-b", sum = 2, seq = 1, payload = "b1".toByteArray()))
        assertEquals("c0c1", assembler.add("message-c", sum = 2, seq = 1, payload = "c1".toByteArray()))
        assertNull(assembler.add("message-a", sum = 2, seq = 1, payload = "a1".toByteArray()))
    }
}
