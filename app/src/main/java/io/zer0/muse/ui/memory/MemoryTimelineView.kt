package io.zer0.muse.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.zer0.muse.R
import io.zer0.muse.ui.common.form.MuseChip
import io.zer0.muse.ui.common.icons.MuseIcons
import io.zer0.muse.ui.theme.AmberWarmth
import io.zer0.muse.ui.theme.CoralWhisper
import io.zer0.muse.ui.theme.LavenderDream
import io.zer0.muse.ui.theme.MuseShapes
import io.zer0.muse.ui.theme.SageCalm
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Phase 2 2A: 记忆时间轴视图组件 — 筛选行、月份标题与事件卡片。
 *
 * v2.1.x 修复「点时间轴即崩溃」: 原 MemoryTimelineView 自带 LazyColumn,
 * 在 MemoryScreen 外层 LazyColumn 的 item 中渲染构成嵌套滚动, 内层 LazyColumn
 * 测得无限高约束, 抛 "Vertically scrollable component was measured with an
 * infinity maximum height constraints"。
 * 现将时间轴内容拍平到外层 LazyListScope(见 MemoryScreen.memoryStreamItems),
 * 本文件只保留无滚动的子组件。
 */

/** 时间轴内部筛选行(全部 / 事实 / 摘要 / 里程碑)。 */
@Composable
internal fun MemoryTimelineFilterRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val filters = listOf(
        "all" to stringResource(R.string.memory_timeline_filter_all),
        "fact" to stringResource(R.string.memory_timeline_filter_fact),
        "summary" to stringResource(R.string.memory_timeline_filter_summary),
        "milestone" to stringResource(R.string.memory_timeline_filter_milestone),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { (key, label) ->
            MuseChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = label,
            )
        }
    }
}

@Composable
internal fun MonthHeader(month: String) {
    Text(
        text = month,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

@Composable
internal fun TimelineEventCard(item: TimelineItem) {
    val nodeColor = when (item.importance) {
        2 -> CoralWhisper
        1 -> LavenderDream
        else -> SageCalm
    }
    val nodeIcon = when (item.importance) {
        2 -> MuseIcons.star
        else -> if (item.importance >= 1) MuseIcons.circle else MuseIcons.circle
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
    ) {
        // 时间轴节点
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp),
        ) {
            Icon(
                imageVector = nodeIcon,
                // A11Y-05: 状态不只靠颜色传达 — 补重要性文本描述
                contentDescription = when (item.importance) {
                    2 -> stringResource(R.string.memory_importance_critical)
                    1 -> stringResource(R.string.memory_importance_important)
                    else -> stringResource(R.string.memory_importance_normal)
                },
                tint = nodeColor,
                modifier = Modifier.size(12.dp),
            )
            // 竖向连接线
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        Spacer(Modifier.width(8.dp))
        // 事件卡片
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MuseShapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 类型标签 + 重要性星标
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 来源标签
                    Surface(
                        shape = MuseShapes.small,
                        color = nodeColor.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = item.source,
                            style = MaterialTheme.typography.labelSmall,
                            color = nodeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    // 重要性星标
                    if (item.importance > 0) {
                        Row {
                            repeat(item.importance) {
                                Icon(
                                    MuseIcons.star,
                                    contentDescription = null,
                                    tint = AmberWarmth,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }
                // 内容(最多 3 行)
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                // 时间
                val timeStr = remember(item.createdAt) {
                    try {
                        val dt = Instant.parse(item.createdAt).atZone(ZoneId.systemDefault())
                        DateTimeFormatter.ofPattern("MM-dd HH:mm").format(dt)
                    } catch (_: Exception) {
                        ""
                    }
                }
                if (timeStr.isNotBlank()) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

/**
 * 简化的时间轴条目数据类(在 ViewModel 中由 MemoryItem 映射而来)。
 */
data class TimelineItem(
    val id: String,
    val content: String,
    val source: String,
    val importance: Int,
    val createdAt: String?,
    val tags: List<String> = emptyList(),
)
