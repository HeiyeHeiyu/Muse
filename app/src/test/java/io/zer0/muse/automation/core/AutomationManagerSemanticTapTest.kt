package io.zer0.muse.automation.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import io.zer0.muse.automation.executors.RootExecutor
import io.zer0.muse.tools.system.ShizukuAuthorizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutomationManagerSemanticTapTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun manager(): AutomationManager = spyk(
        AutomationManager(
            context = context,
            shizukuAuthorizer = mockk<ShizukuAuthorizer>(relaxed = true),
            rootExecutor = mockk<RootExecutor>(relaxed = true),
        ),
    )

    private fun settingsScreen() = ScreenInfo(
        nodes = listOf(
            UiNode(
                text = "Settings",
                viewIdResourceName = "com.example:id/settings",
                isClickable = true,
                boundsLeft = 1,
                boundsTop = 1,
                boundsRight = 9,
                boundsBottom = 9,
            ),
        ),
        screenWidth = 10,
        screenHeight = 10,
    )

    @Test
    fun `screenshot falls back from shell and root to accessibility`() = runTest {
        val attempts = mutableListOf<String>()

        val image = firstNonEmptyScreenshot(
            {
                attempts += "shell"
                null
            },
            {
                attempts += "root"
                byteArrayOf()
            },
            {
                attempts += "accessibility"
                byteArrayOf(1, 2, 3)
            },
        )

        assertArrayEquals(byteArrayOf(1, 2, 3), image)
        assertEquals(listOf("shell", "root", "accessibility"), attempts)
    }

    @Test
    fun `screenshot does not call lower priority channels after a valid capture`() = runTest {
        val attempts = mutableListOf<String>()

        val image = firstNonEmptyScreenshot(
            {
                attempts += "shell"
                byteArrayOf(9)
            },
            {
                attempts += "root"
                byteArrayOf(2)
            },
        )

        assertArrayEquals(byteArrayOf(9), image)
        assertEquals(listOf("shell"), attempts)
    }

    @Test
    fun `matched text tap with unknown channel result does not scroll or retry`() = runTest {
        val manager = manager()
        coEvery { manager.readScreen() } returns settingsScreen()
        coEvery { manager.tap(5, 5) } returns false

        val result = manager.tapByTextWithRetryDetailed("Settings", exact = true, maxSwipes = 3)

        assertFalse(result.success)
        assertTrue(result.matched)
        assertFalse(result.verified)
        coVerify(exactly = 1) { manager.tap(5, 5) }
        coVerify(exactly = 0) { manager.swipe(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `unverified successful text tap does not repeat when confirmation text is absent`() = runTest {
        val manager = manager()
        coEvery { manager.readScreen() } returnsMany listOf(
            settingsScreen(),
            ScreenInfo(nodes = emptyList(), screenWidth = 10, screenHeight = 10),
        )
        coEvery { manager.tap(5, 5) } returns true

        val result = manager.tapByTextWithRetryDetailed(
            text = "Settings",
            exact = true,
            maxSwipes = 3,
            verifyText = "Advanced settings",
        )

        assertFalse(result.success)
        assertTrue(result.matched)
        assertFalse(result.verified)
        coVerify(exactly = 1) { manager.tap(5, 5) }
        coVerify(exactly = 0) { manager.swipe(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `matched resource id tap failure does not scroll or retry`() = runTest {
        val manager = manager()
        coEvery { manager.readScreen() } returns settingsScreen()
        coEvery { manager.tap(5, 5) } returns false

        val result = manager.tapByViewIdWithRetryDetailed("com.example:id/settings", maxSwipes = 3)

        assertFalse(result.success)
        assertTrue(result.matched)
        assertFalse(result.verified)
        coVerify(exactly = 1) { manager.tap(5, 5) }
        coVerify(exactly = 0) { manager.swipe(any(), any(), any(), any(), any()) }
    }
}
