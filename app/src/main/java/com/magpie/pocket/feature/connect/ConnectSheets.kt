package com.magpie.pocket.feature.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magpie.pocket.core.design.InlineNotice
import com.magpie.pocket.core.design.MagpieDimensions
import com.magpie.pocket.core.design.MagpieTheme
import com.magpie.pocket.core.design.MagpieSwitch
import com.magpie.pocket.core.model.Capabilities
import com.magpie.pocket.core.model.ConnectionConfig
import com.magpie.pocket.core.model.DataMode
import com.magpie.pocket.core.model.DisplayPreferences
import com.magpie.pocket.core.model.GlassTier
import com.magpie.pocket.core.model.ThemeMode
import com.magpie.pocket.core.network.MagpieApiClient
import com.magpie.pocket.core.network.MagpieSession
import com.magpie.pocket.core.network.MagpieTiming
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectBottomSheet(
    currentConfig: ConnectionConfig,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    // Receives the config plus the client and probe the verification already paid for.
    onConnectSuccess: (ConnectionConfig, MagpieApiClient, MagpieSession.ProbeResult) -> Unit,
    onSwitchToDemo: () -> Unit
) {
    val colors = MagpieTheme.colors
    val scope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf(currentConfig.baseUrl ?: "") }
    var keyInput by remember { mutableStateOf(currentConfig.webKey ?: "") }
    var cfClientIdInput by remember { mutableStateOf(currentConfig.cfAccessClientId ?: "") }
    var cfClientSecretInput by remember { mutableStateOf(currentConfig.cfAccessClientSecret ?: "") }
    var showCfAccessSection by remember { mutableStateOf(currentConfig.cfAccessClientId?.isNotBlank() == true) }
    var connectionName by remember { mutableStateOf(currentConfig.name) }
    var isTesting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var probeResult by remember { mutableStateOf<Capabilities?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = MagpieDimensions.RadiusSheetTop, topEnd = MagpieDimensions.RadiusSheetTop)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MagpieDimensions.PagePadding)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "连接管理服务",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭", tint = colors.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = connectionName,
                onValueChange = { connectionName = it },
                label = { Text("连接名称") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.divider
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = urlInput,
                onValueChange = { input ->
                    urlInput = input
                    // Automatically extract ?k= if user pasted full URL
                    val extractedKey = MagpieApiClient.extractKeyFromUrl(input)
                    if (extractedKey != null) {
                        keyInput = extractedKey
                        urlInput = MagpieApiClient.normalizeBaseUrl(input)
                    }
                },
                label = { Text("管理服务地址 (非推理 /v1)") },
                placeholder = { Text("https://magpie.example.com") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.divider
                ),
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Link, contentDescription = null, tint = colors.textSecondary)
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("Web Key (认证凭证)") },
                placeholder = { Text("留空或输入服务端密钥") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.divider
                ),
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Key, contentDescription = null, tint = colors.textSecondary)
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Web Key 是 Magpie 管理服务的登录密钥；Cloudflare Access 凭证只用于通过前置网关，两者不能互相替代。",
                fontSize = 11.sp,
                color = colors.textSecondary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Expandable Cloudflare Access Service Token section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showCfAccessSection = !showCfAccessSection }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = if (showCfAccessSection) colors.accent else colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cloudflare Access 凭证 (可选)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (showCfAccessSection) colors.accent else colors.textSecondary
                    )
                }
                Text(
                    text = if (showCfAccessSection) "收起" else "展开",
                    fontSize = 12.sp,
                    color = colors.accent
                )
            }

            if (showCfAccessSection) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = cfClientIdInput,
                    onValueChange = { cfClientIdInput = it },
                    label = { Text("CF-Access-Client-Id") },
                    placeholder = { Text("xxxxxx.access") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.divider
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = cfClientSecretInput,
                    onValueChange = { cfClientSecretInput = it },
                    label = { Text("CF-Access-Client-Secret") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.divider
                    ),
                    singleLine = true
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                InlineNotice(
                    message = errorMessage!!,
                    isError = true
                )
            }

            if (probeResult != null) {
                Spacer(modifier = Modifier.height(12.dp))
                InlineNotice(
                    message = "验证成功！服务端版本: ${probeResult?.verifiedDeploymentVersion ?: "Magpie"}",
                    icon = Icons.Filled.Check,
                    isError = false
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (urlInput.isBlank()) {
                        errorMessage = "请输入有效的管理服务地址"
                        return@Button
                    }
                    isTesting = true
                    errorMessage = null
                    probeResult = null

                    scope.launch {
                        try {
                            val client = MagpieApiClient(
                                cfAccessClientId = cfClientIdInput.ifBlank { null },
                                cfAccessClientSecret = cfClientSecretInput.ifBlank { null }
                            )
                            val cleanUrl = MagpieApiClient.normalizeBaseUrl(urlInput)

                            // Shared production rule: sign in on this exact client, then
                            // require /api/state and only degrade optional endpoints on 404.
                            val result = MagpieSession.connect(
                                client = client,
                                baseUrl = cleanUrl,
                                webKey = keyInput.ifBlank { null }
                            )
                            probeResult = result.capabilities
                            MagpieTiming.mark("connect-verified")

                            val newConfig = ConnectionConfig(
                                id = "live-${System.currentTimeMillis()}",
                                name = connectionName.ifBlank { "生产网关" },
                                mode = DataMode.LIVE,
                                baseUrl = cleanUrl,
                                webKey = keyInput.ifBlank { null },
                                serverReadOnlyPermissionConfirmed = result.state.readOnly,
                                cfAccessClientId = cfClientIdInput.ifBlank { null },
                                cfAccessClientSecret = cfClientSecretInput.ifBlank { null }
                            )
                            // Hand over the verified client and its probe bodies: the first
                            // refresh reuses both instead of reading state and groups again.
                            onConnectSuccess(newConfig, client, result)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errorMessage = safeConnectionError(e)
                        } finally {
                            isTesting = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                enabled = !isTesting
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在校验接口与能力...")
                } else {
                    Text("测试并保存连接", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    onSwitchToDemo()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent.copy(alpha = 0.12f),
                    contentColor = colors.accent
                )
            ) {
                Text("体验演示模式 (无需服务)", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun safeConnectionError(error: Throwable): String = when (error) {
    is com.magpie.pocket.core.security.CredentialStorageException ->
        "设备加密存储不可用，连接未保存。请检查锁屏与安全设置后重试。"
    is com.magpie.pocket.core.network.AuthenticationRequiredException ->
        "需要 Magpie 登录：请填写有效的 Web Key（不是 Cloudflare Access 凭证）。"
    is com.magpie.pocket.core.network.CloudflareAccessRequiredException ->
        "Cloudflare Access 拒绝了访问：请检查 Service Token 或网络访问权限。"
    is com.magpie.pocket.core.network.PermissionDeniedException ->
        "访问被拒绝：请检查 Cloudflare Access Service Token 权限。"
    is com.magpie.pocket.core.network.NetworkUnavailableException ->
        "网络不可达：请检查网络连接与管理服务地址。"
    is com.magpie.pocket.core.network.UnexpectedResponseException ->
        "服务响应不兼容：该地址可能不是 Magpie 管理服务。"
    else -> "连接测试失败，请检查地址与凭证。"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayPreferencesSheet(
    preferences: DisplayPreferences,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onUpdateTheme: (ThemeMode) -> Unit,
    onUpdateGlass: (GlassTier) -> Unit,
    onUpdateReduceMotion: (Boolean) -> Unit,
    onUpdateMaskAccounts: (Boolean) -> Unit,
    onUpdateKeepLiveConnection: (Boolean) -> Unit = {}
) {
    val colors = MagpieTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = MagpieDimensions.RadiusSheetTop, topEnd = MagpieDimensions.RadiusSheetTop)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MagpieDimensions.PagePadding)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "显示偏好",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭", tint = colors.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "主题外观", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary,
            lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    ThemeMode.SYSTEM to "跟随系统",
                    ThemeMode.LIGHT to "浅色",
                    ThemeMode.DARK to "深色"
                ).forEach { (mode, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable { onUpdateTheme(mode) }
                    ) {
                        RadioButton(
                            selected = preferences.themeMode == mode,
                            onClick = { onUpdateTheme(mode) },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.accent)
                        )
                        Text(text = label, fontSize = 12.sp, color = colors.textPrimary,
                        lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Glass Tier
            Text(text = "控件透明度", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary,
            lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            listOf(
                GlassTier.CLEAR to "标准透明",
                GlassTier.TINTED to "更高不透明度",
                GlassTier.SOLID to "纯色（最易读）"
            ).forEach { (tier, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { onUpdateGlass(tier) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = preferences.glassTier == tier,
                        onClick = { onUpdateGlass(tier) },
                        colors = RadioButtonDefaults.colors(selectedColor = colors.accent)
                    )
                    Text(text = label, fontSize = 12.sp, color = colors.textPrimary,
                    lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reduce motion
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MagpieDimensions.TouchTargetMinimum)
                    .clickable { onUpdateReduceMotion(!preferences.reduceMotion) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "减少动态效果", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary,
                    lineHeight = 16.sp
                    )
                    Text(text = "停用按压与流向动画", fontSize = 11.sp, color = colors.textSecondary,
                    lineHeight = 16.sp
                    )
                }
                MagpieSwitch(
                    label = "减少动态效果",
                    checked = preferences.reduceMotion,
                    onCheckedChange = onUpdateReduceMotion
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mask account names
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MagpieDimensions.TouchTargetMinimum)
                    .clickable { onUpdateMaskAccounts(!preferences.maskAccountNames) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "账号名称打码", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary,
                    lineHeight = 16.sp
                    )
                    Text(text = "隐藏邮箱与密钥的明文片段", fontSize = 11.sp, color = colors.textSecondary,
                    lineHeight = 16.sp
                    )
                }
                MagpieSwitch(
                    label = "账号名称打码",
                    checked = preferences.maskAccountNames,
                    onCheckedChange = onUpdateMaskAccounts
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MagpieDimensions.TouchTargetMinimum)
                    .clickable { onUpdateKeepLiveConnection(!preferences.keepLiveConnectionInBackground) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "后台保持连接",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = "实时连接在后台短暂保持；可随时关闭",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        lineHeight = 16.sp
                    )
                }
                MagpieSwitch(
                    label = "后台保持连接",
                    checked = preferences.keepLiveConnectionInBackground,
                    onCheckedChange = onUpdateKeepLiveConnection,
                    enabled = true
                )
            }
        }
    }
}
