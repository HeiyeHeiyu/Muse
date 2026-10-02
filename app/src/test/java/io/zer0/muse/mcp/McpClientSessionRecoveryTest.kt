package io.zer0.muse.mcp

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.zer0.common.AppJson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class McpClientSessionRecoveryTest {

    @Test
    fun `concurrent requests share one reinitialize after streamable session loss`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        val initializeCount = AtomicInteger(0)
        val staleRequestCount = AtomicInteger(0)
        val staleRequestsReady = CountDownLatch(2)
        val session2Initialized = CountDownLatch(1)

        server.createContext("/mcp") { exchange ->
            val request = exchange.requestBody.use { body ->
                AppJson.parseToJsonElement(body.readBytes().toString(StandardCharsets.UTF_8)).jsonObject
            }
            val method = request["method"]?.jsonPrimitive?.content
            val requestId = request["id"]?.jsonPrimitive?.content
            val sessionId = exchange.requestHeaders.getFirst("Mcp-Session-Id")

            when {
                method == "initialize" -> {
                    when (initializeCount.incrementAndGet()) {
                        1 -> respond(
                            exchange,
                            200,
                            initializeResponse(requestId, "session-1"),
                            sessionId = "session-1",
                        )

                        2 -> respond(
                            exchange,
                            200,
                            initializeResponse(requestId, "session-2"),
                            sessionId = "session-2",
                        )

                        else -> respond(exchange, 500, "duplicate initialize")
                    }
                }

                method == "notifications/initialized" && sessionId == "session-2" -> {
                    respond(exchange, 202, "")
                    session2Initialized.countDown()
                }

                method == "notifications/initialized" -> respond(exchange, 202, "")

                method == "tools/list" && sessionId == "session-1" -> {
                    val staleIndex = staleRequestCount.incrementAndGet()
                    staleRequestsReady.countDown()
                    if (!staleRequestsReady.await(10, TimeUnit.SECONDS)) {
                        respond(exchange, 503, "concurrent stale requests did not arrive")
                        return@createContext
                    }
                    if (staleIndex == 2 && !session2Initialized.await(10, TimeUnit.SECONDS)) {
                        respond(exchange, 503, "session reinitialization did not complete")
                        return@createContext
                    }
                    respond(exchange, 404, "session expired")
                }

                method == "tools/list" && sessionId == "session-2" -> {
                    respond(exchange, 200, toolsResponse(requestId))
                }

                else -> respond(exchange, 400, "unexpected MCP request")
            }
        }
        server.executor = executor
        server.start()

        val client = McpClient(
            config = McpServerConfig(
                id = "session-recovery-test",
                name = "Session Recovery Test",
                transportType = McpTransportType.STREAMABLE_HTTP,
                url = "http://127.0.0.1:${server.address.port}/mcp",
                autoReconnect = false,
                requestTimeoutMs = 15_000L,
            ),
        )

        try {
            client.start()
            withTimeout(5_000L) {
                client.state.first { it == McpConnectionState.CONNECTED }
            }

            val calls = coroutineScope {
                withTimeout(20_000L) {
                    listOf(
                        async { client.listTools() },
                        async { client.listTools() },
                    ).awaitAll()
                }
            }

            assertEquals(2, staleRequestCount.get())
            assertEquals(2, initializeCount.get())
            assertTrue(
                "recovered tool results: ${calls.map { result -> result.map { it.name } }}",
                calls.all { it.size == 1 && it.single().name == "echo" },
            )
        } finally {
            client.close()
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private fun initializeResponse(requestId: String?, sessionId: String): String =
        """{"jsonrpc":"2.0","id":$requestId,"result":{"protocolVersion":"2025-03-26","serverInfo":{"name":"test","version":"1"},"session":"$sessionId"}}"""

    private fun toolsResponse(requestId: String?): String =
        """{"jsonrpc":"2.0","id":$requestId,"result":{"tools":[{"name":"echo","description":"echo"}]}}"""

    private fun respond(exchange: HttpExchange, status: Int, body: String, sessionId: String? = null) {
        exchange.responseHeaders.add("Content-Type", "application/json")
        sessionId?.let { exchange.responseHeaders.add("Mcp-Session-Id", it) }
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
