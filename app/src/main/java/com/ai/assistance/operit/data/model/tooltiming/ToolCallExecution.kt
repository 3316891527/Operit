package com.ai.assistance.operit.data.model.tooltiming

import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 应用侧执行记录，不参与消息正文、提示词或工具结果协议。 */
@Serializable
data class ToolCallExecution(
    val callIndex: Int,
    val clockId: String,
    val startedMs: Long? = null,
    val durationMs: Long? = null,
    val endedMs: Long? = null,
    val stopped: Boolean = false,
)

/** 一轮回答独立持有，工具事件先于正文消费到达时也不会丢失。 */
class ToolTimingState {
    private val lock = Any()
    private var stopped = false
    private val mutable = MutableStateFlow<List<ToolCallExecution>>(emptyList())
    val records: StateFlow<List<ToolCallExecution>> = mutable.asStateFlow()

    fun update(record: ToolCallExecution) = synchronized(lock) {
        if (stopped) return@synchronized
        mutable.update { records ->
            (records.filterNot { it.callIndex == record.callIndex } + record).sortedBy { it.callIndex }
        }
    }

    /** 请求已停止时固定未完成区间，并拒绝迟到的执行事件覆盖终态。 */
    fun stopActive(now: Long) = synchronized(lock) {
        stopped = true
        mutable.update { records ->
            records.map { record ->
                if (record.durationMs != null || record.stopped) record
                else record.copy(endedMs = now, stopped = true)
            }
        }
    }
}
