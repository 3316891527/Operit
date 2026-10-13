package com.ai.assistance.operit.ui.main.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported
import com.ai.assistance.operit.ui.theme.isWaterGlassSupported

data class NavigationDrawerAppearance(
    val containerColor: Color,
    val titleColor: Color,
    val statusAvailableColor: Color,
    val itemColor: Color,
    val buttonContainerColor: Color,
    val selectedContainerColor: Color,
    val selectedContentColor: Color,
    val dividerColor: Color,
    val waterGlassEnabled: Boolean,
    val buttonLiquidGlassEnabled: Boolean,
)

@Composable
fun rememberNavigationDrawerAppearance(): NavigationDrawerAppearance {
    val snapshot = LocalThemePreferenceSnapshot.current
    val colors = MaterialTheme.colorScheme
    // 侧栏统一跟随主题灰阶，旧主题数据里的局部颜色覆盖不再参与绘制。
    return NavigationDrawerAppearance(
        containerColor = colors.surface,
        titleColor = colors.onSurface,
        statusAvailableColor = colors.onSurface,
        itemColor = colors.onSurface,
        buttonContainerColor = colors.surfaceContainerLow,
        selectedContainerColor = colors.surfaceContainerHigh,
        selectedContentColor = colors.onSurface,
        dividerColor = colors.outlineVariant,
        waterGlassEnabled = snapshot.navigationDrawerWaterGlass && isWaterGlassSupported(),
        buttonLiquidGlassEnabled = snapshot.navigationDrawerButtonLiquidGlass && isLiquidGlassSupported(),
    )
}
