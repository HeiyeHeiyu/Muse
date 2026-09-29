package io.zer0.muse.data.provider

import io.zer0.ai.ProviderRegistry
import io.zer0.ai.core.Model
import io.zer0.ai.core.ModelListCache
import io.zer0.ai.core.ModelRegistry
import io.zer0.ai.core.ProviderConfig
import io.zer0.common.resultOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v2.x: 供应商模型清单拉取管线(引导页 / 供应商编辑页共用口径)。
 *
 * 流程: 缓存优先(5 分钟 TTL) → URL 多策略补全(用户漏填/多填 /v1 的常见场景) →
 * 拉取 → [ModelRegistry.enrich] 能力富化(abilities / modalities / contextWindow) →
 * 写回缓存。无论预置供应商还是自定义 OpenAI 兼容端点,都走同一条链。
 *
 * 401/403 立即失败不做 URL 回退(凭证问题换路径也没用)。
 */
object ProviderModelFetcher {

    sealed class Outcome {
        /** 成功: 已富化的模型清单(非空) + 实际命中的配置(含修正后的 baseUrl)。 */
        data class Success(val models: List<Model>, val usedConfig: ProviderConfig) : Outcome()

        /** 失败;[kind] 供调用方映射本地化文案,[message] 为原始诊断信息。 */
        data class Failure(val kind: FailureKind, val message: String) : Outcome()
    }

    enum class FailureKind { AUTH, EMPTY, NETWORK, OTHER }

    /** 拉取并富化模型清单。全策略失败时返回 [Outcome.Failure]。 */
    suspend fun fetch(config: ProviderConfig, forceFresh: Boolean = false): Outcome = withContext(Dispatchers.IO) {
        // 1) 缓存优先(5 分钟 TTL)— 与供应商编辑页共用缓存,避免重复打上游
        if (!forceFresh) {
            val cached = ModelListCache.get(config, forceFresh = false)
            if (cached != null) {
                return@withContext if (cached.isEmpty()) {
                    Outcome.Failure(FailureKind.EMPTY, "cached model list is empty")
                } else {
                    Outcome.Success(cached.map { ModelRegistry.enrich(it) }, config)
                }
            }
        }

        // 2) URL 多策略补全 — 覆盖用户漏填/多填 /v1 的场景
        val base = config.baseUrl.trimEnd('/')
        val urlsToTry = mutableListOf<String>()
        if (base.isNotBlank()) {
            urlsToTry.add(base)
            if (!base.endsWith("/v1") && !base.endsWith("/v1beta")) {
                urlsToTry.add("$base/v1")
            } else if (base.endsWith("/v1")) {
                urlsToTry.add(base.removeSuffix("/v1"))
            }
        } else {
            urlsToTry.add(config.baseUrl)
        }

        var lastError: String? = null
        var sawEmpty = false
        for (url in urlsToTry) {
            val cfg = config.copy(baseUrl = url)
            val result = resultOf { ProviderRegistry.create(cfg).listModels(cfg) }
            val models = result.getOrNull()
            if (models != null) {
                if (models.isEmpty()) {
                    sawEmpty = true
                    continue
                }
                // 3) 逐个富化 — 自动推导 abilities / modalities / contextWindow,
                //    保证视觉/推理三态判定在引导阶段就已生效
                val enriched = models.map { ModelRegistry.enrich(it) }
                ModelListCache.put(cfg, models)
                return@withContext Outcome.Success(enriched, cfg)
            }
            var failMsg = "unknown"
            result.onError { msg, t -> failMsg = t?.message ?: msg }
            if (failMsg.contains("401") || failMsg.contains("403")) {
                return@withContext Outcome.Failure(FailureKind.AUTH, failMsg)
            }
            lastError = failMsg
        }

        // 全策略失败: 空清单 vs 网络/其他
        if (sawEmpty) {
            Outcome.Failure(FailureKind.EMPTY, "upstream returned no models")
        } else {
            Outcome.Failure(FailureKind.NETWORK, lastError ?: "unknown error")
        }
    }
}
