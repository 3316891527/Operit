package com.ai.assistance.operit.ui.features.conversation.workspace.hub

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.*

/** 子工作区向现有导航行提供文件标签及操作，文件列表与编辑器不再各占一行导航。 */
internal class WorkspaceHeaderHost {
    private var owner: Any? = null
    var tabs by mutableStateOf<(@Composable RowScope.() -> Unit)?>(null)
        private set
    var actions by mutableStateOf<(@Composable RowScope.() -> Unit)?>(null)
        private set
    var fileSelected by mutableStateOf(false)
        private set
    var showFiles: (() -> Unit)? = null
        private set
    fun register(key: Any, tabs: @Composable RowScope.() -> Unit, actions: @Composable RowScope.() -> Unit, selected: Boolean, showFiles: () -> Unit) {
        owner = key; this.tabs = tabs; this.actions = actions; fileSelected = selected; this.showFiles = showFiles
    }
    fun unregister(key: Any) {
        if (owner === key) { owner = null; tabs = null; actions = null; fileSelected = false; showFiles = null }
    }
}

internal val LocalWorkspaceHeader = staticCompositionLocalOf<WorkspaceHeaderHost?> { null }
