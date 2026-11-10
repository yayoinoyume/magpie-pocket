package com.magpie.pocket.feature.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magpie.pocket.core.design.DataPanel
import com.magpie.pocket.core.design.InlineNotice
import com.magpie.pocket.core.design.MagpieDimensions
import com.magpie.pocket.core.design.MagpieTheme
import com.magpie.pocket.core.model.PrivacyState

/**
 * Privacy masking sheet skeleton. M5 replaces the body with the day bars, kind
 * ranking, discovery paths and record list; the signature stays stable.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySheet(
    state: PrivacyState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDaysChange: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onReveal: (String) -> Unit
) {
    val colors = MagpieTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                        text = "隐私掩码",
                        fontSize = 21.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "只读，原文仅临时显示",
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

            Spacer(modifier = Modifier.height(16.dp))

            when (state) {
                is PrivacyState.Error -> InlineNotice(
                    message = state.message,
                    isError = true,
                    actionLabel = "重试",
                    onAction = onRetry
                )
                PrivacyState.Unsupported -> InlineNotice(
                    message = "此连接不提供隐私掩码接口（404）"
                )
                is PrivacyState.Ready -> {
                    val summary = state.summary
                    DataPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(MagpieDimensions.RadiusDataPanel),
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SummaryColumn("已掩码", summary.total, Modifier.weight(1f))
                            SummaryColumn("涉及请求", summary.requests, Modifier.weight(1f))
                            SummaryColumn("敏感类型", summary.kinds.size.toLong(), Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "日柱、类型排行与命中记录将在下一版本接入",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = colors.textSecondary
                    )
                }
                else -> Text(
                    text = "正在加载隐私掩码数据",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun SummaryColumn(label: String, value: Long?, modifier: Modifier) {
    val colors = MagpieTheme.colors
    Column(modifier = modifier) {
        Text(label, fontSize = 11.sp, lineHeight = 16.sp, color = colors.textSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value?.toString() ?: "—",
            fontSize = 24.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary
        )
    }
}
