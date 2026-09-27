package io.zer0.muse.tools.script

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.2.1: 大沙盒文件动作的路径安全与基本读写。 */
class SkillBridgeFsTest {

    private fun newRoot(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "skillfs-" + System.nanoTime())
        dir.mkdirs()
        return dir
    }

    @Test
    fun `write read list delete roundtrip`() {
        val root = newRoot()
        val bytes = SkillBridgeFs.write(root, "notes/a.txt", "hello 沙盒")
        assertTrue(bytes > 0)
        assertEquals("hello 沙盒", SkillBridgeFs.read(root, "notes/a.txt"))
        val entries = SkillBridgeFs.list(root, "notes")
        assertEquals(1, entries.size)
        assertEquals("a.txt", entries[0].name)
        assertFalse(entries[0].dir)
        assertTrue(SkillBridgeFs.delete(root, "notes/a.txt"))
        assertTrue(SkillBridgeFs.delete(root, "notes"))
    }

    @Test
    fun `traversal and absolute paths are rejected`() {
        val root = newRoot()
        for (bad in listOf("../escape.txt", "a/../../b.txt", "/etc/passwd", "..")) {
            var rejected = false
            try {
                SkillBridgeFs.write(root, bad, "x")
            } catch (e: SkillBridgeFs.BridgeFsException) {
                rejected = true
            }
            assertTrue("应拒绝路径: $bad", rejected)
        }
    }

    @Test
    fun `delete refuses sandbox root and oversized write is rejected`() {
        val root = newRoot()
        var rootRejected = false
        try {
            SkillBridgeFs.delete(root, "")
        } catch (e: SkillBridgeFs.BridgeFsException) {
            rootRejected = true
        }
        assertTrue(rootRejected)

        var overRejected = false
        try {
            SkillBridgeFs.write(root, "big.txt", "x".repeat(SkillBridgeFs.MAX_WRITE_BYTES + 1))
        } catch (e: SkillBridgeFs.BridgeFsException) {
            overRejected = true
        }
        assertTrue(overRejected)
    }
}
