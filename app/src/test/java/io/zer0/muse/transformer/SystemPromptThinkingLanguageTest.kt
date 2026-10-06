package io.zer0.muse.transformer

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemPromptThinkingLanguageTest {

    @Test
    fun `thinking language follows the configured interface locale`() {
        assertEquals("中文", thinkingLanguageName("zh"))
        assertEquals("English", thinkingLanguageName("en"))
        assertEquals("日本語", thinkingLanguageName("ja"))
        assertEquals("한국어", thinkingLanguageName("ko"))
        assertEquals("Español", thinkingLanguageName("es"))
        assertEquals("Português", thinkingLanguageName("pt"))
        assertEquals("Русский", thinkingLanguageName("ru"))
    }

    @Test
    fun `unknown locale uses a stable language label instead of user language`() {
        assertEquals("the configured system language", thinkingLanguageName("zz"))
    }

    @Test
    fun `thinking instruction requires reasoning before tool rounds without fabricating unsupported output`() {
        val instruction = thinkingInstruction("en")
        org.junit.Assert.assertTrue(instruction.contains("before each tool call round"))
        org.junit.Assert.assertTrue(instruction.contains("Never fabricate reasoning"))
    }
}
