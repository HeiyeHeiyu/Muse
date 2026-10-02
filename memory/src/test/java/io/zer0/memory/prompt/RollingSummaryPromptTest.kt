package io.zer0.memory.prompt

import org.junit.Assert.assertTrue
import org.junit.Test

class RollingSummaryPromptTest {

    @Test
    fun promptKeepsAssistantCommitmentsAndVibeInsideConversationTimeline() {
        val prompt = RollingSummaryPrompt.buildSystemPrompt(locale = "zh-CN")

        assertTrue(prompt.contains("助手明确承诺"))
        assertTrue(prompt.contains("Vibe"))
        assertTrue(prompt.contains("助手当时表达的感受"))
        assertTrue(prompt.contains("Sparks"))
        assertTrue(prompt.contains("Reflections"))
        assertTrue(prompt.contains("Will"))
    }
}
