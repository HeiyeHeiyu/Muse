package io.zer0.muse.a11y

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.zer0.muse.accessibility.IAccessibilityProvider
import io.zer0.muse.accessibility.MuseAccessibilityService

/**
 * v2.2.1: AIDL 桥接服务。
 *
 * 把同进程的 [MuseAccessibilityService.instance](无障碍服务实例)通过
 * [IAccessibilityProvider] 暴露给同签名的 Muse 主应用:
 *  - manifest 以 signature 级权限 [io.zer0.muse.permission.A11Y_BRIDGE] 护栏;
 *  - 无障碍服务未连接时,查询类返回空值/未启用,操作类返回 false(与主应用内置实现同语义)。
 *
 * 方法映射说明:AIDL 的 performGlobalAction 对应基类 execGlobalAction
 * (基类注释:AccessibilityService.performGlobalAction 为 final,不可覆写)。
 */
class A11yBridgeService : Service() {
    private val binder =
        object : IAccessibilityProvider.Stub() {
            override fun getUiHierarchy(): String = service()?.getUiHierarchy() ?: ""

            override fun performClick(x: Int, y: Int): Boolean = service()?.performClick(x, y) ?: false

            override fun performLongPress(x: Int, y: Int): Boolean = service()?.performLongPress(x, y) ?: false

            override fun performGlobalAction(actionId: Int): Boolean = service()?.execGlobalAction(actionId) ?: false

            override fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long): Boolean =
                service()?.performSwipe(startX, startY, endX, endY, duration) ?: false

            override fun findFocusedNodeId(): String = service()?.findFocusedNodeId() ?: ""

            override fun setTextOnNode(nodeId: String, text: String): Boolean = service()?.setTextOnNode(nodeId, text) ?: false

            override fun takeScreenshot(path: String, format: String): Boolean = service()?.takeScreenshot(path, format) ?: false

            override fun isAccessibilityServiceEnabled(): Boolean = service() != null

            override fun getCurrentActivityName(): String = service()?.getCurrentActivityName() ?: ""
        }

    private fun service(): MuseAccessibilityService? = MuseAccessibilityService.instance

    override fun onBind(intent: Intent?): IBinder = binder
}
