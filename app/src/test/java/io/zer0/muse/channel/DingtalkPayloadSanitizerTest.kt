package io.zer0.muse.channel

import io.zer0.common.AppJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DingtalkPayloadSanitizerTest {

    @Test
    fun `stored callback payload does not retain session webhook credentials`() {
        val webhook = "https://api.dingtalk.example/session?access_token=private"
        val payload = AppJson.parseToJsonElement(
            """
            {
              "text": {"content": "hello"},
              "sessionWebhook": "$webhook",
              "sessionWebhookExpiredTime": "1800000000000",
              "conversationId": "cid-1"
            }
            """.trimIndent(),
        ).jsonObject

        val sanitized = sanitizeDingtalkPayloadForStorage(payload)

        assertFalse(sanitized.contains(webhook))
        assertFalse(sanitized.contains("sessionWebhookExpiredTime"))
        assertTrue(sanitized.contains("conversationId"))
        assertTrue(sanitized.contains("hello"))
    }
}
