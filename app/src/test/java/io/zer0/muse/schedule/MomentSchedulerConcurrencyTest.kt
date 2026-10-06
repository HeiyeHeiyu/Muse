package io.zer0.muse.schedule

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.zer0.muse.data.SettingsRepository
import io.zer0.muse.data.assistant.AssistantRepository
import io.zer0.muse.data.moment.MomentGenerator
import io.zer0.muse.data.moment.MomentInteractionEngine
import io.zer0.muse.data.moment.MomentRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentSchedulerConcurrencyTest {

    @Test
    fun `worker and in-process checks are serialized`() = runBlocking {
        val settings = mockk<SettingsRepository>(relaxed = true)
        val repository = mockk<MomentRepository>(relaxed = true)
        val generator = mockk<MomentGenerator>(relaxed = true)
        val assistantRepository = mockk<AssistantRepository>(relaxed = true)
        val interactionEngine = mockk<MomentInteractionEngine>(relaxed = true)
        every { settings.scheduleWorkEnabledFlow } returns flowOf(true)
        every { settings.dailyMomentCountFlow } returns flowOf(1)

        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var active = 0
        var maxActive = 0
        coEvery { repository.countToday() } coAnswers {
            active += 1
            maxActive = maxOf(maxActive, active)
            firstEntered.complete(Unit)
            releaseFirst.await()
            active -= 1
            1
        }

        val scheduler = MomentScheduler(
            appScope = this,
            settings = settings,
            repository = repository,
            generator = generator,
            assistantRepository = assistantRepository,
            interactionEngine = interactionEngine,
        )
        val first = async(Dispatchers.Default) { scheduler.checkAndGenerateOnce() }
        withTimeout(2_000) { firstEntered.await() }
        val second = async(Dispatchers.Default) { scheduler.checkAndGenerateOnce() }

        kotlinx.coroutines.delay(100)
        assertEquals("并发检查不能同时进入 countToday", 1, maxActive)
        releaseFirst.complete(Unit)
        first.await()
        second.await()
    }
}
