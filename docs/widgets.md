# 桌面小组件

当前交付三种尺寸：

- **4×1 迷你条**：`6774 请求 · 100% · 3.9s · ¥2.68`，整块点击进入概览。
- **4×2 当前活动**：最多 3 条处理中请求；已运行时长用 `Chronometer`，由系统自己走秒。空态「当前空闲」，冷进程「未连接」。
- **4×1 额度告警**：已用比例 ≥ 90% 时显示最严重的窗口与其余数量；无告警时显示连接状态与更新时间。

## 文件分工

| 文件 | 作用 |
|---|---|
| `WidgetState.kt` | 概览与额度告警快照、实时/落盘两路读取、DataStore 快照 |
| `WidgetLiveState.kt` | 4×2 活动桥：只读进程内 `runningRequests`，冷进程为空 |
| `WidgetMini.kt` | 4×1 迷你条 UI、接收器、文案格式化 |
| `WidgetLive.kt` | 4×2 活动 UI、`Chronometer` 行、接收器 |
| `WidgetQuotaAlert.kt` | 4×1 额度告警/状态条 UI、接收器 |
| `WidgetPalette.kt` | 三种尺寸共用的固定深浅色与背景 |
| `WidgetRefresh.kt` | 统一刷新入口与 15 分钟 WorkManager 兜底 |
| `res/xml/appwidget_*.xml` | provider 尺寸与更新周期 |
| `res/layout/appwidget_loading.xml` | Glance 首帧占位布局 |
| `res/layout/widget_live_elapsed.xml` | 4×2 行内的 `Chronometer` |
| `res/drawable/widget_mini_background_*.xml` | 24dp 圆角背景（API 24 起生效） |

## 数据与刷新

- 进程内有 `AppViewModel` 时，直接读 `overview`、`connectionStatus`、`quotas` 的当前值；**绝不为此创建** ViewModel，避免小组件冷启动拉起网络栈。
- 冷启动读 `magpie_widget_snapshot` DataStore 快照；App 在概览刷新成功、连接状态或额度变化时写入。
- 额度告警摘要随快照落盘；比例复用 `NotificationText.quotaReadings` 与 `MetricCalculators.calculateQuotaUsedRatio`，阈值复用 `QuotaAlertGate.THRESHOLD`（0.9）。
- 4×2 处理中请求是瞬时数据，不落盘：冷进程返回空列表 + `connected=false`，不伪造活动行。
- `WidgetRefresh.updateAll(context)` 在概览刷新成功后调用，写入快照并重绘所有尺寸。
- WorkManager 每 15 分钟兜底重绘，不要求网络；失败静默返回成功，不产生通知或重试风暴。

## 状态

| 尺寸 | 状态 | 显示 | 点击 |
|---|---|---|---|
| 迷你条 | 已连接、有快照 | 四项指标 | 概览 |
| 迷你条 | 已连接、数据未到 | `—` 占位（不显示 0） | 概览 |
| 迷你条 | 连接失败 / 未连接 | `未连接` | 直接打开连接设置 |
| 当前活动 | 已连接、有请求 | 最多 3 行「模型 · 来源 · 已运行」 | 路由 |
| 当前活动 | 已连接、无请求 | `当前空闲` | 路由 |
| 当前活动 | 冷进程 / 未连接 | `未连接` | 直接打开连接设置 |
| 额度告警 | 有 ≥90% 窗口 | 最严重供应商 + 百分比 + `另有 N 个窗口接近上限` | 用量 · 额度子页 |
| 额度告警 | 无告警 | 已连接 / `HH:mm 更新` 状态条 | 概览 |

深浅色跟随系统 `uiMode`，使用固定色而非 MaterialTheme：深 `#1D1C25` / 文字 `#E9E8F0` / 强调 `#9A92EE`；浅 `#FFFFFF` / 文字 `#1C1A17` / 强调 `#6C5CE7`。

## 新增尺寸

1. 新建 `GlanceAppWidget` 与对应 `GlanceAppWidgetReceiver`。
2. 新建 `res/xml/appwidget_<size>.xml`，在 `AndroidManifest.xml` 注册接收器。
3. 把新 widget 实例追加到 `WidgetRefresh.widgets`；点击参数复用 `MainActivity.EXTRA_TAB` / `EXTRA_OPEN_CONNECT`。

## 构建验证

本机无 JDK，以下需在构建机确认：依赖解析（Glance 1.1.0 / WorkManager 2.9.0）、Manifest 合并、Glance 编译、实际桌面添加与深浅色切换。
