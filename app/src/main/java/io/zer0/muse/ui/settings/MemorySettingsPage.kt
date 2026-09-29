package io.zer0.muse.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.zer0.memory.ticker.MemoryConfig
import io.zer0.muse.R
import io.zer0.muse.data.SettingsRepository
import io.zer0.muse.ui.common.icons.MuseIcons
import io.zer0.muse.ui.common.settings.SectionLabel
import io.zer0.muse.ui.common.settings.SettingsGroup
import io.zer0.muse.ui.common.settings.SettingsGroupDivider
import io.zer0.muse.ui.common.settings.SettingsItemRow
import io.zer0.muse.ui.common.settings.SettingsSliderRow
import io.zer0.muse.ui.common.settings.SettingsSwitchRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * v0.32: 记忆系统高级配置页。
 *
 * 把 MemoryTicker 的硬编码阈值提升为可配置:
 *  - token 预算(影响注入到 system prompt 的记忆量)
 *  - 衰减系数 λ(控制遗忘速度)
 *  - 命中加成(常提起的记忆不易消失)
 *  - 编译阈值(低于此分的记忆不进入 memory.md)
 *  - 遗忘倍率(1.0 正常 / 2.0 忘得快一倍)
 *
 * v1.0.51: 经验库开关移至聊天设置页;通知策略移至聊天设置页。
 * v2.x: 保持唤醒/开机自启移至 Agent 页「后台与可靠性」组。
 */
@Composable
fun MemorySettingsPage(onBack: () -> Unit, onOpenMemorySpace: () -> Unit = {}) {
    val settings: SettingsRepository = koinInject()
    val memoryConfig by settings.memoryConfigFlow.collectAsStateWithLifecycle(initialValue = MemoryConfig())
    // v2.x: 长期记忆总开关自助手资源页归位(记忆功能总控)
    val memoryEnabled by settings.memoryEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    // v2.x: 保持唤醒/开机自启已挪至 Agent 页「后台与可靠性」组;自定义压缩提示词已挪至辅助模型页。
    val scope = rememberCoroutineScope()

    // v1.78 (#19): 滑块防抖 — 拖动时只更新 localConfig,停止 400ms 后才持久化到 DataStore
    var localConfig by remember(memoryConfig) { mutableStateOf(memoryConfig) }
    LaunchedEffect(localConfig) {
        if (localConfig != memoryConfig) {
            delay(400)
            // v1.78 (H7): 保存前校验,拒绝 compileThreshold >= baseImportance 等危险配置
            val err = MemoryConfig.validate(localConfig)
            if (err != null) {
                io.zer0.muse.ui.common.feedback.MuseToast.show(err)
                localConfig = memoryConfig // 回退到上次合法值
            } else {
                scope.launch { settings.saveMemoryConfig(localConfig) }
            }
        }
    }

    SettingsSubPageScaffold(title = stringResource(R.string.settings_memory_page_title), onBack = onBack) {
        // ── 0. 记忆空间管理(P2-2) ──
        item { SectionLabel(stringResource(R.string.memory_space_entry)) }
        item {
            SettingsGroup {
                SettingsItemRow(
                    icon = null,
                    title = stringResource(R.string.memory_space_manage_title),
                    subtitle = "管理工作 / 生活 / 学习等多个记忆空间,切换时互不干扰",
                    onClick = onOpenMemorySpace,
                )
            }
        }
        // ── 1. 记忆系统 ──
        item { SectionLabel(stringResource(R.string.settings_memory_system_section)) }
        item {
            SettingsGroup {
                // v2.x: 长期记忆总开关自助手资源页归位
                SettingsSwitchRow(
                    icon = MuseIcons.atom,
                    title = stringResource(R.string.settings_assistant_memory_enable),
                    subtitle = stringResource(R.string.settings_assistant_memory_enable_subtitle),
                    checked = memoryEnabled,
                    onCheckedChange = { v -> scope.launch { settings.saveMemoryEnabled(v) } },
                )
                SettingsGroupDivider()
                SettingsSliderRow(
                    icon = MuseIcons.server,
                    iconContentDescription = stringResource(R.string.settings_memory_token_budget),
                    title = stringResource(R.string.settings_memory_token_budget),
                    subtitle = stringResource(R.string.settings_memory_token_budget_subtitle),
                    value = localConfig.tokenBudget.toFloat(),
                    valueRange = 500f..6000f,
                    steps = 10,
                    valueText = "${localConfig.tokenBudget}",
                    onValueChange = { v ->
                        localConfig = localConfig.copy(tokenBudget = v.toInt())
                    },
                )
                SettingsGroupDivider()
                SettingsSliderRow(
                    icon = MuseIcons.trendingDown,
                    iconContentDescription = stringResource(R.string.settings_memory_decay_rate),
                    title = stringResource(R.string.settings_memory_decay_rate_title),
                    subtitle = stringResource(R.string.settings_memory_decay_rate_subtitle),
                    value = localConfig.decayPerDay,
                    valueRange = 0.005f..0.06f,
                    steps = 10,
                    valueText = "%.3f".format(localConfig.decayPerDay),
                    onValueChange = { v ->
                        localConfig = localConfig.copy(decayPerDay = v)
                    },
                )
                SettingsGroupDivider()
                SettingsSliderRow(
                    icon = MuseIcons.bolt,
                    iconContentDescription = stringResource(R.string.settings_memory_hit_bonus),
                    title = stringResource(R.string.settings_memory_hit_bonus),
                    // v7: hitBonus 已接入 factScore / cutoffDays / applyDecay
                    subtitle = stringResource(R.string.settings_memory_hit_bonus_subtitle),
                    value = localConfig.hitBonus,
                    valueRange = 0f..15f,
                    steps = 14,
                    valueText = "%.1f".format(localConfig.hitBonus),
                    onValueChange = { v ->
                        localConfig = localConfig.copy(hitBonus = v)
                    },
                )
                SettingsGroupDivider()
                SettingsSliderRow(
                    icon = MuseIcons.arrowsVertical,
                    iconContentDescription = stringResource(R.string.settings_memory_compile_threshold),
                    title = stringResource(R.string.settings_memory_compile_threshold),
                    subtitle = stringResource(R.string.settings_memory_compile_threshold_subtitle, localConfig.baseImportance),
                    value = localConfig.compileThreshold,
                    valueRange = 1f..(localConfig.baseImportance - 0.5f),
                    steps = 17,
                    valueText = "%.1f".format(localConfig.compileThreshold),
                    onValueChange = { v ->
                        localConfig = localConfig.copy(compileThreshold = v)
                    },
                )
                SettingsGroupDivider()
                SettingsSliderRow(
                    icon = MuseIcons.gauge,
                    iconContentDescription = stringResource(R.string.settings_memory_forget_speed),
                    title = stringResource(R.string.settings_memory_forget_speed),
                    subtitle = stringResource(R.string.settings_memory_forget_speed_subtitle),
                    value = localConfig.forgetSpeed,
                    valueRange = 0.5f..3f,
                    steps = 24,
                    valueText = "%.1fx".format(localConfig.forgetSpeed),
                    onValueChange = { v ->
                        localConfig = localConfig.copy(forgetSpeed = v)
                    },
                )
                SettingsGroupDivider()
                // v1.78 (#21): 恢复默认按钮
                SettingsActionRow(
                    title = stringResource(R.string.settings_memory_restore_default),
                    subtitle = stringResource(R.string.settings_memory_restore_default_subtitle),
                    onClick = { localConfig = MemoryConfig() },
                )
            }
        }
    }
}

/**
 * v1.78 (#21): 可点击的操作行(如"恢复默认")。
 */
@Composable
private fun SettingsActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
