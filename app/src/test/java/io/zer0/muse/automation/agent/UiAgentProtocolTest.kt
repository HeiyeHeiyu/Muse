package io.zer0.muse.automation.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v2.2.1 GUI Agent 环:动作协议解析单测。
 */
class UiAgentProtocolTest {

    @Test
    fun `解析 tap`() {
        val action = UiAgentProtocol.parse("""do(action="tap", x=540, y=1200)""")
        assertEquals(UiAgentProtocol.Action.Tap(540, 1200), action)
    }

    @Test
    fun `解析 swipe 带时长`() {
        val action = UiAgentProtocol.parse("""do(action="swipe", x1=540, y1=1500, x2=540, y2=600, duration=300)""")
        assertEquals(UiAgentProtocol.Action.Swipe(540, 1500, 540, 600, 300L), action)
    }

    @Test
    fun `解析带引号含逗号的文本`() {
        val action = UiAgentProtocol.parse("""do(action="text", text="你好,世界")""")
        assertEquals(UiAgentProtocol.Action.TextInput("你好,世界"), action)
    }

    @Test
    fun `解析 key 无引号短值`() {
        val action = UiAgentProtocol.parse("""do(action="key", key=back)""")
        assertEquals(UiAgentProtocol.Action.Key("back"), action)
    }

    @Test
    fun `解析 finish`() {
        val action = UiAgentProtocol.parse("""finish(result="已发送消息")""")
        assertEquals(UiAgentProtocol.Action.Finish("已发送消息"), action)
    }

    @Test
    fun `取最后一个表达式`() {
        val raw = """
            我应该点击搜索框。
            do(action="tap", x=1, y=2)
            其实应该先进入搜索页再点。
            do(action="tap", x=100, y=200)
        """.trimIndent()
        assertEquals(UiAgentProtocol.Action.Tap(100, 200), UiAgentProtocol.parse(raw))
    }

    @Test
    fun `finish 晚于 do 时优先 finish`() {
        val raw = """do(action="tap", x=1, y=2) 然后 finish(result="完成")"""
        assertEquals(UiAgentProtocol.Action.Finish("完成"), UiAgentProtocol.parse(raw))
    }

    @Test
    fun `wait 默认与钳制`() {
        assertEquals(UiAgentProtocol.Action.Wait(800L), UiAgentProtocol.parse("""do(action="wait")"""))
        assertEquals(UiAgentProtocol.Action.Wait(10_000L), UiAgentProtocol.parse("""do(action="wait", ms=99999)"""))
    }

    @Test
    fun `非法输出返回 null`() {
        assertNull(UiAgentProtocol.parse("我不知道该怎么做"))
        assertNull(UiAgentProtocol.parse("""do(action="tap", x=abc, y=1)"""))
        assertNull(UiAgentProtocol.parse("""do(action="dance", x=1, y=1)"""))
    }
}
