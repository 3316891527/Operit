package com.ai.assistance.operit.services.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 派发时绑定配置身份；校验或更新失败交给界面反馈，取消仍遵守父协程生命周期。 */
internal fun CoroutineScope.launchThinkingOptionUpdate(
    configId: String,
    optionId: String,
    save: suspend (String, String) -> Unit,
    onSaved: suspend () -> Unit,
    onFailure: suspend (Exception) -> Unit,
): Job = launch {
    try {
        save(configId, optionId)
        onSaved()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        onFailure(error)
    }
}
