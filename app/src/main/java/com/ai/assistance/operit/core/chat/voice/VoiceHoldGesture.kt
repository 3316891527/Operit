package com.ai.assistance.operit.core.chat.voice

internal enum class VoiceHoldAction { SEND, CANCEL, TRANSCRIBE }
internal enum class VoiceHoldPhase { IDLE, HOLDING, RELEASED, HANDLED, CANCELLED }

internal data class VoiceHoldBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float, slop: Float = 0f): Boolean =
        x >= left - slop && x <= right + slop && y >= top - slop && y <= bottom + slop
}

internal data class VoiceHoldState(
    val id: Long = 0,
    val phase: VoiceHoldPhase = VoiceHoldPhase.IDLE,
    val action: VoiceHoldAction = VoiceHoldAction.SEND,
)

/** 只有真实松手可以产生提交动作；系统取消及旧指针回调不能提交新录音。 */
internal class VoiceHoldGesture {
    var state = VoiceHoldState()
        private set
    private var startX = 0f
    private var startY = 0f
    private var activationDistance = 0f
    private var targetExitSlop = 0f
    private val targets = mutableMapOf<VoiceHoldAction, VoiceHoldBounds>()

    fun begin(x: Float, y: Float, activationDistance: Float, targetExitSlop: Float = 0f): Long? {
        require(activationDistance >= 0f && activationDistance.isFinite())
        require(targetExitSlop >= 0f && targetExitSlop.isFinite())
        if (state.phase != VoiceHoldPhase.IDLE) return null
        startX = x
        startY = y
        this.activationDistance = activationDistance
        this.targetExitSlop = targetExitSlop
        targets.clear()
        state = VoiceHoldState(state.id + 1, VoiceHoldPhase.HOLDING)
        return state.id
    }

    fun setTarget(action: VoiceHoldAction, bounds: VoiceHoldBounds, id: Long = state.id) {
        require(action != VoiceHoldAction.SEND)
        if (id != state.id || state.phase != VoiceHoldPhase.HOLDING) return
        targets[action] = bounds
    }

    fun move(id: Long, x: Float, y: Float) {
        if (id != state.id || state.phase != VoiceHoldPhase.HOLDING) return
        val dx = x - startX
        val dy = y - startY
        val moved = dx * dx + dy * dy >= activationDistance * activationDistance
        val target = if (moved) targets.entries.firstOrNull { it.value.contains(x, y) }?.key else null
        val previousTarget = targets[state.action]
        // 进入其他热区立即切换；退出当前热区保留少量余量，避免边缘抖动连续触发反馈。
        val action = when {
            !moved -> VoiceHoldAction.SEND
            target != null -> target
            previousTarget != null && previousTarget.contains(x, y, targetExitSlop) -> state.action
            else -> VoiceHoldAction.SEND
        }
        state = state.copy(action = action)
    }

    fun release(id: Long, action: VoiceHoldAction = state.action) {
        if (id == state.id && state.phase == VoiceHoldPhase.HOLDING) {
            // 热区尚未完成布局时只取消，不能把一次快速滑动误认为发送。
            val selected = if (targets.size == 2) action else VoiceHoldAction.CANCEL
            state = state.copy(phase = VoiceHoldPhase.RELEASED, action = selected)
        }
    }

    fun cancel(id: Long = state.id) {
        if (id == state.id && state.phase != VoiceHoldPhase.IDLE) {
            state = state.copy(phase = VoiceHoldPhase.CANCELLED, action = VoiceHoldAction.CANCEL)
        }
    }

    /** 文件准备好后才取出松手动作，同一次录音最多交接一次。 */
    fun takeReleasedAction(): VoiceHoldAction? {
        if (state.phase != VoiceHoldPhase.RELEASED) return null
        val action = state.action
        state = state.copy(phase = VoiceHoldPhase.HANDLED)
        return action
    }

    fun reset() {
        targets.clear()
        state = VoiceHoldState(id = state.id)
    }
}
