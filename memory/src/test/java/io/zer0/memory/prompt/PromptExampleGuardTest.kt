package io.zer0.memory.prompt

import io.zer0.memory.ai.ParsedAnalysis
import io.zer0.memory.ai.ParsedEntity
import io.zer0.memory.ai.ParsedLink
import io.zer0.memory.ai.ParsedMerge
import io.zer0.memory.ai.ParsedUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.x: [PromptExampleGuard] 单元测试。
 *
 * 覆盖:示例照抄丢弃 / 真实记忆保留(对话提及关键词) / 大小写 / 整份结果清洗。
 */
class PromptExampleGuardTest {

    @Test
    fun `penicillin example is dropped when conversation never mentions it`() {
        assertTrue(
            PromptExampleGuard.isUngroundedExample(
                text = "对青霉素过敏",
                conversationText = "用户聊了天气和晚饭,没有提药物。",
            ),
        )
    }

    @Test
    fun `real penicillin fact survives when conversation mentions keyword`() {
        assertFalse(
            PromptExampleGuard.isUngroundedExample(
                text = "对青霉素过敏",
                conversationText = "用户说自己对青霉素过敏,让我记下来。",
            ),
        )
    }

    @Test
    fun `english examples are case insensitive`() {
        assertTrue(
            PromptExampleGuard.isUngroundedExample(
                text = "Allergic to penicillin",
                conversationText = "user talked about coffee yesterday",
            ),
        )
        assertFalse(
            PromptExampleGuard.isUngroundedExample(
                text = "allergic to penicillin",
                conversationText = "USER IS ALLERGIC TO PENICILLIN",
            ),
        )
    }

    @Test
    fun `unrelated text passes through`() {
        assertFalse(
            PromptExampleGuard.isUngroundedExample(
                text = "用户喜欢爬山",
                conversationText = "随便聊聊",
            ),
        )
        assertFalse(PromptExampleGuard.isUngroundedExample("", "任意对话"))
    }

    @Test
    fun `sanitize removes leaked entities links and merges but keeps grounded ones`() {
        val conversation = "用户:我今天买了美式咖啡,顺便说下我下周要去爬山。"
        val analysis = ParsedAnalysis(
            mainProblem = ParsedEntity(title = "对青霉素过敏", content = "对青霉素过敏"),
            extractedEntities = listOf(
                // 示例照抄(对话未提及青霉素)→ 应被剔除
                ParsedEntity(title = "对青霉素过敏", content = "对青霉素过敏", importance = 0.9f),
                // 真实事实(对话提到了美式咖啡)→ 应保留
                ParsedEntity(title = "买了美式咖啡", content = "买了美式咖啡"),
            ),
            updatedEntities = listOf(
                ParsedUpdate(matchTitle = "过敏史", newContent = "对青霉素过敏"),
                ParsedUpdate(matchTitle = "运动计划", newContent = "下周去爬山"),
            ),
            mergedEntities = listOf(
                ParsedMerge(
                    sourceTitles = listOf("对青霉素过敏"),
                    mergedTitle = "x",
                    mergedContent = "y",
                ),
            ),
            links = listOf(
                ParsedLink(sourceTitle = "对青霉素过敏", targetTitle = "用阿莫西林治疗"),
                ParsedLink(sourceTitle = "买了美式咖啡", targetTitle = "爬山计划"),
            ),
            profileMarkdown = "## 医疗\n- 对青霉素过敏\n## 兴趣\n- 美式咖啡",
        )

        val cleaned = PromptExampleGuard.sanitize(analysis, conversation)

        assertNull(cleaned.mainProblem)
        assertEquals(1, cleaned.extractedEntities.size)
        assertEquals("买了美式咖啡", cleaned.extractedEntities[0].title)
        assertEquals(1, cleaned.updatedEntities.size)
        assertEquals("运动计划", cleaned.updatedEntities[0].matchTitle)
        assertTrue(cleaned.mergedEntities.isEmpty())
        assertEquals(1, cleaned.links.size)
        assertEquals("买了美式咖啡", cleaned.links[0].sourceTitle)
        // 画像:青霉素行被剔除,美式咖啡行(对话提到)保留
        assertEquals("## 医疗\n## 兴趣\n- 美式咖啡", cleaned.profileMarkdown)
    }

    @Test
    fun `grounded examples are not removed when conversation truly mentions them`() {
        val conversation = "用户:帮我记一下,我对青霉素过敏。"
        val analysis = ParsedAnalysis(
            extractedEntities = listOf(
                ParsedEntity(title = "对青霉素过敏", content = "对青霉素过敏", importance = 0.9f),
            ),
        )
        val cleaned = PromptExampleGuard.sanitize(analysis, conversation)
        assertEquals(1, cleaned.extractedEntities.size)
    }
}
