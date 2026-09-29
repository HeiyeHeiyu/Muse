package io.zer0.memory.fact

import android.content.Context

/**
 * R-DB-03: 记录早期 facts 数据库被归档重建的事件,供 app 层在记忆页给出可见提示。
 */
object MemoryLegacyReset {
    private const val PREFS_NAME = "muse_memory_legacy_reset"

    /** 恢复条数等内部状态的键前缀 —— 不参与归档事件判定、不被 [consume] 清除。 */
    private const val INTERNAL_PREFIX = "__"

    private const val KEY_RECOVERED_COUNT = "__recovered_count"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 标记指定数据库发生归档重建。 */
    fun mark(context: Context, dbName: String) {
        prefs(context).edit().putBoolean(dbName, true).apply()
    }

    /** 是否存在待处理的归档事件(不消费标记)。 */
    fun hasPending(context: Context): Boolean = archiveFlags(context).isNotEmpty()

    /** 消费一次提示;返回是否有待提示的归档重建,并清空归档标记。 */
    fun consume(context: Context): Boolean {
        val p = prefs(context)
        val keys = archiveFlags(context)
        if (keys.isNotEmpty()) {
            p.edit().apply { keys.forEach { remove(it) } }.apply()
        }
        return keys.isNotEmpty()
    }

    /** v2.2.1: 记录最近一次从归档恢复的事实条数(供记忆页提示;一次消费)。 */
    fun noteRecovered(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_RECOVERED_COUNT, count).apply()
    }

    /** v2.2.1: 读取并清除恢复条数;从未记录时返回 null。 */
    fun consumeRecovered(context: Context): Int? {
        val p = prefs(context)
        if (!p.contains(KEY_RECOVERED_COUNT)) return null
        val count = p.getInt(KEY_RECOVERED_COUNT, -1)
        p.edit().remove(KEY_RECOVERED_COUNT).apply()
        return count.takeIf { it >= 0 }
    }

    /** v2.2.1: 该库是否已尝试过误归档恢复(每库只尝试一次,防反复扫描备份)。 */
    fun isRecoveryAttempted(context: Context, dbName: String): Boolean =
        prefs(context).getBoolean("${INTERNAL_PREFIX}recovery_attempted_$dbName", false)

    /** v2.2.1: 标记该库已尝试过误归档恢复。 */
    fun markRecoveryAttempted(context: Context, dbName: String) {
        prefs(context).edit().putBoolean("${INTERNAL_PREFIX}recovery_attempted_$dbName", true).apply()
    }

    /** 归档事件标记键集合(排除 __ 前缀内部状态)。 */
    private fun archiveFlags(context: Context): Set<String> =
        prefs(context).all.filter { (key, value) -> !key.startsWith(INTERNAL_PREFIX) && value == true }.keys
}
