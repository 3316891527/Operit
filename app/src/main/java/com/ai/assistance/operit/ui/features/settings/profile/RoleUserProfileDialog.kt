package com.ai.assistance.operit.ui.features.settings.profile

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.MemorySpace
import com.ai.assistance.operit.data.preferences.MemorySpaceProfileDocumentRepository
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.common.dialogs.ContentSizedTabbedDialog
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 编辑角色实际使用的记忆空间资料，保留同一份 Markdown 的实时预览。 */
@Composable
fun RoleUserProfileDialog(characterCard: CharacterCard, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { MemorySpaceProfileDocumentRepository.getInstance(context) }
    val preferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    var space by remember(characterCard.id) { mutableStateOf<MemorySpace?>(null) }
    var draft by remember(characterCard.id) { mutableStateOf("") }
    var savedMarkdown by remember(characterCard.id) { mutableStateOf("") }
    var showDiscard by remember(characterCard.id) { mutableStateOf(false) }
    var loading by remember(characterCard.id) { mutableStateOf(true) }
    var saving by remember(characterCard.id) { mutableStateOf(false) }
    var error by remember(characterCard.id) { mutableStateOf<String?>(null) }
    val exceedsLimit = draft.length > MemorySpaceProfileDocumentRepository.MAX_CONTENT_CHARS
    fun requestDismiss() {
        if (saving) return
        if (draft != savedMarkdown) showDiscard = true else onDismiss()
    }

    LaunchedEffect(characterCard.id) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                repository.initialize()
                preferences.ensureDefaultMemorySpace(context.getString(R.string.default_profile))
                val spaceId = if (
                    CharacterCardMemoryProfileBindingMode.normalize(characterCard.memoryProfileBindingMode) ==
                        CharacterCardMemoryProfileBindingMode.FIXED_PROFILE
                ) {
                    requireNotNull(characterCard.memoryProfileId?.takeIf { it.isNotBlank() }) {
                        context.getString(R.string.character_card_memory_profile_select)
                    }
                } else {
                    preferences.activeMemorySpaceIdFlow.first()
                }
                require(spaceId in preferences.memorySpaceListFlow.first()) { context.getString(R.string.memory_settings_space_missing) }
                preferences.getMemorySpaceFlow(spaceId).first() to repository.load(spaceId)
            }
            space = loaded.first
            draft = loaded.second
            savedMarkdown = loaded.second
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            AppLogger.e("RoleUserProfile", "读取角色用户资料失败", failure)
            error = failure.message ?: failure.javaClass.simpleName
        } finally {
            loading = false
        }
    }

    ContentSizedTabbedDialog(
        title = stringResource(R.string.role_user_profile_title, characterCard.name),
        tabTitles = listOf(stringResource(R.string.user_md_edit_tab), stringResource(R.string.user_md_preview_tab)),
        pagerState = pagerState,
        onDismiss = ::requestDismiss,
        actions = {
            TextButton(onClick = ::requestDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) }
            Button(
                enabled = !loading && !saving && space != null && !exceedsLimit,
                onClick = {
                    val target = requireNotNull(space)
                    val markdown = draft
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                require(target.id in preferences.memorySpaceListFlow.first()) { context.getString(R.string.memory_settings_space_missing) }
                                repository.save(target.id, markdown)
                            }
                            savedMarkdown = markdown
                            onDismiss()
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            AppLogger.e("RoleUserProfile", "保存角色用户资料失败", failure)
                            error = failure.message ?: failure.javaClass.simpleName
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
    ) { page ->
        if (loading) {
            CircularProgressIndicator()
        } else {
            space?.let { Text(stringResource(R.string.memory_settings_space, it.name), style = MaterialTheme.typography.bodySmall) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (space != null) {
                Text(stringResource(R.string.role_user_profile_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (page == 0) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving,
                        label = { Text(stringResource(R.string.role_user_profile)) },
                        placeholder = { Text(stringResource(R.string.user_md_editor_placeholder)) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        minLines = 10,
                        maxLines = 18,
                        isError = exceedsLimit,
                    )
                    Text(
                        text = "${draft.length} / ${MemorySpaceProfileDocumentRepository.MAX_CONTENT_CHARS}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (exceedsLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (draft.isBlank()) {
                    Text(stringResource(R.string.user_md_preview_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    MarkdownTextComposable(text = draft, textColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.user_md_unsaved_title)) },
            text = { Text(stringResource(R.string.user_md_unsaved_message)) },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; onDismiss() }) { Text(stringResource(R.string.user_md_discard)) }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
