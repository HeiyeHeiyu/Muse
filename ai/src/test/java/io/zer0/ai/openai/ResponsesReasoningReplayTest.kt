package io.zer0.ai.openai

import io.zer0.common.AppJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B5-03: OpenAI Responses reasoning 回放数据契约测试。
 *
 * 锁定两个关键结构,防止回归:
 *  1. 请求 `include` 字段 — 缺它服务端不返回 encrypted_content,回放链路无从触发
 *     (2026-09 排查结论:此前该字段缺失导致整条回放空转);
 *  2. 回放 reasoning item 的形状 — type/id/encrypted_content/summary(必填空数组)。
 */
class ResponsesReasoningReplayTest {

    @Test
    fun `request includes reasoning encrypted content opt-in`() {
        val request = ResponsesRequest(
            model = "gpt-5",
            input = emptyList(),
            include = listOf("reasoning.encrypted_content"),
        )
        val root = AppJson.parseToJsonElement(AppJson.encodeToString(request)).jsonObject
        val include = root["include"]!!.jsonArray
        assertEquals(1, include.size)
        assertEquals("reasoning.encrypted_content", include[0].toString().trim('"'))
    }

    @Test
    fun `replay reasoning item carries required shape`() {
        val item = ResponsesInputItem(
            type = "reasoning",
            id = "rs_abc",
            encrypted_content = "enc_payload",
            summary = buildJsonArray { },
        )
        val root = AppJson.parseToJsonElement(AppJson.encodeToString(item)).jsonObject
        assertEquals("reasoning", root["type"]!!.toString().trim('"'))
        assertEquals("rs_abc", root["id"]!!.toString().trim('"'))
        assertEquals("enc_payload", root["encrypted_content"]!!.toString().trim('"'))
        assertTrue(root["summary"] != null && root["summary"]!!.jsonArray.isEmpty())
    }

    @Test
    fun `request without include leaves nothing to serialize for replay`() {
        // 不带 include 时 encrypted_content 恒为 null(服务端行为) — 这里只锁定
        // DTO 层不因缺省字段崩溃,兼容旧调用方。
        val request = ResponsesRequest(
            model = "gpt-5",
            input = emptyList(),
        )
        val json = AppJson.encodeToString(request)
        assertTrue(json.isNotBlank())
    }
}
