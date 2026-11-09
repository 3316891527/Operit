package com.ai.assistance.operit.ui.theme.script

import com.ai.assistance.operit.data.theme.script.themeResourceLimitFailure

/** The persistent banner keeps the English reason and only the attempted total / limit. */
internal fun themeResourceErrorMessage(failure: Throwable): String {
    val budgetFailure = failure.themeResourceLimitFailure()
    if (budgetFailure != null) return budgetFailure.message.orEmpty()
    return failure.message.orEmpty()
}
