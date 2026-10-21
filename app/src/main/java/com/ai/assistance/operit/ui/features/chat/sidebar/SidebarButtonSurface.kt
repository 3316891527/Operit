package com.ai.assistance.operit.ui.features.chat.sidebar
import com.ai.assistance.operit.data.preferences.glass.GlassRegion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.main.components.NavigationDrawerAppearance
import com.ai.assistance.operit.ui.theme.liquidGlass

/** 侧栏导航按钮共用磨砂绘制，启用时避免实色 Surface 遮住采样背景。 */
@Composable
internal fun SidebarButtonSurface(
    onClick: () -> Unit,
    modifier: Modifier,
    shape: CornerBasedShape,
    appearance: NavigationDrawerAppearance,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.liquidGlass(
            enabled = appearance.buttonLiquidGlassEnabled,
            shape = shape,
            containerColor = containerColor,
            enableLens = false,
            region = GlassRegion.SIDEBAR_BUTTONS,
        ),
        shape = shape,
        color = if (appearance.buttonLiquidGlassEnabled) Color.Transparent else containerColor,
        contentColor = contentColor,
        border = border,
        content = content,
    )
}
