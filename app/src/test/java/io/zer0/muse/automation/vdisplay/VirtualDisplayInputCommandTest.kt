package io.zer0.muse.automation.vdisplay

import io.zer0.muse.automation.core.DeviceCommandPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualDisplayInputCommandTest {
    @Test
    fun `tap command is display scoped and policy valid`() {
        val command = VirtualDisplayInputCommand.build(
            displayId = 42,
            action = "tap",
            args = mapOf("x" to "120", "y" to "640"),
        )

        assertEquals("input -d 42 tap 120 640", command?.shellCommand)
        assertTrue(command?.let { DeviceCommandPolicy.validate(it.shellCommand) } is DeviceCommandPolicy.Check.Valid)
    }

    @Test
    fun `swipe clamps duration to a bounded window`() {
        val command = VirtualDisplayInputCommand.build(
            displayId = 7,
            action = "swipe",
            args = mapOf(
                "x1" to "1",
                "y1" to "2",
                "x2" to "3",
                "y2" to "4",
                "duration_ms" to "99999",
            ),
        )

        assertEquals("input -d 7 swipe 1 2 3 4 5000", command?.shellCommand)
    }

    @Test
    fun `text encodes spaces and rejects shell metacharacters`() {
        val safe = VirtualDisplayInputCommand.build(
            displayId = 3,
            action = "text",
            args = mapOf("text" to "hello world"),
        )
        val unsafe = VirtualDisplayInputCommand.build(
            displayId = 3,
            action = "text",
            args = mapOf("text" to "hello; reboot"),
        )

        assertEquals("input -d 3 text hello%sworld", safe?.shellCommand)
        assertFalse(DeviceCommandPolicy.validate(safe!!.shellCommand) !is DeviceCommandPolicy.Check.Valid)
        assertEquals(null, unsafe)
    }

    @Test
    fun `wait returns a delay without constructing a shell command`() {
        val command = VirtualDisplayInputCommand.build(
            displayId = 3,
            action = "wait",
            args = mapOf("ms" to "99999"),
        )

        assertEquals(10_000L, command?.waitMs)
        assertEquals("", command?.shellCommand)
    }
}
