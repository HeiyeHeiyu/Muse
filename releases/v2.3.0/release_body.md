# Muse v2.3.0

以"长任务体验"为核心的修复版：多步骤任务不再被中途打断，长对话与大量工具操作之后，助手依然记得你们聊过什么；并修复了记忆可能被清空的严重问题。建议所有用户升级。

## 重要修复

- **记忆不再丢失**：修复了重启后事实记忆可能被清空的问题；设备备份中若存有被清理的数据，升级后会自动找回。今后记忆不再随启动丢失。
- **长任务不再"失忆"**：连续执行大量工具步骤后，助手仍记得之前的对话，可以接着之前的上下文继续。
- **偶发长等待修复**：为历史压缩等后台处理增加超时保护，消除少数情况下"消息迟迟不出"的长时间等待。

## 新能力

- **工具轮次放开**：默认不限轮次（也可在 设置 → 聊天 中限制），复杂任务一次做完；异常循环保护依然生效。
- **工具权限透明化**：需要 Shizuku / Root、无障碍或 Termux 的工具会显示就绪状态，未授权时立即给出明确提示，而不是执行到一半莫名失败。
- **提示词技能支持输入**：自定义提示词技能可接收输入文本并代入指令。
- **知识库搜索更可控**：搜索结果阈值真正生效，未命中时提供诊断信息。

## 体验改进

- 消息渠道卡片重新排版：长名称与长账号不再挤成一团。
- 闹钟 / 倒计时修复：补齐系统权限声明，此前会被系统拦截；失败时给出明确反馈。
- 长记忆条目支持点击展开查看全文。

---

# Muse v2.3.0 (English)

A reliability-focused release centered on long tasks: multi-step tasks are no longer interrupted midway, and after long conversations or heavy tool use the assistant still remembers what you talked about — plus a serious fix for memory being wiped on restart. Upgrading is strongly recommended.

## Important Fixes

- **Memory is no longer lost**: fixed an issue where fact memories could be wiped after a restart; data previously cleaned up will be automatically recovered on upgrade if it exists in your device backups. From now on, memories will no longer disappear across restarts.
- **No more "amnesia" on long tasks**: after running many consecutive tool steps, the assistant still remembers the earlier conversation and can continue from where it left off.
- **Occasional long waits fixed**: added a timeout safeguard to background processing such as history compression, eliminating rare cases of "messages taking a very long time to appear".

## New Capabilities

- **Tool rounds uncapped**: rounds are unlimited by default (you can still cap them under Settings → Chat), so complex tasks can complete in one go; loop protections remain active.
- **Transparent tool permissions**: tools requiring Shizuku / Root, Accessibility, or Termux now show their readiness status, and fail fast with a clear message when unauthorized — instead of mysteriously failing halfway.
- **Prompt skills accept input**: custom prompt skills can now receive input text and substitute it into their instructions.
- **More controllable knowledge search**: the search result threshold now truly takes effect, with diagnostics included when nothing matches.

## Experience Improvements

- **Channel cards redesigned**: long names and long account IDs no longer get squeezed into a mess.
- **Alarm / timer fixes**: added the missing system permission declaration (previously blocked by the system); failures now give clear feedback.
- **Long memory entries can be tapped to expand** and read in full.
