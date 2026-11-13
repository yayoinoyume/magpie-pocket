package com.magpie.pocket.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.magpie.pocket.core.design.MagpieTypography
import com.magpie.pocket.core.design.MagpieTypographySet
import com.magpie.pocket.core.model.ThemeMode
import kotlinx.coroutines.launch

/**
 * The single configuration screen for every home-screen size. It stores one global
 * `widget_config` preference set; Cancel leaves the stored set untouched.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )
        applyBars(isSystemDark())

        setContent {
            val loaded by produceState<WidgetConfig?>(initialValue = null) {
                value = WidgetConfigStore.load(this@WidgetConfigActivity)
            }
            val appThemeMode by produceState<ThemeMode>(initialValue = ThemeMode.SYSTEM) {
                value = WidgetThemeSource.loadAppThemeMode(this@WidgetConfigActivity)
            }
            val systemDark = isSystemInDarkTheme()
            var edited by remember(loaded) { mutableStateOf(loaded) }
            val config = edited ?: loaded
            // The preview follows the widget override the user is editing, resolved against
            // the app ThemeMode exactly as the home-screen paint does.
            val isDark = config?.isDark(systemDark, appThemeMode)
                ?: WidgetThemeSource.isDark(WidgetTheme.SYSTEM, appThemeMode, systemDark)
            LaunchedEffect(isDark) { applyBars(isDark) }

            WidgetConfigTheme(isDark = isDark) {
                if (config == null) {
                    WidgetConfigLoading()
                } else {
                    WidgetConfigScreen(
                        config = config,
                        onConfigChange = { edited = it },
                        onSave = { save(config) },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    private fun save(config: WidgetConfig) {
        lifecycleScope.launch {
            val saved = runCatching {
                WidgetConfigStore.save(this@WidgetConfigActivity, config)
            }.isSuccess
            if (!saved) {
                Toast.makeText(this@WidgetConfigActivity, "保存失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            WidgetRefresh.applyRefreshPolicy(this@WidgetConfigActivity)
            WidgetRefresh.updateAll(this@WidgetConfigActivity)
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        }
    }

    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun applyBars(isDark: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = if (isDark) {
                SystemBarStyle.dark(transparent)
            } else {
                SystemBarStyle.light(transparent, transparent)
            },
            navigationBarStyle = if (isDark) {
                SystemBarStyle.dark(transparent)
            } else {
                SystemBarStyle.light(transparent, transparent)
            }
        )
        window.setBackgroundDrawable(
            ColorDrawable(if (isDark) 0xFF1D1C25.toInt() else 0xFFFFFFFF.toInt())
        )
    }
}

@Composable
private fun WidgetConfigTheme(isDark: Boolean, content: @Composable () -> Unit) {
    val palette = if (isDark) ConfigDarkColors else ConfigLightColors
    val scheme = if (isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.background,
            background = palette.background,
            surface = palette.background,
            onBackground = palette.text,
            onSurface = palette.text
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.background,
            background = palette.background,
            surface = palette.background,
            onBackground = palette.text,
            onSurface = palette.text
        )
    }
    CompositionLocalProvider(LocalWidgetConfigPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MagpieTypographySet,
            content = content
        )
    }
}

@Composable
private fun WidgetConfigLoading() {
    val palette = LocalWidgetConfigPalette.current
    Box(
        modifier = Modifier.fillMaxSize()
            .background(palette.background)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "正在读取配置…",
            style = MagpieTypography.BodyStyle,
            color = palette.secondary
        )
    }
}

@Composable
private fun WidgetConfigScreen(
    config: WidgetConfig,
    onConfigChange: (WidgetConfig) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val palette = LocalWidgetConfigPalette.current
    Box(
        modifier = Modifier.fillMaxSize()
            .background(palette.background)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "小组件设置",
                style = MagpieTypography.PageTitleStyle.copy(fontFamily = FontFamily.Serif),
                color = palette.text
            )
            Spacer(Modifier.height(20.dp))

            ConfigSection(title = "内容") {
                WidgetContent.values().forEach { content ->
                    ConfigOptionRow(
                        label = content.label,
                        selected = config.content == content,
                        onClick = { onConfigChange(config.copy(content = content)) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            ConfigSection(title = "主题") {
                WidgetTheme.values().forEach { theme ->
                    ConfigOptionRow(
                        label = theme.label,
                        selected = config.theme == theme,
                        onClick = { onConfigChange(config.copy(theme = theme)) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            ConfigSection(title = "刷新") {
                WidgetRefreshMode.values().forEach { refresh ->
                    ConfigOptionRow(
                        label = if (refresh == WidgetRefreshMode.EVENT_DRIVEN) {
                            "事件驱动（推荐）"
                        } else {
                            refresh.label
                        },
                        selected = config.refresh == refresh,
                        onClick = { onConfigChange(config.copy(refresh = refresh)) }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text(text = "取消", color = palette.secondary, fontSize = 14.sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = palette.background
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "保存", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ConfigSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalWidgetConfigPalette.current
    Text(
        text = title,
        style = MagpieTypography.SectionTitleStyle.copy(fontFamily = FontFamily.Serif),
        color = palette.text
    )
    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(palette.background)
            .border(
                width = 0.75.dp,
                color = palette.text.copy(alpha = 0.10f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        content = content
    )
}

@Composable
private fun ConfigOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalWidgetConfigPalette.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) palette.accent.copy(alpha = 0.12f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = palette.accent,
                unselectedColor = palette.secondary
            )
        )
        Text(
            text = label,
            style = MagpieTypography.BodyStyle,
            color = palette.text,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

private data class WidgetConfigPalette(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color
)

private val LocalWidgetConfigPalette = staticCompositionLocalOf { ConfigLightColors }

// The widget palette is intentionally separate from the app's Material theme: the config
// screen previews the same fixed colors the home-screen surfaces use.
private val ConfigDarkColors = WidgetConfigPalette(
    background = Color(0xFF1D1C25),
    text = Color(0xFFE9E8F0),
    secondary = Color(0xFFE9E8F0).copy(alpha = 0.70f),
    accent = Color(0xFF9A92EE)
)

private val ConfigLightColors = WidgetConfigPalette(
    background = Color(0xFFFFFFFF),
    text = Color(0xFF1C1A17),
    secondary = Color(0xFF1C1A17).copy(alpha = 0.70f),
    accent = Color(0xFF6C5CE7)
)
