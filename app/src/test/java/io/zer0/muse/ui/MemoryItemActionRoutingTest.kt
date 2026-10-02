package io.zer0.muse.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MemoryItemActionRoutingTest {

    @Test
    fun sessionSummaryIdRoutesToSummaryStorageInsteadOfFactIdParsing() {
        var factPathCalled = false
        val item = MemoryItem(
            id = "session-550e8400-e29b-41d4-a716-446655440000",
            title = "Chat title",
            content = "Conversation summary",
            source = "Summary",
        )

        val result = routeMemoryItemAction(
            item = item,
            onFact = { _, _ ->
                factPathCalled = true
                "fact"
            },
            onSummary = { sessionId -> sessionId },
            onUnsupported = { "unsupported" },
        )

        assertFalse(factPathCalled)
        assertEquals("session-550e8400-e29b-41d4-a716-446655440000", result)
    }

    @Test
    fun numericFactIdKeepsItsFactScope() {
        val item = MemoryItem(
            id = "42",
            title = "A fact",
            content = "A fact",
            source = "Fact",
            scope = "assistant-2",
        )

        val result = routeMemoryItemAction(
            item = item,
            onFact = { id, scope -> "$id:$scope" },
            onSummary = { "summary:$it" },
            onUnsupported = { "unsupported" },
        )

        assertEquals("42:assistant-2", result)
    }

    @Test
    fun allScopeMemoryMaintenanceIncludesMainAndChildAssistants() {
        val scopes = listOf(
            ScopeOption(id = null, displayName = "All", isAll = true),
            ScopeOption(id = "main", displayName = "Main", isMain = true),
            ScopeOption(id = "assistant-2", displayName = "Second"),
        )

        assertEquals(listOf("main", "assistant-2"), memoryScopesForMaintenance(null, scopes))
        assertEquals(listOf("assistant-2"), memoryScopesForMaintenance("assistant-2", scopes))
    }
}
