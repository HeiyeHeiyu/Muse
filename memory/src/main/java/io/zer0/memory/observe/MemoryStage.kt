package io.zer0.memory.observe

/**
 * D3-P1: 记忆管线五阶段标签。
 *
 * 用于给现有 [io.zer0.memory.ticker.MemoryTicker] 步骤标注其对应的概念阶段,
 * 让观测数据(见 [PipelineLog])能按 Ingest→Extract→Index→Retrieve→Inject 聚合。
 *
 * 注意:P1 阶段仅做"标注",不改变现有步骤的实际实现或调用顺序;真正的
 * 五阶段解耦(每步可单独失败/重试/观测)留待后续阶段。
 */
enum class MemoryStage {
    /** 记忆产生入口:对话实时保存、每日深挖、手动/回填导入。 */
    INGEST,

    /** LLM 提取:会话 → 摘要、摘要 → 原子事实。 */
    EXTRACT,

    /** 写入/编排存储:编译产物落 Room/文件、窗口滚动、对账。 */
    INDEX,

    /** 注入侧检索:按当前问题召回相关记忆。 */
    RETRIEVE,

    /** system prompt 组装注入。 */
    INJECT,
    ;

    companion object {
        /**
         * 管线段落 → 主阶段的默认映射。
         *
         * 每个段落一步只给一个"主阶段"(步骤内部常同时含抽取与落盘,此处按其
         * 产物归属归类):摘要/事实抽取归 EXTRACT,编译产物编排归 INDEX。
         */
        fun ofStep(stepKey: String): MemoryStage = when (stepKey) {
            "rollingSummary" -> EXTRACT
            "deepMemory", "deepMemory.sessionEnd" -> EXTRACT
            "compileDaily", "compileToday", "rollDailyWindow", "compileFacts",
            "assembleWeekFromDaily", "reconcileFacts",
            -> INDEX
            "autoSave" -> EXTRACT
            else -> INDEX
        }

        /** 检索阶段步骤(注入侧,当前由 SystemPromptAssembler 触发)。 */
        fun ofRetrieveStep(stepKey: String): MemoryStage = when (stepKey) {
            "relevantMemory" -> RETRIEVE
            "longTermMemory" -> INJECT
            else -> RETRIEVE
        }
    }
}
