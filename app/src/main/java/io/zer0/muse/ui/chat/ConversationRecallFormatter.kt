package io.zer0.muse.ui.chat

import io.zer0.muse.chat.InternalPromptMarkers
import io.zer0.muse.data.session.SearchResult
import io.zer0.muse.util.TokenEstimator

/**
 * Formats on-demand current-session history recall for the model.
 *
 * The source remains the messages table/FTS index; this formatter deliberately
 * keeps the result small and treats historical text as reference data rather
 * than a new instruction channel.
 */
internal object ConversationRecallFormatter {
    private const val MAX_ITEM_CHARS = 800
    private const val HEADER = InternalPromptMarkers.RECALL_HEADER

    fun build(query: String, results: List<SearchResult>, maxTokens: Int): String {
        if (query.isBlank() || maxTokens <= 0) return ""
        val usable = results
            .asSequence()
            .filter { it.role == "USER" || it.role == "ASSISTANT" }
            .filter { it.content.isNotBlank() || it.contentSnippet.isNotBlank() }
            .distinctBy { it.messageId }
            .toList()
        val headerTokens = TokenEstimator.estimate(HEADER)
        val section = if (usable.isEmpty() || headerTokens >= maxTokens) {
            ""
        } else {
            renderBounded(usable, maxTokens, headerTokens)
        }
        return section
    }

    private fun renderBounded(usable: List<SearchResult>, budget: Int, headerTokens: Int): String {
        val body = StringBuilder(HEADER)
        var usedTokens = headerTokens
        for (result in usable) {
            val role = if (result.role == "USER") InternalPromptMarkers.ROLE_USER else InternalPromptMarkers.ROLE_ASSISTANT
            val content = (result.content.ifBlank { result.contentSnippet }).take(MAX_ITEM_CHARS)
            if (content.isBlank()) continue
            val prefix = "\n[$role] "
            val fullItem = prefix + content
            val itemTokens = TokenEstimator.estimate(fullItem)
            if (usedTokens + itemTokens > budget) {
                val remaining = budget - usedTokens
                if (remaining > 0) body.append(fitToTokenBudget(prefix, content, remaining))
                return body.toString().takeIf { it.length > HEADER.length }.orEmpty()
            }
            body.append(fullItem)
            usedTokens += itemTokens
        }
        return body.toString().takeIf { it.length > HEADER.length }.orEmpty()
    }

    private fun fitToTokenBudget(prefix: String, content: String, budget: Int): String {
        var end = content.length
        while (end > 0) {
            val candidate = prefix + content.take(end)
            if (TokenEstimator.estimate(candidate) <= budget) return candidate
            end = (end * 0.8f).toInt().coerceAtMost(end - 1)
        }
        return ""
    }
}
