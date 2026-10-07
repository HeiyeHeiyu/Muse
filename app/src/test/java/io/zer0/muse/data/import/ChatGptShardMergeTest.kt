@file:Suppress("PackageName")

package io.zer0.muse.data.`import`

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v2.4.6: ChatGPT 新版分层导出的分片合并测试。
 *
 * 新版导出把会话拆成 conversations-000.json / conversations-001.json ...
 * （顶层各为 JSON 数组），旧识别只认 conversations.json，导致整包导入 0 条。
 * 本测试守护分片合并逻辑：合并结果必须是单个合法顶层数组，且元素完整保序。
 */
class ChatGptShardMergeTest {

    private fun shard(name: String, content: String): File {
        val f = File.createTempFile("shard_", "_$name")
        f.writeText(content)
        f.deleteOnExit()
        return f
    }

    @Test
    fun `merges multiple shards into one top level array`() {
        val a = shard("a", """[{"id":"c1","title":"one"},{"id":"c2","title":"two"}]""")
        val b = shard("b", """[{"id":"c3","title":"three"}]""")

        val merged = ThirdPartyImporter.mergeConversationShards(listOf(a, b))

        assertEquals("""[{"id":"c1","title":"one"},{"id":"c2","title":"two"},{"id":"c3","title":"three"}]""", merged)
    }

    @Test
    fun `handles empty and whitespace shards`() {
        val a = shard("a", "[]")
        val b = shard("b", "   ")
        val c = shard("c", """[{"id":"x"}]""")

        val merged = ThirdPartyImporter.mergeConversationShards(listOf(a, b, c))

        assertEquals("""[{"id":"x"}]""", merged)
    }

    @Test
    fun `strips BOM and surrounding whitespace`() {
        val a = shard("a", "\uFEFF[{\"id\":\"a\"}]\n")
        val b = shard("b", "\n [{\"id\":\"b\"}] ")

        val merged = ThirdPartyImporter.mergeConversationShards(listOf(a, b))

        assertEquals("""[{"id":"a"},{"id":"b"}]""", merged)
        // 必须是单个合法顶层数组
        assertTrue(merged.startsWith("["))
        assertTrue(merged.endsWith("]"))
    }
}
