package io.zer0.muse.ui.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.util.concurrent.ConcurrentHashMap

/** Serializes asynchronous shadow-event writes within one logical generation. */
internal class ConversationShadowEventSequencer {
    private val tails = ConcurrentHashMap<String, Deferred<Unit>>()

    suspend fun <T> enqueue(key: String, block: suspend () -> T): T {
        val gate = CompletableDeferred<Unit>()
        val previous = tails.put(key, gate)
        try {
            previous?.await()
            return block()
        } finally {
            gate.complete(Unit)
            tails.remove(key, gate)
        }
    }
}
