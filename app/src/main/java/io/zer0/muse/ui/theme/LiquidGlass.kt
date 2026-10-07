@file:Suppress("MatchingDeclarationName")

package io.zer0.muse.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/**
 * v2.5.0: 液态玻璃（Liquid Glass）效果中枢。
 *
 * 用户反馈定稿设计：
 * - **风格**：水玻璃(轻透) / 磨砂玻璃(厚乳)，参考 Operit AI 的双玻璃方案
 * - **强度**：0f..1f 连续值，设置页滑杆调节（旧三档自动迁移：低→0.25 中→0.5 高→0.85）
 * - **范围**：全局 —— MainActivity 持有单一 HazeState，NavGraph 内容作为模糊源，
 *   任意页面/表面挂 hazeEffect 即可获得玻璃质感（顶栏岛、输入岛、按钮、气泡等）
 *
 * 风格决定参数基调，强度在其区间内插值：
 * - 水玻璃：blur 薄、遮蔽弱、透亮
 * - 磨砂：blur 厚、遮蔽强
 */

/** 玻璃风格。 */
enum class GlassStyle {
    /** 水玻璃 — 轻薄透亮。 */
    WATER,

    /** 磨砂玻璃 — 厚重乳白。 */
    FROST,
}

/** 液态玻璃全局配置;strength<=0 表示关闭。 */
data class LiquidGlassConfig(
    val style: GlassStyle = GlassStyle.FROST,
    /** 模糊强度 0f..1f;0 = 关闭。 */
    val strength: Float = 0.5f,
) {
    val enabled: Boolean get() = strength > 0.01f

    companion object {
        const val MODE_OFF = "off"
        const val MODE_WATER = "water"
        const val MODE_FROST = "frost"

        /** 从存储字符串解析模式(容错回退 frost)。 */
        fun modeFrom(value: String?): String = when (value) {
            MODE_OFF, MODE_WATER, MODE_FROST -> value
            else -> MODE_OFF
        }

        fun styleFrom(mode: String): GlassStyle = if (mode == MODE_WATER) GlassStyle.WATER else GlassStyle.FROST
    }
}

/**
 * 风格参数曲线。
 *
 * 用户反馈定稿:
 * - 水玻璃 ≈ iOS 液态玻璃: 极透、blur 轻、靠折射感而非厚度 —— blur 6..18dp,
 *   tint 薄(0.10..0.20),基础底色也薄(0.06..0.14),随强度微升
 * - 磨砂玻璃 ≈ Android 原生磨砂: 明显的"厚模糊" —— blur 20..60dp,
 *   tint 中等(0.32..0.52),低强度时也要能一眼看出"模糊"
 *
 * 旧版问题: 参数曲线太平(4..16dp / 8..30dp)且 tint 偏高盖住了模糊变化,
 * 滑杆拉满拉最低肉眼几乎无差别。
 */
private data class GlassAnchor(
    val blurAt0: Dp,
    val blurAt1: Dp,
    val baseAlphaAt0: Float,
    val baseAlphaAt1: Float,
    val tintAlphaAt0: Float,
    val tintAlphaAt1: Float,
)

private val ANCHORS = mapOf(
    // 水玻璃: 透亮为主,blur 变化轻柔,tint 始终薄
    GlassStyle.WATER to GlassAnchor(
        blurAt0 = 6.dp, blurAt1 = 18.dp,
        baseAlphaAt0 = 0.06f, baseAlphaAt1 = 0.14f,
        tintAlphaAt0 = 0.10f, tintAlphaAt1 = 0.20f,
    ),
    // 磨砂: 强度 0 也要有可感知的模糊(20dp),拉满 60dp 才有原生磨砂的厚重感
    GlassStyle.FROST to GlassAnchor(
        blurAt0 = 20.dp, blurAt1 = 60.dp,
        baseAlphaAt0 = 0.24f, baseAlphaAt1 = 0.40f,
        tintAlphaAt0 = 0.32f, tintAlphaAt1 = 0.52f,
    ),
)

/**
 * 按当前主题 + 配置生成玻璃样式。色值走 MaterialTheme，深浅主题自动适配。
 *
 * @param surfaceColor 该表面的"实色底"（即关闭玻璃时用的颜色），玻璃 tint 以它为基调。
 */
@Composable
fun liquidGlassStyle(surfaceColor: Color, config: LiquidGlassConfig): HazeStyle {
    val a = ANCHORS.getValue(config.style)
    val s = config.strength.coerceIn(0f, 1f)
    return HazeStyle(
        backgroundColor = surfaceColor.copy(alpha = a.baseAlphaAt0 + (a.baseAlphaAt1 - a.baseAlphaAt0) * s),
        tint = HazeTint(surfaceColor.copy(alpha = a.tintAlphaAt0 + (a.tintAlphaAt1 - a.tintAlphaAt0) * s)),
        blurRadius = Dp(a.blurAt0.value + (a.blurAt1.value - a.blurAt0.value) * s),
        noiseFactor = 0f,
    )
}

/** 液态玻璃配置的全局快照;MainActivity 收集设置流后提供。默认关闭(效果待打磨,设置项已隐藏)。 */
val LocalLiquidGlass = staticCompositionLocalOf { LiquidGlassConfig(strength = 0f) }

/** 全局 HazeState(内容源);MainActivity 提供,null = 玻璃未启用。 */
val LocalGlassHazeState = staticCompositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }
