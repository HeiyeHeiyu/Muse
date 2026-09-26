package io.zer0.muse.data.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 临时探针:观察 FTS 引擎对中文/英文/符号查询的真实命中行为。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionRepositorySearchTest {

    private lateinit var db: MuseDb
    private lateinit var repo: SessionRepository

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MuseDb::class.java)
            .allowMainThreadQueries()
            .build()
        repo = SessionRepository(
            sessionDao = db.sessionDao(),
            messageDao = db.messageDao(),
            database = db,
            context = context,
            messageImageStore = MessageImageStore(File(context.cacheDir, "msg_imgs_search_test")),
        )
        db.sessionDao().insert(SessionEntity("s1", "会话标题", 1L, 1L))
        db.messageDao().upsertAll(
            listOf(
                MessageEntity(id = "m1", sessionId = "s1", role = "USER", content = "今天天气很好，我们出发吧。", createdAt = 1000),
                MessageEntity(id = "m2", sessionId = "s1", role = "ASSISTANT", content = "连续的CJK游程测试：你好世界", createdAt = 2000),
                MessageEntity(id = "m3", sessionId = "s1", role = "USER", content = "!!! important note", createdAt = 3000),
                MessageEntity(id = "m4", sessionId = "s1", role = "USER", content = "apple banana", createdAt = 4000),
            ),
        )
        repo.rebuildFtsIndex()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun probe() = runBlocking {
        val out = StringBuilder()
        out.appendLine("PROBE useFts5=${MessageFtsRuntime.useFts5}")
        for (q in listOf("天气", "天气很好", "你好世界", "好", "!!!", "important", "apple", "app", "世")) {
            val results = repo.searchMessages(q)
            val ids = results.map { it.messageId }
            val snippet = results.firstOrNull()?.contentSnippet?.replace("\n", "\\n")
            out.appendLine("PROBE query=[$q] ids=$ids firstSnippet=[$snippet]")
            println("PROBE query=[$q] ids=$ids firstSnippet=[$snippet]")
        }
        File("E:/1Project/Muse/1muse/probe_search.txt").writeText(out.toString())
    }
}
