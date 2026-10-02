package io.zer0.muse.automation.vdisplay

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.zer0.muse.automation.core.AutomationManager
import io.zer0.muse.automation.executors.ShellExecutor
import io.zer0.muse.vdproto.IVirtualDisplayService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VirtualDisplayServerManagerTest {

    @Test
    fun concurrentEnsureCallsStartTheServerOnlyOnce() = runBlocking {
        VirtualDisplayBinderRegistry.update(null)
        val firstStatEntered = CompletableDeferred<Unit>()
        val duplicateStatEntered = CompletableDeferred<Unit>()
        val releaseFirstStat = CompletableDeferred<Unit>()
        val statCalls = AtomicInteger()
        val killCalls = AtomicInteger()
        val launchCalls = AtomicInteger()
        val service = aliveService()
        val context = mockk<Context>()
        val assets = mockk<AssetManager>()
        every { context.packageName } returns "io.zer0.muse"
        every { context.assets } returns assets
        every { assets.open("vd/vd-server.jar") } returns ByteArrayInputStream(byteArrayOf(1, 2, 3))

        val shell = mockk<AutomationManager>()
        every { shell.permissionState } returns MutableStateFlow(AutomationManager.PermissionState(shellEnabled = true))
        assertTrue(shell.permissionState.value.shellEnabled)
        coEvery { shell.execTiered(any()) } coAnswers {
            val command = firstArg<String>()
            val result = when {
                command.startsWith("stat -c %s ") -> {
                    val count = statCalls.incrementAndGet()
                    if (count == 1) {
                        firstStatEntered.complete(Unit)
                        releaseFirstStat.await()
                    } else {
                        duplicateStatEntered.complete(Unit)
                    }
                    ShellExecutor.ExecDetail(0, "3")
                }
                command.startsWith("pkill -f ") -> {
                    killCalls.incrementAndGet()
                    ShellExecutor.ExecDetail(0, "")
                }
                command.startsWith("CLASSPATH=") -> {
                    launchCalls.incrementAndGet()
                    VirtualDisplayBinderRegistry.update(service.asBinder())
                    ShellExecutor.ExecDetail(0, "")
                }
                else -> error("Unexpected device command: $command")
            }
            "Shizuku" to result
        }

        try {
            val manager = VirtualDisplayServerManager(context, shell)
            val first = async { manager.ensureStarted() }
            val firstStatWasReached = withTimeoutOrNull(2_000) {
                firstStatEntered.await()
                true
            } ?: false
            if (!firstStatWasReached) {
                releaseFirstStat.complete(Unit)
                val initialResult = first.await()
                assertTrue(
                    "first ensure failed before checking the deployed jar: ${initialResult.exceptionOrNull()}",
                    firstStatWasReached,
                )
            }
            val second = async { manager.ensureStarted() }
            val duplicateStartupObserved =
                withTimeoutOrNull(1_000) {
                    duplicateStatEntered.await()
                    true
                } ?: false
            releaseFirstStat.complete(Unit)

            val results = awaitAll(first, second)

            assertFalse("second caller must reuse the first startup", duplicateStartupObserved)
            assertTrue(results.all(Result<*>::isSuccess))
            assertEquals(1, statCalls.get())
            assertEquals(1, killCalls.get())
            assertEquals(1, launchCalls.get())
        } finally {
            releaseFirstStat.complete(Unit)
            VirtualDisplayBinderRegistry.update(null)
        }
    }

    private fun aliveService(): IVirtualDisplayService = object : IVirtualDisplayService.Stub() {
        override fun ensureDisplay(width: Int, height: Int, dpi: Int): Int = 42

        override fun createDisplay(width: Int, height: Int, dpi: Int): Int = 43

        override fun launchApp(packageName: String, displayId: Int): Boolean = true

        override fun destroyDisplay(displayId: Int) = Unit

        override fun requestScreenshot(displayId: Int): ByteArray? = null

        override fun isAlive(): Boolean = true
    }
}
