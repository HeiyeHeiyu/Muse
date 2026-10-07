# Muse v2.5.1

本版为 v2.5.0 的紧急修复版本，修复两个严重回归。**请务必更新到本版**——v2.5.0 的两个问题都会导致对话不可用。

## 修复

### 1. 工具调用名被空串续片覆盖，所有工具调用被丢弃（glm / DeepSeek 等中转站必现）

部分中转站的流式 `tool_calls` 在后续增量分片中携带**空字符串的 name 字段**。此前下游用「null 合并」逻辑累积工具名——空串不是 null，穿透了合并逻辑，把首片已正确到达的完整工具名（如 `knowledge_search`、`search_memory`）覆盖成空串。

流结束后清洗器按「name 为空」丢弃全部工具调用，表现为：

- 提示「模型发起了 N 个工具调用，但参数格式异常未能执行」
- 思考过程（CoT）正常输出，但正文为空，直接被工具调用失败挡回
- 会话回顾时该轮消息闪现后消失（空消息被渲染层过滤）

双保险修复：上游对后续增量的空白工具名改发 null（下游语义保留旧名）；下游累积时同样过滤空白串。

### 2. 深度思考被 10 分钟总超时硬杀（回复截断）

glm-5.3 等深度思考模型在中转站上的思考期（实测 10 分钟以上）**不吐任何流式数据**，而流式请求此前受 10 分钟总超时约束——模型思考满 10 分钟整时，整个流被硬杀，回复截断（此前已有部分工具调用成功也会被丢弃）。

修复：流式请求不再设总超时。静默挂起仍由 5 分钟读超时兜底（真挂起照样断），用户取消按钮仍然有效；思考多久都不再被系统掐断。

## 已知事项

- 部分中转站（如 newapi.0z.hk）自身对 glm-5.3 等模型会返回 503/400（`code:11133 参数被供应商拒绝`），这是中转站上游问题，客户端重试也无法解决，建议换模型或换站点验证。
- 模型自动命名会话时偶尔输出 `...` 字面量导致会话名变省略号的问题，将在下版拦截。

## 构建信息

- 版本：2.5.1 (251)
- 数据库版本：109（与 2.5.0 相同，直接覆盖安装）
- 分发：arm64-v8a / armeabi-v7a / universal

---
# Muse v2.5.1 (English)

An urgent hotfix release for v2.5.0, fixing two severe regressions that make conversations unusable. **Please update.**

## Fixes

### 1. Tool call names overwritten by empty-string continuation fragments, causing all tool calls to be dropped (reproducible on GLM / DeepSeek relays)

Some relays' streaming `tool_calls` carry an **empty-string name field** in subsequent delta fragments. The downstream name accumulation used null-coalescing — an empty string is not null, so it slipped through and overwrote the complete tool name (e.g. `knowledge_search`, `search_memory`) that had already arrived correctly in the first fragment.

After the stream ended, the sanitizer dropped every tool call for having an "empty name", resulting in:

- The notice "N tool calls were initiated but could not be executed due to malformed arguments"
- Reasoning (CoT) output normally, but an empty body cut off by the tool-call failure
- The round's messages disappearing on session revisit (empty messages filtered by the render layer)

Fixed with double insurance: the upstream provider now sends null for blank names in continuation fragments (downstream treats null as "keep the existing name"), and the downstream accumulator filters blank strings as well.

### 2. Deep thinking killed by a 10-minute total timeout (truncated replies)

Deep-thinking models like glm-5.3 on relays can think for 10+ minutes **emitting no streaming data at all**. Streaming requests were subject to a 10-minute total timeout — exactly when the model finished thinking, the entire stream was killed and the reply truncated (even successfully executed tool calls from earlier rounds were discarded).

Fix: streaming requests no longer have a total timeout. Silent hangs are still caught by the 5-minute read timeout (genuinely stuck streams still disconnect), and the cancel button still works. Thinking for any length is no longer cut off by the system.

## Known issues

- Some relays (e.g. newapi.0z.hk) return 503/400 (`code:11133 parameters rejected by the provider`) for models like glm-5.3 — this is an upstream relay problem that client retries cannot fix. Try a different model or endpoint.
- Auto session naming occasionally outputs `...` literal, turning session titles into ellipses. Will be intercepted in the next release.

## Build

- Version: 2.5.1 (251)
- Database version: 109 (unchanged from 2.5.0; install over the existing app)
- Distributions: arm64-v8a / armeabi-v7a / universal
