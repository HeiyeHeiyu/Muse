package io.zer0.muse.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Muse 设计令牌 — 长文阅读节奏(MuseMarkdownRhythm,v2.x)。
 *
 * E3 阶段 5 · 聊天长内容排版校准:Markdown 长回复的「代码块 / 表格 / 引用块 / 标题 /
 * 段落间距」在双平面体系下的垂直节奏。此前这些间距散落在 `MarkdownText` 各处
 * (8 / 6 / 4 / 2 dp 裸值),既互不对齐,标题与正文的层级也靠"碰巧的数值差"表达。
 *
 * 这里把阅读节奏收敛成一档令牌,由 `markdownBlockGap` 统一驱动(空行块只贡献段落间距,
 * 不再各自 `Spacer`)。节奏取向对齐 ColorOS 阅读类页面:
 *  - **段落松**:段落之间留出稳定的呼吸位;
 *  - **区块匀**:代码 / 表格 / 引用等独立结构块上下留白一致,与正文拉开对比;
 *  - **标题贴身**:标题上留白明显大于下留白,让它贴近所引导的正文(接近性原则)。
 *
 * 圆角不在此定义 —— 代码块 / 表格容器统一走 [MuseShapes.medium](12dp,设计稿"代码块"档),
 * 分隔线走 [MusePaddings.dividerThickness] 与 `onSurface@13%` 线色(对齐 MuseDivider)。
 */
object MuseMarkdownRhythm {
    /** 段落之间的垂直间距(原文用空行分隔段落时)。 */
    val paragraphGap = 10.dp

    /**
     * 结构块(代码 / 表格 / 引用 / 公式 / 分隔线)与上下文字的间距。
     * 大于 [paragraphGap]:独立区块在正文流里"自成一段",需要更清楚的边界。
     */
    val blockGap = 16.dp

    /** 标题上方留白。 */
    val headingSpaceAbove = 18.dp

    /**
     * 标题下方留白(小于 [headingSpaceAbove])。
     * 接近性原则:标题与它引导的正文更近,与前一段内容更远,读者一眼能看出归属。
     */
    val headingSpaceBelow = 8.dp

    /** 列表项之间的紧凑间距(即便原文条目之间夹了空行,也保持列表的紧凑观感)。 */
    val listItemGap = 2.dp

    /** 代码块 / 表格容器内边距。 */
    val blockInner = PaddingValues(horizontal = 12.dp, vertical = 10.dp)

    /** 表格表头单元格内边距。 */
    val tableCellHeader = PaddingValues(horizontal = 10.dp, vertical = 8.dp)

    /** 表格数据单元格内边距。 */
    val tableCellBody = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
}
