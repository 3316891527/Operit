package com.ai.assistance.operit.ui.features.extensions.packages.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketV2ManifestCategory
import com.ai.assistance.operit.ui.components.ActionSheetItem
import com.ai.assistance.operit.ui.components.menu.WorkbenchDropdownMenu
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

/** Shared market selector with a theme-aware, scrollable popup menu. */
@Composable
fun MarketCategoryDropdown(
    selectedCategory: String,
    categories: List<MarketV2ManifestCategory>,
    onCategorySelected: (String) -> Unit,
    categoryLabel: @Composable (String) -> String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val canExpand = enabled && categories.isNotEmpty()
    val selectedLabel = if (selectedCategory.isBlank()) {
        ""
    } else {
        categoryLabel(selectedCategory)
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            label = { Text(stringResource(R.string.market_detail_category_label)) },
            placeholder = { Text(stringResource(R.string.select_category)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = true,
            isError = enabled && selectedCategory.isBlank(),
            trailingIcon = {
                IconButton(
                    onClick = { if (canExpand) expanded = !expanded },
                    enabled = canExpand,
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.select_category),
                    )
                }
            },
        )

        WorkbenchDropdownMenu(
            expanded = expanded && canExpand,
            onDismissRequest = { expanded = false },
        ) {
            categories.forEach { category ->
                ActionSheetItem(
                    label = categoryLabel(category.id),
                    icon = Icons.Default.Folder,
                    selected = category.id == selectedCategory,
                    enabled = canExpand,
                    onClick = {
                        onCategorySelected(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
