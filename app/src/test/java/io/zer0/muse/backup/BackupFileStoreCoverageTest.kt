package io.zer0.muse.backup

import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileStoreCoverageTest {

    @Test
    fun `backup file store allowlist includes scoped pinned memory files`() {
        assertTrue("pinned_memory/pinned-memory.json" in BackupService.backupFileStorePaths)
        assertTrue("pinned_memory/pinned.md" in BackupService.backupFileStorePaths)
    }
}
