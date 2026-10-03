package io.zer0.memory.summary

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 上下文检查点 —— 会话压缩（context compaction）的持久化产物。
 *
 * ## 为什么需要它
 *
 * 压缩如果只改内存里的消息列表，会有三个后果：重新打开会话就回到全量历史（等于没压）、
 * 每次重载都要再花一次摘要调用、以及"压缩"在用户眼里只是插了一条消息而不是一次上下文重置。
 * 把压缩结果落成一条**有边界指针的会话事件**，才能让"压缩后的上下文"成为稳定事实。
 *
 * ## 边界指针为什么用 seq 而不是消息 id 集合
 *
 * 消息表已有单调递增的 `seq` 列。以 `covered_seq`（"覆盖到最后一条消息的 seq"）为边界：
 *  - 组装上下文只需 `seq > covered_seq` 一个条件，不必携带 id 集合；
 *  - 旧消息被删除/编辑不会让边界整体失效（id 集合会）；
 *  - 跨进程稳定，重开应用依然成立。
 *
 * [lastCoveredMessageId] 与 [coveredCount] 仅作**一致性校验**：边界当刻最后一条消息的 id 与
 * 覆盖条数。读回时若该 id 已不存在，说明历史被改动过，检查点应作废并重新生成（见 DAO 使用方）。
 *
 * ## 滚动而非追加
 *
 * 每个会话只保留一条检查点（[sessionId] 主键，覆盖式 upsert）：新一次压缩把"旧检查点 + 新增区间"
 * 一起改写成新检查点。结构上杜绝"摘要套摘要"层层叠加。
 *
 * @property coveredSeq 已并入检查点的最后一条消息的 seq；组装上下文时只取 seq 大于它的消息
 * @property lastCoveredMessageId 边界当刻最后一条消息 id（一致性校验用，可为空字符串）
 * @property coveredCount 已覆盖的消息条数（审计/展示用）
 * @property summary 检查点正文（结构化格式，见 ContextCheckpointFormat）
 * @property tokensBefore 压缩前上下文 token 估算
 * @property tokensAfter 压缩后上下文 token 估算
 * @property strategy 产出方式：summary(模型摘要) / local(零请求本地重建) / truncate(硬截断)
 * @property reason 触发原因：auto(占用率) / manual(用户手动) / midrun(生成中途接缝)
 * @property updatedAt 写入时间（epoch millis）
 */
@Serializable
@Entity(tableName = "context_checkpoints")
data class ContextCheckpointEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "covered_seq")
    val coveredSeq: Long,

    @ColumnInfo(name = "last_covered_message_id", defaultValue = "''")
    val lastCoveredMessageId: String = "",

    @ColumnInfo(name = "covered_count", defaultValue = "0")
    val coveredCount: Int = 0,

    /**
     * 累计并入摘要的消息总条数（滚动累加，仅用于界面展示"已压缩多少条"）。
     *
     * 与 [coveredCount] 的区别：[coveredCount] 是本次压缩新并入的条数（审计/校验用），
     * 这里是历史累计值，语义随时间单调增长，适合展示。
     */
    @ColumnInfo(name = "total_covered_count", defaultValue = "0")
    val totalCoveredCount: Int = 0,

    @ColumnInfo(name = "summary")
    val summary: String,

    @ColumnInfo(name = "tokens_before", defaultValue = "0")
    val tokensBefore: Int = 0,

    @ColumnInfo(name = "tokens_after", defaultValue = "0")
    val tokensAfter: Int = 0,

    @ColumnInfo(name = "strategy", defaultValue = "summary")
    val strategy: String = STRATEGY_SUMMARY,

    @ColumnInfo(name = "reason", defaultValue = "auto")
    val reason: String = REASON_AUTO,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "space_id", defaultValue = "default")
    val spaceId: String = "default",
) {
    /** 压缩净省下的 token（可能为 0 或负值，读回方自行决定如何展示）。 */
    val savedTokens: Int get() = (tokensBefore - tokensAfter).coerceAtLeast(0)

    companion object {
        /** 模型摘要产出。 */
        const val STRATEGY_SUMMARY = "summary"

        /** 零模型请求的本地重建产出。 */
        const val STRATEGY_LOCAL = "local"

        /** 硬截断产出（摘要不可用时的最后一道防线）。 */
        const val STRATEGY_TRUNCATE = "truncate"

        /** 占用率触发。 */
        const val REASON_AUTO = "auto"

        /** 用户手动触发。 */
        const val REASON_MANUAL = "manual"

        /** 生成过程中每轮接缝触发。 */
        const val REASON_MIDRUN = "midrun"
    }
}
