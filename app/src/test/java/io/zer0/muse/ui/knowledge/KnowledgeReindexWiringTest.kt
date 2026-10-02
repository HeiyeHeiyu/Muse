package io.zer0.muse.ui.knowledge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KnowledgeReindexWiringTest {

    @Test
    fun `single document reindex does not pass the preview directly to indexDocument`() {
        val source = File("src/main/java/io/zer0/muse/ui/knowledge/KnowledgeScreen.kt").readText()

        assertFalse(
            "长文档的 doc.content 可能只是预览，单文档重索引必须走原始内容恢复链路",
            source.contains("ragService.indexDocument(doc.id, doc.content"),
        )
    }

    @Test
    fun `knowledge importers use persistable document access`() {
        val screen = File("src/main/java/io/zer0/muse/ui/knowledge/KnowledgeScreen.kt").readText()
        val manager = File("src/main/java/io/zer0/muse/ui/knowledge/KnowledgeBaseManagePage.kt").readText()

        assertFalse(screen.contains("ActivityResultContracts.GetContent()"))
        assertFalse(manager.contains("ActivityResultContracts.GetContent()"))
        assertTrue(screen.contains("persistKnowledgeUriReadPermission(context, uri)"))
        assertTrue(manager.contains("persistKnowledgeUriReadPermission(context, uri)"))
    }
}
