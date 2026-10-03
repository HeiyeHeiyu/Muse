package io.zer0.muse.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具链"本地重建"保留条数的判定测试。
 *
 * 背景：工具链截断原来只看条数（[MAX_TOOL_CHAIN_MESSAGES]）。但几条**超大**工具结果
 * （日志、大文件、长网页正文）能在条数限额内把上下文窗口撞爆，而生成中途没有压缩机会。
 * 这里锁定两级判定：条数上限照旧，token 超预算时按比例收紧。
 */
class ToolChainTokenBudgetTest {

    @Test
    fun `empty chain keeps nothing to keep`() {
        assertEquals(0, toolChainTailToKeep(chainSize = 0, chainTokens = 0, budgetTokens = 100_000))
        assertEquals(0, toolChainTailToKeep(chainSize = -3, chainTokens = 0, budgetTokens = 100_000))
    }

    @Test
    fun `without a budget only the count cap applies`() {
        assertEquals(5, toolChainTailToKeep(chainSize = 5, chainTokens = 0, budgetTokens = 0))
        assertEquals(
            MAX_TOOL_CHAIN_MESSAGES,
            toolChainTailToKeep(chainSize = MAX_TOOL_CHAIN_MESSAGES * 3, chainTokens = 0, budgetTokens = -1),
        )
    }

    @Test
    fun `chain under half the budget keeps the count-capped tail`() {
        // 预算 100K，工具链只用了 40K（< 50K）→ 不因 token 收紧
        assertEquals(
            MAX_TOOL_CHAIN_MESSAGES,
            toolChainTailToKeep(chainSize = MAX_TOOL_CHAIN_MESSAGES, chainTokens = 40_000, budgetTokens = 100_000),
        )
    }

    @Test
    fun `oversized chain is tightened by the token budget`() {
        // 预算 100K → 尾部预算 50K → 每条按 50 token 计，最多留 1000 条；
        // 链条本身只有 400 条，因此仍全留（条数上限 200? 见断言：取两者较小）
        val kept = toolChainTailToKeep(chainSize = 400, chainTokens = 90_000, budgetTokens = 100_000)
        assertTrue("应不超过条数上限", kept <= MAX_TOOL_CHAIN_MESSAGES)
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
    fun `tighter budget keeps fewer messages than the count cap`() {
        // 链条 1000 条、共 60K token → 平均 60 token/条。
        // 预算充足时由条数上限决定（MAX_TOOL_CHAIN_MESSAGES）；
        // 预算很紧时（尾部预算 600 token）只能留 10 条 —— 必须低于条数上限，
        // 否则说明 token 判定被条数上限完全遮蔽、等于没生效。
        val roomy = toolChainTailToKeep(chainSize = 1_000, chainTokens = 60_000, budgetTokens = 200_000)
        val tight = toolChainTailToKeep(chainSize = 1_000, chainTokens = 60_000, budgetTokens = 1_200)
        assertEquals("预算充足时取条数上限", MAX_TOOL_CHAIN_MESSAGES, roomy)
        assertTrue("预算收紧后应少于条数上限，实际 $tight", tight < MAX_TOOL_CHAIN_MESSAGES)
        assertEquals("尾部预算 600 / 每条 60 → 10 条", 10, tight)
    }

    @Test
    fun `result never exceeds the available chain`() {
        listOf(1, 5, MAX_TOOL_CHAIN_MESSAGES, MAX_TOOL_CHAIN_MESSAGES + 1).forEach { size ->
            listOf(0, 1_000, 100_000).forEach { budget ->
                val kept = toolChainTailToKeep(size, chainTokens = 10_000, budgetTokens = budget)
                assertTrue("保留条数不得超过链长（size=$size budget=$budget）", kept <= size)
            }
        }
    }
}
