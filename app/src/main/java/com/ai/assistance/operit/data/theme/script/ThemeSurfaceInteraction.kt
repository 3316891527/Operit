package com.ai.assistance.operit.data.theme.script

import kotlinx.serialization.Serializable

/** 原生交互状态之间的过渡，不驱动脚本重新求值。 */
@Serializable
data class ThemeStateTransition(val durationMillis: Int = 150)

/** 由现有原生点击部件绘制涟漪，未声明时沿用应用设置。 */
@Serializable
data class ThemeRipple(val enabled: Boolean = true, val color: String? = null)
