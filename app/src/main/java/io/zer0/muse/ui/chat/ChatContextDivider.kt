// Composable 按 Compose 约定用 PascalCase 命名，与 detekt 的 kotlin 命名规则冲突；
// 沿用项目既有做法（见 MusePopover.kt / MuseFloatingActionMenu.kt）在文件级豁免。
@file:Suppress("FunctionNaming")

package io.zer0.muse.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.zer0.muse.R
import io.zer0.muse.ui.common.icons.MuseIcons
import io.zer0.muse.ui.theme.MusePaddings

/**
 * 对话流里的"上下文已压缩"分隔线。
 *
 * ## 它表达什么
 *
 * 这条线以上的对话已经并入摘要：**模型不再逐条看到它们**。界面仍然保留原文（点一下可展开），
 * 因为用户需要能回看自己说过什么——但默认收起，让压缩后的会话看起来像"从新上下文继续"，
 * 而不是"中间插了一段摘要"。
 *
 * ## 与摘要消息的区别
 *
 * 真正发给模型的摘要是 `[COMPRESSED]` 开头的 SYSTEM 消息（见 ContextCheckpointMerge）；
 * 本条只是界面锚点，不参与任何请求组装。
 *
 * @param totalCovered 累计并入摘要的条数；0 表示未知（旧检查点），此时不显示条数
 * @param expanded 老消息当前是否展开
 * @param onToggle 点击切换展开/收起
 */
@Composable
fun ChatContextDivider(totalCovered: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val title = if (totalCovered > 0) {
        stringResource(R.string.chat_context_divider_title_count, totalCovered)
    } else {
        stringResource(R.string.chat_context_divider_title)
    }
    val hint = stringResource(
        if (expanded) {
            R.string.chat_context_divider_hint_expanded
        } else {
            R.string.chat_context_divider_hint_collapsed
        },
    )
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = MusePaddings.screen, vertical = MusePaddings.messageGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MusePaddings.tinyGap),
            modifier = Modifier.padding(vertical = MusePaddings.tinyGap),
        ) {
            Icon(
                imageVector = MuseIcons.gitMerge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(14.dp),
            )
            // 文案长度随语言与条数变化（德语/俄语明显更长），给足宽度并限行，
            // 避免在窄屏放大字号下逐字换行。
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
