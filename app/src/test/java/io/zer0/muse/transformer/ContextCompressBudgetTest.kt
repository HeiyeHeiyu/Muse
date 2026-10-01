package io.zer0.muse.transformer

import io.zer0.ai.core.MessageRole
import io.zer0.ai.core.ToolCall
import io.zer0.ai.core.UIMessage
import io.zer0.muse.util.TokenEstimator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextCompressBudgetTest {

    @Test
    fun `compression budget counts reasoning tools and images`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            content = "short",
            reasoning = "hidden reasoning that still occupies context",
            toolCalls = listOf(ToolCall("call-1", "search", "{\"query\":\"Muse\"}")),
            imageBase64List = listOf("image"),
        )
        val actualTokens = TokenEstimator.estimate(listOf(message))

        assertTrue(isOverContextCompressionBudget(listOf(message), actualTokens - 1))
        assertFalse(isOverContextCompressionBudget(listOf(message), actualTokens))
    }
}
