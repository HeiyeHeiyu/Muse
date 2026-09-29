package io.zer0.memory.observe

import java.util.concurrent.ConcurrentHashMap

/**
 * D3-P3: 步骤级失败退避。
 *
 * 连续失败触发指数退避: 第 n 次连续失败后,在 base * 2^(n-1)（封顶 [maxBackoffMs]）
 * 窗口内跳过该步骤运行,防"每小时 tick 无限重试烧 LLM"。成功即清零。
 *
 * 为什么需要: daily pipeline 每小时兜底 tick 会在步骤失败后反复重试,LLM 类失败
 * (限流/超时) 下可能持续烧调用;退避把重试节奏从"每小时无脑重试"收敛为
 * "30min → 1h → 2h → … 封顶 6h",6 小时上限保证"修好即恢复"(用户改配置/网络恢复后
 * 最迟 6 小时内自动重试成功)。
 *
 * 仅内存态: 进程重启清零(重启=新上下文,单次重试无害)。
 * 与 [FailureClassifier] 解耦: 所有到达 [recordFailure] 的真失败(非取消)统一退避。
 */
class MemoryStepBackoff(
    private val baseMs: Long = DEFAULT_BASE_MS,
    private val maxBackoffMs: Long = DEFAULT_MAX_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private data class State(val consecutiveFailures: Int, val nextAllowedAt: Long)

    private val states = ConcurrentHashMap<String, State>()

    /** 当前是否允许运行该步骤(不在退避窗口内)。 */
    fun shouldRun(stepKey: String): Boolean {
        val s = states[stepKey] ?: return true
        return clock() >= s.nextAllowedAt
    }

    /** 记录一次失败,推进退避窗口(指数增长,封顶 [maxBackoffMs])。 */
    fun recordFailure(stepKey: String) {
        val now = clock()
        states.compute(stepKey) { _, prev ->
            val n = (prev?.consecutiveFailures ?: 0) + 1
            // 位移量限制在 8(×256)内防溢出,再统一封顶
            val backoff = (baseMs shl (n - 1).coerceAtMost(8)).coerceAtMost(maxBackoffMs)
            State(n, now + backoff)
        }
    }

    /** 记录一次成功,清零退避。 */
    fun recordSuccess(stepKey: String) {
        states.remove(stepKey)
    }

    /** 当前连续失败次数(观测/测试用)。 */
    fun failureCount(stepKey: String): Int = states[stepKey]?.consecutiveFailures ?: 0

    /** 距下次允许运行还剩多少毫秒(观测/测试用;不在退避时为 0)。 */
    fun remainingBackoffMs(stepKey: String): Long = ((states[stepKey]?.nextAllowedAt ?: 0L) - clock()).coerceAtLeast(0L)

    companion object {
        /** 基础退避 30 分钟。 */
        const val DEFAULT_BASE_MS = 30L * 60 * 1000

        /** 封顶 6 小时(保证"修好即恢复"的最迟重试)。 */
        const val DEFAULT_MAX_MS = 6L * 60 * 60 * 1000
    }
}
