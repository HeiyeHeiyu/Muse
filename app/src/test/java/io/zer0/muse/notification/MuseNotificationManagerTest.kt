package io.zer0.muse.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.zer0.muse.rag.ReindexAllWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class MuseNotificationManagerTest {

    @Test
    fun `reindex notification ids do not collide with chat approval or backup ids`() {
        val reindexId = privateConstantInt(ReindexAllWorker::class.java, "NOTIF_ID_REINDEX")
        val resultId = privateConstantInt(ReindexAllWorker::class.java, "NOTIF_ID_REINDEX_RESULT")
        val reserved = setOf(1001, 1002, 1003, 1004, 1005, 1006)

        assertFalse(reindexId in reserved)
        assertFalse(resultId in reserved)
        assertFalse(reindexId == resultId)
    }

    @Test
    fun `cancelAll removes proactive notification range and legacy id only`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val platformManager = context.getSystemService(NotificationManager::class.java)
        val channelId = "notification-cleanup-test"
        platformManager.createNotificationChannel(
            NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        val museIds = setOf(0x1000_0001, 0x1000_0002, 1004)
        val unrelatedId = 9000
        (museIds + unrelatedId).forEach { platformManager.notify(it, notification) }

        MuseNotificationManager(context).cancelAll()

        assertEquals(setOf(unrelatedId), platformManager.activeNotifications.map { it.id }.toSet())
    }

    private fun privateConstantInt(type: Class<*>, name: String): Int {
        val field = sequenceOf(type, *type.declaredClasses)
            .mapNotNull { candidate -> runCatching { candidate.getDeclaredField(name) }.getOrNull() }
            .first()
            .apply { isAccessible = true }
        return field.get(null) as Int
    }
}
