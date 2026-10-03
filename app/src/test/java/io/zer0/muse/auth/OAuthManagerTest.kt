package io.zer0.muse.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthManagerTest {

    @Test
    fun authorizationCodeWaitTimesOutInsteadOfHangingForever() = runTest {
        val result = OAuthManager.awaitAuthorizationCode(
            deferred = CompletableDeferred(),
            timeoutMs = 1L,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("超时"))
    }
}
