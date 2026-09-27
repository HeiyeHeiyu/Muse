package io.zer0.muse.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.x: 工具权限分层 — "任一就绪"语义与权限表覆盖测试。 */
class ToolPermissionStatusTest {
    @Test
    fun `empty requirement needs no authorization`() {
        val status = ToolPermissionStatus(accessibility = false, shellTier = false, termux = false)
        assertTrue(status.satisfiedAny(emptySet()))
    }

    @Test
    fun `any satisfied permission passes`() {
        val status = ToolPermissionStatus(accessibility = false, shellTier = true, termux = false)
        assertTrue(status.satisfiedAny(setOf(ToolPermission.ACCESSIBILITY, ToolPermission.SHELL_TIER)))
    }

    @Test
    fun `all unsatisfied blocks`() {
        val status = ToolPermissionStatus(accessibility = false, shellTier = false, termux = false)
        assertFalse(status.satisfiedAny(setOf(ToolPermission.SHELL_TIER)))
        assertFalse(status.satisfiedAny(setOf(ToolPermission.ACCESSIBILITY, ToolPermission.TERMUX)))
    }

    @Test
    fun `permission map covers known gated tools`() {
        assertTrue(ToolCategories.permissionOf("device_shell").contains(ToolPermission.SHELL_TIER))
        assertTrue(ToolCategories.permissionOf("ui_click").contains(ToolPermission.ACCESSIBILITY))
        assertTrue(ToolCategories.permissionOf("termux_exec").contains(ToolPermission.TERMUX))
        assertTrue(ToolCategories.permissionOf("screen_pinch") == setOf(ToolPermission.ACCESSIBILITY))
        assertTrue(ToolCategories.permissionOf("get_current_time").isEmpty())
    }
}
