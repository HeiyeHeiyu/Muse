package io.zer0.muse.channel

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.nio.file.Files

class TelegramReceiverTest {
    @Test
    fun `telegram offset file is deterministic and does not expose channel id`() {
        val context = mockk<Context>()
        val filesDir = Files.createTempDirectory("telegram-offset-test").toFile()
        every { context.filesDir } returns filesDir

        val first = telegramOffsetFile(context, "telegram-account-a")
        val retry = telegramOffsetFile(context, "telegram-account-a")
        val second = telegramOffsetFile(context, "telegram-account-b")

        assertEquals(first, retry)
        assertNotEquals(first, second)
        assertFalse(first.name.contains("telegram-account-a"))
    }
}
