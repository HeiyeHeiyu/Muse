package io.zer0.muse.backup

import io.zer0.muse.data.session.MessageImageStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

class BackupNdJsonImageCleanupTest {
    @Test
    fun successfulStreamingRestoreKeepsReferencedAndNewImagesAndRemovesOrphans() = kotlinx.coroutines.runBlocking {
        val directory = Files.createTempDirectory("backup-ndjson-image-cleanup").toFile()
        val imageStore = MessageImageStore(directory)
        val keepValue = Base64.getEncoder().encodeToString(ByteArray(1024) { 1 })
        val orphanValue = Base64.getEncoder().encodeToString(ByteArray(1024) { 2 })
        val restoredValue = Base64.getEncoder().encodeToString(ByteArray(1024) { 3 })
        val keepPath = imageStore.toPersistable("keep", listOf(keepValue)).single()
        val orphanPath = imageStore.toPersistable("orphan", listOf(orphanValue)).single()
        var restoredPath = ""

        val counts = restoreAndPruneMessageImages(
            imageStore = imageStore,
            restore = {
                restoredPath = imageStore.toPersistable("restored", listOf(restoredValue)).single()
                imageStore.toPersistable("restored-again", listOf(restoredValue))
                2 to 3
            },
            readReferencedPaths = { setOf(keepPath, restoredPath) },
        )

        assertEquals(2 to 3, counts)
        assertTrue(File(keepPath.removePrefix("file://")).exists())
        assertTrue(!File(orphanPath.removePrefix("file://")).exists())
        assertTrue(File(restoredPath.removePrefix("file://")).exists())
    }

    @Test
    fun failedRestoreDoesNotPruneAnyPreexistingImage() = kotlinx.coroutines.runBlocking {
        val directory = Files.createTempDirectory("backup-ndjson-image-failure").toFile()
        val imageStore = MessageImageStore(directory)
        val value = Base64.getEncoder().encodeToString(ByteArray(1024) { 7 })
        val path = imageStore.toPersistable("pre-restore", listOf(value)).single()

        runCatching {
            restoreAndPruneMessageImages(
                imageStore = imageStore,
                restore = { error("restore failed") },
                readReferencedPaths = { emptySet() },
            )
        }

        assertTrue(File(path.removePrefix("file://")).exists())
    }

    @Test
    fun cleanupCompletesNonCancellablyAfterSuccessfulRestore() = runBlocking {
        val directory = Files.createTempDirectory("backup-ndjson-image-cancel").toFile()
        val imageStore = MessageImageStore(directory)
        val value = Base64.getEncoder().encodeToString(ByteArray(1024) { 8 })
        val orphanPath = imageStore.toPersistable("pre-restore", listOf(value)).single()
        val cleanupStarted = CompletableDeferred<Unit>()
        val continueCleanup = CompletableDeferred<Unit>()
        var restoreCompleted = false

        val job = launch {
            restoreAndPruneMessageImages(
                imageStore = imageStore,
                restore = {
                    restoreCompleted = true
                    4 to 5
                },
                readReferencedPaths = {
                    cleanupStarted.complete(Unit)
                    continueCleanup.await()
                    emptySet()
                },
            )
        }
        cleanupStarted.await()
        job.cancel()
        continueCleanup.complete(Unit)
        job.join()

        assertTrue(restoreCompleted)
        assertFalse(File(orphanPath.removePrefix("file://")).exists())
    }
}
