package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

// 只统一宿主控件外观，显式 Panel 的圆角、背景和布局继续由卡片作者决定。
internal val CardControlShape = RoundedCornerShape(12.dp)
internal val CardContainerShape = RoundedCornerShape(16.dp)

/** 选项行保留整行点击和原有读屏角色，底色与描边跟随当前卡片的局部色板。 */
@Composable
internal fun Modifier.cardChoiceSurface(selected: Boolean): Modifier {
    val scheme = MaterialTheme.colorScheme
    return clip(CardControlShape)
        .background(if (selected) scheme.primary.copy(alpha = .10f) else scheme.surfaceContainerHigh)
        .border(
            BorderStroke(1.dp, if (selected) scheme.primary.copy(alpha = .55f) else scheme.outlineVariant.copy(alpha = .55f)),
            CardControlShape,
        )
}

/** 功能控件的边界保留至少 3:1 对比度，不能为淡化外框牺牲可辨识性。 */
@Composable
private fun cardControlOutline(): Color {
    val scheme = MaterialTheme.colorScheme
    val backdrop = LocalA2uiBackdrop.current
    val ground = scheme.surfaceContainerHigh.compositeOver(if (backdrop.isSpecified) backdrop else scheme.surface)
    return ensureReadableOn(scheme.outline, ground, 3.0)
}

/** 输入背景与选项行共用轻底色，焦点和错误继续使用 Material 原生状态颜色。 */
@Composable
internal fun cardFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = scheme.surfaceContainerHigh,
        unfocusedContainerColor = scheme.surfaceContainerHigh,
        disabledContainerColor = scheme.surfaceContainerHigh,
        errorContainerColor = scheme.surfaceContainerHigh,
        unfocusedBorderColor = cardControlOutline(),
        disabledBorderColor = scheme.outlineVariant.copy(alpha = .5f),
    )
}

/** 选择器与折叠区共用整行标题和状态箭头，长标签自然换行而不裁掉正文。 */
@Composable
internal fun CardSelectorButton(
    text: String,
    expanded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(160),
        label = "card-selector-arrow",
    )
    val state = stringResource(if (expanded) R.string.expanded else R.string.collapsed)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = state },
        shape = CardControlShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, cardControlOutline()),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Outlined.ExpandMore, contentDescription = null,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation })
    }
}
