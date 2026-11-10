package com.magpie.pocket.feature.providers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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

/**
 * Provider catalog sheet. The header and the search field stay fixed; the catalog itself
 * is a list of expandable provider cards. Ordering is online-first, then name. Only the
 * key count ever renders — key names and key material stay out of the UI.
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
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }
    val configuration = LocalConfiguration.current
    val horizontalPadding = if (configuration.screenWidthDp < 360) {
        MagpieDimensions.CompactPagePadding
    } else {
        MagpieDimensions.PagePadding
    }
    val stackMeta = configuration.fontScale >= 1.45f

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
                .padding(horizontal = horizontalPadding)
        ) {
            ProviderCatalogHeader(state = state, onDismiss = onDismiss)

            when (state) {
                is ProviderCatalogState.Ready -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    ProviderSearchField(
                        query = query,
                        onQueryChange = {
                            query = it
                            onSearch(it)
                        },
                        onClear = {
                            query = ""
                            onSearch("")
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val providers = remember(state.providers, query) {
                        filterProviders(state.providers, query)
                    }
                    when {
                        state.providers.isEmpty() -> ProviderEmptyState(
                            modifier = Modifier.weight(1f),
                            onRetry = onRetry
                        )
                        providers.isEmpty() -> NoSearchResults(modifier = Modifier.weight(1f))
                        else -> LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(items = providers, key = { it.id }) { provider ->
                                ProviderCard(
                                    provider = provider,
                                    expanded = provider.id in expandedIds,
                                    stackMeta = stackMeta,
                                    onToggle = {
                                        expandedIds = if (provider.id in expandedIds) {
                                            expandedIds - provider.id
                                        } else {
                                            expandedIds + provider.id
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
                    Spacer(modifier = Modifier.height(32.dp))
                }

                is ProviderCatalogState.Error -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    InlineNotice(
                        message = state.message,
                        isError = true,
                        actionLabel = "重试",
                        onAction = onRetry
                    )
                    Spacer(modifier = Modifier.height(32.dp))
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

@Composable
private fun ProviderCard(
    provider: ProviderCatalogItem,
    expanded: Boolean,
    stackMeta: Boolean,
    onToggle: () -> Unit
) {
    val colors = MagpieTheme.colors
    val reduceMotion = MagpieTheme.preferences.reduceMotion
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduceMotion) tween(0) else tween(220),
        label = "providerChevron"
    )
    val (statusLabel, statusColor, statusSurface) = when {
        provider.off == true -> Triple("停用", colors.error, colors.errorSurface)
        provider.ready == true -> Triple("在线", colors.success, colors.successSurface)
        else -> Triple("离线", colors.textSecondary, colors.surfaceSubtle)
    }
    val enabledCount = provider.models.count { it.enabled == true }
    val modelCountText = buildString {
        append(provider.models.size)
        append(" 个模型")
        if (enabledCount > 0) {
            append(" · ")
            append(enabledCount)
            append(" 已启用")
        }
    }

    DataPanel(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                stateDescription = if (expanded) "已展开" else "已收起"
            },
        shape = RoundedCornerShape(MagpieDimensions.RadiusCard),
        onClick = onToggle,
        contentPadding = PaddingValues(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MagpieDimensions.TouchTargetMinimum)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = provider.name,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { contentDescription = provider.name }
                )
                Spacer(modifier = Modifier.width(8.dp))
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

            Spacer(modifier = Modifier.height(4.dp))

            if (stackMeta) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ProviderMetaText(modelCountText)
                    provider.host?.takeIf { it.isNotBlank() }?.let { host ->
                        ProviderMetaText(host)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderMetaText(modelCountText)
                    provider.host?.takeIf { it.isNotBlank() }?.let { host ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = host,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = if (reduceMotion) {
                    EnterTransition.None
                } else {
                    expandVertically(animationSpec = tween(220)) +
                        fadeIn(animationSpec = tween(220))
                },
                exit = if (reduceMotion) {
                    ExitTransition.None
                } else {
                    shrinkVertically(animationSpec = tween(220)) +
                        fadeOut(animationSpec = tween(160))
                }
            ) {
                ProviderExpandedContent(provider = provider)
            }
        }
    }
}

@Composable
private fun ProviderExpandedContent(provider: ProviderCatalogItem) {
    val colors = MagpieTheme.colors
    val shownModels = provider.models.take(8)
    val specs = buildList {
        if (provider.speaks.isNotEmpty()) {
            add("协议 " + provider.speaks.joinToString(" / "))
        }
        if (provider.keyNames.isNotEmpty()) {
            add("密钥 " + provider.keyNames.size + " 个")
        }
        provider.maxConcurrency?.let { add("并发 " + it) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(thickness = 0.75.dp, color = colors.divider)
        Spacer(modifier = Modifier.height(10.dp))

        if (provider.models.isNotEmpty()) {
            ModelChips(models = provider.models)
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
                ModelDetailRow(model = model)
                if (index != shownModels.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        val hiddenCount = provider.models.size - shownModels.size
        if (hiddenCount > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "还有 " + hiddenCount + " 个模型",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colors.textTertiary
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
private fun ModelDetailRow(model: ProviderModelInfo) {
    val colors = MagpieTheme.colors
    val name = model.displayName()
    val meta = buildList {
        model.context?.let { add("上下文 " + MetricCalculators.formatCompactNumber(it)) }
        model.output?.let { add("输出 " + MetricCalculators.formatCompactNumber(it)) }
        val inputPrice = model.inputPrice
        val outputPrice = model.outputPrice
        if (inputPrice != null && outputPrice != null) {
            add("$" + formatPrice(inputPrice) + " / $" + formatPrice(outputPrice))
        }
        if (model.efforts.isNotEmpty()) {
            add(model.efforts.joinToString("/"))
        }
        if (model.images == true) {
            add("图片")
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = name,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics { contentDescription = name }
        )
        if (meta.isNotEmpty()) {
            Text(
                text = meta.joinToString(" · "),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colors.textSecondary
            )
        }
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

@Composable
private fun NoSearchResults(modifier: Modifier = Modifier) {
    val colors = MagpieTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "没有匹配的供应商",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = colors.textSecondary
        )
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
        Box(
            modifier = Modifier
                .height(MagpieDimensions.TouchTargetMinimum)
                .clip(RoundedCornerShape(MagpieDimensions.RadiusItem))
                .background(colors.accentSurface)
                .springPress(scaleDown = 0.98f) { onRetry() }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "重试",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.accent
            )
        }
    }
}

private fun providerSubtitle(state: ProviderCatalogState): String = when (state) {
    is ProviderCatalogState.Ready -> {
        val onlineCount = state.providers.count { isProviderOnline(it) }
        state.providers.size.toString() + " 个供应商 · " + onlineCount + " 个在线"
    }

    ProviderCatalogState.Unsupported -> "此连接不提供供应商目录"
    is ProviderCatalogState.Error -> "目录加载失败"
    ProviderCatalogState.Idle, ProviderCatalogState.Loading -> "正在读取供应商…"
}

private fun isProviderOnline(provider: ProviderCatalogItem): Boolean =
    provider.ready == true && provider.off != true

/** Case-insensitive match over provider name, id and host, then online-first name ordering. */
private fun filterProviders(
    providers: List<ProviderCatalogItem>,
    query: String
): List<ProviderCatalogItem> {
    val needle = query.trim()
    return providers
        .filter { provider ->
            needle.isEmpty() ||
                provider.name.contains(needle, ignoreCase = true) ||
                provider.id.contains(needle, ignoreCase = true) ||
                provider.host?.contains(needle, ignoreCase = true) == true
        }
        .sortedWith(
            compareByDescending<ProviderCatalogItem> { isProviderOnline(it) }
                .thenBy { it.name }
        )
}

private fun ProviderModelInfo.displayName(): String =
    name?.takeIf { it.isNotBlank() } ?: id

private fun formatPrice(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
