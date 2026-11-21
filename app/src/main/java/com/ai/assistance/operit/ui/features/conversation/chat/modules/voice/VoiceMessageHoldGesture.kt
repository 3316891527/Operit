package com.ai.assistance.operit.ui.features.conversation.chat.modules.voice

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.voice.VoiceHoldAction
import com.ai.assistance.operit.core.chat.voice.VoiceHoldBounds
import com.ai.assistance.operit.core.chat.voice.VoiceHoldGesture
import com.ai.assistance.operit.core.chat.voice.VoiceHoldPhase
import com.ai.assistance.operit.util.logging.AppLogger

internal class VoiceRecordingGestureController {
    private val gesture = VoiceHoldGesture()
    var state by mutableStateOf(gesture.state)
        private set
    fun begin(x: Float, y: Float, distance: Float, exitSlop: Float = 0f): Long? =
        gesture.begin(x, y, distance, exitSlop).also { state = gesture.state }
    fun move(id: Long, x: Float, y: Float) { gesture.move(id, x, y); state = gesture.state }
    fun release(id: Long) { gesture.release(id); state = gesture.state }
    fun releaseTo(action: VoiceHoldAction) { gesture.release(state.id, action); state = gesture.state }
    fun cancel(id: Long = state.id) { gesture.cancel(id); state = gesture.state }
    fun reset() { gesture.reset(); state = gesture.state }
    fun takeReleasedAction(): VoiceHoldAction? = gesture.takeReleasedAction().also { state = gesture.state }
    fun setTarget(action: VoiceHoldAction, bounds: VoiceHoldBounds, id: Long) = gesture.setTarget(action, bounds, id)
}

internal val LocalVoiceRecordingGesture = staticCompositionLocalOf<VoiceRecordingGestureController?> { null }

private class VoiceGestureAnchor { var coordinates: LayoutCoordinates? = null }

/** 按钮和浮层使用屏幕坐标，键盘收起或浮层出现时不改变手指命中的热区。 */
internal fun LayoutCoordinates.voiceScreenPoint(view: View, position: Offset): Offset {
    val location = IntArray(2)
    view.getLocationOnScreen(location)
    val point = localToRoot(position)
    return Offset(location[0] + point.x, location[1] + point.y)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.voiceMessageHoldGesture(enabled: Boolean, onStart: () -> Unit): Modifier {
    val controller = LocalVoiceRecordingGesture.current
    if (!enabled || controller == null) return this
    val anchor = remember { VoiceGestureAnchor() }
    val view = LocalView.current
    val distance = with(LocalDensity.current) { 48.dp.toPx() }
    val exitSlop = with(LocalDensity.current) { 8.dp.toPx() }
    val latestStart by rememberUpdatedState(onStart)
    val label = stringResource(R.string.voice_message_hold_to_record)
    return this
        // 系统在按下时就判断侧滑归属，按钮起点也必须提前排除。
        .systemGestureExclusion()
        .onGloballyPositioned { anchor.coordinates = it }
        .semantics {
            onLongClick(label) {
                val coordinates = anchor.coordinates
                if (coordinates == null || !coordinates.isAttached) false
                else {
                    val center = Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                    val point = coordinates.voiceScreenPoint(view, center)
                    if (controller.begin(point.x, point.y, distance, exitSlop) == null) false
                    else {
                        val id = controller.state.id
                        latestStart()
                        if (controller.state.id == id && controller.state.phase == VoiceHoldPhase.HOLDING)
                            view.performVoiceHaptic(HapticFeedbackConstants.LONG_PRESS)
                        true
                    }
                }
            }
        }
        .pointerInput(controller, distance, exitSlop) {
            awaitEachGesture {
                // 先在 Main 阶段等待长按，避免提前消费按下事件导致长按检测自行取消。
                val down = awaitFirstDown(requireUnconsumed = false)
                var token: Long? = null
                var released = false
                try {
                    val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    if (held.id != down.id || currentEvent.changes.count { it.pressed } != 1) return@awaitEachGesture
                    val coordinates = anchor.coordinates ?: return@awaitEachGesture
                    if (!coordinates.isAttached) return@awaitEachGesture
                    val origin = coordinates.voiceScreenPoint(view, held.position)
                    val id = controller.begin(origin.x, origin.y, distance, exitSlop) ?: return@awaitEachGesture
                    token = id
                    held.consume()
                    latestStart()
                    if (controller.state.id == id && controller.state.phase == VoiceHoldPhase.HOLDING)
                        view.performVoiceHaptic(HapticFeedbackConstants.LONG_PRESS)
                    while (controller.state.phase == VoiceHoldPhase.HOLDING && controller.state.id == id) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                        val currentCoordinates = anchor.coordinates ?: break
                        if (!currentCoordinates.isAttached) break
                        val point = currentCoordinates.voiceScreenPoint(view, change.position)
                        val previousAction = controller.state.action
                        controller.move(id, point.x, point.y)
                        if (previousAction != controller.state.action) {
                            view.performVoiceHaptic(if (controller.state.action == VoiceHoldAction.SEND)
                                HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONTEXT_CLICK)
                        }
                        event.changes.forEach { it.consume() }
                        if (!change.pressed) {
                            if (change.previousPressed) {
                                controller.release(id)
                                released = true
                            }
                            break
                        }
                    }
                } finally {
                    // 协程取消、系统抢走指针和多指手势都不能伪装成松手发送。
                    if (!released) token?.let(controller::cancel)
                }
            }
        }
}

/** 使用操作触感区分热区切换；系统拒绝时记录结果，方便设备验收定位。 */
private fun View.performVoiceHaptic(type: Int) {
    if (!performHapticFeedback(type)) {
        AppLogger.d("VoiceRecordingHaptics", "系统未执行录音触感请求，type=$type，viewEnabled=$isHapticFeedbackEnabled")
    }
}
