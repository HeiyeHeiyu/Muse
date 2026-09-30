package io.zer0.memory.summary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.zer0.memory.fact.MemoryLegacyReset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [33])
class MemoryDbDowngradeGuardTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseFile: File
    private val databaseName = "memory_downgrade_${UUID.randomUUID()}.db"
    private val archives = mutableListOf<File>()

    @Before
    fun setUp() {
        databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()
        MemoryLegacyReset.consume(context)
    }

    @After
    fun tearDown() {
        listOf(
            databaseFile,
            File("${databaseFile.absolutePath}-wal"),
            File("${databaseFile.absolutePath}-shm"),
        ).forEach { it.delete() }
        archives.forEach { it.deleteRecursively() }
        MemoryLegacyReset.consume(context)
    }

    @Test
    fun newerDatabaseIsArchivedBeforeRoomCreatesCurrentSchema() {
        createFutureDatabase(version = MEMORY_DB_VERSION + 1)

        val archive = MemoryDbDowngradeGuard.archiveIfNewer(
            context,
            databaseName,
            MEMORY_DB_VERSION,
        )
        assertTrue(archive != null)
        val backup = checkNotNull(archive)
        archives += backup
        assertTrue("archive is published before the active db is touched", databaseFile.exists())
        assertTrue(backup.isDirectory)
        val restoredCopy = copyArchiveForRestore(backup)
        assertEquals(MEMORY_DB_VERSION + 1, readVersion(restoredCopy))
        assertEquals("keep me", readMarker(restoredCopy))

        val currentDb = Room.databaseBuilder(context, MemoryDb::class.java, databaseFile.absolutePath)
            .addMigrations(MemoryDb.MIGRATION_1_2, MemoryDb.MIGRATION_2_3)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .allowMainThreadQueries()
            .build()
        currentDb.openHelper.writableDatabase.query("SELECT COUNT(*) FROM session_summaries").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        assertEquals("backup retains pre-downgrade memory", "keep me", readMarker(restoredCopy))
        currentDb.close()
        assertTrue("memory screen should be notified about the archived downgrade", MemoryLegacyReset.consume(context))
    }

    @Test
    fun existingArchiveIsNeverOverwritten() {
        createFutureDatabase(version = MEMORY_DB_VERSION + 1)
        val preferred = File(
            databaseFile.parentFile,
            "${databaseFile.name}.pre-destructive.v${MEMORY_DB_VERSION + 1}.bak",
        )
        preferred.mkdir()
        File(preferred, "preserved.txt").writeText("previous recovery copy")
        archives += preferred

        val archive = MemoryDbDowngradeGuard.archiveIfNewer(
            context,
            databaseName,
            MEMORY_DB_VERSION,
        )

        assertTrue(archive != null)
        val newArchive = checkNotNull(archive)
        archives += newArchive
        assertEquals("previous recovery copy", File(preferred, "preserved.txt").readText())
        assertEquals("keep me", readMarker(copyArchiveForRestore(newArchive)))
    }

    @Test
    fun newerWalDatabaseArchivesItsSidecarsAndUncheckpointedRows() {
        val writer = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            assertTrue(writer.enableWriteAheadLogging())
            writer.rawQuery("PRAGMA journal_mode=WAL", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
            writer.execSQL("CREATE TABLE recovery_probe (value TEXT NOT NULL)")
            writer.execSQL("INSERT INTO recovery_probe(value) VALUES ('main-row')")
            writer.version = MEMORY_DB_VERSION + 1

            val reader = SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            try {
                val snapshot = reader.rawQuery("SELECT value FROM recovery_probe", null)
                try {
                    check(snapshot.moveToFirst())
                    writer.execSQL("INSERT INTO recovery_probe(value) VALUES ('wal-only-row')")
                    val walFile = File("${databaseFile.absolutePath}-wal")
                    assertTrue("test must create a non-empty WAL sidecar", walFile.isFile && walFile.length() > 0)

                    val archive = checkNotNull(
                        MemoryDbDowngradeGuard.archiveIfNewer(context, databaseName, MEMORY_DB_VERSION),
                    )
                    archives += archive
                    assertTrue(File(archive, "$databaseName-wal").isFile)
                    assertTrue(File(archive, "$databaseName-shm").isFile)
                    val restoredCopy = copyArchiveForRestore(archive)
                    assertEquals(setOf("main-row", "wal-only-row"), readMarkers(restoredCopy).toSet())
                } finally {
                    snapshot.close()
                }
            } finally {
                reader.close()
            }
        } finally {
            writer.close()
        }
    }

    @Test
    fun currentVersionDatabaseIsNotArchived() {
        createFutureDatabase(version = MEMORY_DB_VERSION)

        assertNull(
            MemoryDbDowngradeGuard.archiveIfNewer(
                context,
                databaseName,
                MEMORY_DB_VERSION,
            ),
        )
        assertTrue(databaseFile.exists())
        assertEquals("keep me", readMarker(databaseFile))
        assertFalse(MemoryLegacyReset.consume(context))
    }

    @Test
    fun unreadableDatabaseIsLeftUntouchedByVersionDowngradeGuard() {
        databaseFile.writeText("not a sqlite database")
        val originalBytes = databaseFile.readBytes()

        try {
            assertNull(MemoryDbDowngradeGuard.archiveIfNewer(context, databaseName, MEMORY_DB_VERSION))
        } catch (_: IllegalStateException) {
            // SQLite may report an invalid database while reading its version; either way, keep it intact.
        }
        assertTrue(databaseFile.exists())
        assertTrue(originalBytes.contentEquals(databaseFile.readBytes()))
    }

    private fun createFutureDatabase(version: Int) {
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL("CREATE TABLE recovery_probe (value TEXT NOT NULL)")
            database.execSQL("INSERT INTO recovery_probe(value) VALUES ('keep me')")
            database.version = version
        }
    }

    private fun readVersion(file: File): Int =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { it.version }

    private fun readMarker(file: File): String =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT value FROM recovery_probe", null).use {
                check(it.moveToFirst())
                it.getString(0)
            }
        }

    private fun readMarkers(file: File): List<String> =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT value FROM recovery_probe", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
        }

    private fun copyArchiveForRestore(archive: File): File {
        val restored = File(databaseFile.parentFile, "restored_$databaseName")
        File(archive, databaseName).copyTo(restored, overwrite = true)
        listOf("wal", "shm").forEach { suffix ->
            val archivedSidecar = File(archive, "$databaseName-$suffix")
            if (archivedSidecar.exists()) {
                archivedSidecar.copyTo(File("${restored.absolutePath}-$suffix"), overwrite = true)
            }
        }
        return restored.also { archives += it }
    }
}
