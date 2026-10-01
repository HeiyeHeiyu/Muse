package io.zer0.muse.data.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemoryIsolationTest {
    @Test
    fun `newly created assistants do not opt into global memory`() {
        assertFalse(AssistantEntity(id = "assistant-new", name = "New").useGlobalMemory)
    }

    @Test
    fun `preset assistants keep their memory isolated by default`() {
        assertFalse(PresetCharacters.toEntity(PresetCharacters.XIAOYE).useGlobalMemory)
    }

    @Test
    fun `assistant facts remain available when global sharing is disabled`() {
        assertTrue(AssistantMemoryAccessPolicy.canReadAssistantFacts("assistant-writer", useGlobalMemory = false))
    }

    @Test
    fun `assistant facts are not injected when memory is disabled ignored or isolated`() {
        assertFalse(
            AssistantMemoryAccessPolicy.canInjectAssistantFacts(
                assistantId = "assistant-writer",
                useGlobalMemory = false,
                memoryEnabled = false,
                forSubagent = false,
                ignoreMemory = false,
            ),
        )
        assertFalse(
            AssistantMemoryAccessPolicy.canInjectAssistantFacts(
                assistantId = "assistant-writer",
                useGlobalMemory = false,
                memoryEnabled = true,
                forSubagent = false,
                ignoreMemory = true,
            ),
        )
        assertFalse(
            AssistantMemoryAccessPolicy.canInjectAssistantFacts(
                assistantId = "assistant-writer",
                useGlobalMemory = false,
                memoryEnabled = true,
                forSubagent = true,
                ignoreMemory = false,
            ),
        )
    }

    @Test
    fun `default assistant facts are treated as global and can be disabled`() {
        assertFalse(AssistantMemoryAccessPolicy.canReadAssistantFacts("default", useGlobalMemory = false))
        assertTrue(AssistantMemoryAccessPolicy.canReadAssistantFacts("default", useGlobalMemory = true))
    }

    @Test
    fun `missing assistant context cannot read global or assistant facts`() {
        assertFalse(AssistantMemoryAccessPolicy.canReadAssistantFacts(null, useGlobalMemory = true))
        assertFalse(AssistantMemoryAccessPolicy.canReadGlobalMemory(null, useGlobalMemory = true))
    }

    @Test
    fun `global memory requires explicit opt in for a known assistant`() {
        assertFalse(AssistantMemoryAccessPolicy.canReadGlobalMemory("assistant-writer", useGlobalMemory = false))
        assertTrue(AssistantMemoryAccessPolicy.canReadGlobalMemory("assistant-writer", useGlobalMemory = true))
    }

    @Test
    fun `assistant recent chats are independent of global memory opt in`() {
        assertTrue(
            AssistantMemoryAccessPolicy.canReadRecentChats(
                assistantId = "assistant-writer",
                memoryEnabled = true,
                recentChatsEnabled = true,
                forSubagent = false,
                ignoreMemory = false,
            ),
        )
    }

    @Test
    fun `recent chats remain disabled for missing assistant or memory opt outs`() {
        assertFalse(
            AssistantMemoryAccessPolicy.canReadRecentChats(
                assistantId = null,
                memoryEnabled = true,
                recentChatsEnabled = true,
                forSubagent = false,
                ignoreMemory = false,
            ),
        )
        assertFalse(
            AssistantMemoryAccessPolicy.canReadRecentChats(
                assistantId = "assistant-writer",
                memoryEnabled = true,
                recentChatsEnabled = true,
                forSubagent = false,
                ignoreMemory = true,
            ),
        )
    }
}
