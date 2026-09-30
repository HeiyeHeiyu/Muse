package io.zer0.muse.web

import io.zer0.common.AppJson
import io.zer0.muse.channel.ChannelConfig
import io.zer0.muse.channel.ChannelPlatform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class WebhookAuthPolicyTest {

    @Test
    fun `legacy channel configuration keeps webhooks disabled`() {
        val legacy = AppJson.decodeFromString(
            ChannelConfig.serializer(),
            """{"id":"legacy","platform":"FEISHU"}""",
        )

        assertFalse(legacy.webhookEnabled)
        assertTrue(legacy.webhookVerificationToken.isEmpty())
    }

    @Test
    fun `feishu webhook requires explicit enable and matching verification token`() {
        val config = ChannelConfig(
            id = "feishu",
            platform = ChannelPlatform.FEISHU,
            webhookEnabled = true,
            webhookVerificationToken = "verify-token",
        )

        assertTrue(WebhookAuthPolicy.verifyFeishu(config, "verify-token"))
        assertFalse(WebhookAuthPolicy.verifyFeishu(config, null))
        assertFalse(WebhookAuthPolicy.verifyFeishu(config, "wrong-token"))
        assertFalse(WebhookAuthPolicy.verifyFeishu(config.copy(webhookEnabled = false), "verify-token"))
    }

    @Test
    fun `feishu token selects exactly one matching channel and event timestamp must be fresh`() {
        val first = ChannelConfig(
            id = "feishu-a",
            platform = ChannelPlatform.FEISHU,
            webhookEnabled = true,
            webhookVerificationToken = "token-a",
        )
        val second = ChannelConfig(
            id = "feishu-b",
            platform = ChannelPlatform.FEISHU,
            webhookEnabled = true,
            webhookVerificationToken = "token-b",
        )
        assertTrue(WebhookAuthPolicy.selectFeishuConfig(listOf(first, second), "token-b") == second)
        assertFalse(WebhookAuthPolicy.selectFeishuConfig(listOf(first, second), "unknown") != null)
        assertFalse(WebhookAuthPolicy.selectFeishuConfig(listOf(first, second, second), "token-b") != null)

        val now = 1_800_000_000_000L
        val createTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochMilli(now).atOffset(ZoneOffset.UTC),
        )
        assertTrue(WebhookAuthPolicy.isFreshFeishuEvent(createTime, now))
        assertTrue(WebhookAuthPolicy.isFreshFeishuEvent((now / 1000).toString(), now))
        assertTrue(WebhookAuthPolicy.isFreshFeishuEvent(now.toString(), now))
        assertTrue(WebhookAuthPolicy.isFreshFeishuEvent("${now / 1000}.000", now))
        assertFalse(WebhookAuthPolicy.isFreshFeishuEvent(createTime, now + 300_001))
        assertFalse(WebhookAuthPolicy.isFreshFeishuEvent(null, now))
    }

    @Test
    fun `qq webhook requires callback url token and fresh event signature`() {
        val secret = "unit-test-bot-secret"
        val callbackToken = "long-random-callback-token"
        val now = 1_800_000_000L
        val timestamp = now.toString()
        val body = """{"op":"0","d":{"content":"hello"}}"""
        val signature = Ed25519.sign(
            Ed25519.seedFromSecret(secret),
            (timestamp + body).toByteArray(Charsets.UTF_8),
        ).joinToString("") { "%02x".format(it) }
        val config = ChannelConfig(
            id = "qq",
            platform = ChannelPlatform.QQ,
            appSecret = secret,
            webhookEnabled = true,
            webhookVerificationToken = callbackToken,
        )

        assertTrue(WebhookAuthPolicy.verifyQqCallbackToken(config, callbackToken))
        assertFalse(WebhookAuthPolicy.verifyQqCallbackToken(config, null))
        assertFalse(WebhookAuthPolicy.verifyQqCallbackToken(config, "wrong-token"))
        assertTrue(WebhookAuthPolicy.verifyQq(config, timestamp, body, signature, now))
        assertFalse(WebhookAuthPolicy.verifyQq(config, null, body, signature, now))
        assertFalse(WebhookAuthPolicy.verifyQq(config, timestamp, body, null, now))
        assertFalse(WebhookAuthPolicy.verifyQq(config, timestamp, body, "00".repeat(64), now))
        assertFalse(WebhookAuthPolicy.verifyQq(config, timestamp, body, "${signature}0", now))
        assertFalse(WebhookAuthPolicy.verifyQq(config, (now - 301).toString(), body, signature, now))
        assertFalse(
            WebhookAuthPolicy.verifyQq(
                config.copy(webhookEnabled = false),
                timestamp,
                body,
                signature,
                now,
            ),
        )
    }

    @Test
    fun `qq challenge refuses JSON event bodies as signing oracle input`() {
        val secret = "unit-test-bot-secret"
        val callbackToken = "long-random-callback-token"
        val timestamp = "1800000000"
        val forgedEventBody = """{"op":"0","d":{"content":"forged"}}"""
        val challengeSignature = WebhookSignatures.signChallenge(secret, timestamp, forgedEventBody)
        val config = ChannelConfig(
            id = "qq",
            platform = ChannelPlatform.QQ,
            appSecret = secret,
            webhookEnabled = true,
            webhookVerificationToken = callbackToken,
        )

        assertFalse(WebhookAuthPolicy.isValidQqChallenge(forgedEventBody, timestamp, 1_800_000_000L))
        assertTrue(WebhookAuthPolicy.isValidQqChallenge("platform-nonce-123", timestamp, 1_800_000_000L))
        assertFalse(WebhookAuthPolicy.isValidQqChallenge("platform-nonce-123", timestamp, 1_800_000_301L))
        assertFalse(WebhookAuthPolicy.verifyQqCallbackToken(config, null))
        assertTrue(
            "the event signature helper remains mathematically correct for platform-signed events",
            WebhookAuthPolicy.verifyQq(
                config,
                timestamp,
                forgedEventBody,
                challengeSignature,
                1_800_000_000L,
            ),
        )
    }

    @Test
    fun `feishu stored raw payload strips verification tokens`() {
        val payload = Json.parseToJsonElement(
            """{"header":{"token":"header-secret","event_id":"event-1"},"token":"legacy-secret","event":{"x":1}}""",
        ).jsonObject

        val sanitized = WebhookAuthPolicy.sanitizeFeishuPayloadForStorage(payload)

        assertFalse(sanitized.contains("header-secret"))
        assertFalse(sanitized.contains("legacy-secret"))
        assertTrue(sanitized.contains("event-1"))
        assertTrue(sanitized.contains("\"x\":1"))
    }
}
