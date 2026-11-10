package com.magpie.pocket.feature.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magpie.pocket.core.design.AttemptEntry
import com.magpie.pocket.core.design.AttemptState
import com.magpie.pocket.core.design.AttemptTimeline
import com.magpie.pocket.core.design.ErrorBlock
import com.magpie.pocket.core.design.FilterChipRow
import com.magpie.pocket.core.design.KvGrid
import com.magpie.pocket.core.design.MagpieDimensions
import com.magpie.pocket.core.design.MagpiePageHeader
import com.magpie.pocket.core.design.MagpieTheme
import com.magpie.pocket.core.design.MagpieTypography
import com.magpie.pocket.core.design.SectionHeader
import com.magpie.pocket.core.design.StatusBadge
import com.magpie.pocket.core.design.displayOrDash
import com.magpie.pocket.core.model.Account
import com.magpie.pocket.core.model.Attempt
import com.magpie.pocket.core.model.FinalState
import com.magpie.pocket.core.model.RequestDetail
import com.magpie.pocket.core.model.RequestFilter
import com.magpie.pocket.core.model.RequestPage
import com.magpie.pocket.core.model.RequestSummary
import com.magpie.pocket.core.util.MetricCalculators
import com.magpie.pocket.core.util.RequestTimestamps
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The ledger fixes the row title one step above the 14sp body; no type token carries 15sp. */
private val RequestModelStyle = TextStyle(
    fontSize = 15.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.SemiBold
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(
    requestPage: RequestPage?,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    statusFilter: String,
    onStatusFilterChange: (String) -> Unit,
    supportedFilters: Set<RequestFilter>,
    selectedDay: String?,
    onSelectDay: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onRequestClick: (RequestSummary) -> Unit,
    accounts: List<Account> = emptyList(),
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    val preferences = MagpieTheme.preferences
    var localSearch by remember(searchQuery) { mutableStateOf(searchQuery) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Only the filters the active connection can really apply are drawn, in enum order.
    val availableFilters = remember(supportedFilters) {
        RequestFilter.entries.filter { it in supportedFilters }
    }
    val pagePadding = Modifier.padding(horizontal = MagpieDimensions.PagePadding)

    LaunchedEffect(localSearch) {
        if (localSearch != searchQuery) {
            delay(300)
            onSearchQueryChange(localSearch)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            MagpiePageHeader(
                title = "请求",
                subtitle = "账本",
                actions = {
                    RealtimeDayPill(
                        selectedDay = selectedDay,
                        onClick = { showDatePicker = true }
                    )
                }
            )
        }

        item {
            RequestSearchField(
                value = localSearch,
                onValueChange = { localSearch = it },
                onClear = {
                    localSearch = ""
                    onSearchQueryChange("")
                },
                modifier = pagePadding
            )
        }

        item {
            FilterChipRow(
                options = availableFilters.map { filterLabel(it) },
                selectedIndex = availableFilters.indexOfFirst { it.code == statusFilter },
                onSelect = { index ->
                    availableFilters.getOrNull(index)?.let { onStatusFilterChange(it.code) }
                },
                modifier = pagePadding.padding(top = 10.dp, bottom = 4.dp)
            )
        }

        item {
            Row(
                modifier = pagePadding
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedDay == null) "最近请求" else "历史请求 · $selectedDay",
                    style = MagpieTypography.LabelStyle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = requestPage?.let { "共 ${it.total} 条" } ?: "加载中",
                    style = MagpieTypography.LabelStyle,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
        }

        item { HorizontalDivider(modifier = pagePadding, color = colors.divider) }

        val itemsList = requestPage?.items.orEmpty()
        if (itemsList.isEmpty() && !isLoading) {
            item {
                Box(
                    modifier = pagePadding
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "未找到匹配的请求记录",
                        style = MagpieTypography.LabelStyle,
                        color = colors.textSecondary
                    )
                }
            }
        } else {
            items(itemsList, key = { it.key.routeId }) { request ->
                RequestRow(
                    summary = request,
                    maskAccount = preferences.maskAccountNames,
                    accountAlias = request.accountId?.let { id ->
                        accounts.firstOrNull { it.id == id }?.alias
                    },
                    onClick = { onRequestClick(request) },
                    modifier = pagePadding
                )
            }
        }

        if (requestPage?.hasMore == true) {
            item {
                Box(
                    modifier = pagePadding
                        .fillMaxWidth()
                        .height(MagpieDimensions.TouchTargetMinimum)
                        .clickable(onClick = onLoadMore),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "加载更多",
                        style = MagpieTypography.LabelStyle.copy(fontWeight = FontWeight.Medium),
                        color = colors.accent
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDay?.let { parseDayMillis(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // Material encodes the picked calendar day as UTC midnight.
                    // Keep that UTC date; local conversion would shift it a day.
                    onSelectDay(datePickerState.selectedDateMillis?.let { formatDayMillis(it) })
                    showDatePicker = false
                }) { Text("查看") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onSelectDay(null)
                        showDatePicker = false
                    }) { Text("实时") }
                    TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** Live / history capsule with the calendar entry; the state comes from the selected day. */
@Composable
private fun RealtimeDayPill(
    selectedDay: String?,
    onClick: () -> Unit
) {
    val colors = MagpieTheme.colors
    val live = selectedDay == null
    Box(
        modifier = Modifier
            .heightIn(min = MagpieDimensions.TouchTargetMinimum)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (live) colors.successSurface else colors.surfaceSubtle)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (live) colors.success else colors.textSecondary)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (live) "实时" else "历史",
                style = MagpieTypography.LabelStyle.copy(fontWeight = FontWeight.Medium),
                color = if (live) colors.success else colors.textSecondary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = "选择日期",
                tint = if (live) colors.success else colors.textSecondary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun RequestSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MagpieDimensions.TouchTargetMinimum)
            .clip(RoundedCornerShape(MagpieDimensions.RadiusSearch))
            .background(colors.surface)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
            singleLine = true,
            textStyle = MagpieTypography.SubheadStyle.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            "搜索模型、来源或路由组",
                            style = MagpieTypography.SubheadStyle,
                            color = colors.textSecondary
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "清除搜索",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** Wire codes of [RequestFilter] in the wording the ledger uses. */
private fun filterLabel(filter: RequestFilter): String = when (filter) {
    RequestFilter.ALL -> "全部"
    RequestFilter.RUNNING -> "处理中"
    RequestFilter.REROUTED -> "改道"
    RequestFilter.ERROR -> "错误"
}

@Composable
fun RequestRow(
    summary: RequestSummary,
    maskAccount: Boolean,
    accountAlias: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    val totalTokens = MetricCalculators.calculateTotalTokensOrNull(summary.usage)
    val account = (accountAlias ?: summary.accountId)
        ?.let { MetricCalculators.maskAccountName(it, maskAccount) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayOrDash(modelText(summary.servedModel, summary.requestedModel)),
                style = RequestModelStyle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = RequestTimestamps.formatTime(summary.startedAt),
                style = MagpieTypography.LabelStyle,
                color = colors.textSecondary,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        // The real reroute count keeps "成功 · 改道 N 次" matching the 改道 filter.
        StatusBadge(state = summary.finalState, rerouteCount = summary.rerouteCount)
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = listOf(
                displayOrDash(summary.agentId),
                displayOrDash(summary.providerId),
                displayOrDash(account)
            ).joinToString(" · "),
            style = MagpieTypography.LabelStyle,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = listOf(
                "首响 ${MetricCalculators.formatDurationMs(summary.ttftMs)}",
                "耗时 ${MetricCalculators.formatDurationMs(summary.durationMs)}",
                totalTokens?.let { "${MetricCalculators.formatCompactNumber(it)} token" } ?: "— token"
            ).joinToString("  ·  "),
            style = MagpieTypography.LabelStyle,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(13.dp))
        HorizontalDivider(color = colors.divider)
    }
}

@Composable
fun RequestDetailSheetContent(
    detail: RequestDetail,
    maskAccount: Boolean,
    accounts: List<Account> = emptyList(),
    onDismiss: () -> Unit
) {
    val colors = MagpieTheme.colors
    val summary = detail.summary
    val totalTokens = MetricCalculators.calculateTotalTokensOrNull(summary.usage)
    val account = summary.accountId?.let { id ->
        val alias = accounts.firstOrNull { it.id == id }?.alias ?: id
        MetricCalculators.maskAccountName(alias, maskAccount)
    }
    val fields: List<Pair<String, String?>> = listOf(
        "开始时间" to RequestTimestamps.formatFullLocal(summary.startedAt),
        "来源" to summary.agentId,
        "路由组" to summary.groupId,
        "请求模型" to validModel(summary.requestedModel),
        "应答模型" to modelText(summary.servedModel, summary.requestedModel),
        "供应商" to summary.providerId,
        "账号" to account,
        "首响" to MetricCalculators.formatDurationMs(summary.ttftMs),
        "总耗时" to MetricCalculators.formatDurationMs(summary.durationMs),
        "Token" to totalTokens?.let { MetricCalculators.formatCompactNumber(it) }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MagpieDimensions.PagePadding)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#${summary.key.routeId}",
                style = MagpieTypography.SheetTitleStyle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(MagpieDimensions.TouchTargetMinimum)) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = colors.textSecondary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        // The badge carries the state alone; the count stays a separate line so an
        // in-progress request with reroutes still shows how many times it was rerouted.
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(state = summary.finalState)
            if (summary.rerouteCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "改道 ${summary.rerouteCount} 次",
                    style = MagpieTypography.LabelStyle,
                    color = colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        KvGrid(items = fields)

        Spacer(modifier = Modifier.height(10.dp))
        SectionHeader(
            title = "尝试过程",
            note = detail.attempts.size.takeIf { it > 0 }?.let { "共 $it 次" }
        )
        if (detail.attempts.isEmpty()) {
            Text(
                "服务端未返回尝试时间线",
                style = MagpieTypography.LabelStyle,
                color = colors.textSecondary
            )
        } else {
            AttemptTimeline(
                entries = detail.attempts.map { attemptEntry(it, accounts, maskAccount) }
            )
        }

        val errorText = detail.sanitizedError
        if (!errorText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            ErrorBlock(
                title = if (summary.finalState == FinalState.FAILED) "最终失败" else "错误信息",
                message = errorText
            )
        }
    }
}

/**
 * One attempt entry for the shared timeline: a 2xx status is a success, a handoff
 * to the next target is a reroute, and everything else is a failed attempt.
 */
private fun attemptEntry(
    attempt: Attempt,
    accounts: List<Account>,
    maskAccount: Boolean
): AttemptEntry {
    val status = attempt.httpStatus
    val state = when {
        status != null && status in 200..299 -> AttemptState.SUCCEEDED
        attempt.handedOn -> AttemptState.REROUTED
        else -> AttemptState.FAILED
    }
    val detail = listOfNotNull(
        validModel(attempt.model),
        attempt.accountId?.let { accountDisplay(it, accounts, maskAccount) },
        status?.let { "HTTP $it" },
        attempt.errorCode
    ).joinToString(" · ")
    return AttemptEntry(
        index = attempt.index,
        target = displayOrDash(attempt.providerId),
        detail = detail.ifBlank { "进行中" },
        meta = MetricCalculators.formatDurationMs(attempt.durationMs),
        state = state
    )
}

private fun validModel(value: String?): String? {
    return value?.trim()?.takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
}

/** Served model first, then the requested one, then null so the caller prints an em dash. */
private fun modelText(served: String?, requested: String?): String? {
    return validModel(served) ?: validModel(requested)
}

private fun accountDisplay(accountId: String?, accounts: List<Account>, maskAccount: Boolean): String {
    val value = accounts.firstOrNull { it.id == accountId }?.alias ?: accountId
    return value?.let { MetricCalculators.maskAccountName(it, maskAccount) } ?: "—"
}

private fun parseDayMillis(day: String): Long? {
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(day)?.time
    } catch (_: Exception) {
        null
    }
}

private fun formatDayMillis(millis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))
}
