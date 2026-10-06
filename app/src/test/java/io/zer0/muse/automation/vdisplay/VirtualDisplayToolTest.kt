package io.zer0.muse.automation.vdisplay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.zer0.muse.automation.core.AutomationManager
import io.zer0.muse.automation.executors.ShellExecutor
import io.zer0.muse.tools.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VirtualDisplayToolTest {
    @Test
    fun `default input refreshes compatibility display before sending command`() = runBlocking {
        val client = mockk<VirtualDisplayClient>()
        val manager = mockk<VirtualDisplayServerManager>()
        val automationManager = mockk<AutomationManager>(relaxed = true)
        every { manager.lastDisplayId } returns 42
        coEvery { client.ensureCompatibilityDisplay() } returns
            Result.success(VirtualDisplayClient.DisplayHandle(43, 720, 1280))
        coEvery { manager.exec("input -d 43 tap 10 20") } returns ShellExecutor.ExecDetail(0, "")

        val registry = ToolRegistry(ApplicationProvider.getApplicationContext<Context>())
        VirtualDisplayTool(
            context = ApplicationProvider.getApplicationContext(),
            client = client,
            manager = manager,
            automationManager = automationManager,
        ).register(registry)

        val outcome = registry.execute(
            "virtual_screen_input",
            mapOf("action" to "tap", "x" to "10", "y" to "20"),
        )

        assertFalse(outcome.isError)
        coVerify(exactly = 1) { client.ensureCompatibilityDisplay() }
        coVerify(exactly = 1) { manager.exec("input -d 43 tap 10 20") }
    }
}
