package io.zer0.ai.openai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 工具调用"续片归位"判定测试。
 *
 * ## 对应的真实事故
 *
 * 实测日志（商汤/中转站流式）把一个逻辑调用拆成"带名字的首片 + 多个无名参数续片"：
 *
 * ```
 * index=0  {id, function:{name:"search_memory"}}      ← 首片带名字，参数为空
 * index=1  {function:{arguments:"{\"limit\": 15, "}}  ← 无名续片
 * index=3  {function:{arguments:"query\": \"晏临"}}    ← 无名续片
 * index=4  {function:{arguments:"来\"}"}}              ← 无名续片
 * ```
 *
 * 修复前：无名续片各自变成独立调用 → 参数丢失、真正有名字的调用参数长度为 0 →
 * 下游报"缺少必填参数: query"，同时参数 JSON 被当正文吐给用户。
 *
 * 这里锁定"能不能归位、归位到谁"的判定：**只有一个候选才归位，多个候选一律不猜**。
 */
class ToolCallContinuationCandidateTest {

    /** 造一个"已命名、参数为空"的待补调用。 */
    private fun namedPending(name: String = "search_memory"): ToolCallAccState =
        ToolCallAccState().apply { this.name = name }

    /** 造一个"已命名且已有参数"的调用（不再等待续片）。 */
    private fun namedWithArgs(name: String = "search_memory"): ToolCallAccState =
        ToolCallAccState().apply {
            this.name = name
            args.append("""{"query":"晏临"}""")
        }

    /** 造一个"仅收到参数、名字未到"的调用。 */
    private fun anonymousWithArgs(arguments: String): ToolCallAccState =
        ToolCallAccState().apply { args.append(arguments) }

    @Test
    fun `single named call waiting for arguments is adopted`() {
        val map = mapOf(0 to namedPending())
        assertEquals(0, pendingArgsCandidate(map))
    }

    @Test
    fun `no candidate when nothing is waiting`() {
        assertNull("空表不应有候选", pendingArgsCandidate(emptyMap()))
        assertNull("已带参数的调用不再是候选", pendingArgsCandidate(mapOf(0 to namedWithArgs())))
        assertNull("只有无名调用时不归位（名字还没到）", pendingArgsCandidate(mapOf(1 to anonymousWithArgs("""{"a":1}"""))))
    }

    @Test
    fun `two waiting calls are ambiguous and never guessed`() {
        val map = mapOf(0 to namedPending(), 1 to namedPending())
        assertNull("并行调用都在等参数时必须放弃归位", pendingArgsCandidate(map))
    }

    @Test
    fun `candidate disappears once delta was emitted`() {
        val emitted = namedPending().apply { hasEmitted = true }
        assertNull("已发出 ToolCallDelta 后参数已定型，不能再追加", pendingArgsCandidate(mapOf(0 to emitted)))
    }

    @Test
    fun `fragments merge into a legal json object`() {
        // 续片归位后由累积器拼装；这条锁定"拼出来**能解析成 JSON** 且字段完整"，
        // 即日志里那三段 {"limit": 15, " / query": "晏临 / 来"} 的等价输入。
        // 注意只断言语义（解析后的字段），不断言字符串形状 —— 累积器保留原始空白是正常的。
        val merged = mergeFragments(listOf("""{"limit": 15, """, """"query": "晏临""", """来"}"""))
        val parsed = Json.parseToJsonElement(merged).jsonObject
        assertEquals("15", parsed["limit"].toString())
        assertEquals("\"晏临来\"", parsed["query"].toString())
    }

    @Test
    fun `fragments arriving as whole objects merge without duplication`() {
        // 另一种已知形态：模型把参数拆成多个完整 JSON 对象分片。
        val merged = mergeFragments(listOf("""{"limit":20}""", """{"query":"天气"}"""))
        val parsed = Json.parseToJsonElement(merged).jsonObject
        assertEquals("20", parsed["limit"].toString())
        assertEquals("\"天气\"", parsed["query"].toString())
    }
}

/** 用真实累积器验证续片拼接。 */
private fun mergeFragments(fragments: List<String>): String {
    val acc = ToolCallArgsAccumulator()
    fragments.forEach { acc.append(it) }
    return acc.current()
}
