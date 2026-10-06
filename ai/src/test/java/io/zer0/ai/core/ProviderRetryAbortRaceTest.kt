package io.zer0.ai.core

import io.zer0.ai.anthropic.AnthropicProvider
import io.zer0.ai.gemini.GeminiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Regression coverage for cancellation racing with the first retryable SSE response.
 *
 * A real server response is required here: the old EventSource implementation could schedule a
 * reconnect from a stale callback after the caller had already cancelled the request.
 */
class ProviderRetryAbortRaceTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Anthropic abort during retry backoff prevents reconnect`() = runBlocking {
        assertNoReconnectAfterRetryableResponse(
            ProviderConfig(
                id = "anthropic-race",
                displayName = "Anthropic Race",
                type = ProviderType.ANTHROPIC,
                baseUrl = server.url("/v1").toString(),
                apiKey = "test-key",
            ),
            provider = { config -> AnthropicProvider(config) },
            modelId = "claude-race",
        )
    }

    @Test
    fun `Gemini abort during retry backoff prevents reconnect`() = runBlocking {
        assertNoReconnectAfterRetryableResponse(
            ProviderConfig(
                id = "gemini-race",
                displayName = "Gemini Race",
                type = ProviderType.GEMINI,
                baseUrl = server.url("/v1beta").toString(),
                apiKey = "test-key",
                specific = ProviderSpecificConfig.Gemini(),
            ),
            provider = { config -> GeminiProvider(config) },
            modelId = "gemini-race",
        )
    }

    private suspend fun assertNoReconnectAfterRetryableResponse(
        config: ProviderConfig,
        provider: (ProviderConfig) -> Provider,
        modelId: String,
    ) = coroutineScope {
        val firstRequest = CountDownLatch(1)
        server.start()
        server.delegate.dispatcher = object : Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                firstRequest.countDown()
                return MockResponse.Builder()
                    .code(503)
                    .clearHeaders()
                    .addHeader("Content-Type", "text/event-stream")
                    .body("""{"error":{"message":"temporary"}}""")
                    .build()
            }
        }

        val signal = AbortSignal()
        val request = ChatRequest(
            messages = listOf(UIMessage(role = MessageRole.USER, content = "retry then cancel")),
            model = Model(id = modelId, providerId = config.id),
            abortSignal = signal,
        )
        val collector = launch(Dispatchers.IO) {
            provider(config).streamChat(request).collect()
        }

        assertTrue("first retryable response should arrive", firstRequest.await(5, TimeUnit.SECONDS))
        signal.abort()
        withTimeout(5_000) { collector.join() }

        // Allow the normal 1s retry backoff plus callback delivery time to elapse.
        delay(1_500)
        assertEquals("cancelled retry must not reconnect", 1, server.requestCount)
        assertEquals("abort listener must be removed", 0, signal.listenerCount)
    }
}
