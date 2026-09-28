package io.zer0.muse.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AnrDetector] 判定逻辑单测。
 *
 * 重点覆盖 2026-09-27~29 五份 anr_*.txt 的误报成因:
 * 进程退后台被系统冻结 → 回前台解冻,首轮检查把整个后台时长当成"主线程无响应"。
 */
class AnrDetectorTest {
    private fun detector() =
        AnrDetector(
            timeoutMs = 5_000L,
            expectedIntervalMs = 2_000L,
            suspendToleranceMs = 3_000L,
        )

    /** 模拟一轮"按时醒来、主线程也执行了 ping"的正常检测。 */
    private fun AnrDetector.tick(
        nowMs: Long,
        pongCount: Long,
    ) = onCheck(nowMs = nowMs, postedAtMs = nowMs - 2_000L, pongCount = pongCount)

    @Test
    fun `心跳正常时不判定 ANR`() {
        val d = detector()
        var pong = 0L
        var t = 10_000L
        repeat(10) {
            t += 2_000L
            pong += 1L
            assertEquals(AnrDetector.Decision.Healthy, d.tick(t, pong))
        }
    }

    @Test
    fun `主线程连续无响应超过阈值判定 ANR 且只报一次`() {
        val d = detector()
        var t = 10_000L
        // 前两轮心跳正常
        assertEquals(AnrDetector.Decision.Healthy, d.tick(t, 1L))
        t += 2_000L
        assertEquals(AnrDetector.Decision.Healthy, d.tick(t, 2L))

        // 主线程卡住:ping 计数不再前进
        t += 2_000L
        assertEquals(AnrDetector.Decision.Healthy, d.tick(t, 2L)) // 阻塞约 2s,未达阈值
        t += 2_000L
        assertEquals(AnrDetector.Decision.Healthy, d.tick(t, 2L)) // 约 4s
        t += 2_000L
        val blocked = d.tick(t, 2L) // 约 6s → 判定
        assertTrue("应判定 ANR,实际 $blocked", blocked is AnrDetector.Decision.Blocked)
        assertTrue("阻塞时长应 >= 5s,实际 ${(blocked as AnrDetector.Decision.Blocked).silenceMs}", blocked.silenceMs >= 5_000L)

        // 仍卡住:不重复报告
        t += 2_000L
        assertEquals(AnrDetector.Decision.Suppressed, d.tick(t, 2L))

        // 恢复心跳:重新武装
        t += 2_000L
        assertEquals(AnrDetector.Decision.Healthy, d.tick(t, 3L))
    }

    @Test
    fun `进程被冻结后回前台不得误报 ANR`() {
        val d = detector()
        // 冻结前:心跳正常
        assertEquals(AnrDetector.Decision.Healthy, d.tick(10_000L, 7L))

        // 进程被冻结 93 秒:看门狗自己这一轮也睡了 93 秒
        // (真实场景:App 退后台被 cached-app freezer 挂起,回前台才解冻)
        val decision = d.onCheck(nowMs = 103_000L, postedAtMs = 10_000L, pongCount = 7L)
        assertEquals(AnrDetector.Decision.Suspended, decision)

        // 解冻后主线程立刻执行积压的 ping → 正常
        assertEquals(AnrDetector.Decision.Healthy, d.tick(105_000L, 9L))
    }

    @Test
    fun `冻结容差以内仍按阻塞累计`() {
        val d = detector()
        var t = 10_000L
        d.tick(t, 1L)
        // 每轮只多花 1s(未超 3s 容差)→ 属于"看门狗正常但主线程没响应"
        t += 2_000L
        d.onCheck(nowMs = t + 1_000L, postedAtMs = t, pongCount = 1L)
        t += 3_000L
        d.onCheck(nowMs = t, postedAtMs = t - 2_000L, pongCount = 1L)
        t += 2_000L
        val decision = d.onCheck(nowMs = t, postedAtMs = t - 2_000L, pongCount = 1L)
        assertTrue("应判定 ANR,实际 $decision", decision is AnrDetector.Decision.Blocked)
    }

    @Test
    fun `冻结期间被挂起的看门狗不会带出过期阻塞状态`() {
        val d = detector()
        // 先进入阻塞
        var t = 10_000L
        d.tick(t, 1L)
        repeat(3) {
            t += 2_000L
            d.onCheck(nowMs = t, postedAtMs = t - 2_000L, pongCount = 1L)
        }
        // 阻塞期间进程被冻结(例如用户此时切走 App)
        assertEquals(AnrDetector.Decision.Suspended, d.onCheck(nowMs = t + 60_000L, postedAtMs = t, pongCount = 1L))
        // 解冻且主线程恢复:不得因为"冻结前那一段"再报一次
        assertEquals(AnrDetector.Decision.Healthy, d.tick(t + 62_000L, 2L))
    }
}
