package io.zer0.ai.openai

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAIProviderResponsesTest {

    @Test
    fun `responses output text fallback combines final message blocks`() {
        val response =
            ResponsesResult(
                output = listOf(
                    ResponsesOutputItem(
                        type = "message",
                        content = listOf(
                            ResponsesContentBlock(type = "output_text", text = "hello "),
                            ResponsesContentBlock(type = "output_text", text = "world"),
                        ),
                    ),
                ),
            )

        assertEquals("hello world", responsesOutputText(response))
    }

    @Test
    fun `responses output text prefers top level shortcut`() {
        val response =
            ResponsesResult(
                outputText = "shortcut",
                output = listOf(
                    ResponsesOutputItem(
                        type = "message",
                        content = listOf(ResponsesContentBlock(type = "output_text", text = "ignored")),
                    ),
                ),
            )

        assertEquals("shortcut", responsesOutputText(response))
    }
}
