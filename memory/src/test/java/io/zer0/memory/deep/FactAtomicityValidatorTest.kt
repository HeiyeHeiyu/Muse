package io.zer0.memory.deep

import io.zer0.memory.fact.FactStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D6 第 2 期: [FactAtomicityValidator] 单元测试。
 *
 * 覆盖三类:
 *  - 正例:含连接词/分号的多断言条目应被拆开
 *  - 反例:单一断言、名词枚举、短句不得被拆
 *  - 边界:段数上限、谓词门、去重、元数据透传、计数
 *
 * 纯 JVM 测试,不依赖 Android/Room(校验层只处理字符串 + FactStore.Fact 数据类)。
 */
class FactAtomicityValidatorTest {

    // ── 正例 ───────────────────────────────────────────────────────────────

    @Test
    fun `splits two assertions joined by comma connective`() {
        val parts = FactAtomicityValidator.splitAssertions("用户在北京工作，而且周末喜欢爬山")
        assertEquals(listOf("用户在北京工作", "周末喜欢爬山"), parts)
    }

    @Test
    fun `splits two assertions joined by bare connective`() {
        val parts = FactAtomicityValidator.splitAssertions("他喜欢读书并且喜欢写作")
        assertEquals(listOf("他喜欢读书", "喜欢写作"), parts)
    }

    @Test
    fun `splits on semicolon`() {
        val parts = FactAtomicityValidator.splitAssertions("用户正在准备搬家；同时在找新工作")
        assertEquals(2, parts.size)
        assertEquals("用户正在准备搬家", parts[0])
        assertTrue("第二段应保留“找新工作”这一断言", parts[1].contains("找新工作"))
    }

    @Test
    fun `splits medical multi assertion`() {
        val parts = FactAtomicityValidator.splitAssertions("用户是素食主义者，并且对花生过敏")
        assertEquals(listOf("用户是素食主义者", "对花生过敏"), parts)
    }

    @Test
    fun `splits english multi assertion on semicolon`() {
        val parts = FactAtomicityValidator.splitAssertions("The user works in Beijing; likes hiking on weekends")
        assertEquals(2, parts.size)
        assertEquals("The user works in Beijing", parts[0])
    }

    // ── 反例 ───────────────────────────────────────────────────────────────

    @Test
    fun `short sentence is untouched`() {
        assertEquals(listOf("在北京"), FactAtomicityValidator.splitAssertions("在北京"))
    }

    @Test
    fun `single assertion with and is untouched`() {
        // “和”不在拆分点内,属单一偏好断言
        assertEquals(listOf("用户喜欢摄影和旅行"), FactAtomicityValidator.splitAssertions("用户喜欢摄影和旅行"))
    }

    @Test
    fun `enumeration with no comma connector is untouched`() {
        // “以及”仅在逗号引导时才作为拆分点
        val text = "用户喜欢跑步、游泳以及爬山"
        assertEquals(listOf(text), FactAtomicityValidator.splitAssertions(text))
    }

    @Test
    fun `noun phrase split is blocked by predicate gate`() {
        // 第二段“上海的美食”不含谓词 → 判定为名词短语被误拆,放弃
        val text = "用户喜欢北京，以及上海的美食"
        assertEquals(listOf(text), FactAtomicityValidator.splitAssertions(text))
    }

    @Test
    fun `too many parts is not split`() {
        // 6 段 > MAX_PARTS(5) → 视为异常输出,原样保留
        val text = "他喜欢跑步并且他喜欢游泳并且他喜欢爬山并且他喜欢读书并且他喜欢旅行并且他喜欢健身"
        assertEquals(listOf(text), FactAtomicityValidator.splitAssertions(text))
    }

    @Test
    fun `duplicate parts collapse and stay unsplit`() {
        val text = "他喜欢咖啡并且他喜欢咖啡"
        assertEquals(listOf(text), FactAtomicityValidator.splitAssertions(text))
    }

    @Test
    fun `blank text is returned as is`() {
        val parts = FactAtomicityValidator.splitAssertions("    ")
        assertEquals(1, parts.size)
        assertTrue(parts[0].isBlank())
    }

    // ── 边界:enforce 整体 ─────────────────────────────────────────────────

    @Test
    fun `empty input yields empty result`() {
        val result = FactAtomicityValidator.enforce(emptyList())
        assertTrue(result.facts.isEmpty())
        assertEquals(0, result.splitCount)
        assertEquals(0, result.addedCount)
    }

    @Test
    fun `enforce counts split and added entries and keeps unsplittable`() {
        val input = listOf(
            FactStore.Fact(fact = "用户在北京工作，而且周末喜欢爬山"),
            FactStore.Fact(fact = "用户喜欢摄影和旅行"),
        )
        val result = FactAtomicityValidator.enforce(input)

        assertEquals(3, result.facts.size)
        assertEquals(1, result.splitCount)
        assertEquals(1, result.addedCount)
        assertTrue("不可拆条目原样保留", result.facts.any { it.fact == "用户喜欢摄影和旅行" })
        assertTrue("拆分结果保留第一段", result.facts.any { it.fact == "用户在北京工作" })
        assertTrue("拆分结果保留第二段", result.facts.any { it.fact == "周末喜欢爬山" })
    }

    @Test
    fun `metadata is preserved across split parts`() {
        val fact = FactStore.Fact(
            fact = "用户在北京工作，而且周末喜欢爬山",
            tags = listOf("近况", "工作"),
            time = "2026-08-12T09:00",
            sessionId = "session-1",
            importance = 2,
            category = "event",
            confidence = 0.5f,
            source = "user_explicit",
            entityKey = "张三",
        )
        val result = FactAtomicityValidator.enforce(listOf(fact))

        assertEquals(2, result.facts.size)
        result.facts.forEach { part ->
            assertEquals(fact.tags, part.tags)
            assertEquals(fact.time, part.time)
            assertEquals(fact.sessionId, part.sessionId)
            assertEquals(fact.importance, part.importance)
            assertEquals(fact.category, part.category)
            assertEquals(fact.confidence, part.confidence, 0.001f)
            assertEquals(fact.source, part.source)
            assertEquals(fact.entityKey, part.entityKey)
        }
    }
}
