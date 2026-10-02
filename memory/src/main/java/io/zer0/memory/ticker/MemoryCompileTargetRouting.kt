package io.zer0.memory.ticker

import io.zer0.memory.compile.MemoryCompileTarget

/**
 * Resolve a background notification to the assistant that generated the turn.
 *
 * The runtime assistant can change while a fire-and-forget job is waiting in the
 * application scope; it must not decide where the previous turn is compiled.
 */
internal fun notificationCompileTarget(current: MemoryCompileTarget, assistantId: String, spaceId: String?): MemoryCompileTarget {
    val resolvedAssistantId = assistantId
        .ifBlank { current.assistantId.orEmpty() }
        .ifBlank { MemoryTicker.MAIN_ASSISTANT_ID }
    val resolvedScope = resolvedAssistantId.takeIf { it != MemoryTicker.MAIN_ASSISTANT_ID } ?: "main"
    return current.copy(
        assistantId = resolvedAssistantId,
        scope = resolvedScope,
        spaceId = spaceId?.ifBlank { null } ?: current.normalizedSpaceId,
    )
}
