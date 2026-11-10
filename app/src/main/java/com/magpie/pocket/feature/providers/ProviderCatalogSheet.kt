package com.magpie.pocket.feature.providers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magpie.pocket.core.design.DataPanel
import com.magpie.pocket.core.design.InlineNotice
import com.magpie.pocket.core.design.MagpieDimensions
import com.magpie.pocket.core.design.MagpieTheme
import com.magpie.pocket.core.design.MagpieTypography
import com.magpie.pocket.core.design.springPress
import com.magpie.pocket.core.model.ProviderCatalogItem
import com.magpie.pocket.core.model.ProviderCatalogState
import com.magpie.pocket.core.model.ProviderModelInfo
import com.magpie.pocket.core.util.MetricCalculators

/** Models rendered before the "show all" action; the rest stay behind one tap. */
private const val MODEL_PREVIEW_COUNT = 5

/** Label shown for every model spec the catalog does not report. */
private const val UNSPECIFIED_VALUE = "未标注"

/**
 * Provider catalog sheet. The header, the search field and the sort chips stay fixed; the
 * catalog itself is a list of expandable provider cards. Ordering is always online-first,
 * then the chosen sort key (name by default). Only the key count ever renders — key names
 * and key material stay out of the UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderCatalogSheet(
    state: ProviderCatalogState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSearch: (String) -> Unit = {}
) {
    val colors = MagpieTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ProviderSort.NAME) }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }
    var showAllModelIds by remember { mutableStateOf(emptySet<String>()) }
    val configuration = LocalConfiguration.current
    val horizontalPadding = if (configuration.screenWidthDp < 360) {
        MagpieDimensions.CompactPagePadding
    } else {
        MagpieDimensions.PagePadding
    }
    val singleColumn = configuration.screenWidthDp < 360 || configuration.fontScale >= 1.45f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(
            topStart = MagpieDimensions.RadiusSheetTop,
            topEnd = MagpieDimensions.RadiusSheetTop
        ),
        modifier = Modifier.fillMaxHeight(0.94f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .imePadding()
                .padding(horizontal = horizontalPadding)
                .padding(bottom = MagpieDimensions.Spacing32)
        ) {
            ProviderCatalogHeader(state = state, onDismiss = onDismiss)

            when (state) {
                is ProviderCatalogState.Ready -> {
                    // Typing re-seeds both expansion sets: a model hit opens its provider,
                    // and every provider returns to the five-model preview.
                    val onQueryChange: (String) -> Unit = { next ->
                        query = next
                        onSearch(next)
                        expandedIds = providersWithModelMatches(state.providers, next)
                        showAllModelIds = emptySet()
                    }
                    val clearSearch: () -> Unit = {
                        query = ""
                        onSearch("")
                        expandedIds = emptySet()
                        showAllModelIds = emptySet()
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    ProviderSearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onClear = clearSearch
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ProviderSortChips(
                        selected = sort,
                        onSelect = { sort = it }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val providers = remember(state.providers, query, sort) {
                        filterProviders(state.providers, query, sort)
                    }
                    when {
                        state.providers.isEmpty() -> ProviderEmptyState(
                            modifier = Modifier.weight(1f),
                            onRetry = onRetry
                        )
                        providers.isEmpty() -> NoSearchResults(
                            modifier = Modifier.weight(1f),
                            onClearSearch = clearSearch
                        )
                        else -> LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(items = providers, key = { it.id }) { provider ->
                                ProviderCard(
                                    provider = provider,
                                    expanded = provider.id in expandedIds,
                                    query = query,
                                    showAllModels = provider.id in showAllModelIds,
                                    singleColumn = singleColumn,
                                    onToggle = {
                                        expandedIds = if (provider.id in expandedIds) {
                                            expandedIds - provider.id
                                        } else {
                                            expandedIds + provider.id
                                        }
                                    },
                                    onToggleShowAllModels = {
                                        showAllModelIds = if (provider.id in showAllModelIds) {
                                            showAllModelIds - provider.id
                                        } else {
                                            showAllModelIds + provider.id
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                ProviderCatalogState.Unsupported -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    InlineNotice(message = "此连接不提供供应商目录")
                }

                is ProviderCatalogState.Error -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    InlineNotice(
                        message = state.message,
                        isError = true,
                        actionLabel = "重试",
                        onAction = onRetry
                    )
                }

                ProviderCatalogState.Idle, ProviderCatalogState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "正在读取供应商…",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCatalogHeader(
    state: ProviderCatalogState,
    onDismiss: () -> Unit
) {
    val colors = MagpieTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "供应商目录",
                style = MagpieTypography.SheetTitleStyle,
                color = colors.textPrimary
            )
            Text(
                text = providerSubtitle(state),
                style = MagpieTypography.LabelStyle,
                color = colors.textSecondary
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(MagpieDimensions.TouchTargetMinimum)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "关闭", tint = colors.textSecondary)
        }
    }
}

@Composable
private fun ProviderSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    val colors = MagpieTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MagpieDimensions.TouchTargetMinimum)
            .clip(RoundedCornerShape(MagpieDimensions.RadiusSearch))
            .background(colors.surfaceSubtle)
            .border(
                width = 0.75.dp,
                color = colors.divider,
                shape = RoundedCornerShape(MagpieDimensions.RadiusSearch)
            )
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "搜索供应商或模型",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = colors.textSecondary
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = colors.textPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "搜索供应商或模型" }
            )
        }
        if (query.isEmpty()) {
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(MagpieDimensions.TouchTargetMinimum)
            ) {
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

/** Three sort keys. Online providers always sort first; the chips only pick the tie-breaker. */
@Composable
private fun ProviderSortChips(
    selected: ProviderSort,
    onSelect: (ProviderSort) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProviderSort.entries.forEach { option ->
            ProviderSortChip(
                label = option.label,
                selected = option == selected,
                onClick = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun ProviderSortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MagpieTheme.colors
    Box(
        modifier = Modifier
            .height(MagpieDimensions.TouchTargetMinimum)
            .clip(RoundedCornerShape(17.dp))
            .semantics {
                role = Role.Button
                stateDescription = if (selected) "已选中" else "未选中"
            }
            .springPress(onClick = onClick)
            // 48dp touch target wrapping the 34dp pill keeps the row tappable without
            // letting the chip look heavier than the surrounding metadata.
            .padding(vertical = 7.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) colors.accentSurface else colors.surfaceSubtle)
            .border(
                width = 0.75.dp,
                color = if (selected) colors.accent.copy(alpha = 0.55f) else colors.divider,
                shape = RoundedCornerShape(17.dp)
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.accent else colors.textSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderCatalogItem,
    expanded: Boolean,
    query: String,
    showAllModels: Boolean,
    singleColumn: Boolean,
    onToggle: () -> Unit,
    onToggleShowAllModels: () -> Unit
) {
    val colors = MagpieTheme.colors
    val reduceMotion = MagpieTheme.preferences.reduceMotion
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduceMotion) tween(0) else tween(180),
        label = "providerChevron"
    )
    val (statusLabel, statusColor, statusSurface) = when {
        provider.off == true -> Triple("停用", colors.error, colors.errorSurface)
        provider.ready == true -> Triple("在线", colors.success, colors.successSurface)
        else -> Triple("离线", colors.textSecondary, colors.surfaceSubtle)
    }

    DataPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MagpieDimensions.RadiusCard),
        contentPadding = PaddingValues(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Only the header toggles the card, so tapping a model row never collapses it.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MagpieDimensions.TouchTargetMinimum)
                    .semantics {
                        role = Role.Button
                        stateDescription = if (expanded) "已展开" else "已收起"
                    }
                    .springPress(onClick = onToggle)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = provider.name,
                        style = MagpieTypography.SectionTitleStyle,
                        color = colors.textPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clearAndSetSemantics { contentDescription = provider.name }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        modifier = Modifier.padding(top = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProviderStatusPill(
                            label = statusLabel,
                            color = statusColor,
                            background = statusSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(chevronAngle)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                ProviderMetaRow(provider = provider)

                provider.host?.takeIf { it.isNotBlank() }?.let { host ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = host,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (expanded) {
                ProviderExpandedContent(
                    provider = provider,
                    query = query,
                    showAllModels = showAllModels,
                    singleColumn = singleColumn,
                    onToggleShowAllModels = onToggleShowAllModels
                )
            }
        }
    }
}

/** Model count, key count and the provider price band as separate, evenly spaced segments. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderMetaRow(provider: ProviderCatalogItem) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ProviderMetaText(text = provider.models.size.toString() + " 个模型")
        // Enabled models stay visible: the catalog reports them even when nothing is in use.
        val enabledCount = provider.models.count { it.enabled == true }
        if (enabledCount > 0) {
            ProviderMetaText(text = enabledCount.toString() + " 已启用")
        }
        ProviderMetaText(text = provider.keyNames.size.toString() + " 个密钥")
        ProviderMetaText(text = providerPriceSummary(provider))
    }
}

@Composable
private fun ProviderExpandedContent(
    provider: ProviderCatalogItem,
    query: String,
    showAllModels: Boolean,
    singleColumn: Boolean,
    onToggleShowAllModels: () -> Unit
) {
    val colors = MagpieTheme.colors
    val matchedModelCount = matchingModelCount(provider, query)
    // Models the search hit stay at the top of the detail list, so a hit inside a long
    // catalog is visible without opening "show all" first.
    val orderedModels = remember(provider, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            provider.models
        } else {
            provider.models.filter { it.hitsQuery(needle) } +
                provider.models.filterNot { it.hitsQuery(needle) }
        }
    }
    val shownModels = if (showAllModels) {
        orderedModels
    } else {
        orderedModels.take(MODEL_PREVIEW_COUNT)
    }
    val specs = buildList {
        if (provider.speaks.isNotEmpty()) {
            add("协议 " + provider.speaks.joinToString(" / "))
        }
        provider.maxConcurrency?.let { add("并发 " + it) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(thickness = 0.75.dp, color = colors.divider)
        Spacer(modifier = Modifier.height(10.dp))

        if (matchedModelCount > 0) {
            Text(
                text = matchedModelCount.toString() + " 个匹配模型",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colors.accent
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (provider.models.isNotEmpty()) {
            ModelChips(models = orderedModels)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (specs.isNotEmpty()) {
            Text(
                text = specs.joinToString(" · "),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colors.textSecondary
            )
        }

        if (shownModels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            shownModels.forEachIndexed { index, model ->
                ModelDetail(model = model, singleColumn = singleColumn)
                if (index != shownModels.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        if (provider.models.size > MODEL_PREVIEW_COUNT) {
            Spacer(modifier = Modifier.height(8.dp))
            ProviderTextButton(
                label = if (showAllModels) {
                    "收起"
                } else {
                    "展开全部 " + provider.models.size + " 个"
                },
                onClick = onToggleShowAllModels
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelChips(models: List<ProviderModelInfo>) {
    val colors = MagpieTheme.colors
    val shown = models.take(12)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        shown.forEach { model ->
            ModelChip(label = model.displayName(), background = colors.surfaceSubtle, color = colors.textSecondary)
        }
        val hiddenCount = models.size - shown.size
        if (hiddenCount > 0) {
            ModelChip(
                label = "+" + hiddenCount,
                background = colors.accentSurface,
                color = colors.accent
            )
        }
    }
}

@Composable
private fun ModelChip(label: String, background: Color, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp)
        )
    }
}

@Composable
private fun ModelDetail(model: ProviderModelInfo, singleColumn: Boolean) {
    val colors = MagpieTheme.colors
    val name = model.displayName()
    val specs = listOf(
        "上下文" to (model.context?.let { MetricCalculators.formatCompactNumber(it) + " token" }
            ?: UNSPECIFIED_VALUE),
        "最大输出" to (model.output?.let { MetricCalculators.formatCompactNumber(it) + " token" }
            ?: UNSPECIFIED_VALUE),
        "输入价" to (model.inputPrice?.let { "$" + formatPrice(it) + " / 1M token" }
            ?: UNSPECIFIED_VALUE),
        "输出价" to (model.outputPrice?.let { "$" + formatPrice(it) + " / 1M token" }
            ?: UNSPECIFIED_VALUE),
        "推理档位" to (model.efforts.takeIf { it.isNotEmpty() }?.joinToString(" / ")
            ?: UNSPECIFIED_VALUE),
        "图片" to (
            when (model.images) {
                true -> "支持"
                false -> "不支持"
                else -> UNSPECIFIED_VALUE
            }
        )
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = name,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            modifier = Modifier.clearAndSetSemantics { contentDescription = name }
        )
        Spacer(modifier = Modifier.height(4.dp))
        ModelSpecGrid(specs = specs, singleColumn = singleColumn)
    }
}

/** Label/value grid: two columns on roomy layouts, one column below 360dp or at large font scale. */
@Composable
private fun ModelSpecGrid(specs: List<Pair<String, String>>, singleColumn: Boolean) {
    val columns = if (singleColumn) 1 else 2
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        specs.chunked(columns).forEach { rowSpecs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowSpecs.forEach { (label, value) ->
                    ModelSpecCell(
                        label = label,
                        value = value,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowSpecs.size < columns) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ModelSpecCell(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MagpieTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = colors.textTertiary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ProviderStatusPill(label: String, color: Color, background: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun ProviderMetaText(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = MagpieTheme.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Flat 48dp action: retry, clear search and show-all share one shape and one text style. */
@Composable
private fun ProviderTextButton(label: String, onClick: () -> Unit) {
    val colors = MagpieTheme.colors
    Box(
        modifier = Modifier
            .height(MagpieDimensions.TouchTargetMinimum)
            .clip(RoundedCornerShape(MagpieDimensions.RadiusItem))
            .background(colors.accentSurface)
            .springPress(scaleDown = 0.98f) { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.accent
        )
    }
}

@Composable
private fun NoSearchResults(modifier: Modifier = Modifier, onClearSearch: () -> Unit) {
    val colors = MagpieTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "没有匹配的供应商或模型",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        ProviderTextButton(label = "清除搜索", onClick = onClearSearch)
    }
}

@Composable
private fun ProviderEmptyState(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    val colors = MagpieTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "暂无供应商数据",
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "连接未返回供应商或模型数据。",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        ProviderTextButton(label = "重试", onClick = onRetry)
    }
}

private fun providerSubtitle(state: ProviderCatalogState): String = when (state) {
    is ProviderCatalogState.Ready -> {
        val modelCount = state.providers.sumOf { it.models.size }
        state.providers.size.toString() + " 个供应商 · " + modelCount + " 个模型"
    }

    ProviderCatalogState.Unsupported -> "此连接不提供供应商目录"
    is ProviderCatalogState.Error -> "目录加载失败"
    ProviderCatalogState.Idle, ProviderCatalogState.Loading -> "正在读取供应商…"
}

private fun isProviderOnline(provider: ProviderCatalogItem): Boolean =
    provider.ready == true && provider.off != true

/** Sort keys for the chips; online providers stay first for all three. */
private enum class ProviderSort(val label: String) {
    NAME("名称"),
    MODEL_COUNT("模型数"),
    INPUT_PRICE("输入价")
}

/**
 * Case-insensitive match over provider name, id, host and model id/name. Online providers
 * always sort first, then the selected [sort] key breaks the tie.
 */
private fun filterProviders(
    providers: List<ProviderCatalogItem>,
    query: String,
    sort: ProviderSort
): List<ProviderCatalogItem> {
    val needle = query.trim()
    val onlineFirst = compareByDescending<ProviderCatalogItem> { isProviderOnline(it) }
    val comparator = when (sort) {
        ProviderSort.NAME -> onlineFirst.thenBy { it.name }
        ProviderSort.MODEL_COUNT -> onlineFirst
            .thenByDescending { it.models.size }
            .thenBy { it.name }

        ProviderSort.INPUT_PRICE -> onlineFirst
            .thenBy { it.cheapestInputPrice() ?: Double.MAX_VALUE }
            .thenBy { it.name }
    }
    return providers
        .filter { provider -> needle.isEmpty() || provider.matchesQuery(needle) }
        .sortedWith(comparator)
}

private fun ProviderCatalogItem.matchesQuery(needle: String): Boolean =
    name.contains(needle, ignoreCase = true) ||
        id.contains(needle, ignoreCase = true) ||
        host?.contains(needle, ignoreCase = true) == true ||
        models.any { it.hitsQuery(needle) }

private fun ProviderModelInfo.hitsQuery(needle: String): Boolean =
    id.contains(needle, ignoreCase = true) || name?.contains(needle, ignoreCase = true) == true

/** How many of [provider]'s models the current search text hits. */
private fun matchingModelCount(provider: ProviderCatalogItem, query: String): Int {
    val needle = query.trim()
    if (needle.isEmpty()) return 0
    return provider.models.count { it.hitsQuery(needle) }
}

/** Providers whose models match the search text; those cards open automatically. */
private fun providersWithModelMatches(
    providers: List<ProviderCatalogItem>,
    query: String
): Set<String> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptySet()
    return providers.filter { provider -> provider.models.any { it.hitsQuery(needle) } }
        .map { it.id }
        .toSet()
}

private fun ProviderCatalogItem.cheapestInputPrice(): Double? =
    models.mapNotNull { it.inputPrice }.minOrNull()

/** Cheapest–dearest input and output price per 1M tokens; one value when they agree. */
private fun providerPriceSummary(provider: ProviderCatalogItem): String {
    val inputs = provider.models.mapNotNull { it.inputPrice }
    val outputs = provider.models.mapNotNull { it.outputPrice }
    val segments = buildList {
        inputs.minOrNull()?.let { min ->
            add("输入 " + formatPriceRange(min, inputs.maxOrNull() ?: min))
        }
        outputs.minOrNull()?.let { min ->
            add("输出 " + formatPriceRange(min, outputs.maxOrNull() ?: min))
        }
    }
    return if (segments.isEmpty()) "价格未提供" else segments.joinToString(" / ")
}

private fun formatPriceRange(min: Double, max: Double): String =
    if (min == max) "$" + formatPrice(min) else "$" + formatPrice(min) + "–" + formatPrice(max)

private fun ProviderModelInfo.displayName(): String =
    name?.takeIf { it.isNotBlank() } ?: id

private fun formatPrice(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
