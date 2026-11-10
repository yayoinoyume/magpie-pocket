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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PowerSettingsNew
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
import com.magpie.pocket.core.design.InlineNotice
import com.magpie.pocket.core.design.MagpieDimensions
import com.magpie.pocket.core.design.MagpieSwitch
import com.magpie.pocket.core.design.MagpieTheme
import com.magpie.pocket.core.design.MagpieTypography
import com.magpie.pocket.core.design.springPress
import com.magpie.pocket.core.model.Capabilities
import com.magpie.pocket.core.model.ConnectionConfig
import com.magpie.pocket.core.model.ConnectionHealth
import com.magpie.pocket.core.model.ConnectionStatus
import com.magpie.pocket.core.model.DataMode
import com.magpie.pocket.core.model.PrivacyState
import com.magpie.pocket.core.model.ProviderCatalogState

/**
 * More page: the gateway panel and three panels of entries that belong to no single statistic.
 * The gateway panel is first because a failed connection is the one thing a reader must be able
 * to fix from here; the catalog and the masking log open their existing sheets, display
 * preferences open their own sheet, and the background keep-alive toggle writes the preference
 * that sheet also writes. Keeping them here leaves the usage page purely statistical.
 */
@Composable
fun MoreScreen(
    config: ConnectionConfig,
    capabilities: Capabilities?,
    status: ConnectionStatus?,
    providerCatalog: ProviderCatalogState,
    privacy: PrivacyState,
    contentPadding: PaddingValues,
    onOpenProviders: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDisplayPreferences: () -> Unit,
    onEditConnection: () -> Unit,
    onDisconnect: () -> Unit,
    onRetry: () -> Unit,
    onKeepLiveConnectionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    val preferences = MagpieTheme.preferences
    val versionName = rememberVersionName()
    val keepAlive = preferences.keepLiveConnectionInBackground
    val keepAliveLabel = if (keepAlive) "已开启" else "已关闭"
    val statusMessage = status?.message
    val needsCredentials =
        status?.health == ConnectionHealth.AUTH_REQUIRED ||
            status?.health == ConnectionHealth.ACCESS_DENIED

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
                    text = "网关、数据、偏好与关于",
                    fontSize = MagpieTypography.Subhead,
                    color = colors.textSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        item { SectionLabel("网关") }

        item {
            DataPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MagpieDimensions.RadiusDataPanel),
                contentPadding = PaddingValues(18.dp)
            ) {
                Column {
                    if (statusMessage != null) {
                        InlineNotice(
                            message = statusMessage,
                            isError = true,
                            actionLabel = if (needsCredentials) "修改连接" else "重试",
                            onAction = if (needsCredentials) onEditConnection else onRetry
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = config.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                            lineHeight = 20.sp
                        )
                        Text(
                            text = gatewayEndpointLabel(config),
                            fontSize = MagpieTypography.Caption,
                            color = colors.textSecondary,
                            lineHeight = 16.sp
                        )
                    }

                    HorizontalDivider(
                        color = colors.divider,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    capabilityRows(capabilities).forEach { (label, supported) ->
                        CapabilityStatusRow(label = label, supported = supported)
                    }

                    HorizontalDivider(
                        color = colors.divider,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    MoreRow(
                        icon = Icons.Filled.Link,
                        title = "配置网关连接",
                        subtitle = if (config.mode == DataMode.DEMO) "切换到实时网关" else "修改地址与凭证",
                        onClick = onEditConnection
                    )

                    if (config.mode == DataMode.LIVE) {
                        HorizontalDivider(color = colors.divider)
                        MoreRow(
                            icon = Icons.Filled.PowerSettingsNew,
                            title = "断开连接并返回演示模式",
                            subtitle = "清除已保存的地址与凭证",
                            onClick = onDisconnect
                        )
                    }
                }
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
 * One gateway capability and its answer. A null [supported] means the app has not learned the
 * capability list yet, so the row prints an em dash instead of borrowing the model's defaults.
 */
@Composable
private fun CapabilityStatusRow(
    label: String,
    supported: Boolean?
) {
    val colors = MagpieTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = colors.textPrimary,
            lineHeight = 18.sp
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (supported == true) colors.success.copy(alpha = 0.14f) else colors.divider)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = when (supported) {
                    true -> "可用"
                    false -> "不可用"
                    null -> "—"
                },
                fontSize = MagpieTypography.Caption,
                color = when (supported) {
                    true -> colors.success
                    false -> colors.textSecondary
                    null -> colors.textTertiary
                },
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * The five gateway capabilities this page reports: trace, ledger, usage, quotas, groups.
 * A null [capabilities] means the first probe has not answered, so every answer stays null and
 * the rows print em dashes rather than the model's "supported" defaults.
 */
internal fun capabilityRows(capabilities: Capabilities?): List<Pair<String, Boolean?>> = listOf(
    "路由追踪" to capabilities?.trace,
    "历史账本" to capabilities?.ledger,
    "用量" to capabilities?.usage,
    "额度" to capabilities?.quotas,
    "路由组" to capabilities?.groups
)

/** Live gateways name the address they talk to; the demo gateway keeps everything on-device. */
private fun gatewayEndpointLabel(config: ConnectionConfig): String =
    if (config.mode == DataMode.DEMO) "演示模式 · 本地数据隔离" else (config.baseUrl ?: "已连接")

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
