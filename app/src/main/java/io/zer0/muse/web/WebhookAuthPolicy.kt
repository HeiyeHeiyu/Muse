package io.zer0.muse.web

import io.zer0.muse.channel.ChannelConfig
import io.zer0.muse.channel.ChannelPlatform
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.OffsetDateTime

/** Webhook 鉴权的纯逻辑边界,供路由与回归测试共用。 */
internal object WebhookAuthPolicy {

    fun verifyFeishu(config: ChannelConfig?, presentedToken: String?): Boolean {
        val expected = config?.webhookVerificationToken.orEmpty()
        val presented = presentedToken.orEmpty()
        val enabled = config?.let {
            it.enabled && it.webhookEnabled && it.platform == ChannelPlatform.FEISHU
        } == true
        return enabled &&
            expected.isNotBlank() &&
            presented.isNotBlank() &&
            MessageDigest.isEqual(
                expected.toByteArray(Charsets.UTF_8),
                presented.toByteArray(Charsets.UTF_8),
            )
    }

    fun selectFeishuConfig(configs: List<ChannelConfig>, presentedToken: String?): ChannelConfig? =
        configs.filter { verifyFeishu(it, presentedToken) }.singleOrNull()

    fun isFreshFeishuEvent(createTime: String?, nowEpochMillis: Long = System.currentTimeMillis()): Boolean {
        val value = createTime?.trim().orEmpty()
        val timestamp = parseFeishuTimestamp(value)
            ?: return false
        return timestamp in (nowEpochMillis - FEISHU_TIMESTAMP_TOLERANCE_MILLIS)..(nowEpochMillis + FEISHU_TIMESTAMP_TOLERANCE_MILLIS)
    }

    private fun parseFeishuTimestamp(value: String): Long? {
        return value.toLongOrNull()?.let { numeric ->
            if (numeric in 0 until NUMERIC_MILLIS_THRESHOLD) numeric * 1000 else numeric
        } ?: value.toBigDecimalOrNull()?.let { decimalSeconds ->
            runCatching { decimalSeconds.multiply(BigDecimal.valueOf(1000)).toLong() }.getOrNull()
        } ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
    }

    fun sanitizeFeishuPayloadForStorage(payload: JsonObject): String {
        val sanitized = payload.toMutableMap()
        sanitized.remove("token")
        val header = payload["header"] as? JsonObject
        if (header != null && "token" in header) {
            sanitized["header"] = JsonObject(header.toMutableMap().apply { remove("token") })
        }
        return JsonObject(sanitized).toString()
    }

    fun verifyQqCallbackToken(config: ChannelConfig?, presentedToken: String?): Boolean {
        val expected = config?.webhookVerificationToken.orEmpty()
        val presented = presentedToken.orEmpty()
        val enabled = config?.let {
            it.enabled && it.webhookEnabled && it.platform == ChannelPlatform.QQ
        } == true
        return enabled &&
            expected.isNotBlank() &&
            presented.isNotBlank() &&
            MessageDigest.isEqual(
                expected.toByteArray(Charsets.UTF_8),
                presented.toByteArray(Charsets.UTF_8),
            )
    }

    fun selectQqConfig(configs: List<ChannelConfig>, presentedToken: String?): ChannelConfig? =
        configs.filter { verifyQqCallbackToken(it, presentedToken) }.singleOrNull()

    fun isValidQqChallenge(plainToken: String?, timestamp: String?, nowEpochSeconds: Long): Boolean {
        val token = plainToken.orEmpty()
        if (token.isBlank() || !isFreshQqTimestamp(timestamp, nowEpochSeconds)) return false
        val jsonPayload = runCatching { io.zer0.common.AppJson.parseToJsonElement(token) }.getOrNull()
        return jsonPayload !is JsonObject
    }

    fun verifyQq(
        config: ChannelConfig?,
        timestamp: String?,
        body: String,
        signature: String?,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ): Boolean {
        val secret = config?.appSecret.orEmpty()
        val eventTimestamp = timestamp.orEmpty()
        val eventSignature = signature.orEmpty()
        return isFreshQqTimestamp(eventTimestamp, nowEpochSeconds) &&
            config?.let { it.enabled && it.webhookEnabled && it.platform == ChannelPlatform.QQ } == true &&
            secret.isNotBlank() &&
            eventTimestamp.isNotBlank() &&
            eventSignature.isNotBlank() &&
            WebhookSignatures.verifyWebhook(secret, eventTimestamp, body, eventSignature)
    }

    private fun isFreshQqTimestamp(timestamp: String?, nowEpochSeconds: Long): Boolean {
        val parsedTimestamp = timestamp?.toLongOrNull() ?: return false
        return parsedTimestamp >= nowEpochSeconds - QQ_TIMESTAMP_TOLERANCE_SECONDS &&
            parsedTimestamp <= nowEpochSeconds + QQ_TIMESTAMP_TOLERANCE_SECONDS
    }

    private const val QQ_TIMESTAMP_TOLERANCE_SECONDS = 300L
    private const val FEISHU_TIMESTAMP_TOLERANCE_MILLIS = 300_000L
    private const val NUMERIC_MILLIS_THRESHOLD = 100_000_000_000L
}
