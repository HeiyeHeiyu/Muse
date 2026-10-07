# Muse v2.5.0

本版为 v2.4.6 之后的正式版本，覆盖 v2.4.2 以来的全部累积更新：AI 生成链路加固、工具系统升级、输入栏与界面重设计、知识库文件夹、第三方导入扩展，以及一批稳定性修复。

## 新功能

### 1. 知识库文件夹

知识库现在支持**独立文件夹**：可以新建、重命名、删除文件夹（含空文件夹），文档可归入文件夹，检索时可按文件夹圈定范围。

- 文件夹可独立存在，不再依附于文档路径。
- 助手绑定知识库时，除整库外还可以**绑定到具体文件夹**，多个文件夹各自成为独立检索域，互不串扰。
- 未选任何文件夹时保持原有整库绑定行为，老用户无感知。

### 2. 第三方导入扩展

- **新版 ChatGPT 分片导出**（`chat.json` + 多个分片文件）可直接导入，自动合并分片、重建会话顺序。
- 导入对**群聊、助手、提供商配置**的字段覆盖更全，失败时给出具体原因。

### 3. 工具系统升级

- 工具长输出（文件读取、Shell、网页抓取等）支持**捕获与分页**，超长内容截断保真，不再刷屏。
- 新增**会话检索工具**，助手可以搜索历史会话内容。
- 工具权限、风险等级与数据隐私策略同步收紧。

### 4. 联网搜索查询提炼

搜索前对口语化输入做**保守提炼**：去掉"帮我""谢谢"等套话与语气词，超长查询按词截断；短关键词与带明确句式的查询原样透传，避免误删有效信息。

### 5. 输入栏重设计

- 输入岛改为**真胶囊**造型并整体抬高，不再贴底边。
- 发送键改为**实心纸飞机**图标（全图标集中唯一刻意加重的实心图标，主操作一眼可辨），随主题自动适配深浅色。
- 用户消息支持**行内编辑**快捷按钮。
- 斜杠命令**内联补全**。
- 行内多个操作收敛进「更多」菜单，气泡更干净。
- 消息地图、字母索引支持**拖动连续定位**。

### 6. 翻译页改进

- 修复翻译结果区**灰色空块**。
- 翻译历史支持**长按复制**，译文与原文按固定格式一并复制。

### 7. 聊天设置排版预览面板

设置 → 聊天的「正文排版」区新增**实时预览**：两个模拟气泡（用户/AI）随字号、字间距、气泡圆角、通栏、时间戳、模型名、MOOD/思考显示开关即时变化，所见即所得。

### 8. 液态玻璃效果

新增「液态玻璃」视觉模式（设置 → 外观）：顶栏、输入栏与**消息气泡**改为玻璃质感，消息从玻璃下方滚过时呈现柔和透视。

参考 Operit AI 的双风格方案，提供两种玻璃质感与三档强度，由用户按设备性能自选：

- **水玻璃**：轻薄透亮，透出内容多，适合喜欢清透观感的用户
- **磨砂玻璃**：厚重乳白（默认），经典毛玻璃质感
- **强度档位**：低 / 中 / 高 —— 低端设备建议选「低」或直接关闭，以获得最佳流畅度

Android 10+ 可用；旧设备自动降级为半透明着色。旧版布尔开关自动迁移（开→磨砂，关→关闭）。

## 稳定性修复

### 1. 流式中断与重试链路加固

- 新增**首事件看门狗**：请求发出后长时间收不到第一个事件时主动中断并提示，不再无限转圈。
- Provider 错误分类更细，取消竞态、重试代际、工具调用续片归位均有处理；「关闭深度思考」不再伪装成最低档推理。

### 2. 后台调度与渠道写入一致性

- 前台服务停止前增加**一致性门**：待写入完成才停止，避免丢消息。
- 备份、每日总结、主动消息、定时任务、群聊调度的写入路径对齐，inbox 恢复边界更稳。

### 3. 采样温度与输出预算出口兜底

- 部分供应商对 `temperature` 用开区间（如商汤 2.0 非法），滑块拉满或角色卡带任意温度会 HTTP 400，现在出口统一夹取。
- 模型未声明输出上限时不再把整段上下文当 `max_tokens`，交回 Provider 默认值，修复 `max_tokens exceeds the limit`。

### 4. 回复自动跟随滚动

自动跟随此前会被消息区尾部的"思考中/占位/任务卡"项打断，整段回复期间不跟随。现在到/越过消息区末尾即算到底并主动跟随，用户上滑仍立即停止。

### 5. 工具过程关闭后的幽灵空白

关闭「显示工具调用过程」后，只有工具载荷的消息在渲染层被隐藏但仍占据列表项，连续工具轮每条叠一份消息间距，在思考块之间叠出大段空白。现在这些消息直接不进渲染列表，空白彻底消失。

### 6. 知识库系统返回手势

在文件夹或知识库视图中使用系统返回手势，此前会直接退出整个知识库页面。现在与顶栏返回键同层退出：先清搜索 → 返回上一级文件夹 → 返回知识库列表 → 退出。

### 7. 深浅色主题跟随系统即时切换

应用设为「跟随系统」时，系统切换深浅色此前需要退出重进才能生效（Manifest 声明了 uiMode 配置变更处理导致 Activity 不重建）。现在切换立即生效，无需重启。

### 8. 消息地图滑条无法唤出

消息地图的触摸热区从 36dp 收窄到 24dp 且贴死屏幕右缘后，被 Android 10+ 的系统返回手势带（约 20dp）覆盖，实际可触面积不足，导致长会话中滑条完全唤不出来。现在热区加宽到 40dp 并内缩避开系统手势带，正常拖动即可唤出；消息底部操作行的点击不受影响。

### 9. 生成进度通知异常

修复三类通知问题：

- **字数不变化**：前台服务更新通知时恒传 0 字符，覆盖了 UI 层的真实字数。现在服务展示真实的已生成字数，进度动态可见。
- **通知残留**：生成结束后通知栏残留字数不变的动态进度条（应用退出也在）。原因是前台服务的通知无法被单独取消，必须先退前台再清理。现在服务停止时同步清理，服务被系统强杀时也兼底清理。
- **通知堆积**：多条通知并存的问题随上述修复一并消除。

### 10. 后台生成中断

后台保活链路（前台服务 + wakelock + 应用级协程）已经完整。部分激进 ROM（小米/华为等）仍可能限制后台网络导致流式中断，建议将 Muse 加入电池优化白名单/允许后台运行。中断后的内容已通过检查点自动保留，回前台可续传。

### 11. 其他

- 记忆解析的说话人归属修正，记忆写入门禁对齐。
- 虚拟屏 Binder 交接时序与协议字段对齐；手机 Agent 执行分级回退（Root/Shell 降级）与 display 失效刷新。
- 补齐并修正 7 语言字符串；内置知识文档与模型目录更新（2026-09-30 版，35 provider / 1024 模型）。

## 构建信息

- 版本：2.5.0 (250)
- 数据库版本：109
- 分发：arm64-v8a / armeabi-v7a / universal

---
# Muse v2.5.0 (English)

The first stable release after v2.4.6, covering everything accumulated since v2.4.2: AI generation pipeline hardening, tool system upgrades, an input bar and UI redesign, knowledge base folders, third-party import extensions, and a batch of stability fixes.

## New Features

### 1. Knowledge base folders

Knowledge bases now support **standalone folders**: create, rename and delete folders (including empty ones), organize documents into them, and scope retrieval to a folder.

- Folders exist independently, no longer tied to document paths.
- When binding a knowledge base to an assistant, you can bind **specific folders** in addition to the whole base. Each folder becomes an isolated retrieval scope.
- With no folder selected the existing whole-base binding applies, so existing users see no change.

### 2. Third-party import extensions

- **New-format ChatGPT shard exports** (`chat.json` plus multiple shard files) import directly; shards are merged and conversation order rebuilt automatically.
- Imports cover group chats, assistants and provider configs more completely, and report specific reasons on failure.

### 3. Tool system upgrade

- Long tool outputs (file reads, shell, web fetches) now support **capture and paging**, with truncation that preserves fidelity instead of flooding the screen.
- A new **conversation search tool** lets the assistant search past sessions.
- Tool permissions, risk levels and data privacy policies are tightened in sync.

### 4. Web search query refinement

Before searching, colloquial input is **conservatively refined**: filler words and polite phrases are removed, and overly long queries are truncated by word. Short keywords and clearly structured queries pass through untouched to avoid deleting useful information.

### 5. Input bar redesign

- The input island is now a **true pill** shape, lifted off the bottom edge.
- The send key uses a **solid paper-plane** icon (the only intentionally weighted filled icon in the set, instantly recognizable as the primary action) and adapts to light/dark themes automatically.
- User messages gain an **inline edit** shortcut.
- Slash commands get **inline completion**.
- Multiple inline actions are consolidated into a "more" menu for cleaner bubbles.
- The message map and letter index support **continuous drag positioning**.

### 6. Translate page improvements

- Fixed the **gray empty block** in the translation result area.
- Translation history supports **long-press to copy**, copying translation and source text together in a fixed format.

### 7. Chat settings typography preview panel

The "Typography" section in Settings → Chat gains a **live preview**: two mock bubbles (user/AI) that update instantly with font size, letter spacing, bubble radius, full-width mode, timestamp, model name, and MOOD/reasoning visibility toggles.

### 8. Liquid glass effect

A new "Liquid glass" appearance mode (Settings → Appearance) applies **app-wide**: the top bar, input bar, message bubbles, and back/more buttons can all use glass material, with messages scrolling softly beneath.

Inspired by Operit AI's dual-style approach:

- **Water glass**: light and translucent, more content shows through
- **Frosted glass**: thick and milky (default), classic frosted feel
- **Strength slider** (0-100%): continuous blur adjustment; low-end devices can dial it down or turn it off for the best smoothness

The chat top bar becomes a **standalone glass island** (aligned with the group chat top bar language), with back and menu buttons integrated. Available on Android 10+; older devices fall back to translucent tinting automatically. Legacy toggles migrate automatically.

## Stability Fixes

### 1. Streaming interruption and retry hardening

- A **first-event watchdog**: when no first event arrives long after the request is sent, generation is aborted with a clear notice instead of spinning forever.
- Provider errors are classified more finely; cancel races, retry generations and tool-call continuation fragments are all handled. Turning off deep thinking no longer masquerades as the lowest reasoning tier.

### 2. Background scheduling and channel write consistency

- A **consistency gate** before a foreground service stops: pending writes complete before shutdown, so messages are no longer lost.
- Write paths for backup, daily summary, proactive messages, scheduled tasks and group chat scheduling are aligned; inbox recovery boundaries are more robust.

### 3. Upstream constraint clamping at the request boundary

- Some providers constrain `temperature` with an open interval (e.g. SenseNova rejects 2.0). A maxed-out slider or an arbitrary value from an imported character card no longer causes HTTP 400; values are clamped at the request boundary.
- When a model declares no output limit, the full context is no longer used as `max_tokens`; the provider default applies instead, fixing `max_tokens exceeds the limit`.

### 4. Auto-follow scrolling during replies

Auto-follow used to break whenever trailing items ("thinking", placeholders, task cards) appeared in the message area, so the list never followed during a reply. It now treats "reached or passed the end" as at-bottom and follows actively; a manual scroll up still stops it immediately.

### 5. Ghost whitespace after hiding tool details

With "show tool call details" off, messages carrying only tool payloads were hidden by the renderer but still occupied list items; consecutive tool rounds stacked message gaps into large blank areas between thinking blocks. These messages are now excluded from the render list entirely.

### 6. Knowledge base system back gesture

Inside a folder or knowledge base view, the system back gesture previously exited the whole knowledge page. It now mirrors the top-bar back button: clear search → up one folder level → back to the KB list → exit.

### 7. Dark/light theme follows system instantly

With the app set to "follow system", toggling the system dark mode previously required restarting the app (the Manifest declared uiMode config-changes handling, so the Activity was not recreated). The switch now applies immediately.

### 8. Message map rail could not be summoned

After the message map touch zone was narrowed from 36dp to 24dp flush against the screen edge, it was covered by the Android 10+ system back gesture zone (~20dp), leaving too little reachable area — the rail could not be summoned in long conversations. The touch zone is now 40dp wide and inset away from the system gesture zone; bottom action-row taps are unaffected.

### 9. Others

- Memory parsing speaker attribution fixed; memory write gates aligned.
- Virtual display Binder handoff timing and protocol fields aligned; phone agent execution falls back across privilege levels (Root/Shell) and refreshes on display loss.
- Strings completed and corrected across 7 languages; bundled knowledge docs and model catalog updated (2026-09-30, 35 providers / 1024 models).

## Build

- Version: 2.5.0 (250)
- Database version: 109
- Distributions: arm64-v8a / armeabi-v7a / universal
