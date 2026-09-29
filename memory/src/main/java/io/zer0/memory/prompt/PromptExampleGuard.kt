package io.zer0.memory.prompt

import io.zer0.memory.ai.ParsedAnalysis

/**
 * v2.x: 提示词示例泄漏防护。
 *
 * 背景: 提取类提示词([io.zer0.memory.ai.MemoryExtractPrompt] / [FactExtractionPrompt])的
 * 输出格式区带演示样例(如"对青霉素过敏"),弱模型有时会把示例内容照抄进输出,导致示例被当成
 * 用户真实记忆入库(用户反馈:"青霉素"等示例出现在记忆里)。
 *
 * 判定规则(文本级兜底):
 *  - 条目命中了示例词表,但"本次输入原文(对话/摘要)"从未提及该词 → 判定为照抄示例,丢弃;
 *  - 对话里真的提到过 → 视为真实记忆,照常保留(不误伤)。
 *
 * 词表只覆盖各提示词示例里出现过的专有词,避免影响正常内容。
 */
object PromptExampleGuard {

    /** 示例词表:各提示词 Output Format 示例里出现过的专有词(小写比较)。 */
    private val EXAMPLE_TERMS: List<String> = listOf(
        // MemoryExtractPrompt(zh / en)输出示例
        "青霉素", "阿莫西林", "美式咖啡", "张先生", "张三",
        "penicillin", "amoxicillin", "american coffee", "mr. zhang", "zhang san",
        // FactExtractionPrompt(zh / en)输出示例
        "筹备搬家", "论文初稿", "preparing to move", "thesis draft",
    )

    /**
     * 文本是否"照抄了提示词示例"(命中示例词且输入原文从未提及)。
     *
     * @param text 待检文本(事实正文 / 标题 / 链接端点等)
     * @param conversationText 本次提取的输入原文(对话历史 / 摘要+快照)
     */
    fun isUngroundedExample(text: String, conversationText: String): Boolean {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return false
        val conv = conversationText.lowercase()
        return EXAMPLE_TERMS.any { term -> t.contains(term) && !conv.contains(term) }
    }

    /**
     * 清洗整份提取结果:剔除照抄示例的条目(实体 / 更新 / 合并 / 链接 / 画像行)。
     */
    fun sanitize(analysis: ParsedAnalysis, conversationText: String): ParsedAnalysis {
        fun leaked(vararg parts: String?): Boolean = parts.any { p -> !p.isNullOrBlank() && isUngroundedExample(p, conversationText) }

        return analysis.copy(
            mainProblem = analysis.mainProblem?.takeUnless { leaked(it.title, it.content) },
            extractedEntities = analysis.extractedEntities
                .filterNot { leaked(it.title, it.content) },
            updatedEntities = analysis.updatedEntities
                .filterNot { leaked(it.matchTitle, it.newContent) },
            mergedEntities = analysis.mergedEntities.filterNot {
                leaked(it.mergedTitle, it.mergedContent) ||
                    it.sourceTitles.any { t -> isUngroundedExample(t, conversationText) }
            },
            links = analysis.links.filterNot { leaked(it.sourceTitle, it.targetTitle) },
            profileMarkdown = scrubMarkdown(analysis.profileMarkdown, conversationText),
        )
    }

    /** 画像 Markdown 逐行清洗:照抄示例的行整行剔除。 */
    private fun scrubMarkdown(markdown: String?, conversationText: String): String? {
        if (markdown.isNullOrBlank()) return markdown
        val kept = markdown.lines().filterNot { line -> isUngroundedExample(line, conversationText) }
        return kept.joinToString("\n").trim().ifBlank { null }
    }
}
