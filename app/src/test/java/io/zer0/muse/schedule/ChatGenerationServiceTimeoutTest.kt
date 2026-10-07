package io.zer0.muse.schedule

import android.app.Application
import android.app.Notification
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.zer0.muse.notification.MuseNotificationManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ChatGenerationServiceTimeoutTest {

    private lateinit var notificationManager: MuseNotificationManager
    private lateinit var generationManager: ChatGenerationManager

    @Before
    fun setUp() {
        if (GlobalContext.getOrNull() != null) stopKoin()
        val application = ApplicationProvider.getApplicationContext<Application>()
        notificationManager = mockk(relaxed = true)
        generationManager = mockk(relaxed = true)
        every {
            notificationManager.buildGenerationNotification(any(), any(), any())
        } returns Notification()

        startKoin {
            androidContext(application)
            modules(
                module {
                    single<MuseNotificationManager> { notificationManager }
                    single<ChatGenerationManager> { generationManager }
                },
            )
        }
    }

    @After
    fun tearDown() {
        if (GlobalContext.getOrNull() != null) stopKoin()
    }

    @Test
    fun dataSyncTimeoutCancelsGenerationAndStopsService() {
        val controller = Robolectric.buildService(ChatGenerationService::class.java).create()
        val service = controller.get()

        service.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        verify(exactly = 1) { generationManager.stop() }
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun dataSyncTimeoutRequestsServiceStopBeforeCancellingGeneration() {
        val controller = Robolectric.buildService(ChatGenerationService::class.java).create()
        val service = controller.get()
        assertFalse(shadowOf(service).isStoppedBySelf)
        val cancellationEntered = CountDownLatch(1)
        val allowCancellationToFinish = CountDownLatch(1)
        every {
            generationManager.stop()
        } answers {
            cancellationEntered.countDown()
            assertTrue(allowCancellationToFinish.await(5, TimeUnit.SECONDS))
        }

        val timeoutThread = Thread {
            service.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }.apply { start() }
        try {
            assertTrue(cancellationEntered.await(5, TimeUnit.SECONDS))
            assertTrue(
                "service stop must be requested before generation cancellation",
                shadowOf(service).isStoppedBySelf,
            )
        } finally {
            allowCancellationToFinish.countDown()
            timeoutThread.join(5_000)
            controller.destroy()
        }
        assertFalse("timeout callback must finish after cancellation is released", timeoutThread.isAlive)
    }

    @Test
    fun dataSyncTimeoutStillStopsServiceWhenGenerationCancellationThrows() {
        every { generationManager.stop() } throws IllegalStateException("cancel failed")
        val controller = Robolectric.buildService(ChatGenerationService::class.java).create()
        val service = controller.get()

        service.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }
}
