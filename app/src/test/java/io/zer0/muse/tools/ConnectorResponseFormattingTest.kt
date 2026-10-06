package io.zer0.muse.tools

import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorResponseFormattingTest {

    @Test
    fun `connector response keeps complete provider body`() {
        val body = "provider response ".repeat(1_000)

        val result = formatConnectorResponse(200, body)

        assertTrue(result.startsWith("HTTP 200\n"))
        assertTrue(result.endsWith(body))
    }
}
