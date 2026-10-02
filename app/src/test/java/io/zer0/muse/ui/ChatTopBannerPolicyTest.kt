package io.zer0.muse.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTopBannerPolicyTest {

    @Test
    fun activeCompressionProgressIsNeverHiddenByExistingWarnings() {
        assertEquals(
            ChatTopBanner.COMPRESSION,
            resolveChatTopBanner(
                isCompressing = true,
                showPendingResume = true,
                hasErrors = true,
                runningDelegateCount = 2,
                isConfigured = false,
            ),
        )
    }

    @Test
    fun remainingBannersKeepTheirPriorityAfterCompression() {
        assertEquals(
            ChatTopBanner.ERROR,
            resolveChatTopBanner(false, showPendingResume = true, hasErrors = true, runningDelegateCount = 1, isConfigured = false),
        )
        assertEquals(
            ChatTopBanner.PENDING_TOOLS,
            resolveChatTopBanner(false, showPendingResume = true, hasErrors = false, runningDelegateCount = 1, isConfigured = false),
        )
        assertEquals(
            ChatTopBanner.DELEGATION,
            resolveChatTopBanner(false, showPendingResume = false, hasErrors = false, runningDelegateCount = 1, isConfigured = false),
        )
        assertEquals(
            ChatTopBanner.NOT_CONFIGURED,
            resolveChatTopBanner(false, showPendingResume = false, hasErrors = false, runningDelegateCount = 0, isConfigured = false),
        )
        assertEquals(
            ChatTopBanner.NONE,
            resolveChatTopBanner(false, showPendingResume = false, hasErrors = false, runningDelegateCount = 0, isConfigured = true),
        )
    }
}
