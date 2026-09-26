package io.zer0.muse.rag

import io.mockk.coEvery
import io.mockk.mockk
import io.zer0.muse.data.knowledge.KnowledgeChunkFtsDao
import io.zer0.muse.data.knowledge.KnowledgeChunkFtsHit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B4-03: RAG 元数据过滤测试。
 *
 * 覆盖验收点:
 *  - 无过滤 = 全量(metadataFilter 默认 null/空 → 现有调用方行为不变)
 *  - 按 documentId 过滤后,向量链路与 BM25(混合检索)链路结果一致
 *  - 标题 / 来源匹配在两条链路同样生效
 *  - [VectorSearchService.MetadataFilter.matches] 逐维度判定
 *
 * 测试数据:两个文档各 2 个块,所有块向量与查询同向(余弦恒为 1.0),
 * 因此结果集合只由元数据过滤决定,不受相似度阈值干扰。
 */
class MetadataFilterSearchTest {

    private val dim = 4

    /** 所有块共用同一向量,且与查询向量同向 → 余弦相似度恒为 1.0。 */
    private val sharedVector = FloatArray(dim) { 0.5f }

    private data class Chunk(
        val chunkId: String,
        val docId: String,
        val docTitle: String,
        val metadataJson: String,
        val createdAt: Long = 0L,
    )

    private val chunks = listOf(
        Chunk("A1", "doc-A", "合同法总则", """{"source":"upload/legal"}"""),
        Chunk("A2", "doc-A", "合同法总则", """{"source":"upload/legal"}"""),
        Chunk("B1", "doc-B", "侵权责任法", """{"source":"web"}"""),
        Chunk("B2", "doc-B", "侵权责任法", """{"source":"web"}"""),
    )

    private fun toChunkWithDoc(c: Chunk) = VectorSearchService.ChunkWithDoc(
        chunkId = c.chunkId,
        docId = c.docId,
        docTitle = c.docTitle,
        content = "content-${c.chunkId}",
        embedding = "",
        embeddingBlob = VectorSearchService.floatArrayToBlob(sharedVector),
        chunkIndex = 0,
        metadataJson = c.metadataJson,
        createdAt = c.createdAt,
    )

    private fun vectorService() = VectorSearchService(
        chunkPageProvider = { limit, offset -> chunks.map(::toChunkWithDoc).drop(offset).take(limit) },
        chunkCountProvider = { chunks.size },
    )

    /** BM25 命中横跨两个文档:为过滤用例提供「必须被 BM25 链路剔除」的样本。 */
    private fun ftsDao(): KnowledgeChunkFtsDao {
        val fts = mockk<KnowledgeChunkFtsDao>(relaxed = true)
        coEvery { fts.searchBm25(any(), any()) } returns listOf(
            KnowledgeChunkFtsHit(chunkId = "A1", score = -10.0),
            KnowledgeChunkFtsHit(chunkId = "B1", score = -11.0),
            KnowledgeChunkFtsHit(chunkId = "A2", score = -12.0),
        )
        return fts
    }

    private fun hybridService(vector: VectorSearchService) = HybridSearchService(
        ftsDao = ftsDao(),
        vectorSearch = vector,
        bm25MetaResolver = { ids ->
            ids.associateWith { id ->
                val c = chunks.first { it.chunkId == id }
                HybridSearchService.ChunkMeta(
                    docId = c.docId,
                    docTitle = c.docTitle,
                    content = "content-$id",
                    chunkIndex = 0,
                    metadataJson = c.metadataJson,
                    createdAt = c.createdAt,
                )
            }
        },
    )

    @Test
    fun `no filter returns all documents on both paths`() = runBlocking {
        val vector = vectorService()
        val vectorHits = vector.search(sharedVector, topK = 10, threshold = 0f)
        assertEquals(
            "无过滤时向量链路应返回全部块",
            chunks.map { it.chunkId }.toSet(),
            vectorHits.map { it.chunkId }.toSet(),
        )
        assertEquals(setOf("doc-A", "doc-B"), vectorHits.map { it.docId }.toSet())

        val hybridHits = hybridService(vector).hybridSearch(
            query = "合同法 侵权",
            queryVector = sharedVector,
            topK = 10,
            threshold = 0f,
            mmrLambda = 1f,
        )
        assertEquals(
            "无过滤时混合(BM25+向量)链路应返回两个文档",
            setOf("doc-A", "doc-B"),
            hybridHits.map { it.docId }.toSet(),
        )
    }

    @Test
    fun `document id filter applies to both vector and bm25 consistently`() = runBlocking {
        val filter = VectorSearchService.MetadataFilter(docIds = listOf("doc-A"))

        val vector = vectorService()
        val vectorHits = vector.search(sharedVector, topK = 10, threshold = 0f, metadataFilter = filter)
        assertTrue("向量链路按 docId 过滤后不得为空", vectorHits.isNotEmpty())
        assertTrue(
            "向量链路过滤后只能含 doc-A,实际=${vectorHits.map { it.docId }}",
            vectorHits.all { it.docId == "doc-A" },
        )

        // 混合链路:向量侧已过滤,BM25 侧必须同样剔除 doc-B 的命中(B1)
        val hybridHits = hybridService(vector).hybridSearch(
            query = "合同法",
            queryVector = sharedVector,
            topK = 10,
            threshold = 0f,
            mmrLambda = 1f,
            metadataFilter = filter,
        )
        assertTrue("混合链路按 docId 过滤后不得为空", hybridHits.isNotEmpty())
        assertTrue(
            "BM25(混合)链路过滤后只能含 doc-A,实际=${hybridHits.map { it.docId }}",
            hybridHits.all { it.docId == "doc-A" },
        )
        assertTrue(
            "BM25 侧应剔除非 doc-A 的命中 A1/A2 之外的块",
            hybridHits.none { it.chunkId == "B1" },
        )
        assertEquals(
            "向量链路与 BM25(混合)链路的文档集合必须一致",
            vectorHits.map { it.docId }.toSet(),
            hybridHits.map { it.docId }.toSet(),
        )
    }

    @Test
    fun `title keyword filter applies to both paths`() = runBlocking {
        val filter = VectorSearchService.MetadataFilter(titleKeyword = "侵权")

        val vector = vectorService()
        val vectorHits = vector.search(sharedVector, topK = 10, threshold = 0f, metadataFilter = filter)
        assertTrue(vectorHits.isNotEmpty())
        assertTrue("标题过滤后向量链路应只剩 doc-B", vectorHits.all { it.docId == "doc-B" })

        val hybridHits = hybridService(vector).hybridSearch(
            query = "侵权",
            queryVector = sharedVector,
            topK = 10,
            threshold = 0f,
            mmrLambda = 1f,
            metadataFilter = filter,
        )
        assertTrue(hybridHits.isNotEmpty())
        assertTrue("标题过滤后混合链路应只剩 doc-B", hybridHits.all { it.docId == "doc-B" })
    }

    @Test
    fun `source keyword filter applies to both paths`() = runBlocking {
        val filter = VectorSearchService.MetadataFilter(sourceKeyword = "web")

        val vector = vectorService()
        val vectorHits = vector.search(sharedVector, topK = 10, threshold = 0f, metadataFilter = filter)
        assertTrue(vectorHits.isNotEmpty())
        assertTrue("来源过滤后向量链路应只剩 doc-B", vectorHits.all { it.docId == "doc-B" })

        val hybridHits = hybridService(vector).hybridSearch(
            query = "侵权",
            queryVector = sharedVector,
            topK = 10,
            threshold = 0f,
            mmrLambda = 1f,
            metadataFilter = filter,
        )
        assertTrue(hybridHits.isNotEmpty())
        assertTrue("来源过滤后混合链路应只剩 doc-B", hybridHits.all { it.docId == "doc-B" })
    }

    @Test
    fun `empty filter is treated as no filter`() = runBlocking {
        val filter = VectorSearchService.MetadataFilter()
        assertTrue("全空 filter 应视为不过滤", filter.isEmpty())

        val hits = vectorService().search(sharedVector, topK = 10, threshold = 0f, metadataFilter = filter)
        assertEquals(
            "空 filter 结果应与无过滤一致",
            chunks.map { it.chunkId }.toSet(),
            hits.map { it.chunkId }.toSet(),
        )
    }

    @Test
    fun `metadata filter matches evaluates each dimension`() {
        val filter = VectorSearchService.MetadataFilter(
            docIds = listOf("doc-A"),
            tag = "rag",
            startTime = 100L,
            endTime = 200L,
            titleKeyword = "合同",
            sourceKeyword = "legal",
        )
        assertFalse(filter.isEmpty())
        val meta = """{"tag":"rag","source":"legal"}"""

        assertTrue("全部条件命中", filter.matches("doc-A", "合同法", meta, 150L))
        assertFalse("docId 不命中", filter.matches("doc-B", "合同法", meta, 150L))
        assertFalse("标题不命中", filter.matches("doc-A", "侵权法", meta, 150L))
        assertFalse("来源不命中", filter.matches("doc-A", "合同法", """{"tag":"rag","source":"web"}""", 150L))
        assertFalse("tag 不命中", filter.matches("doc-A", "合同法", """{"source":"legal"}""", 150L))
        assertFalse("早于 startTime", filter.matches("doc-A", "合同法", meta, 50L))
        assertFalse("晚于 endTime", filter.matches("doc-A", "合同法", meta, 250L))
    }
}
