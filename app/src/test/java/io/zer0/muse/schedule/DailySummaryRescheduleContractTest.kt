package io.zer0.muse.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 每日总结调度回归契约。
 *
 * 这两条契约覆盖 WorkManager 持久唯一任务与 Worker 自续期的交界：
 *  - 修改时段后，旧的按小时任务不能永远留在 WorkManager 中；
 *  - Worker 发现自己的小时已经被用户移除后，不能把旧任务重新排到下一天。
 *
 * 这里先用源码契约固定行为边界，避免单元测试必须启动 Android WorkManager；
 * 真实 WorkManager 的持久化/进程重建仍需后续 instrumentation 验收。
 */
class DailySummaryRescheduleContractTest {

    private val source: String by lazy {
        File("src/main/java/io/zer0/muse/schedule/DailySummaryWorker.kt").readText()
    }
    private val settingsSource: String by lazy {
        File("src/main/java/io/zer0/muse/ui/settings/AgentSettingsPage.kt").readText()
    }

    @Test
    fun `rescheduling cleans hour-scoped unique work instead of only the base name`() {
        assertTrue(
            "scheduleNext must clean the per-hour unique work names when settings change",
            source.contains("cancelUniqueWork(uniqueWorkName("),
        )
    }

    @Test
    fun `worker does not renew a slot that is no longer configured`() {
        val start = source.indexOf("if (!shouldContinueForConfiguredSlot(slotHour, configuredSlots))")
        val end = source.indexOf("// Worker 与首页前台补偿共用同一个服务", start)
        check(start >= 0 && end > start) { "DailySummaryWorker stale-slot branch moved unexpectedly" }
        val staleBranch = source.substring(start, end)
        assertFalse(
            "an obsolete slot must stop without scheduling itself for tomorrow",
            staleBranch.contains("scheduleNextSlot("),
        )
    }

    @Test
    fun `worker keeps the current slot scheduled when slot settings cannot be read`() {
        val start = source.indexOf("if (configuredSlotsResult.isError)")
        val end = source.indexOf("val configuredSlots = slotsAfterRead(", start)
        check(start >= 0 && end > start) { "DailySummaryWorker slot-read failure branch moved unexpectedly" }
        val failureBranch = source.substring(start, end)
        assertTrue(
            "a transient settings read failure must re-enqueue the current slot",
            failureBranch.contains("scheduleNextSlot(applicationContext, slotHour, slotMinute"),
        )
    }

    @Test
    fun `scheduleNext reads slots before cancelling legacy work`() {
        val read = source.indexOf("val configuredSlots = resolvedConfiguredSlots()")
        val cancel = source.indexOf("cancelUniqueWork(UNIQUE_WORK_NAME)")
        assertTrue("settings must be read before legacy work is cancelled", read >= 0 && cancel > read)
    }

    @Test
    fun `settings save immediately re-registers daily summary work`() {
        val save = settingsSource.indexOf("settings.saveDailySummarySlots")
        val next = settingsSource.indexOf("DailySummaryWorker.scheduleNext(context)", save)
        assertTrue("settings save must be followed by schedule re-registration", save >= 0 && next > save)
    }
}
