package com.ai.assistance.operit.ui.features.extensions.packages.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketReviewReason
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketReviewState
import com.ai.assistance.operit.ui.features.extensions.packages.market.labelResId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketManageScaffold(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    embedded: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    val body: @Composable (PaddingValues) -> Unit = { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            // Account, publish actions and records share one scroller, including on small screens.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
    if (embedded) {
        body(PaddingValues(0.dp))
    } else {
        CustomScaffold { body(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarketManageItemCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
    onClick: (() -> Unit)? = null,
    leadingContent: @Composable RowScope.() -> Unit = {},
    headerContent: @Composable RowScope.() -> Unit = {},
    supportingContent: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit
) {
    val clickableModifier =
        if (onClick != null) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
        }

    Card(
        modifier = clickableModifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, content = leadingContent)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        headerContent()
                    }
                }
            }

            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                content = supportingContent
            )

            if (showActions) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    actions()
                }
            }
        }
    }
}

@Composable
fun MarketManageLabelChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MarketManageReviewFlow(
    reviewState: MarketReviewState,
    isOpen: Boolean,
    listingState: String,
    modifier: Modifier = Modifier,
) {
    val submittedColor = MaterialTheme.colorScheme.primary
    val reviewColor = marketReviewFlowColor(reviewState)
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val isPendingListing = listingState == "pending_listing"
    val publicationActive = reviewState == MarketReviewState.APPROVED && isOpen && !isPendingListing
    val publicationColor = if (publicationActive) submittedColor else MaterialTheme.colorScheme.outline
    val publicationLabel = when {
        publicationActive -> stringResource(R.string.published)
        reviewState == MarketReviewState.APPROVED -> stringResource(R.string.market_review_step_scheduled)
        else -> stringResource(R.string.market_review_step_unlisted)
    }
    val firstConnector = if (reviewState == MarketReviewState.APPROVED) submittedColor else trackColor
    val secondConnector = if (publicationActive) submittedColor else trackColor

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(18.dp)) {
            val points = listOf(1f / 6f, 0.5f, 5f / 6f).map { Offset(size.width * it, size.height / 2f) }
            val radius = 5.dp.toPx()
            // Lines stretch between node centres; drawing them first keeps the nodes clearly visible.
            val nodeInset = Offset(radius, 0f)
            drawLine(firstConnector, points[0] + nodeInset, points[1] - nodeInset, strokeWidth = 2.dp.toPx())
            drawLine(secondConnector, points[1] + nodeInset, points[2] - nodeInset, strokeWidth = 2.dp.toPx())
            drawCircle(submittedColor, radius, points[0])
            drawCircle(reviewColor, radius, points[1])
            if (publicationActive) {
                drawCircle(publicationColor, radius, points[2])
            } else {
                drawCircle(publicationColor, radius, points[2], style = Stroke(width = 2.dp.toPx()))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listOf(
                stringResource(R.string.market_review_step_submitted) to submittedColor,
                stringResource(reviewState.labelResId()) to reviewColor,
                publicationLabel to publicationColor,
            ).forEach { (label, color) ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun marketReviewFlowColor(reviewState: MarketReviewState): Color {
    return when (reviewState) {
        MarketReviewState.PENDING -> MaterialTheme.colorScheme.tertiary
        MarketReviewState.APPROVED -> MaterialTheme.colorScheme.primary
        MarketReviewState.CHANGES_REQUESTED -> MaterialTheme.colorScheme.secondary
        MarketReviewState.REJECTED -> MaterialTheme.colorScheme.error
        MarketReviewState.WITHDRAWN -> MaterialTheme.colorScheme.outline
    }
}

@Composable
fun MarketManageReviewStatusChip(
    reviewState: MarketReviewState,
    modifier: Modifier = Modifier
) {
    val label =
        stringResource(reviewState.labelResId())
    val containerColor =
        when (reviewState) {
            MarketReviewState.PENDING -> MaterialTheme.colorScheme.tertiaryContainer
            MarketReviewState.APPROVED -> MaterialTheme.colorScheme.secondaryContainer
            MarketReviewState.CHANGES_REQUESTED -> MaterialTheme.colorScheme.primaryContainer
            MarketReviewState.REJECTED -> MaterialTheme.colorScheme.errorContainer
            MarketReviewState.WITHDRAWN -> MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        when (reviewState) {
            MarketReviewState.PENDING -> MaterialTheme.colorScheme.onTertiaryContainer
            MarketReviewState.APPROVED -> MaterialTheme.colorScheme.onSecondaryContainer
            MarketReviewState.CHANGES_REQUESTED -> MaterialTheme.colorScheme.onPrimaryContainer
            MarketReviewState.REJECTED -> MaterialTheme.colorScheme.onErrorContainer
            MarketReviewState.WITHDRAWN -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    MarketManageLabelChip(
        text = label,
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = modifier
    )
}

@Composable
fun MarketManageReviewReasonChip(
    reason: MarketReviewReason,
    modifier: Modifier = Modifier
) {
    val label =
        stringResource(reason.labelResId())

    MarketManageLabelChip(
        text = label,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
fun RowScope.MarketManageSecondaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RowScope.MarketManageDangerActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun RowScope.MarketManageVersionActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun RowScope.MarketManagePrimaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MarketManageDeleteDialog(
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    titleText: String = stringResource(R.string.confirm_delete),
    confirmText: String = stringResource(R.string.confirm_delete_action),
    dismissText: String = stringResource(R.string.cancel)
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

@Composable
fun MarketManageErrorCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun MarketManageLoadingState(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(8.dp))
        Text(message)
    }
}

@Composable
fun MarketManageEmptyState(title: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
