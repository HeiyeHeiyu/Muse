package io.zer0.ai.openai

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIEndpointResponseTest {
    @Test
    fun `html content type is diagnosed as an api route error`() {
        val message = openAiHtmlEndpointHint("text/html; charset=utf-8", "<!doctype html><html><title>New API</title></html>")

        assertNotNull(message)
        assertTrue(message!!.contains("HTML page instead of OpenAI-compatible JSON"))
        assertTrue(message.contains("/v1"))
    }

    @Test
    fun `html payload is detected even when content type is missing`() {
        assertNotNull(openAiHtmlEndpointHint("", "  <html><body>login</body></html>"))
    }

    @Test
    fun `json response is not classified as html`() {
        assertNull(openAiHtmlEndpointHint("application/json; charset=utf-8", "{\"data\":[]}"))
    }
}
