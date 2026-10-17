package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 管理首次使用时的用户协议确认状态。 */
class AgreementPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("agreement_preferences", Context.MODE_PRIVATE)
    private val agreementAcceptedKey = "agreement_accepted"
    private val legacyAgreementVersionKey = "accepted_agreement_version"

    private val _agreementAcceptedFlow = MutableStateFlow(isAgreementAccepted())
    val agreementAcceptedFlow: StateFlow<Boolean> = _agreementAcceptedFlow.asStateFlow()

    fun isAgreementAccepted(): Boolean {
        if (prefs.contains(legacyAgreementVersionKey)) {
            // 已确认旧协议的覆盖安装用户保留完成状态，并清除旧的版本记录。
            val wasAccepted = prefs.getString(legacyAgreementVersionKey, null) != null
            prefs.edit()
                .putBoolean(agreementAcceptedKey, wasAccepted)
                .remove(legacyAgreementVersionKey)
                .apply()
        }
        return prefs.getBoolean(agreementAcceptedKey, false)
    }

    fun acceptCurrentAgreement() {
        prefs.edit().putBoolean(agreementAcceptedKey, true).apply()
        _agreementAcceptedFlow.value = true
    }
}
