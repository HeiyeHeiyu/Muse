package io.zer0.muse.schedule

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.zer0.muse.data.SettingsRepository
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class DailySummaryCatchUpPolicyTest {

    @Test
    fun `catch up skips safely when configured slots cannot be read`() = runTest {
        val settings = mockk<SettingsRepository>()
        every { settings.dailySummarySlotsFlow } returns flow {
            throw IOException("DataStore unavailable")
        }

        DailySummaryWorker.enqueueCatchUpIfDue(
            context = mockk<Context>(relaxed = true),
            settings = settings,
            nowMillis = 1_791_000_000_000L,
        )
    }
}
