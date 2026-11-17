package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ai.assistance.operit.data.resources.OfflineResources

/** 所有文件入口共用安装完成标记，恢复前台后重新检查，不创建终端会话。 */
@Composable
internal fun rememberWorkspaceLinuxInstalled(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return remember(context, revision) { OfflineResources.ubuntuInstalled(context) }
}
