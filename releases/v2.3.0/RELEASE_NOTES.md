# Muse 2.3.0 归档说明（本地构建 · 记忆修复 + 工具循环/上下文连续性大批次）

- 归档时间：2026-09-28 凌晨（GMT+8）
- 构建来源：`E:\1Project\Muse\1muse` @ commit `5aba0591`（本地共领先远端 58 提交；远端基线 `899b2ef8` = v2.2.0）
- 构建命令：`.\gradlew :app:assembleRelease "-PversionName=2.3.0" "-PversionCode=230"`
- 状态：**本地归档，未推送 / 未打 tag / 未发布**（等用户实测）
- 说明：本包**取代 2.2.1 归档**（2.2.1 从未推送，仅本地流转；其记忆修复已全部包含在本包内）

## 本版包含的修复与改动

### 一、记忆系统归档缺陷根治（2.2.1 批次，含数据救援）
- 版本守卫写死 `≤13` 而库已 v14 → 每次启动把当前真库误归档重建为空库。现守卫与数据库版本三处同源；
- 归档前 `wal_checkpoint` 保真（失败时 WAL 随备份保留）；
- 新增一次性误归档恢复：修复版首启自动从"含有数据的误归档备份"找回记忆（空备份救不回——历史 WAL 已被旧版删除）；
- 记忆页接线归档/恢复提示；长记忆点击展开。

### 二、工具轮次放开（新）
- 新设置「工具轮次上限」：无限制（默认）/100/50/25（设置→聊天→上下文）；
- 单轮总调用上限 60→300；死循环保护（重复调用指纹/无进展/连续失败早停）不变。

### 三、上下文连续性修复（新，核心）
- 窗口外历史不再静默丢弃：生成 `[HISTORY_DIGEST]` 确定性摘录（用户/助手自然语言原文片段 + 工具条目）插在窗口前；
- 工具回合压缩摘要保留"助手说明"预览；
- 压缩调用加 20s 超时护栏（实测曾拖慢首字 ~31s）。

### 四、工具权限分层（新）
- 需 Shizuku/Root、无障碍、Termux 的工具全量标注；find_tools 带 `[needs X; READY/NOT READY]` + 通道状态行；系统提示工具索引标 `*` + 图例；
- 执行前预检：通道未就绪直接返回结构化 `permission_required` 错误（替代"假成功"文案）；
- `screen_permission_status` 提为常驻可见；补登记 4 个漏分类工具。

### 五、技能与诊断（新）
- 提示词技能支持可选 `input` 参数（`{{input}}`/`{{args}}` 占位符；无占位符追加末尾）；
- 内置技能 seed 改为"缺失插入 + 已存在刷新定义字段（保留启停）"——修复老库 schema 永不更新；
- knowledge_search：兜底评分连续化（旧离散分使 threshold≤0.5 完全无区分度——"传 0.5 没效果"的真根因）+ 未命中诊断回显；
- MOOD 调试出口（`MoodDebug` 日志）；`skill_system_guide` 增补 Skill vs Plugin 选择指引。

### 六、UI 与零星修复
- 消息渠道卡片两行式重设计（长名称/长 ID 不再挤成竖排；ID 中段省略）；
- 补 `com.android.alarm.permission.SET_ALARM`（此前 set_alarm/set_timer 被系统拦截）；闹钟失败文案加 `[失败]` 判定标记。

## 产物与校验和（SHA-256）

| 文件 | 大小 | SHA-256 |
|---|---|---|
| Muse_v2.3.0_arm64-v8a.apk | 66MB | `87fd0a8cf8042b30d14eabec42e8881e2616d98ebed4eace3595dd88f0dfebd7` |
| Muse_v2.3.0_armeabi-v7a.apk | 55MB | `c56d221aab2bea8de9b325e6d9dd86aff3c3fa206bf6bb80ced35301fe7aded2` |
| Muse_v2.3.0_universal.apk | 167MB | `10827b06df5b7d8203aa15e5fe19f0c70eab4aa161b03e4887c8bf633b289a75` |
| Muse_v2.3.0_accessibility-provider.apk | 2MB | `e70be8d0d82e727a664e966cf0853ddc5e217a70985ade3307208855b6f179e3` |

> 主应用与 Provider 同一把 release keystore（证书 SHA-256 `e82f4ecde8304b7d78b530336a48e41a42b80c0ebac8af2d2c10448277644ebf`）。
> 三变体包内 `assets/a11y/accessibility-provider.apk` 与独立产物逐字节一致（已验证）。

## 出厂验证记录

- aapt2：`versionCode=230 / versionName=2.3.0` ✓
- 官方校验器 `ci/script/validate_release_apks.py`：三包签名 PASS ✓
- 全量单测（app+memory+ai）复跑绿（2m28s）；新增测试：ContextHistoryDigest 5 / ContextWindowSplit 4 / ToolTraceProjection +1 / ToolPermissionStatus 4 / seedOrRefreshBuiltIn 1 ✓
- ktlint 门禁：app+ai 基线刷新后二跑 SUCCESS ✓
- 记忆恢复演练（MuMu，2.2.1 批次）：制造"含 1 条记忆的误归档备份 + 未消费标记" → 首启日志 `已从归档恢复事实记忆: 1 条` → 连续两次重启事实保持且无新归档 ✓

## 安装注意

- 手机上如装有 2.2.1 修复版/2.2.0 正式版：同签名直接覆盖升级；
- 如装有调试包：需先卸载（签名不同）。

## 归档约定

- 正式归档位：仓库内 `E:\1Project\Muse\1muse\releases\v2.3.0\`（2026-09-28 起启用，APK 由 `.gitignore` 排除，仅作本地产物与 GitHub Release 上传暂存）；
- 已推送 Git 的版本以 GitHub Release 资产为准；2.2.0 及更早未做本地归档。
