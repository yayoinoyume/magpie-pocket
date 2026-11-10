package com.magpie.pocket.feature.providers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magpie.pocket.core.design.InlineNotice
import com.magpie.pocket.core.design.MagpieDimensions
import com.magpie.pocket.core.design.MagpieTheme
import com.magpie.pocket.core.design.springPress
import com.magpie.pocket.core.model.ProviderCatalogState

/**
 * Provider catalog sheet skeleton. M4 replaces the body with cards, sorting and
 * per-provider model rows; the signature stays stable.
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
                .padding(horizontal = MagpieDimensions.PagePadding)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "供应商目录",
                        fontSize = 21.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = when (state) {
                            is ProviderCatalogState.Ready ->
                                state.providers.size.toString() + " 个供应商"
                            ProviderCatalogState.Unsupported -> "此连接不提供目录"
                            is ProviderCatalogState.Error -> "目录加载失败"
                            else -> "正在加载"
                        },
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
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

            Spacer(modifier = Modifier.height(12.dp))

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
                    .padding(horizontal = 14.dp),
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
                            color = colors.textSecondary
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            onSearch(it)
                        },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = colors.textPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (state) {
                is ProviderCatalogState.Error -> InlineNotice(
                    message = state.message,
                    isError = true,
                    actionLabel = "重试",
                    onAction = onRetry
                )
                ProviderCatalogState.Unsupported -> InlineNotice(
                    message = "此连接不提供供应商目录（404），不影响路由与用量"
                )
                is ProviderCatalogState.Ready -> {
                    if (state.providers.isEmpty()) {
                        EmptyCatalog(onRetry = onRetry)
                    } else {
                        Text(
                            text = "供应商卡片、排序与模型明细将在下一版本接入",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = colors.textSecondary
                        )
                    }
                }
                else -> Text(
                    text = "正在加载供应商目录",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun EmptyCatalog(onRetry: () -> Unit) {
    val colors = MagpieTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "暂无供应商目录",
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
