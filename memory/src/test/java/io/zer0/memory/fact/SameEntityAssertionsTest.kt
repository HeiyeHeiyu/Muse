package io.zer0.memory.fact

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D6 第 2 期: [FactStore.findSameEntityMultiAssertionGroups] 测试。
 *
 * 覆盖:
 *  - 同实体 ≥3 条不同断言 → 返回该实体
 *  - 不足阈值 → 不返回;阈值可下调
 *  - 同实体重复断言去重后计数(不虚高)
 *  - 不同实体各自成组,不混组
 *  - entity_key 为空的历史数据不参与
 *  - 置顶事实不计入
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SameEntityAssertionsTest {

    private lateinit var db: FactDb
    private lateinit var store: FactStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, FactDb::class.java)
            .allowMainThreadQueries()
            .build()
        store = FactStore(db.factDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(prefix: String = "张三") {
        store.add(FactStore.Fact(fact = "${prefix}喜欢咖啡", entityKey = prefix))
        store.add(FactStore.Fact(fact = "${prefix}在学游泳", entityKey = prefix))
        store.add(FactStore.Fact(fact = "${prefix}喜欢跑步", entityKey = prefix))
    }

    @Test
    fun `entity with three distinct assertions is returned`() = runTest {
        seed()
        val groups = store.findSameEntityMultiAssertionGroups("main", "default", minAssertions = 3)

        assertEquals(1, groups.size)
        assertEquals("张三", groups[0].entityKey)
        assertEquals(3, groups[0].assertions.size)
    }

    @Test
    fun `entity below threshold is not returned but lower threshold finds it`() = runTest {
        store.add(FactStore.Fact(fact = "李四喜欢咖啡", entityKey = "李四"))
        store.add(FactStore.Fact(fact = "李四喜欢跑步", entityKey = "李四"))

        assertTrue("2 条 < 阈值 3,不应返回", store.findSameEntityMultiAssertionGroups("main", "default", 3).isEmpty())
        assertEquals("阈值降到 2 应命中", 1, store.findSameEntityMultiAssertionGroups("main", "default", 2).size)
    }

    @Test
    fun `duplicate assertions are deduplicated before counting`() = runTest {
        // 绕过写入去重直接插两行同实体同文本,模拟存量脏数据
        val now = java.time.Instant.now().toString()
        db.factDao().insert(FactEntity(fact = "张三喜欢咖啡", createdAt = now, entityKey = "张三"))
        db.factDao().insert(FactEntity(fact = "张三喜欢咖啡", createdAt = now, entityKey = "张三"))
        store.add(FactStore.Fact(fact = "张三喜欢跑步", entityKey = "张三"))

        // 去重后仅 2 条不同断言 < 阈值 3
        assertTrue(store.findSameEntityMultiAssertionGroups("main", "default", 3).isEmpty())
        val groups = store.findSameEntityMultiAssertionGroups("main", "default", 2)
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].assertions.size)
    }

    @Test
    fun `distinct entities form separate groups`() = runTest {
        seed("张三")
        seed("李四")

        val groups = store.findSameEntityMultiAssertionGroups("main", "default", 3)

        assertEquals(2, groups.size)
        assertEquals(setOf("张三", "李四"), groups.map { it.entityKey }.toSet())
    }

    @Test
    fun `rows without entity key are ignored`() = runTest {
        val now = java.time.Instant.now().toString()
        db.factDao().insert(FactEntity(fact = "喜欢咖啡", createdAt = now))
        db.factDao().insert(FactEntity(fact = "在学游泳", createdAt = now))
        db.factDao().insert(FactEntity(fact = "喜欢跑步", createdAt = now))

        assertTrue(store.findSameEntityMultiAssertionGroups("main", "default", 2).isEmpty())
    }

    @Test
    fun `pinned facts are excluded`() = runTest {
        seed("张三")
        val pinned = store.getByScopeAndSpace("main", "default").first()
        store.setPinned(pinned.id, true)

        // 置顶一条后,该实体只剩 2 条可建议断言 < 阈值 3
        assertTrue(store.findSameEntityMultiAssertionGroups("main", "default", 3).isEmpty())
    }
}
