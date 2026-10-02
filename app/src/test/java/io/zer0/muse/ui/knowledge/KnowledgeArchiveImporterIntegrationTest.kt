package io.zer0.muse.ui.knowledge

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.zer0.muse.data.SettingsRepository
import io.zer0.muse.data.knowledge.KnowledgeBaseDao
import io.zer0.muse.data.knowledge.KnowledgeBaseEntity
import io.zer0.muse.data.knowledge.KnowledgeDocDao
import io.zer0.muse.data.knowledge.KnowledgeDocEntity
import io.zer0.muse.doc.DocumentParser
import io.zer0.muse.rag.RagConfig
import io.zer0.muse.rag.RagService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KnowledgeArchiveImporterIntegrationTest {

    @Test
    fun `zip 7z rar4 and rar5 import text entries into indexed documents`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cases = listOf(
            archiveCase(context, ArchiveFormat.ZIP, "manuals/getting-started.md", "zip guide"),
            archiveCase(context, ArchiveFormat.SEVEN_Z, "manuals/getting-started.md", "7z guide"),
            rarFixture(context, "rar4_sample.rar", "file1\r\n", "file2\r\n"),
            rarFixture(context, "rar5_sample.rar", "file1\r\n", "file2\r\n"),
        )

        try {
            cases.forEach { (archive, format, expectedEntries) ->
                assertArchiveImported(context, archive, format, expectedEntries)
            }
        } finally {
            cases.forEach { it.archive.delete() }
        }
    }

    private suspend fun assertArchiveImported(
        context: Context,
        archive: File,
        format: ArchiveFormat,
        expectedEntries: Map<String, String>,
    ) {
        val kbDao = mockk<KnowledgeBaseDao>(relaxed = true)
        val docDao = mockk<KnowledgeDocDao>()
        val settings = mockk<SettingsRepository>()
        val ragService = mockk<RagService>()
        val knowledgeBases = mutableListOf<KnowledgeBaseEntity>()
        val documents = mutableMapOf<String, KnowledgeDocEntity>()
        val indexedContent = mutableMapOf<String, String>()

        coEvery { kbDao.upsert(any()) } coAnswers {
            knowledgeBases += firstArg<KnowledgeBaseEntity>()
        }
        coEvery { settings.getRagConfig() } returns RagConfig()
        coEvery { docDao.upsert(any()) } coAnswers {
            firstArg<KnowledgeDocEntity>().also { documents[it.id] = it }
        }
        coEvery { docDao.getById(any()) } coAnswers { documents[firstArg<String>()] }
        coEvery {
            ragService.indexDocumentStreamed(any(), any(), any(), any(), any())
        } coAnswers {
            val docId = firstArg<String>()
            indexedContent[docId] = secondArg<Flow<String>>().toList().joinToString("")
            1
        }

        importArchiveBundle(
            archiveUri = Uri.fromFile(archive),
            archiveName = archive.name,
            format = format,
            deps = ArchiveImportDeps(
                context = context,
                kbDao = kbDao,
                docDao = docDao,
                ragService = ragService,
                settings = settings,
                documentParser = mockk<DocumentParser>(relaxed = true),
                onProgress = {},
            ),
        )

        assertEquals(1, knowledgeBases.size)
        assertEquals("All supported text entries should be imported", expectedEntries.size, documents.size)
        assertTrue(documents.values.all { it.kbId == knowledgeBases.single().id })
        expectedEntries.forEach { (entryPath, expectedContent) ->
            val sourcePath = "${format.sourceScheme}://${archive.name}/$entryPath"
            val document = documents.values.singleOrNull { it.filePath == sourcePath }
            assertNotNull("Missing imported archive entry: $sourcePath", document)
            document!!
            assertEquals(expectedContent, indexedContent[document.id])
            assertEquals(1, document.chunkCount)
            assertEquals(RagConfig.embeddingModelKey(RagConfig()), document.embeddingModel)
            assertTrue("Imported content hash should be persisted", document.contentHash.isNotBlank())
        }
        coVerify(exactly = 1) { kbDao.upsert(match { it.description == archive.name }) }
    }

    private fun archiveCase(
        context: Context,
        format: ArchiveFormat,
        entryPath: String,
        content: String,
    ): ArchiveCase {
        val extension = ".${format.extension}"
        val archive = File.createTempFile("knowledge-import-", extension, context.cacheDir)
        when (format) {
            ArchiveFormat.ZIP -> ZipOutputStream(archive.outputStream()).use { output ->
                output.putNextEntry(ZipEntry(entryPath))
                output.write(content.toByteArray())
                output.closeEntry()
            }
            ArchiveFormat.SEVEN_Z -> SevenZOutputFile(archive).use { output ->
                output.putArchiveEntry(SevenZArchiveEntry().apply { name = entryPath })
                output.write(content.toByteArray())
                output.closeArchiveEntry()
            }
            ArchiveFormat.RAR -> error("RAR archives use checked-in fixtures")
        }
        return ArchiveCase(archive, format, mapOf(entryPath to content))
    }

    private fun rarFixture(
        context: Context,
        fixtureName: String,
        firstContent: String,
        secondContent: String,
    ): ArchiveCase {
        val fixture = checkNotNull(javaClass.getResourceAsStream("/knowledge/archive-fixtures/$fixtureName"))
        val archive = File.createTempFile("knowledge-import-", ".rar", context.cacheDir)
        fixture.use { input -> archive.outputStream().use(input::copyTo) }

        return ArchiveCase(
            archive,
            ArchiveFormat.RAR,
            linkedMapOf(
                "FILE1.TXT" to firstContent,
                "FILE2.TXT" to secondContent,
            ),
        )
    }

    private data class ArchiveCase(
        val archive: File,
        val format: ArchiveFormat,
        val expectedEntries: Map<String, String>,
    )
}
