# Muse v2.5.1

本版为 v2.5.0 的紧急修复版本，修复部分中转站/模型下**工具调用全部失败**的严重回归。

## 修复

### 1. 工具调用名被空串续片覆盖，所有工具调用被丢弃（glm / DeepSeek 等中转站必现）

部分中转站的流式 `tool_calls` 在后续增量分片中携带**空字符串的 name 字段**。此前下游用「null 合并」逻辑累积工具名——空串不是 null，穿透了合并逻辑，把首片已正确到达的完整工具名（如 `knowledge_search`、`search_memory`）覆盖成空串。

流结束后清洗器按「name 为空」丢弃全部工具调用，表现为：

- 提示「模型发起了 N 个工具调用，但参数格式异常未能执行」
- 思考过程（CoT）正常输出，但正文为空，直接被工具调用失败挡回
- 会话回顾时该轮消息闪现后消失（空消息被渲染层过滤）

双保险修复：上游对后续增量的空白工具名改发 null（下游语义保留旧名）；下游累积时同样过滤空白串。两条链路任一生效即不会丢失工具名。

## 构建信息

- 版本：2.5.1 (251)
- 数据库版本：109（与 2.5.0 相同，直接覆盖安装）
- 分发：arm64-v8a / armeabi-v7a / universal

---
# Muse v2.5.1 (English)

An urgent hotfix release for v2.5.0, addressing a severe regression where **all tool calls failed** on certain relay endpoints and models.

## Fixes

### 1. Tool call names overwritten by empty-string continuation fragments, causing all tool calls to be dropped (reproducible on GLM / DeepSeek relays)

Some relays' streaming `tool_calls` carry an **empty-string name field** in subsequent delta fragments. The downstream name accumulation used null-coalescing — an empty string is not null, so it slipped through and overwrote the complete tool name (e.g. `knowledge_search`, `search_memory`) that had already arrived correctly in the first fragment.

After the stream ended, the sanitizer dropped every tool call for having an "empty name", resulting in:

- The notice "N tool calls were initiated but could not be executed due to malformed arguments"
- Reasoning (CoT) output normally, but an empty body cut off by the tool-call failure
- The round's messages disappearing on session revisit (empty messages filtered by the render layer)

Fixed with double insurance: the upstream provider now sends null for blank names in continuation fragments (downstream treats null as "keep the existing name"), and the downstream accumulator filters blank strings as well. Either path alone prevents the name loss.

## Build

- Version: 2.5.1 (251)
- Database version: 109 (unchanged from 2.5.0; install over the existing app)
- Distributions: arm64-v8a / armeabi-v7a / universal
