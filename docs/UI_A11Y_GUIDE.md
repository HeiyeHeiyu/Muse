# UI 无障碍(a11y)标注约定

> 适用范围:app 模块 Compose UI。
> 与 [`ACCESSIBILITY.md`](./ACCESSIBILITY.md) 的区别:那份文档描述**给 AI 用的无障碍服务**(UI 自动化能力);本文档是**面向视障用户**的 UI 语义标注约定。
> 建立:v2.x(基于 2026-09 全面审计的 C11 项)。

## 硬性要求

1. **触摸目标 ≥ 48dp** — 统一走 `MuseIconSizes.touchTarget`,CI 由 `ci/script/check_touch_target.py` 抽查。
2. **可交互图标必须有 contentDescription** — 纯装饰性图标(min-contentDescription=null)需明确传 `null`;
   新 Icon 一律显式给出二者之一,不留默认。CI 由 `check_icon_content_description.py` 抽查。
3. **状态必须进入语义** — 选中/开关/展开等状态用 `Modifier.semantics { selected = ...; role = Role.Checkbox }`
   (参考 `MuseChip`);开关用 `Modifier.toggleable(role = Role.Switch)`(参考 `MuseSwitch`)。
4. **自绘内容必须可读** — Canvas 图表/波形/热力图等自绘元素补 `contentDescription` 描述数据大意
   (如"最近 30 天活动热力图,峰值出现在周三")。
5. **动态提示走 MuseToast** — 已内置 `liveRegion = LiveRegionMode.Polite`,不自行实现播报。

## 推荐做法

- **设置类行**:优先复用共享组件(`MuseListItem` / `SettingsSwitchRow` / `SettingsItemRow` / `MuseSlider`),
  语义与触控目标已内置,不要手搭行结构。
- **复杂组合控件**:外框保证 48dp 触摸目标(视觉可以更小,参考 MuseSwitch 的 51×31dp 视觉 + 48dp 外框模式)。
- **文本缩放**:不设固定高度截断;字号走既有 4 档缩放,长内容允许换行。
- **动效**:一律走 `MuseMotion` 令牌(已接入系统"关闭动画"偏好与 reduced-motion)。
- **对比度**:正文/背景目标 WCAG AA;StatusColors 深浅成对使用,不自行调透明度破坏对比。

## 走查清单(发版前)

- [ ] 开 TalkBack 走核心链路:启动 → 会话列表 → 聊天 → 发送 → 设置 → 主题切换
- [ ] 系统字体放大到最大档,检查聊天页/设置页无截断
- [ ] 开"移除动画"后检查动画均静态降级
- [ ] 深色模式下抽查 3 个主要页面
