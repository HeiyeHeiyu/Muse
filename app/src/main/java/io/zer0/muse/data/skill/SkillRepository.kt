package io.zer0.muse.data.skill

import kotlinx.coroutines.flow.Flow

/**
 * Phase 8.8: Skill Repository。
 */
class SkillRepository(private val dao: SkillDao) {
    val observeAll: Flow<List<SkillEntity>> = dao.observeAll()

    suspend fun listEnabled(): List<SkillEntity> = dao.listEnabled()

    // L-SR2: getById 调用频率低(主要在工具执行路由时单次查询),暂不加缓存。
    // 若未来出现热路径(如循环调用),可考虑加 LRU 内存缓存。
    suspend fun getById(id: String): SkillEntity? = dao.getById(id)

    suspend fun upsert(entity: SkillEntity) = dao.upsert(entity)

    /**
     * P0-11: 内置技能启动 seed — 仅当主键缺失时初始化,已存在整行保留
     * (含用户手动关闭的 enabled=false),避免启动 REPLACE 静默重置用户的禁用选择。
     */
    suspend fun seedBuiltInIfAbsent(entity: SkillEntity) = dao.seedIfAbsent(entity)

    /**
     * v2.x: 内置技能启动 seed — 缺失则插入,已存在则仅刷新定义字段
     * (name/description/parametersJson/requiredJson/category/implementationKotlin),
     * 保留 enabled 等用户状态。
     *
     * 修复两个历史问题:
     *  - 纯 seedIfAbsent 永不刷新 → 老库 schema 停在首装版本(模型看不到新增/变更的参数);
     *  - upsert(REPLACE) 会把用户关闭的技能重新启用(P0-11 已避免,本方法延续该保证)。
     */
    suspend fun seedOrRefreshBuiltIn(entity: SkillEntity) {
        if (dao.getById(entity.id) == null) {
            dao.seedIfAbsent(entity)
        } else {
            dao.refreshBuiltInDefinition(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                parametersJson = entity.parametersJson,
                requiredJson = entity.requiredJson,
                category = entity.category,
                implementationKotlin = entity.implementationKotlin,
            )
        }
    }

    suspend fun update(entity: SkillEntity) = dao.update(entity)

    suspend fun delete(id: String) = dao.delete(id)

    /** 切换 Skill 启用状态(同时刷新 updatedAt)。 */
    suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    ) = dao.setEnabled(id, enabled, System.currentTimeMillis())

    /**
     * 按 id 列表过滤启用的 Skills。
     * @param skillIds 启用的 skill id 列表;null 表示全部启用
     *
     * M-SR1: 改用 DAO 层 `WHERE id IN (:ids)` 查询,避免全量加载再内存 filter。
     */
    suspend fun listEnabledByIds(skillIds: List<String>?): List<SkillEntity> {
        if (skillIds.isNullOrEmpty()) return dao.listEnabled()
        return dao.listEnabledByIds(skillIds)
    }
}
