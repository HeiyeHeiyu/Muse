# Muse v2.3.1

本次以知识库导入与稳定性为主：压缩包可以直接导入并自动建库，大文件导入不再闪退或卡住；同时修复了崩溃日志面板为空、Agent 页偶发串会话等问题。

## 重要修复

- **44MB 压缩包导入闪退**：修复了根因——压缩包与二进制文件此前被当作纯文本读取并建立索引。现在压缩包会被正确识别，不再闪退；其他二进制文件在主知识库页也会被拦下并给出提示，不再把乱码内容写进知识库。
- **崩溃日志面板与导出为空**：崩溃日志此前读错了目录，升级后历史崩溃记录会正常显示并可导出。
- **Agent 页偶发串会话**：快速切换会话或助手时，不再出现"另一个会话的内容闪现"。
- **导入成功但仍显示"0 篇文档"**：知识库文档数改为实时统计，导入或删除后立即显示正确。
- **大文件导入卡住界面**：文档分块的 CPU 与磁盘操作移出主线程，导入过程中界面保持可用。

## 新能力

- **压缩包直接导入**：在知识库管理页选择 zip，应用会自动解压、以压缩包名新建知识库，并逐个导入其中的文档（支持 md / txt / csv / json / log / pdf / docx / doc / epub / pptx）。目录、隐藏文件与 macOS 元数据会自动跳过，单次最多处理 500 个条目。

## 体验改进

- **下载与更新入口统一**：首页的更新提示与 设置 → 关于 里的下载按钮，现在都打开官网下载页，国内直连更稳。
- 补齐终端、群聊、工具与搜索相关文案在 7 种语言（中文 / 英文 / 西班牙文 / 日文 / 韩文 / 葡萄牙文 / 俄文）下的翻译。

---

# Muse v2.3.1 (English)

This release focuses on knowledge-base import and stability: archives can be imported directly and become their own knowledge base, and large-file imports no longer crash or freeze. It also fixes an empty crash-log panel and occasional session mix-ups on the Agent page.

## Important Fixes

- **Crash when importing a 44 MB archive**: fixed the root cause - archives and binary files were previously read as plain text and indexed. Archives are now recognized correctly and no longer crash; other binary files are also rejected on the main knowledge page with a clear message, instead of writing garbled content into your knowledge base.
- **Empty crash-log panel and export**: crash logs were being read from the wrong directory; existing crash records now show up and can be exported.
- **Occasional session mix-ups on the Agent page**: rapidly switching sessions or assistants no longer makes another conversation's content flash on screen.
- **Import succeeded but still shows "0 documents"**: document counts are now computed live, so they are correct immediately after importing or deleting.
- **Large-file imports freezing the screen**: chunking work (CPU and disk) now runs off the main thread, so the interface stays responsive while importing.

## New Capabilities

- **Import archives directly**: pick a zip in the knowledge-base management page and the app will extract it, create a new knowledge base named after the archive, and import the documents inside one by one (md / txt / csv / json / log / pdf / docx / doc / epub / pptx are supported). Directories, hidden files, and macOS metadata are skipped, up to 500 entries per import.

## Experience Improvements

- **Unified download and update entry**: the update banner on the home screen and the download button under Settings -> About now both open the official download page, which is faster to reach from mainland China.
- Completed the translations for terminal, group-chat, tool, and search strings in all seven languages (Chinese, English, Spanish, Japanese, Korean, Portuguese, Russian).
