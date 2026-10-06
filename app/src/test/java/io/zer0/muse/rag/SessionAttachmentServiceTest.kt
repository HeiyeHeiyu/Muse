package io.zer0.muse.rag

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.zer0.muse.data.SettingsRepository
import io.zer0.muse.data.knowledge.KnowledgeDocDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAttachmentServiceTest {

    @Test
    fun `dropping a session cancels in-flight indexing before cleanup`() = runBlocking {
        val ragService = mockk<RagService>(relaxed = true)
        val settings = mockk<SettingsRepository>(relaxed = true)
        val docDao = mockk<KnowledgeDocDao>(relaxed = true)
        every { settings.ragConfigFlow } returns flowOf(RagConfig())

        val indexingStarted = CompletableDeferred<Unit>()
        val releaseIndexing = CompletableDeferred<Unit>()
        coEvery {
            ragService.indexDocument(
                docId = any(),
                content = any(),
                ragConfig = any(),
                onProgress = any(),
            )
        } coAnswers {
            indexingStarted.complete(Unit)
            releaseIndexing.await()
            1
        }

        val service = SessionAttachmentService(ragService, settings, docDao)
        service.indexSessionAttachment("session-1", "note.txt", "content")
        withTimeout(2_000) { indexingStarted.await() }

        service.dropSessionAttachments("session-1")
        releaseIndexing.complete(Unit)

        withTimeout(2_000) {
            while (!service.attachmentsBySession("session-1").isEmpty()) {
                kotlinx.coroutines.delay(10)
            }
        }
        coVerify(exactly = 1) { docDao.upsert(any()) }
        coVerify(exactly = 1) { docDao.delete(any()) }
        coVerify(exactly = 1) { ragService.deleteDocIndex(any()) }
        assertTrue(service.attachmentsBySession("session-1").isEmpty())
    }
}
