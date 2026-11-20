package com.ai.assistance.operit.ui.features.conversation.chat.modules.voice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.audio.VoiceMessageRecorder
import com.ai.assistance.operit.core.chat.voice.VoiceHoldAction
import com.ai.assistance.operit.core.chat.voice.VoiceHoldBounds
import com.ai.assistance.operit.core.chat.voice.VoiceHoldPhase
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

@Composable
internal fun VoiceRecordingOverlayContent(
    state: VoiceMessageState,
    gesture: VoiceRecordingGestureController,
    sending: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)?,
    retryLabel: String,
) {
    val holding = gesture.state.phase == VoiceHoldPhase.HOLDING
    val action = gesture.state.action
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val backdrop = if (dark) Color(0xFF171717) else colors.background
    val foreground = colors.onBackground
    val arcColor = if (dark) Color(0xFF202020) else colors.surfaceContainerHigh
    val pressedColor = if (dark) Color(0xFF2A2A2A) else colors.surfaceContainerHighest
    val centerColor by animateColorAsState(
        if (holding && action == VoiceHoldAction.SEND) pressedColor else arcColor,
        animationSpec = tween(160), label = "voice-center-color",
    )
    val bubbleColor by animateColorAsState(
        if (action == VoiceHoldAction.CANCEL) colors.error else colors.primary,
        animationSpec = tween(160), label = "voice-recording-color",
    )
    val bubbleContent by animateColorAsState(
        if (action == VoiceHoldAction.CANCEL) colors.onError else colors.onPrimary,
        animationSpec = tween(160), label = "voice-recording-content-color",
    )
    val busy = sending || state.phase == VoiceMessagePhase.TRANSCRIBING || state.phase == VoiceMessagePhase.EDITING
    val preparing = state.phase == VoiceMessagePhase.PREPARING
    val finishing = !holding && state.phase == VoiceMessagePhase.RECORDING
    val showTargets = holding && errorMessage == null && !busy
    val density = LocalDensity.current
    val safeTop = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() }
    val safeBottom = with(density) { WindowInsets.safeDrawing.getBottom(this).toDp() }
    val safeLeft = with(density) { WindowInsets.safeDrawing.getLeft(this, LayoutDirection.Ltr).toDp() }
    val safeRight = with(density) { WindowInsets.safeDrawing.getRight(this, LayoutDirection.Ltr).toDp() }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to backdrop.copy(alpha = if (dark) 0.08f else 0.04f),
                0.32f to backdrop.copy(alpha = 0.68f),
                0.55f to backdrop,
                1f to backdrop,
            ),
        ).systemGestureExclusion().pointerInput(gesture) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // 原长按仍由输入按钮处理；新增触点只会取消，不会代替原手指发送。
                if (gesture.state.phase == VoiceHoldPhase.HOLDING) {
                    down.consume()
                    gesture.cancel()
                }
            }
        },
    ) {
        val usableHeight = (maxHeight - safeTop - safeBottom).coerceAtLeast(0.dp)
        val compact = usableHeight < 460.dp
        val arcHeight = if (compact) (usableHeight * 0.24f).coerceIn(72.dp, 104.dp)
            else (usableHeight * 0.2f).coerceIn(112.dp, 164.dp)
        val arcBottom = arcHeight + safeBottom
        val targetHeight = (if (compact) 56.dp else 68.dp) + 20.dp * (density.fontScale - 1f).coerceAtLeast(0f)
        val targetBottom = arcBottom - if (compact) 4.dp else 12.dp
        val feedbackBottom = arcBottom + targetHeight + if (compact) 12.dp else 32.dp
        val feedbackSpace = (maxHeight - safeTop - feedbackBottom - 12.dp).coerceAtLeast(0.dp)
        val edgePadding = (maxWidth * 0.06f).coerceIn(16.dp, 48.dp)
        val targetWidth = (maxWidth * 0.3f).coerceAtMost(156.dp)
        val bubbleFraction by animateFloatAsState(
            when (action) {
                VoiceHoldAction.SEND -> 0.66f
                VoiceHoldAction.CANCEL -> 0.54f
                VoiceHoldAction.TRANSCRIBE -> 0.84f
            },
            animationSpec = tween(180), label = "voice-recording-width",
        )
        val bubbleOffset by animateDpAsState(
            when (action) {
                VoiceHoldAction.SEND -> 0.dp
                VoiceHoldAction.CANCEL -> maxWidth * (-0.1f * direction)
                VoiceHoldAction.TRANSCRIBE -> maxWidth * (0.06f * direction)
            },
            animationSpec = tween(180), label = "voice-recording-offset",
        )
        // 超宽椭圆只露出上部浅弧，画到窗口底边；安全区只约束文字和按钮。
        Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(arcBottom).clipToBounds()) {
            drawOval(arcColor, topLeft = Offset(-size.width * 0.275f, 0f),
                size = Size(size.width * 1.55f, size.height * 2f))
            drawOval(centerColor, topLeft = Offset(size.width * 0.16f, arcHeight.toPx() * 0.22f),
                size = Size(size.width * 0.68f, size.height * 2f))
        }
        Column(
            Modifier.align(Alignment.BottomCenter)
                .absolutePadding(left = safeLeft + edgePadding, right = safeRight + edgePadding, bottom = feedbackBottom)
                .fillMaxWidth().heightIn(max = feedbackSpace).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
        ) {
            if (errorMessage != null) {
                Text(errorMessage, color = colors.error, style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center)
                if (onRetry != null) Button(onClick = onRetry) { Text(retryLabel) }
            } else if (busy || preparing || finishing) {
                CircularProgressIndicator(Modifier.size(if (compact) 28.dp else 36.dp))
                Text(
                    stringResource(when {
                        sending -> R.string.voice_message_sending
                        state.phase == VoiceMessagePhase.TRANSCRIBING || state.phase == VoiceMessagePhase.EDITING -> R.string.voice_message_transcribing
                        finishing -> R.string.voice_message_finishing
                        else -> R.string.voice_message_preparing
                    }),
                    color = foreground, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                )
            } else {
                Box(Modifier.absoluteOffset(x = bubbleOffset), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(bubbleFraction)
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.voice_message_send)) {
                                gesture.releaseTo(VoiceHoldAction.SEND)
                            },
                        color = bubbleColor, shape = RoundedCornerShape(if (compact) 22.dp else 28.dp),
                    ) {
                        Column(
                            Modifier.padding(horizontal = if (compact) 18.dp else 24.dp,
                                vertical = if (compact) 10.dp else 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
                        ) {
                            Text(voiceDurationLabel(state.elapsedMs), color = bubbleContent,
                                style = MaterialTheme.typography.labelLarge)
                            VoiceWaveform(state.levels, Modifier.fillMaxWidth().height(if (compact) 20.dp else 28.dp),
                                live = state.phase == VoiceMessagePhase.RECORDING, color = bubbleContent)
                        }
                    }
                    Canvas(Modifier.align(Alignment.BottomCenter).offset(y = 8.dp).size(20.dp, 12.dp)) {
                        drawPath(Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        }, bubbleColor)
                    }
                }
                Text(
                    stringResource(when (action) {
                        VoiceHoldAction.SEND -> R.string.voice_message_release_send
                        VoiceHoldAction.CANCEL -> R.string.voice_message_release_cancel
                        VoiceHoldAction.TRANSCRIBE -> R.string.voice_message_release_transcribe
                    }),
                    color = foreground, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                )
                if (holding && state.phase == VoiceMessagePhase.READY) {
                    Text(stringResource(R.string.voice_message_hold_limit, VoiceMessageRecorder.MAX_DURATION_MS / 1000),
                        color = foreground.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center)
                }
            }
        }
        if (showTargets) {
            Row(
                Modifier.align(Alignment.BottomCenter)
                    .absolutePadding(left = safeLeft + edgePadding, right = safeRight + edgePadding, bottom = targetBottom)
                    .widthIn(max = 560.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                VoiceRecordingTarget(gesture, VoiceHoldAction.CANCEL, Icons.Default.Close,
                    stringResource(R.string.cancel), foreground, dark, -12f * direction,
                    Modifier.width(targetWidth).height(targetHeight))
                VoiceRecordingTarget(gesture, VoiceHoldAction.TRANSCRIBE, Icons.Default.Description,
                    stringResource(R.string.voice_message_transcribe), foreground, dark, 12f * direction,
                    Modifier.width(targetWidth).height(targetHeight))
            }
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .absolutePadding(left = safeLeft + 24.dp, right = safeRight + 24.dp, bottom = safeBottom + if (compact) 12.dp else 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
            ) {
                Icon(Icons.Default.Mic, null, Modifier.size(if (compact) 26.dp else 32.dp),
                    tint = foreground.copy(alpha = if (action == VoiceHoldAction.SEND) 0.9f else 0.45f))
                Text(stringResource(R.string.voice_message_hold_slide_hint), color = foreground.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        } else {
            TextButton(onClick = onDismiss, enabled = !sending,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = safeBottom + 24.dp)) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun VoiceRecordingTarget(
    gesture: VoiceRecordingGestureController,
    action: VoiceHoldAction,
    icon: ImageVector,
    label: String,
    foreground: Color,
    dark: Boolean,
    rotation: Float,
    modifier: Modifier,
) {
    val view = LocalView.current
    val recordingId = gesture.state.id
    val selected = gesture.state.action == action
    val colors = MaterialTheme.colorScheme
    val accent = if (action == VoiceHoldAction.CANCEL) colors.error else colors.primary
    val color by animateColorAsState(
        if (selected) accent else if (dark) Color(0xFF282828) else colors.surfaceContainer,
        animationSpec = tween(160), label = "voice-target-color",
    )
    val contentColor by animateColorAsState(
        if (selected) {
            if (action == VoiceHoldAction.CANCEL) colors.onError else colors.onPrimary
        } else foreground.copy(alpha = 0.85f),
        animationSpec = tween(160), label = "voice-target-content-color",
    )
    Surface(
        modifier = modifier.graphicsLayer { rotationZ = rotation }
            .onGloballyPositioned { coordinates ->
                // 旋转后对角两点不足以描述边界，四角都转换到原按钮使用的屏幕坐标。
                val width = coordinates.size.width.toFloat()
                val height = coordinates.size.height.toFloat()
                val corners = listOf(Offset.Zero, Offset(width, 0f), Offset(width, height), Offset(0f, height))
                    .map { coordinates.voiceScreenPoint(view, it) }
                gesture.setTarget(action, VoiceHoldBounds(corners.minOf { it.x }, corners.minOf { it.y },
                    corners.maxOf { it.x }, corners.maxOf { it.y }), recordingId)
            }
            .clickable(role = Role.Button) { gesture.releaseTo(action) },
        shape = CircleShape, color = color,
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.5f) else foreground.copy(alpha = 0.06f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            Icon(icon, null, Modifier.size(22.dp), tint = contentColor)
            Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
