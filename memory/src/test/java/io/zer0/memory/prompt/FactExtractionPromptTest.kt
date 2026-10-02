package io.zer0.memory.prompt

import org.junit.Assert.assertTrue
import org.junit.Test

class FactExtractionPromptTest {

    @Test
    fun promptExcludesAssistantVibeAndCommitmentsFromUserFacts() {
        val prompt = FactExtractionPrompt.buildSystemPrompt(locale = "zh-CN")

        assertTrue(prompt.contains("助手的 Vibe 自述"))
        assertTrue(prompt.contains("助手承诺"))
        assertTrue(prompt.contains("不是用户事实"))
    }
}
