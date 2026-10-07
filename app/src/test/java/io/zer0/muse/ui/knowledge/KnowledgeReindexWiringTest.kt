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
        // v2.4.6: 知识库管理页已与 KnowledgeScreen 合并为同一页,只查合并后的单页。
        val screen = File("src/main/java/io/zer0/muse/ui/knowledge/KnowledgeScreen.kt").readText()

        assertFalse(screen.contains("ActivityResultContracts.GetContent()"))
        assertTrue(screen.contains("persistKnowledgeUriReadPermission(context, uri)"))
    }
}
