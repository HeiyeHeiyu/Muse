package io.zer0.muse.data.sticker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v2.x: 表情包分类名容错匹配单测 — 精确/归一化/编辑距离/唯一包含/歧义放弃。
 */
class StickerCategoryMatchTest {
    private val cats = listOf("开心", "无语", "安慰", "猫猫", "不开心")

    @Test
    fun `exact match`() {
        assertEquals("开心", matchStickerCategory(cats, "开心"))
    }

    @Test
    fun `normalized match ignores case and whitespace`() {
        assertEquals("开心", matchStickerCategory(cats, " 开心 "))
        assertEquals("Happy", matchStickerCategory(listOf("Happy", "Sad"), "happy"))
        assertEquals("猫猫", matchStickerCategory(cats, "猫 猫"))
    }

    @Test
    fun `edit distance one with same length`() {
        // 单字替换:开兴 → 开心
        assertEquals("开心", matchStickerCategory(cats, "开兴"))
    }

    @Test
    fun `containing shorter category does not match`() {
        // "开心" 与 "不开心" 仅一字之差但语义相反:反向包含禁用,拒绝匹配
        assertNull(matchStickerCategory(listOf("不开心"), "开心"))
    }

    @Test
    fun `suffix-typed name matches shorter category`() {
        // 模型把分类名写长一点(方向:name 包含 candidate) → 匹配
        assertEquals("开心", matchStickerCategory(listOf("开心"), "开心呀"))
    }

    @Test
    fun `ambiguous containment returns null`() {
        // 同时包含多个分类 → 歧义,放弃
        assertNull(matchStickerCategory(listOf("开心", "开心果"), "开心果果"))
    }

    @Test
    fun `no match returns null`() {
        assertNull(matchStickerCategory(cats, "愤怒"))
        assertNull(matchStickerCategory(emptyList(), "开心"))
        assertNull(matchStickerCategory(cats, "   "))
    }

    @Test
    fun `levenshtein sanity`() {
        assertEquals(0, levenshteinDistance("开心", "开心"))
        assertEquals(1, levenshteinDistance("开心", "开兴"))
        assertEquals(1, levenshteinDistance("happy", "happi"))
        assertEquals(3, levenshteinDistance("kitten", "sitting"))
        assertEquals(2, levenshteinDistance("", "ab"))
    }
}
