package io.zer0.muse.data.session

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.zer0.common.AppJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Base64
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionRepositoryImageCleanupTest {
    private lateinit var context: Context
    private lateinit var database: MuseDb
    private lateinit var imageStore: MessageImageStore
    private lateinit var imageDirectory: File
    private lateinit var repository: SessionRepository
    private var previousFtsMode: Boolean = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        previousFtsMode = MessageFtsRuntime.useFts5
        MessageFtsRuntime.useFts5 = false
        database = Room.inMemoryDatabaseBuilder(context, MuseDb::class.java)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(MessageFtsDdl.createSql(useFts5 = false))
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.setForeignKeyConstraintsEnabled(true)
                    }
                },
            )
            .build()
        imageDirectory = File(context.cacheDir, "session-repository-images-${UUID.randomUUID()}")
        imageStore = MessageImageStore(imageDirectory)
        repository = SessionRepository(
            sessionDao = database.sessionDao(),
            messageDao = database.messageDao(),
            database = database,
            context = context,
            messageImageStore = imageStore,
        )
    }

    @After
    fun tearDown() {
        database.close()
        imageDirectory.deleteRecursively()
        MessageFtsRuntime.useFts5 = previousFtsMode
    }

    @Test
    fun hardDeletingSessionRemovesItsMessageImages() = runTest {
        val image = createImageBackedMessage()

        repository.deleteSession(sessionId)

        assertFalse(image.exists())
    }

    @Test
    fun softDeletingSessionRetainsImagesForRestore() = runTest {
        val image = createImageBackedMessage()

        repository.softDeleteSession(sessionId)

        assertTrue(image.exists())
    }

    @Test
    fun purgingExpiredSessionsRemovesTheirMessageImages() = runTest {
        val image = createImageBackedMessage()
        database.sessionDao().softDelete(sessionId, System.currentTimeMillis() - EXPIRED_AGE_MS)

        repository.purgeOldDeletedSessions()

        assertFalse(image.exists())
    }

    @Test
    fun hardDeletingAllSessionsRemovesAllMessageImages() = runTest {
        val first = createImageBackedMessage()
        val second = createImageBackedMessage(newSession = true)

        repository.hardDeleteAllSessions()

        assertFalse(first.exists())
        assertFalse(second.exists())
    }

    private suspend fun createImageBackedMessage(newSession: Boolean = false): File {
        val now = System.currentTimeMillis()
        val currentSessionId = if (newSession) UUID.randomUUID().toString() else sessionId
        database.sessionDao().insert(
            SessionEntity(
                id = currentSessionId,
                title = "image cleanup",
                createdAt = now,
                updatedAt = now,
            ),
        )
        val messageId = UUID.randomUUID().toString()
        val base64 = Base64.getEncoder().encodeToString(ByteArray(2048) { (it % 255).toByte() })
        val imageReference = imageStore.toPersistable(messageId, listOf(base64)).single()
        val image = File(imageReference.removePrefix("file://"))
        database.messageDao().upsert(
            MessageEntity(
                id = messageId,
                sessionId = currentSessionId,
                role = "USER",
                content = "image",
                createdAt = now,
                imageBase64Json = AppJson.encodeToString(
                    ListSerializer(String.serializer()),
                    listOf(imageReference),
                ),
            ),
        )
        assertTrue(image.exists())
        return image
    }

    private val sessionId = UUID.randomUUID().toString()

    private companion object {
        const val EXPIRED_AGE_MS = 8L * 24 * 60 * 60 * 1000
    }
}
