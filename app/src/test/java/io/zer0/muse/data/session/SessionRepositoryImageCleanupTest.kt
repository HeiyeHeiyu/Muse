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
import org.junit.Assert.assertEquals
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
    fun detectsCheckpointForOutboxUserMessageToPreventDuplicateRecovery() = runTest {
        val now = System.currentTimeMillis()
        val userMessageId = UUID.randomUUID().toString()
        database.sessionDao().insert(SessionEntity(id = sessionId, title = "checkpoint lookup", createdAt = now, updatedAt = now))
        database.generationCheckpointDao().upsert(
            GenerationCheckpointEntity(
                assistantMessageId = UUID.randomUUID().toString(),
                sessionId = sessionId,
                userMessageId = userMessageId,
                content = "partial",
                createdAt = now,
                updatedAt = now,
            ),
        )

        assertTrue(repository.hasGenerationCheckpointForUserMessage(userMessageId))
        assertFalse(repository.hasGenerationCheckpointForUserMessage(UUID.randomUUID().toString()))
    }

    @Test
    fun checkpointAndOutboxConsumptionCommitAtomically() = runTest {
        val now = System.currentTimeMillis()
        val assistantMessageId = UUID.randomUUID().toString()
        val userMessageId = UUID.randomUUID().toString()
        val outboxId = UUID.randomUUID().toString()
        database.sessionDao().insert(SessionEntity(id = sessionId, title = "checkpoint", createdAt = now, updatedAt = now))
        repository.insertOutbox(
            MessageOutboxEntity(
                id = outboxId,
                sessionId = sessionId,
                text = "hello",
                userMessageId = userMessageId,
                assistantMessageId = assistantMessageId,
                createdAt = now,
            ),
        )

        repository.upsertGenerationCheckpointAndDeleteOutbox(
            sessionId = sessionId,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            content = "partial",
            createdAt = now,
            outboxId = outboxId,
        )

        assertTrue(repository.getPendingOutbox(sessionId).isEmpty())
        val checkpoint = database.generationCheckpointDao().getAllPending().single()
        assertEquals(assistantMessageId, checkpoint.assistantMessageId)
        assertEquals("partial", checkpoint.content)
    }

    @Test
    fun checkpointFailureRollsBackOutboxDeletion() = runTest {
        val now = System.currentTimeMillis()
        val outboxId = UUID.randomUUID().toString()
        val userMessageId = UUID.randomUUID().toString()
        val assistantMessageId = UUID.randomUUID().toString()
        database.sessionDao().insert(SessionEntity(id = sessionId, title = "checkpoint rollback", createdAt = now, updatedAt = now))
        repository.insertOutbox(
            MessageOutboxEntity(
                id = outboxId,
                sessionId = sessionId,
                text = "hello",
                userMessageId = userMessageId,
                assistantMessageId = assistantMessageId,
                createdAt = now,
            ),
        )

        val result = runCatching {
            repository.upsertGenerationCheckpointAndDeleteOutbox(
                sessionId = "missing-session",
                userMessageId = userMessageId,
                assistantMessageId = assistantMessageId,
                content = "partial",
                createdAt = now,
                outboxId = outboxId,
            )
        }

        assertTrue(result.isFailure)
        assertEquals(1, repository.getPendingOutbox(sessionId).size)
        assertTrue(database.generationCheckpointDao().getAllPending().isEmpty())
    }

    @Test
    fun retryingAppendWithSameMessageIdDoesNotInflateSessionMessageCount() = runTest {
        val now = System.currentTimeMillis()
        database.sessionDao().insert(SessionEntity(id = sessionId, title = "idempotent append", createdAt = now, updatedAt = now))
        val message = io.zer0.ai.core.UIMessage(
            role = io.zer0.ai.core.MessageRole.USER,
            content = "retry me",
            createdAt = now,
        )

        repository.appendMessage(sessionId, message)
        val afterFirst = database.sessionDao().getById(sessionId)!!
        repository.appendMessage(sessionId, message)
        val afterRetry = database.sessionDao().getById(sessionId)!!

        assertEquals(1, afterRetry.messageCount)
        assertEquals(afterFirst.lastMessagePreview, afterRetry.lastMessagePreview)
        assertEquals(afterFirst.updatedAt, afterRetry.updatedAt)
        assertEquals(1, database.messageDao().countBySession(sessionId))
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
