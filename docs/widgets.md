# 桌面小组件

当前交付两个尺寸：**4×1 迷你条**（`6774 请求 · 100% · 3.9s · ¥2.68`，整块点击进入概览）与 **4×2 今日概览**（四项指标 + 24 小时迷你柱）。后续尺寸复用同一套数据桥与刷新入口。

## 文件分工

| 文件 | 作用 |
|---|---|
| `WidgetState.kt` | 单例状态、实时/落盘两路读取、DataStore 快照 |
| `WidgetMini.kt` | 4×1 Glance UI、接收器、文案格式化 |
| `WidgetOverview.kt` | 4×2 Glance UI、接收器、四宫格与 24 小时柱 |
| `WidgetRefresh.kt` | 统一刷新入口与 15 分钟 WorkManager 兜底 |
| `res/xml/appwidget_mini.xml` | 4×1 provider 尺寸与更新周期 |
| `res/xml/appwidget_overview.xml` | 4×2 provider 尺寸与更新周期 |
| `res/layout/appwidget_loading.xml` | Glance 首帧占位布局 |
| `res/drawable/widget_mini_background_*.xml` | 24dp 圆角背景（API 24 起生效） |

## 数据与刷新

- 进程内有 `AppViewModel` 时，直接读 `overview` 与 `connectionStatus` 的当前值；**绝不为此创建** ViewModel，避免小组件冷启动拉起网络栈。
- 冷启动读 `magpie_widget_snapshot` DataStore 快照；App 在概览刷新成功、连接状态变化时写入。快照同时保存最近 24 个小时桶的请求量，供 4×2 柱状图使用，不额外发网络请求。
- `WidgetRefresh.updateAll(context)` 在概览刷新成功后调用，写入快照并重绘所有尺寸。
- WorkManager 每 15 分钟兜底重绘，不要求网络；失败静默返回成功，不产生通知或重试风暴。

## 状态

| 状态 | 显示 | 点击 |
|---|---|---|
| 已连接、有快照 | 四项指标 | 概览 |
| 已连接、数据未到 | `—` 占位（不显示 0） | 概览 |
| 连接失败 / 未连接 | `未连接` | 直接打开连接设置 |

4×2 概览的点击分工：请求与成功率进入请求页；首响、费用与柱子进入用量页。未连接或无快照时整块显示 `未连接`，有快照时附带 `上次更新` 时间，绝不把旧数字当作当前数据；小时桶为空时显示 `暂无趋势`。

深浅色跟随系统 `uiMode`，使用固定色而非 MaterialTheme：深 `#1D1C25` / 文字 `#E9E8F0` / 强调 `#9A92EE`；浅 `#FFFFFF` / 文字 `#1C1A17` / 强调 `#6C5CE7`。

## 新增尺寸

1. 新建 `GlanceAppWidget` 与对应 `GlanceAppWidgetReceiver`。
2. 新建 `res/xml/appwidget_<size>.xml`，在 `AndroidManifest.xml` 注册接收器。
3. 把新 widget 实例追加到 `WidgetRefresh.widgets`；点击参数复用 `MainActivity.EXTRA_TAB` / `EXTRA_OPEN_CONNECT`。用量目标同时带 `Notifications.EXTRA_USAGE_SECTION`（非 `quota` 值落在「用量」子页），App 已在前台时 `onNewIntent` 也能切页。

## 构建验证

本机无 JDK，以下需在构建机确认：依赖解析（Glance 1.1.0 / WorkManager 2.9.0）、Manifest 合并、Glance 编译、实际桌面添加与深浅色切换。
