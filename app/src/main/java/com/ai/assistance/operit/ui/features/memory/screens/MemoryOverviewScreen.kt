package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MenuDefaults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.MemorySpace
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.MemorySpaceProfileDocumentRepository
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun MemoryOverviewScreen(onOpenSpace: (String) -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val cardManager = remember(context) { CharacterCardManager.getInstance(context) }
    val isCurrentScreen = LocalIsCurrentScreen.current

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val spaceIds by preferences.memorySpaceListFlow.collectAsState(initial = emptyList())
    val activeId by preferences.activeMemorySpaceIdFlow.collectAsState(initial = "default")
    val cardIds by cardManager.characterCardListFlow.collectAsState(initial = emptyList())
    var ready by remember { mutableStateOf(false) }
    var initializationError by remember { mutableStateOf<String?>(null) }
    var initializeAttempt by remember { mutableStateOf(0) }
    var spaces by remember { mutableStateOf<List<MemorySpace>>(emptyList()) }
    var cards by remember { mutableStateOf<List<CharacterCard>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var chooseGlobal by remember { mutableStateOf(false) }
    var pendingGlobalId by remember { mutableStateOf(activeId) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<MemorySpace?>(null) }
    var deleting by remember { mutableStateOf<MemorySpace?>(null) }
    var editingCard by remember { mutableStateOf<CharacterCard?>(null) }
    var pendingBindingId by remember { mutableStateOf<String?>(null) }
    var draftName by remember { mutableStateOf("") }

    LaunchedEffect(initializeAttempt) {
        try {
            MemorySpaceProfileDocumentRepository.getInstance(context).initialize()
            preferences.ensureDefaultMemorySpace(context.getString(R.string.default_profile))
            spaces = preferences.memorySpaceListFlow.first().map { preferences.getMemorySpaceFlow(it).first() }
            cards = cardManager.getAllCharacterCards().sortedBy { it.name }
            ready = true
        } catch (error: Exception) {
            initializationError = error.toString()
        }
    }
    LaunchedEffect(spaceIds, ready, isCurrentScreen) {
        if (ready && isCurrentScreen) {
            try {
                spaces = spaceIds.map { preferences.getMemorySpaceFlow(it).first() }
            } catch (error: Exception) {
                snackbar.showSnackbar(error.toString())
            }
        }
    }
    LaunchedEffect(cardIds, ready, isCurrentScreen) {
        if (ready && isCurrentScreen) {
            try {
                cards = cardManager.getAllCharacterCards().sortedBy { it.name }
            } catch (error: Exception) {
                snackbar.showSnackbar(error.toString())
            }
        }
    }

    fun perform(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                action()
                spaces = preferences.memorySpaceListFlow.first().map { preferences.getMemorySpaceFlow(it).first() }
                cards = cardManager.getAllCharacterCards().sortedBy { it.name }
            } catch (error: Exception) {
                snackbar.showSnackbar(error.toString())
            } finally {
                busy = false
            }
        }
    }

    val names = spaces.associate { it.id to it.name }
    val activeName = names[activeId].orEmpty()
    CustomScaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (!ready) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val error = initializationError
                if (error == null) {
                    CircularProgressIndicator()
                } else {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { initializationError = null; initializeAttempt++ }) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                item {
                    MemorySectionTitle(stringResource(R.string.memory_overview_global))
                    MemoryOverviewRow(
                        title = stringResource(R.string.memory_overview_current_global),
                        subtitle = activeName,
                        onClick = {
                            pendingGlobalId = activeId
                            chooseGlobal = true
                        },
                        trailing = stringResource(R.string.memory_overview_change)
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.memory_overview_spaces),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedButton(
                            onClick = { draftName = ""; creating = true },
                            enabled = !busy
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.create))
                        }
                    }
                }
                items(spaces, key = { it.id }) { space ->
                    val fixedCount = cards.count {
                        CharacterCardMemoryProfileBindingMode.normalize(it.memoryProfileBindingMode) ==
                            CharacterCardMemoryProfileBindingMode.FIXED_PROFILE && it.memoryProfileId == space.id
                    }
                    val followingCount = if (space.id == activeId) cards.count {
                        CharacterCardMemoryProfileBindingMode.normalize(it.memoryProfileBindingMode) ==
                            CharacterCardMemoryProfileBindingMode.FOLLOW_GLOBAL
                    } else 0
                    MemorySpaceRow(
                        space = space,
                        subtitle = stringResource(
                            if (space.id == activeId) R.string.memory_overview_active_count
                            else R.string.memory_overview_bound_count,
                            fixedCount + followingCount
                        ),
                        onOpen = { onOpenSpace(space.id) },
                        onRename = { renaming = space; draftName = space.name },
                        onDelete = { deleting = space },
                        enabled = !busy
                    )
                    HorizontalDivider()
                }
                item {
                    MemorySectionTitle(stringResource(R.string.memory_overview_roles))
                }
                items(cards, key = { it.id }) { card ->
                    val fixedName = card.memoryProfileId?.let { names[it] }
                    val fixed = CharacterCardMemoryProfileBindingMode.normalize(card.memoryProfileBindingMode) ==
                        CharacterCardMemoryProfileBindingMode.FIXED_PROFILE
                    val description = if (fixed && fixedName == null) {
                        stringResource(R.string.memory_overview_missing_space)
                    } else if (fixedName != null && fixed) {
                        stringResource(R.string.memory_overview_fixed, fixedName)
                    } else {
                        stringResource(R.string.memory_overview_follow, activeName)
                    }
                    MemoryOverviewRow(
                        title = card.name,
                        subtitle = description,
                        onClick = {
                            editingCard = card
                            pendingBindingId = if (fixedName != null && fixed) card.memoryProfileId else null
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (chooseGlobal) {
        AlertDialog(
            onDismissRequest = { chooseGlobal = false },
            title = { Text(stringResource(R.string.memory_overview_choose_global)) },
            text = {
                Column(modifier = Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    spaces.forEach { space ->
                        MemoryChoice(space.name, space.id == pendingGlobalId) { pendingGlobalId = space.id }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val id = pendingGlobalId
                    chooseGlobal = false
                    perform { preferences.setActiveMemorySpace(id) }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { chooseGlobal = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (creating || renaming != null) {
        val target = renaming
        AlertDialog(
            onDismissRequest = { creating = false; renaming = null },
            title = { Text(stringResource(if (target == null) R.string.memory_overview_create else R.string.memory_overview_rename)) },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = { Text(stringResource(R.string.memory_overview_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(enabled = draftName.isNotBlank() && !busy, onClick = {
                    val name = draftName.trim()
                    creating = false
                    renaming = null
                    perform {
                        if (target == null) preferences.createMemorySpace(name)
                        else preferences.updateMemorySpace(target.copy(name = name))
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { creating = false; renaming = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    deleting?.let { space ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.memory_overview_delete_title)) },
            text = { Text(stringResource(R.string.memory_overview_delete_warning, space.name)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    deleting = null
                    perform { preferences.deleteMemorySpace(space.id) }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    editingCard?.let { card ->
        AlertDialog(
            onDismissRequest = { editingCard = null },
            title = { Text(stringResource(R.string.memory_overview_binding_title, card.name)) },
            text = {
                Column(modifier = Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    MemoryChoice(stringResource(R.string.character_card_memory_profile_follow_global), pendingBindingId == null) {
                        pendingBindingId = null
                    }
                    spaces.forEach { space ->
                        MemoryChoice(space.name, pendingBindingId == space.id) { pendingBindingId = space.id }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val id = pendingBindingId
                    editingCard = null
                    perform {
                        val latest = cardManager.getCharacterCard(card.id)
                        cardManager.updateCharacterCard(latest.copy(
                            memoryProfileBindingMode = if (id == null) CharacterCardMemoryProfileBindingMode.FOLLOW_GLOBAL
                                else CharacterCardMemoryProfileBindingMode.FIXED_PROFILE,
                            memoryProfileId = id
                        ))
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editingCard = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun MemorySectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun MemoryOverviewRow(title: String, subtitle: String, onClick: () -> Unit, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.Default.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun MemorySpaceRow(
    space: MemorySpace,
    subtitle: String,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean
) {
    var expanded by remember(space.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).clickable(enabled = enabled, onClick = onOpen).padding(vertical = 12.dp)
        ) {
            Text(space.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // 默认空间只有重命名操作，直接打开编辑弹窗；其他空间保留重命名和删除菜单。
        if (space.id == "default") {
            IconButton(onClick = onRename, enabled = enabled) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename))
            }
        } else {
            FilledTonalIconButton(
                onClick = { expanded = true }, enabled = enabled,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.memory_overview_space_actions))
            }
            DropdownMenu(
                expanded = expanded, onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp, shadowElevation = 6.dp,
            ) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { expanded = false; onRename() })
                HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error),
                    onClick = { expanded = false; onDelete() })
            }
        }
        IconButton(onClick = onOpen, enabled = enabled) {
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun MemoryChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
