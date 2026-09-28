package io.zer0.muse.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.3.1: 会话切换序号守卫单测(纯逻辑,无 Android 依赖)。
 *
 * 守卫是模块级单例,序号只增不减且不重置,因此断言只依赖「同一用例内 [SessionSwitchGuard.begin]
 * 的相对先后」,不假设初始值 —— 避免用例执行顺序影响结果。
 */
class SessionSwitchGuardTest {
    @Test
    fun `latest token is never stale`() {
        val token = SessionSwitchGuard.begin()
        assertFalse(SessionSwitchGuard.isStale(token))
    }

    @Test
    fun `begin returns increasing tokens`() {
        val first = SessionSwitchGuard.begin()
        val second = SessionSwitchGuard.begin()
        assertTrue(second > first)
    }

    @Test
    fun `begin issues distinct tokens`() {
        val first = SessionSwitchGuard.begin()
        val second = SessionSwitchGuard.begin()
        assertTrue(first != second)
    }

    @Test
    fun `superseded token becomes stale`() {
        val old = SessionSwitchGuard.begin()
        SessionSwitchGuard.begin()
        assertTrue(SessionSwitchGuard.isStale(old))
    }

    /** 不变量:快速连续切换后,只有最后一次发起的切换可提交,更早的一律过期。 */
    @Test
    fun `rapid switching keeps only the last committable`() {
        val first = SessionSwitchGuard.begin()
        val second = SessionSwitchGuard.begin()
        val third = SessionSwitchGuard.begin()
        assertTrue(SessionSwitchGuard.isStale(first))
        assertTrue(SessionSwitchGuard.isStale(second))
        assertFalse(SessionSwitchGuard.isStale(third))
    }
}
