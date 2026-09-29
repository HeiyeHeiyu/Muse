package io.zer0.memory.observe

import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

/**
 * D3-P1: 记忆管线统一步骤运行器。
 *
 * 把 [io.zer0.memory.ticker.MemoryTicker] 的既有步骤(rollingSummary / compileDaily /
 * compileToday / rollDailyWindow / compileFacts / deepMemory 等)包成同一层,统一采集:
 *  - 所属阶段([MemoryStage])、步骤键、触发来源
 *  - 耗时、成功/失败结果
 *  - 失败分类([FailureKind],仅记录)
 *  - 每步累计计数(total/successes/failures)
 *
 * 并同步上报既有健康体系([onStepSuccess]/[onStepFailure] → MemoryTicker 的
 * markSuccess/markFailure → healthFlow),不另起健康体系。
 *
 * 行为契约(保证包装前后等价):
 *  - [CancellationException] 原样重抛,绝不吞掉协程取消信号;
 *  - 健康回调与日志写入都被 [runCatching] 保护,观测本身绝不改变控制流;
 *  - [HealthMode.NONE] 时完全不触碰健康体系(供 rollingSummary 这类"成功但无变化
 *    不应算成功"的特殊步骤使用,由调用方自行上报健康)。
 */
class MemoryStepRunner(
    /** 步骤成功的健康上报;null 表示不上报(如无健康体系的环境)。 */
    private val onStepSuccess: ((String) -> Unit)? = null,
    /** 步骤失败的健康上报;null 表示不上报。 */
    private val onStepFailure: ((String, Throwable) -> Unit)? = null,
    /** 管线运行日志;null 表示禁用观测(测试/无 filesDir 环境)。 */
    private val pipelineLog: PipelineLog? = null,
    /** 时钟,便于测试注入。 */
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /** 健康上报模式。 */
    enum class HealthMode {
        /** 成功/失败都上报到既有健康体系。 */
        AUTO,

        /** 不上报,由调用方自行决定(用于 changed 语义特殊的步骤)。 */
        NONE,
    }

    /** 单次运行结果。 */
    sealed interface StepResult<out T> {
        data class Success<T>(val value: T, val durationMs: Long) : StepResult<T>
        data class Failure(val error: Throwable, val kind: FailureKind, val durationMs: Long) : StepResult<Nothing>
    }

    private val counters = ConcurrentHashMap<String, StepCounters>()

    /** 读取某步的累计计数(不存在时返回零值)。 */
    fun countersOf(stepKey: String): StepCounters = counters[stepKey] ?: StepCounters()

    /** 全部步骤计数快照。 */
    fun snapshotCounters(): Map<String, StepCounters> = counters.toMap()

    /**
     * D3-P1: 直接记录一次"成功"(不包 block)。
     *
     * 供既有 try/catch 结构的调用方使用——调用方维持自己精细的控制流,
     * 只借用运行器的累计计数与管线日志;健康上报由调用方自理。
     */
    fun recordSuccess(
        stepKey: String,
        durationMs: Long,
        trigger: String,
        scope: String? = null,
        spaceId: String? = null,
        detail: String? = null,
    ) {
        val c = bump(stepKey, success = true)
        runCatching {
            pipelineLog?.appendStep(
                stage = MemoryStage.ofStep(stepKey), step = stepKey, trigger = trigger, ok = true,
                durationMs = durationMs, failureKind = null, error = null,
                scope = scope, spaceId = spaceId, detail = detail, counters = c,
            )
        }
    }

    /**
     * D3-P1: 直接记录一次"失败"(不包 block);失败分类仅记录,不改控制流。
     */
    fun recordFailure(
        stepKey: String,
        error: Throwable,
        durationMs: Long,
        trigger: String,
        scope: String? = null,
        spaceId: String? = null,
    ) {
        val c = bump(stepKey, success = false)
        val kind = FailureClassifier.classify(error)
        runCatching {
            pipelineLog?.appendStep(
                stage = MemoryStage.ofStep(stepKey), step = stepKey, trigger = trigger, ok = false,
                durationMs = durationMs, failureKind = kind, error = error,
                scope = scope, spaceId = spaceId, detail = null, counters = c,
            )
        }
    }

    /**
     * 运行一个步骤。
     *
     * @param stepKey 步骤键(与 MemoryTicker.STEP_KEYS 对齐)
     * @param stage 所属阶段(仅标注)
     * @param trigger 触发来源
     * @param scope 记忆作用域(可空)
     * @param spaceId 记忆空间(可空)
     * @param health 健康上报模式(默认 AUTO)
     * @param detailOf 由成功结果派生附加说明(如 rollingSummary 的 changed/unchanged)
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun <T> run(
        stepKey: String,
        stage: MemoryStage,
        trigger: String,
        scope: String? = null,
        spaceId: String? = null,
        health: HealthMode = HealthMode.AUTO,
        detailOf: (T) -> String? = { null },
        block: suspend () -> T,
    ): StepResult<T> {
        val start = clock()
        return try {
            val value = block()
            val duration = clock() - start
            val c = bump(stepKey, success = true)
            if (health == HealthMode.AUTO) runCatching { onStepSuccess?.invoke(stepKey) }
            val detail = runCatching { detailOf(value) }.getOrNull()
            runCatching {
                pipelineLog?.appendStep(
                    stage = stage, step = stepKey, trigger = trigger, ok = true, durationMs = duration,
                    failureKind = null, error = null, scope = scope, spaceId = spaceId,
                    detail = detail, counters = c,
                )
            }
            StepResult.Success(value, duration)
        } catch (e: CancellationException) {
            // 协程取消是终态,原样重抛(不记录为步骤失败)
            throw e
        } catch (e: Throwable) {
            val duration = clock() - start
            val kind = FailureClassifier.classify(e)
            val c = bump(stepKey, success = false)
            if (health == HealthMode.AUTO) runCatching { onStepFailure?.invoke(stepKey, e) }
            runCatching {
                pipelineLog?.appendStep(
                    stage = stage, step = stepKey, trigger = trigger, ok = false, durationMs = duration,
                    failureKind = kind, error = e, scope = scope, spaceId = spaceId,
                    detail = null, counters = c,
                )
            }
            StepResult.Failure(e, kind, duration)
        }
    }

    private fun bump(stepKey: String, success: Boolean): StepCounters = counters.compute(stepKey) { _, prev ->
        val base = prev ?: StepCounters()
        base.copy(
            total = base.total + 1,
            successes = base.successes + if (success) 1 else 0,
            failures = base.failures + if (success) 0 else 1,
        )
    } ?: StepCounters()
}

/** 单步累计计数。 */
data class StepCounters(
    val total: Int = 0,
    val successes: Int = 0,
    val failures: Int = 0,
)
