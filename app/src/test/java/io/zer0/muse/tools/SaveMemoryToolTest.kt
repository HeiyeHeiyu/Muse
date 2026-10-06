package io.zer0.muse.tools

import io.mockk.coEvery
import io.mockk.mockk
import io.zer0.memory.fact.FactStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveMemoryToolTest {

    @Test
    fun `storage failure is returned with a diagnostic message`() = runBlocking {
        val store = mockk<FactStore>()
        coEvery {
            store.add(any(), scope = any(), spaceId = any())
        } throws IllegalStateException("fts insert failed")

        val result = SaveMemoryTool.execute(
            args = mapOf("content" to "用户喜欢 Kotlin"),
            factStore = store,
            executionContext = ToolExecutionContext(scope = "main", spaceId = "default"),
        )

        assertTrue(result.contains("fts insert failed"))
    }
}
