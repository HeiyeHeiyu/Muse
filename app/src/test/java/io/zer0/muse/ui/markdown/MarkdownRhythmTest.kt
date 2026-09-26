package io.zer0.muse.ui.markdown

import androidx.compose.ui.unit.dp
import io.zer0.muse.ui.theme.MuseMarkdownRhythm
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * E3 阶段 5 · 聊天长内容排版校准:Markdown 长回复垂直节奏纯函数 [markdownBlockGap] 单测。
 *
 * 只验证"块与块之间该留多少"的语义(段落 / 标题 / 列表 / 结构块),不涉及渲染。
 */
class MarkdownRhythmTest {

    private val paragraph = MarkdownBlock.Paragraph("正文")
    private val heading = MarkdownBlock.Heading(2, "小标题")
    private val code = MarkdownBlock.CodeBlock("kotlin", "val x = 1")
    private val table = MarkdownBlock.Table(listOf("a", "b"), listOf(listOf("1", "2")))
    private val quote = MarkdownBlock.Quote("引用")
    private val listA = MarkdownBlock.ListItem(ordered = false, index = 0, text = "一")
    private val listB = MarkdownBlock.ListItem(ordered = false, index = 0, text = "二")

    @Test
    fun first_block_has_no_leading_gap() {
        assertEquals(0.dp, markdownBlockGap(prev = null, current = paragraph, blankBetween = false))
    }

    @Test
    fun soft_line_break_without_blank_line_stays_flush() {
        assertEquals(0.dp, markdownBlockGap(paragraph, paragraph, blankBetween = false))
    }

    @Test
    fun blank_line_separates_paragraphs() {
        assertEquals(
            MuseMarkdownRhythm.paragraphGap,
            markdownBlockGap(paragraph, paragraph, blankBetween = true),
        )
    }

    @Test
    fun heading_has_more_space_above_than_below() {
        assertEquals(
            MuseMarkdownRhythm.headingSpaceAbove,
            markdownBlockGap(paragraph, heading, blankBetween = true),
        )
        assertEquals(
            MuseMarkdownRhythm.headingSpaceBelow,
            markdownBlockGap(heading, paragraph, blankBetween = false),
        )
        // 相邻标题取"标题上方"档,不塌成标题下方的小留白
        assertEquals(
            MuseMarkdownRhythm.headingSpaceAbove,
            markdownBlockGap(heading, heading, blankBetween = false),
        )
    }

    @Test
    fun consecutive_list_items_stay_compact_even_when_source_has_blank_lines() {
        assertEquals(
            MuseMarkdownRhythm.listItemGap,
            markdownBlockGap(listA, listB, blankBetween = true),
        )
    }

    @Test
    fun structural_blocks_get_the_block_gap_on_both_sides() {
        assertEquals(MuseMarkdownRhythm.blockGap, markdownBlockGap(paragraph, code, blankBetween = true))
        assertEquals(MuseMarkdownRhythm.blockGap, markdownBlockGap(code, paragraph, blankBetween = true))
        assertEquals(MuseMarkdownRhythm.blockGap, markdownBlockGap(paragraph, table, blankBetween = true))
        assertEquals(MuseMarkdownRhythm.blockGap, markdownBlockGap(quote, paragraph, blankBetween = true))
    }

    @Test
    fun block_gap_wins_over_paragraph_gap() {
        // 结构块即使紧贴正文也按块间距留白(不被 paragraphGap 覆盖)
        assertEquals(MuseMarkdownRhythm.blockGap, markdownBlockGap(paragraph, quote, blankBetween = true))
        // 无结构块时,段落级才落到 paragraphGap
        assertEquals(MuseMarkdownRhythm.paragraphGap, markdownBlockGap(paragraph, listA, blankBetween = true))
    }
}
