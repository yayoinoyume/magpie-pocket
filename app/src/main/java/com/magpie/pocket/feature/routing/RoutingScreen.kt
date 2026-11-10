package com.magpie.pocket.feature.routing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.magpie.pocket.core.design.AttemptEntry
import com.magpie.pocket.core.design.AttemptTimeline
import com.magpie.pocket.core.design.DataPanel
import com.magpie.pocket.core.design.BirdMark
import com.magpie.pocket.core.design.DecisionMark
import com.magpie.pocket.core.design.KvGrid
import com.magpie.pocket.core.design.MagpieColorScheme
import com.magpie.pocket.core.design.MagpieDimensions
import com.magpie.pocket.core.design.MagpiePageHeader
import com.magpie.pocket.core.design.MagpieTheme
import com.magpie.pocket.core.design.MagpieTypography
import com.magpie.pocket.core.design.SectionHeader
import com.magpie.pocket.core.design.StatusBadge
import com.magpie.pocket.core.design.displayOrDash
import com.magpie.pocket.core.design.springPress
import com.magpie.pocket.core.model.Account
import com.magpie.pocket.core.model.Attempt
import com.magpie.pocket.core.model.ContextBreakdown
import com.magpie.pocket.core.model.RequestDetail
import com.magpie.pocket.core.model.RequestSummary
import com.magpie.pocket.core.model.RouteGroup
import com.magpie.pocket.core.model.TraceSnapshot
import com.magpie.pocket.core.model.FinalState
import com.magpie.pocket.core.routing.RoutePhase
import com.magpie.pocket.core.routing.RouteSettle
import com.magpie.pocket.core.routing.attemptEvidence
import com.magpie.pocket.core.routing.routePhase
import com.magpie.pocket.core.routing.timelineState
import com.magpie.pocket.core.routing.visibleProviderIds
import com.magpie.pocket.core.util.MetricCalculators
import android.provider.Settings
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private sealed interface RoutingSheet {
    data object AllProviders : RoutingSheet
    data class ProviderAccounts(val providerId: String) : RoutingSheet
    data class AccountDetail(val providerId: String, val account: Account) : RoutingSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingScreen(
    trace: TraceSnapshot?,
    groups: List<RouteGroup>,
    accounts: List<Account>,
    selectedGroupId: String?,
    selectedRequest: RequestSummary?,
    requestDetail: RequestDetail?,
    onSelectRequest: (RequestSummary) -> Unit,
    onResetToLatest: () -> Unit,
    onOpenRequestDetail: (RequestSummary) -> Unit,
    isDemo: Boolean = false,
    isLoading: Boolean = false,
    isOnline: Boolean = true,
    isFollowingLive: Boolean = true,
    settle: RouteSettle? = null,
    onConsumeSettle: (Long) -> Unit = {},
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    val preferences = MagpieTheme.preferences
    var showDecisionModal by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<RoutingSheet?>(null) }

    // Automatic routing never falls back to the first configured group. The
    // selected request is the only evidence of the active group.
    val currentGroup = selectedRequest?.groupId?.let { id -> groups.find { it.id == id } }
        ?: selectedGroupId?.let { id -> groups.find { it.id == id } }
    val groupProviders = currentGroup?.providerIds ?: emptyList()
    // Highlight only with matching request/attempt evidence. A running route id from a
    // different group is not evidence for this group.
    val matchingSelection = selectedRequest
    val evidence = attemptEvidence(matchingSelection)
    val activeProvider = evidence?.first
    val activeAccountId = evidence?.second
    val activeAccounts = activeAccountId
        ?.let { id -> accounts.find { it.id == id } }
        ?.let { listOf(it) }
        ?: emptyList()
    val visibleProviders = visibleProviderIds(groupProviders, activeProvider, 2)
    val extraProviders = groupProviders.filter { it !in visibleProviders }
    val phase = routePhase(matchingSelection, trace, pinned = !isFollowingLive, online = isOnline)
    val settleMotion = isFollowingLive && isOnline && matchingSelection != null &&
        settle != null && settle.routeId == matchingSelection.key.routeId
    LaunchedEffect(settle?.token, settleMotion, matchingSelection?.key, isOnline) {
        if (settle != null && !settleMotion) onConsumeSettle(settle.token)
    }

    val presentation = when {
        !isOnline -> Triple("离线", colors.warning, colors.warningSurface)
        trace == null && isLoading -> Triple("同步中", colors.textSecondary, colors.surfaceSubtle)
        trace == null -> Triple("未连接", colors.warning, colors.warningSurface)
        trace.observable == false -> Triple("不可观测", colors.warning, colors.warningSurface)
        isDemo -> Triple("演示", colors.success, colors.successSurface)
        else -> Triple("实时", colors.success, colors.successSurface)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            MagpiePageHeader(
                title = "路由",
                subtitle = "这一次",
                actions = {
                    LivePill(
                        label = presentation.first,
                        tone = presentation.second,
                        surface = presentation.third
                    )
                }
            )
        }

        item {
            PageBlock {
                FocusRequestCard(
                    summary = matchingSelection,
                    group = currentGroup,
                    phase = phase,
                    isFollowingLive = isFollowingLive,
                    traceObservable = trace?.observable != false,
                    onResetToLatest = onResetToLatest
                )
            }
        }

        item {
            PageBlock {
                Spacer(modifier = Modifier.height(12.dp))
                DataPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        RouteCanvas(
                            selectedRequest = matchingSelection,
                            currentGroup = currentGroup,
                            accounts = accounts,
                            activeProvider = activeProvider,
                            activeAccountId = activeAccountId,
                            activeAccounts = activeAccounts,
                            visibleProviders = visibleProviders,
                            phase = phase,
                            isFollowingLive = isFollowingLive,
                            settle = settle,
                            onConsumeSettle = onConsumeSettle,
                            isOnline = isOnline,
                            onProviderClick = { providerId ->
                                activeSheet = RoutingSheet.ProviderAccounts(providerId)
                            }
                        )
                        if (extraProviders.isNotEmpty()) {
                            IconButton(
                                onClick = { activeSheet = RoutingSheet.AllProviders },
                                modifier = Modifier.align(Alignment.TopEnd).size(48.dp)
                            ) {
                                Icon(Icons.Filled.MoreHoriz, contentDescription = "更多供应商", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        item {
            PageBlock {
                Spacer(modifier = Modifier.height(6.dp))
                val decisionEvidence = requestDetail?.decisionEvidence.orEmpty()
                SectionHeader(
                    title = "为什么选它",
                    note = decisionEvidence.size.takeIf { it > 0 }?.let { "$it 条证据" }
                )
                DecisionRow(
                    evidence = decisionEvidence,
                    summary = matchingSelection,
                    onOpen = { showDecisionModal = true }
                )
            }
        }

        item {
            PageBlock {
                Spacer(modifier = Modifier.height(14.dp))
                val attempts = requestDetail?.attempts.orEmpty()
                SectionHeader(
                    title = "尝试过程",
                    note = attempts.size.takeIf { it > 0 }?.let { "$it 次" }
                )
                if (attempts.isEmpty()) {
                    Text(
                        text = "服务端未返回尝试时间线",
                        style = MagpieTypography.LabelStyle,
                        color = colors.textSecondary
                    )
                } else {
                    AttemptTimeline(
                        entries = attempts.map { attempt ->
                            attempt.toTimelineEntry(
                                accounts = accounts,
                                maskEnabled = preferences.maskAccountNames,
                                routeState = requestDetail?.summary?.finalState
                            )
                        }
                    )
                }
            }
        }

        item {
            PageBlock {
                Spacer(modifier = Modifier.height(18.dp))
                val context = requestDetail?.context
                SectionHeader(
                    title = "上下文构成",
                    note = context?.let { "${MetricCalculators.formatCompactNumber(it.totalTokens)} token" }
                )
                if (context != null) {
                    ContextSection(context = context)
                } else {
                    Text(
                        text = "暂无上下文明细",
                        style = MagpieTypography.LabelStyle,
                        color = colors.textSecondary
                    )
                }
            }
        }

        // A trace that is not observable, or carries no totals, has no footer to show.
        // Printing zeros would claim a count this connection never reported.
        val traceTotals = trace?.takeIf { it.observable }?.totals
        if (traceTotals != null) {
            item {
                PageBlock {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "本次启动以来 " + listOf(
                            "${displayOrDash(traceTotals.completedRequests?.toString())} 请求",
                            "${displayOrDash(traceTotals.rerouteHandoffs?.toString())} 次改道",
                            "${displayOrDash(traceTotals.finalErrors?.toString())} 次最终失败"
                        ).joinToString(" · "),
                        style = MagpieTypography.LabelStyle,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    if (showDecisionModal) {
        ModalBottomSheet(
            onDismissRequest = { showDecisionModal = false },
            containerColor = colors.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            DecisionDetailSheetContent(
                evidence = requestDetail?.decisionEvidence.orEmpty(),
                selectedRequest = selectedRequest,
                onDismiss = { showDecisionModal = false },
                onOpenRequest = selectedRequest?.let {
                    {
                        showDecisionModal = false
                        onOpenRequestDetail(it)
                    }
                }
            )
        }
    }

    when (val sheet = activeSheet) {
        null -> Unit
        RoutingSheet.AllProviders -> {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = null },
                containerColor = colors.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                AllProvidersSheetContent(
                    providerIds = groupProviders,
                    accounts = accounts,
                    maskEnabled = preferences.maskAccountNames,
                    onProviderClick = { providerId ->
                        activeSheet = RoutingSheet.ProviderAccounts(providerId)
                    },
                    onDismiss = { activeSheet = null }
                )
            }
        }
        is RoutingSheet.ProviderAccounts -> {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = null },
                containerColor = colors.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                ProviderAccountsSheetContent(
                    providerId = sheet.providerId,
                    accounts = accounts.filter { it.providerId == sheet.providerId },
                    activeAccountId = if (sheet.providerId == activeProvider) activeAccountId else null,
                    maskEnabled = preferences.maskAccountNames,
                    onAccountClick = { account ->
                        activeSheet = RoutingSheet.AccountDetail(sheet.providerId, account)
                    },
                    onBack = { activeSheet = RoutingSheet.AllProviders },
                    onDismiss = { activeSheet = null }
                )
            }
        }
        is RoutingSheet.AccountDetail -> {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = null },
                containerColor = colors.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                AccountDetailSheetContent(
                    account = sheet.account,
                    maskEnabled = preferences.maskAccountNames,
                    onBack = { activeSheet = RoutingSheet.ProviderAccounts(sheet.providerId) },
                    onDismiss = { activeSheet = null }
                )
            }
        }
    }

}

/** LazyColumn items carry the page gutter themselves; [MagpiePageHeader] has its own. */
@Composable
private fun PageBlock(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MagpieDimensions.PagePadding),
        content = content
    )
}

/**
 * The one request this page explains: served model, outcome and the group behind it.
 * Follow mode and its way back to live sit on the card's last line because pinning is a
 * sticky state the reader may need to leave from anywhere on the page.
 */
@Composable
private fun FocusRequestCard(
    summary: RequestSummary?,
    group: RouteGroup?,
    phase: RoutePhase,
    isFollowingLive: Boolean,
    traceObservable: Boolean,
    onResetToLatest: () -> Unit
) {
    val colors = MagpieTheme.colors
    DataPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // DataPanel stacks its content, so the card's lines need an explicit column.
        Column(modifier = Modifier.fillMaxWidth()) {
            if (summary == null) {
                Text(
                    text = "暂无实时请求",
                    style = MagpieTypography.BodyStyle,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (traceObservable) {
                        "新的调用会自动成为路由焦点。"
                    } else {
                        "服务端未提供实时 trace；累计数据仍来自可用接口。"
                    },
                    style = MagpieTypography.LabelStyle,
                    color = colors.textSecondary
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayOrDash(summary.servedModel ?: summary.requestedModel),
                        style = MagpieTypography.SectionTitleStyle,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PhaseTag(phase = phase)
                }
                Spacer(modifier = Modifier.height(12.dp))
                KvGrid(
                    items = listOf(
                        "来源" to summary.agentId?.displayAgent(),
                        "路由组" to (group?.name ?: summary.groupId),
                        "状态" to phase.statusText(),
                        "耗时" to MetricCalculators.formatDurationMs(summary.durationMs),
                        "改道次数" to "${summary.rerouteCount} 次"
                    )
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = colors.divider)
            FollowModeRow(isFollowingLive = isFollowingLive, onResetToLatest = onResetToLatest)
        }
    }
}

/** Outcome tag beside the focus model; its tint only reuses the five state colors. */
@Composable
private fun PhaseTag(phase: RoutePhase) {
    val colors = MagpieTheme.colors
    val tint = phase.tint(colors)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(0.5.dp, tint.copy(alpha = 0.20f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.5.dp)
    ) {
        Text(
            text = phase.statusText(),
            style = MagpieTypography.LabelStyle,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1
        )
    }
}

/** Follow mode and its one-tap exit, kept in one 48dp row. */
@Composable
private fun FollowModeRow(isFollowingLive: Boolean, onResetToLatest: () -> Unit) {
    val colors = MagpieTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MagpieDimensions.TouchTargetMinimum),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isFollowingLive) "自动跟随" else "历史固定",
            style = MagpieTypography.LabelStyle,
            fontWeight = if (isFollowingLive) FontWeight.Normal else FontWeight.SemiBold,
            color = if (isFollowingLive) colors.textSecondary else colors.accent
        )
        Spacer(modifier = Modifier.weight(1f))
        if (!isFollowingLive) {
            Box(
                modifier = Modifier
                    .heightIn(min = MagpieDimensions.TouchTargetMinimum)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onResetToLatest)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "返回实时",
                    style = MagpieTypography.LabelStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent
                )
            }
        }
    }
}

@Composable
private fun LivePill(label: String, tone: Color, surface: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(surface)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tone)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = MagpieTypography.LabelStyle.fontSize,
            fontWeight = FontWeight.Medium,
            color = tone
        )
    }
}

@Composable
private fun RouteCanvas(
    selectedRequest: RequestSummary?,
    currentGroup: RouteGroup?,
    accounts: List<Account>,
    activeProvider: String?,
    activeAccountId: String?,
    activeAccounts: List<Account>,
    visibleProviders: List<String>,
    phase: RoutePhase,
    isFollowingLive: Boolean,
    settle: RouteSettle?,
    onConsumeSettle: (Long) -> Unit,
    isOnline: Boolean,
    onProviderClick: (String) -> Unit
) {
    val colors = MagpieTheme.colors
    val source = selectedRequest?.agentId?.displayAgent()
    val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
    val systemAnimations = rememberSystemAnimationsEnabled()
    val motionEnabled = isOnline &&
        lifecycle.isAtLeast(Lifecycle.State.RESUMED) &&
        !MagpieTheme.preferences.reduceMotion &&
        systemAnimations
    val liveMotion = motionEnabled && phase == RoutePhase.ACTIVE
    // A completion received while this canvas was absent is already history.
    val initialSettleToken = remember { settle?.token }
    val settleMotion = motionEnabled && isFollowingLive && phase != RoutePhase.PINNED &&
        settle != null && settle.token != initialSettleToken &&
        settle.routeId == selectedRequest?.key?.routeId
    LaunchedEffect(settle?.token, settleMotion) {
        val pending = settle ?: return@LaunchedEffect
        if (settleMotion) delay(500) // Let the 450 ms return finish once.
        onConsumeSettle(pending.token)
    }
    DisposableEffect(settle?.token) {
        onDispose { settle?.let { onConsumeSettle(it.token) } }
    }
    val animatedPhase = settle?.state?.let { state ->
        if (settleMotion) state else null
    }
    val activeIndex = visibleProviders.indexOf(activeProvider)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Terminal,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            if (source != null) {
                Text(
                    text = source,
                    fontSize = MagpieTypography.LabelStyle.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = "请求来源",
                fontSize = MagpieTypography.LabelStyle.fontSize,
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(5.dp))
        WireSegment(
            active = liveMotion,
            color = colors.accent,
            modifier = Modifier
                .width(1.dp)
                .height(14.dp)
        )
        Spacer(modifier = Modifier.height(5.dp))

        Column(
            modifier = Modifier
                .width(152.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.accentSurface.copy(alpha = 0.78f))
                .border(
                    0.75.dp,
                    if (liveMotion) colors.accent.copy(alpha = 0.48f) else colors.accent.copy(alpha = 0.22f),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 16.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BirdMark(tint = colors.accent, size = 16.dp, strokeWidth = 1.8f)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "magpie",
                    fontSize = MagpieTypography.SubheadStyle.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent
                )
            }
            Text(
                text = when (phase) {
                    RoutePhase.ACTIVE -> "正在选择并转发"
                    RoutePhase.SUCCEEDED -> "已完成选路"
                    RoutePhase.FAILED -> "选路失败"
                    RoutePhase.CANCELLED -> "已取消"
                    RoutePhase.UNCONFIRMED -> "结果待确认"
                    RoutePhase.PINNED -> "历史请求"
                    RoutePhase.UNAVAILABLE -> "实时数据不可用"
                    RoutePhase.IDLE -> currentGroup?.affinityLabel() ?: "保持本轮账号"
                },
                fontSize = MagpieTypography.LabelStyle.fontSize,
                color = colors.textSecondary,
                maxLines = 1
            )
        }

        BranchConnector(
            providerCount = visibleProviders.size,
            activeIndex = activeIndex,
            phase = phase,
            liveMotion = liveMotion,
            settleState = animatedPhase,
            settleToken = settle?.token ?: 0L,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )

        if (visibleProviders.isEmpty()) {
            Text(
                text = "当前组未提供供应商明细",
                fontSize = MagpieTypography.LabelStyle.fontSize,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                visibleProviders.forEach { providerId ->
                    val isActive = providerId == activeProvider
                    ProviderCard(
                        providerId = providerId,
                        isActive = isActive,
                        phase = if (isActive) phase else RoutePhase.IDLE,
                        motionEnabled = motionEnabled,
                        accounts = accounts.filter { it.providerId == providerId },
                        activeAccountId = if (isActive) activeAccountId else null,
                        activeAccounts = if (isActive) activeAccounts else emptyList(),
                        onClick = { onProviderClick(providerId) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (visibleProviders.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        if (visibleProviders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = routeCaption(selectedRequest, currentGroup, activeAccounts, activeAccountId, phase),
                fontSize = MagpieTypography.LabelStyle.fontSize,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun rememberFlowOffset(active: Boolean, periodMs: Int, distance: Float): Float {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(active, periodMs, distance) {
        if (!active) {
            offset.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { }
            offset.snapTo(0f)
            offset.animateTo(distance, tween(periodMs, easing = LinearEasing))
        }
    }
    return offset.value
}

@Composable
private fun rememberSystemAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember {
        mutableStateOf(
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) > 0f
        )
    }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = Settings.Global.getFloat(
                    resolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                ) > 0f
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

@Composable
private fun WireSegment(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { }
            progress.snapTo(0f)
            progress.animateTo(1f, tween(800, easing = LinearEasing))
        }
    }
    Canvas(modifier = modifier) {
        val dashOffset = progress.value * 6.dp.toPx()
        drawLine(
            color = if (active) color else color.copy(alpha = 0.34f),
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = 1.dp.toPx(),
            pathEffect = if (active) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()), dashOffset) else null
        )
        if (active) {
            val center = Offset(size.width / 2f, progress.value * size.height)
            drawCircle(color = color, radius = 1.7.dp.toPx(), center = center)
        }
    }
}

@Composable
private fun ProviderCard(
    providerId: String,
    isActive: Boolean,
    phase: RoutePhase,
    motionEnabled: Boolean,
    accounts: List<Account>,
    activeAccountId: String?,
    activeAccounts: List<Account>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    val account = activeAccounts.firstOrNull()
    val accountLabel = account?.let {
        "${MetricCalculators.maskAccountName(it.alias, MagpieTheme.preferences.maskAccountNames)} · ${it.availabilityLabel()}"
    } ?: activeAccountId?.let {
        MetricCalculators.maskAccountName(it, MagpieTheme.preferences.maskAccountNames)
    }
    val answering = isActive && phase == RoutePhase.ACTIVE
    val settledColor = when (phase) {
        RoutePhase.SUCCEEDED -> colors.success
        RoutePhase.FAILED -> colors.error
        RoutePhase.CANCELLED -> colors.textTertiary
        RoutePhase.UNCONFIRMED -> colors.warning
        else -> colors.accent
    }
    Column(
        modifier = modifier
            .heightIn(min = MagpieDimensions.TouchTargetMinimum)
            .clip(RoundedCornerShape(15.dp))
            .background(if (isActive) colors.accentSurface else colors.surfaceSubtle)
            .border(
                0.75.dp,
                if (isActive) settledColor.copy(alpha = 0.46f) else colors.divider,
                RoundedCornerShape(15.dp)
            )
            .springPress(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = providerId.displayProvider(),
            fontSize = MagpieTypography.LabelStyle.fontSize,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isActive) {
                if (answering && motionEnabled) {
                    ProviderPulse(color = settledColor)
                } else {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(settledColor)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = when {
                    answering -> account?.let {
                        "${MetricCalculators.maskAccountName(it.alias, MagpieTheme.preferences.maskAccountNames)} · 处理中"
                    } ?: activeAccountId?.let {
                        "${MetricCalculators.maskAccountName(it, MagpieTheme.preferences.maskAccountNames)} · 处理中"
                    } ?: "处理中"
                    isActive && phase == RoutePhase.SUCCEEDED -> "已应答"
                    isActive && phase == RoutePhase.FAILED -> "失败"
                    isActive && phase == RoutePhase.CANCELLED -> "已取消"
                    isActive && phase == RoutePhase.UNCONFIRMED -> "待确认"
                    isActive && phase == RoutePhase.PINNED -> "历史"
                    accountLabel != null -> accountLabel
                    accounts.isNotEmpty() -> "${accounts.size} 个账号"
                    else -> "账号明细不可用"
                },
                fontSize = MagpieTypography.CaptionStyle.fontSize,
                color = if (isActive) colors.textPrimary else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProviderPulse(color: Color) {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(900, easing = LinearEasing))
        }
    }
    Canvas(modifier = Modifier.size(10.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = color.copy(alpha = 0.20f * (1f - pulse.value)),
            radius = (2.5.dp.toPx() + 2.5.dp.toPx() * pulse.value)
        )
        drawCircle(color = color, radius = 2.5.dp.toPx(), center = center)
    }
}

@Composable
private fun BranchConnector(
    providerCount: Int,
    activeIndex: Int,
    phase: RoutePhase,
    liveMotion: Boolean,
    settleState: FinalState?,
    settleToken: Long,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    val dashOffset = rememberFlowOffset(liveMotion, 700, 6f)
    val travel = remember { Animatable(0f) }
    LaunchedEffect(liveMotion, settleToken, settleState) {
        if (liveMotion) {
            travel.snapTo(0f)
            while (true) {
                withFrameNanos { }
                travel.snapTo(0f)
                travel.animateTo(1f, tween(1250, easing = LinearEasing))
            }
        } else if (settleState != null) {
            // Return evidence travels provider -> hub -> source, then settles.
            travel.snapTo(1f)
            travel.animateTo(0f, tween(450))
        } else {
            travel.snapTo(0f)
        }
    }
    Canvas(modifier = modifier) {
        val midX = size.width / 2f
        val midY = size.height * 0.46f
        val stroke = 1.dp.toPx()
        val radius = 7.dp.toPx()
        val neutral = colors.divider
        val accent = when (phase) {
            RoutePhase.ACTIVE -> colors.accent
            RoutePhase.SUCCEEDED -> colors.success
            RoutePhase.FAILED -> colors.error
            RoutePhase.CANCELLED -> colors.textTertiary
            RoutePhase.UNCONFIRMED -> colors.warning
            RoutePhase.PINNED -> colors.textTertiary
            else -> neutral
        }
        val animated = liveMotion || settleState != null
        val dash = if (liveMotion) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()), dashOffset) else null

        drawLine(
            color = if (animated) accent.copy(alpha = 0.8f) else accent.copy(alpha = 0.35f),
            start = Offset(midX, 0f),
            end = Offset(midX, midY),
            strokeWidth = stroke,
            pathEffect = dash
        )

        val targets = when {
            providerCount <= 1 -> listOf(midX)
            else -> listOf(size.width * 0.25f, size.width * 0.75f)
        }
        targets.forEachIndexed { index, x ->
            val chosen = activeIndex == index || providerCount <= 1 && activeIndex == 0
            val chosenLit = chosen && (animated || phase in setOf(
                RoutePhase.SUCCEEDED,
                RoutePhase.FAILED,
                RoutePhase.CANCELLED,
                RoutePhase.UNCONFIRMED
            ))
            val path = Path().apply {
                moveTo(midX, midY)
                if (x == midX) {
                    lineTo(midX, size.height)
                } else {
                    lineTo(x - if (x < midX) -radius else radius, midY)
                    quadraticBezierTo(x, midY, x, midY + radius)
                    lineTo(x, size.height)
                }
            }
            drawPath(
                path = path,
                color = if (chosenLit) accent else neutral,
                style = Stroke(
                    width = if (chosenLit) stroke * 1.35f else stroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = if (chosen && liveMotion) dash else null
                )
            )
            if (chosen && (liveMotion || settleState != null)) {
                val pm = PathMeasure().apply { setPath(path, false) }
                val length = pm.length
                if (length > 0f) {
                    val position = pm.getPosition(travel.value * length)
                    drawCircle(colors.surface, radius = 4.dp.toPx(), center = position)
                    drawCircle(
                        if (settleState == FinalState.FAILED) colors.error else if (settleState == FinalState.SUCCEEDED) colors.success else colors.accent,
                        radius = 2.5.dp.toPx(),
                        center = position
                    )
                    if (settleState != null) {
                        val sourcePosition = Offset(
                            position.x + (midX - position.x) * (1f - travel.value),
                            position.y + (0f - position.y) * (1f - travel.value)
                        )
                        drawCircle(colors.surface, radius = 4.dp.toPx(), center = sourcePosition)
                        drawCircle(
                            if (settleState == FinalState.FAILED) colors.error else if (settleState == FinalState.SUCCEEDED) colors.success else colors.accent,
                            radius = 2.5.dp.toPx(),
                            center = sourcePosition
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandProvidersRow(hiddenCount: Int, onOpen: () -> Unit) {
    val colors = MagpieTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MagpieDimensions.TouchTargetMinimum)
            .clickable(onClick = onOpen)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "更多供应商 · $hiddenCount",
            fontSize = MagpieTypography.LabelStyle.fontSize,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(14.dp)
        )
    }
}

/** One-line preview of the routing evidence; the full list stays in the decision sheet. */
@Composable
private fun DecisionRow(evidence: List<String>, summary: RequestSummary?, onOpen: () -> Unit) {
    val colors = MagpieTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MagpieDimensions.TouchTargetMinimum)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DecisionMark(tint = colors.accent)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = evidence.firstOrNull()
                ?: if (summary != null) "按组策略选择当前账号" else "暂无可复盘请求",
            style = MagpieTypography.SubheadStyle,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "查看选路原因",
            tint = colors.textSecondary,
            modifier = Modifier.size(15.dp)
        )
    }
}

/** Three-segment context bar; the window limit stays beside the legend. */
@Composable
private fun ContextSection(context: ContextBreakdown) {
    val colors = MagpieTheme.colors
    val segments = context.parts.take(3)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(colors.divider),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val palette = listOf(colors.accent, colors.accent.copy(alpha = 0.58f), colors.textTertiary.copy(alpha = 0.55f))
            segments.forEachIndexed { index, part ->
                val ratio = if (context.totalTokens > 0) part.tokens.toFloat() / context.totalTokens.toFloat() else 0f
                if (ratio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(ratio)
                            .fillMaxSize()
                            .background(palette[index % palette.size])
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = segments.take(2).joinToString(" · ") { part ->
                    "${part.kind.contextLabel()} ${MetricCalculators.formatPercent(if (context.totalTokens > 0) part.tokens.toDouble() / context.totalTokens else null)}"
                },
                fontSize = MagpieTypography.CaptionStyle.fontSize,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (context.windowLimit != null) {
                    "窗口 ${MetricCalculators.formatCompactNumber(context.windowLimit)}"
                } else {
                    "窗口上限未知"
                },
                fontSize = MagpieTypography.CaptionStyle.fontSize,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
fun GroupPickerSheetContent(
    groups: List<RouteGroup>,
    selectedGroupId: String?,
    onSelectGroup: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MagpieTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    val filtered = groups.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.id.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MagpieDimensions.PagePadding)
            .padding(bottom = 32.dp)
    ) {
        SheetHeading(title = "选择路由组", onDismiss = onDismiss)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索路由组", fontSize = MagpieTypography.SubheadStyle.fontSize) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textSecondary) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.divider
            )
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            items(filtered, key = { it.id }) { group ->
                val isSelected = group.id == selectedGroupId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { onSelectGroup(group.id) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            group.name,
                            fontSize = MagpieTypography.SubheadStyle.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                        Text(
                            "${group.strategyLabel()} · ${group.enabledMemberCount} 个成员",
                            fontSize = MagpieTypography.CaptionStyle.fontSize,
                            color = colors.textSecondary
                        )
                    }
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = "当前查看", tint = colors.accent, modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = colors.divider)
            }
        }
    }
}

@Composable
fun DecisionDetailSheetContent(
    evidence: List<String>,
    selectedRequest: RequestSummary?,
    onDismiss: () -> Unit,
    onOpenRequest: (() -> Unit)? = null
) {
    val colors = MagpieTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MagpieDimensions.PagePadding)
            .padding(bottom = 32.dp)
    ) {
        SheetHeading(title = "为什么选择这个账号", onDismiss = onDismiss)
        Text(
            text = evidence.firstOrNull() ?: "暂无选路证据",
            fontSize = MagpieTypography.BodyStyle.fontSize,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary
        )
        if (evidence.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            evidence.drop(1).forEachIndexed { index, step ->
                Row(modifier = Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        "${index + 2}.",
                        fontSize = MagpieTypography.LabelStyle.fontSize,
                        color = colors.textSecondary,
                        modifier = Modifier.width(22.dp)
                    )
                    Text(
                        step,
                        fontSize = MagpieTypography.LabelStyle.fontSize,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (selectedRequest != null) {
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = colors.divider)
            DetailRow("请求来源", selectedRequest.agentId ?: "—")
            DetailRow("路由组", selectedRequest.groupId ?: "—")
            DetailRow("最终模型", selectedRequest.servedModel ?: selectedRequest.requestedModel ?: "—")
            DetailRow("最终状态", selectedRequest.finalState.statusLabel())
        }
        if (onOpenRequest != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.accentSurface)
                    .clickable(onClick = onOpenRequest),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "查看这次请求",
                    fontSize = MagpieTypography.SubheadStyle.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent
                )
            }
        }
    }
}

@Composable
fun AccountDetailSheetContent(
    account: Account,
    maskEnabled: Boolean,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MagpieTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MagpieDimensions.PagePadding)
            .padding(bottom = 32.dp)
    ) {
        SheetHeading(
            title = MetricCalculators.maskAccountName(account.alias, maskEnabled),
            onBack = onBack,
            onDismiss = onDismiss
        )
        DetailRow("供应商", account.providerId.displayProvider())
        DetailRow("可用性", account.availabilityLabel())
        DetailRow("账号标识", MetricCalculators.maskAccountName(account.id, maskEnabled))
        DetailRow("操作权限", "只读查询")
        if (account.cooldownUntil != null) DetailRow("冷却至", account.cooldownUntil)
    }
}

@Composable
private fun AllProvidersSheetContent(
    providerIds: List<String>,
    accounts: List<Account>,
    maskEnabled: Boolean,
    onProviderClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MagpieTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MagpieDimensions.PagePadding)
            .padding(bottom = 32.dp)
    ) {
        SheetHeading(title = "更多供应商", onDismiss = onDismiss)
        if (providerIds.isEmpty()) {
            Text(
                "当前路由组未提供供应商明细",
                fontSize = MagpieTypography.BodyStyle.fontSize,
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            return@Column
        }
        providerIds.forEach { providerId ->
            val providerAccounts = accounts.filter { it.providerId == providerId }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MagpieDimensions.TouchTargetMinimum)
                    .clickable { onProviderClick(providerId) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        providerId.displayProvider(),
                        fontSize = MagpieTypography.BodyStyle.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                    Text(
                        if (providerAccounts.isEmpty()) "账号明细不可用" else "${providerAccounts.size} 个账号",
                        fontSize = MagpieTypography.LabelStyle.fontSize,
                        color = colors.textSecondary
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            HorizontalDivider(color = colors.divider)
        }
    }
}

@Composable
private fun ProviderAccountsSheetContent(
    providerId: String,
    accounts: List<Account>,
    activeAccountId: String?,
    maskEnabled: Boolean,
    onAccountClick: (Account) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MagpieTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MagpieDimensions.PagePadding)
            .padding(bottom = 32.dp)
    ) {
        SheetHeading(title = providerId.displayProvider(), onBack = onBack, onDismiss = onDismiss)
        if (accounts.isEmpty()) {
            Text(
                "该供应商未返回账号明细",
                fontSize = MagpieTypography.BodyStyle.fontSize,
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            return@Column
        }
        accounts.forEach { account ->
            val isActive = account.id == activeAccountId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MagpieDimensions.TouchTargetMinimum)
                    .clickable { onAccountClick(account) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) colors.success else when (account.availability) {
                                "AVAILABLE" -> colors.textTertiary
                                "COOLDOWN" -> colors.warning
                                else -> colors.textTertiary
                            }
                        )
                )
                Spacer(modifier = Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        MetricCalculators.maskAccountName(account.alias, maskEnabled),
                        fontSize = MagpieTypography.BodyStyle.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                    Text(
                        account.availabilityLabel(),
                        fontSize = MagpieTypography.LabelStyle.fontSize,
                        color = colors.textSecondary
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "账号详情",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            HorizontalDivider(color = colors.divider)
        }
    }
}

@Composable
private fun SheetHeading(
    title: String,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val colors = MagpieTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(MagpieDimensions.TouchTargetMinimum)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = colors.textSecondary
                )
            }
        }
        Text(
            title,
            fontSize = MagpieTypography.SheetTitleStyle.fontSize,
            fontWeight = MagpieTypography.SheetTitleStyle.fontWeight,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "关闭", tint = colors.textSecondary)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = MagpieTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = MagpieTypography.LabelStyle.fontSize,
            color = colors.textSecondary
        )
        Text(
            value,
            fontSize = MagpieTypography.LabelStyle.fontSize,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

private fun RouteGroup.strategyLabel(): String = when (strategy?.lowercase()) {
    "rotate" -> "轮流"
    "smart" -> "智能"
    null, "" -> "默认"
    else -> strategy
}

private fun RouteGroup.affinityLabel(): String = when (affinity?.lowercase()) {
    "turn" -> "保持本轮账号"
    "auto" -> "自动保持会话"
    null, "" -> "保持会话"
    else -> affinity
}

private fun String.displayAgent(): String = when (lowercase()) {
    "codex" -> "Codex"
    "claude-code" -> "Claude Code"
    else -> this
}

private fun String.displayProvider(): String = when (lowercase()) {
    "workbuddy" -> "WorkBuddy"
    "workbuddy-ai" -> "WorkBuddy AI"
    "qoder-cn" -> "Qoder CN"
    "opencode-zen-free" -> "OpenCode Zen"
    "moonshot" -> "Moonshot"
    "deepseek" -> "DeepSeek"
    else -> this
}

private fun Account.availabilityLabel(): String = when (availability) {
    "AVAILABLE" -> "可用"
    "BUSY" -> "处理中"
    "COOLDOWN" -> "冷却中"
    else -> "未知"
}

private fun com.magpie.pocket.core.model.FinalState.statusLabel(): String = when (this) {
    com.magpie.pocket.core.model.FinalState.SUCCEEDED -> "成功"
    com.magpie.pocket.core.model.FinalState.FAILED -> "最终失败"
    com.magpie.pocket.core.model.FinalState.IN_PROGRESS -> "处理中"
    com.magpie.pocket.core.model.FinalState.PENDING -> "排队中"
    com.magpie.pocket.core.model.FinalState.CANCELLED -> "已取消"
    com.magpie.pocket.core.model.FinalState.UNCONFIRMED -> "待确认"
}

@Composable
private fun routeCaption(
    summary: RequestSummary?,
    group: RouteGroup?,
    activeAccounts: List<Account>,
    activeAccountId: String?,
    phase: RoutePhase
): String {
    val model = summary?.servedModel ?: summary?.requestedModel
    val members = group?.enabledMemberCount?.takeIf { it > 0 }?.let { "$it 个启用成员" }
    val account = activeAccounts.firstOrNull()?.alias?.let {
        MetricCalculators.maskAccountName(it, MagpieTheme.preferences.maskAccountNames)
    } ?: activeAccountId?.let {
        MetricCalculators.maskAccountName(it, MagpieTheme.preferences.maskAccountNames)
    }
    val state = when (phase) {
        RoutePhase.ACTIVE -> "处理中"
        RoutePhase.SUCCEEDED -> "已应答"
        RoutePhase.FAILED -> "失败"
        RoutePhase.CANCELLED -> "已取消"
        RoutePhase.UNCONFIRMED -> "待确认"
        RoutePhase.PINNED -> "历史"
        else -> null
    }
    return listOfNotNull(model, account, state, members).joinToString(" · ").ifBlank { "等待实时请求" }
}

private fun RoutePhase.statusText(): String = when (this) {
    RoutePhase.ACTIVE -> "处理中"
    RoutePhase.SUCCEEDED -> "成功"
    RoutePhase.FAILED -> "最终失败"
    RoutePhase.CANCELLED -> "已取消"
    RoutePhase.UNCONFIRMED -> "待确认"
    RoutePhase.PINNED -> "历史固定"
    RoutePhase.UNAVAILABLE -> "不可观测"
    RoutePhase.IDLE -> "等待实时"
}

private fun RoutePhase.tint(colors: MagpieColorScheme): Color = when (this) {
    RoutePhase.ACTIVE -> colors.accent
    RoutePhase.SUCCEEDED -> colors.success
    RoutePhase.FAILED -> colors.error
    RoutePhase.CANCELLED -> colors.textSecondary
    RoutePhase.UNCONFIRMED -> colors.warning
    RoutePhase.PINNED -> colors.accent
    RoutePhase.UNAVAILABLE -> colors.warning
    RoutePhase.IDLE -> colors.textSecondary
}

/**
 * One attempt as a timeline row. [AttemptEntry.target] is the provider/account that was
 * tried, [AttemptEntry.detail] keeps the recorded HTTP status or error next to the model,
 * and [AttemptEntry.meta] is the duration.
 */
private fun Attempt.toTimelineEntry(
    accounts: List<Account>,
    maskEnabled: Boolean,
    routeState: FinalState?
): AttemptEntry {
    val provider = providerId?.displayProvider() ?: "—"
    val accountAlias = accountId?.let { id -> accounts.find { it.id == id }?.alias ?: id }
    val accountLabel = accountAlias?.let { MetricCalculators.maskAccountName(it, maskEnabled) }
    return AttemptEntry(
        index = index,
        target = accountLabel?.let { "$provider · $it" } ?: provider,
        detail = listOfNotNull(
            httpStatus?.let { "HTTP $it" } ?: errorCode,
            model?.takeIf { it.isNotBlank() }
        ).joinToString(" · "),
        meta = MetricCalculators.formatDurationMs(durationMs),
        state = timelineState(routeState)
    )
}

private fun String.contextLabel(): String = when (this) {
    "tool_results" -> "工具结果"
    "conversation" -> "会话"
    else -> "其他"
}
