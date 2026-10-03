package io.zer0.muse.tools

import io.zer0.muse.ui.markdown.DataCardParser
import org.junit.Assert.assertEquals
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
    fun tableWithTooManyColumnsIsRejected() {
        val columns = (1..13).joinToString(prefix = "[", postfix = "]") { "\"c$it\"" }
        val result = RenderDataTool.execute(
            mapOf(
                "type" to "table",
                "title" to "Too wide",
                "data" to """{"columns":$columns,"rows":[]}""",
            ),
        )

        assertTrue(result.startsWith("Error:"))
    }
}
