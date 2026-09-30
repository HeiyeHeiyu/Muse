package io.zer0.muse.web

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException

internal const val MAX_CHANNEL_WEBHOOK_BODY_BYTES = 1_048_576
internal const val MAX_CHANNEL_WEBHOOK_READ_MILLIS = 15_000L

internal sealed interface WebhookBodyRead {
    data class Content(val value: String) : WebhookBodyRead
    data object TooLarge : WebhookBodyRead
    data object TimedOut : WebhookBodyRead
}

internal suspend fun readWebhookBodyWithinLimit(
    channel: ByteReadChannel,
    maxBytes: Int = MAX_CHANNEL_WEBHOOK_BODY_BYTES,
    timeoutMillis: Long = MAX_CHANNEL_WEBHOOK_READ_MILLIS,
): WebhookBodyRead {
    require(maxBytes >= 0)
    require(timeoutMillis > 0)
    val result = withTimeoutOrNull(timeoutMillis) { readBodyBytes(channel, maxBytes) }
    if (result != null) return result
    channel.cancel(CancellationException("Webhook request body read deadline exceeded"))
    return WebhookBodyRead.TimedOut
}

private suspend fun readBodyBytes(channel: ByteReadChannel, maxBytes: Int): WebhookBodyRead {
    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
    val buffer = ByteArray(8192)
    var count = 0
    while (count >= 0) {
        val allowedReadSize = minOf(buffer.size, maxBytes - output.size() + 1).coerceAtLeast(1)
        count = channel.readAvailable(buffer, 0, allowedReadSize)
        if (count > 0) {
            if (output.size() + count > maxBytes) return WebhookBodyRead.TooLarge
            output.write(buffer, 0, count)
        }
    }
    return WebhookBodyRead.Content(output.toString(Charsets.UTF_8.name()))
}
