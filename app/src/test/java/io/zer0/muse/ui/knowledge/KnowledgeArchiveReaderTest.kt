package io.zer0.muse.ui.knowledge

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class KnowledgeArchiveReaderTest {

    @Test
    fun `zip reader streams nested file contents`() {
        val archiveFile = File.createTempFile("knowledge-reader-", ".zip")
        val content = "zip note".toByteArray()
        try {
            ZipOutputStream(archiveFile.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("Notes/readme.md"))
                output.write(content)
                output.closeEntry()
            }

            ZipArchiveEntryReader(archiveFile.inputStream()).use { reader ->
                val entry = reader.next() ?: throw AssertionError("ZIP entry missing")
                assertEquals("Notes/readme.md", entry.name)
                assertArrayEquals(content, entry.openStream().use { it.readBytes() })
                assertNull(reader.next())
            }
        } finally {
            archiveFile.delete()
        }
    }

    @Test
    fun `7z reader streams file contents and advances entries`() {
        val archiveFile = File.createTempFile("knowledge-reader-", ".7z")
        val content = "folder note".toByteArray()
        try {
            SevenZOutputFile(archiveFile).use { output ->
                val entry = SevenZArchiveEntry().apply { name = "Notes/readme.md" }
                output.putArchiveEntry(entry)
                output.write(content)
                output.closeArchiveEntry()
            }

            SevenZArchiveEntryReader(archiveFile).use { reader ->
                val entry = reader.next() ?: throw AssertionError("7z entry missing")
                assertEquals("Notes/readme.md", entry.name)
                assertArrayEquals(content, entry.openStream().use { it.readBytes() })
                assertNull(reader.next())
            }
        } finally {
            archiveFile.delete()
        }
    }

    @Test
    fun `rar reader extracts entries synchronously with their original bytes`() {
        val sample = javaClass.getResourceAsStream("/knowledge/archive-fixtures/rar4_sample.rar")
        checkNotNull(sample)
        val archiveFile = File.createTempFile("knowledge-reader-", ".rar")
        try {
            sample.use { input ->
                archiveFile.outputStream().use { output -> input.copyTo(output) }
            }

            RarArchiveEntryReader(archiveFile).use { reader ->
                val first = reader.next() ?: throw AssertionError("First RAR entry missing")
                assertEquals("FILE1.TXT", first.name)
                val firstContent = ByteArrayOutputStream()
                first.extractToFile?.invoke(firstContent)
                assertArrayEquals("file1\r\n".toByteArray(), firstContent.toByteArray())

                val second = reader.next() ?: throw AssertionError("Second RAR entry missing")
                assertEquals("FILE2.TXT", second.name)
                val secondContent = ByteArrayOutputStream()
                second.extractToFile?.invoke(secondContent)
                assertArrayEquals("file2\r\n".toByteArray(), secondContent.toByteArray())
                assertNull(reader.next())
            }
        } finally {
            archiveFile.delete()
        }
    }

    @Test
    fun `rar5 reader extracts entries synchronously with their original bytes`() {
        val sample = javaClass.getResourceAsStream("/knowledge/archive-fixtures/rar5_sample.rar")
        checkNotNull(sample)
        val archiveFile = File.createTempFile("knowledge-reader-", ".rar")
        try {
            sample.use { input ->
                archiveFile.outputStream().use { output -> input.copyTo(output) }
            }

            RarArchiveEntryReader(archiveFile).use { reader ->
                val first = reader.next() ?: throw AssertionError("First RAR5 entry missing")
                assertEquals("FILE1.TXT", first.name)
                val firstContent = ByteArrayOutputStream()
                first.extractToFile?.invoke(firstContent)
                assertArrayEquals("file1\r\n".toByteArray(), firstContent.toByteArray())

                val second = reader.next() ?: throw AssertionError("Second RAR5 entry missing")
                assertEquals("FILE2.TXT", second.name)
                val secondContent = ByteArrayOutputStream()
                second.extractToFile?.invoke(secondContent)
                assertArrayEquals("file2\r\n".toByteArray(), secondContent.toByteArray())
                assertNull(reader.next())
            }
        } finally {
            archiveFile.delete()
        }
    }

    @Test
    fun `rar checksum failures are surfaced instead of returning partial content`() {
        val sample = javaClass.getResourceAsStream("/knowledge/archive-fixtures/rar4_sample.rar")
        checkNotNull(sample)
        val archiveFile = File.createTempFile("knowledge-reader-", ".rar")
        try {
            val bytes = sample.use { it.readBytes() }
            val fileContent = "file1\r\n".toByteArray()
            val dataOffset = bytes.indexOf(fileContent)
            assertTrue("RAR sample must contain the stored first entry", dataOffset >= 0)
            bytes[dataOffset] = (bytes[dataOffset].toInt() xor 0x01).toByte()
            archiveFile.writeBytes(bytes)

            RarArchiveEntryReader(archiveFile).use { reader ->
                val entry = reader.next() ?: throw AssertionError("First RAR entry missing")
                org.junit.Assert.assertThrows(Exception::class.java) {
                    entry.extractToFile?.invoke(ByteArrayOutputStream())
                }
            }
        } finally {
            archiveFile.delete()
        }
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int = (0..size - needle.size).firstOrNull { offset ->
        needle.indices.all { index -> this[offset + index] == needle[index] }
    } ?: -1
}
