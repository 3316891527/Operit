package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.theme.script.ThemeScriptContract
import com.ai.assistance.operit.data.theme.script.hasBackgroundPaint

/** 全局及每个页面背景共用独立模糊入口，继承背景时不创建空白覆盖层。 */
@Composable
internal fun StudioBackgroundBlurProperties(state: ThemeStudioState) {
    if (Build.VERSION.SDK_INT < 31) return
    val surface = state.surface()
    val independent = surface.hasBackgroundPaint()
    StudioSection(stringResource(R.string.theme_background_blur)) {
        StudioSlider(
            state,
            stringResource(R.string.theme_background_blur_radius),
            surface.backgroundBlur,
            0f..ThemeScriptContract.MAX_BACKGROUND_BLUR_DP,
            "dp",
            enabled = independent,
        ) { value -> state.updateSurface { it.copy(backgroundBlur = value) } }
        StudioHint(stringResource(if (independent) R.string.studio_background_blur_hint
            else R.string.studio_background_blur_inherit_hint))
    }
}
