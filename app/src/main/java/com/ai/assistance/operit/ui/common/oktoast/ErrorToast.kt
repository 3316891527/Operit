package com.ai.assistance.operit.ui.common.oktoast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R

/** 将错误排入提示队列后消费一次源状态，避免重组或重新进入页面时重复展示。 */
@Composable
fun ErrorToast(errorMessage: String, onShown: () -> Unit) {
    val context = LocalContext.current
    val displayMessage = errorMessage.ifBlank { stringResource(R.string.unknown_error) }
    LaunchedEffect(errorMessage) {
        OKToast.error(context, displayMessage, OKToast.LENGTH_LONG).show()
        onShown()
    }
}
