package io.zer0.muse.tools

import io.zer0.muse.ui.markdown.DataCardParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderDataToolTest {

    @Test
    fun barChartResultIsConsumedByExistingDataCardParser() {
        val result = RenderDataTool.execute(
            mapOf(
                "type" to "bar",
                "title" to "Weekly messages",
                "data" to """{"labels":["Mon","Tue"],"values":[12,18]}""",
            ),
        )

        val card = DataCardParser.parse(result)
        assertTrue(card != null)
        assertEquals("Weekly messages", card?.title)
        assertEquals(listOf("Mon", "Tue"), card?.labels)
        assertEquals(listOf(12f, 18f), card?.values)
    }

    @Test
    fun tableResultIsBoundedMarkdownWithHeaderAndRows() {
        val result = RenderDataTool.execute(
            mapOf(
                "type" to "table",
                "title" to "Inventory",
                "data" to """{"columns":["Item","Count"],"rows":[["Pens",3],["Books",5]]}""",
            ),
        )

        assertTrue(result.contains("Inventory"))
        assertTrue(result.contains("| Item | Count |"))
        assertTrue(result.contains("| Pens | 3 |"))
        assertTrue(result.contains("| Books | 5 |"))
    }

    @Test
    fun malformedDataReturnsStructuredError() {
        val result = RenderDataTool.execute(
            mapOf("type" to "bar", "title" to "Broken", "data" to "{not-json"),
        )

        assertTrue(result.startsWith("Error:"))
    }

    @Test
    fun chartWithMismatchedLabelsAndValuesReturnsStructuredError() {
        val result = RenderDataTool.execute(
            mapOf(
                "type" to "line",
                "title" to "Broken",
                "data" to """{"labels":["Mon","Tue"],"values":[1]}""",
            ),
        )

        assertTrue(result.startsWith("Error:"))
    }

    @Test
    fun tableWithTooManyColumnsIsRejectedByTheStructuralGuard() {
        val columns = (1..65).joinToString(prefix = "[", postfix = "]") { "\"c$it\"" }
        val result = RenderDataTool.execute(
            mapOf(
                "type" to "table",
                "title" to "Too wide",
                "data" to """{"columns":$columns,"rows":[]}""",
            ),
        )

        assertTrue(result.startsWith("Error:"))
    }

    @Test
    fun `table keeps large datasets instead of truncating or rejecting output`() {
        val rows = (1..500).joinToString(",") { index ->
            """["row-$index","${"x".repeat(64)}"]"""
        }

        val result = RenderDataTool.execute(
            mapOf(
                "type" to "table",
                "title" to "Large table",
                "data" to """{"columns":["id","payload"],"rows":[$rows]}""",
            ),
        )

        assertFalse(result.startsWith("Error:"))
        assertTrue(result.contains("row-500"))
    }

    @Test
    fun `table keeps long cell text intact`() {
        val cell = "detail-".repeat(256)
        val result = RenderDataTool.execute(
            mapOf(
                "type" to "table",
                "title" to "Long cell",
                "data" to """{"columns":["content"],"rows":[["$cell"]]}""",
            ),
        )

        assertFalse(result.startsWith("Error:"))
        assertTrue(result.contains(cell))
    }
}
