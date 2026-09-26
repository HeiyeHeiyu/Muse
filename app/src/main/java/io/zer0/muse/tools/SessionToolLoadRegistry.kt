package io.zer0.muse.tools

import java.util.concurrent.ConcurrentHashMap

/**
 * v2.x 工具瘦身阶段3:会话级"动态装载"工具名注册表。
 *
 * find_tools 命中工具后把工具名记在这里(键 = 宿主会话 id);后续请求的工具暴露
 * 过滤会把已装载工具并入 dynamicAllowed,让它们对模型持续可见。
 *
 * 注意:装载 ≠ 放行 — 只影响"可见性",审批/权限/风险链路完全不变。
 * 纯内存态,进程死亡自然丢失;不与 SessionPermissionStore(用户"本会话允许")混用。
 */
object SessionToolLoadRegistry {

    /** 单会话装载上限,防 find_tools 反复检索把工具面撑回全量。 */
    private const val MAX_LOADED_PER_SESSION = 64

    private val loaded = ConcurrentHashMap<String, Set<String>>()

    /** 记录某会话已装载的工具名(幂等;达到上限后不再新增)。 */
    fun markLoaded(sessionId: String, toolNames: Collection<String>) {
        if (sessionId.isBlank() || toolNames.isEmpty()) return
        loaded.compute(sessionId) { _, current ->
            val set = (current ?: emptySet()).toMutableSet()
            toolNames.forEach { name -> if (set.size < MAX_LOADED_PER_SESSION) set.add(name) }
            set
        }
    }

    /** 读取某会话已装载的工具名(无记录时为空集)。 */
    fun loadedFor(sessionId: String): Set<String> = loaded[sessionId].orEmpty()

    /** 清除某会话的装载记录(会话删除时调用)。 */
    fun clear(sessionId: String) {
        loaded.remove(sessionId)
    }
}
