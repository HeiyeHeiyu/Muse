package io.zer0.muse.tools

import org.junit.Assert.assertTrue
import org.junit.Test

class WebFetchOutputTest {

    @Test
    fun `formatted web fetch result keeps the complete extracted body`() {
        val body = "article paragraph ".repeat(6_000)

        val result = formatWebFetchResult(200, body)

        assertTrue(result.startsWith("HTTP 200\n"))
        assertTrue(result.endsWith(body))
    }
}
