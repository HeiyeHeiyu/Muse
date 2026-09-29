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
 * v2.2.1: 无障碍双路(独立 Provider / 应用内服务)设备面实证。
 *
 * 前置(由外部脚本准备,缺失时 assume 跳过,不判失败):
 *  1. Provider 用例: 已安装 accessibility-provider-debug.apk(同 debug 签名)且其无障碍服务已启用;
 *  2. 应用内用例: 主应用无障碍服务已启用,且 Provider 服务未启用(否则路由优先走 Provider)。
 *
 * 验证链路: bindService/静态实例 → UI 操作接口 → 跨进程/同进程读取 UI 层级与当前 Activity。
 */
@RunWith(AndroidJUnit4::class)
class A11yProviderInstrumentedTest {
    @Test
    fun provider_bridge_serves_ui_operations() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = AccessibilityClient(context)

        assumeTrue("独立 Provider APK 未安装,跳过", client.isProviderInstalled())
        assumeTrue(
            "独立 Provider 无障碍服务未在系统设置启用,跳过",
            isServiceEnabled(context, AccessibilityClient.PROVIDER_PACKAGE),
        )

        // ColorOS 等定制 ROM 的后台限制(WIU 逻辑)会拒绝后台应用跨应用绑定。
        // 测试前把自身活动拉到前台,模拟真实使用场景(用户打开应用时绑定)。
        bringSelfToFront(context)

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

        assertReadable(context, client)
    }

    @Test
    fun in_app_service_serves_ui_operations_when_provider_off() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = AccessibilityClient(context)

        assumeTrue(
            "应用内无障碍服务未在系统设置启用,跳过",
            isServiceEnabled(context, context.packageName),
        )
        assumeTrue(
            "独立 Provider 仍被启用(路由优先于应用内服务),跳过",
            !isServiceEnabled(context, AccessibilityClient.PROVIDER_PACKAGE),
        )

        bringSelfToFront(context)

        var connected = false
        for (attempt in 0 until 40) {
            if (client.isConnected()) {
                connected = true
                break
            }
            Thread.sleep(250)
        }
        // instrument 启动会 force-stop 应用,部分 ROM 不会把应用内无障碍服务重绑进测试进程;
        // 该场景跳过而非判失败(跨进程 Provider 路径另有 provider_bridge 用例硬覆盖)。
        assumeTrue("应用内无障碍服务未在测试进程内就绪(force-stop 重绑限制),跳过", connected)

        assertReadable(context, client)
    }

    /** 读屏可读性断言: 当前 Activity 组件名 + 非空 UI 层级(带重试,窗口状态可能滞后)。 */
    private suspend fun assertReadable(context: Context, client: AccessibilityClient) {
        var activity = ""
        for (attempt in 0 until 6) {
            activity = client.currentActivityName()
            if (activity.contains('/')) break
            Thread.sleep(500)
        }
        assertTrue("currentActivityName 应返回组件名,实际: $activity", activity.contains('/'))

        var page = ""
        for (attempt in 0 until 8) {
            page = client.getPageInfo()
            if (page.contains("[activity]")) break
            Thread.sleep(500)
        }
        assertTrue("getPageInfo 应返回非空层级文本,实际: $page", page.isNotBlank())
        assertTrue("getPageInfo 应包含 [activity] 头,实际: $page", page.contains("[activity]"))
    }

    private fun bringSelfToFront(context: Context) {
        runCatching {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        }
        Thread.sleep(2000)
    }

    /** 通过 Secure 设置解析指定包的无障碍服务是否启用(不依赖内部实现)。 */
    private fun isServiceEnabled(context: Context, packageName: String): Boolean {
        val enabled =
            runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                )
            }.getOrNull().orEmpty()
        return enabled.split(':').any { entry ->
            entry.trim().startsWith("$packageName/")
        }
    }
}
