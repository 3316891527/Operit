package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import android.content.Context
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.EditorSettings
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.toJson
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.editorSettingsFromJson
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

private val Context.workspaceBrowserStore by preferencesDataStore(name = "workspace_browser")

/** 仅保存浏览器偏好，不改变对话已绑定的工作区。 */
internal class WorkspaceBrowserPreferences(context: Context) {
    private val store = context.applicationContext.workspaceBrowserStore
    private val key = stringPreferencesKey("settings")
    private val hiddenFiles = WorkspaceHiddenPathsFiles(File(context.applicationContext.filesDir, "workspace_hidden_paths"))
    val settings: Flow<WorkspaceBrowserSettings> = store.data.combine(hiddenFiles.changes) { values, _ ->
        val current = values[key]?.let(::decodeWorkspaceBrowserSettings) ?: WorkspaceBrowserSettings()
        current.copy(hidden = hiddenFiles.read(current.hidden))
    }

    suspend fun hiddenPathsFile(environment: String?): File {
        val values = store.data.first()
        val current = values[key]?.let(::decodeWorkspaceBrowserSettings) ?: WorkspaceBrowserSettings()
        return hiddenFiles.editableFile(workspaceEnvironment(environment), current.hidden)
    }

    suspend fun update(transform: (WorkspaceBrowserSettings) -> WorkspaceBrowserSettings) {
        store.edit { values ->
            val current = values[key]?.let(::decodeWorkspaceBrowserSettings) ?: WorkspaceBrowserSettings()
            values[key] = encodeWorkspaceBrowserSettings(hiddenFiles.update(current, transform))
        }
    }
}

private fun WorkspaceLocation.toJson(): JSONObject = JSONObject()
    .put("path", path).put("environment", environment)

private fun JSONObject.location(): WorkspaceLocation =
    WorkspaceLocation(getString("path"), getString("environment"))

internal fun encodeWorkspaceBrowserSettings(settings: WorkspaceBrowserSettings): String = JSONObject().apply {
    settings.home?.let { put("home", it.toJson()) }
    put("bookmarks", JSONArray().apply {
        settings.bookmarks.forEach { bookmark ->
            put(JSONObject().put("name", bookmark.name).put("location", bookmark.location.toJson()))
        }
    })
    put("hidden", JSONArray().apply { settings.hidden.forEach { put(it.toJson()) } })
    put("showDotFiles", settings.showDotFiles)
    put("showManuallyHidden", settings.showManuallyHidden)
    put("wordWrap", settings.wordWrap)
    put("editor", settings.editor.toJson())
}.toString()

internal fun decodeWorkspaceBrowserSettings(value: String): WorkspaceBrowserSettings {
    val json = JSONObject(value)
    val bookmarks = json.getJSONArray("bookmarks")
    val hidden = json.getJSONArray("hidden")
    return WorkspaceBrowserSettings(
        home = if (json.has("home")) json.getJSONObject("home").location() else null,
        bookmarks = List(bookmarks.length()) { index ->
            val entry = bookmarks.getJSONObject(index)
            WorkspaceBookmark(entry.getString("name"), entry.getJSONObject("location").location())
        },
        hidden = (0 until hidden.length()).map { hidden.getJSONObject(it).location() }.toSet(),
        showDotFiles = json.getBoolean("showDotFiles"),
        showManuallyHidden = json.getBoolean("showManuallyHidden"),
        wordWrap = json.getBoolean("wordWrap"),
        // 旧偏好没有编辑器配置，首次升级使用新设置的声明默认值；原换行偏好继续保留。
        editor = if (json.has("editor")) editorSettingsFromJson(json.getJSONObject("editor")) else EditorSettings(),
    )
}
