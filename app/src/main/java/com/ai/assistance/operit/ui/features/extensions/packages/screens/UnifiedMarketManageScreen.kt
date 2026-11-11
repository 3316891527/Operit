package com.ai.assistance.operit.ui.features.extensions.packages.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageReviewReasonChip
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageReviewFlow
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageScaffold
import com.ai.assistance.operit.ui.features.extensions.packages.components.MarketManageSecondaryActionButton
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
    PLUGIN(R.string.market_category_type_package),
    SCRIPT(R.string.market_category_type_script);

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

@OptIn(ExperimentalMaterial3Api::class)
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
    onNavigateToNotifications: () -> Unit = {}
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
    var showPublishDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            viewModel.loadEntries()
            notificationViewModel.loadNotifications()
        } else {
            viewModel.reset()
        }
    }

    val showManageLoading = isLoggedIn && !hasLoaded && entries.isEmpty()
    val showRequestLoading = isLoggedIn && hasLoaded && isLoading
    val filteredEntries = remember(entries, selectedTab) {
        entries.filter { selectedTab.matches(it.type.lowercase()) }
    }
    val showEmptyState = hasLoaded && errorMessage == null && filteredEntries.isEmpty()

    MarketManageScaffold(
        isLoggedIn = isLoggedIn,
        isLoading = showManageLoading,
        errorMessage = errorMessage,
        isEmpty = showEmptyState,
        onLogin = { showGitHubLogin = true },
        onPublish = { showPublishDialog = true },
        publishContentDescription = stringResource(R.string.publish_new_artifact),
        loginDescription = stringResource(R.string.need_login_github_manage_artifacts),
        loadingMessage = stringResource(R.string.loading_your_artifacts),
        emptyIcon = Icons.Default.Store,
        emptyTitle = stringResource(R.string.no_published_artifacts_yet),
        emptyDescription = stringResource(R.string.click_button_publish_first_artifact),
        emptyActionLabel = stringResource(R.string.publish_to_market),
        topContent = {
            CreatorAccountHeader(
                isLoggedIn = isLoggedIn,
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
            CreatorPublishSection(
                onPublishArtifact = { showPublishDialog = true },
                onPublishSkill = { onNavigateToPublishRepo(MarketStatsType.SKILL) },
                onPublishMcp = { onNavigateToPublishRepo(MarketStatsType.MCP) },
            )
            CreatorFilterRow(
                selected = selectedTab,
                counts = ManageTypeTab.entries.associateWith { tab ->
                    entries.count { tab.matches(it.type.lowercase()) }
                },
                onSelected = { selectedTab = it },
            )
            Text(
                text = stringResource(R.string.market_manage_owned_section),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.loadEntries(refresh = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredEntries, key = { "entry-${it.id}" }) { entry ->
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

    if (showPublishDialog) {
        ManagePublishChooserDialog(
            onDismiss = { showPublishDialog = false },
            onPublishArtifact = {
                showPublishDialog = false
                onNavigateToPublishArtifact()
            },
            onPublishRepo = { type ->
                showPublishDialog = false
                onNavigateToPublishRepo(type)
            }
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
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
                    label = stringResource(R.string.market_review_changes_requested),
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

@Composable
private fun CreatorStatCell(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (emphasis) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = if (emphasis) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 4.dp),
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
}

@Composable
private fun CreatorPublishSection(
    onPublishArtifact: () -> Unit,
    onPublishSkill: () -> Unit,
    onPublishMcp: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.publish_to_market),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        CreatorPublishAction(
            title = stringResource(R.string.market_publish_artifact),
            subtitle = stringResource(R.string.publish_target_package_market),
            icon = Icons.Default.Extension,
            onClick = onPublishArtifact,
        )
        CreatorPublishAction(
            title = stringResource(R.string.market_publish_skill),
            subtitle = stringResource(R.string.market_category_type_skill),
            icon = Icons.Default.AutoAwesome,
            onClick = onPublishSkill,
        )
        CreatorPublishAction(
            title = stringResource(R.string.market_publish_mcp),
            subtitle = stringResource(R.string.market_category_type_mcp),
            icon = Icons.Default.Api,
            onClick = onPublishMcp,
        )
    }
}

@Composable
private fun CreatorPublishAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = androidx.compose.material.icons.filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ManageTypeTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Surface(
                modifier = Modifier.clickable { onSelected(tab) },
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Text(
                    text = "${stringResource(tab.labelRes)} ${counts[tab] ?: 0}",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
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
private fun ManageSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
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
        isOpen = canOpenPublicEntry,
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
                MarketManageSecondaryActionButton(
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
private fun ManagePublishChooserDialog(
    onDismiss: () -> Unit,
    onPublishArtifact: () -> Unit,
    onPublishRepo: (MarketStatsType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.market_section_publish)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ManagePublishAction(
                    title = stringResource(R.string.market_publish_artifact),
                    onClick = onPublishArtifact
                )
                ManagePublishAction(
                    title = stringResource(R.string.market_publish_skill),
                    onClick = { onPublishRepo(MarketStatsType.SKILL) }
                )
                ManagePublishAction(
                    title = stringResource(R.string.market_publish_mcp),
                    onClick = { onPublishRepo(MarketStatsType.MCP) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ManagePublishAction(
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
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

