package io.zer0.memory.ticker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记忆滚动摘要与"今日编译"的触发间隔测试。
 *
 * 背景：用户反馈 10 轮才更新一次滚动摘要太迟（刚发生的事容易错过），改为每 5 轮；
 * 但"编译今日记忆 + 装配"的写入与大模型调用明显更重，保持 10 轮一次。
 *
 * 这里锁死两件事：① 两个间隔各自生效；② **摘要间隔整除编译间隔** ——
 * 否则编译会跑在摘要之前，用上一轮的旧摘要去编译今日记忆。
 */
class MemorySummaryIntervalTest {

    @Test
    fun `rolling summary fires every five turns`() {
        val expected = setOf(5, 10, 15, 20, 25)
        (1..25).forEach { turn ->
            val should = turn in expected
            assertTrue("第 $turn 轮的摘要判定应为 $should", shouldRollSummary(turn) == should)
        }
    }

    @Test
    fun `compile today stays at every ten turns`() {
        val expected = setOf(10, 20, 30)
        (1..30).forEach { turn ->
            val should = turn in expected
            assertTrue("第 $turn 轮的编译判定应为 $should", shouldCompileToday(turn) == should)
        }
    }

    @Test
    fun `compile never runs without a fresh summary in the same turn`() {
        (1..120).forEach { turn ->
            if (shouldCompileToday(turn)) {
                assertTrue(
                    "编译必须与摘要同时发生（第 $turn 轮），否则会用旧摘要编译今日记忆",
                    shouldRollSummary(turn),
                )
            }
        }
    }

    @Test
    fun `zero and negative turn counts never trigger`() {
        listOf(0, -1, -5).forEach { turn ->
            assertFalse("第 $turn 轮不应触发摘要", shouldRollSummary(turn))
            assertFalse("第 $turn 轮不应触发编译", shouldCompileToday(turn))
        }
    }

    @Test
    fun `summary interval divides the compile interval`() {
        val summary = MemoryTicker.TURNS_PER_SUMMARY
        val compile = MemoryTicker.TURNS_PER_COMPILE
        assertTrue("摘要间隔($summary)必须整除编译间隔($compile)", compile % summary == 0)
        assertTrue("摘要应比编译更频繁", summary < compile)
    }
}
