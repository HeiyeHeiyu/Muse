package io.zer0.muse.web

import android.content.Context
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.zer0.muse.channel.ChannelInbox
import io.zer0.muse.channel.ChannelManager

internal fun Route.installChannelWebhookRoutes(context: Context, channelManager: ChannelManager) {
    installFeishuWebhook(context, channelManager)
    installQqWebhook(context, channelManager)
}

private fun Route.installFeishuWebhook(context: Context, channelManager: ChannelManager) {
    post("/webhook/feishu") {
        channelManager.refresh()
        val body = when (val bodyRead = readLimitedRequestBody(call)) {
            is WebhookBodyRead.Content -> bodyRead.value
            WebhookBodyRead.TooLarge -> {
                call.respondText("payload too large", status = HttpStatusCode.PayloadTooLarge)
                return@post
            }
            WebhookBodyRead.TimedOut -> {
                call.respondText("request body timed out", status = HttpStatusCode.RequestTimeout)
                return@post
            }
        }
        val payload = parseWebhookPayload(body)
        if (payload == null) {
            call.respondText("invalid payload", status = HttpStatusCode.BadRequest)
            return@post
        }
        when (val dispatch = resolveFeishuPayload(payload, channelManager.channels.value)) {
            is FeishuDispatch.Rejected -> call.respondText(dispatch.message, status = dispatch.status)
            is FeishuDispatch.Challenge -> call.respondText(
                "{\"challenge\":${kotlinx.serialization.json.JsonPrimitive(dispatch.value)}}",
                ContentType.Application.Json,
            )
            is FeishuDispatch.Inbound -> {
                ChannelInbox.attach(context)
                val recorded = recordWebhookInbound(dispatch.source, dispatch.text, dispatch.rawPayload)
                if (recorded == null) {
                    call.respondText("temporarily unavailable", status = HttpStatusCode.ServiceUnavailable)
                } else {
                    call.respondText("{\"code\":0}", ContentType.Application.Json)
                }
            }
            FeishuDispatch.Acknowledge -> call.respondText("{\"code\":0}", ContentType.Application.Json)
        }
    }
}

private fun Route.installQqWebhook(context: Context, channelManager: ChannelManager) {
    post("/webhook/qq") {
        channelManager.refresh()
        val config = WebhookAuthPolicy.selectQqConfig(
            channelManager.channels.value,
            call.request.queryParameters["token"],
        )
        if (config == null || config.appSecret.isBlank()) {
            call.respondText("webhook disabled", status = HttpStatusCode.NotFound)
            return@post
        }
        val body = when (val bodyRead = readLimitedRequestBody(call)) {
            is WebhookBodyRead.Content -> bodyRead.value
            WebhookBodyRead.TooLarge -> {
                call.respondText("payload too large", status = HttpStatusCode.PayloadTooLarge)
                return@post
            }
            WebhookBodyRead.TimedOut -> {
                call.respondText("request body timed out", status = HttpStatusCode.RequestTimeout)
                return@post
            }
        }
        val payload = parseWebhookPayload(body)
        if (payload == null) {
            call.respondText("invalid payload", status = HttpStatusCode.BadRequest)
            return@post
        }
        when (
            val dispatch = resolveQqPayload(
                config,
                payload,
                body,
                call.request.headers["X-Signature-Ed25519"],
                call.request.headers["X-Signature-Timestamp"],
            )
        ) {
            is QqDispatch.Rejected -> call.respondText(dispatch.message, status = dispatch.status)
            is QqDispatch.Challenge -> call.respondText(
                "{\"plain_token\":${kotlinx.serialization.json.JsonPrimitive(dispatch.plainToken)}," +
                    "\"signature\":\"${dispatch.signature}\"}",
                ContentType.Application.Json,
            )
            is QqDispatch.Inbound -> {
                ChannelInbox.attach(context)
                val recorded = recordWebhookInbound(dispatch.source, dispatch.text, dispatch.rawPayload)
                if (recorded == null) {
                    call.respondText("temporarily unavailable", status = HttpStatusCode.ServiceUnavailable)
                } else {
                    call.respondText("ok")
                }
            }
        }
    }
}
