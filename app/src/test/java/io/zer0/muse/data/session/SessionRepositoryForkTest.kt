package io.zer0.muse.data.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.zer0.ai.core.MessageRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionRepositoryForkTest {

    private lateinit var context: Context
    private lateinit var database: MuseDb
    private lateinit var imageStore: MessageImageStore
    private lateinit var imageDir: File
    private var previousFts5 = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        imageDir = Files.createTempDirectory("session-fork-images").toFile()
        database = Room.databaseBuilder(context, MuseDb::class.java, File(imageDir, "muse.db").absolutePath)
            .allowMainThreadQueries()
            .build()
        imageStore = MessageImageStore(imageDir)
        previousFts5 = MessageFtsRuntime.useFts5
    }

    @After
    fun tearDown() {
        database.close()
        imageDir.deleteRecursively()
        MessageFtsRuntime.useFts5 = previousFts5
    }

    @Test
    fun forkStopsAtAnchorWhenLaterMessagesShareItsTimestamp() = runBlocking {
        val source = SessionEntity(
            id = "source-session",
            title = "source",
            createdAt = 1_000L,
            updatedAt = 1_000L,
        )
        database.sessionDao().insert(source)
        database.messageDao().upsertAll(
            listOf(
                message(id = "u1", role = MessageRole.USER.name, content = "first", seq = 1, commitSeq = 1),
                message(id = "a1", role = MessageRole.ASSISTANT.name, content = "anchor", seq = 2, commitSeq = 2),
                message(id = "u2", role = MessageRole.USER.name, content = "later user", seq = 3, commitSeq = 3),
                message(id = "a2", role = MessageRole.ASSISTANT.name, content = "later answer", seq = 4, commitSeq = 4),
            ),
        )
        val repository = SessionRepository(
            sessionDao = database.sessionDao(),
            messageDao = database.messageDao(),
            database = database,
            context = context,
            messageImageStore = imageStore,
        )
        // Robolectric's FTS4 fallback can abort the surrounding transaction; this regression
        // isolates the fork's primary-data boundary and leaves derived-index coverage separate.
        MessageFtsRuntime.useFts5 = true

        val forkedId = repository.forkSession(source.id, "a1")
        assertNotNull(forkedId)
        val copied = database.messageDao().observeBySession(forkedId!!).first()

        assertEquals(listOf("first", "anchor"), copied.map { it.content })
    }

    private fun message(
        id: String,
        role: String,
        content: String,
        seq: Long,
        commitSeq: Long,
    ) = MessageEntity(
        id = id,
        sessionId = "source-session",
        role = role,
        content = content,
        createdAt = 1_000L,
        seq = seq,
        commitSeq = commitSeq,
    )
}
