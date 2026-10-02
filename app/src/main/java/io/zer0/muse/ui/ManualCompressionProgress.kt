package io.zer0.muse.ui

enum class ManualCompressionPhase {
    IDLE,
    UPDATING_MEMORY,
    COMPRESSING_CONTEXT,
}

internal suspend fun <T> runManualCompressionStages(
    updateMemoryFirst: Boolean,
    onPhase: (ManualCompressionPhase) -> Unit,
    updateMemory: suspend () -> Unit,
    compress: suspend () -> T,
): T {
    if (updateMemoryFirst) {
        onPhase(ManualCompressionPhase.UPDATING_MEMORY)
        updateMemory()
    }
    onPhase(ManualCompressionPhase.COMPRESSING_CONTEXT)
    return compress()
}
