@file:Suppress("MatchingDeclarationName")

package io.zer0.muse.ui.theme

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/**
 * v2.5.2: 液态玻璃效果中枢（重设计）。
 *
 * ## 上一版为什么不好看
 * 只做了「模糊 + 半透明平涂着色」，那是毛玻璃；而 tint 偏重(0.4+)又把模糊盖住，
 * 拉强度滑杆几乎看不出变化。真正的玻璃感来自三层，之前缺了两层：
 *
 * | 层 | 作用 | 上一版 |
 * |---|---|---|
 * | 背景层 | 模糊（Haze 背景捕获） | ✅ 有 |
 * | 体层 | 着色 + 通透度 | ⚠️ 有但过重 |
 * | **边缘层** | **顶部高光边 + 底部暗边 + 斜向光扫** | ❌ 完全没有 |
 *
 * ## 本版方案
 * 不升级依赖（Haze 2.x 要求 Compose 1.12+，项目在 1.7.x，升级风险不可接受），
 * 改为：**沿用 Haze 1.5.3 做模糊底 + 自绘玻璃三要素**。
 *
 * 三要素由 [Modifier.glassEdgeHighlight] 提供：
 * 1. 顶部高光边（1.5dp 白色渐隐）—— 玻璃"厚度"的主要来源
 * 2. 底部暗边（细，制造体积感）
 * 3. 斜向光扫（35° 白色微渐变，模拟环境光反射）
 *
 * 风格双档（视觉上真正不同，不只是 blur 数值差）：
 * - 水玻璃：弱模糊 + 强高光 + 薄 tint（iOS 观感）
 * - 磨砂：强模糊 + 弱高光 + 厚 tint（Android 原生观感）
 *
 * 范围分层：chrome 表面（顶栏岛/输入岛/菜单）用真模糊；消息气泡用假玻璃（无模糊，
 * 只有渐变+高光），避免一屏几十个模糊层拖垮滚动。
 */

/** 玻璃风格。 */
enum class GlassStyle {
    /** 水玻璃 — 轻薄透亮，高光锐利。 */
    WATER,

    /** 磨砂玻璃 — 厚重乳白，高光柔和。 */
    FROST,
}

/** 液态玻璃全局配置;strength<=0 表示关闭。 */
data class LiquidGlassConfig(
    val style: GlassStyle = GlassStyle.FROST,
    /** 强度 0f..1f;0 = 关闭。 */
    val strength: Float = 0.5f,
) {
    val enabled: Boolean get() = strength > 0.01f

    companion object {
        const val MODE_OFF = "off"
        const val MODE_WATER = "water"
        const val MODE_FROST = "frost"

        fun modeFrom(value: String?): String = when (value) {
            MODE_OFF, MODE_WATER, MODE_FROST -> value
            else -> MODE_OFF
        }

        fun styleFrom(mode: String): GlassStyle =
            if (mode == MODE_WATER) GlassStyle.WATER else GlassStyle.FROST
    }
}

/**
 * 玻璃参数锚点（强度 0 与 1 两端，中间线性插值）。
 *
 * 关键调整（相对上一版）：
 * - tint 大幅降低 —— 让模糊真的看得见（旧版 tint 0.4+ 把模糊盖死了）
 * - 水玻璃 blur 很浅（4→14dp），靠高光撑质感
 * - 磨砂 blur 拉开（14→44dp），一眼可辨"厚"
 */
private data class GlassAnchor(
    val blurAt0: Dp,
    val blurAt1: Dp,
    val baseAlphaAt0: Float,
    val baseAlphaAt1: Float,
    val tintAlphaAt0: Float,
    val tintAlphaAt1: Float,
    /** 顶部高光边峰值 alpha。 */
    val highlightAt0: Float,
    val highlightAt1: Float,
)

private val ANCHORS = mapOf(
    // 水玻璃：极浅模糊 + 强高光 + 薄 tint（iOS 液态观感）
    GlassStyle.WATER to GlassAnchor(
        blurAt0 = 4.dp, blurAt1 = 14.dp,
        baseAlphaAt0 = 0.05f, baseAlphaAt1 = 0.12f,
        tintAlphaAt0 = 0.06f, tintAlphaAt1 = 0.14f,
        highlightAt0 = 0.30f, highlightAt1 = 0.55f,
    ),
    // 磨砂：厚模糊 + 柔高光 + 厚 tint（原生磨砂观感）
    GlassStyle.FROST to GlassAnchor(
        blurAt0 = 14.dp, blurAt1 = 44.dp,
        baseAlphaAt0 = 0.18f, baseAlphaAt1 = 0.34f,
        tintAlphaAt0 = 0.20f, tintAlphaAt1 = 0.38f,
        highlightAt0 = 0.18f, highlightAt1 = 0.32f,
    ),
)

/** 解析后的玻璃参数（供样式与叠层共用）。 */
internal data class ResolvedGlass(
    val blur: Dp,
    val baseAlpha: Float,
    val tintAlpha: Float,
    val highlightAlpha: Float,
    val style: GlassStyle,
)

internal fun resolveGlass(config: LiquidGlassConfig): ResolvedGlass {
    val a = ANCHORS.getValue(config.style)
    val s = config.strength.coerceIn(0f, 1f)
    fun lerp(x: Float, y: Float) = x + (y - x) * s
    return ResolvedGlass(
        blur = Dp(lerp(a.blurAt0.value, a.blurAt1.value)),
        baseAlpha = lerp(a.baseAlphaAt0, a.baseAlphaAt1),
        tintAlpha = lerp(a.tintAlphaAt0, a.tintAlphaAt1),
        highlightAlpha = lerp(a.highlightAt0, a.highlightAt1),
        style = config.style,
    )
}

/**
 * 按当前主题 + 配置生成 Haze 玻璃样式（模糊底）。
 *
 * @param surfaceColor 该表面的"实色底"（关闭玻璃时用的颜色），玻璃 tint 以它为基调。
 */
@Composable
fun liquidGlassStyle(surfaceColor: Color, config: LiquidGlassConfig): HazeStyle {
    val p = resolveGlass(config)
    return HazeStyle(
        backgroundColor = surfaceColor.copy(alpha = p.baseAlpha),
        tint = HazeTint(surfaceColor.copy(alpha = p.tintAlpha)),
        blurRadius = p.blur,
        noiseFactor = 0f,
    )
}

/**
 * v2.5.2: 玻璃边缘三要素叠层 —— 这是让表面"像玻璃"而不是"半透明灰块"的关键。
 *
 * 必须在模糊底之上绘制（作为内容覆盖层），依次画：
 * 1. 顶部高光边：从上往下的白色渐隐（玻璃厚度感的主要来源）
 * 2. 底部暗边：从下往上的黑色微渐隐（体积感）
 * 3. 斜向光扫：约 35° 的白色线性渐变（环境光反射）
 *
 * @param shape 玻璃形状（需与底色裁切形状一致）
 * @param config 玻璃配置（强度/风格决定高光强弱）
 */
fun Modifier.glassEdgeHighlight(shape: Shape, config: LiquidGlassConfig): Modifier =
    this.clip(shape).drawWithContent {
        drawContent()
        val p = resolveGlass(config)
        val w = size.width
        val h = size.height
        val hi = p.highlightAlpha

        // 1. 顶部高光边 —— 1.5dp 内从亮到透明
        val topBand = 1.5.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = hi),
                1f to Color.Transparent,
                startY = 0f,
                endY = topBand,
            ),
            size = Size(w, topBand),
        )
        // 2. 底部暗边 —— 1dp 内从暗到透明（体积感）
        val bottomBand = 1.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Color.Black.copy(alpha = hi * 0.35f),
                startY = h - bottomBand,
                endY = h,
            ),
            topLeft = Offset(0f, h - bottomBand),
            size = Size(w, bottomBand),
        )
        // 3. 斜向光扫 —— 左上到右下，白色 0.6*hi 渐隐（环境光）
        drawRect(
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = hi * 0.55f),
                0.45f to Color.Transparent,
                start = Offset(0f, 0f),
                end = Offset(w * 0.85f, h),
            ),
        )
    }

/**
 * v2.5.2: 假玻璃底 —— 给气泡这类高频、滚动中的表面用（不跑模糊）。
 *
 * 用垂直渐变替代模糊：顶部略亮（环境光）、底部略深（体积），叠加在基调色上。
 * 配合 [glassEdgeHighlight] 即可获得近似玻璃观感，成本几乎为零。
 */
fun glassFakeSurfaceColor(base: Color, config: LiquidGlassConfig): Brush {
    val p = resolveGlass(config)
    val lift = 0.10f * (p.highlightAlpha / 0.55f).coerceIn(0.3f, 1f)
    fun mix(alpha: Float) = androidx.compose.ui.graphics.lerp(base, Color.White, alpha)
    return Brush.verticalGradient(
        0f to mix(p.baseAlpha + lift),
        0.55f to mix(p.baseAlpha + p.tintAlpha * 0.6f),
        1f to mix(p.baseAlpha + p.tintAlpha),
    )
}

/**
 * v2.5.2: 玻璃描边 —— 上亮下暗的一圈 1px 边，进一步强化"玻璃片"边界。
 * 与 [glassEdgeHighlight] 配合使用（先画高光再描边）。
 */
fun Modifier.glassBorder(shape: Shape, config: LiquidGlassConfig): Modifier {
    val p = resolveGlass(config)
    return this.border(
        width = 0.8.dp,
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = p.highlightAlpha * 0.9f),
            0.5f to Color.White.copy(alpha = p.highlightAlpha * 0.25f),
            1f to Color.Black.copy(alpha = p.highlightAlpha * 0.22f),
        ),
        shape = shape,
    )
}

/** 液态玻璃配置的全局快照;MainActivity 收集设置流后提供。默认关闭。 */
val LocalLiquidGlass = staticCompositionLocalOf { LiquidGlassConfig(strength = 0f) }
/** 全局 HazeState(内容源);MainActivity 提供,null = 玻璃未启用。 */
val LocalGlassHazeState = staticCompositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }
