package io.zer0.memory.ticker

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTickerWriteGateWiringTest {

    @Test
    fun `memory ticker rechecks restore gate before forced compilation and checkpoint writes`() {
        val source =
            listOf(
                Path.of("src/main/java/io/zer0/memory/ticker/MemoryTicker.kt"),
                Path.of("memory/src/main/java/io/zer0/memory/ticker/MemoryTicker.kt"),
            )
                .firstOrNull(Files::exists)
                ?.let { Files.newBufferedReader(it).use { reader -> reader.readText() } }
                ?: error("MemoryTicker.kt not found")

        val forceBody = source.substringAfter("suspend fun forceCompileNow")
            .substringBefore("suspend fun readCompiledMemoryMarkdown")
        val dailyStateBody = source.substringAfter("private suspend fun writeDailyState")
            .substringBefore("private suspend fun doRollingSummary")
        val dailyBody = source.substringAfter("private suspend fun doDaily")
            .substringBefore("suspend fun getAllSummarizedSessionIds")

        assertTrue(forceBody.contains("ProcessWriteGate.restoring"))
        assertTrue(dailyStateBody.contains("ProcessWriteGate.restoring"))
        assertTrue(dailyBody.contains("ProcessWriteGate.restoring"))
    }
}
