package io.zer0.muse.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupFileStoreRestoreTest {

    @Test
    fun `file store restore propagates a write failure to trigger rollback`() {
        val written = mutableListOf<String>()

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                restoreBackupFileStores(
                    stores = mapOf(
                        "channel_configs.json" to "{}",
                        "pinned_memory/pinned.md" to "memory",
                    ),
                    allowedPaths = setOf("channel_configs.json", "pinned_memory/pinned.md"),
                ) { name, _ ->
                    written += name
                    if (name == "channel_configs.json") {
                        error("synthetic file-store failure")
                    }
                }
            }
        }

        assertEquals(listOf("channel_configs.json"), written)
    }

    @Test
    fun `file store snapshot propagates a read failure instead of omitting the file`() {
        assertThrows(IllegalStateException::class.java) {
            readBackupFileStores(paths = listOf("channel_configs.json")) {
                error("synthetic file-store read failure")
            }
        }
    }
}
