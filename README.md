<p align="center">
  <img src="docs/screenshots/app-icon.png" width="80" alt="Magpie Pocket app icon">
</p>

# Magpie Pocket

[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-purple.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

[中文](#中文) · [English](#english) · [接入说明](docs/integration.md) · [验证记录](docs/verification.md) · [设计 Skill](.agents/skills/magpie-pocket-design/SKILL.md)

**[下载 v1.0.2 APK · Download](https://github.com/CottonChou/magpie-pocket/releases/download/v1.0.2/magpie-pocket-v1.0.2.apk)**（Android 7.0+） · [发布说明 / Release notes](https://github.com/CottonChou/magpie-pocket/releases/tag/v1.0.2) · [设计 Skill ZIP](https://github.com/CottonChou/magpie-pocket/releases/download/v1.0.2/magpie-pocket-design.zip)

v1.0.1 起使用新的发布签名；v1.0.0 及更早版本需卸载后重装，连接与偏好会清除。v1.0.1 → v1.0.2 保持相同签名，可直接覆盖升级。详见[升级说明](docs/integration.md#v101-安装与签名变更)。

v1.0.1 introduced a new signing certificate. Versions 1.0.0 and earlier require uninstall/reinstall, which clears saved data. Version 1.0.2 retains the v1.0.1 certificate and supports in-place upgrades.

## 中文

[Magpie](https://github.com/yetone/magpie) AI 网关的原生 Android 观测客户端。采用 Kotlin、Jetpack Compose 和轻透玻璃风格，提供概览、实时路由、用量、请求账本与更多五个页面。首期聚焦只读观测；路由组由实际请求自动跟随，不需要手动选择。

### 页面预览

以下为 **Android 11 模拟器中的实际应用截图**，覆盖四个主页面（「更多」页见应用内），全部使用应用内标注的演示数据，账号显示为别名。

<table>
  <tr>
    <th>概览 · Overview</th>
    <th>实时路由 · Routing</th>
    <th>用量 · Usage</th>
    <th>请求 · Requests</th>
  </tr>
  <tr>
    <td><img src="docs/screenshots/overview.png" width="210" alt="概览：请求统计、趋势与额度"></td>
    <td><img src="docs/screenshots/routing.png" width="210" alt="路由：请求来源、Magpie、供应商和账号"></td>
    <td><img src="docs/screenshots/usage.png" width="210" alt="用量：Token、缓存命中率和供应商额度"></td>
    <td><img src="docs/screenshots/requests.png" width="210" alt="请求账本：状态、耗时和本地时间"></td>
  </tr>
</table>

**路由动效**：处理中沿实际选中的路径流动，账号状态随请求更新；完成后停止流动。演示录屏用于展示效果，真实连接时由服务端 trace 驱动。

<img src="docs/screenshots/routing-live.gif" width="516" alt="实时路由动效：请求沿连线流向当前供应商与账号">

**请求明细**：完整本地时间、UTC 偏移、时区，以及模型、账号和尝试经过。

<img src="docs/screenshots/request-detail.png" width="300" alt="请求明细：UTC 时间已转换为 UTC+08:00 本地时间">

**显示偏好**：浅色使用白色圆点，深色使用深色圆点；开启后仍与轨道清晰分离。

<img src="docs/screenshots/preferences-light.png" width="260" alt="浅色偏好：选中开关的白色圆点清晰可见"> <img src="docs/screenshots/preferences-dark.png" width="260" alt="深色偏好：选中开关的深色圆点清晰可见">

### 能力

- **概览（现在）**：今日请求、成功率、首响均值与今日费用四项指标，最多 3 条处理中请求，只在有告警时出现的额度提醒，以及今日 24 小时柱图。本页只讲今日，不提供 14 天切换、不显示昨日同期、不放独立额度卡。
- **实时路由（这一次）**：焦点请求卡（来源、路由组、状态、耗时、改道次数）、选路画布、选路依据、尝试时间线与上下文构成；自动跟随全局最新处理中请求及其实际路由组，历史查看保持固定，支持返回实时；页脚给本次启动以来的累计。
- **用量（一段时间）**：今日 / 近 7 天 / 近 30 天 / 全部四档周期下的费用（含未计价次数与信用余额）、Token 构成、缓存命中率与解码速度、按日双线趋势、六维明细，以及标注为实时快照的额度健康。
- **请求（账本）**：搜索、只显示当前连接支持的状态筛选、行内改道徽标（只有带尝试明细时才印数字）、请求明细与尝试时间线。RFC3339 时间按手机本地时区显示，支持纳秒、正负时区偏移和跨日。
- **更多**：供应商目录（模型/价格/并发/密钥数量）、隐私掩码（摘要、日柱、类型排行、记录与按需揭示）、显示偏好与关于。
- **连接认证**：支持 Magpie Web Key / Cookie 会话，以及可选的 Cloudflare Access Service Token。需要两层认证时，两组凭据分别填写；CF Token 不能替代 Magpie Web Key。
- **后台连接**：真实连接默认启用可关闭的常驻通知服务、30 秒轻量心跳与有上限的退避重连。返回前台或网络恢复时重新校验；演示模式不启动后台联网。
- **显示偏好**：浅色 / 深色、三档材质、账号打码与减少动态效果；开关在浅色与深色的开/关状态都有明确的圆点和轨道。系统动画关闭、后台或离线时，路由动画停止。
- **演示模式**：无需服务器即可体验页面；演示与真实连接分开标识。

玻璃效果通过透明色、层级、高光与柔和阴影实现，数据区域优先保证可读性。它是 Android 原生设计实现，不依赖 Apple 系统材质。

### 构建与运行

要求 JDK 17 或 21、Android SDK 34。最低运行版本为 **Android 7.0 / API 24**。

Debug 构建不需要发布密钥。构建 Release 前，将 `signing.properties.example` 复制为 `signing.properties`，填写自己的签名文件及密码；也可以使用 `MAGPIE_KEYSTORE_FILE`、`MAGPIE_KEYSTORE_PASSWORD`、`MAGPIE_KEY_ALIAS`、`MAGPIE_KEY_PASSWORD` 环境变量。真实密钥和本地签名配置均由 Git 忽略；未配置签名时，Release 构建会明确报错。

```bash
git clone https://github.com/CottonChou/magpie-pocket.git
cd magpie-pocket

# 测试与代码检查
./gradlew :app:testDebugUnitTest :app:lintDebug

# 构建 APK
./gradlew :app:assembleDebug :app:assembleRelease
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk` 和 `app/build/outputs/apk/release/app-release.apk`。

启动后可先使用演示模式，也可从“更多”页的网关分组配置管理服务地址与认证信息；概览页的连接胶囊同样跳到那里。地址应指向 **Magpie 管理服务**，不是 `/v1` 模型推理接口。连接探测成功后才保存配置。

### 当前验证

- **204 项单元测试**（`@Test` 计数），最近一次全量运行无失败、错误或跳过。
- **Android 11 / 14 原生验证通过**，覆盖后台心跳、故障恢复、自动路由、冷启动、动效、开关状态及正式包升级；具体范围见验证记录。
- Debug / Release 构建成功；Lint 无错误；正式 APK v2 / v3 签名验证通过。

接口能力由实际探测决定；缺失的数据保留为空，不用估算值补造。历史查询范围和详情保留期取决于服务端。具体范围见[验证记录](docs/verification.md)。

## English

**Magpie Pocket** is a native Android observability client for the [Magpie AI gateway](https://github.com/yetone/magpie), built with Kotlin and Jetpack Compose. It offers five pages — Overview, Routing, Usage, Requests and More — and focuses on read-only monitoring with a restrained glass-inspired interface.

The screenshots and animation above are captured from the actual Android app using clearly labeled **Demo data**; the table covers the four main pages, and the More page is shown in the app.

- **Overview**: today's requests, success rate, mean TTFT and today's cost, at most three in-flight requests, quota alerts only when a window is near its limit, and today's 24-hour bars. Today only — no 14-day toggle, no same-time-yesterday comparison and no separate quota card.
- **Background connection**: optional foreground notification service, a lightweight 30-second heartbeat and reconnect backoff capped at 30 seconds per delay; no live service in Demo mode.
- **Routing**: a focus card (source, group, state, duration, reroute count), the route canvas, routing evidence, the attempt timeline and the context breakdown; automatic group selection from the latest live request, trace-driven flow and a startup-total footer. Inspect historical requests without losing the selection, then return to live following.
- **Usage**: four periods (today, 7 days, 30 days, all), cost with unpriced count and credit balance, token composition, cache hit rate and decode speed, a dual-line daily trend, the six-dimension breakdown and a quota-health snapshot.
- **Requests**: search, filters limited to the states the connection supports, an inline reroute badge that only prints a count when attempts exist, and attempt details. Timestamps are parsed as instants and rendered in the device timezone, including explicit offsets and nanosecond timestamps.
- **More**: provider catalog (models, prices, concurrency and key counts), privacy masking (summary, daily bars, type ranking, mask log and reveal on demand), display preferences and about.
- **Access**: Magpie Web Key sessions and optional Cloudflare Access service-token headers, with encrypted credential storage.
- **Accessibility**: account masking, light/dark themes and reduced motion. Routing motion also stops when system animations are disabled, the app is backgrounded or the connection is offline.

Requires **Android 7.0+ (API 24)**. Build with JDK 17/21 and Android SDK 34 using the commands above. The interface uses layered tints, highlights and shadows; it does not claim Apple system-material or optical-refraction support.

Debug builds need no release key. For Release builds, copy `signing.properties.example` to the ignored `signing.properties` and supply your own signing credentials, or set the four `MAGPIE_KEYSTORE_*` / `MAGPIE_KEY_*` environment variables listed above. Private signing material must never be committed. Release builds fail explicitly when signing is not configured.

Validation: **200 unit tests** (`@Test` count), Android 11/14 native checks for background recovery, routing, switches and upgrades, successful Debug/Release builds and no lint errors. See [verification](docs/verification.md), [integration](docs/integration.md) and [design decisions](docs/decisions.md).

## 复用设计 · Reuse the design

项目内置 [$magpie-pocket-design](.agents/skills/magpie-pocket-design/SKILL.md)，包含[详细规范](.agents/skills/magpie-pocket-design/references/design-spec.md)、精确参数和用户确认的参考图。可交给其他 AI 使用，也可将该目录复制到支持 skill 的工具目录中安装。规范明确了自动路由、开关各状态与 Android 原生安全区，不把旧图中的分组下拉照搬回新版。

## License

项目本体 [MIT](LICENSE)。界面图标：Material Icons (Apache-2.0)；鸟标：Lucide (ISC)。

[MIT](LICENSE). The bird mark adapts Lucide artwork under the ISC license; attribution is retained in [design decisions](docs/decisions.md#bird-artwork-attribution). UI icons come from Material Icons (Apache-2.0).
