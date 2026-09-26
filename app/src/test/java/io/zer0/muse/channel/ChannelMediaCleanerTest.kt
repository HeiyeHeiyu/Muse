package io.zer0.muse.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.x 遗留收尾:channel_media 清理策略纯函数单测(不触碰文件系统)。
 */
class ChannelMediaCleanerTest {

    private val now = 1_000_000_000_000L

    private fun meta(name: String, ageMs: Long, size: Long) =
        ChannelMediaCleaner.FileMeta(path = name, lastModified = now - ageMs, size = size)

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun emptyInputDeletesNothing() {
        assertEquals(
            emptyList<String>(),
            ChannelMediaCleaner.selectForDeletion(emptyList(), now),
        )
    }

    @Test
    fun deletesOnlyFilesOlderThanTtl() {
        val files = listOf(
            meta("fresh.bin", ageMs = 1 * day, size = 10),
            meta("edge.bin", ageMs = 30 * day, size = 10), // 恰好等于 TTL,不删(严格大于才删)
            meta("stale.bin", ageMs = 31 * day, size = 10),
        )

        val deleted = ChannelMediaCleaner.selectForDeletion(
            files,
            now = now,
            ttlMs = 30 * day,
            maxTotalBytes = Long.MAX_VALUE,
        )

        assertEquals(listOf("stale.bin"), deleted)
    }

    @Test
    fun sizeCapEvictsLeastRecentlyModifiedFirst() {
        // 无过期文件,总大小 300 > 上限 250 → 淘汰最旧的直到 ≤ 250
        val files = listOf(
            meta("a_old.bin", ageMs = 5 * day, size = 100),
            meta("b_mid.bin", ageMs = 3 * day, size = 100),
            meta("c_new.bin", ageMs = 1 * day, size = 100),
        )

        val deleted = ChannelMediaCleaner.selectForDeletion(
            files,
            now = now,
            ttlMs = 30 * day,
            maxTotalBytes = 250,
        )

        assertEquals(listOf("a_old.bin"), deleted)
    }

    @Test
    fun sizeCapEvictsMultipleOldestUntilUnderLimit() {
        // 总 500 > 上限 150 → 删两个最旧的(100+100),剩 300 仍 >150 → 再删一个 → 剩 200 仍>150
        // 继续删 → 剩 100 ≤150 停。实际最旧优先逐个删。
        val files = listOf(
            meta("f1.bin", ageMs = 5 * day, size = 100),
            meta("f2.bin", ageMs = 4 * day, size = 100),
            meta("f3.bin", ageMs = 3 * day, size = 100),
            meta("f4.bin", ageMs = 2 * day, size = 100),
            meta("f5.bin", ageMs = 1 * day, size = 100),
        )

        val deleted = ChannelMediaCleaner.selectForDeletion(
            files,
            now = now,
            ttlMs = 30 * day,
            maxTotalBytes = 150,
        )

        // 从最旧删起:删 f1..f4 后剩 100 ≤ 150 停 → 保留 f5
        assertEquals(listOf("f1.bin", "f2.bin", "f3.bin", "f4.bin"), deleted)
    }

    @Test
    fun ttlAndSizeCapCombine() {
        // stale 先被 TTL 删;剩余总 300 > 上限 250 → 再淘汰最旧的 fresh_old
        val files = listOf(
            meta("stale.bin", ageMs = 40 * day, size = 999),
            meta("fresh_old.bin", ageMs = 5 * day, size = 150),
            meta("fresh_mid.bin", ageMs = 3 * day, size = 100),
            meta("fresh_new.bin", ageMs = 1 * day, size = 50),
        )

        val deleted = ChannelMediaCleaner.selectForDeletion(
            files,
            now = now,
            ttlMs = 30 * day,
            maxTotalBytes = 250,
        )

        assertEquals(listOf("stale.bin", "fresh_old.bin"), deleted)
    }

    @Test
    fun keepsEverythingWhenUnderBothLimits() {
        val files = listOf(
            meta("x.bin", ageMs = 1 * day, size = 10),
            meta("y.bin", ageMs = 2 * day, size = 20),
        )

        val deleted = ChannelMediaCleaner.selectForDeletion(
            files,
            now = now,
            ttlMs = 30 * day,
            maxTotalBytes = 1024,
        )

        assertTrue(deleted.isEmpty())
    }
}
