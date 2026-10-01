package io.zer0.muse.ui.chat

import io.zer0.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSystemPromptCompositionTest {

    @Test
    fun `static and dynamic prompts remain separate system messages`() {
        val messages = composeSystemPromptMessages("stable prompt", "current time")

        assertEquals(2, messages.size)
        assertEquals(MessageRole.SYSTEM, messages[0].role)
        assertEquals("stable prompt", messages[0].content)
        assertEquals("current time", messages[1].content)
    }

    @Test
    fun `blank prompt parts are omitted`() {
        assertEquals(1, composeSystemPromptMessages("stable prompt", "").size)
        assertEquals(1, composeSystemPromptMessages("", "current time").size)
        assertEquals(0, composeSystemPromptMessages("", "").size)
    }
}
