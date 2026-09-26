package io.zer0.muse.tools.channel

import io.zer0.ai.core.ToolDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B8-03 / v2.x (B3): 群聊媒体工具进出策略测试。 */
class GroupChatToolPolicyTest {

    private fun def(name: String) = ToolDefinition(
        name = name,
        description = name,
        parametersJsonSchema = "{}",
    )

    @Test
    fun mediaFilterKeepsOnlyEnabledMediaTools() {
        // v2.x 遗留收尾: generate_video 视频通道补齐后也放开,三个媒体工具均可用
        val input = listOf(
            def("web_search"),
            def("generate_image"),
            def("generate_video"),
            def("generate_qr_code"),
            def("calculator"),
        )

        val result = GroupChatToolPolicy.filterMediaTools(input)

        assertEquals(listOf("generate_image", "generate_video", "generate_qr_code"), result.map { it.name })
        assertTrue(result.all { it.name in GroupChatToolPolicy.ENABLED_MEDIA_TOOLS })
        assertFalse(result.any { it.name in GroupChatToolPolicy.BLOCKED_MEDIA_TOOLS })
    }

    @Test
    fun regularFilterDropsBlockedMediaAndHighRisk() {
        // P1-11: 高风险工具必须被风险白名单挡住;媒体工具(generate_* 前缀 = SAFE)不在
        // 常规工具过滤器里被移除(由 filterMediaTools 单独注入)。
        val input = listOf(
            def("web_search"),
            def("generate_image"),
            def("generate_qr_code"),
            def("generate_video"),
            def("send_sms"),
            def("workspace_write"),
            def("calculator"),
        )

        val result = GroupChatToolPolicy.filterRegularTools(input)

        assertEquals(
            listOf("web_search", "generate_image", "generate_qr_code", "generate_video", "calculator"),
            result.map { it.name },
        )
        assertFalse(result.any { it.name in GroupChatToolPolicy.BLOCKED_MEDIA_TOOLS })
    }

    @Test
    fun keepsRegularToolsWhenNoMediaToolsPresent() {
        val input = listOf(def("web_search"), def("schedule_reminder"))

        val result = GroupChatToolPolicy.filterRegularTools(input)

        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "web_search" })
        assertTrue(result.any { it.name == "schedule_reminder" })
    }

    @Test
    fun filtersHighRiskToolsFromDirectExecutionChannel() {
        // P1-11: 群聊常规工具无审批,高风险工具必须被风险白名单挡在直执行通道外
        val input = listOf(
            def("web_search"),
            def("send_sms"),
            def("workspace_write"),
            def("calculator"),
        )

        val result = GroupChatToolPolicy.filterRegularTools(input)

        assertEquals(listOf("web_search", "calculator"), result.map { it.name })
    }
}
