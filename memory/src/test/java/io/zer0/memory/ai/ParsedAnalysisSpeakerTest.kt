package io.zer0.memory.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class ParsedAnalysisSpeakerTest {

    @Test
    fun `missing speaker is fail closed and never becomes a user fact`() {
        val analysis = ParsedAnalysis(
            extractedEntities = listOf(
                ParsedEntity(
                    title = "assistant preference",
                    content = "assistant prefers concise replies",
                ),
            ),
        )

        assertTrue(analysis.userFactsOnly().isEmpty())
    }
}
