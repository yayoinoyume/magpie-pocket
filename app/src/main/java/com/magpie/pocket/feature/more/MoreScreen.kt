package com.magpie.pocket.feature.more

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magpie.pocket.core.design.DataPanel
import com.magpie.pocket.core.design.MagpieDimensions
import com.magpie.pocket.core.design.MagpieSwitch
import com.magpie.pocket.core.design.MagpieTheme
import com.magpie.pocket.core.design.MagpieTypography
import com.magpie.pocket.core.design.springPress
import com.magpie.pocket.core.model.PrivacyState
import com.magpie.pocket.core.model.ProviderCatalogState

/**
 * More page: three panels of entries that belong to no single statistic. The catalog and the
 * masking log open their existing sheets, display preferences reuse the sheet the gateway
 * detail already opens, and the background keep-alive toggle writes the same preference the
 * sheet writes. Keeping them here leaves the usage page purely statistical.
 */
@Composable
fun MoreScreen(
    providerCatalog: ProviderCatalogState,
    privacy: PrivacyState,
    contentPadding: PaddingValues,
    onOpenProviders: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDisplayPreferences: () -> Unit,
    onKeepLiveConnectionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    val preferences = MagpieTheme.preferences
    val versionName = rememberVersionName()
    val keepAlive = preferences.keepLiveConnectionInBackground
    val keepAliveLabel = if (keepAlive) "已开启" else "已关闭"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MagpieDimensions.PagePadding),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "更多",
                    fontSize = MagpieTypography.PageTitle,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "数据、偏好与关于",
                    fontSize = MagpieTypography.Subhead,
                    color = colors.textSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        item { SectionLabel("数据") }

        item {
            DataPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MagpieDimensions.RadiusDataPanel),
                contentPadding = PaddingValues(18.dp)
            ) {
                Column {
                    MoreRow(
                        icon = Icons.Filled.Key,
                        title = "供应商目录",
                        subtitle = providerSubtitle(providerCatalog),
                        onClick = onOpenProviders
                    )
                    HorizontalDivider(color = colors.divider)
                    MoreRow(
                        icon = Icons.Filled.Security,
                        title = "隐私掩码",
                        subtitle = privacySubtitle(privacy),
                        onClick = onOpenPrivacy
                    )
                }
            }
        }

        item { SectionLabel("偏好") }

        item {
            DataPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MagpieDimensions.RadiusDataPanel),
                contentPadding = PaddingValues(18.dp)
            ) {
                Column {
                    MoreRow(
                        icon = Icons.Filled.Tune,
                        title = "显示偏好",
                        subtitle = "主题 · 材质 · 动画 · 账号打码",
                        onClick = onOpenDisplayPreferences
                    )
                    HorizontalDivider(color = colors.divider)
                    MoreRow(
                        icon = Icons.Filled.Sync,
                        title = "后台保持连接",
                        subtitle = "实时连接在后台短暂保持",
                        description = "后台保持连接，实时连接在后台短暂保持，当前" + keepAliveLabel,
                        onClick = { onKeepLiveConnectionChange(!keepAlive) }
                    ) {
                        MagpieSwitch(
                            label = "后台保持连接",
                            checked = keepAlive,
                            onCheckedChange = onKeepLiveConnectionChange
                        )
                    }
                }
            }
        }

        item { SectionLabel("关于") }

        item {
            DataPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MagpieDimensions.RadiusDataPanel),
                contentPadding = PaddingValues(18.dp)
            ) {
                Column {
                    MoreRow(
                        icon = Icons.Filled.Info,
                        title = "版本",
                        subtitle = "$versionName · 只读观测"
                    )
                    HorizontalDivider(color = colors.divider)
                    MoreRow(
                        icon = Icons.Filled.Description,
                        title = "开源许可",
                        subtitle = "MIT · Lucide (ISC)"
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

/**
 * One entry: 30dp tinted icon block, title over subtitle, and either a trailing control or the
 * chevron that promises another surface. Rows without [onClick] are informational and carry no
 * button role, so TalkBack never offers a target that does nothing.
 */
@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = MagpieTheme.colors
    val interactive = if (onClick == null) {
        Modifier
    } else {
        Modifier
            .springPress(scaleDown = 0.98f, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description ?: "$title，$subtitle"
            }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .then(interactive),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.accentSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                lineHeight = 20.sp
            )
            Text(
                text = subtitle,
                fontSize = MagpieTypography.Caption,
                color = colors.textSecondary,
                lineHeight = 16.sp
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    val colors = MagpieTheme.colors
    Text(
        text = label,
        fontSize = MagpieTypography.Label,
        fontWeight = FontWeight.Medium,
        color = colors.textPrimary,
        lineHeight = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 9.dp)
    )
}

/**
 * Same count wording as the provider sheet header, so the row and the sheet never disagree.
 * The online rule (ready and not off) is the sheet's own rule; it is private there, so it is
 * repeated instead of exported.
 */
private fun providerSubtitle(state: ProviderCatalogState): String {
    val providers = (state as? ProviderCatalogState.Ready)?.providers
    if (providers.isNullOrEmpty()) return "查看模型、价格与并发"
    val online = providers.count { it.ready == true && it.off != true }
    return "${providers.size} 个供应商 · $online 个在线"
}

/**
 * Counts the masked value buckets, which is exactly what the privacy sheet labels
 * "已掩码的值 · 共 N 个" for the same window. Falling back to the generic line keeps the row
 * honest when the gateway reports activity without value buckets.
 */
private fun privacySubtitle(state: PrivacyState): String {
    val ready = state as? PrivacyState.Ready ?: return "查看已屏蔽的敏感内容"
    val maskedValues = ready.summary.values.size
    if (maskedValues == 0) return "查看已屏蔽的敏感内容"
    return "近 ${ready.selectedDays} 天 $maskedValues 个值被掩码"
}

/** Version name of the installed package; the app does not generate BuildConfig. */
@Composable
private fun rememberVersionName(): String {
    val context = LocalContext.current
    return remember(context) { packageVersionName(context) }
}

@Suppress("DEPRECATION")
private fun packageVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "—"
