package io.zer0.muse.data.knowledge

import android.content.Context
import android.content.res.AssetManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.zer0.muse.rag.RagConfig
import io.zer0.muse.rag.RagService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class BuiltInKnowledgeDocSeederTest {

    @Test
    fun `seeding is idempotent and marks docs internal`() = runBlocking {
        val context = mockk<Context>()
        val assets = mockk<AssetManager>()
        val dao = mockk<KnowledgeDocDao>(relaxed = true)
        every { context.assets } returns assets
        every { assets.list("devdocs") } returns arrayOf("guide.md")
        every { assets.open("devdocs/guide.md") } returns
            ByteArrayInputStream("# Guide\nUse tools safely.".toByteArray())
        coEvery { dao.upsert(any()) } returns Unit

        val seeder = BuiltInKnowledgeDocSeeder(context, dao)
        seeder.ensureSeeded()
        seeder.ensureSeeded()

        coVerify(exactly = 1) {
            dao.upsert(
                match {
                    it.id == "devdoc-guide" &&
                        it.isInternal &&
                        it.fileType == "devdoc" &&
                        it.title == "Guide"
                },
            )
        }
        assertTrue(true)
    }

    @Test
    fun `seeding keeps index metadata when content is unchanged`() = runBlocking {
        val context = mockk<Context>()
        val assets = mockk<AssetManager>()
        val dao = mockk<KnowledgeDocDao>(relaxed = true)
        val content = "# Guide\nUse tools safely."
        every { context.assets } returns assets
        every { assets.list("devdocs") } returns arrayOf("guide.md")
        every { assets.open("devdocs/guide.md") } returns ByteArrayInputStream(content.toByteArray())
        coEvery { dao.getInternalDocs() } returns
            listOf(
                KnowledgeDocEntity(
                    id = "devdoc-guide",
                    title = "Guide",
                    content = content,
                    fileType = "devdoc",
                    isInternal = true,
                    chunkCount = 4,
                    contentHash = RagService.computeContentHash(content),
                ),
            )

        BuiltInKnowledgeDocSeeder(context, dao).ensureSeeded()

        // 内容未变且已索引：不重写、不丢 chunk_count
        coVerify(exactly = 0) { dao.upsert(any()) }
        assertTrue(true)
    }

    @Test
    fun `ensureIndexed builds index for pending docs and persists metadata`() = runBlocking {
        val context = mockk<Context>()
        val dao = mockk<KnowledgeDocDao>(relaxed = true)
        val ragService = mockk<RagService>()
        val config = RagConfig()
        val content = "# Guide\n正文"
        val doc =
            KnowledgeDocEntity(
                id = "devdoc-guide",
                title = "Guide",
                content = content,
                fileType = "devdoc",
                isInternal = true,
                chunkCount = 0,
                contentHash = "",
            )
        coEvery { dao.getInternalDocs() } returns listOf(doc)
        coEvery { ragService.indexDocument("devdoc-guide", content, config) } returns 3

        val seeder = BuiltInKnowledgeDocSeeder(context, dao, ragService) { config }
        seeder.ensureIndexed()
        seeder.ensureIndexed()

        coVerify(exactly = 1) { ragService.indexDocument("devdoc-guide", content, config) }
        coVerify(exactly = 1) {
            dao.upsert(match { it.id == "devdoc-guide" && it.chunkCount == 3 && it.embeddingModel.isNotBlank() })
        }
        assertTrue(true)
    }

    @Test
    fun `ensureIndexed skips docs already indexed with same hash`() = runBlocking {
        val context = mockk<Context>()
        val dao = mockk<KnowledgeDocDao>(relaxed = true)
        val ragService = mockk<RagService>()
        val content = "# Guide\n正文"
        coEvery { dao.getInternalDocs() } returns
            listOf(
                KnowledgeDocEntity(
                    id = "devdoc-guide",
                    title = "Guide",
                    content = content,
                    fileType = "devdoc",
                    isInternal = true,
                    chunkCount = 3,
                    embeddingModel = "embed:local_keyword",
                    contentHash = RagService.computeContentHash(content),
                ),
            )

        BuiltInKnowledgeDocSeeder(context, dao, ragService) { RagConfig() }.ensureIndexed()

        coVerify(exactly = 0) { ragService.indexDocument(any(), any(), any()) }
        coVerify(exactly = 0) { dao.upsert(any()) }
        assertTrue(true)
    }

    @Test
    fun `ensureIndexed tolerates embedding failures`() = runBlocking {
        val context = mockk<Context>()
        val dao = mockk<KnowledgeDocDao>(relaxed = true)
        val ragService = mockk<RagService>()
        coEvery { dao.getInternalDocs() } returns
            listOf(
                KnowledgeDocEntity(
                    id = "devdoc-guide",
                    title = "Guide",
                    content = "# Guide\n正文",
                    fileType = "devdoc",
                    isInternal = true,
                    chunkCount = 0,
                    contentHash = "",
                ),
            )
        coEvery { ragService.indexDocument(any(), any(), any()) } throws
            IllegalStateException("embedding provider unavailable")

        BuiltInKnowledgeDocSeeder(context, dao, ragService) { RagConfig() }.ensureIndexed()

        coVerify(exactly = 0) { dao.upsert(any()) }
        assertTrue(true)
    }
}
