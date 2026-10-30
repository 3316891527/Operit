package com.ai.assistance.operit.ui.features.conversation.chat.modules.history

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchAction
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchProgress
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.ui.features.conversation.chat.modules.history.folders.FolderChatHistoryPanel
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatHistoryDisplayMode

/** 保留聊天页现有面板入口，目录布局与行为由独立组件维护。 */
@Composable
fun ChatHistorySelector(
    modifier: Modifier = Modifier,
    onNewChat: (characterCardName: String?, characterGroupId: String?, folderId: String?) -> Unit,
    onSelectChat: (String) -> Unit, onDeleteChat: (String, Boolean) -> Unit,
    onUpdateChatTitle: (chatId: String, newTitle: String) -> Unit,
    onSummarizeChatTitle: (chatId: String) -> Unit,
    onUpdateChatBinding: (chatId: String, characterCardName: String?, characterGroupId: String?) -> Unit,
    chatHistories: List<ChatHistory>, currentId: String?,
    activeStreamingChatIds: Set<String> = emptySet(), lazyListState: LazyListState? = null,
    onBack: (() -> Unit)? = null, searchQuery: String, onSearchQueryChange: (String) -> Unit,
    historyDisplayMode: ChatHistoryDisplayMode, onDisplayModeChange: (ChatHistoryDisplayMode) -> Unit,
    autoSwitchCharacterCard: Boolean, onAutoSwitchCharacterCardChange: (Boolean) -> Unit,
    autoSwitchChatOnCharacterSelect: Boolean, onAutoSwitchChatOnCharacterSelectChange: (Boolean) -> Unit,
    onQuickScrollInteractionChange: (Boolean) -> Unit = {}, activePrompt: ActivePrompt,
    batchProgress: ChatBatchProgress?,
    onRunBatch: (ChatBatchAction, List<String>) -> Unit,
    deletionProgress: Map<String, Float> = emptyMap(),
) {
    FolderChatHistoryPanel(
        modifier, onNewChat, onSelectChat, onDeleteChat, onUpdateChatTitle, onSummarizeChatTitle, onUpdateChatBinding,
        chatHistories, currentId, activeStreamingChatIds, lazyListState, onBack, searchQuery, onSearchQueryChange,
        historyDisplayMode, onDisplayModeChange, autoSwitchCharacterCard, onAutoSwitchCharacterCardChange,
        autoSwitchChatOnCharacterSelect, onAutoSwitchChatOnCharacterSelectChange, onQuickScrollInteractionChange, activePrompt,
        batchProgress = batchProgress, onRunBatch = onRunBatch,
        deletionProgress = deletionProgress,
    )
}
