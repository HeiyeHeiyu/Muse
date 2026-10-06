# Muse v2.4.6

本版为 v2.4.5 之后的缺陷修复版本，聚焦「上游服务商约束在本地兜住」，修掉三类会导致请求被上游拒绝或交互体验受损的问题。

## 修复

### 1. 采样温度越界导致 HTTP 400（换助手发第一条消息即失败）

部分供应商对 `temperature` 采用**开区间**约束（如商汤 SenseNova 为 `[0.0, 2.0)`，2.0 本身非法），而应用内「回答随机性」滑块上限恰为 2.0、第三方角色卡导入的温度可任意取值，此前这些值会原样透传给上游，触发 `field Temperature invalid` 并中断本轮生成。

现在在请求出口按供应商能力声明统一收紧温度：新增 `ProviderCompat.maxTemperatureExclusive`，商汤端点声明上限 2.0；`ChatService` 在构造请求前把温度夹进合法区间（与既有的 `topP` 夹取同位置）。滑块拉到 2.0 也不会再报错。

### 2. 输出预算越界导致 HTTP 400（max_tokens exceeds the limit）

模型未声明 `maxOutputTokens` 时，此前会把「整段剩余上下文窗口」当成本次输出预算，算出接近上下文全长的 `max_tokens`，被上游以 `max_tokens exceeds the limit of 65536` 拒绝。

现在 `ModelOutputPolicy` 在「模型未声明上限且调用方未给预算」时交回 Provider 自身默认值，不再凭空放大。

### 3. 回复过程中不自动跟随滚动

此前自动跟随要求「最后可见项恰好是最后一条消息」，但消息区尾部会插入「思考中/工具执行」提示、图片与视频占位、子代理任务卡等条件项——这些项一出现判定即失效，导致整段回复期间列表不跟随，必须手动滑到底才能看到全文，且「已上滑」状态被误锁。

现在改为「已到达或越过消息区末尾即视为到底」，并在尾随项贴底但列表仍可前滚时主动前滚一屏。用户主动上滑仍会立即停止跟随（设计不变）。

## 构建信息

- 版本：2.4.6 (246)
- 数据库版本：107
- 分发：arm64-v8a / armeabi-v7a / universal

---
# Muse v2.4.6 (English)

This is a defect-fix release following v2.4.5, focused on keeping upstream provider constraints enforced locally. It fixes three issues that caused requests to be rejected upstream or degraded the interaction experience.

## Fixes

### 1. Out-of-range sampling temperature caused HTTP 400 (failed on the first message after switching assistants)

Some providers constrain `temperature` with an open interval (for example SenseNova uses `[0.0, 2.0)`, where 2.0 itself is invalid). The in-app "response randomness" slider tops out at 2.0, and imported third-party character cards may carry arbitrary values. These values were passed through unchanged, triggering `field Temperature invalid` and aborting generation.

The temperature is now clamped at the request boundary according to the provider capability declaration: a new `ProviderCompat.maxTemperatureExclusive` field lets the SenseNova host declare an upper bound of 2.0, and `ChatService` clamps temperature into the legal range before building the request (at the same point where `topP` was already clamped). Pushing the slider to 2.0 no longer errors.

### 2. Out-of-range output budget caused HTTP 400 (max_tokens exceeds the limit)

When a model did not declare `maxOutputTokens`, the full remaining context window was used as the output budget, producing a `max_tokens` close to the entire context length and getting rejected with `max_tokens exceeds the limit of 65536`.

`ModelOutputPolicy` now falls back to the provider's own default when the model declares no limit and the caller supplies no budget, instead of inflating the value.

### 3. No auto-follow scrolling during replies

Auto-follow previously required the last visible item to be exactly the last message. However, the message area appends conditional trailing items such as "thinking/tool running" hints, image and video placeholders, and sub-agent task cards. Once any of these appeared, the condition failed, so the list stopped following for the entire reply, forcing a manual scroll to the bottom to read the full text, and the "user scrolled up" flag was wrongly locked.

The check is now "reached or passed the end of the message area", and the list actively scrolls by one viewport when a trailing item is pinned to the bottom while the list can still scroll forward. A manual scroll up still stops following immediately (unchanged by design).

## Build

- Version: 2.4.6 (246)
- Database version: 107
- Distributions: arm64-v8a / armeabi-v7a / universal
