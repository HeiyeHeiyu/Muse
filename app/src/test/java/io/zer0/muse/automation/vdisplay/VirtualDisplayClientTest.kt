package io.zer0.muse.automation.vdisplay

import android.content.Context
import android.os.RemoteException
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.zer0.muse.vdproto.IVirtualDisplayService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VirtualDisplayClientTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `createDisplay requests a dedicated server-side display`() = runBlocking {
        val manager = mockk<VirtualDisplayServerManager>()
        val service = mockk<IVirtualDisplayService>()
        coEvery { manager.ensureStarted() } returns Result.success(service)
        every { service.createDisplay(720, 1280, 320) } returns 51
        val client = VirtualDisplayClient(context, manager)

        val result = client.createDisplay()

        assertEquals(51, result.getOrThrow().displayId)
        assertEquals(720, result.getOrThrow().width)
        verify(exactly = 0) { service.ensureDisplay(any(), any(), any()) }
    }

    @Test
    fun `cancellation after remote allocation destroys the dedicated display`() = runBlocking {
        val manager = mockk<VirtualDisplayServerManager>()
        val service = mockk<IVirtualDisplayService>()
        val parent = Job()
        coEvery { manager.ensureStarted() } returns Result.success(service)
        every { service.createDisplay(720, 1280, 320) } answers {
            parent.cancel()
            52
        }
        every { service.destroyDisplay(52) } just runs
        val client = VirtualDisplayClient(context, manager)
        val scope = CoroutineScope(coroutineContext + parent + Dispatchers.Unconfined)

        try {
            scope.async { client.createDisplay() }.await()
            fail("cancelled parent should cancel createDisplay")
        } catch (_: CancellationException) {
            // Expected: the client must still release the server-side allocation before propagating cancellation.
        }

        verify(exactly = 1) { service.destroyDisplay(52) }
    }

    @Test
    fun `destroy clears stale display id when the virtual display service is unavailable`() = runBlocking {
        val manager = mockk<VirtualDisplayServerManager>(relaxed = true)
        every { manager.lastDisplayId } returns 53
        coEvery { manager.existingLiveProxy() } returns null
        val client = VirtualDisplayClient(context, manager)

        assertFalse(client.destroy(53))

        verify(exactly = 1) { manager.lastDisplayId = -1 }
    }

    @Test
    fun `screenshot clears cached id when a live service rejects the stale display`() = runBlocking {
        val manager = mockk<VirtualDisplayServerManager>(relaxed = true)
        val service = mockk<IVirtualDisplayService>()
        every { manager.lastDisplayId } returns 42
        coEvery { manager.ensureStarted() } returns Result.success(service)
        every { service.requestScreenshot(42) } throws RemoteException("stale display")
        val client = VirtualDisplayClient(context, manager)

        assertNull(client.screenshot())

        verify(exactly = 1) { manager.lastDisplayId = -1 }
    }

    @Test
    fun `openApp clears cached id when a live service rejects the stale display`() = runBlocking {
        val manager = mockk<VirtualDisplayServerManager>(relaxed = true)
        val service = mockk<IVirtualDisplayService>()
        every { manager.lastDisplayId } returns 42
        coEvery { manager.existingLiveProxy() } returns service
        every { service.launchApp("com.example.target", 42) } throws RemoteException("stale display")
        val client = VirtualDisplayClient(context, manager)

        assertFalse(client.openApp("com.example.target", 42))

        verify(exactly = 1) { manager.lastDisplayId = -1 }
    }
}
