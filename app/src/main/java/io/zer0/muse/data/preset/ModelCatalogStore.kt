package io.zer0.muse.data.preset

import android.content.Context
import io.zer0.common.AppJson
import io.zer0.ai.core.Model
import io.zer0.ai.core.VisionCapabilities
import io.zer0.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * P1 模型目录（本地可维护）。
 *
 * 目标：解决 PresetProviders.kt 硬编码模型清单过期、上下文/能力不准的问题。
 *
 * 三层数据：
 * 1. 内置默认：由 [builtinEntries] 提供（随版本发布，代码内默认）。
 * 2. 用户覆盖：`filesDir/model_catalog/user_models.json`，用户手动修正过的字段。
 * 3. 远端目录：`filesDir/model_catalog/remote_models.json`，由 [refreshRemote] 从 [remoteUrl] 拉取。
 *    **默认关闭**：[remoteUrl] 为空串时不发任何网络请求，也不会应用远端层。
 *
 * 合并规则（用户优先）：
 * - 合并优先级：用户 > 远端 > 内置（字段级，高层只覆盖自己声明过的非空字段）。
 * - 同一 providerId + modelId：用户覆盖字段优先，未覆盖字段回退远端，再回退内置。
 * - 用户/远端新增模型：保留；builtInRemoved=true 表示该层隐藏模型。
 *
 * 任意一层缺失/失败都不影响上一层：远端拉取失败时保留已有缓存与内置目录，
 * 只记日志（静默回退）。
 */
class ModelCatalogStore(
    private val context: Context,
    /**
     * 远端模型目录 URL（JSON，格式见 [RemoteCatalog]）。
     *
     * 空串 = 未启用（默认值）：[refreshRemote] 立即返回且不发任何网络请求，
     * [entries] 也不会应用远端层。启用场景需由调用方显式传入 URL。
     */
    private val remoteUrl: String = "",
) {

    @Serializable
    data class ModelCatalogEntry(
        val providerId: String,
        val modelId: String,
        val displayName: String? = null,
        val contextWindow: Int? = null,
        val maxOutputTokens: Int? = null,
        val supportsVision: Boolean? = null,
        val supportsStreaming: Boolean? = null,
        val supportsVideo: Boolean? = null,
        val supportsTools: Boolean? = null,
        val supportsReasoning: Boolean? = null,
        val inputModalities: Set<String>? = null,
        val outputModalities: Set<String>? = null,
        val visionCapabilities: VisionCapabilities? = null,
        val updatedAt: Long = 0L,
        val userEdited: Boolean = false,
        val builtInRemoved: Boolean = false,
    )

    @Serializable
    private data class UserOverrides(
        val items: List<ModelCatalogEntry> = emptyList(),
        val updatedAt: Long = 0L,
    )

    /**
     * 远端目录文档格式：`{ "schemaVersion":1, "publishedAt":"...", "items":[ModelCatalogEntry...] }`。
     * 与内置/用户覆盖共用 [ModelCatalogEntry]，字段级合并。
     */
    @Serializable
    data class RemoteCatalog(
        val schemaVersion: Int = 1,
        val publishedAt: String = "",
        val items: List<ModelCatalogEntry> = emptyList(),
    )

    private val catalogDir: File
        get() = File(context.filesDir, "model_catalog")

    private val userOverridesFile: File
        get() = File(catalogDir, "user_models.json")

    /** 远端目录本地缓存(拉取成功后落盘)。 */
    private val remoteCatalogFile: File
        get() = File(catalogDir, "remote_models.json")

    @Volatile
    private var userCache: UserOverrides? = null

    /** 远端目录内存缓存(null 表示未加载或不存在)。 */
    @Volatile
    private var remoteCache: RemoteCatalog? = null

    /** 远端目录是否已尝试读取磁盘(避免反复 IO / 反复记录解析失败日志)。 */
    @Volatile
    private var remoteLoaded: Boolean = false

    /**
     * 内置默认模型目录（随版本发布）。
     *
     * 只覆盖主流/常用模型，字段缺失时 UI 显示“未收录/未知”，
     * 不强行猜测，避免把错误上下文发给 Provider。
     */
    fun builtinEntries(): List<ModelCatalogEntry> = buildList {
        // OpenAI
        add(entry("openai", "gpt-4o", "GPT-4o", 128_000, 16_384, vision = true, tools = true))
        add(entry("openai", "gpt-4o-mini", "GPT-4o mini", 128_000, 16_384, vision = true, tools = true))
        add(entry("openai", "gpt-4.1", "GPT-4.1", 1_047_576, 32_768, vision = true, tools = true))
        add(entry("openai", "gpt-4.1-mini", "GPT-4.1 mini", 1_047_576, 32_768, vision = true, tools = true))
        add(entry("openai", "o3", "o3", 200_000, 100_000, tools = true, reasoning = true))
        add(entry("openai", "o4-mini", "o4-mini", 200_000, 100_000, vision = true, tools = true, reasoning = true))
        // Anthropic
        add(entry("anthropic", "claude-sonnet-4-5-20250514", "Claude Sonnet 4.5", 200_000, 64_000, vision = true, tools = true, reasoning = true))
        add(entry("anthropic", "claude-opus-4-1-20250805", "Claude Opus 4.1", 200_000, 32_000, vision = true, tools = true, reasoning = true))
        add(entry("anthropic", "claude-haiku-4-5-20251001", "Claude Haiku 4.5", 200_000, 64_000, vision = true, tools = true))
        // Gemini
        add(entry("gemini", "gemini-2.5-pro", "Gemini 2.5 Pro", 1_048_576, 65_536, vision = true, tools = true, reasoning = true))
        add(entry("gemini", "gemini-2.5-flash", "Gemini 2.5 Flash", 1_048_576, 65_536, vision = true, tools = true, reasoning = true))
        // DeepSeek
        add(entry("deepseek", "deepseek-chat", "DeepSeek Chat", 64_000, 8_192, tools = true))
        add(entry("deepseek", "deepseek-reasoner", "DeepSeek Reasoner", 64_000, 8_192, reasoning = true))
        // 通义
        add(entry("qwen", "qwen-plus", "Qwen Plus", 131_072, 8_192, tools = true))
        add(entry("qwen", "qwen-max", "Qwen Max", 32_000, 8_192, vision = true, tools = true))
        // 智谱
        add(entry("zhipu", "glm-4-plus", "GLM-4 Plus", 128_000, 8_192, tools = true))
        add(entry("zhipu", "glm-4-flash", "GLM-4 Flash", 128_000, 8_192, tools = true))
        // Kimi
        add(entry("moonshot", "kimi-k2", "Kimi K2", 128_000, 16_384, tools = true))
        add(entry("moonshot", "kimi-k2.7", "Kimi K2.7", 256_000, 16_384, tools = true, reasoning = true))
        // 豆包
        add(entry("doubao", "doubao-pro-32k", "Doubao Pro 32K", 32_000, 4_096, tools = true))
        // xAI
        add(entry("xai", "grok-4", "Grok 4", 256_000, 32_768, vision = true, tools = true))
        add(entry("xai", "grok-4-heavy", "Grok 4 Heavy", 256_000, 32_768, vision = true, tools = true, reasoning = true))
        // Ollama 本地常见模型
        add(entry("ollama", "llama3.1", "Llama 3.1", 128_000, null, tools = true))
        add(entry("ollama", "qwen2.5", "Qwen 2.5", 131_072, null, tools = true))
        // SiliconFlow 免费常用
        add(entry("siliconflow", "deepseek-ai/DeepSeek-V3", "DeepSeek V3", 64_000, 8_192, tools = true))
        add(entry("siliconflow", "Qwen/Qwen2.5-7B-Instruct", "Qwen 2.5 7B", 131_072, 8_192, tools = true))
    }

    /**
     * 返回内置 + 用户覆盖合并后的完整目录。
     *
     * @param providerId 为空返回全部；非空只返回该供应商。
     */
    fun entries(providerId: String? = null): List<ModelCatalogEntry> {
        val merged = merge(builtinEntries(), loadRemoteEntries(), loadUserOverrides())
        return merged.filter { providerId == null || it.providerId.equals(providerId, ignoreCase = true) }
    }

    /** 按 providerId + modelId 精确查询（含远端与用户覆盖）。 */
    fun find(providerId: String, modelId: String): ModelCatalogEntry? =
        entries(providerId).firstOrNull { it.modelId.equals(modelId, ignoreCase = true) }

    /** 远端目录是否启用（未配置 URL 时为 false，不会发起任何网络请求）。 */
    fun isRemoteEnabled(): Boolean = remoteUrl.isNotBlank()

    /** 当前生效的远端目录（缓存优先）；未启用/缺失/解析失败返回 null。 */
    fun remoteCatalog(): RemoteCatalog? {
        if (remoteUrl.isBlank()) return null
        remoteCache?.let { return it }
        if (remoteLoaded) return null
        remoteLoaded = true
        if (!remoteCatalogFile.exists()) return null
        val loaded = runCatching {
            AppJson.decodeFromString(RemoteCatalog.serializer(), remoteCatalogFile.readText())
        }.onFailure { Logger.w(TAG, "远端模型目录解析失败,按空处理", it) }.getOrNull()
        remoteCache = loaded
        return loaded
    }

    private fun loadRemoteEntries(): List<ModelCatalogEntry> = remoteCatalog()?.items.orEmpty()

    /**
     * 从 [remoteUrl] 拉取远端目录并落盘缓存。
     *
     * - 未配置 URL（[isRemoteEnabled]=false）时立即返回，**不发任何网络请求**（默认关闭）；
     * - 网络/解析失败：保留现有缓存与内置目录，仅记日志（静默回退）；
     * - [publishedAt] 与当前一致且非 [forceFresh] 时不重复写盘。
     */
    suspend fun refreshRemote(forceFresh: Boolean = false): RemoteRefreshResult = withContext(Dispatchers.IO) {
        val url = remoteUrl.trim()
        if (url.isEmpty()) {
            return@withContext RemoteRefreshResult(enabled = false, ok = false, updated = false, message = "远端目录未启用")
        }
        val text = try {
            httpGet(url)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "远端目录拉取失败: ${e.message}")
            null
        }
        if (text.isNullOrBlank()) {
            return@withContext RemoteRefreshResult(enabled = true, ok = false, updated = false, message = "网络请求失败")
        }
        val parsed = runCatching {
            AppJson.decodeFromString(RemoteCatalog.serializer(), text)
        }.onFailure { Logger.w(TAG, "远端目录格式不合法: ${it.message}") }.getOrNull()
        if (parsed == null || parsed.items.isEmpty()) {
            return@withContext RemoteRefreshResult(enabled = true, ok = false, updated = false, message = "远端目录为空或格式不合法")
        }
        val currentPublished = remoteCatalog()?.publishedAt.orEmpty()
        if (!forceFresh && currentPublished.isNotBlank() && currentPublished == parsed.publishedAt) {
            return@withContext RemoteRefreshResult(enabled = true, ok = true, updated = false, message = "已是最新(${parsed.publishedAt})")
        }
        runCatching {
            catalogDir.mkdirs()
            remoteCatalogFile.writeText(text)
            remoteCache = parsed
            remoteLoaded = true
            Logger.i(TAG, "远端模型目录已更新: ${parsed.publishedAt}, ${parsed.items.size} 条")
        }.onFailure { e ->
            Logger.w(TAG, "远端模型目录写入失败: ${e.message}")
            return@withContext RemoteRefreshResult(enabled = true, ok = false, updated = false, message = "写入缓存失败")
        }
        RemoteRefreshResult(enabled = true, ok = true, updated = true, message = "已更新到 ${parsed.publishedAt}")
    }

    /** 远端目录状态摘要（设置页/诊断用）。 */
    fun remoteStatus(): RemoteStatus {
        val catalog = remoteCatalog()
        return RemoteStatus(
            enabled = isRemoteEnabled(),
            url = remoteUrl,
            publishedAt = catalog?.publishedAt.orEmpty(),
            itemCount = catalog?.items?.size ?: 0,
        )
    }

    private fun httpGet(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = REMOTE_TIMEOUT_MS
            conn.readTimeout = REMOTE_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 将目录条目投影到运行时模型列表。
     *
     * 现有 Provider 模型会应用目录元数据；目录中的用户新增模型也会进入列表。
     * 这样“模型目录里维护了”与“模型选择器/请求实际使用了”保持同一份数据。
     */
    fun mergeIntoModels(
        providerId: String,
        models: List<Model>,
        runtimeProviderId: String = providerId,
    ): List<Model> {
        val catalogEntries = entries(providerId)
            .associateBy { keyOf(providerId, it.modelId) }
        val result = LinkedHashMap<String, Model>()
        for (model in models) {
            val entry = catalogEntries[keyOf(providerId, model.id)]
            if (entry?.builtInRemoved == true) continue
            // 保留运行时 Provider 身份（例如 preset_openai），只用目录 providerId 做匹配。
            result[model.id.trim().lowercase()] = model.applyCatalogEntry(entry)
        }
        for (entry in catalogEntries.values) {
            val key = entry.modelId.trim().lowercase()
            if (entry.builtInRemoved || result.containsKey(key)) continue
            result[key] = entry.toModel(runtimeProviderId)
        }
        return result.values.toList()
    }

    /**
     * 保存用户对某个模型的字段修改。
     *
     * 只写入用户显式修改的字段；内置其他字段继续回退。
     */
    @Synchronized
    fun saveUserOverride(providerId: String, modelId: String, patch: ModelCatalogEntry): ModelCatalogEntry {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(modelId.isNotBlank()) { "modelId must not be blank" }
        requirePositiveOrNull(patch.contextWindow, "contextWindow")
        requirePositiveOrNull(patch.maxOutputTokens, "maxOutputTokens")
        val current = find(providerId, modelId)
        val base = current ?: ModelCatalogEntry(providerId = providerId, modelId = modelId)
        val merged = base.copy(
            providerId = providerId,
            modelId = modelId,
            displayName = patch.displayName ?: base.displayName,
            contextWindow = patch.contextWindow ?: base.contextWindow,
            maxOutputTokens = patch.maxOutputTokens ?: base.maxOutputTokens,
            supportsVision = patch.supportsVision ?: base.supportsVision,
            supportsStreaming = patch.supportsStreaming ?: base.supportsStreaming,
            supportsVideo = patch.supportsVideo ?: base.supportsVideo,
            supportsTools = patch.supportsTools ?: base.supportsTools,
            supportsReasoning = patch.supportsReasoning ?: base.supportsReasoning,
            inputModalities = patch.inputModalities ?: base.inputModalities,
            outputModalities = patch.outputModalities ?: base.outputModalities,
            visionCapabilities = patch.visionCapabilities ?: base.visionCapabilities,
            updatedAt = System.currentTimeMillis(),
            userEdited = true,
            builtInRemoved = false,
        )
        val overrides = loadUserOverrides()
        val newList = overrides.filterNot {
            keyOf(it.providerId, it.modelId) == keyOf(providerId, modelId)
        } + merged
        persistUserOverrides(UserOverrides(items = newList, updatedAt = System.currentTimeMillis()))
        return merged
    }

    /** 用户删除内置模型（目录中隐藏，不破坏内置数据）。 */
    @Synchronized
    fun removeModel(providerId: String, modelId: String) {
        val overrides = loadUserOverrides()
        val removed = ModelCatalogEntry(
            providerId = providerId,
            modelId = modelId,
            updatedAt = System.currentTimeMillis(),
            userEdited = true,
            builtInRemoved = true,
        )
        val newList = overrides.filterNot {
            keyOf(it.providerId, it.modelId) == keyOf(providerId, modelId)
        } + removed
        persistUserOverrides(UserOverrides(items = newList, updatedAt = System.currentTimeMillis()))
    }

    /** 恢复内置默认（移除该模型的所有用户覆盖）。 */
    @Synchronized
    fun resetModel(providerId: String, modelId: String) {
        val overrides = loadUserOverrides()
        val newList = overrides.filterNot {
            keyOf(it.providerId, it.modelId) == keyOf(providerId, modelId)
        }
        persistUserOverrides(UserOverrides(items = newList, updatedAt = System.currentTimeMillis()))
    }

    /** 用户手动新增一个目录中不存在的模型。 */
    @Synchronized
    fun addUserModel(providerId: String, modelId: String, displayName: String? = null): ModelCatalogEntry =
        saveUserOverride(
            providerId,
            modelId,
            ModelCatalogEntry(
                providerId = providerId,
                modelId = modelId,
                displayName = displayName ?: modelId,
            ),
        )

    /** 导出当前合并目录为 JSON（备份/分享用）。 */
    fun exportJson(): String = AppJson.encodeToString(
        ListSerializer(ModelCatalogEntry.serializer()),
        entries(),
    )

    /** 导入目录 JSON（仅更新用户覆盖；内置仍由代码提供）。 */
    @Synchronized
    @Suppress("CyclomaticComplexMethod")
    fun importJson(json: String): Boolean {
        return runCatching {
            val imported = AppJson.decodeFromString<List<ModelCatalogEntry>>(json)
            val overrides = loadUserOverrides()
            val existing = overrides.filterNot { existingEntry ->
                imported.any {
                    keyOf(it.providerId, it.modelId) == keyOf(existingEntry.providerId, existingEntry.modelId)
                }
            }
            persistUserOverrides(
                UserOverrides(
                    items = existing + imported.map { it.copy(userEdited = true, updatedAt = System.currentTimeMillis()) },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }.onFailure { e ->
            Logger.w(TAG, "模型目录导入失败", e)
        }.isSuccess
    }

    private fun entry(
        providerId: String,
        modelId: String,
        displayName: String? = null,
        contextWindow: Int? = null,
        maxOutputTokens: Int? = null,
        vision: Boolean? = null,
        tools: Boolean? = null,
        reasoning: Boolean? = null,
    ) = ModelCatalogEntry(
        providerId = providerId,
        modelId = modelId,
        displayName = displayName,
        contextWindow = contextWindow,
        maxOutputTokens = maxOutputTokens,
        supportsVision = vision,
        supportsTools = tools,
        supportsReasoning = reasoning,
    )

    /**
     * 三层合并（优先级：用户 > 远端 > 内置）。
     *
     * 同 key 字段级合并：高层只覆盖自己声明过的非空字段；
     * `builtInRemoved=true` 表示该层隐藏此模型（用户删除优先于其他层）。
     */
    private fun merge(
        builtin: List<ModelCatalogEntry>,
        remote: List<ModelCatalogEntry>,
        user: List<ModelCatalogEntry>,
    ): List<ModelCatalogEntry> {
        val result = LinkedHashMap<String, ModelCatalogEntry>()
        builtin.forEach { b -> result[keyOf(b.providerId, b.modelId)] = b }
        applyLayer(result, remote)
        applyLayer(result, user)
        return result.values.toList()
    }

    /** 把一层覆盖（远端/用户）原地应用到已合并表。 */
    private fun applyLayer(
        target: LinkedHashMap<String, ModelCatalogEntry>,
        layer: List<ModelCatalogEntry>,
    ) {
        layer.forEach { entry ->
            val key = keyOf(entry.providerId, entry.modelId)
            if (entry.builtInRemoved) {
                target.remove(key)
                return@forEach
            }
            val base = target[key]
            target[key] = if (base == null) entry else base.mergeFields(entry)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun Model.applyCatalogEntry(entry: ModelCatalogEntry?): Model {
        if (entry == null) return this
        val mergedAbilities = abilities.toMutableSet().apply {
            if (entry.supportsTools == true) add(io.zer0.ai.core.ModelAbility.TOOL)
            if (entry.supportsTools == false) remove(io.zer0.ai.core.ModelAbility.TOOL)
            if (entry.supportsReasoning == true) add(io.zer0.ai.core.ModelAbility.REASONING)
            if (entry.supportsReasoning == false) remove(io.zer0.ai.core.ModelAbility.REASONING)
        }
        return copy(
            name = entry.displayName ?: name,
            contextWindow = entry.contextWindow ?: contextWindow,
            maxOutputTokens = entry.maxOutputTokens ?: maxOutputTokens,
            supportsVision = entry.supportsVision ?: supportsVision,
            supportsStreaming = entry.supportsStreaming ?: supportsStreaming,
            supportsVideo = entry.supportsVideo ?: supportsVideo,
            inputModalities = entry.inputModalities ?: inputModalities,
            outputModalities = entry.outputModalities ?: outputModalities,
            visionCapabilities = entry.visionCapabilities ?: visionCapabilities,
            abilities = mergedAbilities,
        )
    }

    private fun ModelCatalogEntry.toModel(providerId: String): Model = Model(
        id = modelId,
        name = displayName ?: modelId,
        providerId = providerId,
        contextWindow = contextWindow,
        maxOutputTokens = maxOutputTokens,
        supportsVision = supportsVision ?: false,
        supportsStreaming = supportsStreaming ?: true,
        supportsVideo = supportsVideo ?: false,
        inputModalities = inputModalities ?: setOf("text"),
        outputModalities = outputModalities ?: setOf("text"),
        visionCapabilities = visionCapabilities,
        abilities = buildSet {
            if (supportsTools == true) add(io.zer0.ai.core.ModelAbility.TOOL)
            if (supportsReasoning == true) add(io.zer0.ai.core.ModelAbility.REASONING)
        },
    )

    @Suppress("CyclomaticComplexMethod")
    private fun ModelCatalogEntry.mergeFields(overlay: ModelCatalogEntry): ModelCatalogEntry =
        copy(
            displayName = overlay.displayName ?: displayName,
            contextWindow = overlay.contextWindow ?: contextWindow,
            maxOutputTokens = overlay.maxOutputTokens ?: maxOutputTokens,
            supportsVision = overlay.supportsVision ?: supportsVision,
            supportsStreaming = overlay.supportsStreaming ?: supportsStreaming,
            supportsVideo = overlay.supportsVideo ?: supportsVideo,
            supportsTools = overlay.supportsTools ?: supportsTools,
            supportsReasoning = overlay.supportsReasoning ?: supportsReasoning,
            inputModalities = overlay.inputModalities ?: inputModalities,
            outputModalities = overlay.outputModalities ?: outputModalities,
            visionCapabilities = overlay.visionCapabilities ?: visionCapabilities,
            updatedAt = overlay.updatedAt.let { if (it > 0) it else updatedAt },
            userEdited = overlay.userEdited || userEdited,
            builtInRemoved = overlay.builtInRemoved || builtInRemoved,
        )

    private fun loadUserOverrides(): List<ModelCatalogEntry> {
        val cached = userCache
        if (cached != null) return cached.items
        if (!userOverridesFile.exists()) return emptyList()
        return runCatching {
            AppJson.decodeFromString(UserOverrides.serializer(), userOverridesFile.readText()).items
        }.onSuccess {
            userCache = UserOverrides(items = it)
        }.getOrElse { e ->
            Logger.w(TAG, "用户模型覆盖解析失败,按空处理", e)
            emptyList()
        }
    }

    private fun persistUserOverrides(overrides: UserOverrides) {
        runCatching {
            catalogDir.mkdirs()
            userOverridesFile.writeText(AppJson.encodeToString(UserOverrides.serializer(), overrides))
            userCache = overrides
        }.onFailure { e ->
            Logger.w(TAG, "用户模型覆盖写入失败", e)
        }
    }

    private fun keyOf(providerId: String, modelId: String) =
        "${providerId.trim().lowercase()}\u0000${modelId.trim().lowercase()}"

    private fun requirePositiveOrNull(value: Int?, field: String) {
        require(value == null || value > 0) { "$field must be positive when provided" }
    }

    companion object {
        private const val TAG = "ModelCatalogStore"

        /** 远端目录拉取超时（连接/读取，毫秒）。 */
        private const val REMOTE_TIMEOUT_MS = 10_000
    }
}

/** 远端目录拉取结果。 */
data class RemoteRefreshResult(
    /** 远端层是否启用（未配置 URL 时为 false，未发任何请求）。 */
    val enabled: Boolean,
    val ok: Boolean,
    val updated: Boolean,
    val message: String,
)

/** 远端目录状态摘要。 */
data class RemoteStatus(
    val enabled: Boolean,
    val url: String,
    val publishedAt: String,
    val itemCount: Int,
)
