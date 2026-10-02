package io.zer0.muse.ui.knowledge

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KnowledgeArchiveImportWiringTest {

    @Test
    fun `main knowledge import routes supported archive bundles before file size and binary guards`() {
        val source = File("src/main/java/io/zer0/muse/ui/knowledge/KnowledgeScreen.kt").readText()
        val archiveRoute = source.indexOf("val archiveFormat = ArchiveFormat.fromFileName(lowerName)")

        assertTrue("主知识库导入必须统一识别支持的压缩包格式", archiveRoute >= 0)

        val bundleImporter = source.indexOf("importArchiveBundle(", archiveRoute)
        val sizeGuard = source.indexOf("if (fileSize > maxFileSizeBytes", archiveRoute)
        val binaryGuard = source.indexOf("val unsupportedTextCandidate", archiveRoute)

        assertTrue("支持的压缩包必须交给共享压缩包导入器", bundleImporter > archiveRoute)
        assertTrue("archive 分流必须先于文件大小限制", sizeGuard > bundleImporter)
        assertTrue("archive 分流必须先于通用二进制拦截", binaryGuard > bundleImporter)
    }
}
