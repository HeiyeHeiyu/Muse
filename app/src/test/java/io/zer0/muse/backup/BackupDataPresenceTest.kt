package io.zer0.muse.backup

import org.junit.Assert.assertTrue
import org.junit.Test
import sun.misc.Unsafe

class BackupDataPresenceTest {

    @Test
    fun `file store only backup is not considered empty`() {
        val backup = BackupService.Backup(
            exportedAt = 1L,
            sessions = emptyList(),
            messages = emptyList(),
            fileStores = mapOf("channel_configs.json" to "{}"),
        )

        assertTrue(invokeHasAnyData(backup))
    }

    private fun invokeHasAnyData(backup: BackupService.Backup): Boolean {
        val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as Unsafe
        val service = unsafe.allocateInstance(BackupService::class.java)
        val method = BackupService::class.java.getDeclaredMethod(
            "hasAnyData",
            BackupService.Backup::class.java,
        )
        method.isAccessible = true
        return method.invoke(service, backup) as Boolean
    }
}
