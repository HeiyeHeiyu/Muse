package io.zer0.muse.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Muse 高程/阴影语义刻度 (CMP-01: 唯一数据源,tonalElevation 与 shadowElevation 共用)。
 *
 * 各档用途:
 *  - [none]:   0 — 无层次(默认表面)
 *  - [micro]:  0.5 — 紧贴表面,仅暗示层次
 *  - [low]:    1 — 列表项 / 卡片默认
 *  - [card]:   1 — 卡片(与 low 同值,语义区分)
 *  - [medium]: 2 — 输入岛 / 浮动条 shadowElevation
 *  - [high]:   4 — 选中项 / 强调卡片
 *  - [modal]:  8 — 对话框 / BottomSheet
 *  - [floating]: 12 — v2.x: 浮层 / 大面板(补回大档位)
 *  - [overlay]:  24 — v2.x: 全屏模态 / 悬浮大元素(补回大档位)
 *
 * 历史两套同名不同值(MuseShadow.low=2/high=12/modal=24)已合并删除,统一走本刻度。
 */
object MuseElevation {
    val none: Dp = 0.dp
    val micro: Dp = 0.5.dp
    val low: Dp = 1.dp
    val card: Dp = 1.dp
    val medium: Dp = 2.dp
    val high: Dp = 4.dp
    val modal: Dp = 8.dp

    /** v2.x: 浮层 / 大面板 — 12dp(补回大档位,供菜单/面板类容器使用)。 */
    val floating: Dp = 12.dp

    /** v2.x: 全屏模态 / 悬浮大元素 — 24dp(补回大档位,供全屏覆盖层使用)。 */
    val overlay: Dp = 24.dp
}
