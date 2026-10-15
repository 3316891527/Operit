package com.ai.assistance.operit.ui.features.chat.webview.computer

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import com.ai.assistance.operit.ui.features.terminal.EmbeddedTerminalPage
import com.ai.assistance.operit.ui.features.terminal.EmbeddedTerminalScreen

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ComputerScreen(page: EmbeddedTerminalPage, onPageChange: (EmbeddedTerminalPage) -> Unit) {
    EmbeddedTerminalScreen(page = page, onPageChange = onPageChange)
}
