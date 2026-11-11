package com.ai.assistance.operit.ui.features.extensions.packages.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2PublisherEntrySummary
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import com.ai.assistance.operit.ui.features.extensions.github.GitHubLoginDialog
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageDangerActionButton
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageDeleteDialog
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageItemCard
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageLabelChip
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManagePrimaryActionButton
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageVersionActionButton
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageErrorCard
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageLoadingState
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageEmptyState
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageReviewReasonChip
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageReviewFlow
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageScaffold
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageSecondaryActionButton
import com.ai.assistance.operit.ui.features.extensions.packages.market.BindMarketSearchToTopBar
import com.ai.assistance.operit.ui.features.extensions.packages.market.UnifiedMarketBrowseConfig
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketReviewState
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketStatsType
import com.ai.assistance.operit.ui.features.extensions.packages.market.PublishArtifactType
import com.ai.assistance.operit.ui.features.extensions.packages.market.labelResId
import com.ai.assistance.operit.ui.features.extensions.packages.market.resolveMarketReviewSnapshot
import com.ai.assistance.operit.ui.features.extensions.packages.screens.market.viewmodel.UnifiedMarketBrowseScope
import com.ai.assistance.operit.ui.features.extensions.packages.screens.market.viewmodel.UnifiedMarketBrowseViewModel
import com.ai.assistance.operit.ui.features.extensions.packages.screens.market.viewmodel.UnifiedMarketManageKind
import com.ai.assistance.operit.ui.features.extensions.packages.screens.market.viewmodel.UnifiedMarketManageViewModel
import kotlinx.coroutines.launch

private enum class ManageTypeTab(val labelRes: Int) {
    ALL(R.string.market_tab_all),
    SCRIPT(R.string.market_category_type_script),
    PLUGIN(R.string.market_category_type_package);

    fun matches(type: String): Boolean = when (this) {
        ALL -> true
        PLUGIN -> type in setOf(
            MarketStatsType.PACKAGE.wireValue,
            MarketStatsType.SKILL.wireValue,
            MarketStatsType.MCP.wireValue,
        )
        SCRIPT -> type == MarketStatsType.SCRIPT.wireValue
    }
}

@Composable
fun UnifiedMarketManageScreen(
    onNavigateToGitHubAccount: () -> Unit,
    onNavigateToEditArtifact: (MarketV2Entry) -> Unit,
    onNavigateToEditRepo: (MarketStatsType, MarketV2Entry) -> Unit,
    onNavigateToPublishArtifactVersion: (MarketV2Entry, Boolean) -> Unit,
    onNavigateToPublishRepoVersion: (MarketStatsType, MarketV2Entry, Boolean) -> Unit,
    onNavigateToPublishArtifact: () -> Unit,
    onNavigateToPublishRepo: (MarketStatsType) -> Unit,
    onNavigateToDetail: (MarketV2Entry) -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    embedded: Boolean = false,
) {
    val context = LocalContext.current
    val githubAuth = remember { GitHubAuthPreferences.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(ManageTypeTab.ALL) }
    val viewModel: UnifiedMarketManageViewModel =
        viewModel(
            key = "market-manage-all",
            factory =
                UnifiedMarketManageViewModel.Factory(
                    context.applicationContext,
                    UnifiedMarketManageKind.ALL
                )
        )
    val notificationViewModel: UnifiedMarketBrowseViewModel =
        viewModel(
            key = "market-creator-notifications",
            factory = UnifiedMarketBrowseViewModel.Factory(
                context.applicationContext,
                UnifiedMarketBrowseScope.All
            )
        )

    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val currentUser by githubAuth.userInfoFlow.collectAsState(initial = null)
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val hasLoaded by viewModel.hasLoaded.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val notifications by notificationViewModel.notifications.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<MarketV2PublisherEntrySummary?>(null) }
    var showReviewDialog by remember { mutableStateOf<MarketV2PublisherEntrySummary?>(null) }
    var showGitHubLogin by remember { mutableStateOf(false) }

    BindMarketSearchToTopBar(
        enabled = false,
        searchQuery = "",
        onSearchQueryChanged = {},
        searchPlaceholderRes = UnifiedMarketBrowseConfig.searchPlaceholderRes,
    )

    // Reset both stores on account changes so an old response cannot repopulate this screen.
    LaunchedEffect(isLoggedIn, currentUser?.id) {
        viewModel.reset()
        notificationViewModel.clearNotifications()
        selectedTab = ManageTypeTab.ALL
        showDeleteDialog = null
        showReviewDialog = null
        if (isLoggedIn && currentUser != null) {
            viewModel.loadEntries()
            notificationViewModel.loadNotifications()
        }
    }

    val accountReady = isLoggedIn && currentUser != null
    val showManageLoading = accountReady && !hasLoaded && errorMessage == null
    val showRequestLoading = accountReady && hasLoaded && isLoading
    val filteredEntries = remember(entries, selectedTab) {
        entries.filter { selectedTab.matches(it.type.lowercase()) }
    }

    MarketManageScaffold(
        isRefreshing = accountReady && isRefreshing,
        onRefresh = {
            if (accountReady) {
                viewModel.loadEntries(refresh = true)
                notificationViewModel.loadNotifications()
            }
        },
        embedded = embedded,
    ) {
        item(key = "account") {
            CreatorAccountHeader(
                isLoggedIn = accountReady,
                currentUser = currentUser,
                publishedCount = entries.count { it.isOpen() && it.listingState != "pending_listing" },
                reviewingCount = entries.count { it.resolveMarketReviewSnapshot().state == MarketReviewState.PENDING },
                changesCount = entries.count { it.resolveMarketReviewSnapshot().state == MarketReviewState.CHANGES_REQUESTED },
                notificationCount = notifications.size,
                onLogin = { showGitHubLogin = true },
                onOpenAccount = onNavigateToGitHubAccount,
                onLogout = { coroutineScope.launch { githubAuth.logout() } },
                onOpenNotifications = onNavigateToNotifications,
            )
        }
        item(key = "publish") {
            CreatorPublishSection(
                enabled = accountReady,
                onPublishArtifact = onNavigateToPublishArtifact,
                onPublishSkill = { onNavigateToPublishRepo(MarketStatsType.SKILL) },
                onPublishMcp = { onNavigateToPublishRepo(MarketStatsType.MCP) },
            )
        }
        // Account data, filters and owned entries are private content; guests only see login and locked actions.
        if (accountReady) {
            item(key = "publications-header") {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.market_creator_my_publications),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    CreatorFilterRow(
                        selected = selectedTab,
                        counts = ManageTypeTab.entries.associateWith { tab ->
                            entries.count { tab.matches(it.type.lowercase()) }
                        },
                        onSelected = { selectedTab = it },
                    )
                }
            }
            errorMessage?.let { message ->
                item(key = "error") { MarketManageErrorCard(message) }
            }
            when {
                showManageLoading -> item(key = "loading") {
                    MarketManageLoadingState(stringResource(R.string.loading_your_artifacts))
                }
                hasLoaded && errorMessage == null && filteredEntries.isEmpty() -> item(key = "empty") {
                    MarketManageEmptyState(
                        title = stringResource(R.string.market_creator_filter_empty),
                    )
                }
                else -> items(filteredEntries, key = { "entry-${it.id}" }) { entry ->
                    ManagedEntryCard(
                        entry = entry,
                        canManageEntry = entry.isOwnerRelation(),
                        viewModel = viewModel,
                        onNavigateToDetail = onNavigateToDetail,
                        onNavigateToEditArtifact = onNavigateToEditArtifact,
                        onNavigateToEditRepo = onNavigateToEditRepo,
                        onNavigateToPublishArtifactVersion = onNavigateToPublishArtifactVersion,
                        onNavigateToPublishRepoVersion = onNavigateToPublishRepoVersion,
                        onRequestRevision = { target ->
                            viewModel.openEntryForRevision(target) { fullEntry ->
                                when (val type = fullEntry.marketStatsType()) {
                                    MarketStatsType.SCRIPT,
                                    MarketStatsType.PACKAGE -> onNavigateToPublishArtifactVersion(fullEntry, target.relation == "owner")
                                    MarketStatsType.SKILL,
                                    MarketStatsType.MCP -> onNavigateToPublishRepoVersion(type, fullEntry, target.relation == "owner")
                                    null -> Unit
                                }
                            }
                        },
                        onShowReview = { showReviewDialog = it },
                        onDelete = { showDeleteDialog = it }
                    )
                }
            }
        }
    }

    showDeleteDialog?.let { entry ->
        MarketManageDeleteDialog(
            text = stringResource(R.string.confirm_remove_artifact_from_market, entry.title),
            onConfirm = {
                viewModel.withdrawEntry(entry)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }

    if (showGitHubLogin) {
        GitHubLoginDialog(
            onDismissRequest = { showGitHubLogin = false }
        )
    }

    if (showRequestLoading) {
        MarketManageRequestLoadingDialog()
    }

    showReviewDialog?.let { entry ->
        MarketManageReviewDialog(
            entry = entry,
            onDismiss = { showReviewDialog = null }
        )
    }

}

@Composable
private fun CreatorAccountHeader(
    isLoggedIn: Boolean,
    currentUser: GitHubUser?,
    publishedCount: Int,
    reviewingCount: Int,
    changesCount: Int,
    notificationCount: Int,
    onLogin: () -> Unit,
    onOpenAccount: () -> Unit,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLoggedIn && currentUser != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (currentUser.avatarUrl.isNotBlank()) {
                        Image(
                            painter = rememberAsyncImagePainter(currentUser.avatarUrl),
                            contentDescription = stringResource(R.string.user_avatar),
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = currentUser.name?.takeIf { it.isNotBlank() } ?: currentUser.login,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "@${currentUser.login}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onOpenAccount) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = stringResource(R.string.github_account),
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = stringResource(R.string.logout),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.github_account_not_logged_in),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.github_account_not_logged_in_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onLogin) {
                        Text(stringResource(R.string.login_github))
                    }
                }
            }

            if (isLoggedIn) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CreatorStatCell(
                        value = publishedCount,
                        label = stringResource(R.string.published),
                        modifier = Modifier.weight(1f),
                    )
                    CreatorStatCell(
                        value = reviewingCount,
                        label = stringResource(R.string.market_review_pending),
                        modifier = Modifier.weight(1f),
                    )
                    CreatorStatCell(
                        value = changesCount,
                        label = stringResource(R.string.market_creator_needs_changes),
                        modifier = Modifier.weight(1f),
                        emphasis = changesCount > 0,
                    )
                    CreatorStatCell(
                        value = notificationCount,
                        label = stringResource(R.string.market_notifications_title),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpenNotifications),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatorStatCell(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
) {
    Column(
        modifier = modifier.padding(vertical = 9.dp, horizontal = 4.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (emphasis) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CreatorPublishSection(
    enabled: Boolean,
    onPublishArtifact: () -> Unit,
    onPublishSkill: () -> Unit,
    onPublishMcp: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.publish_to_market),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        CreatorPublishAction(
            title = stringResource(R.string.market_publish_artifact),
            subtitle = stringResource(R.string.market_creator_publish_artifact_hint),
            icon = Icons.Default.Extension,
            enabled = enabled,
            onClick = onPublishArtifact,
        )
        CreatorPublishAction(
            title = stringResource(R.string.market_publish_skill),
            subtitle = stringResource(R.string.market_category_type_skill),
            icon = Icons.Default.AutoAwesome,
            enabled = enabled,
            onClick = onPublishSkill,
        )
        CreatorPublishAction(
            title = stringResource(R.string.market_publish_mcp),
            subtitle = stringResource(R.string.market_category_type_mcp),
            icon = Icons.Default.Api,
            enabled = enabled,
            onClick = onPublishMcp,
        )
    }
}

@Composable
private fun CreatorPublishAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val titleColor = colors.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    val detailColor = colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) colors.surfaceContainerLow else colors.onSurface.copy(alpha = 0.04f),
        contentColor = titleColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp)
                    .background(
                        color = if (enabled) colors.primaryContainer else colors.onSurface.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) colors.onPrimaryContainer else detailColor,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = title, color = titleColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, color = detailColor, style = MaterialTheme.typography.bodySmall)
            }
            Icon(
                imageVector = if (enabled) Icons.Default.ChevronRight else Icons.Default.Lock,
                contentDescription = if (enabled) null else stringResource(R.string.market_creator_publish_locked),
                tint = detailColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CreatorFilterRow(
    selected: ManageTypeTab,
    counts: Map<ManageTypeTab, Int>,
    onSelected: (ManageTypeTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ManageTypeTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val shape = RoundedCornerShape(50)
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(
                modifier = Modifier.heightIn(min = 28.dp).clip(shape).selectable(
                    selected = isSelected,
                    role = Role.Tab,
                    onClick = { onSelected(tab) },
                ),
                shape = shape,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(text = stringResource(tab.labelRes), style = MaterialTheme.typography.labelSmall, color = contentColor)
                    Text(text = counts.getValue(tab).toString(), style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.75f))
                }
            }
        }
    }
}

@Composable
private fun MarketManageRequestLoadingDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.market_manage_loading_request_title)) },
        text = {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Text(stringResource(R.string.market_manage_loading_request_message))
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun MarketManageReviewDialog(
    entry: MarketV2PublisherEntrySummary,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.market_manage_review_dialog_title)) },
        text = { MarketManageReviewDialogContent(entry = entry) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}

@Composable
private fun MarketManageReviewDialogContent(entry: MarketV2PublisherEntrySummary) {
    val context = LocalContext.current
    val review = remember(entry) { entry.resolveMarketReviewSnapshot() }
    val stateText = remember(review.state) { context.getString(review.state.labelResId()) }
    val reasonText =
        remember(review.reasons) {
            review.reasons.map { reason -> context.getString(reason.labelResId()) }.joinToString(separator = " / ")
        }
    val reviewDetail = remember(entry.reviewDetail) { entry.reviewDetail?.trim().orEmpty() }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.market_manage_resubmit_last_state, stateText),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (reasonText.isNotBlank()) {
            Text(
                text = stringResource(R.string.market_manage_resubmit_last_reason, reasonText),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (reviewDetail.isNotBlank()) {
            Text(
                text = stringResource(R.string.market_manage_review_detail, reviewDetail),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ManagedEntryCard(
    entry: MarketV2PublisherEntrySummary,
    canManageEntry: Boolean,
    viewModel: UnifiedMarketManageViewModel,
    onNavigateToDetail: (MarketV2Entry) -> Unit,
    onNavigateToEditArtifact: (MarketV2Entry) -> Unit,
    onNavigateToEditRepo: (MarketStatsType, MarketV2Entry) -> Unit,
    onNavigateToPublishArtifactVersion: (MarketV2Entry, Boolean) -> Unit,
    onNavigateToPublishRepoVersion: (MarketStatsType, MarketV2Entry, Boolean) -> Unit,
    onRequestRevision: (MarketV2PublisherEntrySummary) -> Unit,
    onShowReview: (MarketV2PublisherEntrySummary) -> Unit,
    onDelete: (MarketV2PublisherEntrySummary) -> Unit
) {
    val review = remember(entry) { entry.resolveMarketReviewSnapshot() }
    val reviewDetail = entry.reviewDetail?.trim().orEmpty()
    val canSubmitRevision = review.state == MarketReviewState.CHANGES_REQUESTED
    // A pending listing has no public detail shard, so public-detail actions cannot be opened yet.
    val canOpenPublicEntry = entry.isOpen() && entry.listingState != "pending_listing"
    val canPublishNewVersion = canOpenPublicEntry || (canManageEntry && entry.isWithdrawn())
    MarketManageItemCard(
        title = entry.title,
        description = entry.manageSummaryText(),
        showActions = canPublishNewVersion || canSubmitRevision || (canManageEntry && entry.isOpen()),
        onClick = {
            if (canOpenPublicEntry) {
                viewModel.openEntryDetail(entry, onNavigateToDetail)
            } else {
                onShowReview(entry)
            }
        },
        leadingContent = {
            CreatorEntryIcon(title = entry.title, type = entry.type)
        },
        headerContent = {
            MarketEntryTypeBadge(entry.type)
            MarketManageRelationBadge(entry.relation)
        },
        supportingContent = {
            MarketManageReviewFlow(
                reviewState = review.state,
                isOpen = entry.isOpen(),
                listingState = entry.listingState
            )
            if (review.reasons.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    review.reasons.take(2).forEach { reason ->
                        MarketManageReviewReasonChip(reason = reason)
                    }
                }
            }
            if (reviewDetail.isNotBlank()) {
                Text(
                    text = reviewDetail,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            if (canManageEntry && canOpenPublicEntry) {
                MarketManageSecondaryActionButton(
                    label = stringResource(R.string.edit),
                    icon = Icons.Default.Edit,
                    onClick = {
                        viewModel.openEntryDetail(entry) { fullEntry ->
                            when (val type = fullEntry.marketStatsType()) {
                                MarketStatsType.SCRIPT,
                                MarketStatsType.PACKAGE -> onNavigateToEditArtifact(fullEntry)
                                MarketStatsType.SKILL,
                                MarketStatsType.MCP -> onNavigateToEditRepo(type, fullEntry)
                                null -> Unit
                            }
                        }
                    }
                )
            }
            if (canPublishNewVersion) {
                MarketManageVersionActionButton(
                    label = stringResource(R.string.market_publish_new_version),
                    icon = Icons.Default.NewReleases,
                    onClick = {
                        val openNewVersionScreen = { fullEntry: MarketV2Entry ->
                            when (val type = fullEntry.marketStatsType()) {
                                MarketStatsType.SCRIPT,
                                MarketStatsType.PACKAGE -> onNavigateToPublishArtifactVersion(fullEntry, canManageEntry)
                                MarketStatsType.SKILL,
                                MarketStatsType.MCP -> onNavigateToPublishRepoVersion(type, fullEntry, canManageEntry)
                                null -> Unit
                            }
                        }
                        if (canOpenPublicEntry) {
                            viewModel.openEntryDetail(entry, openNewVersionScreen)
                        } else {
                            viewModel.openOwnedEntryDetail(entry, openNewVersionScreen)
                        }
                    }
                )
            }
            if (canManageEntry && entry.isOpen()) {
                MarketManageDangerActionButton(
                    label = stringResource(R.string.remove),
                    icon = Icons.Default.Delete,
                    onClick = { onDelete(entry) }
                )
            }
            if (canSubmitRevision) {
                MarketManagePrimaryActionButton(
                    label = stringResource(R.string.market_manage_submit_revision),
                    icon = Icons.Default.NewReleases,
                    onClick = { onRequestRevision(entry) }
                )
            }
        }
    )
}

@Composable
private fun RowScope.CreatorEntryIcon(title: String, type: String) {
    val isArtifact = PublishArtifactType.fromWireValue(type) != null
    Surface(
        modifier = Modifier.size(44.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (isArtifact) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (isArtifact) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title.trim().firstOrNull()?.toString().orEmpty().ifBlank { "?" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
    Spacer(modifier = Modifier.width(10.dp))
}

@Composable
private fun MarketEntryTypeBadge(type: String) {
    val label =
        when (type.lowercase()) {
            MarketStatsType.SCRIPT.wireValue -> stringResource(R.string.artifact_type_script)
            MarketStatsType.PACKAGE.wireValue -> stringResource(R.string.artifact_type_package)
            MarketStatsType.SKILL.wireValue -> stringResource(R.string.market_category_type_skill)
            MarketStatsType.MCP.wireValue -> stringResource(R.string.market_category_type_mcp)
            else -> type.ifBlank { "-" }
        }

    val isArtifact = PublishArtifactType.fromWireValue(type) != null
    MarketManageLabelChip(
        text = label,
        containerColor =
            if (isArtifact) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        contentColor =
            if (isArtifact) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
    )
}

@Composable
private fun MarketManageRelationBadge(relation: String) {
    val isContributor = relation.equals("contributor", ignoreCase = true)
    MarketManageLabelChip(
        text =
            stringResource(
                if (isContributor) {
                    R.string.market_manage_relation_contributor
                } else {
                    R.string.market_manage_relation_owner
                }
            ),
        containerColor =
            if (isContributor) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        contentColor =
            if (isContributor) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
    )
}

private fun MarketV2PublisherEntrySummary.isOpen(): Boolean {
    return stateCode.equals("approved", ignoreCase = true) || stateCode.equals("open", ignoreCase = true)
}

private fun MarketV2PublisherEntrySummary.isWithdrawn(): Boolean {
    return stateCode.equals("withdrawn", ignoreCase = true)
}

private fun MarketV2PublisherEntrySummary.isOwnerRelation(): Boolean {
    return relation.equals("owner", ignoreCase = true)
}

private fun MarketV2PublisherEntrySummary.isContributorRelation(): Boolean {
    return relation.equals("contributor", ignoreCase = true)
}

@Composable
private fun MarketV2PublisherEntrySummary.manageSummaryText(): String {
    val typeText =
        when (type.lowercase()) {
            MarketStatsType.SCRIPT.wireValue -> stringResource(R.string.artifact_type_script)
            MarketStatsType.PACKAGE.wireValue -> stringResource(R.string.artifact_type_package)
            MarketStatsType.SKILL.wireValue -> stringResource(R.string.market_category_type_skill)
            MarketStatsType.MCP.wireValue -> stringResource(R.string.market_category_type_mcp)
            else -> type
        }
    val categoryText =
        categoryId
            .takeIf { it.isNotBlank() }
            ?.let { marketCategoryLabel(it) }
    val updatedText = updatedAt.take(10).takeIf { it.isNotBlank() }
    return listOfNotNull(typeText, categoryText, updatedText).joinToString(" · ")
}

private fun MarketV2Entry.marketStatsType(): MarketStatsType? {
    return MarketStatsType.entries.firstOrNull { it.wireValue == type.lowercase() }
}

private fun MarketV2PublisherEntrySummary.marketStatsType(): MarketStatsType? {
    return MarketStatsType.entries.firstOrNull { it.wireValue == type.lowercase() }
}

