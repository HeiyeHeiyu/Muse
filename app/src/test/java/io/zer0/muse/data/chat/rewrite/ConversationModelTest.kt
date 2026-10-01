package io.zer0.muse.data.chat.rewrite

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationModelTest {

    @Test
    fun deterministicEventIdIsStableAcrossRetriesAndChangesWithSequence() {
        val first = ConversationEventDraft(
            sessionId = "s1",
            turnId = "t1",
            type = ConversationEventType.CONTENT_DELTA,
            sequenceInStream = 1,
            payloadJson = "{\"text\":\"hello\"}",
            createdAt = 100L,
        )
        val retry = first.copy(createdAt = 200L)
        val next = first.copy(sequenceInStream = 2)

        assertEquals(first.deterministicEventId(), retry.deterministicEventId())
        org.junit.Assert.assertNotEquals(first.deterministicEventId(), next.deterministicEventId())
    }

    @Test
    fun sensitivePayloadValuesAreRedactedBeforePersistence() {
        val event = ConversationEventDraft(
            sessionId = "s1",
            turnId = "t1",
            type = ConversationEventType.TOOL_CALLED,
            payloadJson = "{\"api_key\":\"secret\",\"text\":\"keep\"}",
        )

        val entity = event.toEntity(1, "e1")
        assertEquals("{\"api_key\":\"[REDACTED]\",\"text\":\"keep\"}", entity.payloadJson)
        assertEquals(entity.payloadJson.length, entity.payloadLength)
    }
}
