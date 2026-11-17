package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorSettings

/** 路径始终与来源一起保存，Android、Linux 和授权目录中的根路径互不混淆。 */
internal data class WorkspaceLocation(val path: String, val environment: String = "android") {
    val key: String get() = "$environment\u0000$path"
}

internal data class WorkspaceBookmark(val name: String, val location: WorkspaceLocation)

internal data class WorkspaceBrowserSettings(
    val home: WorkspaceLocation? = null,
    val bookmarks: List<WorkspaceBookmark> = emptyList(),
    val hidden: Set<WorkspaceLocation> = emptySet(),
    val showDotFiles: Boolean = false,
    val showManuallyHidden: Boolean = false,
    val wordWrap: Boolean = true,
    val editor: EditorSettings = EditorSettings(),
)

internal fun workspaceEnvironment(environment: String?): String =
    if (environment.isNullOrBlank() || environment.equals("android", true)) "android" else if (environment.equals("linux", true)) "linux" else environment

/** 手工路径输入相对于当前目录解析，父级段不能越过所属来源的根。 */
internal fun resolveWorkspacePath(input: String, current: WorkspaceLocation): WorkspaceLocation {
    val value = input.trim()
    require(value.isNotEmpty() && '\u0000' !in value && '\n' !in value && '\r' !in value) { "Invalid path" }
    val expanded = when {
        current.environment == "linux" && value == "~" -> "/root"
        current.environment == "linux" && value.startsWith("~/") -> "/root/" + value.substring(2)
        value.startsWith('/') -> value
        else -> current.path.trimEnd('/') + "/" + value
    }
    val segments = ArrayDeque<String>()
    for (segment in expanded.split('/')) {
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeLast()
            else -> segments.addLast(segment)
        }
    }
    return WorkspaceLocation("/" + segments.joinToString("/"), current.environment)
}

internal fun workspaceParent(location: WorkspaceLocation): WorkspaceLocation? {
    val normalized = location.path.trimEnd('/').ifEmpty { "/" }
    if (normalized == "/") return null
    return location.copy(path = normalized.substringBeforeLast('/', "").ifEmpty { "/" })
}

internal fun workspaceChild(parent: WorkspaceLocation, name: String): WorkspaceLocation =
    parent.copy(path = parent.path.trimEnd('/') + "/" + name)

internal fun isWorkspaceEntryHidden(
    location: WorkspaceLocation,
    name: String,
    settings: WorkspaceBrowserSettings,
): Boolean = (!settings.showDotFiles && name.startsWith('.')) ||
    (!settings.showManuallyHidden && isWorkspacePathManuallyHidden(location, settings.hidden))

internal fun isWorkspacePathManuallyHidden(location: WorkspaceLocation, hidden: Set<WorkspaceLocation>): Boolean =
    hidden.any { rule -> rule.environment == location.environment &&
        (location.path == rule.path || location.path.startsWith(rule.path.trimEnd('/') + "/")) }
