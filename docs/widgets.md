# 桌面小组件

当前交付五种尺寸：

- **4×1 迷你条**：主数字「请求」+ 成功率 / 首响 / 费用三格小读数；四列统一「数值在上、标签在下」，成功率整十显示 `100%`，整块点击进入概览。
- **4×2 今日概览**：大号请求数 + 成功率 / 首响 / 费用三行小账 + 24 小时柱；柱子只画有数据的真实桶，仅 1 个桶时显示单根 + `数据不足`，柱区高度 22dp。
- **4×2 当前活动**：最多 3 条处理中请求；每条两行：模型一行，来源 + 已运行时长一行，最久的一条模型加粗；`Chronometer` 固定 76dp 槽位，由系统自己走秒。空态「当前空闲」，冷进程「未连接」。
- **2×2 额度**：大号平均剩余百分比 + 进度条 + 窗口数 / 低于 30% 两行脚注。
- **4×1 额度告警**：左侧 warning 色条 + 最严重窗口 + 其余数量；无告警时显示连接状态与更新时间。

## 文件分工

| 文件 | 作用 |
|---|---|
| `WidgetState.kt` | 概览与额度告警快照、实时/落盘两路读取、DataStore 快照 |
| `WidgetLiveState.kt` | 4×2 活动桥：只读进程内 `runningRequests`，冷进程为空 |
| `WidgetThemeSource.kt` | 读取 App `ThemeMode` + 系统 `uiMode`，叠加 widget override |
| `WidgetConfig.kt` | 全局 widget 配置；`WidgetTheme.SYSTEM` 表示「跟随 App」 |
| `WidgetMini.kt` / `WidgetOverview.kt` | 4×1 迷你条 / 4×2 概览 UI 与接收器 |
| `WidgetLive.kt` / `WidgetQuota.kt` / `WidgetQuotaAlert.kt` | 活动 / 额度 / 告警 UI 与接收器 |
| `WidgetPalette.kt` | 五种尺寸共用的 App token 色板与背景 drawable |
| `WidgetConfigActivity.kt` | 配置页；保留 App 的 24dp 卡片圆角，主题预览跟随 override |
| `WidgetRefresh.kt` | 统一刷新入口、主题重绘与 15 分钟 WorkManager 兜底 |
| `res/xml/appwidget_*.xml` | provider 尺寸、更新周期与 `previewImage` |
| `res/drawable/widget_preview_*.png` | 选择界面 5 张预览图（设计师 3x 导出） |
| `res/values/strings.xml` | App 名称与 5 个小组件在系统选择界面的独立名称 |
| `res/layout/appwidget_loading.xml` | Glance 首帧占位布局 |
| `res/layout/widget_live_elapsed.xml` | 4×2 行内的 `Chronometer` |
| `res/drawable/widget_*_background_*.xml` | 26dp 圆角背景（API 24 起生效） |

## 主题与配色

- 唯一颜色来源是 `core/design/Tokens.kt` 的 `LightScheme` / `DarkScheme`；`WidgetPalette` 只做字段映射，widget 里不再写十六进制色值。
- 浅色：背景 `#FFFFFF`、主文字 `#202330`、强调 `#5E59D9`、warning `#986116`；深色：背景 `#1A1E26`、主文字 `#EEF0F6`、强调 `#B0AAFF`、warning `#F3BF71`。
- 5 个 widget 圆角统一 **26dp**。API 24-30 由 shape drawable 提供，S+ 走同一份 drawable，行为一致。
- App 的主题入口是 `pref_theme_mode`（`ThemeMode.SYSTEM/LIGHT/DARK`）。widget 配置的 `SYSTEM` 已改为**跟随 App**；`DARK` / `LIGHT` 仍是显式覆盖。老用户保存的 `SYSTEM` 直接按新语义生效，不做迁移。
- `MagpieApplication` 收集 App 主题变化；`onConfigurationChanged` 会先把系统深浅写入 `WidgetThemeSource.systemDarkFlow`，再调用 `WidgetRefresh.repaintAll`。5 个 widget 的 `WidgetThemeState` 同时收集 config、App `ThemeMode` 与 `systemDarkFlow`，因此已存在的 composition 也会随系统深浅重绘；`updateAll` 的原有语义（写快照 + 重绘）保持不变。
- `Chronometer` 默认色是深色 token `#EEF0F6`，代码里仍按当前主题显式 `setTextColor`。

## 数据与刷新

- 进程内有 `AppViewModel` 时，直接读 `overview`、`connectionStatus`、`quotas` 的当前值；**绝不为此创建** ViewModel，避免小组件冷启动拉起网络栈。
- 冷启动读 `magpie_widget_snapshot` DataStore 快照；App 在概览刷新成功、连接状态或额度变化时写入。
- 额度告警摘要随快照落盘；比例复用 `NotificationText.quotaReadings` 与 `MetricCalculators.calculateQuotaUsedRatio`，阈值复用 `QuotaAlertGate.THRESHOLD`（0.9）。
- 4×2 处理中请求是瞬时数据，不落盘：冷进程返回空列表 + `connected=false`，不伪造活动行。
- `WidgetRefresh.updateAll(context)` 在概览刷新成功后调用，写入快照并重绘所有尺寸。
- `WidgetRefresh.repaintAll(context)` 只重绘、不写快照，用于 App 主题或系统深浅色变化。
- WorkManager 每 15 分钟兜底重绘，不要求网络；失败静默返回成功，不产生通知或重试风暴。

## 状态

| 尺寸 | 状态 | 显示 | 点击 |
|---|---|---|---|
| 迷你条 | 已连接、有快照 | 请求主数字 + 三项读数 | 概览 |
| 迷你条 | 已连接、数据未到 | `—` 占位（不显示 0） | 概览 |
| 迷你条 | 连接失败 / 未连接 | `未连接` | 直接打开连接设置 |
| 概览 | 已连接、有快照 | 大数字 + 三行小账 + 真实数据桶（1 个桶时 `数据不足`） | 请求区 → 请求页；首响/费用 → 用量页；柱状图 → 用量页 |
| 当前活动 | 已连接、有请求 | 最多 3 条，每条两行：模型 / 来源 + 已运行时长 | 路由 |
| 当前活动 | 已连接、无请求 | `当前空闲` | 路由 |
| 当前活动 | 冷进程 / 未连接 | `未连接` | 直接打开连接设置 |
| 额度 | 有数据 | 大百分比 + 进度条 + 两行脚注 | 用量 · 额度子页 |
| 额度告警 | 有 ≥90% 窗口 | warning 色条 + 供应商 + 百分比 + `另有 N 个窗口接近上限` | 用量 · 额度子页 |
| 额度告警 | 无告警 | 已连接 / `HH:mm 更新` 状态条 | 概览 |

## 选择界面

5 个 receiver 在 `AndroidManifest.xml` 里各自带 `android:label`：迷你条 4×1、今日概览 4×2、当前活动 4×2、额度 2×2、额度告警 4×1。对应 `appwidget_*.xml` 通过 `android:previewImage` 引用 `res/drawable/widget_preview_*.png`，两个 4×2 组件用不同预览图区分。预览图由设计师按 328×64dp / 328×110dp / 150×110dp 的 3x PNG 导出。

## 通知配色边界

`LiveKeepaliveService` 的三类通知（常驻、额度告警、请求失败）只用 `setColor` 取当前主题的 accent / warning / error token。折叠态常驻通知的标题只写「今日 N 请求」，处理中数量放在正文；展开行统一为「4:38 · model」，最久的请求排在最前；不再设置 `InboxStyle` summary footer，避免与正文重复。折叠态仍由系统模板绘制，不做自定义 `RemoteViews`、背景、胶囊按钮或三列表格；能改的只有颜色、标题、正文、按钮文字和 `InboxStyle`。small icon 本轮不换图。

## 新增尺寸

1. 新建 `GlanceAppWidget` 与对应 `GlanceAppWidgetReceiver`。
2. 新建 `res/xml/appwidget_<size>.xml`，在 `AndroidManifest.xml` 注册接收器。
3. 把新 widget 实例追加到 `WidgetRefresh.widgets`；点击参数复用 `MainActivity.EXTRA_TAB` / `EXTRA_OPEN_CONNECT`。
4. 用 `WidgetThemeSource.load/observe` 取主题，用 `widgetPalette(isDark)` 取颜色，不要新建字面量色板。

## 构建验证

本机无 JDK / Android SDK，未执行 Gradle。以下需在构建机确认：依赖解析（Glance 1.1.0 / WorkManager 2.9.0）、Manifest 合并、Glance 编译、JVM 单测（`WidgetThemeTest`）、实际桌面添加与三种 ThemeMode 切换。

已知限制：冷进程切换主题时，launcher 持有的旧 RemoteViews 会短暂显示上一套配色，下一次重绘或点击后纠正；MIUI 等系统的灵动通知/小组件裁剪可能改变折叠态观感；字体缩放未锁定，标签保持单行省略，关键数字允许缩小。
