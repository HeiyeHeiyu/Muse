package io.zer0.muse.rag

import io.mockk.coEvery
import io.mockk.mockk
import io.zer0.muse.data.knowledge.KnowledgeChunkDao
import io.zer0.muse.data.knowledge.KnowledgeChunkEntity
import io.zer0.muse.data.knowledge.KnowledgeChunkFtsDao
import io.zer0.muse.data.knowledge.KnowledgeChunkFtsHit
import io.zer0.muse.data.knowledge.KnowledgeDocDao
import io.zer0.muse.data.knowledge.KnowledgeDocEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeKeywordFallbackTest {
    @Test
    fun `fts fallback preserves chunk citation and enforces scope metadata and internal filters`() = runBlocking {
        val docs = mockk<KnowledgeDocDao>()
        val chunks = mockk<KnowledgeChunkDao>()
        val fts = mockk<KnowledgeChunkFtsDao>()
        val publicChunk = KnowledgeChunkEntity(
            id = "public-chunk",
            docId = "public-doc",
            content = "matching public text",
            chunkIndex = 4,
            metadataJson = "{\"tags\":[\"keep\"]}",
            createdAt = 100L,
        )
        val internalChunk = KnowledgeChunkEntity(id = "internal-chunk", docId = "internal-doc", content = "secret")
        val outsideChunk = KnowledgeChunkEntity(id = "outside-chunk", docId = "outside-doc", content = "other")

        coEvery { fts.searchBm25Safe("needle", any()) } returns listOf(
            KnowledgeChunkFtsHit("internal-chunk", -3.0),
            KnowledgeChunkFtsHit("outside-chunk", -2.0),
            KnowledgeChunkFtsHit("public-chunk", -1.0),
        )
        coEvery { chunks.getByIds(any()) } returns listOf(internalChunk, outsideChunk, publicChunk)
        coEvery { docs.getByIds(any()) } returns listOf(
            KnowledgeDocEntity(id = "internal-doc", title = "Internal", isInternal = true),
            KnowledgeDocEntity(id = "outside-doc", title = "Outside"),
            KnowledgeDocEntity(id = "public-doc", title = "Public"),
        )

        val results = KnowledgeKeywordFallback.search(
            query = "needle",
            topK = 5,
            scopeDocIds = listOf("internal-doc", "outside-doc", "public-doc"),
            metadataFilter = VectorSearchService.MetadataFilter(tag = "keep", startTime = 100L),
            daos = KnowledgeKeywordFallback.SearchDaos(docDao = docs, chunkDao = chunks, ftsDao = fts),
        )

        assertEquals(1, results.size)
        assertEquals("public-doc", results.single().docId)
        assertEquals("public-chunk", results.single().chunkId)
        assertEquals(4, results.single().chunkIndex)
        assertEquals("matching public text", results.single().snippet)
        assertEquals(1f, results.single().score)
        assertTrue(!results.single().isInternal)
    }

    @Test
    fun `legacy document fallback also excludes internal documents and returns chunk identity`() = runBlocking {
        val docs = mockk<KnowledgeDocDao>()
        val chunks = mockk<KnowledgeChunkDao>()
        val fts = mockk<KnowledgeChunkFtsDao>()
        val publicDoc = KnowledgeDocEntity(id = "public-doc", title = "Public", content = "document content")
        val internalDoc = KnowledgeDocEntity(id = "internal-doc", title = "Internal", content = "private", isInternal = true)
        val publicChunk = KnowledgeChunkEntity(id = "public-chunk", docId = "public-doc", content = "chunk content", chunkIndex = 2)

        coEvery { fts.searchBm25Safe("needle", any()) } returns emptyList()
        coEvery { docs.search("needle") } returns flowOf(listOf(internalDoc, publicDoc))
        coEvery { chunks.getByDoc("public-doc") } returns listOf(publicChunk)

        val results = KnowledgeKeywordFallback.search(
            query = "needle",
            topK = 5,
            scopeDocIds = null,
            metadataFilter = null,
            daos = KnowledgeKeywordFallback.SearchDaos(docDao = docs, chunkDao = chunks, ftsDao = fts),
        )

        assertEquals(1, results.size)
        assertEquals("public-doc", results.single().docId)
        assertEquals("public-chunk", results.single().chunkId)
        assertEquals(2, results.single().chunkIndex)
        assertEquals("chunk content", results.single().snippet)
    }
}
