package io.zer0.muse.ui.theme

/**
 * v2.x (C9): Muse 断点令牌 — 窗口宽度分级阈值(对齐 Material 3 WindowSizeClass 规范值)。
 *
 * 由 [io.zer0.muse.ui.common.media.rememberWindowWidthClass] 与后续折叠屏/平板适配引用,
 * 消除散落的 600 / 840 魔法数。
 */
object MuseBreakpoints {
    /** Medium 起始:平板竖屏 / 手机横屏(≥600dp)。 */
    const val MEDIUM_DP = 600

    /** Expanded 起始:平板横屏 / 桌面(≥840dp)。 */
    const val EXPANDED_DP = 840
}
