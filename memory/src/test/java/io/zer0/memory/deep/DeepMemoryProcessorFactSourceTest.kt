package io.zer0.memory.deep

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepMemoryProcessorFactSourceTest {

    @Test
    fun assistantAttributedTimelineItemsAreExcludedFromLongTermExtraction() {
        val summary = """
            ### 重要事实
            - 用户最近在筹备搬家
            ### 事情经过
            - [用户] 说明了新家的计划
            - [助手] 承诺继续跟进
            - [助手 Vibe 自述] 有些担心遗漏细节
        """.trimIndent()

        val source = filterAssistantAttributedMemoryEntries(summary)

        assertTrue(source.contains("用户最近在筹备搬家"))
        assertTrue(source.contains("[用户]"))
        assertFalse(source.contains("[助手]"))
        assertFalse(source.contains("[助手 Vibe 自述]"))
    }

    @Test
    fun sameUserFactsWithOnlyAssistantTimelineChangesDoNotRequireExtraction() {
        val previous = """
            ### 重要事实
            - 用户最近在筹备搬家
            ### 事情经过
            - [助手 Vibe 自述] 有些担心遗漏细节
        """.trimIndent()
        val current = """
            ### 重要事实
            - 用户最近在筹备搬家
            ### 事情经过
            - [助手 Vibe 自述] 感觉更放心了
        """.trimIndent()

        assertTrue(
            filterAssistantAttributedMemoryEntries(previous) ==
                filterAssistantAttributedMemoryEntries(current),
        )
    }
}
