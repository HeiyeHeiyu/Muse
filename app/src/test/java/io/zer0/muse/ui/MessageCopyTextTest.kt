package io.zer0.muse.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageCopyTextTest {

    @Test
    fun `copy includes reasoning when body exists`() {
        assertEquals(
            "answer\n\n[思考过程]\nthinking",
            messageCopyText("answer", "thinking"),
        )
    }

    @Test
    fun `copy falls back to reasoning for reasoning-only message`() {
        assertEquals("thinking", messageCopyText("", "thinking"))
    }
}
