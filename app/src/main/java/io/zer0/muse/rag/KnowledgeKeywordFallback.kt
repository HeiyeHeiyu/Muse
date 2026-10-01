package io.zer0.muse.rag

import io.zer0.common.resultOf
import io.zer0.muse.data.knowledge.KnowledgeChunkDao
import io.zer0.muse.data.knowledge.KnowledgeChunkFtsDao
import io.zer0.muse.data.knowledge.KnowledgeDocDao
import kotlinx.coroutines.flow.first

/** A keyword-retrieved chunk with enough identity to produce a real citation. */
data class KeywordFallbackResult(
    val docId: String,
    val docTitle: String,
    val snippet: String,
    val chunkId: String = "",
    val chunkIndex: Int = 0,
    val score: Float = 0f,
    val isInternal: Boolean = false,
    val metadataJson: String = "{}",
    val createdAt: Long = 0L,
)

/** Keeps FTS and LIKE fallback behavior aligned with vector scope and metadata filters. */
object KnowledgeKeywordFallback {
    suspend fun search(
        query: String,
        topK: Int,
        scopeDocIds: List<String>?,
        metadataFilter: VectorSearchService.MetadataFilter?,
        docDao: KnowledgeDocDao,
        chunkDao: KnowledgeChunkDao,
        ftsDao: KnowledgeChunkFtsDao,
    ): List<KeywordFallbackResult> {
        if (query.isBlank() || topK <= 0) return emptyList()
        val allowedIds = scopeDocIds?.toSet()
        if (allowedIds != null && allowedIds.isEmpty()) return emptyList()
        val filter = metadataFilter?.takeUnless { it.isEmpty() }

        val hits = resultOf { ftsDao.searchBm25Safe(query, (topK * 3).coerceAtLeast(topK)) }
            .getOrNull().orEmpty()
        val chunks = if (hits.isEmpty()) emptyList() else {
            resultOf { chunkDao.getByIds(hits.map { it.chunkId }.distinct()) }.getOrNull().orEmpty()
        }
        val chunkById = chunks.associateBy { it.id }
        val docs = if (chunks.isEmpty()) emptyList() else {
            resultOf { docDao.getByIds(chunks.map { it.docId }.distinct()) }.getOrNull().orEmpty()
        }
        val docById = docs.asSequence()
            .filter { !it.isInternal && (allowedIds == null || it.id in allowedIds) }
            .associateBy { it.id }

        val ftsResults = hits.mapNotNull { hit ->
            val chunk = chunkById[hit.chunkId] ?: return@mapNotNull null
            val doc = docById[chunk.docId] ?: return@mapNotNull null
            if (filter != null && !filter.matches(doc.id, doc.title, chunk.metadataJson, chunk.createdAt)) {
                return@mapNotNull null
            }
            KeywordFallbackResult(
                docId = doc.id,
                docTitle = doc.title,
                snippet = chunk.content.take(MAX_SNIPPET_CHARS),
                chunkId = chunk.id,
                chunkIndex = chunk.chunkIndex,
                score = (-hit.score).toFloat(),
                metadataJson = chunk.metadataJson,
                createdAt = chunk.createdAt,
            )
        }.distinctBy { it.chunkId }.take(topK)
        if (ftsResults.isNotEmpty()) return ftsResults

        val fallbackDocs = resultOf {
            if (allowedIds == null) docDao.search(query).first() else docDao.getByIds(allowedIds.toList())
        }.getOrNull().orEmpty()
        val publicDocs = fallbackDocs.asSequence()
            .filter { !it.isInternal && (allowedIds == null || it.id in allowedIds) }
            .take(topK)
            .toList()

        return publicDocs.mapNotNull { doc ->
            val docChunks = resultOf { chunkDao.getByDoc(doc.id) }.getOrNull().orEmpty()
            val matchingChunk = docChunks.firstOrNull { chunk ->
                filter == null || filter.matches(doc.id, doc.title, chunk.metadataJson, chunk.createdAt)
            }
            if (filter != null && matchingChunk == null) return@mapNotNull null
            KeywordFallbackResult(
                docId = doc.id,
                docTitle = doc.title,
                snippet = (matchingChunk?.content?.takeIf { it.isNotBlank() } ?: doc.content).take(MAX_SNIPPET_CHARS),
                chunkId = matchingChunk?.id.orEmpty(),
                chunkIndex = matchingChunk?.chunkIndex ?: 0,
                metadataJson = matchingChunk?.metadataJson ?: "{}",
                createdAt = matchingChunk?.createdAt ?: 0L,
            )
        }
    }

    private const val MAX_SNIPPET_CHARS = 500
}
