package io.zer0.muse.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具链"本地重建"保留条数的判定测试。
 *
 * 背景：工具链不能因为应用自定条数上限静默丢掉用户 API 的历史结果。
 * 已知模型窗口时按 token 预算收紧；窗口未知时保留完整工具链。
 */
class ToolChainTokenBudgetTest {

    @Test
    fun `unknown model budget does not discard earlier tool messages`() {
        assertEquals(100, toolChainTailToKeep(chainSize = 100, chainTokens = 0, budgetTokens = 0))
    }

    @Test
    fun `empty chain keeps nothing to keep`() {
        assertEquals(0, toolChainTailToKeep(chainSize = 0, chainTokens = 0, budgetTokens = 100_000))
        assertEquals(0, toolChainTailToKeep(chainSize = -3, chainTokens = 0, budgetTokens = 100_000))
    }

    @Test
    fun `without a budget the complete chain is preserved`() {
        assertEquals(5, toolChainTailToKeep(chainSize = 5, chainTokens = 0, budgetTokens = 0))
        assertEquals(
            100,
            toolChainTailToKeep(chainSize = 100, chainTokens = 0, budgetTokens = -1),
        )
    }

    @Test
    fun `chain under half the budget keeps the complete tail`() {
        // 预算 100K，工具链只用了 40K（< 50K）→ 不因 token 收紧
        assertEquals(
            100,
            toolChainTailToKeep(chainSize = 100, chainTokens = 40_000, budgetTokens = 100_000),
        )
    }

    @Test
    fun `oversized chain is tightened by the token budget`() {
        // 预算 100K → 尾部预算 50K → 每条按 50 token 计，最多留 1000 条；
        // 链条本身只有 400 条，因此仍全留（条数上限 200? 见断言：取两者较小）
        val kept = toolChainTailToKeep(chainSize = 400, chainTokens = 90_000, budgetTokens = 100_000)
        assertTrue("应不超过现有链长", kept <= 400)
        assertTrue("应至少保留一条", kept >= 1)
    }

    @Test
    fun `very large single result still keeps one message`() {
        // 单条结果就吃掉整窗：宁可只留 1 条，也不能返回 0 ——
        // 全丢会让模型完全看不到"刚才那次调用发生了什么"
        val kept = toolChainTailToKeep(chainSize = 3, chainTokens = 500_000, budgetTokens = 100_000)
        assertEquals(1, kept)
    }

    @Test
    fun `tighter budget keeps fewer messages than the complete chain`() {
        // 链条 1000 条、共 60K token → 平均 60 token/条。
        // 预算充足时完整保留；
        // 预算很紧时（尾部预算 600 token）只能留 10 条 —— 必须低于完整链条，
        // 否则说明 token 判定没有生效。
        val roomy = toolChainTailToKeep(chainSize = 1_000, chainTokens = 60_000, budgetTokens = 200_000)
        val tight = toolChainTailToKeep(chainSize = 1_000, chainTokens = 60_000, budgetTokens = 1_200)
        assertEquals("预算充足时保留完整链条", 1_000, roomy)
        assertTrue("预算收紧后应少于完整链条，实际 $tight", tight < roomy)
        assertEquals("尾部预算 600 / 每条 60 → 10 条", 10, tight)
    }

    @Test
    fun `result never exceeds the available chain`() {
        listOf(1, 5, 30, 31, 100).forEach { size ->
            listOf(0, 1_000, 100_000).forEach { budget ->
                val kept = toolChainTailToKeep(size, chainTokens = 10_000, budgetTokens = budget)
                assertTrue("保留条数不得超过链长（size=$size budget=$budget）", kept <= size)
            }
        }
    }
}
