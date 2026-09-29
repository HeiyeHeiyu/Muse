// v2.3.1: 本文件为模型侧上下文/简报文本(非 UI 文案),从 ui/chat 迁到 session/,
// 与 check_hardcoded_cjk 的扫描范围(ui/ 的 Compose 文案)保持一致。
package io.zer0.muse.session

import io.zer0.ai.core.MessageRole
import io.zer0.ai.core.UIMessage
import io.zer0.muse.util.TokenEstimator

/**
 * v2.x 导入预热:外部导入 / 备份恢复会话的首轮上下文策略。
 *
 * 动机:导入会话通常内含长历史,而默认上下文按"条数"裁剪
 * (Assistant.contextMessageSize / limitContextWithContext,默认 20 条)——长历史几乎全被
 * 砍掉,模型首轮近乎失忆。预热策略把首轮从"条数裁剪"换成"token 预算裁剪":
 *
 * 1. 全量优先 —— 预算内尽可能携带完整历史,超出时从最旧截起(至少保留最新一条);
 * 2. 仅补一次 —— 由 SessionEntity.warmupPending 标记,首轮成功回复后清除
 *    (见 SessionRepository.clearWarmupPending / ChatGenerationController.finalizeResponse);
 * 3. 防"认领" —— 附带系统简报,说明历史来自外部导入、助手回复由其他模型生成,
 *    要求只回应用户最新消息(见 [briefingMessage]);
 * 4. 图片按文本计量 —— 预算估算直接复用 [TokenEstimator] 口径(含每图约 1000 token),
 *    不做视觉特判。
 */
object WarmupHistory {
    /** token 预算占模型上下文窗口的比例(为输出与系统提示词预留空间)。 */
    const val BUDGET_RATIO = 0.6

    /** contextMaxTokens 未知时的兜底预算(token)。 */
    const val FALLBACK_BUDGET_TOKENS = 12_000

    /** 截断结果。[truncated]=true 表示丢弃了最旧的部分历史。 */
    data class TrimResult(val history: List<UIMessage>, val truncated: Boolean)

    /** 依据模型上下文窗口计算历史 token 预算。 */
    fun budgetTokensFor(contextMaxTokens: Int): Int = if (contextMaxTokens > 0) {
        (contextMaxTokens * BUDGET_RATIO).toInt()
    } else {
        FALLBACK_BUDGET_TOKENS
    }

    /**
     * v2.3.2: 触发上下文压缩的字符预算(与 [budgetTokensFor] 同一口径:窗口的 [BUDGET_RATIO])。
     *
     * 动机:压缩阈值原先只看"条数"(默认 20 条 / 实验档 10 条),而长消息会话(每条几千字)
     * 在 20 条时早已超出模型窗口 —— 触发太晚,压缩还没来得及生效请求就先超限了。
     * 中文约 1 字符 ≈ 1 token,故预算直接以字符数计,与预热的 token 预算共用一个比例,
     * 避免"阈值按条数、预热按 token、硬上限按 payload"三套口径互不核算。
     */
    fun compressCharBudgetFor(contextMaxTokens: Int): Int = budgetTokensFor(contextMaxTokens)

    /**
     * 从最新往旧累计 token,超出预算则截断。
     *
     * 至少保留最后一条(即便其单独超出预算,兜底避免空历史);
     * [estimate] 可注入,便于单测(默认走 [TokenEstimator],包含每图约 1000 token 的计量)。
     */
    fun trimToBudget(
        history: List<UIMessage>,
        budgetTokens: Int,
        estimate: (UIMessage) -> Int = { TokenEstimator.estimate(listOf(it)) },
    ): TrimResult {
        if (history.isEmpty()) return TrimResult(history, false)
        var accumulated = 0
        var cutIndex = history.size
        for (i in history.indices.reversed()) {
            val tokens = estimate(history[i]).coerceAtLeast(1)
            if (accumulated > 0 && accumulated + tokens > budgetTokens) break
            accumulated += tokens
            cutIndex = i
        }
        return if (cutIndex <= 0) {
            TrimResult(history, false)
        } else {
            TrimResult(history.subList(cutIndex, history.size).toList(), true)
        }
    }

    /** 首轮系统简报:防模型把导入历史"认领"为自己的表述。 */
    fun briefingMessage(truncated: Boolean): UIMessage = UIMessage(
        role = MessageRole.SYSTEM,
        content = buildString {
            append("【导入历史说明】本会话的早期消息由外部平台导入,其中助手回复由其他 AI 模型生成。")
            append("请直接回应用户的最新消息,不要总结、复述或重新回答历史中的旧消息,")
            append("也不要把历史中助手的回复当作你自己的表述。")
            if (truncated) append("(历史较长,此处已按上下文预算保留最近部分。)")
        },
    )
}
