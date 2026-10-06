package io.zer0.muse.tools

import io.zer0.muse.data.SettingsRepository
import io.zer0.muse.data.session.SessionRepository

/**
 * Registers the explicit current-session conversation recall tool.
 */
class ConversationSearchToolsRegistrar(
    toolRegistry: ToolRegistry,
    sessionRepository: SessionRepository,
    settings: SettingsRepository,
) {
    init {
        toolRegistry.registerWithContext(SearchConversationTool.toolDef()) { args, context ->
            SearchConversationTool.execute(
                args = args,
                sessionRepository = sessionRepository,
                enabled = settings.memoryConfigCache.conversationRecallEnabled,
                context = context,
            )
        }
    }
}
