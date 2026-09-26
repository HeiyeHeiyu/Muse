package io.zer0.memory.observe

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** D3-P1: 步骤运行器测试(计数 / 日志 / run 包装与取消语义)。 */
class MemoryStepRunnerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun logFile() = File(tmp.root, "pipeline_log.jsonl")

    @Test
    fun `recordSuccess and recordFailure update counters and write log`() {
        val log = PipelineLog(logFile(), maxBytes = 1_000_000)
        val runner = MemoryStepRunner(pipelineLog = log)

        runner.recordSuccess("compileFacts", durationMs = 12, trigger = "daily")
        runner.recordFailure("compileFacts", RuntimeException("HTTP 429"), durationMs = 34, trigger = "daily")

        val c = runner.countersOf("compileFacts")
        assertEquals(2, c.total)
        assertEquals(1, c.successes)
        assertEquals(1, c.failures)

        val lines = logFile().readLines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue("首行应为成功记录: ${lines[0]}", lines[0].contains("\"ok\":true"))
        assertTrue(lines[0].contains("compileFacts"))
        assertTrue(lines[1].contains("\"ok\":false"))
        assertTrue("失败分类应记录: ${lines[1]}", lines[1].contains("RETRYABLE"))
    }

    @Test
    fun `run wraps block and classifies failure`() = runBlocking {
        val log = PipelineLog(logFile(), maxBytes = 1_000_000)
        val runner = MemoryStepRunner(pipelineLog = log)

        val ok = runner.run("rollingSummary", MemoryStage.EXTRACT, "turn") { "done" }
        assertTrue(ok is MemoryStepRunner.StepResult.Success)

        val fail = runner.run<String>("rollingSummary", MemoryStage.EXTRACT, "turn") {
            throw RuntimeException("HTTP 500")
        }
        assertTrue(fail is MemoryStepRunner.StepResult.Failure)
        assertEquals(FailureKind.RETRYABLE, (fail as MemoryStepRunner.StepResult.Failure).kind)
    }

    @Test
    fun `run rethrows cancellation without recording failure`() = runBlocking {
        val runner = MemoryStepRunner()
        var thrown = false
        try {
            runner.run("x", MemoryStage.INDEX, "t") { throw CancellationException("c") }
        } catch (e: CancellationException) {
            thrown = true
        }
        assertTrue("取消应原样重抛", thrown)
        assertEquals("取消不应记为失败", 0, runner.countersOf("x").failures)
    }
}
