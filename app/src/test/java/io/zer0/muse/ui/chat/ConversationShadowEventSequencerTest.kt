package io.zer0.muse.ui.chat

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationShadowEventSequencerTest {
    @Test
    fun `same generation events are serialized in enqueue order`() = runBlocking {
        val sequencer = ConversationShadowEventSequencer()
        val events = mutableListOf<String>()
        val first = async {
            sequencer.enqueue("session-turn-generation") {
                delay(50L)
                events += "started"
            }
        }
        val second = async {
            delay(5L)
            sequencer.enqueue("session-turn-generation") {
                events += "tool-called"
            }
        }

        first.await()
        second.await()

        assertEquals(listOf("started", "tool-called"), events)
    }

    @Test
    fun `different generations do not block each other`() = runBlocking {
        val sequencer = ConversationShadowEventSequencer()
        val events = mutableListOf<String>()
        val first = async {
            sequencer.enqueue("generation-a") {
                delay(50L)
                events += "a"
            }
        }
        val second = async {
            sequencer.enqueue("generation-b") {
                events += "b"
            }
        }

        second.await()
        first.await()

        assertEquals("b", events.first())
    }
}
