package io.zer0.muse.ui.common.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.zer0.muse.ui.theme.MuseActionColors
import io.zer0.muse.ui.theme.MuseAnimation
import io.zer0.muse.ui.theme.MuseMotion
import io.zer0.muse.ui.theme.MusePaddings
import io.zer0.muse.ui.theme.MuseShapes
import io.zer0.muse.ui.theme.semiLarge

/**
 * iOS 风格选择胶囊组件 — 替代 Material3 FilterChip / AssistChip。
 *
 * 视觉特征:
 *  - 选中态:primary 色背景 + onPrimary 文本 + SemiBold 字重
 *  - 未选中:surfaceVariant 半透明(0.5 alpha)+ onSurfaceVariant 文本 + Medium 字重
 *  - 圆角 [MuseShapes.semiLarge],无 ripple(Surface onClick 默认无 ripple)
 *  - 可选 leadingIcon / trailingIcon(如关闭按钮 X)
 *  - 不可用状态:alpha 0.38 + 禁用点击(Surface enabled=false)
 *  - CMP-04: 补选中语义(role=Checkbox + selected)与 48dp 最小触控
 *
 * 用于:标签筛选、类别切换、可关闭的标签、提示词模板选择等场景。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuseChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    // UI-FIX A: 选中=实心黑底白字；未选中=不透明中性底。
    // 旧实现未选中用 surfaceVariant@50% 半透明，压在内容上像一层遮罩，已取消。
    // v2.x: 交互动效 — 按压缩放 + 选中态颜色弹性过渡(自绘反馈,无涟漪)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = MuseMotion.tween(MuseAnimation.FAST_MS),
        label = "chipPress",
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) MuseActionColors.container else MuseActionColors.neutralContainer,
        animationSpec = MuseMotion.tween(MuseAnimation.TACTILE_MS),
        label = "chipBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MuseActionColors.content else MuseActionColors.neutralContent,
        animationSpec = MuseMotion.tween(MuseAnimation.TACTILE_MS),
        label = "chipContent",
    )
    Surface(
        shape = MuseShapes.semiLarge,
        color = bgColor,
        contentColor = contentColor,
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .alpha(if (enabled) 1f else 0.38f)
            // CMP-04/A11Y-01: 选中语义 + 48dp 最小触控(TalkBack 可读"已选中")
            .heightIn(min = 48.dp)
            .semantics {
                this.selected = selected
                role = Role.Checkbox
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MusePaddings.tightGap),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = MusePaddings.itemGap, vertical = MusePaddings.labelVerticalGap),
        ) {
            if (leadingIcon != null) leadingIcon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            if (trailingIcon != null) trailingIcon()
        }
    }
}
