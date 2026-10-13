package com.ai.assistance.operit.ui.features.chat.sidebar

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.features.chat.components.CharacterSelectorPanel
import com.ai.assistance.operit.ui.features.chat.components.CharacterSelectorTarget
import com.ai.assistance.operit.ui.features.chat.folders.FolderChatHistoryPanel
import com.ai.assistance.operit.ui.main.components.NavigationDrawerAppearance
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.screens.GestureStateHolder
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import kotlinx.coroutines.flow.flowOf

/** 角色和历史优先，插件使用可折叠的大按钮区域，底部保持固定。 */
@Composable
fun ChatSidebarContent(
    modifier: Modifier = Modifier,
    brandName: String,
    appearance: NavigationDrawerAppearance,
    pluginEntries: List<NavigationEntrySpec>,
    selectedItem: NavItem?,
    selectedRouteId: String,
    permissionBadgeText: String,
    onNavItemClick: (NavItem) -> Unit,
    onNavigationEntryClick: (NavigationEntrySpec) -> Unit,
    onOpenCharacterSettings: () -> Unit,
    onCloseSidebar: () -> Unit,
    onRunChatAction: (() -> Unit) -> Unit,
    onSelectCharacter: (CharacterSelectorTarget) -> Unit,
    bottomContent: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = LocalMainChatViewModel.current
    val state = LocalMainChatSidebarState.current
    val currentChatId by viewModel.currentChatId.collectAsState()
    val streamingIds by viewModel.activeStreamingChatIds.collectAsState()
    val searchQuery by viewModel.chatHistorySearchQuery.collectAsState()
    val userPreferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val cardId = state.activeCharacterCard?.id
    val groupId = state.activeCharacterGroup?.id
    val cardAvatar by remember(cardId) {
        if (cardId == null) flowOf(null) else userPreferences.getAiAvatarForCharacterCardFlow(cardId)
    }.collectAsState(initial = null)
    val groupAvatar by remember(groupId) {
        if (groupId == null) flowOf(null) else userPreferences.getAiAvatarForCharacterGroupFlow(groupId)
    }.collectAsState(initial = null)
    val memberCardId = state.activeCharacterGroup?.members?.minByOrNull { it.orderIndex }?.characterCardId
    val memberAvatar by remember(memberCardId) {
        if (memberCardId == null) flowOf(null) else userPreferences.getAiAvatarForCharacterCardFlow(memberCardId)
    }.collectAsState(initial = null)
    val avatar = when (state.activePrompt) {
        is ActivePrompt.CharacterCard -> cardAvatar
        is ActivePrompt.CharacterGroup -> groupAvatar ?: memberAvatar
    }
    val characterName = when (state.activePrompt) {
        is ActivePrompt.CharacterCard -> state.activeCharacterCard?.name.orEmpty()
        is ActivePrompt.CharacterGroup -> state.activeCharacterGroup?.name.orEmpty()
    }
    val textColor = getTextColorForBackground(appearance.containerColor)
    var showCharacterSelector by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { GestureStateHolder.isChatScreenGestureConsumed = false }
    }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(brandName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = appearance.titleColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onCloseSidebar, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_content_collapse_sidebar),
                    tint = appearance.itemColor, modifier = Modifier.size(18.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SidebarNavigationItem(Modifier.weight(1f), Icons.Default.AdminPanelSettings,
                    stringResource(R.string.sidebar_permission_short), appearance,
                    selected = selectedItem == NavItem.ShizukuCommands, detail = permissionBadgeText,
                    onClick = { onNavItemClick(NavItem.ShizukuCommands) })
                SidebarNavigationItem(Modifier.weight(1f), NavItem.Workflow.icon,
                    stringResource(NavItem.Workflow.titleResId), appearance,
                    selected = selectedItem == NavItem.Workflow,
                    onClick = { onNavItemClick(NavItem.Workflow) })
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .clickable { showCharacterSelector = true }.padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (avatar.isNullOrBlank()) {
                    Surface(Modifier.size(28.dp), shape = CircleShape, color = appearance.buttonContainerColor) {
                        Icon(Icons.Default.Person, null, tint = textColor, modifier = Modifier.padding(5.dp))
                    }
                } else {
                    Image(rememberAsyncImagePainter(avatar), contentDescription = null,
                        modifier = Modifier.size(28.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                }
                Text(characterName, Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.KeyboardArrowDown, null, tint = appearance.itemColor, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onOpenCharacterSettings, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Settings, stringResource(R.string.sidebar_character_settings),
                    tint = appearance.itemColor, modifier = Modifier.size(18.dp))
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pluginListMaxHeight = maxHeight * 0.3f
            Column(Modifier.fillMaxSize()) {
                FolderChatHistoryPanel(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onNewChat = { cardName, group, folder -> onRunChatAction {
                        viewModel.showChatPage()
                        viewModel.createNewChat(cardName, group, folder, inheritGroupFromCurrent = false)
                    } },
                    onSelectChat = { id -> onRunChatAction { viewModel.showChatPage(); viewModel.switchChat(id) } },
                    onDeleteChat = viewModel::deleteChatHistory,
                    onUpdateChatTitle = viewModel::updateChatTitle,
                    onUpdateChatBinding = viewModel::updateChatCharacterBinding,
                    chatHistories = state.displayedChatHistories,
                    currentId = currentChatId,
                    activeStreamingChatIds = streamingIds,
                    lazyListState = null,
                    onBack = null,
                    searchQuery = searchQuery,
                    onSearchQueryChange = viewModel::onChatHistorySearchQueryChange,
                    historyDisplayMode = state.historyDisplayMode,
                    onDisplayModeChange = state.onDisplayModeChange,
                    autoSwitchCharacterCard = state.autoSwitchCharacterCard,
                    onAutoSwitchCharacterCardChange = state.onAutoSwitchCharacterCardChange,
                    autoSwitchChatOnCharacterSelect = state.autoSwitchChatOnCharacterSelect,
                    onAutoSwitchChatOnCharacterSelectChange = state.onAutoSwitchChatOnCharacterSelectChange,
                    onQuickScrollInteractionChange = { GestureStateHolder.isChatScreenGestureConsumed = it },
                    activePrompt = state.activePrompt,
                    embeddedInSidebar = true,
                    contentColor = textColor,
                    selectedContainerColor = appearance.selectedContainerColor,
                    selectedContentColor = getTextColorForBackground(appearance.selectedContainerColor),
                )
                SidebarPluginSection(
                    entries = pluginEntries,
                    selectedRouteId = selectedRouteId,
                    appearance = appearance,
                    maxListHeight = pluginListMaxHeight,
                    onEntryClick = onNavigationEntryClick,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        bottomContent()
    }
    if (showCharacterSelector) {
        Dialog(onDismissRequest = { showCharacterSelector = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            CharacterSelectorPanel(
                isVisible = true,
                onDismiss = { showCharacterSelector = false },
                onSelectCharacter = onSelectCharacter,
                onOpenCharacterSettings = { showCharacterSelector = false; onOpenCharacterSettings() },
            )
        }
    }
}

@Composable
private fun SidebarNavigationItem(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    appearance: NavigationDrawerAppearance,
    selected: Boolean = false,
    detail: String? = null,
    onClick: () -> Unit,
) {
    val color = if (selected) appearance.selectedContentColor else appearance.itemColor
    SidebarButtonSurface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
        appearance = appearance,
        containerColor = if (selected) appearance.selectedContainerColor
            else if (appearance.buttonLiquidGlassEnabled) appearance.buttonContainerColor else Color.Transparent,
        contentColor = color,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
