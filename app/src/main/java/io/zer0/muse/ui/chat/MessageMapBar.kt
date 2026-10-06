@file:Suppress("FunctionNaming")

package io.zer0.muse.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.zer0.ai.core.MessageRole
import io.zer0.ai.core.UIMessage
import io.zer0.muse.ui.common.form.railTargetFor
import io.zer0.muse.ui.theme.MuseAnimation
import io.zer0.muse.ui.theme.MuseMotion
import io.zer0.muse.ui.theme.MusePaddings
import io.zer0.muse.ui.theme.MuseShapes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A6: 长会话消息地图 — 聊天区右侧隐藏式导航。
 *
 * 长会话(消息数 ≥ [MESSAGE_MAP_MIN_MESSAGES])时保留右缘透明热区:
 * - 平时完全不可见,不因普通聊天滚动自动出现
 * - 手指在最右侧上下拖动时浮出半透明胶囊轨道
 * - 拖动按**纵向位置比例连续定位**,不是"逐条消息跳格"(fix: 旧实现只能落在消息边界)
 * - 松手后短暂停留,随后自动淡出
 * - 桌面端也使用同一拖动热区,不改变消息数据和分页逻辑
 *
 * 遮挡修复(fix): 热区收窄为贴右缘的细条,且不覆盖消息底部操作行;操作行(复制/重试等)
 * 自身也向内让位,避免最右侧按钮落在热区之下收不到点击。
 *
 * 跨会话滚动位置保留由 ChatViewModel v1.45 的 listState 缓存负责,本组件只管导航。
 */
internal const val MESSAGE_MAP_MIN_MESSAGES = 25

/** 轨道可视宽度。 */
private val TRACK_WIDTH = 22.dp

/**
 * 触摸热区宽度（= 导航条占用的右侧空间）。
 *
 * fix: 从 36.dp 收窄到 24.dp —— 旧热区宽到盖住消息底部动作行最右按钮（MuseTactileButton 48dp
 * 触摸目标）。Compose 命中测试是“最高层节点独占”，热区盖住哪里，那一片的按钮就完全收不到事件。
 * 收窄只是一半，另一半是让消息列表在 [MESSAGE_MAP_RESERVED_WIDTH] 上避让（见 ChatScreen）。
 */
internal val MESSAGE_MAP_TOUCH_WIDTH = 24.dp

/** 消息列表右侧为导航条预留的总宽度（热区 + 呼吸间隙），保证按钮永不与热区重叠。 */
internal val MESSAGE_MAP_RESERVED_WIDTH = MESSAGE_MAP_TOUCH_WIDTH + 4.dp

@Suppress("CyclomaticComplexMethod")
@Composable
internal fun MessageMapBar(
    messages: List<UIMessage>,
    listState: LazyListState,
    messageStartIndex: Int,
    modifier: Modifier = Modifier,
) {
    val total = messages.size
    if (total == 0) return
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var showBar by remember { mutableStateOf(false) }
    var activeIndex by remember { mutableStateOf<Int?>(null) }
    val scrollJob = remember { mutableStateOf<Job?>(null) }

    // 普通列表滚动不会触发显示;只在右缘拖动结束后负责自动收起。
    LaunchedEffect(isDragging, showBar) {
        if (!isDragging && showBar) {
            delay(900)
            showBar = false
            activeIndex = null
        }
    }

    val barAlpha by animateFloatAsState(
        targetValue = if (showBar) 1f else 0f,
        animationSpec = MuseMotion.tween(MuseAnimation.FAST_NORMAL_MS),
        label = "mapbar-alpha",
    )

    /**
     * fix: 连续定位。
     *
     * 旧实现 `scrollToItem(messageStartIndex + index)` 只能把某条消息顶到视口顶部,
     * 表现为“必须按上一条/下一条跳一格”。现在改为两段式:
     *  1. 按拖动比例算出目标条目(粗定位): target = fraction × (total - 1);
     *  2. 再用平均条目高度做**亚条条目**微调(细定位),让拖动与内容位置连续对应。
     * 平均高度取当前可见条目的均值,拿不到时回退为视口高/可见数,保证无测量时也能工作。
     */
    fun jumpToFraction(fraction: Float, viewportHeightPx: Float) {
        val target = railTargetFor(fraction, total)
        val targetIndex = target.index
        val subFraction = target.subFraction
        val visible = listState.layoutInfo.visibleItemsInfo
        val avgItemPx =
            if (visible.isNotEmpty()) {
                visible.sumOf { it.size }.toFloat() / visible.size
            } else if (viewportHeightPx > 0f) {
                viewportHeightPx / total.coerceAtLeast(1)
            } else {
                0f
            }
        scrollJob.value?.cancel()
        scrollJob.value = scope.launch {
            listState.scrollToItem(messageStartIndex + targetIndex)
            if (subFraction > 0f && avgItemPx > 0f) {
                listState.scrollBy(subFraction * avgItemPx)
            }
        }
    }

    val messages = messages
    // 热区收窄到贴右缘,不再盖住消息底部动作行。
    Box(
        modifier =
        modifier
            .width(MESSAGE_MAP_TOUCH_WIDTH)
            .fillMaxHeight()
            .pointerInput(total, messageStartIndex) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        showBar = true
                        val fraction = (offset.y / size.height).coerceIn(0f, 1f)
                        activeIndex = railTargetFor(fraction, total).index
                        jumpToFraction(fraction, size.height.toFloat())
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        isDragging = true
                        showBar = true
                        val fraction = (change.position.y / size.height).coerceIn(0f, 1f)
                        val index = railTargetFor(fraction, total).index
                        if (index != activeIndex) activeIndex = index
                        jumpToFraction(fraction, size.height.toFloat())
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                )
            },
    ) {
        MessageMapTrack(
            messages = messages,
            listState = listState,
            messageStartIndex = messageStartIndex,
            alpha = barAlpha,
            modifier = Modifier.align(Alignment.CenterEnd),
        )

        val previewMessage = activeIndex?.let { messages.getOrNull(it) }
        if (showBar && previewMessage != null) {
            MessageMapTooltip(
                msg = previewMessage,
                modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-(TRACK_WIDTH + 4.dp))),
            )
        }
    }
}

/** 轨道 Canvas：只负责绘制，与拖动/状态解耦，避免主函数过长。 */
@Composable
private fun MessageMapTrack(
    messages: List<UIMessage>,
    listState: LazyListState,
    messageStartIndex: Int,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val total = messages.size
    val visibleInfo = listState.layoutInfo.visibleItemsInfo
    val first = (visibleInfo.firstOrNull()?.index ?: listState.firstVisibleItemIndex)
        .let { (it - messageStartIndex).coerceIn(0, total - 1) }
    val last = (visibleInfo.lastOrNull()?.index ?: listState.firstVisibleItemIndex)
        .let { (it - messageStartIndex).coerceIn(0, total - 1) }

    val userColor = MaterialTheme.colorScheme.primaryContainer
    val assistantColor = MaterialTheme.colorScheme.secondary
    val otherColor = MaterialTheme.colorScheme.outlineVariant
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.84f)
    val windowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)

    Canvas(
        modifier =
        modifier
            .width(TRACK_WIDTH)
            .fillMaxHeight()
            .alpha(alpha),
    ) {
        drawMessageMap(
            messages = messages,
            firstVisible = first,
            lastVisible = last,
            total = total,
            barWidth = size.width,
            barHeight = size.height,
            userColor = userColor,
            assistantColor = assistantColor,
            otherColor = otherColor,
            trackColor = trackColor,
            windowColor = windowColor,
        )
    }
}

/** 消息地图绘制:胶囊轨道、消息密度标记和当前窗口滑块。 */
@Suppress("LongParameterList")
private fun DrawScope.drawMessageMap(
    messages: List<UIMessage>,
    firstVisible: Int,
    lastVisible: Int,
    total: Int,
    barWidth: Float,
    barHeight: Float,
    userColor: Color,
    assistantColor: Color,
    otherColor: Color,
    trackColor: Color,
    windowColor: Color,
) {
    val trackRadius = CornerRadius(barWidth / 2f)
    drawRoundRect(
        color = trackColor,
        topLeft = Offset.Zero,
        size = Size(barWidth, barHeight),
        cornerRadius = trackRadius,
    )
    drawRoundRect(
        color = otherColor.copy(alpha = 0.45f),
        topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
        size = Size(barWidth - 1.dp.toPx(), barHeight - 1.dp.toPx()),
        cornerRadius = CornerRadius((barWidth - 1.dp.toPx()) / 2f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
    )

    // 每条消息压缩为轨道中央短标记,避免旧版满屏散落的细横线。
    val markerWidth = 5.dp.toPx().coerceAtMost(barWidth - 8.dp.toPx())
    val markerHeight = 2.dp.toPx()
    messages.forEachIndexed { index, message ->
        val y = (index + 0.5f) / total * barHeight
        val color = when (message.role) {
            MessageRole.USER -> userColor
            MessageRole.ASSISTANT -> assistantColor
            else -> otherColor
        }
        drawRoundRect(
            color = color.copy(alpha = 0.86f),
            topLeft = Offset((barWidth - markerWidth) / 2f, y - markerHeight / 2f),
            size = Size(markerWidth, markerHeight),
            cornerRadius = CornerRadius(markerHeight / 2f),
        )
    }

    // 当前可见窗口使用亮色滑块,拖动时能看出当前位置和覆盖范围。
    val windowY1 = (firstVisible + 0.5f) / total * barHeight
    val windowY2 = (lastVisible + 0.5f) / total * barHeight
    drawRoundRect(
        color = windowColor,
        topLeft = Offset(1.dp.toPx(), windowY1),
        size = Size(
            barWidth - 2.dp.toPx(),
            (windowY2 - windowY1).coerceAtLeast(10.dp.toPx()),
        ),
        cornerRadius = CornerRadius((barWidth - 2.dp.toPx()) / 2f),
    )
}

/** A6: 消息地图拖动预览浮层 — 显示该位置消息前 40 字符。 */
@Composable
private fun MessageMapTooltip(msg: UIMessage, modifier: Modifier = Modifier) {
    val previewText = msg.content
        .replace('\n', ' ')
        .trim()
        .take(40)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
        shape = MuseShapes.medium,
        tonalElevation = 2.dp,
        modifier = modifier.widthIn(max = 220.dp),
    ) {
        Text(
            text = previewText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = MusePaddings.tightGap,
                vertical = MusePaddings.tinyGap,
            ),
        )
    }
}
