# Muse v2.4.0

这是一次从 v2.3 系列持续演进而来的大型更新，重点完成了手机 Agent、Node/npm 本地运行时、MCP 扩展、记忆与知识库可靠性、语音输入和对话恢复等多条核心链路的升级。

## 新能力

- **手机 Agent 全面增强**：从单步 UI 操作扩展到可观察、可验证、可恢复的多步骤手机工作流。Agent 可以读取控件树和屏幕状态，按文本或 resource-id 查找控件，支持滚动查找、点击后验证，并在失败或副作用不确定时停止自动重放。
- **后台虚拟屏操作**：支持在独立虚拟屏中启动应用、读屏、点击、滑动、输入文字、发送按键和等待，不必占用用户当前前台；虚拟屏读屏会严格绑定 display，避免把主屏误当成后台屏。
- **应用控制面**：新增列出应用、启动应用、打开应用设置、强停、清除数据和卸载等受控能力，按权限层级自动降级，并对系统应用、Muse 自身和危险操作设置安全边界。
- **设备能力档案**：Agent 在行动前会先了解当前设备的无障碍、Shell、Root、截图、虚拟屏和 Node 能力，再选择合适的执行通道，减少盲目试错。
- **Node/npm 本地沙盒**：应用内置 Node.js 与 npm 运行环境，可在 Muse 自己的沙盒中运行脚本、使用本地 npm 模块和处理文件/网络任务；高风险脚本仍受审批和超时限制。
- **MCP stdio 与终端扩展**：MCP 除网络传输外支持本地 stdio server，内置文件系统、记忆图和顺序思考模板；终端也能直接使用应用内 Node 运行时。运行时自检覆盖 Node 执行、JavaScript、动态库、TLS、npm 和脚本链接等关键环节。
- **知识库文件夹**：知识库支持类似文件管理器的虚拟文件夹，可以浏览、移动和按目录查看文档；ZIP 导入会保留压缩包内部目录结构。

## 记忆与对话

- **助手记忆隔离**：不同助手默认使用自己的事实、编译记忆和近期上下文；全局记忆由助手级共享设置控制，减少“只和另一个助手说过却被新助手知道”的串记忆问题。
- **记忆删除一致性**：事实、全文索引和记忆关系图谱边在同一事务中删除，避免删掉正文却留下搜索结果或关系边。
- **记忆自动保存更可靠**：空响应、调用失败和 JSON 解析失败不会被误记为成功；失败会保留后续补跑机会，指纹窗口也与实际发送给模型的内容保持一致。
- **长对话不再静默失忆**：改进滚动摘要、历史分块、工具回合压缩、摘要净收益判断和会话排序，保留关键助手说明与工具结果。
- **消息恢复更安全**：outbox、用户消息和首个 generation checkpoint 的提交顺序更加严格；恢复和重试保持 messageId、计数、预览和更新时间幂等，避免重复生成或会话列表被错误顶到顶部。
- **工具回合展示更连贯**：客户端工具调用后的续答不会重复堆叠同一轮思考元数据；时间间隔问题使用相邻真实用户消息的时间，而不是把模型思考或工具调用耗时当成用户等待时间。

## 知识库与 RAG

- **混合检索回退**：向量检索失败或熔断时，优先从知识库 chunk 的 FTS/BM25 检索实际片段，不再只搜索文档预览，长文后半段也能被召回。
- **文档隔离与安全过滤**：内部文档、定向文档范围和元数据过滤在向量、FTS 与回退路径保持一致；元数据读取失败时采用 fail-closed，避免内部资料意外注入上下文。
- **流式索引可回滚**：索引替换中途失败、取消或维度不匹配时，旧索引尽量保留；已经开始替换后失败会清理 DB、FTS 和 HNSW 的部分结果，并同步重置文档索引状态。
- **导入元数据完整**：导入成功后记录内容哈希和 embedding 配置，支持同内容跳过与模型切换检测；大文件哈希按读取分片增量计算，避免重新载入全文。
- **注入预算一致**：知识片段的预算计算和真正写入 system prompt 的文本使用同一段截断内容，避免完整长 chunk 绕过预算挤压对话上下文。

## 语音与外部服务

- **语音识别生命周期统一**：Step、Whisper、DashScope 和 Realtime 控制器都支持幂等 dispose，释放后不会重新进入 Connecting 或启动假会话。
- **语音结果不乱序**：分段 flush 与停止录音的最终 flush 共用串行锁，旧 controller 的迟到回调会被 generation 和实例身份校验丢弃；退出语音对话时会彻底取消监听。
- **识别失败可恢复**：Whisper 分段失败会保留音频段并按上限重试，避免最终 flush 失败时静默丢掉最后一句；多句英文 transcript 之间也会保留可读空格。
- **NewAPI / OpenAI 兼容服务诊断**：仅填写中转站域名时自动补齐常见 `/v1` API 路径；模型列表接口返回网页而不是 JSON 时，会明确提示检查 API 基址、代理路由和网页防护，而不是只显示解析失败。
- **OAuth 与渠道安全**：OAuth 错误日志不再记录原始响应体；渠道媒体清理会保护仍被收件箱和历史消息引用的文件；渠道游标、对话和 Agent checkpoint 的恢复边界更加明确。

## 体验与质量

- 修复大字体或窄屏下 Row 文案被压成一两个字的布局问题，并加入 LAYOUT-01 门禁。
- 补齐知识库、助手记忆、Agent 工作流、语音、工具权限、数据库迁移和恢复路径的回归测试。
- 更新 Kotlin/Compose 代码质量门禁、基线和多语言检查；正式候选包已通过五条本地 lane、发布脚本门禁、签名校验和版本校验。

---

# Muse v2.4.0 (English)

This is a major update built through the v2.3 series. It advances the phone Agent, the local Node/npm runtime, MCP extensions, memory and knowledge-base reliability, voice input, and conversation recovery across the core application.

## New Capabilities

- **A much stronger phone Agent**: Moved from single-step UI actions to observable, verifiable, resumable multi-step phone workflows. The Agent can inspect the control tree and screen state, find controls by text or resource ID, scroll within bounds, verify clicks, and stop instead of silently replaying uncertain side effects.
- **Background virtual displays**: Launch apps, read screens, tap, swipe, type, press keys, and wait inside an isolated display without taking over the user's current foreground app. Screen reads remain bound to the requested display instead of accidentally reading the main screen.
- **Application control**: Added controlled app listing, launching, settings, force-stop, data-clear, and uninstall capabilities with permission-aware fallback and guardrails for system apps, Muse itself, and destructive actions.
- **Device capability profile**: Before acting, the Agent learns whether Accessibility, Shell, Root, screenshots, virtual displays, and Node are available, then chooses an appropriate execution channel instead of guessing.
- **Local Node/npm sandbox**: Muse now includes its own Node.js and npm runtime for scripts, local npm modules, and file/network tasks inside the app sandbox. High-risk scripts remain protected by approval and timeouts.
- **MCP stdio and terminal extensions**: MCP now supports local stdio servers in addition to network transports, with built-in filesystem, memory-graph, and sequential-thinking templates. The terminal can use the same bundled Node runtime, with self-checks for execution, JavaScript, native libraries, TLS, npm, and script links.
- **Knowledge-base folders**: Knowledge documents can be browsed and moved in virtual folders like a file manager; ZIP imports retain their internal directory structure.

## Memory and Conversation

- **Assistant memory isolation**: Each assistant primarily uses its own facts, compiled memory, and recent context. Global memory is controlled by an assistant-level sharing setting, preventing knowledge from one assistant from unexpectedly appearing in another.
- **Consistent memory deletion**: Facts, full-text indexes, and memory-graph edges are deleted in one transaction, preventing stale search results or graph links after deletion.
- **More reliable memory saving**: Empty responses, failed calls, and invalid JSON no longer count as successful memory saves. Failed work remains eligible for retry, and the fingerprint window matches the actual model input.
- **Better long-context continuity**: Improved rolling summaries, history chunking, tool-round compression, net-benefit checks, and conversation ordering preserve important assistant notes and tool results instead of silently forgetting them.
- **Safer message recovery**: Outbox messages, user messages, and the first generation checkpoint follow a stricter persistence order. Recovery and retry remain idempotent for message IDs, counters, previews, and timestamps, preventing duplicate generations and incorrect conversation reordering.
- **Cleaner tool-round presentation**: Follow-up replies after client-side tools no longer repeat the same reasoning metadata unnecessarily. Message-interval questions use the timestamps of adjacent real user messages instead of model thinking or tool latency.

## Knowledge Base and RAG

- **Hybrid retrieval fallback**: When vector retrieval fails or is circuit-broken, fallback search now uses FTS/BM25 over actual knowledge chunks instead of only document previews, so later passages in long documents remain searchable.
- **Isolation and safety filters**: Internal-document exclusion, scoped document IDs, and metadata filters stay consistent across vector, FTS, and fallback retrieval. Metadata failures fail closed instead of injecting potentially internal content.
- **Rollback-safe streaming indexing**: If replacement indexing fails, is cancelled, or encounters an embedding-dimension mismatch, the old index is preserved where possible. Once replacement has started, partial DB, FTS, and HNSW results are cleaned up and the document index state is reset.
- **Complete import metadata**: Successful imports now record content hashes and embedding configuration, enabling same-content skips and model-change detection. Large-file hashes are computed incrementally while reading.
- **Consistent injection budgets**: Budget estimation and the text actually inserted into the system prompt use the same truncated snippet, preventing long chunks from bypassing the RAG budget.

## Voice and External Services

- **Unified speech-recognition lifecycle**: Step, Whisper, DashScope, and Realtime controllers now share idempotent disposal semantics; disposed controllers cannot silently reconnect or enter a fake Connecting state.
- **Ordered voice results**: Segment flushes and the final stop flush share a serial lock, while late callbacks from replaced controllers are rejected by generation and identity checks. Leaving a voice conversation fully cancels listening.
- **Recoverable recognition failures**: Failed Whisper segments retain their audio for bounded retry, preventing the final sentence from disappearing; completed English transcripts also keep readable spacing.
- **NewAPI / OpenAI-compatible diagnostics**: Host-only relay URLs receive the common `/v1` API path automatically. If a models endpoint returns HTML instead of JSON, the error now points to the API base URL, proxy route, and web-protection page.
- **OAuth and channel safety**: OAuth logs no longer record raw response bodies. Channel-media cleanup protects files still referenced by inboxes and conversation history, while cursor, conversation, and Agent-checkpoint recovery boundaries are clearer.

## Experience and Quality

- Fixed Row layouts that collapsed text to one or two characters on narrow screens or larger font settings, and added the LAYOUT-01 gate.
- Added regression coverage for knowledge imports, assistant memory, Agent workflows, voice, tool permissions, migrations, and recovery paths.
- Updated Kotlin/Compose quality gates, baselines, and localization checks. The local release candidate passed all five lanes, release-script gates, signing checks, and version checks.
