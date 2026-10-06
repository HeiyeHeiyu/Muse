package io.zer0.muse.tools.system

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ShellServiceOutputTest {

    @Test
    fun `shell output stream keeps data beyond the old binder response cap`() {
        val output = ByteArray(2 * 1024 * 1024 + 37) { (it % 251).toByte() }
        val copied = ByteArrayOutputStream()

        copyShellOutput(ByteArrayInputStream(output), copied)

        assertArrayEquals(output, copied.toByteArray())
    }
}
