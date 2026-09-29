package io.zer0.muse.automation.vdisplay

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.zer0.muse.vdproto.IVirtualDisplayService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * v2.2.1 虚拟屏:设备面 e2e(shell 服务端 + 广播手递手 + 建屏 + 屏内启动 + 截图)。
 *
 * 前置(由外部脚本准备,缺失时 assume 跳过,不判失败):
 *  1. 服务端 jar 已部署到 /data/local/tmp/muse-vd-server.jar 且以 shell uid 启动;
 *  2. binder 已通过广播手递手到达本应用(注册表非空)。
 *
 * 该用例不依赖 Shizuku(启动部分由 adb 以同等命令代替,验证服务端/契约/截图全链)。
 */
@RunWith(AndroidJUnit4::class)
class VirtualDisplayInstrumentedTest {
    @Test
    fun server_handoff_display_and_screenshot_work() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // 服务端每 5s 重发布一次 binder;instrument 重启应用后注册表为空,
        // 这里轮询等待下一轮广播到达(最多 10s)。
        var binder = VirtualDisplayBinderRegistry.current()
        for (attempt in 0 until 40) {
            if (binder != null) break
            Thread.sleep(250)
            binder = VirtualDisplayBinderRegistry.current()
        }
        assumeTrue("虚拟屏服务端 binder 未在 10s 内就位(前置未启动),跳过", binder != null)

        val proxy = IVirtualDisplayService.Stub.asInterface(binder)
        assertTrue("服务端存活探测失败(isAlive=false)", proxy.isAlive)

        val displayId = proxy.ensureDisplay(720, 1280, 320)
        assertTrue("ensureDisplay 应返回有效 displayId,实际 $displayId", displayId >= 0)

        // 屏内启动设置应用,制造真实渲染内容
        val launched = proxy.launchApp("com.android.settings", displayId)
        Log.i(TAG, "VD-E2E displayId=$displayId launched=$launched")

        // 首帧可能滞后:重试等待
        var bytes: ByteArray? = null
        for (attempt in 0 until 12) {
            Thread.sleep(500)
            bytes = proxy.requestScreenshot(displayId)
            if (bytes != null && bytes.isNotEmpty()) break
        }
        assertNotNull("requestScreenshot 应返回图像字节(服务端建屏/截图链路异常)", bytes)
        assertTrue("图像字节非空", bytes!!.isNotEmpty())
        assertTrue(
            "应为 JPEG 输出(FFD8),实际 ${bytes.take(2).map { it.toInt() and 0xFF }}",
            (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8,
        )

        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull("截图应可解码", decoded)
        assertTrue(
            "解码尺寸应为 720x1280,实际 ${decoded.width}x${decoded.height}",
            decoded.width == 720 && decoded.height == 1280,
        )

        // 留证据:应用私有目录 + 日志(外部脚本可 adb 拉取)
        val dir = File(context.filesDir, "vd").apply { mkdirs() }
        File(dir, "e2e_shot.jpg").writeBytes(bytes)
        Log.i(TAG, "VD-E2E shot saved bytes=${bytes.size} displayId=$displayId")

        // 留出外部脚本注入 `input -d <displayId>` 的窗口,随后再抓一帧对比内容变化
        Log.i(TAG, "VD-E2E input-window open displayId=$displayId")
        Thread.sleep(10_000)
        val after = proxy.requestScreenshot(displayId)
        if (after != null && after.isNotEmpty()) {
            File(dir, "e2e_shot_after.jpg").writeBytes(after)
            Log.i(TAG, "VD-E2E after-shot saved bytes=${after.size} displayId=$displayId")
        } else {
            Log.w(TAG, "VD-E2E after-shot unavailable")
        }

        val destroyed =
            runCatching {
                proxy.destroyDisplay(displayId)
                true
            }.getOrDefault(false)
        assertTrue("destroyDisplay 调用异常", destroyed)
    }

    companion object {
        private const val TAG = "VdE2E"
    }
}
