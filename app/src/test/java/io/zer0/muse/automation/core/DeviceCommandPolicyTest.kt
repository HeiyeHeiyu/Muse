package io.zer0.muse.automation.core

import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.x 自动化一期:device_shell 命令白名单策略。 */
class DeviceCommandPolicyTest {

    private fun valid(cmd: String) = DeviceCommandPolicy.validate(cmd) is DeviceCommandPolicy.Check.Valid

    private fun invalid(cmd: String) = DeviceCommandPolicy.validate(cmd) is DeviceCommandPolicy.Check.Invalid

    @Test
    fun `allowed device commands pass`() {
        assertTrue(valid("input tap 540 1200"))
        assertTrue(valid("input swipe 540 1500 540 500 300"))
        assertTrue(valid("input text hello"))
        assertTrue(valid("input keyevent 3"))
        assertTrue(valid("am start -n com.tencent.mm/.ui.LauncherUI"))
        assertTrue(valid("settings get secure location_mode"))
        assertTrue(valid("uiautomator dump"))
        assertTrue(valid("screencap -p"))
        assertTrue(valid("pm list packages"))
        assertTrue(valid("svc wifi enable"))
        assertTrue(valid("dumpsys window"))
        assertTrue(valid("getprop ro.build.version.sdk"))
        assertTrue(valid("content query --uri content://settings/secure"))
    }

    @Test
    fun `forbidden metacharacters are rejected`() {
        for (bad in listOf(
            "input tap 1 1; rm -rf /",
            "input tap 1 1 && reboot",
            "echo hi | sh",
            "input tap 1 1 > /sdcard/x",
            "`reboot`",
            "input text \$(reboot)",
        )) {
            assertTrue("应拒绝: $bad", invalid(bad))
        }
    }

    @Test
    fun `unknown verbs and subverbs are rejected`() {
        assertTrue(invalid("rm -rf /"))
        assertTrue(invalid("reboot"))
        assertTrue(invalid("input scream 1 2"))
        assertTrue(invalid("am destroy com.x"))
        assertTrue(invalid(""))
    }
}
