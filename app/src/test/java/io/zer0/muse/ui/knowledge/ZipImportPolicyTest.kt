package io.zer0.muse.ui.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.x: zip 导入策略单测(纯逻辑,无 Android 依赖)。 */
class ZipImportPolicyTest {
    @Test
    fun `classify separates text parsed and skip`() {
        assertEquals(ZipEntryKind.TEXT, ZipImportPolicy.classify("docs/a.md"))
        assertEquals(ZipEntryKind.TEXT, ZipImportPolicy.classify("READ_ME.TXT"))
        assertEquals(ZipEntryKind.TEXT, ZipImportPolicy.classify("data/x.csv"))
        assertEquals(ZipEntryKind.PARSED, ZipImportPolicy.classify("书.pdf"))
        assertEquals(ZipEntryKind.PARSED, ZipImportPolicy.classify("legacy.doc"))
        assertEquals(ZipEntryKind.PARSED, ZipImportPolicy.classify("a/b/c.docx"))
        assertEquals(ZipEntryKind.SKIP, ZipImportPolicy.classify("nested.zip"))
        assertEquals(ZipEntryKind.SKIP, ZipImportPolicy.classify("app.exe"))
        assertEquals(ZipEntryKind.SKIP, ZipImportPolicy.classify("photo.png"))
        assertEquals(ZipEntryKind.SKIP, ZipImportPolicy.classify("noext"))
    }

    @Test
    fun `junk filter covers dirs hidden and macosx`() {
        assertTrue(ZipImportPolicy.isJunk("dir/"))
        assertTrue(ZipImportPolicy.isJunk("__MACOSX/x.txt"))
        assertTrue(ZipImportPolicy.isJunk(".hidden"))
        assertTrue(ZipImportPolicy.isJunk("a/.DS_Store"))
        assertTrue(ZipImportPolicy.isJunk(""))
        assertFalse(ZipImportPolicy.isJunk("正常文档.pdf"))
        assertFalse(ZipImportPolicy.isJunk("dir/file.txt"))
    }

    @Test
    fun `temp name keeps extension and strips path`() {
        val name = ZipImportPolicy.sanitizeTempName("目录/报告 v2.pdf")
        assertTrue(name.endsWith(".pdf"))
        assertFalse(name.contains('/'))
        val noExt = ZipImportPolicy.sanitizeTempName("README")
        assertEquals("entry_README", noExt)
    }

    @Test
    fun `file type helpers match single file import convention`() {
        assertEquals("md", ZipImportPolicy.textFileType("a.MD"))
        assertEquals("txt", ZipImportPolicy.textFileType("a.txt"))
        assertEquals("pdf", ZipImportPolicy.parsedFileType("a.pdf"))
        assertEquals("docx", ZipImportPolicy.parsedFileType("a.doc"))
    }
}
