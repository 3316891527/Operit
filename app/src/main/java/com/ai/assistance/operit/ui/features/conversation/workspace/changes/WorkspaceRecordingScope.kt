package com.ai.assistance.operit.ui.features.conversation.workspace.changes

/** 记录范围只来自对话明确绑定的目录，不能从命令位置或文件目标推导新工作区。 */
internal data class WorkspaceRecordingScope(val chatId: String, val path: String, val environment: String) {
    companion object {
        fun create(chatId: String?, workspace: String?, environment: String?): WorkspaceRecordingScope? {
            if (chatId.isNullOrBlank() || workspace.isNullOrBlank()) return null
            return WorkspaceRecordingScope(chatId, changePath(workspace), changeEnvironment(environment))
        }
    }

    fun matches(chatId: String, location: ChangeLocation): Boolean =
        this.chatId == chatId && path == location.path && environment == location.environment
}

/** 操作祖先目录时只采集其与工作区的交集，不扩大到整个目标目录。 */
internal fun boundMutationLocations(
    workspace: ChangeLocation, targets: List<ChangeLocation>, storage: String,
): List<ChangeLocation> = targets.mapNotNull { target ->
    when {
        relativeChangePath(workspace, target, storage) != null -> target
        relativeChangePath(target, workspace, storage) != null -> workspace
        else -> null
    }
}.distinct()
