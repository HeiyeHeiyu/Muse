package io.zer0.muse.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.zer0.muse.R
import io.zer0.muse.data.SettingsRepository
import io.zer0.muse.data.routing.UtilityModelBinding
import io.zer0.muse.data.routing.UtilityTier
import io.zer0.muse.ui.ModelSwitchSheet
import io.zer0.muse.ui.common.icons.MuseIcons
import io.zer0.muse.ui.common.settings.ChevronRight
import io.zer0.muse.ui.common.settings.SectionLabel
import io.zer0.muse.ui.common.settings.SettingsGroup
import io.zer0.muse.ui.common.settings.SettingsGroupDivider
import io.zer0.muse.ui.common.settings.SettingsItemRow
import io.zer0.muse.ui.theme.MuseShapes
import io.zer0.muse.ui.theme.huge
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * v2.x: 辅助模型设置页(原"任务路由"页重构)。
 *
 * 三档模型统一收敛后台辅助任务的模型选择:
 *  - 小工具模型: 标题生成、轻量分类、对话工具轮、封面 prompt、渠道摘要等短任务;
 *  - 大工具模型: 上下文压缩、记忆提取、活动摘要、任务拆解(子代理)等需要理解力的场景;
 *  - 视觉辅助模型: 图片理解 / PDF OCR,点击进入独立页配置(模型选择 + 探针测试)。
 *
 * 级联规则: 大工具留空 → 复用小工具;小工具留空 → 回退主对话模型。
 * 绑定均带 Provider,精确命中所选渠道(修复跨渠道同 id 串台的历史缺陷)。
 *
 * 说明: 原"按对话内容自动分流主模型"的任务分流功能已按用户要求整体移除
 * (安卓端场景过重、使用门槛高);TaskRoutingConfig 存储结构保留以兼容历史数据。
 */
@Composable
fun TaskRoutingSettingsPage(
    onBack: () -> Unit,
    /** v2.x: 打开视觉辅助独立页(辅助模型区块的视觉入口)。 */
    onOpenVision: () -> Unit = {},
) {
    val settings: SettingsRepository = koinInject()
    val providers by settings.providersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeProviderId by settings.activeProviderIdFlow.collectAsStateWithLifecycle(initialValue = null)
    // v2.x: 辅助模型(小工具/大工具)绑定状态 + 视觉模型展示
    val utilityBinding by settings.utilityModelBindingFlow.collectAsStateWithLifecycle(initialValue = null)
    val utilityLargeBinding by settings.utilityLargeModelBindingFlow.collectAsStateWithLifecycle(initialValue = null)
    val visionModelId by settings.visionModelIdFlow.collectAsStateWithLifecycle(initialValue = null)
    val visionProviderId by settings.visionProviderIdFlow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var editingProviderId by remember { mutableStateOf<String?>(null) }
    // v2.x: 辅助模型编辑态(null=无;否则正在编辑的档位)
    var editingUtilityTier by remember { mutableStateOf<UtilityTier?>(null) }
    // v2.x: 辅助模型显示名(绑定带 provider 时优先精确命中)
    val utilitySmallLabel = remember(utilityBinding, providers) {
        utilityBinding?.let { b ->
            providers.firstOrNull { it.id == b.providerId }?.models?.firstOrNull { it.id == b.modelId }?.name
                ?: providers.flatMap { it.models }.firstOrNull { it.id == b.modelId }?.name
        }
    }
    val utilityLargeLabel = remember(utilityLargeBinding, providers) {
        utilityLargeBinding?.let { b ->
            providers.firstOrNull { it.id == b.providerId }?.models?.firstOrNull { it.id == b.modelId }?.name
                ?: providers.flatMap { it.models }.firstOrNull { it.id == b.modelId }?.name
        }
    }
    val visionLabel = remember(visionModelId, visionProviderId, providers) {
        if (visionModelId.isNullOrBlank()) {
            null
        } else {
            providers.firstOrNull { it.id == visionProviderId }?.models?.firstOrNull { it.id == visionModelId }?.name
                ?: providers.flatMap { it.models }.firstOrNull { it.id == visionModelId }?.name
        }
    }
    val utilitySmallInheritText = stringResource(R.string.settings_agent_tool_model_not_set_inherit)
    val utilityLargeInheritText = stringResource(R.string.settings_agent_utility_large_not_set_inherit)

    SettingsSubPageScaffold(
        title = stringResource(R.string.settings_task_routing_aux_section),
        onBack = onBack,
    ) {
        item { SectionLabel(stringResource(R.string.settings_task_routing_aux_models_label)) }
        item {
            SettingsGroup(modifier = Modifier.padding(top = 4.dp)) {
                // 小工具模型
                SettingsItemRow(
                    icon = MuseIcons.wrench,
                    title = stringResource(R.string.settings_agent_tool_model_title),
                    subtitle = stringResource(R.string.settings_task_routing_utility_small_desc),
                    onClick = {
                        editingProviderId = utilityBinding?.providerId ?: activeProviderId
                        editingUtilityTier = UtilityTier.SMALL
                    },
                ) {
                    ModelPill(
                        text = utilitySmallLabel ?: utilitySmallInheritText,
                        bound = utilityBinding != null,
                    )
                }
                SettingsGroupDivider()
                // 大工具模型
                SettingsItemRow(
                    icon = MuseIcons.bolt,
                    title = stringResource(R.string.settings_agent_subagent_model_title),
                    subtitle = stringResource(R.string.settings_task_routing_utility_large_desc),
                    onClick = {
                        editingProviderId = utilityLargeBinding?.providerId ?: activeProviderId
                        editingUtilityTier = UtilityTier.LARGE
                    },
                ) {
                    ModelPill(
                        text = utilityLargeLabel ?: utilityLargeInheritText,
                        bound = utilityLargeBinding != null,
                    )
                }
                SettingsGroupDivider()
                // 视觉辅助模型(跳独立页配置)
                SettingsItemRow(
                    icon = MuseIcons.eye,
                    title = stringResource(R.string.settings_vision_title),
                    subtitle = visionLabel ?: stringResource(R.string.settings_task_routing_vision_unset),
                    onClick = onOpenVision,
                ) {
                    ChevronRight()
                }
            }
        }
    }

    // v2.x: 辅助模型编辑弹窗(小工具/大工具共用,ModelSwitchSheet 带 Provider 选择)
    editingUtilityTier?.let { tier ->
        val currentBinding = when (tier) {
            UtilityTier.SMALL -> utilityBinding
            UtilityTier.LARGE -> utilityLargeBinding
            else -> null
        }
        ModelSwitchSheet(
            providers = providers,
            activeProviderId = editingProviderId ?: activeProviderId,
            selectedModelId = currentBinding?.modelId,
            onPickProvider = { providerId -> editingProviderId = providerId },
            onPickModel = { modelId ->
                val providerId = editingProviderId ?: activeProviderId
                val binding = if (modelId != null && providerId != null) {
                    UtilityModelBinding(providerId = providerId, modelId = modelId)
                } else {
                    null
                }
                scope.launch { saveUtilityBinding(settings, tier, binding) }
                editingUtilityTier = null
                editingProviderId = null
            },
            // 辅助模型需要"显式绑定"与"未绑定(级联)"可区分;
            // 普通聊天的 ModelSwitchSheet 仍保留原来的清除绑定语义。
            onPickDefaultModel = {
                val providerId = editingProviderId ?: activeProviderId
                val defaultModelId = providers.firstOrNull { it.id == providerId }
                    ?.models?.firstOrNull()?.id
                val binding = if (providerId != null && defaultModelId != null) {
                    UtilityModelBinding(providerId = providerId, modelId = defaultModelId)
                } else {
                    null
                }
                scope.launch { saveUtilityBinding(settings, tier, binding) }
                editingUtilityTier = null
                editingProviderId = null
            },
            onRefreshModels = { /* 模型列表由 Provider 设置页维护 */ },
            isFetchingModels = false,
            fetchModelsError = null,
            onDismiss = {
                editingUtilityTier = null
                editingProviderId = null
            },
        )
    }
}

/** v2.x: 按档位保存辅助模型绑定(null=清除绑定,走级联)。 */
private suspend fun saveUtilityBinding(
    settings: SettingsRepository,
    tier: UtilityTier,
    binding: UtilityModelBinding?,
) {
    when (tier) {
        UtilityTier.SMALL -> settings.saveUtilityModelBinding(binding)
        UtilityTier.LARGE -> settings.saveUtilityLargeModelBinding(binding)
        else -> Unit
    }
}

/** 辅助模型的绑定状态胶囊:已绑定显示模型名(主色浅底),未绑定显示"沿用/复用"提示(灰底)。 */
@Composable
private fun ModelPill(text: String, bound: Boolean) {
    Surface(
        shape = MuseShapes.huge,
        color = if (bound) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (bound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 132.dp),
            )
            Spacer(Modifier.width(3.dp))
            Icon(
                imageVector = MuseIcons.arrowRight,
                contentDescription = null,
                tint = if (bound) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                },
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
