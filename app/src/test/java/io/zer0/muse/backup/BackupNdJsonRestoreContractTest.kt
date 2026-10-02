package io.zer0.muse.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupNdJsonRestoreContractTest {

    @Test
    fun `ndjson restore must propagate fts rebuild failure`() {
        val source = File("src/main/java/io/zer0/muse/backup/BackupService.kt").readText()
        val start = source.indexOf("private suspend fun applyNdJsonStreaming(")
        val end = source.indexOf(
            "private suspend fun applyNdJsonStreamingWithImageCleanup",
            startIndex = start,
        )
        require(start >= 0 && end > start) { "无法定位 NDJSON 恢复方法边界" }
        val ndJsonRestoreMethod = source.substring(start, end)

        assertFalse(
            "NDJSON 恢复不能吞掉 FTS 重建异常，否则不会触发 recovery point 回滚",
            ndJsonRestoreMethod.contains("resultOf { sessionRepository.rebuildFtsIndex() }"),
        )
        assertTrue(ndJsonRestoreMethod.contains("sessionRepository.rebuildFtsIndex()"))
    }

    @Test
    fun `internal ndjson recovery accepts an empty pre-restore snapshot`() {
        val source = File("src/main/java/io/zer0/muse/backup/BackupService.kt").readText()

        assertTrue(
            "恢复点导入必须有 allowEmptyBackup 入口",
            source.contains("allowEmptyBackup"),
        )
        assertTrue(
            "NDJSON 失败回滚必须显式允许空恢复点",
            source.contains("applyNdJsonStreaming(recoveryLines, allowEmptyBackup = true)"),
        )
    }
}
