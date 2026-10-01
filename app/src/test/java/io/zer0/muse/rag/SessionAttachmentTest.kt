package io.zer0.muse.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionAttachmentTest {

    @Test
    fun `session attachment creates public knowledge metadata for retrieval`() {
        val attachment = SessionAttachment(
            id = "attachment-1",
            sessionId = "session-1",
            docId = SessionAttachment.buildDocId("session-1", "attachment-1"),
            name = "合同.txt",
            status = SessionAttachmentStatus.READY,
        )

        val doc = attachment.toKnowledgeDoc("合同正文")

        assertEquals(attachment.docId, doc.id)
        assertEquals("合同.txt", doc.title)
        assertEquals("合同正文", doc.content)
        assertEquals("session_attachment", doc.fileType)
        assertFalse(doc.isInternal)
    }
}
