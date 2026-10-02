package io.zer0.muse.ui.chat

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChatGenerationMemoryTargetTest {

    @Test
    fun `final memory notification uses generation assistant and space snapshots`() {
        val source = File("src/main/java/io/zer0/muse/ui/chat/ChatGenerationController.kt").readText()

        assertTrue(source.contains("generationAssistantId = state.assistant?.id"))
        assertTrue(source.contains("state.transformContext?.extra(\"current_space\")"))
    }
}
