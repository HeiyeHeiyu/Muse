# Muse v2.5.2

本版重做了液态玻璃效果（v2.5.1 起该效果默认关闭、设置项隐藏）。如果你对玻璃观感感兴趣，可在 设置 → 外观 → 液态玻璃效果 中开启体验；不开启则完全不影响日常使用。

## 液态玻璃重设计

### 上一版的问题

只做了「模糊 + 半透明平涂着色」，那是毛玻璃；而且着色偏重（0.4+），把模糊本身盖住了——拉强度滑杆几乎看不出变化。真正的玻璃感来自三层，之前缺了两层。

### 本版补齐

**1. 边缘层（最关键的缺失）**

新增玻璃三要素叠层：

- 顶部 1.5dp 白色高光边 —— 玻璃"厚度"的主要来源
- 底部 1dp 暗边 —— 体积感
- 斜向 35° 光扫 —— 模拟环境光反射

**2. 体层通透度**

着色透明度大幅调低，让模糊真正可见、强度滑杆变化可感知。

**3. 双风格（视觉上真正区分）**

- **水玻璃**：弱模糊 + 强高光 + 薄着色（接近 iOS 观感）
- **磨砂玻璃**：强模糊 + 柔高光 + 厚着色（接近 Android 原生观感）

### 范围分层（性能关键）

- **真玻璃**（背景模糊）：顶栏返回键 / 更多键 / 输入岛 / 更多菜单 —— 数量少、位置固定
- **假玻璃**（渐变 + 高光边，不做模糊）：消息气泡 —— 数量多、滚动中，避免一屏几十个模糊层拖垮滚动

### 其他

- 聊天顶栏从"整条横接透明条"改为**三颗独立岛**（左返回 / 中标题 / 右更多）
- 更多菜单（右上角三点）同样应用玻璃质感

## 构建信息

- 版本：2.5.2 (252)
- 数据库版本：109（与 2.5.0/2.5.1 相同，直接覆盖安装）
- 分发：arm64-v8a / armeabi-v7a / universal

---
# Muse v2.5.2 (English)

This release redesigns the liquid glass effect (which has been off by default with its setting hidden since v2.5.1). If you're curious about the look, enable it under Settings → Appearance → Liquid glass effect; leaving it off changes nothing in daily use.

## Liquid glass redesign

### What was wrong before

It only did "blur + flat translucent tint" — that is frosted glass, not liquid glass. The tint was also too heavy (0.4+), covering the blur itself, so dragging the intensity slider barely changed anything. Real glass comes from three layers; two were missing.

### What's added

**1. Edge layer (the key missing piece)**

A new three-element glass overlay:

- A 1.5dp top white highlight edge — the main source of glass "thickness"
- A 1dp bottom shadow edge — volume
- A diagonal 35° light sweep — simulated ambient reflection

**2. Body transparency**

Tint alpha is lowered substantially so the blur is genuinely visible and the intensity slider has a perceptible effect.

**3. Two styles (visually distinct)**

- **Water glass**: light blur + strong highlight + thin tint (close to the iOS look)
- **Frosted glass**: heavy blur + soft highlight + thick tint (close to the Android native look)

### Layered scope (performance-critical)

- **Real glass** (backdrop blur): top-bar back/more buttons, input bar, more menu — few and fixed
- **Fake glass** (gradient + highlight edge, no blur): message bubbles — many and scrolling, avoiding dozens of blur layers per screen

### Other

- The chat top bar changes from a full-width translucent strip to **three separate islands** (back / title / more)
- The top-right "more" menu also gets the glass treatment

## Build

- Version: 2.5.2 (252)
- Database version: 109 (same as 2.5.0/2.5.1; install over the existing app)
- Distributions: arm64-v8a / armeabi-v7a / universal
