package io.zer0.muse.crash

/**
 * ANR 判定核心逻辑 —— 纯计算,不依赖 Looper/Handler,便于单测。
 *
 * 设计要点(2026-09-29 修订):
 *  - **ticket 计数法**:看门狗每轮向主线程投递一个 ping,ping 执行时把计数 +1。
 *    判定依据是"计数有没有前进",而不是"距上次响应过了多久" —— 后者在
 *    看门狗线程自身被挂起(进程冻结/深度 Doze)或饿死时会把那段时间错算成主线程阻塞。
 *  - **冻结检测**:若本轮实际耗时远超预期间隔(overshoot 超过 [suspendToleranceMs]),
 *    说明看门狗自己都没能按时醒来,这段时间无法判定主线程 —— 直接重置基线,不报 ANR。
 *    这正是 2026-09-27~29 五份 anr_*.txt 误报的成因:App 退后台被系统冻结 → 回前台解冻,
 *    首轮检查把整个后台时长记成"主线程无响应"(最长 133s),而采到的栈只是恢复前台时的正常工作。
 *  - **去重**:同一段阻塞只报一次,主线程恢复心跳后重新武装。
 *
 * @param timeoutMs 判定阈值:主线程连续无响应超过该时长即判定 ANR
 * @param expectedIntervalMs 看门狗预期的一轮间隔(投递 ping → 等待 → 检查)
 * @param suspendToleranceMs overshoot 容差:实际耗时超出预期间隔这么多,即认为进程被挂起
 */
internal class AnrDetector(
    private val timeoutMs: Long,
    private val expectedIntervalMs: Long,
    private val suspendToleranceMs: Long,
) {
    /** 单轮检测结论。 */
    sealed interface Decision {
        /** 判定 ANR(该段阻塞的首次报告);[silenceMs] 为已累计的阻塞时长。 */
        data class Blocked(val silenceMs: Long) : Decision

        /** 仍在阻塞,但该段已经报过(去重)。 */
        object Suppressed : Decision

        /** 主线程心跳正常。 */
        object Healthy : Decision

        /** 看门狗自身被挂起(进程冻结/深度 Doze):本轮不做判定,基线已重置。 */
        object Suspended : Decision
    }

    /** 上次观察到的 ping 计数(-1 = 尚未建立基线)。 */
    private var lastPongCount: Long = -1L

    /** 本段阻塞的起始时刻(elapsedRealtime);null = 当前无阻塞。 */
    private var blockedSinceMs: Long? = null

    /** 本段阻塞是否已报告过。 */
    private var reported: Boolean = false

    /**
     * 处理一轮检测。
     *
     * @param nowMs 本轮检查时刻(SystemClock.elapsedRealtime)
     * @param postedAtMs 本轮投递 ping 的时刻(同上时钟)
     * @param pongCount 主线程已执行的 ping 次数(单调递增)
     */
    fun onCheck(nowMs: Long, postedAtMs: Long, pongCount: Long): Decision {
        val overshootMs = nowMs - postedAtMs - expectedIntervalMs
        val suspended = overshootMs > suspendToleranceMs
        val alive = pongCount > lastPongCount

        if (suspended || alive) {
            // 看门狗被挂起,或主线程有心跳 → 基线归零(冻结期间的时间不计入阻塞)
            lastPongCount = pongCount
            blockedSinceMs = null
            reported = false
        } else if (blockedSinceMs == null) {
            // 确认进入阻塞:从"这次没被执行的 ping 的投递时刻"起算
            blockedSinceMs = postedAtMs
        }

        val silenceMs = blockedSinceMs?.let { nowMs - it } ?: 0L
        val decision =
            when {
                suspended -> Decision.Suspended
                alive -> Decision.Healthy
                silenceMs >= timeoutMs && !reported -> {
                    reported = true
                    Decision.Blocked(silenceMs)
                }
                silenceMs >= timeoutMs -> Decision.Suppressed
                else -> Decision.Healthy
            }
        return decision
    }
}
