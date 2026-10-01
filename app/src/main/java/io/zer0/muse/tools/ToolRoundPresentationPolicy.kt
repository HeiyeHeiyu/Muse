package io.zer0.muse.tools

/** Limits visible reasoning metadata to the first provider request in a client-side tool turn. */
internal object ToolRoundPresentationPolicy {
    fun exposeReasoning(round: Int): Boolean = round == 1
}
