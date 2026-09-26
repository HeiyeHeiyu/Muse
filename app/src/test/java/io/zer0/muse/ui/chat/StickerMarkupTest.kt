package io.zer0.muse.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.x: 表情包标记解析器单测 — 完整标记/多标记/流式半截/代码围栏/容错。
 */
class StickerMarkupTest {
    // ── findMarkers ─────────────────────────────────────────────────────

    @Test
    fun `finds single complete marker`() {
        val markers = StickerMarkup.findMarkers("今天太好了 [[sticker:开心]]")
        assertEquals(1, markers.size)
        assertEquals("开心", markers[0].category)
    }

    @Test
    fun `finds multiple markers in order`() {
        val markers = StickerMarkup.findMarkers("a [[sticker:开心]] b [[sticker:无语]] c")
        assertEquals(listOf("开心", "无语"), markers.map { it.category })
    }

    @Test
    fun `case insensitive and spaces trimmed`() {
        val markers = StickerMarkup.findMarkers("[[STICKER: 开心 ]]")
        assertEquals("开心", markers.single().category)
    }

    @Test
    fun `skips markers inside code fence`() {
        val text = "```\n[[sticker:开心]]\n```\n[[sticker:无语]]"
        val markers = StickerMarkup.findMarkers(text)
        assertEquals(listOf("无语"), markers.map { it.category })
    }

    @Test
    fun `does not match non sticker double brackets`() {
        assertTrue(StickerMarkup.findMarkers("[[wiki link]]").isEmpty())
    }

    @Test
    fun `marker positions are correct`() {
        val text = "你好 [[sticker:开心]] 结束"
        val marker = StickerMarkup.findMarkers(text).single()
        assertEquals(text.substring(marker.start, marker.end), "[[sticker:开心]]")
    }

    // ── findOpenMarkerStart ─────────────────────────────────────────────

    @Test
    fun `detects unclosed marker while streaming`() {
        assertTrue(StickerMarkup.findOpenMarkerStart("好的 [[sticker:开") >= 0)
        assertTrue(StickerMarkup.findOpenMarkerStart("好的 [[st") >= 0)
        assertTrue(StickerMarkup.findOpenMarkerStart("好的 [[") >= 0)
        assertTrue(StickerMarkup.findOpenMarkerStart("好的 [[sticker:") >= 0)
    }

    @Test
    fun `closed marker is not reported open`() {
        assertEquals(-1, StickerMarkup.findOpenMarkerStart("好的 [[sticker:开心]] 嗯"))
    }

    @Test
    fun `unrelated double bracket is not swallowed`() {
        assertEquals(-1, StickerMarkup.findOpenMarkerStart("看这个 [[foo"))
        assertEquals(-1, StickerMarkup.findOpenMarkerStart("普通文本没有括号"))
    }

    // ── split ───────────────────────────────────────────────────────────

    @Test
    fun `split returns single text segment when no marker`() {
        val segs = StickerMarkup.split("普通回复")
        assertEquals(1, segs.size)
        assertTrue(segs[0] is StickerMarkup.Segment.Text)
    }

    @Test
    fun `split produces text and sticker segments`() {
        val segs = StickerMarkup.split("今天太好了 [[sticker:开心]]")
        assertEquals(2, segs.size)
        assertEquals("今天太好了", (segs[0] as StickerMarkup.Segment.Text).text)
        assertEquals("开心", (segs[1] as StickerMarkup.Segment.Sticker).category)
    }

    @Test
    fun `split swallows unclosed marker tail`() {
        val segs = StickerMarkup.split("好的 [[sticker:开")
        assertEquals(1, segs.size)
        assertEquals("好的", (segs[0] as StickerMarkup.Segment.Text).text)
    }

    @Test
    fun `split handles marker in the middle`() {
        val segs = StickerMarkup.split("前 [[sticker:无语]] 后")
        assertEquals(3, segs.size)
        assertEquals("前", (segs[0] as StickerMarkup.Segment.Text).text)
        assertEquals("无语", (segs[1] as StickerMarkup.Segment.Sticker).category)
        assertEquals("后", (segs[2] as StickerMarkup.Segment.Text).text)
    }

    @Test
    fun `split handles adjacent markers`() {
        val segs = StickerMarkup.split("[[sticker:开心]][[sticker:无语]]")
        assertEquals(2, segs.size)
        assertEquals("开心", (segs[0] as StickerMarkup.Segment.Sticker).category)
        assertEquals("无语", (segs[1] as StickerMarkup.Segment.Sticker).category)
    }

    @Test
    fun `split drops marker-only blank text segments`() {
        val segs = StickerMarkup.split("[[sticker:开心]]")
        assertEquals(1, segs.size)
        assertTrue(segs[0] is StickerMarkup.Segment.Sticker)
    }

    @Test
    fun `split keeps paragraph text around marker`() {
        val segs = StickerMarkup.split("第一段。\n\n[[sticker:开心]]\n\n第二段。")
        assertEquals(3, segs.size)
        assertEquals("第一段。", (segs[0] as StickerMarkup.Segment.Text).text)
        assertEquals("第二段。", (segs[2] as StickerMarkup.Segment.Text).text)
    }
}
