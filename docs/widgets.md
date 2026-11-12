# 桌面小组件

当前交付两种尺寸：

- **4×1 迷你条**：`6774 请求 · 100% · 3.9s · ¥2.68`，整块点击进入概览。
- **2×2 额度块**：大号平均剩余 + 水平进度条 + `37 个窗口` + `18 项 <30%`，点击进入用量页的额度子页。Glance 没有 Canvas 弧形，额度页的圆环在小组件里改为水平进度条，读数仍取同一个 `quotaOverview`。

## 文件分工

| 文件 | 作用 |
|---|---|
| `WidgetState.kt` | 迷你条状态、实时/落盘两路读取、DataStore 快照 |
| `WidgetQuotaState.kt` | 额度块状态，复用 `quotaProviderCards` / `quotaOverview`，独立快照 |
| `WidgetMini.kt` | 4×1 Glance UI、接收器、文案格式化 |
| `WidgetQuota.kt` | 2×2 Glance UI、接收器、文案格式化 |
| `WidgetConfig.kt` | `widget_config` DataStore：内容、主题、刷新策略 |
| `WidgetConfigActivity.kt` | 单 Activity 配置页；保存返回 `RESULT_OK`，取消不写配置 |
| `WidgetRefresh.kt` | 统一刷新入口、事件驱动/15 分钟策略、WorkManager 兜底 |
| `res/xml/appwidget_mini.xml` | 迷你条尺寸与 configure 入口 |
| `res/xml/appwidget_quota.xml` | 2×2 尺寸与 configure 入口 |
| `res/layout/appwidget_loading.xml` | Glance 首帧占位布局 |
| `res/drawable/widget_*_background_*.xml` | 24dp 圆角背景（API 24 起生效） |

## 数据与刷新

- 进程内有 `AppViewModel` 时，直接读 `overview`、`connectionStatus`、`quotas` 与 `displayPreferences` 的当前值；**绝不为此创建** ViewModel，避免小组件冷启动拉起网络栈。
- 冷启动读 `magpie_widget_snapshot` / `magpie_widget_quota` DataStore 快照；App 在概览刷新成功、连接状态变化时写入。
- `WidgetRefresh.updateAll(context)` 在概览刷新成功后调用，写入两类快照并重绘所有尺寸。
- 刷新策略由 `widget_config` 决定：`事件驱动` 只跟随 App 的刷新信号；`每 15 分钟` 由 WorkManager 兜底。两个 provider 的 `updatePeriodMillis` 均为 `0`，避免系统 30 分钟更新绕过配置。
- WorkManager 失败静默返回成功，不产生通知或重试风暴。

## 配置

配置页为全局配置，写入 `widget_config` 偏好文件：

| 分组 | 选项 | 默认 |
|---|---|---|
| 内容 | 今日概览 / 当前活动 / 额度 / 趋势 | 今日概览 |
| 主题 | 跟随系统 / 深色 / 浅色 | 跟随系统 |
| 刷新 | 事件驱动（推荐）/ 每 15 分钟 | 事件驱动 |

取消配置不写 DataStore，已有小组件保持原配置。主题覆盖由每个 `provideGlance` 读取 `WidgetConfig.theme` 决定；刷新策略由 `WidgetRefresh.applyRefreshPolicy` 在 App 启动和保存配置时应用。

## 状态

| 状态 | 显示 | 点击 |
|---|---|---|
| 已连接、有快照 | 对应尺寸的指标 | 概览 / 额度 |
| 已连接、数据未到 | `—` 占位（不显示 0） | 概览 / 额度 |
| 连接失败 / 未连接 | 迷你条显示 `未连接`；额度块有快照时保留上次读数，否则 `暂无额度数据` | 直接打开连接设置 / 打开应用 |

深浅色默认跟随系统 `uiMode`，使用固定色而非 MaterialTheme：深 `#1D1C25` / 文字 `#E9E8F0` / 强调 `#9A92EE`；浅 `#FFFFFF` / 文字 `#1C1A17` / 强调 `#6C5CE7`。配置页同样使用这组颜色，标题使用衬线字体，圆角 24dp。

## 新增尺寸

1. 新建 `GlanceAppWidget` 与对应 `GlanceAppWidgetReceiver`。
2. 新建 `res/xml/appwidget_<size>.xml`，在 `AndroidManifest.xml` 注册接收器，并把 `android:configure` 指向 `WidgetConfigActivity`。
3. 把新 widget 实例追加到 `WidgetRefresh.widgets`；点击参数复用 `MainActivity.EXTRA_TAB` / `EXTRA_OPEN_CONNECT`，额度入口复用 `Notifications.EXTRA_USAGE_SECTION`。
4. 需要配置的尺寸读取 `WidgetConfigStore.observe(context)`；内容选项由 4×2 概览消费。

> 4×2 概览小组件不在当前分支。落地时需要在 `res/xml/appwidget_overview.xml` 加 `android:configure="com.magpie.pocket.widget.WidgetConfigActivity"`，并读取 `WidgetConfig.content`。

## 构建验证

本机无 JDK，以下需在构建机确认：依赖解析（Glance 1.1.0 / WorkManager 2.9.0）、Manifest 合并、Glance 编译（含 `LinearProgressIndicator` 在 API 31 以下使用平台默认 tint）、实际桌面添加、取消配置、深浅色与主题覆盖切换。
