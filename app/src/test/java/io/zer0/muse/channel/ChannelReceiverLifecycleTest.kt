package io.zer0.muse.channel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelReceiverLifecycleTest {

    @Test
    fun `socket cleanup runs when receiver wait is cancelled`() = runBlocking {
        val disconnected = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        var cleanupCount = 0
        val job = launch {
            entered.complete(Unit)
            awaitChannelSocketTermination(disconnected) { cleanupCount++ }
        }

        entered.await()
        job.cancel()
        job.join()

        assertEquals(1, cleanupCount)
    }
}
