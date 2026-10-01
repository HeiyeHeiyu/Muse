package io.zer0.memory.pin

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class PinnedMemoryStoreScopeTest {

    @Test
    fun `assistant scoped pins do not leak while legacy global pins remain opt in`() = runBlocking {
        val directory = Files.createTempDirectory("pinned-scope-test").toFile()
        try {
            val store = PinnedMemoryStore(directory)
            store.add("legacy global")
            store.add("assistant A", assistantId = "assistant-a")
            store.add("assistant B", assistantId = "assistant-b")

            assertEquals(
                setOf("assistant A"),
                store.getForAssistant("assistant-a", includeGlobal = false).map { it.content }.toSet(),
            )
            assertEquals(
                setOf("legacy global", "assistant A"),
                store.getForAssistant("assistant-a", includeGlobal = true).map { it.content }.toSet(),
            )

            val reloaded = PinnedMemoryStore(directory)
            assertEquals(
                setOf("assistant A"),
                reloaded.getForAssistant("assistant-a", includeGlobal = false).map { it.content }.toSet(),
            )
        } finally {
            directory.deleteRecursively()
        }
    }
}
