package io.zer0.muse.a11y

import android.content.Context
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.zer0.muse.tools.system.AccessibilityClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v2.2.1: 无障碍独立 Provider 桥接设备面实证。
 *
 * 前置(由外部脚本准备,缺失时 assume 跳过,不判失败):
 *  1. 已安装 accessibility-provider-debug.apk(与主应用同 debug 签名);
 *  2. 已在系统设置启用其无障碍服务(可用 adb settings put secure 写入)。
 *
 * 验证链路: bindService(签名级权限 A11Y_BRIDGE) → AIDL 代理 →
 * 跨进程读取 UI 层级 / 当前 Activity(证明主应用 ↔ Provider 双进程通道可用)。
 */
@RunWith(AndroidJUnit4::class)
class A11yProviderInstrumentedTest {
    @Test
    fun provider_bridge_serves_ui_operations() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val client = AccessibilityClient(context)

            assumeTrue("独立 Provider APK 未安装,跳过", client.isProviderInstalled())
            assumeTrue("独立 Provider 无障碍服务未在系统设置启用,跳过", isProviderServiceEnabled(context))

            // 绑定是异步的: 轮询等待桥接就绪(binder 到达后 isConnected 转 true)
            var connected = false
            for (attempt in 0 until 24) {
                if (client.isConnected()) {
                    connected = true
                    break
                }
                Thread.sleep(250)
            }
            assertTrue("Provider 桥接未在 6s 内就绪", connected)

            // 跨进程 AIDL: 读取 UI 层级 + 当前 Activity
            val page = client.getPageInfo()
            assertTrue("getPageInfo 应返回非空层级文本,实际: $page", page.isNotBlank())
            assertTrue("getPageInfo 应包含 [activity] 头,实际: $page", page.contains("[activity]"))

            val activity = client.currentActivityName()
            assertTrue("currentActivityName 应返回组件名,实际: $activity", activity.contains('/'))
        }

    /** 通过 Secure 设置解析独立 Provider 的无障碍服务是否启用(不依赖内部实现)。 */
    private fun isProviderServiceEnabled(context: Context): Boolean {
        val enabled =
            runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                )
            }.getOrNull().orEmpty()
        return enabled.split(':').any { entry ->
            entry.trim().startsWith("${AccessibilityClient.PROVIDER_PACKAGE}/")
        }
    }
}
