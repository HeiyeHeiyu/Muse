package io.zer0.muse.ui

internal inline fun <T> routeMemoryItemAction(
    item: MemoryItem,
    onFact: (factId: String, scope: String?) -> T,
    onSummary: (sessionId: String) -> T,
    onUnsupported: () -> T,
): T = when (item.source) {
    "Fact" -> onFact(item.id, item.scope)
    "Summary" -> onSummary(item.id)
    else -> onUnsupported()
}

internal fun memoryScopesForMaintenance(selectedScope: String?, availableScopes: List<ScopeOption>): List<String> {
    if (selectedScope != null) return listOf(selectedScope)
    return availableScopes.asSequence()
        .filterNot { it.isAll }
        .mapNotNull { it.id }
        .distinct()
        .toList()
        .ifEmpty { listOf("main") }
}
