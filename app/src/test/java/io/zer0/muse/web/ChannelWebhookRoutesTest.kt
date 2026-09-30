package io.zer0.muse.web

import android.content.Context
import io.ktor.http.ContentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.zer0.muse.channel.ChannelConfig
import io.zer0.muse.channel.ChannelInbox
import io.zer0.muse.channel.ChannelManager
import io.zer0.muse.channel.ChannelPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.file.Files
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

class ChannelWebhookRoutesTest {
    private val filesDir = Files.createTempDirectory("webhook-route-test").toFile()
    private val context = mockk<Context>(relaxed = true)
    private val configs = MutableStateFlow<List<ChannelConfig>>(emptyList())
    private val channelManager = mockk<ChannelManager>()

    @Before
    fun setUp() {
        every { context.applicationContext } returns context
        every { context.filesDir } returns filesDir
        every { channelManager.channels } returns configs
        coEvery { channelManager.refresh() } just Runs
        ChannelInbox.onInbound = null
        ChannelInbox.clear()
    }

    @Test
    fun qqChallengeCannotSignAnArbitraryEventAndMissingCallbackTokenIsRejected() {
        val callbackToken = "qq-callback-token"
        configs.value = listOf(qqConfig("qq-a", callbackToken, "qq-app-secret"))
        val eventId = UUID.randomUUID().toString()
        val forgedEvent = """{"op":"0","id":"$eventId","d":{"content":"forged"}}"""
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val challengeBody = buildJsonObject {
            put("op", 13)
            putJsonObject("d") {
                put("event_ts", timestamp)
                put("plain_token", forgedEvent)
            }
        }.toString()

        withServer { port ->
            val noToken = post(port, "/webhook/qq", challengeBody)
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, noToken.status)

            val challenge = post(port, "/webhook/qq?token=$callbackToken", challengeBody)
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, challenge.status)
            assertFalse(ChannelInbox.messages.value.any { it.sourceEventId == eventId })
        }
    }

    @Test
    fun qqWebhookSelectsTheChannelWhoseCallbackTokenAndSignatureMatch() {
        val first = qqConfig("qq-a", "callback-a", "secret-a")
        val second = qqConfig("qq-b", "callback-b", "secret-b")
        configs.value = listOf(first, second)
        val eventId = UUID.randomUUID().toString()
        val body = """{"op":"0","id":"$eventId","d":{"content":"hello","author":{"user_openid":"user-1"}}}"""
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val signature = signQqBody(second.appSecret, timestamp, body)

        withServer { port ->
            val response = post(
                port,
                "/webhook/qq?token=${second.webhookVerificationToken}",
                body,
                headers = mapOf(
                    "X-Signature-Timestamp" to timestamp,
                    "X-Signature-Ed25519" to signature,
                ),
            )
            val retry = post(
                port,
                "/webhook/qq?token=${second.webhookVerificationToken}",
                body,
                headers = mapOf(
                    "X-Signature-Timestamp" to timestamp,
                    "X-Signature-Ed25519" to signature,
                ),
            )

            assertEquals(HttpURLConnection.HTTP_OK, response.status)
            assertEquals("duplicate provider delivery must still be acknowledged", HttpURLConnection.HTTP_OK, retry.status)
            val inbound = ChannelInbox.messages.value.firstOrNull { it.sourceEventId == eventId }
            assertNotNull(inbound)
            assertEquals(second.id, inbound?.sourceChannelId)
            assertEquals("user-1", inbound?.from)
            assertEquals(1, ChannelInbox.messages.value.count { it.sourceEventId == eventId })
        }
    }

    @Test
    fun feishuWebhookSelectsTheMatchingChannelAndDoesNotPersistItsToken() {
        val first = feishuConfig("feishu-a", "token-a")
        val second = feishuConfig("feishu-b", "token-b")
        configs.value = listOf(first, second)
        val eventId = UUID.randomUUID().toString()
        val createTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.UTC))
        val body = buildJsonObject {
            put("schema", "2.0")
            putJsonObject("header") {
                put("token", second.webhookVerificationToken)
                put("event_id", eventId)
                put("create_time", createTime)
                put("event_type", "im.message.receive_v1")
            }
            putJsonObject("event") {
                putJsonObject("sender") {
                    put("sender_type", "user")
                    putJsonObject("sender_id") { put("open_id", "user-2") }
                }
                putJsonObject("message") { put("content", "hello") }
            }
        }.toString()

        withServer { port ->
            val response = post(port, "/webhook/feishu", body)

            assertEquals(HttpURLConnection.HTTP_OK, response.status)
            val inbound = ChannelInbox.messages.value.firstOrNull { it.sourceEventId == eventId }
            assertNotNull(inbound)
            assertEquals(second.id, inbound?.sourceChannelId)
            assertFalse(inbound?.raw.orEmpty().contains(second.webhookVerificationToken))
        }
    }

    @Test
    fun feishuRejectsOversizedChunkedRequestsBeforeParsingOrAuthentication() {
        configs.value = emptyList()
        val body = ByteArray(MAX_CHANNEL_WEBHOOK_BODY_BYTES + 1024) { 'x'.code.toByte() }

        withServer { port ->
            val response = post(port, "/webhook/feishu", body, chunked = true)
            assertEquals(HttpURLConnection.HTTP_ENTITY_TOO_LARGE, response.status)
        }
    }

    private fun withServer(block: (port: Int) -> Unit) {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            routing { installChannelWebhookRoutes(context, channelManager) }
        }
        server.start(wait = false)
        try {
            waitUntilReady(port)
            block(port)
        } finally {
            server.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
        }
    }

    private fun waitUntilReady(port: Int) {
        repeat(100) {
            val ready = runCatching {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 100) }
            }.isSuccess
            if (ready) return
            Thread.sleep(10)
        }
        error("Ktor webhook test server did not start")
    }

    private fun post(
        port: Int,
        path: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        chunked: Boolean = false,
    ): HttpResult = post(port, path, body.toByteArray(Charsets.UTF_8), headers, chunked)

    private fun post(
        port: Int,
        path: String,
        body: ByteArray,
        headers: Map<String, String> = emptyMap(),
        chunked: Boolean = false,
    ): HttpResult {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", ContentType.Application.Json.toString())
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        if (chunked) {
            connection.setChunkedStreamingMode(8192)
        } else {
            connection.setFixedLengthStreamingMode(body.size.toLong())
        }
        try {
            connection.outputStream.use { it.write(body) }
        } catch (_: IOException) {
            // The server may reject a chunked body as soon as it crosses the hard limit.
        }
        val status = connection.responseCode
        val responseBody = (if (status >= 400) connection.errorStream else connection.inputStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        connection.disconnect()
        return HttpResult(status, responseBody)
    }

    private fun qqConfig(id: String, callbackToken: String, appSecret: String) = ChannelConfig(
        id = id,
        platform = ChannelPlatform.QQ,
        appSecret = appSecret,
        webhookEnabled = true,
        webhookVerificationToken = callbackToken,
    )

    private fun feishuConfig(id: String, token: String) = ChannelConfig(
        id = id,
        platform = ChannelPlatform.FEISHU,
        webhookEnabled = true,
        webhookVerificationToken = token,
    )

    private fun signQqBody(secret: String, timestamp: String, body: String): String {
        return Ed25519.sign(
            Ed25519.seedFromSecret(secret),
            (timestamp + body).toByteArray(Charsets.UTF_8),
        ).joinToString("") { "%02x".format(it) }
    }

    private data class HttpResult(val status: Int, val body: String)
}
