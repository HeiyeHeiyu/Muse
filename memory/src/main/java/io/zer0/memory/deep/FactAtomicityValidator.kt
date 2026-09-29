package io.zer0.memory.deep

import io.zer0.memory.fact.FactStore

/**
 * D6 第 2 期: DeepMemory 提取输出的原子性校验层。
 *
 * 背景: 提取 prompt([io.zer0.memory.prompt.FactExtractionPrompt])已要求"每条事实一个断言",
 * 但 LLM 输出不保证。写入 facts 表前在这里做一次确定性校验,把"连接词串联多个断言"的条目
 * 保守地拆成多条,降低"一条 fact 一段话"的存量堆积。
 *
 * 策略(宁可不拆,不可误伤):
 *  1. 只认强连接词/分号 —— “；;”、以及“并且/而且/同时”与逗号引导的“另外/此外/以及”；
 *     句内普通逗号、“和/或”不拆(如“喜欢摄影和旅行”属单一偏好)。
 *  2. 拆分后每段须 ≥ [MIN_PART_CHARS] 字,段数落在 2..[MAX_PARTS],否则放弃拆分,原样保留。
 *  3. 至少两段各自含谓词词素(或英文单词),避免把“名词枚举”误拆。
 *  4. 过短的整体( < [MIN_TOTAL_CHARS])不处理,避免误伤短句。
 *  5. 任何不满足条件的情形 → 原文原样返回,绝不丢字。
 *
 * 不改变 facts 表 schema(predicate 列属第 3 期);拆分条目复制同源元数据
 * (tags/time/importance/category/confidence/source/entityKey/sessionId 等)。
 * 与提取 prompt 的关系: prompt 是"劝导层",本类是"兜底层",两者互不替代。
 */
object FactAtomicityValidator {

    /** 低于此长度的整体文本不拆分(避免误伤短句)。 */
    const val MIN_TOTAL_CHARS = 6

    /** 拆分后每段的最小长度。 */
    const val MIN_PART_CHARS = 4

    /** 单条事实最多拆成几段,超过视为异常输出,不拆。 */
    const val MAX_PARTS = 5

    /** 校验结果。 */
    data class Result(
        val facts: List<FactStore.Fact>,
        /** 被拆分的原始条目数。 */
        val splitCount: Int,
        /** 拆分后净增条目数(facts.size - 输入 size)。 */
        val addedCount: Int,
    )

    /**
     * 对提取输出做原子性校验,返回拆分后的事实列表。
     * 无法安全拆分的条目原样保留,因此输出永不缺少任何信息。
     */
    fun enforce(facts: List<FactStore.Fact>): Result {
        if (facts.isEmpty()) return Result(facts, 0, 0)
        val out = ArrayList<FactStore.Fact>(facts.size)
        var splitCount = 0
        for (fact in facts) {
            val parts = splitAssertions(fact.fact)
            if (parts.size >= 2) {
                parts.forEach { part -> out += fact.copy(fact = part) }
                splitCount++
            } else {
                out += fact
            }
        }
        return Result(out, splitCount, out.size - facts.size)
    }

    /**
     * 尝试把一条事实文本拆成多个断言。
     * 不满足拆分条件时返回单元素列表(即原文本),调用方据此判断是否发生了拆分。
     */
    fun splitAssertions(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.length < MIN_TOTAL_CHARS) return listOf(trimmed)
        if (!SPLIT_POINTS.containsMatchIn(trimmed)) return listOf(trimmed)

        val parts = trimmed.split(SPLIT_POINTS)
            .map { cleanPart(it) }
            .filter { it.isNotEmpty() }
        if (parts.size < 2 || parts.size > MAX_PARTS) return listOf(trimmed)
        if (parts.any { it.length < MIN_PART_CHARS }) return listOf(trimmed)
        // 至少两段各自含谓词,排除“名词枚举被连接词拆开”的误伤
        if (parts.count { partHasPredicate(it) } < 2) return listOf(trimmed)

        val distinct = parts.distinct()
        if (distinct.size < 2) return listOf(trimmed)
        return distinct
    }

    /** 去掉段首尾空白与句读。 */
    private fun cleanPart(part: String): String = part.trim().trimEnd('。', '，', '；', ';', '.', '、', '！', '!', '？', '?').trim()

    /** 该段是否含谓词词素(中文)或英文单词。 */
    private fun partHasPredicate(part: String): Boolean = PREDICATE_TOKENS.any { part.contains(it) } || LATIN_WORD_RE.containsMatchIn(part)

    /**
     * 拆分点。逗号引导的连接词(“，另外”等)整体消费逗号,避免残留段首标点;
     * “并且/而且/同时”允许无逗号出现(“喜欢读书并且喜欢写作”)。
     * 正则分支从左到右匹配,故“，并且”先于“并且”命中。
     */
    private val SPLIT_POINTS = Regex("(?:[；;]|，并且|，而且|，同时|，另外|，此外|，以及|并且|而且|同时)")

    /** 英文词(用于英文事实的谓词门)。 */
    private val LATIN_WORD_RE = Regex("[A-Za-z]{2,}")

    /**
     * 中文谓词词素表 —— 只用于“这段像不像一个判断”的粗筛,不求穷尽;
     * 命中不足时放弃拆分(宁漏拆不误拆)。
     * 刻意排除“在/学/用/做/看/听/读/写/玩/养”等易作名词子串的单字。
     */
    private val PREDICATE_TOKENS = listOf(
        "喜欢", "爱", "讨厌", "害怕", "希望", "想要", "打算", "计划", "准备", "正在", "住在", "来自",
        "从事", "负责", "习惯", "经常", "通常", "工作", "生活", "学习", "参加", "完成", "使用",
        "觉得", "认为", "需要", "过敏", "素食", "跑步", "游泳", "旅行", "摄影", "健身", "爬山", "读书", "锻炼",
        "是", "有", "会", "要", "想",
    )
}
