package io.zer0.ai.anthropic

import com.sun.net.httpserver.HttpServer
import io.zer0.ai.core.ChatRequest
import io.zer0.ai.core.MessageRole
import io.zer0.ai.core.Model
import io.zer0.ai.core.ProviderConfig
import io.zer0.ai.core.ProviderError
import io.zer0.ai.core.ProviderException
import io.zer0.ai.core.ProviderType
import io.zer0.ai.core.UIMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress

class AnthropicProviderErrorTest {

    @Test
    fun `complete text preserves retry after on rate limit`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/messages") { exchange ->
            val body = """{"error":{"type":"rate_limit_error","message":"slow down"}}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Retry-After", "7")
            exchange.sendResponseHeaders(429, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
        try {
            val provider = AnthropicProvider(
                ProviderConfig(
                    id = "anthropic-test",
                    displayName = "Anthropic Test",
                    type = ProviderType.ANTHROPIC,
                    baseUrl = "http://127.0.0.1:${server.address.port}/v1",
                    apiKey = "key",
                ),
            )

            try {
                provider.completeText(
                    ChatRequest(
                        messages = listOf(UIMessage(role = MessageRole.USER, content = "hi")),
                        model = Model(id = "claude-test", providerId = "anthropic-test"),
                    ),
                )
                fail("429 should throw ProviderException")
            } catch (error: ProviderException) {
                val rateLimit = error.providerError as? ProviderError.RateLimit
                assertEquals(7, rateLimit?.retryAfterSec)
            }
        } finally {
            server.stop(0)
        }
    }
}
