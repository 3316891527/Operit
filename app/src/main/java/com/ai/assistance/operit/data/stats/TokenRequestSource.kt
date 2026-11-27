package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.model.FunctionType
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** 在实际发送调用时传入用途；缓存的服务实例不保存固定用途。 */
class TokenRequestSource(
    val functionType: FunctionType?,
    val executionModel: String? = null,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TokenRequestSource>
}
