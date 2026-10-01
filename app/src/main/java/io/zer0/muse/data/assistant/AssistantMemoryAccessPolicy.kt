package io.zer0.muse.data.assistant

/**
 * Defines which memory scopes a conversation may read.
 *
 * The built-in `default` assistant owns the global fact store. Other assistants keep
 * their own facts and recent chats even when global memories are not shared.
 */
internal object AssistantMemoryAccessPolicy {
    private const val DEFAULT_ASSISTANT_ID = "default"

    fun canReadGlobalMemory(assistantId: String?, useGlobalMemory: Boolean): Boolean = !assistantId.isNullOrBlank() && useGlobalMemory

    fun canReadAssistantFacts(assistantId: String?, useGlobalMemory: Boolean): Boolean {
        val id = assistantId?.takeIf { it.isNotBlank() } ?: return false
        return id != DEFAULT_ASSISTANT_ID || useGlobalMemory
    }

    fun canInjectAssistantFacts(
        assistantId: String?,
        useGlobalMemory: Boolean,
        memoryEnabled: Boolean,
        forSubagent: Boolean,
        ignoreMemory: Boolean,
    ): Boolean = memoryEnabled &&
        canReadAssistantFacts(assistantId, useGlobalMemory) &&
        !forSubagent &&
        !ignoreMemory

    fun canReadRecentChats(
        assistantId: String?,
        memoryEnabled: Boolean,
        recentChatsEnabled: Boolean,
        forSubagent: Boolean,
        ignoreMemory: Boolean,
    ): Boolean = !assistantId.isNullOrBlank() &&
        memoryEnabled &&
        recentChatsEnabled &&
        !forSubagent &&
        !ignoreMemory
}
