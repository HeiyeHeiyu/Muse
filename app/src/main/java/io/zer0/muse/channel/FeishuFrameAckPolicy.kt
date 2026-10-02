package io.zer0.muse.channel

/** 只有带事件身份的数据帧才走飞书事件 ACK 路径。 */
internal fun shouldAcknowledgeFeishuEventFrame(messageId: String?, frameType: String?): Boolean =
    !messageId.isNullOrBlank() && frameType == "event"
