package io.zer0.muse.rag

import io.zer0.muse.data.knowledge.KnowledgeDocEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeFolderBrowserTest {
    @Test
    fun `zip entries become navigable relative folders`() {
        val docs = listOf(
            KnowledgeDocEntity(id = "one", title = "one.txt", filePath = "zip://bundle.zip/Notes/one.txt"),
            KnowledgeDocEntity(id = "two", title = "two.txt", filePath = "zip://bundle.zip/Notes/Nested/two.txt"),
            KnowledgeDocEntity(id = "root", title = "root.txt", filePath = "zip://bundle.zip/root.txt"),
        )

        assertEquals("Notes", KnowledgeFolderBrowser.folderPath(docs[0]))
        assertEquals("Notes/Nested", KnowledgeFolderBrowser.folderPath(docs[1]))
        assertEquals("", KnowledgeFolderBrowser.folderPath(docs[2]))
        val folders = KnowledgeFolderBrowser.childFolders(docs, "")
        assertEquals(listOf(KnowledgeFolderBrowser.Folder("Notes", "Notes", 2)), folders)
        assertEquals(listOf("one.txt"), KnowledgeFolderBrowser.documentsInFolder(docs, "Notes").map { it.title })
    }

    @Test
    fun `zip 7z and rar entries expose the same relative folder structure`() {
        val docs = listOf(
            KnowledgeDocEntity(id = "zip", title = "one.md", filePath = "zip://bundle.zip/Notes/one.md"),
            KnowledgeDocEntity(id = "7z", title = "two.md", filePath = "7z://bundle.7z/Notes/two.md"),
            KnowledgeDocEntity(id = "rar", title = "three.md", filePath = "rar://bundle.rar/Notes/three.md"),
        )

        assertEquals(listOf("Notes", "Notes", "Notes"), docs.map(KnowledgeFolderBrowser::folderPath))
        assertEquals(
            listOf(KnowledgeFolderBrowser.Folder("Notes", "Notes", 3)),
            KnowledgeFolderBrowser.childFolders(docs, ""),
        )
    }

    @Test
    fun `explicit folder assignment overrides zip source and preserves other metadata`() {
        val doc = KnowledgeDocEntity(
            id = "one",
            title = "one.txt",
            filePath = "zip://bundle.zip/Old/one.txt",
            metadataJson = "{\"tags\":[\"keep\"],\"source\":\"zip\"}",
        )

        val moved = KnowledgeFolderBrowser.withFolderPath(doc, "Projects/2026")

        assertEquals("Projects/2026", KnowledgeFolderBrowser.folderPath(moved))
        assertTrue(moved.metadataJson.contains("\"tags\":[\"keep\"]"))
        assertTrue(moved.metadataJson.contains("\"source\":\"zip\""))
        assertEquals("", KnowledgeFolderBrowser.parent("Projects"))
        assertEquals("Projects", KnowledgeFolderBrowser.parent("Projects/2026"))
    }

    @Test
    fun `folder paths are normalized to prevent traversal and empty segments`() {
        assertEquals("Docs/2026", KnowledgeFolderBrowser.normalize("/Docs//../Docs/2026/"))
    }

    @Test
    fun `import stays in the current folder only for the selected knowledge base`() {
        assertEquals(
            "Projects/2026",
            KnowledgeFolderBrowser.importFolderPath(
                targetKbId = "kb-work",
                browsingKbId = "kb-work",
                currentFolderPath = "Projects/2026",
                searchQuery = "",
            ),
        )
    }

    @Test
    fun `imports from search or into another knowledge base start at its root`() {
        assertEquals(
            "",
            KnowledgeFolderBrowser.importFolderPath(
                targetKbId = "kb-work",
                browsingKbId = "kb-work",
                currentFolderPath = "Projects/2026",
                searchQuery = "spec",
            ),
        )
        assertEquals(
            "",
            KnowledgeFolderBrowser.importFolderPath(
                targetKbId = "kb-personal",
                browsingKbId = "kb-work",
                currentFolderPath = "Projects/2026",
                searchQuery = "",
            ),
        )
    }
}
