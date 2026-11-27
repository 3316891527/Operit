package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

@Composable
internal fun MarkdownImageLoadErrorLabel(expanded: Boolean, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier.clickable(
            onClickLabel = stringResource(if (expanded) R.string.common_collapse else R.string.view_details),
            role = Role.Button,
            onClick = onClick,
        ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.image_load_failed),
            modifier = Modifier.weight(1f, fill = false),
            color = color,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color,
        )
    }
}

@Composable
internal fun MarkdownImageLoadErrorDetails(diagnostic: MarkdownImageLoadDiagnostic) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MarkdownImageDiagnosticField(stringResource(R.string.crash_report_details_title), diagnostic.reason)
                MarkdownImageDiagnosticField(stringResource(R.string.path), diagnostic.resource)
                MarkdownImageDiagnosticField(stringResource(R.string.markdown_source), diagnostic.markdown)
            }
        }
    }
}

@Composable
private fun MarkdownImageDiagnosticField(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
