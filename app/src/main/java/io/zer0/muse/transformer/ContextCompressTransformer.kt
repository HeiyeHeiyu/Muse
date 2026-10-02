package io.zer0.muse.transformer

import io.zer0.ai.ChatService
import io.zer0.ai.core.ChatCompletion
import io.zer0.ai.core.ChatStreamEvent
import io.zer0.ai.core.MessageRole
import io.zer0.ai.core.UIMessage
import io.zer0.common.Logger
import io.zer0.common.resultOf
import io.zer0.muse.util.TokenEstimator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal const val CONTEXT_COMPRESSED_MARKER = "[COMPRESSED]"

/**
 * 按原始历史顺序保留最近消息与优先级消息。
 *
 * 工具调用和结果可能跨过通用压缩的切点；先合并两个保留集合再回到原列表筛选，
 * 能保留完整相邻关系，避免把 TOOL 结果拼接到 assistant(tool_call) 前面。
 */
internal fun retainMessagesInOriginalOrder(
    messages: List<UIMessage>,
    recent: List<UIMessage>,
    priorityMessages: List<UIMessage>,
): List<UIMessage> {
    val retainedIds = (recent + priorityMessages).map { it.id.toString() }.toSet()
    return messages.filter { it.id.toString() in retainedIds }
}

/** True when the complete payload estimate exceeds the configured compression budget. */
internal fun isOverContextCompressionBudget(messages: List<UIMessage>, tokenBudget: Int): Boolean =
    tokenBudget > 0 && TokenEstimator.estimate(messages) > tokenBudget

/**
 * 上下文压缩 Transformer(Phase 8.1 H1)。
 *
 * 当消息历史超过阈值时,把前面的旧消息压缩成一条 SYSTEM 摘要,
 * 保留最近 N 条原样,避免上下文过长导致 token 爆炸。
 *
 * 行为(v2.3.2: 条数与 token 预算**双口径**触发,见下):
 *  - messages.size <= threshold(默认 20)**且** 总 token 数 <= compress_char_budget(缺省不限)→ 不压缩
 *  - 超过阈值:
 *    1. 跳过头部连续的 SYSTEM 消息(system prompt / RAG / webSearch / lorebook 等动态注入的 prefix)
 *    2. 取 prefix 之后到 recent 之前的部分作为待压缩(跳过已压缩的摘要消息)
 *    3. 调用 LLM 生成摘要
 *    4. 替换为一条 SYSTEM 消息("[COMPRESSED] 历史对话摘要\n\n...")
 *    5. 保留最后 keepRecent 条原样
 *    6. v2.3.2: 结果若未比原文更短(工具密集/可压缩区间过短时摘要可能更长)→ 保留原文
 *
 * 由 [context.extra] 控制参数:
 *  - "compress_enabled" (Boolean, 默认 false): 是否启用压缩
 *  - "compress_threshold" (Int, 默认 20): 触发压缩的消息数阈值
 *  - "compress_keep_recent" (Int, 默认 15): 压缩后保留的最近消息数
 *  - "compress_char_budget" (Int, 默认 0=不限;历史 key 名保留): 触发压缩的 token 预算(调用方按模型上下文窗口给出;
 *    长消息会话靠它提前触发 —— 只按条数判断时"20 条"可能早已超出窗口)
 *
 * 注意: 此 Transformer 会调用 LLM(网络请求),耗时较长。
 *       失败时降级为"截断"(丢弃旧消息,插入标记 SYSTEM 消息告知模型历史被截断)。
 */
class ContextCompressTransformer(
    private val chatService: ChatService,
    /**
     * 可选的对话压缩器(分块并行 + 独立便宜模型)。
     * - 非 null:transform 时委托 [compressor] 完成分块并行压缩(推荐,既有实现)
     * - null:回退到原同步单次 LLM 压缩(向后兼容 / 测试场景)
     */
    private val compressor: ConversationCompressor? = null,
) : Transformer {
    override val name: String = "ContextCompress"

    override suspend fun transform(messages: List<UIMessage>, context: TransformContext): List<UIMessage> {
        val enabled = (context.extra("compress_enabled") as? Boolean) ?: false
        if (!enabled) return messages

        val threshold = (context.extra("compress_threshold") as? Int) ?: DEFAULT_THRESHOLD
        val keepRecent = (context.extra("compress_keep_recent") as? Int) ?: DEFAULT_KEEP_RECENT
        // H10: 手动压缩附加指令(对话框输入),优先于设置级自定义 prompt,空/缺省时用默认
        val instruction = context.extra("compress_instruction") as? String
        // v2.3.2: 历史 key 名为 compress_char_budget,实际统一按 token 预算解释。
        // TokenEstimator 会计入 reasoning/toolCalls/图片,避免压缩在视觉或工具密集会话中触发过晚。
        val tokenBudget = (context.extra("compress_char_budget") as? Int) ?: 0
        val totalTokens = TokenEstimator.estimate(messages)

        // L-COMP6: threshold < keepRecent 时配置语义失效,告警
        if (threshold < keepRecent) {
            Logger.w(name, "compress_threshold($threshold) < compress_keep_recent($keepRecent), 压缩可能无法正常触发")
        }

        val overCount = messages.size > threshold
        val overBudget = isOverContextCompressionBudget(messages, tokenBudget)
        if (!overCount && !overBudget) return messages

        // M-COMP3: 压缩水位线 — 已存在压缩摘要时,要求再涨半个阈值才再次压缩,避免触发频率失控
        // v2.3.2: 水位线同样按双口径判定(条数 / token 预算各留半档余量)
        val hasCompressed =
            messages.any {
                it.role == MessageRole.SYSTEM && it.content.startsWith(CONTEXT_COMPRESSED_MARKER)
            }
        if (hasCompressed) {
            val stillOverCount = messages.size > threshold + threshold / 2
            val stillOverBudget = tokenBudget > 0 && totalTokens > tokenBudget + tokenBudget / 2
            if (!stillOverCount && !stillOverBudget) return messages
        }

        // v1.116 (C1-5): 跳过头部连续的 SYSTEM 消息(system prompt / RAG / webSearch / lorebook 等动态注入的 prefix),
        // 避免压缩这些不可恢复的上下文。一旦遇到第一个非 SYSTEM 消息(用户对话起点),才开始可压缩区间。
        val firstNonSystemIndex = messages.indexOfFirst { it.role != MessageRole.SYSTEM }
        val prefixEnd = if (firstNonSystemIndex >= 0) firstNonSystemIndex else messages.size
        val prefix = messages.subList(0, prefixEnd)

        // v5: 优先级保留 — 工具调用消息和包含工具结果的消息应保留,不压缩
        val priorityIds =
            messages.filter { msg ->
                msg.toolCalls != null || msg.toolCallInfo != null ||
                    msg.role == MessageRole.TOOL || msg.role == MessageRole.SYSTEM
            }.map { it.id.toString() }.toSet()

        // 可压缩区间 = prefix 之后到 recent 之前
        val compressibleEnd = messages.size - keepRecent
        if (prefixEnd >= compressibleEnd) return messages // prefix 本身就占了大部分,无可压缩区间

        // M-COMP3: 跳过已压缩的 SYSTEM 摘要消息,避免摘要叠加摘要
        val toCompress =
            messages.subList(prefixEnd, compressibleEnd).filter {
                !(it.role == MessageRole.SYSTEM && it.content.startsWith(CONTEXT_COMPRESSED_MARKER))
            }
        val recent = messages.takeLast(keepRecent)

        // v5: 把优先级高的消息(工具调用等)保留到 recent 中,避免被压缩。
        // 必须按原历史顺序筛选，而不能用 recent + priorityMessages 拼接：当 assistant(tool_call)
        // 与 TOOL(result) 恰好跨过切点时，拼接会把 result 放到 assistant 前面，破坏 Provider 配对。
        val priorityMessages = toCompress.filter { it.id.toString() in priorityIds }
        val adjustedRecent = retainMessagesInOriginalOrder(messages, recent, priorityMessages)
        val adjustedToCompress = toCompress.filter { it.id.toString() !in priorityIds }

        // Phase 8.5 修复: keepRecent >= messages.size 时 toCompress 为空,跳过避免发无意义 LLM 请求
        if (adjustedToCompress.isEmpty()) return messages

        // v2.3.2: 取摘要的三种结局统一由 [resolveSummary] 给出(它内部优先复用进程内水位线 —— 见 C 的说明,
        // 超时/异常的降级路径也收敛在那里);这里只负责按结局拼装结果。
        val outcome = resolveSummary(adjustedToCompress, instruction, context.sessionId)
        // 超时/异常 → 降级为截断标记(M-COMP4: 告知模型历史被截断,而非静默丢弃全部历史)
        if (outcome is SummaryOutcome.DegradeToMarker) {
            return prefix + listOf(fallbackMessage()) + adjustedRecent
        }
        // v2.3.2: 摘要生成失败(压缩器整体返回 null)→ **保留原文**,不做任何替换。
        // 历史本身还在,失败的只是"摘要"这一步;用占位文本换掉原文才是真正的失忆。
        // (transform 的输入已由上游按 contextSize / token 预算裁剪,保留原文不会无界膨胀。)
        if (outcome !is SummaryOutcome.Ok) {
            Logger.w(name, "compress 全部块失败, 本轮保留原文不压缩")
            return messages
        }
        val summary = outcome.summary

        // M-COMP3: 压缩摘要加 [COMPRESSED] 前缀标记,下次压缩时识别并跳过
        val summaryMsg =
            UIMessage(
                role = MessageRole.SYSTEM,
                content = "$CONTEXT_COMPRESSED_MARKER 历史对话摘要\n\n$summary",
            )
        val compacted = prefix + listOf(summaryMsg) + adjustedRecent
        // v2.3.2: 净收益校验 —— 工具密集会话(大量保留项)或可压缩区间过短时,摘要可能比被替换掉的
        // 原文还长:压缩不但没省上下文,还白花一次 LLM 调用。按字符数比较(便宜的代理指标,
        // 避免在管道里跑 BPE 编码),没变小就保留原文。
        val afterTokens = TokenEstimator.estimate(compacted)
        if (afterTokens >= totalTokens) {
            Logger.i(name, "compress 无净收益($totalTokens → $afterTokens token),保留原文不压缩")
            return messages
        }
        // v2.3.2 (C/D): 摘要**确实被采用**时才记水位线 —— 记录哪些消息已被它覆盖。
        // 记在这里而不是更早,是为了避免"净收益校验否决了这次压缩、原文仍在上下文里,
        // 却把原文标成已摘要"从而在对话树重建时被误过滤(丢历史)。
        CompressionSummaryStore.remember(
            sessionId = context.sessionId,
            coveredIds = adjustedToCompress.map { it.id.toString() }.toSet(),
            summary = summary,
        )
        return compacted
    }

    /** 取摘要的结局(v2.3.2: 把原先散落在 transform 里的 return 收敛成显式类型,便于控制函数长度)。 */
    private sealed interface SummaryOutcome {
        /** 拿到摘要(可能来自缓存水位线,也可能是新生成的)。 */
        data class Ok(val summary: String) : SummaryOutcome

        /** 超时/异常 → 调用方插入"历史暂不可用"截断标记。 */
        object DegradeToMarker : SummaryOutcome

        /** 压缩器整体失败 → 调用方保留原文,本轮不压缩。 */
        object KeepOriginal : SummaryOutcome
    }

    /**
     * 取本次压缩的摘要。
     *
     * v2.3.2 (C): 先查进程内水位线 —— 会话切回 / 从 DB 重载后,同一段历史其实已经被摘要过,
     * 直接复用即可,省掉一次 LLM 调用(以及最长 20s 的首字延迟)。
     * 仅当本次待压缩区间**全部**落在缓存覆盖范围内才复用;部分覆盖会丢历史,交给压缩器重做。
     *
     * 超时/异常的降级路径与"压缩器整体失败"的区分也从这里统一给出(见 [SummaryOutcome])。
     */
    private suspend fun resolveSummary(toCompress: List<UIMessage>, instruction: String?, sessionId: String?): SummaryOutcome {
        val cached = reusableSummary(CompressionSummaryStore.entry(sessionId), toCompress)
        if (cached != null) {
            Logger.i(name, "复用缓存摘要(${toCompress.size} 条),跳过压缩调用")
            return SummaryOutcome.Ok(cached)
        }
        val generated =
            try {
                // v2.x: 压缩调用加超时护栏 — 压缩模型偶发长时间无响应会拖慢整轮生成(实测有 ~30s 首字延迟),
                // 超时后走降级标记路径,不再无限等待。
                val text =
                    withTimeout(COMPRESS_TIMEOUT_MS) {
                        if (compressor != null) {
                            // 优先走分块并行 + 独立便宜模型(既有实现 ChatService.compressConversation)
                            compressWithCompressor(toCompress, instruction)
                        } else {
                            // 回退:原同步单次 LLM 压缩
                            compressMessages(toCompress, instruction)
                        }
                    }
                text?.let { SummaryOutcome.Ok(it) } ?: SummaryOutcome.KeepOriginal
            } catch (timeout: TimeoutCancellationException) {
                // 超时属于压缩失败的一种:降级为截断标记,不中断主流程
                Logger.w(name, "compress timeout after ${COMPRESS_TIMEOUT_MS}ms, fallback to marker")
                SummaryOutcome.DegradeToMarker
            } catch (e: CancellationException) {
                // H-COMP1: 不吞 CancellationException,直接重抛(协程取消必须传播)
                throw e
            } catch (t: Throwable) {
                Logger.e(name, "compress failed, fallback to truncation", t)
                SummaryOutcome.DegradeToMarker
            }
        return generated
    }

    /** M-COMP4: 降级标记消息 — 告知模型历史被截断(而非静默丢弃全部历史)。 */
    private fun fallbackMessage(): UIMessage = UIMessage(
        role = MessageRole.SYSTEM,
        content = "(历史暂不可用,仅保留最近消息)",
    )

    /** 调用 LLM 压缩旧消息为摘要。 */
    private suspend fun compressMessages(oldMessages: List<UIMessage>, instruction: String? = null): String {
        val prompt =
            buildString {
                appendLine("请把下面的对话历史压缩成简洁的摘要,保留关键信息(事实/决策/用户偏好)。")
                appendLine("- 用要点形式,每点一行")
                appendLine("- 不要编造未提及的内容")
                appendLine("- 总长度不超过 800 字")
                // H10: 手动压缩附加指令(如"重点保留预算讨论"),拼进压缩指令
                if (!instruction.isNullOrBlank()) {
                    appendLine("- 附加要求: $instruction")
                }
                appendLine()
                appendLine("对话历史:")
                oldMessages.forEach { msg ->
                    val role =
                        when (msg.role) {
                            MessageRole.USER -> "用户"
                            MessageRole.ASSISTANT -> "助手"
                            MessageRole.SYSTEM -> "系统"
                            MessageRole.TOOL -> "工具"
                        }
                    // L-COMP5: 截断处加 "…" 标记(而非静默截断)
                    // v1.116 (C1-5): 单条消息截断阈值从 500 提升到 1500 字符,
                    // 避免长回复(如代码块/详细分析)被过度截断导致摘要丢失关键信息。
                    val raw = msg.content
                    val text = if (raw.length > MAX_COMPRESS_MSG_CHARS) raw.take(MAX_COMPRESS_MSG_CHARS) + "…" else raw
                    appendLine("[$role] $text")
                }
            }

        val request = listOf(UIMessage(role = MessageRole.USER, content = prompt))
        // H-COMP2 / L-COMP7: 用 resultOf 替代 runCatching.getOrElse
        //  - resultOf 会重抛 CancellationException(不吞协程取消)
        //  - 其他错误转为 Result.Error,onError 记录原始异常后回退 streamChat
        //  - CancellationException 不会到达 getOrNull(),因此不会错误回退
        val completion: ChatCompletion =
            resultOf {
                chatService.completeText(messages = request)
            }.onError { msg, t ->
                Logger.w(name, "completeText 失败,回退 streamChat: $msg", t)
            }.getOrNull() ?: run {
                // 兜底: 流式收集(仅对非 CancellationException 错误到达此处)
                val sb = StringBuilder()
                chatService.streamChat(messages = request).collect { ev ->
                    if (ev is ChatStreamEvent.ContentDelta) sb.append(ev.delta)
                }
                ChatCompletion(text = sb.toString())
            }
        // v1.0.74 fix: 剥离 <think> 推理标签,防止思考内容混入压缩摘要
        return io.zer0.muse.transformer.stripThinkTags(completion.text)
            .ifBlank { "历史对话已压缩(摘要为空)" }
    }

    /**
     * 委托 [compressor] 完成分块并行压缩,把多块摘要合并为单条文本。
     *
     * - v2.3.2: 压缩器**任一块失败**时整体返回 null,由 [transform] 走"保留原文"降级
     *   (不再把"摘要生成失败"占位当成合法摘要 —— 那会让模型自以为见过一段它没见过的历史)
     * - 多块摘要用 "[对话摘要 i/N]" 分段标记合并为一条 SYSTEM 消息
     *   (与 [ContextCompressTransformer] 原"单条 SYSTEM 摘要"语义保持一致,
     *    避免下游 transformer / 持久化逻辑感知分块)
     */
    private suspend fun compressWithCompressor(oldMessages: List<UIMessage>, instruction: String? = null): String? {
        val summaries = compressor!!.compress(oldMessages, instruction) ?: return null
        return when {
            summaries.isEmpty() -> "历史对话已压缩(摘要为空)"
            summaries.size == 1 -> summaries.first()
            else ->
                buildString {
                    summaries.forEachIndexed { idx, s ->
                        append("[对话摘要 ${idx + 1}/${summaries.size}]\n")
                        append(s)
                        if (idx != summaries.lastIndex) append("\n\n")
                    }
                }
        }
    }

    private companion object {
        const val DEFAULT_THRESHOLD = 20
        const val DEFAULT_KEEP_RECENT = 15

        // v1.116 (C1-5): 单条消息送入 LLM 压缩时的最大字符数(原 500,提升到 1500)
        const val MAX_COMPRESS_MSG_CHARS = 1500

        // v2.x: 压缩模型调用超时(毫秒);超时后降级为截断标记,避免拖慢整轮生成
        const val COMPRESS_TIMEOUT_MS = 20_000L
    }
}
