# Magpie Pocket 架构与技术决策 (docs/decisions.md)

## 1. 架构与选型

- **UI 框架**：Kotlin 2.0 + Jetpack Compose + Material 3
- **最低兼容系统**：Android 7.0 (`minSdk = 24`)，目标与编译系统 `targetSdk = 34`, `compileSdk = 34`
- **并发与响应式**：Kotlin Coroutines + StateFlow / Flow + ViewModel
- **网络与序列化**：OkHttp 4.12.0 + kotlinx.serialization.json 1.6.3
- **安全与存储**：AndroidX Security Crypto (EncryptedSharedPreferences / Keystore) + Jetpack DataStore Preferences

## 2. 视觉材质 Liquid Glass 分层与降级

`GlassSurface` 将控制层与数据区域区分开：悬浮底栏、按钮与选择器采用透明色、高光和柔和阴影，数据面板保留清晰背景。所有文字与图标保持清晰，不对包含内容的整个节点施加模糊。

- `CLEAR`：按主题使用轻透色层。
- `TINTED`：提高色层不透明度。
- `SOLID`：使用不透明表面。

这是 Android 原生的玻璃风格实现，不声称实现光学折射或 Apple 系统材质。三档材质均支持最低 API 24。

## 3. 统计口径与算法实现

- **首响均值（TTFT Mean）**：默认采用加权算术均值 `ttftSumMs / ttftSampleCount`，不将均值标为中位数。
- **请求输出速度**：请求级口径，按 `output / ((durationMs - ttftMs) / 1000)` 计算，`output` 含 reasoning；`ttftMs` 缺失时直接返回 null，不按 0 处理（否则会把首响时间算进 decode，得到虚高速度）；输出或耗时缺失/非正、解码时长非正时也返回 null。账本行整项不显示，详情显示“—”，不做上限截断。
- **总 Token 归一化**：`inputUncached + output + cacheRead + cacheWrite`。上游输出中包含的 reasoning Token 不重复累加。
- **加权缓存命中率**：`sum(cacheRead) / sum(inputUncached + cacheRead + cacheWrite)`。分母为 0 时输出 null（界面显示“—”），而非错误记为 0%。
- **改道次数独立于终态**：`rerouteCount` 仅记录发生候选交接的次数；有 attempts 时改道请求既可能最终成功（`成功 · 改道 1 次`），也可能最终失败（`失败 · 改道 2 次`）。账本行没有 attempts、只有上游 retried，徽标只写 `失败 · 有改道`，不把 1 当次数。
- **账号打码保护**：默认开启别名遮蔽，所有前台展示映射为 `账号 01` 或 `ad***n@domain`，明文凭证仅留在内存。

## 4. 演示模式 (Demo) 与真实服务模式 (Live) 物理隔离

- `DemoRepository` 与 `LiveMagpieRepository` 均实现统一接口 `MagpieRepository`。
- 未连接时不生成看似真实的假数据；Demo 模式下界面常驻“演示数据”胶囊标签。
- 无论离线还是接口缺失，均将错误诚实表达为产品状态，不编造虚假网关指标。


## 2026-10-22 UI reference alignment

The attached routing mockup is the visual baseline: cool white background with restrained lavender/blue ambient fields; a dark rounded bird mark; horizontal metric rules; a single opaque route canvas; pale lavender selection; compact decision/context rows; and one floating four-tab capsule with visible labels and thin outline icons. Native Android status/navigation areas remain native. Other screens share these tokens and surface rules.

Glass is rendered with translucent tint, a restrained highlight and soft shadow. This Android implementation does not implement optical refraction or claim Apple system material compliance. Data panels remain opaque and readable. A single request-detail host is shared across tabs; selecting a routing focus alone does not open it.

The implementation is verified against Android emulator screenshots, with shared outline icons, layout tokens and API-24-compatible timestamp handling.

### Bird artwork attribution

`BirdMark.kt` adapts Lucide's bird artwork (https://lucide.dev/icons/bird).

ISC License

Copyright (c) Lucide Contributors

Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted, provided that the above copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.


## 2026-10-22 Authentication and upstream compatibility

Connection validation and restored runtime sessions share MagpieSession rules. Each connection gets an isolated client; runtime reauthenticates with the encrypted saved Web Key before API loading or trace. A protected /api/state response is required. The probe itself confirms only trace and groups, and hands both bodies to the runtime so one refresh reads /api/groups and /api/gateway/trace?after=0 once. Usage, ledger, quotas, providers and masking stay unknown (em dash) until their first real request answers 200 or 404; a transient failure keeps the previous answer instead of claiming availability. A re-probe of an already connected session keeps the connected status; only a failed request downgrades it. Authentication failures remain actionable until explicit retry or reconnection and cannot be erased by old trace emissions.

Current upstream response unions/aliases are parsed explicitly, including numeric IDs, nested route metadata, usage tiers, ledger rows and quota arrays. Quota percentages and fractional absolute counts are represented separately. Credentials and raw authenticated responses are excluded from source, fixtures and verification artifacts. See docs/verification.md for exact scope and production-login limitations.


## 2026-10-22 Local time and trace-driven routing

Request timestamps are parsed as RFC3339 instants and rendered in the device timezone; nanosecond fractions are normalized without changing the UTC instant. The Material date picker continues using UTC-encoded calendar dates. Generated metadata uses true UTC timestamps.

Trace snapshots expose normalized summaries and full details from the same retained cache. Live routing follows the globally newest observed in-flight request and derives its group automatically; without in-flight activity it uses the latest eligible completion. Request-detail modals retain their own selection. Provider/account changes, retries and completion update the graph and evidence together. Epoch/sequence restarts clear stale live state, and deployments without explicit retained IDs use a bounded 60-route cache.

Motion is tied to real in-flight state. Completion feedback is finite and consumed when finished or leaving the canvas. App/system reduced motion, lifecycle and connection state gate animation. Historical inspection stays pinned until returning to live following.


## 2026-10-23 Background lifetime and accessible controls

MagpieApplication owns one AppViewModel shared by the Activity and LiveKeepaliveService. A lightweight 30-second heartbeat and capped reconnect delays keep LIVE observations recoverable. Background traffic requires a promoted service and a loaded, enabled preference; foreground traffic remains usable while preferences load. Every HTTP call closes on coroutine cancellation, including pending response bodies.

A foreground-service request made while the Activity was visible may finish starting after Home is pressed. The service promotes before inspecting configuration, waits for definitive stored preferences, and stops for disabled or Demo state. Authentication failures remain sticky until explicit retry. Notification permission denial does not block the foreground-service contract.

MagpieSwitch provides explicit thumb/track colors, labels, state descriptions and 48dp minimum touch layout. Compact routing headers preserve the actual group name without duplicated follow labels. AppChrome has a 64dp minimum and grows with text; content reserves its measured height so enlarged gateway status text is not clipped.

The reusable visual specification is maintained in .agents/skills/magpie-pocket-design. It includes the approved image and identifies later interaction requirements that supersede its old group picker.

## 2026-11-10 IA v2 数据契约（W1）

页面不再各自计算同一口径，AppViewModel 直接暴露可渲染的派生值：

- **改道次数**：RequestDerivations.rerouteCount(attempts, retried)。有 attempts 时取 max(0, attempts.size - 1)；账本没有 attempts，只回退上游 retried；两者都无则为 0。LIVE 的 RUNNING / REROUTED 筛选在本地对同一批账本行生效，ERROR 仍由账本 failed=1 服务端筛选。
- **改道次数（列表徽标）**：RequestDerivations.rerouteBadgeCount(attempts, retried) 只在有 attempts 时返回可打印的数字；账本行只有 retried 时返回 null，徽标显示“有改道”，不把 1 当次数。详情页仍用 rerouteCount 的数字。
- **尝试状态**：Attempt.timelineState(routeState)（core/routing）是请求详情与路由页共用的唯一映射；自身没有 http 状态与错误码的尝试跟随请求终态，请求未定论时留在 warning（琥珀），不判 FAILED。
- **费用**：CostDisplay(value, unpriced, hasUnpriced)；cost = 0.0 且 unpriced > 0 时页面仍能显示“N 次未计价”，不再把未计价吞成零。
- **额度**：额度是供应商维度，不是账号维度。`/api/usage/quotas` 以 `provider` / `name` / `windows` 为主，实测 19 行里 13 行带 `user`、6 行不带，从不带 `account`，所以 AccountQuota 以 providerId 为键、accountId 可空。VM 把同一 provider 的多条合并（窗口全部保留，同名窗口按出现顺序编号），丢掉没有任何窗口的 provider，再为每个 provider 只暴露 AccountQuotaView.tightestWindow（ratio 最大的窗口）并给出 QuotaAlert。AccountQuota.known 只表示这次回答是否可信（取上游 q.known），窗口有没有数值由 QuotaWindow.known 单独说，否则“全部无限额”会被误判成“未提供额度”。窗口数值的绝对值/百分比在映射时分开：`used` 在 0..100 内一律是百分比（实测 37 个窗口里 23 个带 `limit`，`used` 仍只是 0–100 的比例，带 limit 不代表是绝对数），只有 `used` 大于 100 才当绝对数；`amount` 与 `limit` 同时存在时比率优先用 `amount / limit`（比百分比更精确）。上游标 `text:true` 的窗口只有 display 文本，`used` 不是读数：`known` 仍为 true（有文字可显示），但不参与百分比与比率。供应商名不打码；只有上游真的给了账号，才按偏好打码并追加在供应商名之后。视图按 ratio 降序（最差在前，圆环取第一条）；同一 provider 内同名窗口一条不丢，显示时编号 #2、#3（SVC 的做法）。重置时间由 RequestTimestamps.formatResetAt 处理：3 天内显示“N 小时后 / N 天后”，更远显示“M月d日”。
- **账号明细来源**：路由页账号按 `/api/groups` 的 `memberInfo[].account` → `/api/usage` 的 accounts 维度 → `/api/usage/quotas` 的账号 合并，按 id 去重且 groups 胜出；usage/额度来源的可用性记为 UNKNOWN，usage 来源标 `fromUsage`。额度行只有带 `user` 时才贡献账号行（实测 19 行中 13 行），供应商名不该被当成账号。合并在 VM 的同一次刷新内复用已加载的面板数据，不新增请求；只有 usage 来源时 sheet 注明“仅显示近期有流量的账号”，没有任何来源时显示“网关未提供账号级明细”。
- **统计日**：statisticsDay 由 Capabilities.statisticsTimeZone（缺失时回退请求 SourceMeta）换算 rangeEnd，没有时区时取服务器字面日期。
- **趋势**：DailyTrendPoint 带日期、请求数与当日已计价费用（上游 series 的 cost）；UsageSnapshot.dailyTrends 保留所选周期的 series，概览 history 不可用时 VM 回退到该序列，两者都没有才为空列表，不补零。usageDecodeSpeed 与概览使用同一公式。
- **路由聚焦**：VM 提供 focusRoute(routeId)，按 id 聚焦但仍保持自动跟随；runningRequest（单条）保留，runningRequests 最多三条按 startedAt 倒序。MainActivity 的接线在后续任务。
- **覆盖率**：dataCoverage 取 overview / usage / 请求页中最差的 Coverage，并带 stale。
- **缺失不等于零**：TokenUsage 四个字段与 RequestSummary.usage、QuotaWindow.used/limit 保留 null；下游统一用 MetricCalculators.formatTotalTokens 输出“—”（页面接线在后续任务）。sameTimeYesterdayCompletedRequests 字段保留，但不再有数据来源。


## 2026-11-10 按时间尺度重组五屏信息架构

**背景**：重构前同一指标在多页各算一遍，口径彼此不一致。概览同时放“较昨日同期”差值、“近 14 天”切换、解码速度和一块“需要留意 · 额度”卡；用量页也放费用、Token 与额度健康。同一组数字因此在两页各有说法，读的人分不清哪个是今日、哪个是周期、哪个是当下快照。重构前的审计发现同一指标在多处重复，且口径不一致。

**决策**：每页只负责一个时间尺度。

- 概览 = 现在：今日四项统计、最多 3 条处理中请求、仅告警时出现的额度提醒、今日 24 小时桶。
- 路由 = 这一次：单条请求的焦点卡、选路画布、选路依据、尝试过程、上下文构成，页脚给本次启动以来的累计。
- 请求 = 账本：可搜索、可筛选、可分页的历史行，行内改道徽标只说有没有，详情才给次数。
- 用量 = 一段时间：四档分段下的费用、Token、按日趋势、六维明细与额度健康快照。
- 更多 = 网关、数据、偏好与关于：网关连接状态与服务能力、不归属任何统计的供应商目录、隐私掩码、显示偏好。

**影响**：

- 概览删除 14 天切换、解码速度和独立额度卡；昨日同期字段保留但已无数据源，恒为空值而不是 0。
- 四档分段只存在于用量页；解码速度与额度明细也只在用量页出现。
- 尝试状态统一走 `core/routing` 的 `Attempt.timelineState`：路由页与请求详情读同一条映射，同一次尝试不会在两页判成两种颜色。
- 改道徽标不再猜数字：有尝试明细才印次数，账本行只拿到上游 retried 标记时显示“有改道”。
- 额度一律标注为实时快照，与周期无关；信用余额标“不随周期变化”。
- 每个区块自带口径标签（今日 / 该周期 / 本次启动以来 / 实时快照），缺失值保持“—”，不补零。

## 2026-11-11 额度「已用 / 剩余」双显示（默认剩余）

**背景**：额度行只印已用比例，而读的人关心的是“还剩多少”。真实网关的窗口同时给了 `used` 百分比、`amount` / `limit` 与上游的 `display` 文本，两种口径都能算出来。

**决策**：

- 偏好新增 `pref_quota_display_mode`（`used` / `remaining`，默认 `remaining`），写在 `DisplayPreferences` 里随 `displayPreferencesFlow` 发布；用量页「全部供应商」右侧用两格小胶囊切换，切换即写偏好。
- `AccountQuotaView` 同时带两种读数：`usedPercent` / `remainingPercent`（0..100）、`usedAmount` / `remainingAmount` / `unitLabel` / `displayText`。`ratio` 恒为已用比例。
- **排序与颜色永远按已用 ratio**：按 ratio 降序、≥ 0.9 红、≥ 0.7 黄。剩余模式下若按剩余排序，最危险的会沉到最底。
- 圆环取当前模式的百分比并在圆心标注「已用 / 剩余」；ratio ≥ 0.9 时轨道改用淡 error 色，剩余 0% 时不至于只剩一个空圈。
- 行副标题写绝对值对：`已用 4752.5 / 4900 credits` 或 `剩余 147.5 / 4900 credits`；只有百分比时写百分比；`text` 窗口写上游原文；无限额仍写「无限额」。大于一万的数字转紧凑写法（`200.0M`），避免一行塞下九位数字。
- 详情 sheet 用同一套措辞（`quotaWindowText`），上游给了 `display` 就照抄。

**影响**：

- `quotaFigureIsCount` 收敛成只判 `used > 100`：此前“带 limit 就是绝对数”会把 50/500 算成 10%，真实网关 23 个带 limit 的窗口全部算错。
- 时间范围分段（文字 + 下划线）本轮不改，只做额度。

## 2026-11-11 额度改为供应商卡片（卡内展开）

**决策**：

- 额度子页按供应商成卡：卡头供应商名 + 最紧窗口的显示百分比（有余额时显示余额原文），4dp 条，下面是最紧窗口的计数/百分比与重置行；卡底按钮在卡内展开全部窗口，不再打开底部 sheet。
- 总览卡用全部窗口的剩余比例算术平均（排除 `unlimited` / `text` / 无读数窗口），剩余低于 30% 计数；最低/最高按供应商最紧窗口。
- 多账号供应商先在卡内给「N 个账号合计」块，再列账号；只有 ≥2 个账号共有的同名窗口才合计：计数窗口 `amount` / `limit` 相加，纯百分比只在同 plan 账号间平均，`text` / `unlimited` 跳过。
- 账户默认展开 `lastServedAt` 最大的一个，其余用 2 列简略网格；账号行点击切换，卡底可一次展开全部或收成简略。（已由下节「额度卡片三层递进」取代）
- Pace = `used - 100 * (now - startsAt) / (resetsAt - startsAt)`；正数「比均速快」，负数「比均速慢」，窗口两端无效时不显示。
- 余额卡显示上游给的余额原文且不画条；无限额显示「无限额」；纯文本窗口照抄上游 `display`；纯百分比窗口只显示百分比。
- 已用/剩余切换继续作用于所有数字和条；切换只改读数方向，不改最紧窗口、排序与颜色。

**影响**：`QuotaWindow` / `AccountQuota` 增加 `startsAt`、`plan`、`balance`、`until`、`lastServedAt`；`app/QuotaCards.kt` 与旧 `QuotaProviderViews.kt` 并存，旧视图仍供概览告警使用。

## 2026-11-11 额度卡片三层递进

**背景**：多账号卡默认把四个账号连同各自的窗口网格一起铺开，首屏信息过载。

**决策**：

- 多账号卡分三层：层 1 只留卡头与「N 个账号合计」块；点合计块（整块可点）进入层 2 的账号列表；再点账号行右侧 chevron 进入层 3，展开该账号的窗口。
- 合计块按 `mergedWindows` 每个窗口一行显示名称、计数/百分比和 3dp 条；没有可合计窗口时只写「N 个账号」并保留 chevron。
- 账号行显示账号名与该账号已用比例最大的窗口读数，行本身不响应点击，只有 chevron 触发展开。
- 两个展开集合默认都为空；单账号卡仍用卡底「查看 N 个窗口 / 收起」展开现有窗口列表。
- `QuotaCards.kt` 新增 `quotaAccountTightest`，按已用比例最大选账号最紧窗口；删除按 `lastServedAt` 默认展开的 `defaultQuotaAccountKey` / `defaultExpandedQuotaAccounts`。

## 2026-11-11 动效统一入口与额度计数方向

**背景**：额度页与底部五个 tab 都是硬切换，圆环和额度条直接跳到目标值；同时额度行的计数恒为已用量，剩余模式下花光的窗口会同时印出「500 / 500 credits」和「0% 剩余」。

**决策**：

- `core/design/Motion.kt` 是全应用唯一的动效预算：`motionDuration(base)` 在系统关闭动画（`ValueAnimator.areAnimatorsEnabled()`，Android 8 以下按开启处理）或显示偏好 `reduceMotion` 打开时返回 0，所有 `tween` 时长都取自它，动效关闭时连错峰等待一起消失。缓动统一 easeOutCubic `CubicBezierEasing(0.215, 0.61, 0.355, 1)`：不弹跳、不 overshoot。
- 时长取 SVC Dashboard 的取向、magpie Web 的规则：额度总览圆环 760ms（圆环与圆心百分比同一时长同一曲线），所有额度条 560ms，三层展开 340ms / 收起 260ms（收比开快），chevron 旋转 220ms（同一枚箭头旋转，不再换字形），用量/额度子页切换 260ms 水平滑动（方向由 tab 索引决定，向右切换从右进），时间范围切换 180ms 淡入淡出（不滑动），底部五个 tab 220ms 淡入加内容 8dp 上移。
- 额度条首次进入从 0 长到目标，之后每次读数变化继续走同一段动画：`rememberEntered()` 让首帧取 0，再交给 `animateFloatAsState`，所以切换已用/剩余也是滑过去的。
- 供应商卡首次组合时 320ms 淡入并上移 10dp，按列表索引错峰 `min(index, 6) * 40ms`：最多 6 张卡错峰，滚动到新卡才入场的会再播一次，纯重组不重播。
- 计数方向：`QuotaCards.kt` 的 `countText` 接收 `displayMode`，`REMAINING` 时分子是 `limit - amount`（下限 0），`USED` 时分子是已用量；单窗口与多账号合计同一条规则。剩余模式下花光的窗口因此读作「0 / 500 credits」，与「0% 剩余」一致。

**影响**：新增 `MotionTest`（时长开关）与 `QuotaCardsTest.aCountWindowReadsTheDirectionThePagePrints`（两种方向）；`UsageScreen` 的额度卡改为 `itemsIndexed`，卡片入场靠 `graphicsLayer`，不加任何常驻动画。
