package io.zer0.muse.backup

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import io.zer0.muse.data.session.GenerationCheckpointEntity
import io.zer0.muse.data.session.MuseDb
import io.zer0.muse.data.session.SessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupRestoreDatabaseCleanupTest {
    private lateinit var database: MuseDb

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MuseDb::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun restoreCleanupExplicitlyRemovesCheckpointsAndSessionRows() = runTest {
        val sessionId = UUID.randomUUID().toString()
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.sessionDao().insert(
            SessionEntity(
                id = sessionId,
                title = "restore cleanup",
                createdAt = now,
                updatedAt = now,
            ),
        )
        database.generationCheckpointDao().upsert(
            GenerationCheckpointEntity(
                assistantMessageId = UUID.randomUUID().toString(),
                sessionId = sessionId,
                userMessageId = UUID.randomUUID().toString(),
                content = "stale partial response",
                createdAt = now,
                updatedAt = now,
            ),
        )
        database.messageDao().upsert(
            io.zer0.muse.data.session.MessageEntity(
                id = messageId,
                sessionId = sessionId,
                role = "USER",
                content = "old conversation",
                createdAt = now,
            ),
        )
        assertEquals(1, database.generationCheckpointDao().getAllPending().size)

        database.withTransaction {
            clearConversationStateForRestore(database)
        }

        assertTrue(database.generationCheckpointDao().getAllPending().isEmpty())
        assertEquals(0, database.sessionDao().count())
        assertEquals(0, database.messageDao().countMessages())
    }
}
